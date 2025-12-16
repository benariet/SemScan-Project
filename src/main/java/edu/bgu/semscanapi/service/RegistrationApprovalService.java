package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.entity.ApprovalStatus;
import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.SeminarSlotRegistrationId;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Service
public class RegistrationApprovalService {

    private static final Logger logger = LoggerUtil.getLogger(RegistrationApprovalService.class);

    private final SeminarSlotRegistrationRepository registrationRepository;
    private final SeminarSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final GlobalConfig globalConfig;
    private final DatabaseLoggerService databaseLoggerService;

    @Value("${app.registration.approval.token-expiration-hours:72}")
    private int approvalTokenExpirationHours;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public RegistrationApprovalService(
            SeminarSlotRegistrationRepository registrationRepository,
            SeminarSlotRepository slotRepository,
            UserRepository userRepository,
            MailService mailService,
            GlobalConfig globalConfig,
            DatabaseLoggerService databaseLoggerService) {
        this.registrationRepository = registrationRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.globalConfig = globalConfig;
        this.databaseLoggerService = databaseLoggerService;
    }

    /**
     * Generate approval token and send approval email to supervisor
     */
    @Transactional
    public void sendApprovalEmail(SeminarSlotRegistration registration) {
        logger.info("📧 [RegistrationApprovalService] sendApprovalEmail() called - slotId: {}, presenter: {}",
                registration.getSlotId(), registration.getPresenterUsername());
        databaseLoggerService.logAction("INFO", "EMAIL_SEND_APPROVAL_EMAIL_CALLED",
                String.format("sendApprovalEmail() called for slotId=%d, presenter=%s", registration.getSlotId(), registration.getPresenterUsername()),
                registration.getPresenterUsername(), String.format("slotId=%d", registration.getSlotId()));
        
        if (registration.getSupervisorEmail() == null || registration.getSupervisorEmail().trim().isEmpty()) {
            String errorMsg = String.format("Cannot send approval email: supervisor email is missing for registration slotId=%d, presenter=%s",
                    registration.getSlotId(), registration.getPresenterUsername());
            logger.warn("📧 ❌ {}", errorMsg);
            databaseLoggerService.logError("EMAIL_REGISTRATION_APPROVAL_EMAIL_MISSING_SUPERVISOR",
                    errorMsg, null, registration.getPresenterUsername(),
                    String.format("slotId=%d,presenter=%s", registration.getSlotId(), registration.getPresenterUsername()));
            return;
        }

        logger.info("📧 [RegistrationApprovalService] Supervisor email found: {}", registration.getSupervisorEmail());
        databaseLoggerService.logAction("INFO", "EMAIL_SUPERVISOR_EMAIL_VALIDATED",
                String.format("Supervisor email validated: %s", registration.getSupervisorEmail()),
                registration.getPresenterUsername(), String.format("slotId=%d,supervisorEmail=%s", registration.getSlotId(), registration.getSupervisorEmail()));

        // Generate cryptographically secure UUID token for email approval link (expires in configured hours)
        String approvalToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(approvalTokenExpirationHours);
        logger.info("📧 [RegistrationApprovalService] Generated approval token: {} (expires: {})", approvalToken, expiresAt);
        databaseLoggerService.logAction("INFO", "EMAIL_APPROVAL_TOKEN_GENERATED",
                String.format("Generated approval token (expires: %s)", expiresAt),
                registration.getPresenterUsername(), String.format("slotId=%d,tokenExpiresAt=%s", registration.getSlotId(), expiresAt));

        registration.setApprovalToken(approvalToken);
        registration.setApprovalTokenExpiresAt(expiresAt);
        registration.setApprovalStatus(ApprovalStatus.PENDING);
        registrationRepository.save(registration);
        logger.info("📧 [RegistrationApprovalService] Registration saved with approval token");
        databaseLoggerService.logAction("INFO", "EMAIL_REGISTRATION_TOKEN_SAVED",
                String.format("Registration saved with approval token"),
                registration.getPresenterUsername(), String.format("slotId=%d", registration.getSlotId()));

        // Retrieve slot information (date, time, location) to include in approval email
        SeminarSlot slot = slotRepository.findById(registration.getSlotId())
                .orElseThrow(() -> {
                    String errorMsg = "Slot not found: " + registration.getSlotId();
                    logger.error("📧 ❌ {}", errorMsg);
                    databaseLoggerService.logError("EMAIL_SLOT_NOT_FOUND_EMAIL",
                            errorMsg, null, registration.getPresenterUsername(),
                            String.format("slotId=%d", registration.getSlotId()));
                    return new IllegalArgumentException(errorMsg);
                });
        logger.info("📧 [RegistrationApprovalService] Slot retrieved: slotId={}, date={}, time={}-{}",
                slot.getSlotId(), slot.getSlotDate(), slot.getStartTime(), slot.getEndTime());

        // Generate mobile-compatible approval/decline URLs using token-based authentication
        String baseUrl = globalConfig.getApiBaseUrl();
        String approveUrl = baseUrl + "/approve/" + approvalToken;
        String declineUrl = baseUrl + "/decline/" + approvalToken;
        logger.info("📧 [RegistrationApprovalService] Generated URLs - approve: {}, decline: {}", approveUrl, declineUrl);
        
        // Generate HTML email with slot details, student info, and clickable approve/decline buttons
        String subject = "SemScan: Approval Required for Seminar Slot Registration";
        logger.info("📧 [RegistrationApprovalService] Generating HTML email content...");
        String htmlContent = generateApprovalEmailHtml(registration, slot, approveUrl, declineUrl, expiresAt);
        logger.info("📧 [RegistrationApprovalService] HTML email content generated (length: {} chars)", htmlContent.length());
        databaseLoggerService.logAction("INFO", "EMAIL_CONTENT_GENERATED",
                String.format("HTML email content generated (length: %d chars)", htmlContent.length()),
                registration.getPresenterUsername(), String.format("slotId=%d,contentLength=%d", registration.getSlotId(), htmlContent.length()));
        
        // Send email to supervisor's email address via MailService
        logger.info("📧 [RegistrationApprovalService] Calling mailService.sendHtmlEmail() - to: {}, subject: {}", 
                registration.getSupervisorEmail(), subject);
        databaseLoggerService.logAction("INFO", "EMAIL_CALLING_MAILSERVICE",
                String.format("Calling mailService.sendHtmlEmail() to: %s", registration.getSupervisorEmail()),
                registration.getPresenterUsername(), String.format("slotId=%d,supervisorEmail=%s", registration.getSlotId(), registration.getSupervisorEmail()));
        boolean sent = mailService.sendHtmlEmail(registration.getSupervisorEmail(), subject, htmlContent);
        
        if (sent) {
            logger.info("📧 ✅ [RegistrationApprovalService] Approval email sent successfully to supervisor {} for registration slotId={}, presenter={}",
                    registration.getSupervisorEmail(), registration.getSlotId(), registration.getPresenterUsername());
            databaseLoggerService.logBusinessEvent("EMAIL_REGISTRATION_APPROVAL_EMAIL_SENT",
                    String.format("Approval email sent to supervisor %s for registration slotId=%d, presenter=%s",
                            registration.getSupervisorEmail(), registration.getSlotId(), registration.getPresenterUsername()),
                    registration.getPresenterUsername());
        } else {
            logger.error("📧 ❌ [RegistrationApprovalService] Failed to send approval email to supervisor {} for registration slotId={}, presenter={}",
                    registration.getSupervisorEmail(), registration.getSlotId(), registration.getPresenterUsername());
            databaseLoggerService.logError("EMAIL_REGISTRATION_APPROVAL_EMAIL_FAILED",
                    String.format("Failed to send approval email to supervisor %s for registration slotId=%d, presenter=%s",
                            registration.getSupervisorEmail(), registration.getSlotId(), registration.getPresenterUsername()),
                    null, registration.getPresenterUsername(),
                    String.format("slotId=%d,supervisorEmail=%s", registration.getSlotId(), registration.getSupervisorEmail()));
        }
        
        logger.info("📧 [RegistrationApprovalService] sendApprovalEmail() completed - slotId: {}, presenter: {}, sent: {}",
                registration.getSlotId(), registration.getPresenterUsername(), sent);
    }

