package edu.bgu.semscanapi.scheduled;

import edu.bgu.semscanapi.entity.ApprovalStatus;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.service.AppConfigService;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.MailService;
import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled job to send reminder emails to supervisors who haven't approved/declined
 * Runs every 6 hours to check for pending registrations that need reminders
 */
@Component
public class SupervisorApprovalReminderJob {

    private static final Logger logger = LoggerUtil.getLogger(SupervisorApprovalReminderJob.class);

    @Autowired
    private SeminarSlotRegistrationRepository registrationRepository;

    @Autowired
    private SeminarSlotRepository slotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppConfigService appConfigService;

    @Autowired
    private MailService mailService;

    @Autowired
    private GlobalConfig globalConfig;

    @Autowired(required = false)
    private DatabaseLoggerService databaseLoggerService;

    /**
     * Check for PENDING registrations that need reminder emails
     * Runs every 6 hours
     */
    @Scheduled(fixedRate = 21600000) // 6 hours
    @Transactional
    public void sendApprovalReminders() {
        logger.info("Starting supervisor approval reminder job...");

        if (databaseLoggerService != null) {
            databaseLoggerService.logAction("INFO", "APPROVAL_REMINDER_JOB_STARTED",
                    "Scheduled job started: checking for pending registrations needing reminders",
                    null, "job=SupervisorApprovalReminderJob");
        }

        try {
            // Get config values
            int reminderIntervalDays = appConfigService.getIntegerConfig("approval_reminder_interval_days", 2);
            int tokenExpiryDays = appConfigService.getIntegerConfig("approval_token_expiry_days", 14);

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime reminderThreshold = now.minusDays(reminderIntervalDays);

            // Find all PENDING registrations
            List<SeminarSlotRegistration> pendingRegistrations = registrationRepository.findByApprovalStatus(ApprovalStatus.PENDING);

            int remindersSent = 0;
            int skipped = 0;

            for (SeminarSlotRegistration reg : pendingRegistrations) {
                try {
                    // Skip if no supervisor email
                    if (reg.getSupervisorEmail() == null || reg.getSupervisorEmail().isEmpty()) {
                        skipped++;
                        continue;
                    }

                    // Skip if token is expired
                    if (reg.getApprovalTokenExpiresAt() != null && now.isAfter(reg.getApprovalTokenExpiresAt())) {
                        skipped++;
                        continue;
                    }

                    // Check if reminder is needed
                    boolean needsReminder = false;
                    LocalDateTime lastContact = reg.getLastReminderSentAt();

                    if (lastContact == null) {
                        // No reminder sent yet - check if registered_at is old enough
                        lastContact = reg.getRegisteredAt();
                    }

                    if (lastContact != null && lastContact.isBefore(reminderThreshold)) {
                        needsReminder = true;
                    }

                    if (needsReminder) {
                        // Send reminder
                        sendReminderEmails(reg);

                        // Update last reminder sent timestamp
                        reg.setLastReminderSentAt(now);
                        registrationRepository.save(reg);

                        remindersSent++;

                        logger.info("Sent reminder for registration: slotId={}, presenter={}, supervisor={}",
                                reg.getSlotId(), reg.getPresenterUsername(), reg.getSupervisorEmail());

                        if (databaseLoggerService != null) {
                            databaseLoggerService.logBusinessEvent("APPROVAL_REMINDER_SENT",
                                    String.format("Reminder sent to supervisor %s for presenter %s, slotId=%d",
                                            reg.getSupervisorEmail(), reg.getPresenterUsername(), reg.getSlotId()),
                                    reg.getPresenterUsername());
                        }
                    }

                } catch (Exception e) {
                    logger.error("Error processing reminder for registration slotId={}, presenter={}: {}",
                            reg.getSlotId(), reg.getPresenterUsername(), e.getMessage(), e);
                    if (databaseLoggerService != null) {
                        databaseLoggerService.logError("APPROVAL_REMINDER_ERROR",
                                String.format("Error sending reminder for slotId=%d, presenter=%s: %s",
                                        reg.getSlotId(), reg.getPresenterUsername(), e.getMessage()),
                                e, reg.getPresenterUsername(), null);
                    }
                }
            }

            logger.info("Supervisor approval reminder job completed. Reminders sent: {}, Skipped: {}",
                    remindersSent, skipped);

            if (databaseLoggerService != null) {
                databaseLoggerService.logAction("INFO", "APPROVAL_REMINDER_JOB_COMPLETED",
                        String.format("Reminder job completed: sent=%d, skipped=%d, total_pending=%d",
                                remindersSent, skipped, pendingRegistrations.size()),
                        null, String.format("sent=%d,skipped=%d", remindersSent, skipped));
            }

        } catch (Exception e) {
            logger.error("Error in supervisor approval reminder job: {}", e.getMessage(), e);
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("APPROVAL_REMINDER_JOB_FAILED",
                        "Supervisor approval reminder job failed: " + e.getMessage(),
                        e, null, null);
            }
        }
    }

    /**
     * Send reminder email to supervisor and notification to student
     */
    private void sendReminderEmails(SeminarSlotRegistration reg) {
        // Get slot details
        Optional<SeminarSlot> slotOpt = slotRepository.findById(reg.getSlotId());
        if (slotOpt.isEmpty()) {
            logger.warn("Slot not found for reminder: slotId={}", reg.getSlotId());
            return;
        }
        SeminarSlot slot = slotOpt.get();

        // Get presenter details
        Optional<User> userOpt = userRepository.findByBguUsername(reg.getPresenterUsername());
        String presenterName = reg.getPresenterUsername();
        String presenterEmail = null;
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            presenterName = user.getFirstName() + " " + user.getLastName();
            presenterEmail = user.getEmail();
        }

        // Build URLs
        String baseUrl = globalConfig.getApiBaseUrl();
        String approveUrl = baseUrl + "/approve/" + reg.getApprovalToken();
        String declineUrl = baseUrl + "/decline/" + reg.getApprovalToken();

        // Format date
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String slotDate = slot.getSlotDate() != null ? slot.getSlotDate().format(dateFormatter) : "TBD";
        String slotTime = slot.getStartTime() != null ? slot.getStartTime().format(timeFormatter) : "";
        if (slot.getEndTime() != null) {
            slotTime += " - " + slot.getEndTime().format(timeFormatter);
        }

        // Calculate days remaining
        long daysRemaining = 0;
        if (reg.getApprovalTokenExpiresAt() != null) {
            daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), reg.getApprovalTokenExpiresAt());
            if (daysRemaining < 0) daysRemaining = 0;
        }

        // Send reminder to supervisor
        String supervisorSubject = "Reminder: Pending Approval for " + presenterName + "'s Seminar Registration";
        String supervisorHtml = generateSupervisorReminderEmail(
                reg.getSupervisorName(),
                presenterName,
                reg.getTopic(),
                slotDate,
                slotTime,
                slot.getRoom(),
                approveUrl,
                declineUrl,
                daysRemaining
        );

        mailService.sendHtmlEmail(reg.getSupervisorEmail(), supervisorSubject, supervisorHtml);

        // Send notification to student (if we have their email)
        if (presenterEmail != null && !presenterEmail.isEmpty()) {
            String studentSubject = "Reminder Sent to Your Supervisor";
            String studentHtml = generateStudentNotificationEmail(
                    presenterName,
                    reg.getSupervisorName(),
                    reg.getTopic(),
                    slotDate,
                    daysRemaining
            );
            mailService.sendHtmlEmail(presenterEmail, studentSubject, studentHtml);
        }
    }

    private String generateSupervisorReminderEmail(String supervisorName, String presenterName,
                                                    String topic, String date, String time, String room,
                                                    String approveUrl, String declineUrl, long daysRemaining) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background-color: #FF9800; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0;">
                        <h1 style="margin: 0; font-size: 24px;">Reminder: Pending Approval</h1>
                    </div>
                    <div style="background-color: white; padding: 30px; border-radius: 0 0 8px 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
                        <p style="font-size: 16px; color: #333;">Dear %s,</p>
                        <p style="color: #666;">This is a friendly reminder that you have a pending seminar registration request from <strong>%s</strong> that requires your approval.</p>

                        <div style="background-color: #FFF3E0; padding: 15px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #FF9800;">
                            <p style="margin: 0; color: #E65100;"><strong>This link will expire in %d day(s).</strong></p>
                        </div>

                        <div style="background-color: #f9f9f9; padding: 20px; border-radius: 8px; margin: 20px 0;">
                            <h3 style="margin-top: 0; color: #333;">Registration Details:</h3>
                            <p style="margin: 5px 0;"><strong>Student:</strong> %s</p>
                            <p style="margin: 5px 0;"><strong>Topic:</strong> %s</p>
                            <p style="margin: 5px 0;"><strong>Date:</strong> %s</p>
                            <p style="margin: 5px 0;"><strong>Time:</strong> %s</p>
                            <p style="margin: 5px 0;"><strong>Room:</strong> %s</p>
                        </div>

                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" style="display: inline-block; background-color: #4CAF50; color: white; padding: 15px 40px; text-decoration: none; border-radius: 5px; font-size: 16px; margin: 5px;">Approve</a>
                            <a href="%s" style="display: inline-block; background-color: #f44336; color: white; padding: 15px 40px; text-decoration: none; border-radius: 5px; font-size: 16px; margin: 5px;">Decline</a>
                        </div>

                        <p style="color: #999; font-size: 12px; margin-top: 30px;">This is an automated reminder from SemScan.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                supervisorName != null ? supervisorName : "Supervisor",
                presenterName,
                daysRemaining,
                presenterName,
                topic != null ? topic : "Not specified",
                date,
                time,
                room != null ? room : "TBD",
                approveUrl,
                declineUrl
        );
    }

    private String generateStudentNotificationEmail(String studentName, String supervisorName,
                                                     String topic, String date, long daysRemaining) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background-color: #2196F3; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0;">
                        <h1 style="margin: 0; font-size: 24px;">Reminder Sent to Supervisor</h1>
                    </div>
                    <div style="background-color: white; padding: 30px; border-radius: 0 0 8px 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
                        <p style="font-size: 16px; color: #333;">Dear %s,</p>
                        <p style="color: #666;">We have sent a reminder to your supervisor <strong>%s</strong> regarding your pending seminar registration.</p>

                        <div style="background-color: #E3F2FD; padding: 15px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #2196F3;">
                            <p style="margin: 0; color: #1565C0;"><strong>Your registration is still pending approval.</strong></p>
                            <p style="margin: 5px 0 0 0; color: #1565C0;">The approval link will expire in %d day(s).</p>
                        </div>

                        <div style="background-color: #f9f9f9; padding: 20px; border-radius: 8px; margin: 20px 0;">
                            <h3 style="margin-top: 0; color: #333;">Your Registration:</h3>
                            <p style="margin: 5px 0;"><strong>Topic:</strong> %s</p>
                            <p style="margin: 5px 0;"><strong>Date:</strong> %s</p>
                        </div>

                        <p style="color: #666;">If you haven't heard back from your supervisor, you may want to follow up with them directly.</p>

                        <p style="color: #999; font-size: 12px; margin-top: 30px;">This is an automated notification from SemScan.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                studentName,
                supervisorName != null ? supervisorName : "your supervisor",
                daysRemaining,
                topic != null ? topic : "Not specified",
                date
        );
    }
}
