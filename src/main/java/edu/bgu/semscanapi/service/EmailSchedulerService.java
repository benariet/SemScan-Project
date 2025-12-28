package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.entity.ApprovalStatus;
import edu.bgu.semscanapi.entity.EmailQueue;
import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.SupervisorReminderTracking;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.SupervisorReminderTrackingRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Scheduled service for email processing:
 * 1. Process email queue (every 2 minutes)
 * 2. Send daily supervisor reminders (at 9 AM)
 * 3. Send expiration warnings to students (at 8 AM)
 */
@Service
public class EmailSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(EmailSchedulerService.class);

    private static final DateTimeFormatter HUMAN_DATE_FORMAT =
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private static final DateTimeFormatter HUMAN_DATETIME_FORMAT =
        DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH);

    @Autowired
    private EmailQueueService emailQueueService;

    @Autowired
    private SeminarSlotRegistrationRepository registrationRepository;

    @Autowired
    private SeminarSlotRepository slotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SupervisorReminderTrackingRepository reminderTrackingRepository;

    @Autowired
    private DatabaseLoggerService dbLogger;

    @Autowired
    private GlobalConfig globalConfig;

    @Autowired
    private AppConfigService appConfigService;

    // ==================== PROCESS EMAIL QUEUE ====================

    /**
     * Process pending emails every 2 minutes
     */
    @Scheduled(fixedRate = 120000) // 2 minutes
    public void processEmailQueue() {
        try {
            int sent = emailQueueService.processQueue();
            if (sent > 0) {
                logger.info("[EMAIL_SCHEDULER] Processed {} emails from queue", sent);
            }
        } catch (Exception e) {
            logger.error("[EMAIL_SCHEDULER] Error processing email queue: {}", e.getMessage(), e);
            dbLogger.logError("EMAIL_SCHEDULER_QUEUE_ERROR",
                "Error processing email queue: " + e.getMessage(),
                e, null, null);
        }
    }

    // ==================== DAILY SUPERVISOR REMINDERS ====================

    /**
     * Send daily supervisor reminders at 9:00 AM
     * For all pending approvals that haven't been approved/declined yet
     */
    @Scheduled(cron = "0 0 9 * * *") // 9:00 AM every day
    @Transactional
    public void sendDailySupervisorReminders() {
        String logPrefix = "[SUPERVISOR_REMINDERS] ";
        LocalDate today = LocalDate.now();

        logger.info(logPrefix + "Starting daily supervisor reminder job");
        dbLogger.logAction("INFO", "EMAIL_SUPERVISOR_REMINDER_JOB_START",
            logPrefix + "Starting daily supervisor reminder job for " + today,
            null, null);

        try {
            // Find all registrations pending approval with valid tokens
            List<SeminarSlotRegistration> pendingApprovals = registrationRepository
                .findByApprovalStatusAndApprovalTokenIsNotNullAndApprovalTokenExpiresAtAfter(
                    ApprovalStatus.PENDING,
                    LocalDateTime.now()
                );

            int remindersSent = 0;
            int skipped = 0;

            for (SeminarSlotRegistration reg : pendingApprovals) {
                try {
                    Long slotId = reg.getSlotId();
                    String presenterUsername = reg.getPresenterUsername();

                    // Check if reminder already sent today for this specific registration (slotId + presenterUsername)
                    if (slotId != null && presenterUsername != null &&
                        reminderTrackingRepository.existsBySlotIdAndPresenterUsernameAndReminderDate(slotId, presenterUsername, today)) {
                        skipped++;
                        continue;
                    }

                    // Get supervisor email
                    String supervisorEmail = reg.getSupervisorEmail();
                    if (supervisorEmail == null || supervisorEmail.isEmpty()) {
                        logger.warn(logPrefix + "Skipping registration slotId={}, presenter={} - no supervisor email",
                            slotId, presenterUsername);
                        continue;
                    }

                    // Queue reminder email
                    queueSupervisorReminderEmail(reg);

                    // Track that we sent reminder today (using slotId + presenterUsername to uniquely identify)
                    SupervisorReminderTracking tracking = new SupervisorReminderTracking();
                    tracking.setSlotId(slotId);
                    tracking.setPresenterUsername(presenterUsername);
                    tracking.setSupervisorEmail(supervisorEmail);
                    tracking.setReminderDate(today);
                    reminderTrackingRepository.save(tracking);

                    remindersSent++;

                } catch (Exception e) {
                    logger.error(logPrefix + "Error sending reminder for registration {}: {}",
                        reg.getId(), e.getMessage());
                }
            }

            logger.info(logPrefix + "Job complete - Reminders queued: {}, Skipped (already sent): {}",
                remindersSent, skipped);
            dbLogger.logAction("INFO", "EMAIL_SUPERVISOR_REMINDER_JOB_COMPLETE",
                logPrefix + "Job complete - Queued: " + remindersSent + ", Skipped: " + skipped,
                null, null);

        } catch (Exception e) {
            logger.error(logPrefix + "Error in supervisor reminder job: {}", e.getMessage(), e);
            dbLogger.logError("EMAIL_SUPERVISOR_REMINDER_JOB_ERROR",
                logPrefix + "Job failed: " + e.getMessage(),
                e, null, null);
        }
    }

    /**
     * Queue a supervisor reminder email
     */
    private void queueSupervisorReminderEmail(SeminarSlotRegistration reg) {
        // Get presenter info
        String presenterUsername = reg.getPresenterUsername();
        String studentName = presenterUsername;

        // Try to get full name from user table
        if (presenterUsername != null) {
            Optional<User> userOpt = userRepository.findByBguUsername(presenterUsername);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.getFirstName() != null && user.getLastName() != null) {
                    studentName = user.getFirstName() + " " + user.getLastName();
                }
            }
        }

        // Get slot info
        String slotDate = "Unknown";
        String slotTime = "Unknown";
        Long slotId = reg.getSlotId();

        if (slotId != null) {
            Optional<SeminarSlot> slotOpt = slotRepository.findById(slotId);
            if (slotOpt.isPresent()) {
                SeminarSlot slot = slotOpt.get();
                if (slot.getSlotDate() != null) {
                    slotDate = slot.getSlotDate().format(HUMAN_DATE_FORMAT);
                }
                if (slot.getStartTime() != null && slot.getEndTime() != null) {
                    slotTime = slot.getStartTime().toString() + " - " + slot.getEndTime().toString();
                }
            }
        }

        String subject = "REMINDER: Seminar Slot Approval Pending - " + studentName;

        String approveUrl = globalConfig.getApiBaseUrl() + "/approve/" + reg.getApprovalToken();
        String declineUrl = globalConfig.getApiBaseUrl() + "/decline/" + reg.getApprovalToken();
        String expiresAt = reg.getApprovalTokenExpiresAt() != null ?
            reg.getApprovalTokenExpiresAt().format(HUMAN_DATETIME_FORMAT) : "soon";

        long daysRemaining = 0;
        if (reg.getApprovalTokenExpiresAt() != null) {
            daysRemaining = java.time.Duration.between(LocalDateTime.now(), reg.getApprovalTokenExpiresAt()).toDays();
        }

        String htmlContent = generateReminderEmailHtml(studentName, slotDate, slotTime,
            reg.getTopic(), approveUrl, declineUrl, expiresAt, daysRemaining);

        emailQueueService.queueEmail(
            EmailQueue.EmailType.SUPERVISOR_REMINDER,
            reg.getSupervisorEmail(),
            subject,
            htmlContent,
            null,  // No single registration ID (composite key)
            slotId,
            presenterUsername
        );

        logger.info("[SUPERVISOR_REMINDERS] Queued reminder for registration {} to {}",
            reg.getId(), reg.getSupervisorEmail());
    }

    // ==================== EXPIRATION WARNINGS ====================

    /**
     * Send expiration warnings to students at 8:00 AM
     * For tokens expiring within 24 hours
     */
    @Scheduled(cron = "0 0 8 * * *") // 8:00 AM every day
    @Transactional
    public void sendExpirationWarnings() {
        String logPrefix = "[EXPIRATION_WARNINGS] ";

        logger.info(logPrefix + "Starting expiration warning job");
        dbLogger.logAction("INFO", "EMAIL_EXPIRATION_WARNING_JOB_START",
            logPrefix + "Starting expiration warning job",
            null, null);

        try {
            LocalDateTime now = LocalDateTime.now();
            int expirationWarningHours = appConfigService.getIntegerConfig("expiration_warning_hours_before", 24);
            LocalDateTime expiresWithinThreshold = now.plusHours(expirationWarningHours);

            // Find PENDING registrations with tokens expiring within threshold (default 24 hours)
            // Only sends to pending registrations - approved ones don't need warnings
            List<SeminarSlotRegistration> expiringRegistrations = registrationRepository
                .findPendingByApprovalTokenExpiresAtBetween(ApprovalStatus.PENDING, now, expiresWithinThreshold);

            int warningsSent = 0;

            for (SeminarSlotRegistration reg : expiringRegistrations) {
                try {
                    // Get student email from user table
                    String presenterUsername = reg.getPresenterUsername();
                    String studentEmail = null;

                    if (presenterUsername != null) {
                        Optional<User> userOpt = userRepository.findByBguUsername(presenterUsername);
                        if (userOpt.isPresent() && userOpt.get().getEmail() != null) {
                            studentEmail = userOpt.get().getEmail();
                        }
                    }

                    if (studentEmail == null || studentEmail.isEmpty()) {
                        logger.warn(logPrefix + "Skipping registration {} - no student email", reg.getId());
                        continue;
                    }

                    // Queue expiration warning email
                    queueExpirationWarningEmail(reg, studentEmail);
                    warningsSent++;

                } catch (Exception e) {
                    logger.error(logPrefix + "Error sending warning for registration {}: {}",
                        reg.getId(), e.getMessage());
                }
            }

            logger.info(logPrefix + "Job complete - Warnings queued: {}", warningsSent);
            dbLogger.logAction("INFO", "EMAIL_EXPIRATION_WARNING_JOB_COMPLETE",
                logPrefix + "Job complete - Warnings queued: " + warningsSent,
                null, null);

        } catch (Exception e) {
            logger.error(logPrefix + "Error in expiration warning job: {}", e.getMessage(), e);
            dbLogger.logError("EMAIL_EXPIRATION_WARNING_JOB_ERROR",
                logPrefix + "Job failed: " + e.getMessage(),
                e, null, null);
        }
    }

    /**
     * Queue an expiration warning email to student
     */
    private void queueExpirationWarningEmail(SeminarSlotRegistration reg, String studentEmail) {
        String presenterUsername = reg.getPresenterUsername();
        String studentName = presenterUsername;

        // Try to get full name
        if (presenterUsername != null) {
            Optional<User> userOpt = userRepository.findByBguUsername(presenterUsername);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.getFirstName() != null && user.getLastName() != null) {
                    studentName = user.getFirstName() + " " + user.getLastName();
                }
            }
        }

        // Get slot date
        String slotDate = "Unknown";
        Long slotId = reg.getSlotId();

        if (slotId != null) {
            Optional<SeminarSlot> slotOpt = slotRepository.findById(slotId);
            if (slotOpt.isPresent() && slotOpt.get().getSlotDate() != null) {
                slotDate = slotOpt.get().getSlotDate().format(HUMAN_DATE_FORMAT);
            }
        }

        String subject = "URGENT: Your Seminar Slot Registration Expires Soon";

        String expiresAt = reg.getApprovalTokenExpiresAt() != null ?
            reg.getApprovalTokenExpiresAt().format(HUMAN_DATETIME_FORMAT) : "soon";

        long hoursRemaining = 0;
        if (reg.getApprovalTokenExpiresAt() != null) {
            hoursRemaining = java.time.Duration.between(LocalDateTime.now(), reg.getApprovalTokenExpiresAt()).toHours();
        }

        // Determine what the student needs to do
        String whatToDo;
        ApprovalStatus approvalStatus = reg.getApprovalStatus();
        if (approvalStatus == ApprovalStatus.PENDING && reg.getSupervisorEmail() != null) {
            whatToDo = "Your supervisor has not yet approved your registration. " +
                       "Please contact your supervisor (" + reg.getSupervisorEmail() + ") to approve it.";
        } else if (approvalStatus == ApprovalStatus.PENDING) {
            whatToDo = "Please check your email for the confirmation link and click 'Confirm' to proceed.";
        } else {
            whatToDo = "Please check your email for any pending actions.";
        }

        String htmlContent = generateExpirationWarningHtml(studentName, slotDate, expiresAt, hoursRemaining, whatToDo);

        emailQueueService.queueEmail(
            EmailQueue.EmailType.EXPIRATION_WARNING,
            studentEmail,
            subject,
            htmlContent,
            null,  // No single registration ID (composite key)
            slotId,
            presenterUsername
        );

        logger.info("[EXPIRATION_WARNINGS] Queued warning for registration {} to {}",
            reg.getId(), studentEmail);
    }

    // ==================== EMAIL HTML GENERATORS ====================

    private String generateReminderEmailHtml(String studentName, String slotDate, String slotTime,
                                              String topic, String approveUrl, String declineUrl,
                                              String expiresAt, long daysRemaining) {
        String urgencyColor = daysRemaining <= 2 ? "#dc3545" : "#ffc107";
        String urgencyText = daysRemaining <= 1 ? "EXPIRES TOMORROW" :
                            daysRemaining <= 2 ? "EXPIRES IN " + daysRemaining + " DAYS" :
                            daysRemaining + " days remaining";

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
            "<div style='max-width: 600px; margin: 0 auto; padding: 20px;'>" +
            "<div style='background: " + urgencyColor + "; color: white; padding: 15px; text-align: center; border-radius: 5px 5px 0 0;'>" +
            "<h2 style='margin: 0;'>Reminder: Approval Pending</h2>" +
            "<p style='margin: 5px 0 0 0; font-size: 14px;'>" + urgencyText + "</p>" +
            "</div>" +
            "<div style='background: #f8f9fa; padding: 20px; border: 1px solid #ddd;'>" +
            "<p>Dear Supervisor,</p>" +
            "<p>This is a reminder that <strong>" + escapeHtml(studentName) + "</strong> is waiting for your approval for a seminar slot registration.</p>" +
            "<table style='width: 100%; margin: 20px 0;'>" +
            "<tr><td style='padding: 5px 0;'><strong>Student:</strong></td><td>" + escapeHtml(studentName) + "</td></tr>" +
            "<tr><td style='padding: 5px 0;'><strong>Date:</strong></td><td>" + escapeHtml(slotDate) + "</td></tr>" +
            "<tr><td style='padding: 5px 0;'><strong>Time:</strong></td><td>" + escapeHtml(slotTime) + "</td></tr>" +
            (topic != null && !topic.isEmpty() ? "<tr><td style='padding: 5px 0;'><strong>Topic:</strong></td><td>" + escapeHtml(topic) + "</td></tr>" : "") +
            "<tr><td style='padding: 5px 0;'><strong>Expires:</strong></td><td style='color: " + urgencyColor + ";'>" + escapeHtml(expiresAt) + "</td></tr>" +
            "</table>" +
            "<div style='text-align: center; margin: 30px 0;'>" +
            "<a href='" + approveUrl + "' style='display: inline-block; padding: 12px 30px; background: #28a745; color: white; text-decoration: none; border-radius: 5px; margin: 5px;'>Approve</a>" +
            "<a href='" + declineUrl + "' style='display: inline-block; padding: 12px 30px; background: #dc3545; color: white; text-decoration: none; border-radius: 5px; margin: 5px;'>Decline</a>" +
            "</div>" +
            "<p style='font-size: 12px; color: #666;'>If the buttons don't work, copy and paste these links:</p>" +
            "<p style='font-size: 11px; word-break: break-all;'>Approve: " + approveUrl + "</p>" +
            "<p style='font-size: 11px; word-break: break-all;'>Decline: " + declineUrl + "</p>" +
            "</div>" +
            "<div style='text-align: center; padding: 15px; font-size: 12px; color: #666;'>" +
            "<p>SemScan - Seminar Attendance Management System</p>" +
            "</div>" +
            "</div></body></html>";
    }

    private String generateExpirationWarningHtml(String studentName, String slotDate, String expiresAt,
                                                   long hoursRemaining, String whatToDo) {
        String urgencyColor = hoursRemaining <= 6 ? "#dc3545" : "#ffc107";

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>" +
            "<div style='max-width: 600px; margin: 0 auto; padding: 20px;'>" +
            "<div style='background: " + urgencyColor + "; color: white; padding: 15px; text-align: center; border-radius: 5px 5px 0 0;'>" +
            "<h2 style='margin: 0;'>Action Required</h2>" +
            "<p style='margin: 5px 0 0 0; font-size: 14px;'>Your registration expires in " + hoursRemaining + " hours</p>" +
            "</div>" +
            "<div style='background: #f8f9fa; padding: 20px; border: 1px solid #ddd;'>" +
            "<p>Dear " + escapeHtml(studentName) + ",</p>" +
            "<p>Your seminar slot registration for <strong>" + escapeHtml(slotDate) + "</strong> will expire soon.</p>" +
            "<div style='background: #fff3cd; border: 1px solid #ffc107; padding: 15px; border-radius: 5px; margin: 20px 0;'>" +
            "<strong>Expires:</strong> " + escapeHtml(expiresAt) +
            "</div>" +
            "<p><strong>What you need to do:</strong></p>" +
            "<p>" + escapeHtml(whatToDo) + "</p>" +
            "<p>If you do not take action before the expiration time, your registration will be cancelled and the slot will be given to the next person on the waiting list.</p>" +
            "</div>" +
            "<div style='text-align: center; padding: 15px; font-size: 12px; color: #666;'>" +
            "<p>SemScan - Seminar Attendance Management System</p>" +
            "</div>" +
            "</div></body></html>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