    /**
     * Approve registration via token
     */
    @Transactional
    public void approveRegistration(Long slotId, String presenterUsername, String token) {
        SeminarSlotRegistration registration = registrationRepository
                .findById(new SeminarSlotRegistrationId(slotId, presenterUsername))
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        // Validate token
        if (!token.equals(registration.getApprovalToken())) {
            databaseLoggerService.logError("REGISTRATION_APPROVAL_INVALID_TOKEN",
                    String.format("Invalid approval token for slotId=%d, presenter=%s", slotId, presenterUsername),
                    null, presenterUsername, String.format("slotId=%d", slotId));
            throw new IllegalArgumentException("Invalid approval token");
        }

        // Verify token hasn't expired: compare current time with token expiration timestamp
        if (registration.getApprovalTokenExpiresAt() != null && 
            LocalDateTime.now().isAfter(registration.getApprovalTokenExpiresAt())) {
            registration.setApprovalStatus(ApprovalStatus.EXPIRED);
            registrationRepository.save(registration);
            databaseLoggerService.logBusinessEvent("REGISTRATION_EXPIRED",
                    String.format("Registration expired for slotId=%d, presenter=%s (token expired)", slotId, presenterUsername),
                    presenterUsername);
            throw new IllegalStateException("Approval token has expired");
        }

        // Prevent double-processing: only PENDING registrations can be approved/declined
        if (registration.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Registration is not pending approval");
        }

        // Update registration status to APPROVED and record supervisor approval timestamp
        registration.setApprovalStatus(ApprovalStatus.APPROVED);
        registration.setSupervisorApprovedAt(LocalDateTime.now());
        registrationRepository.save(registration);

        logger.info("Registration approved for slotId={}, presenter={}", slotId, presenterUsername);
        databaseLoggerService.logBusinessEvent("REGISTRATION_APPROVED",
                String.format("Registration approved for slotId=%d, presenter=%s", slotId, presenterUsername),
                presenterUsername);

        // Send approval notification email OUTSIDE transaction to prevent holding database locks during SMTP operations
        try {
            sendApprovalNotificationEmail(registration);
        } catch (Exception e) {
            logger.error("Failed to send approval notification email to presenter {} for slot {}", presenterUsername, slotId, e);
            // Don't fail approval if email fails
        }
    }

    /**
     * Approve registration via token only (mobile-compatible)
     */
    @Transactional
    public void approveRegistrationByToken(String approvalToken) {
        SeminarSlotRegistration registration = registrationRepository
                .findByApprovalToken(approvalToken)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found for this token"));

        Long slotId = registration.getSlotId();
        String presenterUsername = registration.getPresenterUsername();
        
        approveRegistration(slotId, presenterUsername, approvalToken);
    }

    /**
     * Decline registration via token only (mobile-compatible)
     */
    @Transactional
    public void declineRegistrationByToken(String approvalToken, String reason) {
        SeminarSlotRegistration registration = registrationRepository
                .findByApprovalToken(approvalToken)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found for this token"));

        Long slotId = registration.getSlotId();
        String presenterUsername = registration.getPresenterUsername();
        
        declineRegistration(slotId, presenterUsername, approvalToken, reason);
    }

    /**
     * Decline registration via token
     */
    @Transactional
    public void declineRegistration(Long slotId, String presenterUsername, String token, String reason) {
        SeminarSlotRegistration registration = registrationRepository
                .findById(new SeminarSlotRegistrationId(slotId, presenterUsername))
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        // Validate token
        if (!token.equals(registration.getApprovalToken())) {
            databaseLoggerService.logError("REGISTRATION_APPROVAL_INVALID_TOKEN",
                    String.format("Invalid approval token for slotId=%d, presenter=%s", slotId, presenterUsername),
                    null, presenterUsername, String.format("slotId=%d", slotId));
            throw new IllegalArgumentException("Invalid approval token");
        }

        // Verify token hasn't expired: compare current time with token expiration timestamp
        if (registration.getApprovalTokenExpiresAt() != null && 
            LocalDateTime.now().isAfter(registration.getApprovalTokenExpiresAt())) {
            registration.setApprovalStatus(ApprovalStatus.EXPIRED);
            registrationRepository.save(registration);
            databaseLoggerService.logBusinessEvent("REGISTRATION_EXPIRED",
                    String.format("Registration expired for slotId=%d, presenter=%s (token expired)", slotId, presenterUsername),
                    presenterUsername);
            throw new IllegalStateException("Approval token has expired");
        }

        // Prevent double-processing: only PENDING registrations can be approved/declined
        if (registration.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Registration is not pending approval");
        }

        // Decline registration
        registration.setApprovalStatus(ApprovalStatus.DECLINED);
        registration.setSupervisorDeclinedAt(LocalDateTime.now());
        registration.setSupervisorDeclinedReason(reason);
        registrationRepository.save(registration);

        logger.info("Registration declined for slotId={}, presenter={}, reason={}", slotId, presenterUsername, reason);
        databaseLoggerService.logBusinessEvent("REGISTRATION_DECLINED",
                String.format("Registration declined for slotId=%d, presenter=%s, reason=%s", slotId, presenterUsername, reason),
                presenterUsername);
    }

    /**
     * Generate HTML email content for approval request
     */
    private String generateApprovalEmailHtml(SeminarSlotRegistration registration, SeminarSlot slot, 
                                             String approveUrl, String declineUrl, LocalDateTime expiresAt) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background-color: #f9f9f9; }
                    .button { display: inline-block; padding: 12px 24px; margin: 10px 5px; text-decoration: none; 
                             border-radius: 5px; font-weight: bold; }
                    .button-approve { background-color: #4CAF50; color: white; }
                    .button-decline { background-color: #f44336; color: white; }
                    .info-box { background-color: #e3f2fd; padding: 15px; margin: 15px 0; border-left: 4px solid #2196F3; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>SemScan - Approval Required</h1>
                    </div>
                    <div class="content">
                        <p>Dear %s,</p>
                        <p>Your student <strong>%s</strong> has requested to register for a seminar slot and requires your approval.</p>
                        
                        <div class="info-box">
                            <h3>Registration Details:</h3>
                            <p><strong>Student:</strong> %s</p>
                            <p><strong>Slot Date:</strong> %s</p>
                            <p><strong>Time:</strong> %s - %s</p>
                            <p><strong>Topic:</strong> %s</p>
                            <p><strong>Degree:</strong> %s</p>
                        </div>
                        
                        <p>Please review the registration and choose one of the following options:</p>
                        
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" class="button button-approve">Approve Registration</a>
                            <a href="%s" class="button button-decline">Decline Registration</a>
                        </div>
                        
                        <p style="color: #666; font-size: 12px;">
                            <strong>Note:</strong> This approval link will expire on %s.
                        </p>
                    </div>
                    <div class="footer">
                        <p>This is an automated message from SemScan Attendance System.</p>
                        <p>If you did not expect this email, please ignore it.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
            registration.getSupervisorName() != null ? registration.getSupervisorName() : "Supervisor",
            registration.getPresenterUsername(),
            registration.getPresenterUsername(),
            slot.getSlotDate().toString(),
            slot.getStartTime().toString(),
            slot.getEndTime().toString(),
            registration.getTopic() != null ? registration.getTopic() : "Not specified",
            registration.getDegree().toString(),
            approveUrl,
            declineUrl,
            expiresAt.toString()
        );
    }

    /**
     * Send approval notification email to presenter when registration is approved
     */
    private void sendApprovalNotificationEmail(SeminarSlotRegistration registration) {
        // Get presenter user to get email
        Optional<User> presenterUser = userRepository.findByBguUsernameIgnoreCase(registration.getPresenterUsername());
        if (presenterUser.isEmpty()) {
            logger.warn("Cannot send approval notification email: presenter user not found: {}", registration.getPresenterUsername());
            return;
        }

        User presenter = presenterUser.get();
        String presenterEmail = presenter.getEmail();
        if (presenterEmail == null || presenterEmail.trim().isEmpty()) {
            logger.warn("Cannot send approval notification email: presenter email is missing for user: {}", registration.getPresenterUsername());
            return;
        }

        // Get slot details
        SeminarSlot slot = slotRepository.findById(registration.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + registration.getSlotId()));

        // Format presenter name
        String presenterName = presenter.getFirstName() != null && presenter.getLastName() != null
                ? presenter.getFirstName() + " " + presenter.getLastName()
                : registration.getPresenterUsername();

        // Generate email content
        String subject = "SemScan: Your Seminar Slot Registration Has Been Approved";
        String htmlContent = generateApprovalNotificationEmailHtml(registration, slot, presenterName);

        // Send email
        boolean sent = mailService.sendHtmlEmail(presenterEmail, subject, htmlContent);
        if (sent) {
            logger.info("Approval notification email sent to presenter {} ({}) for registration slotId={}",
                    registration.getPresenterUsername(), presenterEmail, registration.getSlotId());
            databaseLoggerService.logBusinessEvent("REGISTRATION_APPROVAL_NOTIFICATION_SENT",
                    String.format("Approval notification email sent to presenter %s (%s) for registration slotId=%d",
                            registration.getPresenterUsername(), presenterEmail, registration.getSlotId()),
                    registration.getPresenterUsername());
        } else {
            logger.error("Failed to send approval notification email to presenter {} ({}) for registration slotId={}",
                    registration.getPresenterUsername(), presenterEmail, registration.getSlotId());
            databaseLoggerService.logError("EMAIL_REGISTRATION_APPROVAL_NOTIFICATION_EMAIL_FAILED",
                    String.format("Failed to send approval notification email to presenter %s (%s) for registration slotId=%d",
                            registration.getPresenterUsername(), presenterEmail, registration.getSlotId()),
                    null, registration.getPresenterUsername(),
                    String.format("slotId=%d,presenterEmail=%s", registration.getSlotId(), presenterEmail));
        }
    }

    /**
     * Generate HTML email content for approval notification to presenter
     */
    private String generateApprovalNotificationEmailHtml(SeminarSlotRegistration registration, SeminarSlot slot, String presenterName) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background-color: #f9f9f9; }
                    .success-box { background-color: #e8f5e9; padding: 15px; margin: 15px 0; border-left: 4px solid #4CAF50; }
                    .info-box { background-color: #e3f2fd; padding: 15px; margin: 15px 0; border-left: 4px solid #2196F3; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>SemScan - Registration Approved</h1>
                    </div>
                    <div class="content">
                        <p>Dear %s,</p>
                        
                        <div class="success-box">
                            <h3 style="margin-top: 0; color: #2e7d32;">✓ Your registration has been approved!</h3>
                            <p>Your supervisor has approved your seminar slot registration.</p>
                        </div>
                        
                        <div class="info-box">
                            <h3>Registration Details:</h3>
                            <p><strong>Slot Date:</strong> %s</p>
                            <p><strong>Time:</strong> %s - %s</p>
                            <p><strong>Location:</strong> %s, Room %s</p>
                            <p><strong>Topic:</strong> %s</p>
                            <p><strong>Degree:</strong> %s</p>
                        </div>
                        
                        <p>You are now registered for this seminar slot. Please make sure to attend on the scheduled date and time.</p>
                        
                        <p>If you have any questions, please contact your supervisor or the system administrator.</p>
                    </div>
                    <div class="footer">
                        <p>This is an automated message from SemScan Attendance System.</p>
                        <p>If you did not expect this email, please ignore it.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
            presenterName,
            slot.getSlotDate() != null ? DATE_FORMAT.format(slot.getSlotDate()) : "N/A",
            slot.getStartTime() != null ? TIME_FORMAT.format(slot.getStartTime()) : "N/A",
            slot.getEndTime() != null ? TIME_FORMAT.format(slot.getEndTime()) : "N/A",
            slot.getBuilding() != null ? slot.getBuilding() : "N/A",
            slot.getRoom() != null ? slot.getRoom() : "N/A",
            registration.getTopic() != null ? registration.getTopic() : "Not specified",
            registration.getDegree() != null ? registration.getDegree().toString() : "N/A"
        );
    }
}

