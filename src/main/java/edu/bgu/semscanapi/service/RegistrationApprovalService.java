package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.entity.ApprovalStatus;
import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.SeminarSlotRegistrationId;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.entity.WaitingListPromotion;
import edu.bgu.semscanapi.entity.EmailQueue;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.repository.WaitingListPromotionRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.EmailQueueService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RegistrationApprovalService {

    private static final Logger logger = LoggerUtil.getLogger(RegistrationApprovalService.class);

    private final SeminarSlotRegistrationRepository registrationRepository;
    private final SeminarSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final EmailQueueService emailQueueService;
    private final GlobalConfig globalConfig;
    private final DatabaseLoggerService databaseLoggerService;
    private final PresenterHomeService presenterHomeService; // For promoting next person when student declines
    private final WaitingListPromotionRepository waitingListPromotionRepository;
    private final WaitingListService waitingListService; // For removing user from waiting lists on approval
    private final AppConfigService appConfigService;
    private final FcmService fcmService;

    // Self-injection to enable @Transactional on saveApprovalToken when called from within the class
    @Autowired
    @Lazy
    private RegistrationApprovalService self;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public RegistrationApprovalService(
            SeminarSlotRegistrationRepository registrationRepository,
            SeminarSlotRepository slotRepository,
            UserRepository userRepository,
            MailService mailService,
            EmailQueueService emailQueueService,
            GlobalConfig globalConfig,
            DatabaseLoggerService databaseLoggerService,
            @Lazy PresenterHomeService presenterHomeService,
            WaitingListPromotionRepository waitingListPromotionRepository,
            @Lazy WaitingListService waitingListService,
            AppConfigService appConfigService,
            FcmService fcmService) {
        this.registrationRepository = registrationRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.emailQueueService = emailQueueService;
        this.globalConfig = globalConfig;
        this.databaseLoggerService = databaseLoggerService;
        this.presenterHomeService = presenterHomeService;
        this.waitingListPromotionRepository = waitingListPromotionRepository;
        this.waitingListService = waitingListService;
        this.appConfigService = appConfigService;
        this.fcmService = fcmService;
    }

    /**
     * Send student confirmation email for waiting list promotion
     * Student must confirm before supervisor approval email is sent
     */
    @Transactional
    public void sendStudentConfirmationEmail(SeminarSlotRegistration registration) {
        logger.info("📧 [RegistrationApprovalService] sendStudentConfirmationEmail() called - slotId: {}, presenter: {}",
                registration.getSlotId(), registration.getPresenterUsername());
        databaseLoggerService.logAction("INFO", "EMAIL_SEND_STUDENT_CONFIRMATION_EMAIL_CALLED",
                String.format("sendStudentConfirmationEmail() called for slotId=%d, presenter=%s", registration.getSlotId(), registration.getPresenterUsername()),
                registration.getPresenterUsername(), String.format("slotId=%d", registration.getSlotId()));
        
        // Get student user to get email
        Optional<User> studentUser = userRepository.findByBguUsernameIgnoreCase(registration.getPresenterUsername());
        if (studentUser.isEmpty()) {
            String errorMsg = String.format("Cannot send student confirmation email: student user not found: %s", registration.getPresenterUsername());
            logger.warn("📧 {}", errorMsg);
            databaseLoggerService.logError("EMAIL_STUDENT_CONFIRMATION_EMAIL_USER_NOT_FOUND",
                    errorMsg, null, registration.getPresenterUsername(),
                    String.format("slotId=%d,presenter=%s", registration.getSlotId(), registration.getPresenterUsername()));
            return;
        }
        
        User student = studentUser.get();
        String studentEmail = student.getEmail();
        if (studentEmail == null || studentEmail.trim().isEmpty()) {
            String errorMsg = String.format("Cannot send student confirmation email: student email is missing for user: %s", registration.getPresenterUsername());
            logger.warn("📧 {}", errorMsg);
            databaseLoggerService.logError("EMAIL_STUDENT_CONFIRMATION_EMAIL_MISSING_EMAIL",
                    errorMsg, null, registration.getPresenterUsername(),
                    String.format("slotId=%d,presenter=%s", registration.getSlotId(), registration.getPresenterUsername()));
            return;
        }
        
        logger.info("📧 [RegistrationApprovalService] Student email found: {}", studentEmail);
        databaseLoggerService.logAction("INFO", "EMAIL_STUDENT_EMAIL_VALIDATED",
                String.format("Student email validated: %s", studentEmail),
                registration.getPresenterUsername(), String.format("slotId=%d,studentEmail=%s", registration.getSlotId(), studentEmail));
        
        // Generate student confirmation token (expires in configured days)
        int expiryDays = appConfigService.getIntegerConfig("approval_token_expiry_days", 14);
        String confirmationToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(expiryDays);
        logger.info("📧 [RegistrationApprovalService] Generated student confirmation token: {} (expires: {})", confirmationToken, expiresAt);
        databaseLoggerService.logAction("INFO", "EMAIL_STUDENT_CONFIRMATION_TOKEN_GENERATED",
                String.format("Generated student confirmation token (expires: %s)", expiresAt),
                registration.getPresenterUsername(), String.format("slotId=%d,tokenExpiresAt=%s", registration.getSlotId(), expiresAt));
        
        // Store confirmation token (this will be used for student confirmation, then replaced with supervisor token)
        registration.setApprovalToken(confirmationToken);
        registration.setApprovalTokenExpiresAt(expiresAt);
        registration.setApprovalStatus(ApprovalStatus.PENDING);
        registrationRepository.save(registration);
        logger.info("📧 [RegistrationApprovalService] Registration saved with student confirmation token");
        databaseLoggerService.logAction("INFO", "EMAIL_REGISTRATION_STUDENT_CONFIRMATION_TOKEN_SAVED",
                String.format("Registration saved with student confirmation token"),
                registration.getPresenterUsername(), String.format("slotId=%d", registration.getSlotId()));
        
        // Retrieve slot information
        SeminarSlot slot = slotRepository.findById(registration.getSlotId())
                .orElseThrow(() -> {
                    String errorMsg = "Slot not found: " + registration.getSlotId();
                    logger.error("📧 {}", errorMsg);
                    databaseLoggerService.logError("EMAIL_SLOT_NOT_FOUND_STUDENT_CONFIRMATION",
                            errorMsg, null, registration.getPresenterUsername(),
                            String.format("slotId=%d", registration.getSlotId()));
                    return new IllegalArgumentException(errorMsg);
                });
        
        // Generate student confirmation URLs
        String baseUrl = globalConfig.getApiBaseUrl();
        String confirmUrl = baseUrl + "/student-confirm/" + confirmationToken;
        String declineUrl = baseUrl + "/student-decline/" + confirmationToken;
        logger.info("📧 [RegistrationApprovalService] Generated URLs - confirm: {}, decline: {}", confirmUrl, declineUrl);
        
        // Generate HTML email for student confirmation
        String subject = "SemScan: Confirm Your Waiting List Promotion";
        logger.info("📧 [RegistrationApprovalService] Generating student confirmation HTML email content...");
        String htmlContent = generateStudentConfirmationEmailHtml(registration, slot, confirmUrl, declineUrl, expiresAt, student);
        logger.info("📧 [RegistrationApprovalService] Student confirmation HTML email content generated (length: {} chars)", htmlContent.length());
        
        // Queue email to student (with retry support)
        logger.info("📧 [RegistrationApprovalService] Queueing student confirmation email - to: {}, subject: {}", studentEmail, subject);
        emailQueueService.queueEmail(
            EmailQueue.EmailType.STUDENT_CONFIRMATION,
            studentEmail,
            subject,
            htmlContent,
            null,
            registration.getSlotId(),
            registration.getPresenterUsername()
        );

        logger.info("📧 [RegistrationApprovalService] Student confirmation email queued for {} for registration slotId={}, presenter={}",
                studentEmail, registration.getSlotId(), registration.getPresenterUsername());
        databaseLoggerService.logBusinessEvent("EMAIL_STUDENT_CONFIRMATION_EMAIL_QUEUED",
                String.format("Student confirmation email queued for %s for registration slotId=%d, presenter=%s",
                        studentEmail, registration.getSlotId(), registration.getPresenterUsername()),
                registration.getPresenterUsername());
    }
    
    /**
     * Student confirms promotion - proceed to send supervisor approval email
     */
    @Transactional
    public void confirmStudentPromotion(String confirmationToken) {
        logger.info("📧 [RegistrationApprovalService] confirmStudentPromotion() called with token: {}", 
                confirmationToken.substring(0, Math.min(8, confirmationToken.length())) + "...");
        
        // Find registration by confirmation token
        SeminarSlotRegistration registration = registrationRepository.findByApprovalToken(confirmationToken)
                .orElseThrow(() -> {
                    String errorMsg = "Registration not found for confirmation token";
                    logger.error("📧 {}", errorMsg);
                    databaseLoggerService.logError("EMAIL_STUDENT_CONFIRMATION_INVALID_TOKEN",
                            errorMsg, null, null, String.format("token=%s", confirmationToken.substring(0, Math.min(8, confirmationToken.length()))));
                    return new IllegalArgumentException("Invalid confirmation token");
                });
        
        // Verify token hasn't expired
        if (registration.getApprovalTokenExpiresAt() != null && 
            LocalDateTime.now().isAfter(registration.getApprovalTokenExpiresAt())) {
            registration.setApprovalStatus(ApprovalStatus.EXPIRED);
            registrationRepository.save(registration);
            databaseLoggerService.logBusinessEvent("REGISTRATION_STUDENT_CONFIRMATION_EXPIRED",
                    String.format("Student confirmation expired for slotId=%d, presenter=%s", 
                            registration.getSlotId(), registration.getPresenterUsername()),
                    registration.getPresenterUsername());
            throw new IllegalStateException("Confirmation token has expired");
        }
        
        // Verify registration is still PENDING
        if (registration.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Registration is not pending student confirmation");
        }
        
        logger.info("📧 Student {} confirmed promotion for slotId={}, proceeding to send supervisor approval email",
                registration.getPresenterUsername(), registration.getSlotId());
        databaseLoggerService.logBusinessEvent("WAITING_LIST_STUDENT_CONFIRMED_PROMOTION",
                String.format("Student %s confirmed promotion for slotId=%d, sending supervisor approval email",
                        registration.getPresenterUsername(), registration.getSlotId()),
                registration.getPresenterUsername());
        
        // Clear student confirmation token and send supervisor approval email
        // sendApprovalEmail will generate a new token for supervisor
        registration.setApprovalToken(null);
        registration.setApprovalTokenExpiresAt(null);
        registrationRepository.save(registration);
        
        // Now send supervisor approval email
        boolean emailSent = sendApprovalEmail(registration);
        if (!emailSent) {
            logger.error("📧 Failed to send supervisor approval email after student confirmation for slotId={}, presenter={}",
                    registration.getSlotId(), registration.getPresenterUsername());
            databaseLoggerService.logError("EMAIL_STUDENT_CONFIRMATION_APPROVAL_EMAIL_FAILED",
                    String.format("Failed to send supervisor approval email after student confirmation for slotId=%d, presenter=%s",
                            registration.getSlotId(), registration.getPresenterUsername()),
                    null, registration.getPresenterUsername(),
                    String.format("slotId=%d", registration.getSlotId()));
        }
    }
    
    /**
     * Student declines promotion - cancel the registration
     * Note: After cancellation, the next person on waiting list should be promoted
     * (This is handled by PresenterHomeService.cancelRegistration which calls promoteFromWaitingListAfterCancellation)
     */
    @Transactional
    public void declineStudentPromotion(String confirmationToken) {
        logger.info("📧 [RegistrationApprovalService] declineStudentPromotion() called with token: {}", 
                confirmationToken.substring(0, Math.min(8, confirmationToken.length())) + "...");
        
        // Find registration by confirmation token
        SeminarSlotRegistration registration = registrationRepository.findByApprovalToken(confirmationToken)
                .orElseThrow(() -> {
                    String errorMsg = "Registration not found for confirmation token";
                    logger.error("📧 {}", errorMsg);
                    databaseLoggerService.logError("EMAIL_STUDENT_DECLINE_INVALID_TOKEN",
                            errorMsg, null, null, String.format("token=%s", confirmationToken.substring(0, Math.min(8, confirmationToken.length()))));
                    return new IllegalArgumentException("Invalid confirmation token");
                });
        
        // Verify token hasn't expired
        if (registration.getApprovalTokenExpiresAt() != null && 
            LocalDateTime.now().isAfter(registration.getApprovalTokenExpiresAt())) {
            registration.setApprovalStatus(ApprovalStatus.EXPIRED);
            registrationRepository.save(registration);
            throw new IllegalStateException("Confirmation token has expired");
        }
        
        // Verify registration is still PENDING
        if (registration.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Registration is not pending student confirmation");
        }
        
        Long slotId = registration.getSlotId();
        String presenterUsername = registration.getPresenterUsername();
        
        // Get slot information before deleting registration (needed for promotion)
        SeminarSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> {
                    String errorMsg = "Slot not found: " + slotId;
                    logger.error("📧 {}", errorMsg);
                    databaseLoggerService.logError("EMAIL_STUDENT_DECLINE_SLOT_NOT_FOUND",
                            errorMsg, null, presenterUsername, String.format("slotId=%d", slotId));
                    return new IllegalArgumentException(errorMsg);
                });
        
        // Update WaitingListPromotion status to DECLINED before deleting registration
        updateWaitingListPromotionStatus(slotId, presenterUsername, WaitingListPromotion.PromotionStatus.DECLINED);
        
        // Delete the registration (cancellation)
        registrationRepository.delete(registration);
        
        logger.info("📧 Student {} declined promotion for slotId={}, registration deleted",
                presenterUsername, slotId);
        databaseLoggerService.logBusinessEvent("WAITING_LIST_STUDENT_DECLINED_PROMOTION",
                String.format("Student %s declined promotion for slotId=%d, registration deleted. Attempting to promote next person from waiting list.",
                        presenterUsername, slotId),
                presenterUsername);
        
        // Automatically promote next person from waiting list (outside transaction to avoid holding locks)
        try {
            Optional<edu.bgu.semscanapi.entity.WaitingListEntry> promotedEntry = 
                    presenterHomeService.promoteFromWaitingListAfterCancellation(slotId, slot);
            if (promotedEntry.isPresent()) {
                logger.info("📧 Successfully promoted next person {} from waiting list for slot {} after student declined",
                        promotedEntry.get().getPresenterUsername(), slotId);
                databaseLoggerService.logBusinessEvent("WAITING_LIST_AUTO_PROMOTED_AFTER_DECLINE",
                        String.format("Next person %s automatically promoted from waiting list for slotId=%d after previous student declined",
                                promotedEntry.get().getPresenterUsername(), slotId),
                        promotedEntry.get().getPresenterUsername());
            } else {
                logger.info("📧 No one to promote from waiting list for slot {} (waiting list empty or slot full)", slotId);
            }
        } catch (Exception e) {
            logger.error("📧 Failed to promote next person from waiting list for slot {} after student declined: {}", 
                    slotId, e.getMessage(), e);
            databaseLoggerService.logError("WAITING_LIST_AUTO_PROMOTE_FAILED",
                    String.format("Failed to automatically promote next person from waiting list for slotId=%d after student declined: %s",
                            slotId, e.getMessage()),
                    e, presenterUsername, String.format("slotId=%d", slotId));
            // Don't fail the decline operation if promotion fails
        }
    }
    
    /**
     * Generate HTML email content for student confirmation (waiting list promotion)
     */
    private String generateStudentConfirmationEmailHtml(SeminarSlotRegistration registration, SeminarSlot slot,
                                                        String confirmUrl, String declineUrl, LocalDateTime expiresAt, User student) {
        String studentName = student.getFirstName() != null && student.getLastName() != null
                ? student.getFirstName() + " " + student.getLastName()
                : registration.getPresenterUsername();
        
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0;">
                <table width="100%%" cellpadding="0" cellspacing="0" border="0">
                    <tr>
                        <td align="center">
                            <table width="600" cellpadding="0" cellspacing="0" border="0" style="max-width: 600px;">
                                <!-- Header -->
                                <tr>
                                    <td style="background-color: #2196F3; color: white; padding: 20px; text-align: center;">
                                        <h1 style="margin: 0; font-size: 24px;">SemScan - Slot Available!</h1>
                                    </td>
                                </tr>
                                <!-- Content -->
                                <tr>
                                    <td style="padding: 20px; background-color: #f9f9f9;">
                                        <p>Dear %s,</p>
                                        <p>Good news! A slot has become available and you've been promoted from the waiting list.</p>

                                        <!-- Info Box -->
                                        <table width="100%%" cellpadding="15" cellspacing="0" border="0" style="background-color: #e3f2fd; margin: 15px 0; border-left: 4px solid #2196F3;">
                                            <tr>
                                                <td>
                                                    <h3 style="margin-top: 0;">Slot Details:</h3>
                                                    <p style="margin: 5px 0;"><strong>Date:</strong> %s</p>
                                                    <p style="margin: 5px 0;"><strong>Time:</strong> %s - %s</p>
                                                    <p style="margin: 5px 0;"><strong>Location:</strong> %s, Room %s</p>
                                                    <p style="margin: 5px 0;"><strong>Topic:</strong> %s</p>
                                                    <p style="margin: 5px 0;"><strong>Degree:</strong> %s</p>
                                                </td>
                                            </tr>
                                        </table>

                                        <!-- Warning Box -->
                                        <table width="100%%" cellpadding="15" cellspacing="0" border="0" style="background-color: #fff3cd; margin: 15px 0; border-left: 4px solid #ffc107;">
                                            <tr>
                                                <td>
                                                    <h3 style="margin-top: 0; color: #856404;">Action Required</h3>
                                                    <p style="margin: 5px 0;">To proceed with this registration, you need to confirm. After your confirmation, your supervisor will receive an approval request email.</p>
                                                    <p style="margin: 5px 0;"><strong>If you confirm:</strong> Your supervisor will be notified to approve your registration.</p>
                                                    <p style="margin: 5px 0;"><strong>If you decline:</strong> Your registration will be cancelled and the slot will be offered to the next person on the waiting list.</p>
                                                </td>
                                            </tr>
                                        </table>

                                        <p>Please choose one of the following options:</p>

                                        <!-- Buttons -->
                                        <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="margin: 30px 0;">
                                            <tr>
                                                <td align="center">
                                                    <table cellpadding="0" cellspacing="0" border="0">
                                                        <tr>
                                                            <td style="padding: 0 10px;">
                                                                <a href="%s" style="display: inline-block; padding: 14px 28px; background-color: #4CAF50; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 14px;">Yes, I Want This Slot</a>
                                                            </td>
                                                            <td style="padding: 0 10px;">
                                                                <a href="%s" style="display: inline-block; padding: 14px 28px; background-color: #f44336; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 14px;">No, Decline</a>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="color: #666; font-size: 12px;">
                                            <strong>Note:</strong> This confirmation link will expire on %s.
                                        </p>
                                    </td>
                                </tr>
                                <!-- Footer -->
                                <tr>
                                    <td style="text-align: center; padding: 20px; color: #666; font-size: 12px;">
                                        <p style="margin: 5px 0;">This is an automated message from SemScan Attendance System.</p>
                                        <p style="margin: 5px 0;">If you did not expect this email, please ignore it.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """,
            studentName,
            slot.getSlotDate() != null ? DATE_FORMAT.format(slot.getSlotDate()) : "N/A",
            slot.getStartTime() != null ? TIME_FORMAT.format(slot.getStartTime()) : "N/A",
            slot.getEndTime() != null ? TIME_FORMAT.format(slot.getEndTime()) : "N/A",
            slot.getBuilding() != null ? slot.getBuilding() : "N/A",
            slot.getRoom() != null ? slot.getRoom() : "N/A",
            registration.getTopic() != null ? registration.getTopic() : "Not specified",
            registration.getDegree() != null ? registration.getDegree().toString() : "N/A",
            confirmUrl,
            declineUrl,
            expiresAt.toString()
        );
    }

    /**
     * Generate approval token and send approval email to supervisor
     */
    // NOTE: NOT @Transactional - email sending should be outside transaction to avoid holding DB locks during SMTP operations
    public boolean sendApprovalEmail(SeminarSlotRegistration registration) {
        logger.info("📧 [RegistrationApprovalService] sendApprovalEmail() called - slotId: {}, presenter: {}",
                registration.getSlotId(), registration.getPresenterUsername());
        databaseLoggerService.logAction("INFO", "EMAIL_SEND_APPROVAL_EMAIL_CALLED",
                String.format("sendApprovalEmail() called for slotId=%d, presenter=%s", registration.getSlotId(), registration.getPresenterUsername()),
                registration.getPresenterUsername(), String.format("slotId=%d", registration.getSlotId()));
        
        if (registration.getSupervisorEmail() == null || registration.getSupervisorEmail().trim().isEmpty()) {
            String errorMsg = String.format("Cannot send approval email: supervisor email is missing for registration slotId=%d, presenter=%s",
                    registration.getSlotId(), registration.getPresenterUsername());
            logger.warn("📧 {}", errorMsg);
            databaseLoggerService.logError("EMAIL_REGISTRATION_APPROVAL_EMAIL_MISSING_SUPERVISOR",
                    errorMsg, null, registration.getPresenterUsername(),
                    String.format("slotId=%d,presenter=%s", registration.getSlotId(), registration.getPresenterUsername()));
            return false;
        }

        logger.info("📧 [RegistrationApprovalService] Supervisor email found: {}", registration.getSupervisorEmail());
        databaseLoggerService.logAction("INFO", "EMAIL_SUPERVISOR_EMAIL_VALIDATED",
                String.format("Supervisor email validated: %s", registration.getSupervisorEmail()),
                registration.getPresenterUsername(), String.format("slotId=%d,supervisorEmail=%s", registration.getSlotId(), registration.getSupervisorEmail()));

        // Generate cryptographically secure UUID token for email approval link (expires in configured days)
        int expiryDays = appConfigService.getIntegerConfig("approval_token_expiry_days", 14);
        String approvalToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(expiryDays);
        logger.info("📧 [RegistrationApprovalService] Generated approval token: {} (expires: {})", approvalToken, expiresAt);
        databaseLoggerService.logAction("INFO", "EMAIL_APPROVAL_TOKEN_GENERATED",
                String.format("Generated approval token (expires: %s)", expiresAt),
                registration.getPresenterUsername(), String.format("slotId=%d,tokenExpiresAt=%s", registration.getSlotId(), expiresAt));

        // Save token in a separate transaction (quick DB operation, not holding locks during email send)
        // Use self-injection to call through Spring proxy so @Transactional works
        self.saveApprovalToken(registration, approvalToken, expiresAt);
        
        // CRITICAL: Refresh registration from database to get the actual saved token
        // This ensures we use the token that's actually in the database, not a stale in-memory value
        SeminarSlotRegistration refreshedRegistration = registrationRepository
                .findById(new SeminarSlotRegistrationId(registration.getSlotId(), registration.getPresenterUsername()))
                .orElseThrow(() -> {
                    String errorMsg = String.format("Registration not found after token save - slotId=%d, presenter=%s", 
                            registration.getSlotId(), registration.getPresenterUsername());
                    logger.error("📧 {}", errorMsg);
                    IllegalArgumentException exception = new IllegalArgumentException(errorMsg);
                    databaseLoggerService.logError("EMAIL_REGISTRATION_NOT_FOUND_AFTER_TOKEN_SAVE",
                            errorMsg, exception, registration.getPresenterUsername(),
                            String.format("slotId=%d,presenter=%s", registration.getSlotId(), registration.getPresenterUsername()));
                    return exception;
                });
        
        // Use the token from the refreshed registration (the one actually saved in database)
        String actualToken = refreshedRegistration.getApprovalToken();
        if (actualToken == null || !actualToken.equals(approvalToken)) {
            String errorMsg = String.format("Token mismatch after save - generated: %s, saved: %s, slotId=%d, presenter=%s",
                    approvalToken != null ? approvalToken.substring(0, Math.min(8, approvalToken.length())) : "null",
                    actualToken != null ? actualToken.substring(0, Math.min(8, actualToken.length())) : "null",
                    registration.getSlotId(), registration.getPresenterUsername());
            logger.error("📧 {}", errorMsg);
            databaseLoggerService.logError("EMAIL_TOKEN_MISMATCH_AFTER_SAVE",
                    errorMsg, null, registration.getPresenterUsername(),
                    String.format("slotId=%d,generatedTokenPrefix=%s,savedTokenPrefix=%s", 
                            registration.getSlotId(), 
                            approvalToken != null ? approvalToken.substring(0, Math.min(8, approvalToken.length())) : "null",
                            actualToken != null ? actualToken.substring(0, Math.min(8, actualToken.length())) : "null"));
            // Use the actual token from database, not the generated one
            approvalToken = actualToken;
        }
        
        logger.info("📧 [RegistrationApprovalService] Registration saved with approval token - verified in database. Token: {} (first 8 chars: {})", 
                approvalToken, approvalToken.substring(0, Math.min(8, approvalToken.length())));
        databaseLoggerService.logAction("INFO", "EMAIL_REGISTRATION_TOKEN_SAVED",
                String.format("Registration saved with approval token (verified)"),
                registration.getPresenterUsername(), 
                String.format("slotId=%d,tokenPrefix=%s", registration.getSlotId(), approvalToken.substring(0, Math.min(8, approvalToken.length()))));

        // Retrieve slot information (date, time, location) to include in approval email
        SeminarSlot slot = slotRepository.findById(registration.getSlotId())
                .orElseThrow(() -> {
                    String errorMsg = "Slot not found: " + registration.getSlotId();
                    logger.error("📧 {}", errorMsg);
                    databaseLoggerService.logError("EMAIL_SLOT_NOT_FOUND_EMAIL",
                            errorMsg, null, registration.getPresenterUsername(),
                            String.format("slotId=%d", registration.getSlotId()));
                    return new IllegalArgumentException(errorMsg);
                });
        logger.info("📧 [RegistrationApprovalService] Slot retrieved: slotId={}, date={}, time={}-{}",
                slot.getSlotId(), slot.getSlotDate(), slot.getStartTime(), slot.getEndTime());

        // Generate mobile-compatible approval/decline URLs using token-based authentication
        // CRITICAL: Use the actual token from database (approvalToken variable now contains the saved token)
        String baseUrl = globalConfig.getApiBaseUrl();
        String approveUrl = baseUrl + "/approve/" + approvalToken;
        String declineUrl = baseUrl + "/decline/" + approvalToken;
        logger.info("📧 [RegistrationApprovalService] Generated URLs - approve: {}, decline: {} (using token: {}...)", 
                approveUrl, declineUrl, approvalToken.substring(0, Math.min(8, approvalToken.length())));
        
        // Get student user to resolve full name
        String studentFullName = registration.getPresenterUsername(); // Default to username
        Optional<User> studentUser = userRepository.findByBguUsernameIgnoreCase(registration.getPresenterUsername());
        if (studentUser.isPresent()) {
            User student = studentUser.get();
            if (student.getFirstName() != null && student.getLastName() != null) {
                studentFullName = student.getFirstName() + " " + student.getLastName();
            } else if (student.getFirstName() != null) {
                studentFullName = student.getFirstName();
            }
        }

        // Generate HTML email with slot details, student info, and clickable approve/decline buttons
        String subject = "SemScan: Approval Required for Seminar Slot Registration";
        logger.info("📧 [RegistrationApprovalService] Generating HTML email content...");
        String htmlContent = generateApprovalEmailHtml(registration, slot, approveUrl, declineUrl, expiresAt, studentFullName);
        logger.info("📧 [RegistrationApprovalService] HTML email content generated (length: {} chars)", htmlContent.length());
        databaseLoggerService.logAction("INFO", "EMAIL_CONTENT_GENERATED",
                String.format("HTML email content generated (length: %d chars)", htmlContent.length()),
                registration.getPresenterUsername(), String.format("slotId=%d,contentLength=%d", registration.getSlotId(), htmlContent.length()));
        
        // Queue email to supervisor (with retry support)
        logger.info("📧 [RegistrationApprovalService] Queueing supervisor approval email - to: {}, subject: {}",
                registration.getSupervisorEmail(), subject);
        databaseLoggerService.logAction("INFO", "EMAIL_QUEUEING_SUPERVISOR_APPROVAL",
                String.format("Queueing supervisor approval email to: %s", registration.getSupervisorEmail()),
                registration.getPresenterUsername(), String.format("slotId=%d,supervisorEmail=%s", registration.getSlotId(), registration.getSupervisorEmail()));

        try {
            emailQueueService.queueEmail(
                EmailQueue.EmailType.SUPERVISOR_APPROVAL,
                registration.getSupervisorEmail(),
                subject,
                htmlContent,
                null,
                registration.getSlotId(),
                registration.getPresenterUsername()
            );

            logger.info("📧 [RegistrationApprovalService] Supervisor approval email queued for {} for registration slotId={}, presenter={}",
                    registration.getSupervisorEmail(), registration.getSlotId(), registration.getPresenterUsername());
            databaseLoggerService.logBusinessEvent("EMAIL_REGISTRATION_APPROVAL_EMAIL_QUEUED",
                    String.format("Supervisor approval email queued for %s for registration slotId=%d, presenter=%s",
                            registration.getSupervisorEmail(), registration.getSlotId(), registration.getPresenterUsername()),
                    registration.getPresenterUsername());

            logger.info("📧 [RegistrationApprovalService] sendApprovalEmail() completed - slotId: {}, presenter: {}, queued: true",
                    registration.getSlotId(), registration.getPresenterUsername());

            return true;
        } catch (Exception e) {
            logger.error("📧 [RegistrationApprovalService] Exception while queueing approval email to supervisor {} for registration slotId={}, presenter={}: {}",
                    registration.getSupervisorEmail(), registration.getSlotId(), registration.getPresenterUsername(), e.getMessage(), e);
            databaseLoggerService.logError("EMAIL_REGISTRATION_APPROVAL_EMAIL_QUEUE_EXCEPTION",
                    String.format("Exception while queueing approval email to supervisor %s for registration slotId=%d, presenter=%s: %s",
                            registration.getSupervisorEmail(), registration.getSlotId(), registration.getPresenterUsername(), e.getMessage()),
                    e, registration.getPresenterUsername(),
                    String.format("slotId=%d,supervisorEmail=%s,exception=%s", registration.getSlotId(), registration.getSupervisorEmail(), e.getClass().getName()));
            return false;
        }
    }

    /**
     * Save approval token to database in a separate transaction
     * This is a quick DB operation that commits immediately, before email sending
     * Public method so Spring AOP can proxy it for @Transactional
     */
    @Transactional
    public void saveApprovalToken(SeminarSlotRegistration registration, String approvalToken, LocalDateTime expiresAt) {
        registration.setApprovalToken(approvalToken);
        registration.setApprovalTokenExpiresAt(expiresAt);
        registration.setApprovalStatus(ApprovalStatus.PENDING);
        registrationRepository.save(registration);
        
        // CRITICAL: Verify token was actually saved by re-fetching from database
        SeminarSlotRegistration savedRegistration = registrationRepository
                .findById(new SeminarSlotRegistrationId(registration.getSlotId(), registration.getPresenterUsername()))
                .orElseThrow(() -> {
                    String errorMsg = String.format("Registration not found after save - slotId=%d, presenter=%s", 
                            registration.getSlotId(), registration.getPresenterUsername());
                    logger.error("📧 {}", errorMsg);
                    IllegalArgumentException exception = new IllegalArgumentException(errorMsg);
                    databaseLoggerService.logError("EMAIL_REGISTRATION_NOT_FOUND_AFTER_SAVE",
                            errorMsg, exception, registration.getPresenterUsername(),
                            String.format("slotId=%d,presenter=%s", registration.getSlotId(), registration.getPresenterUsername()));
                    return exception;
                });
        
        // Verify token was saved
        if (savedRegistration.getApprovalToken() == null || !savedRegistration.getApprovalToken().equals(approvalToken)) {
            String errorMsg = String.format("Token was not saved correctly - expected: %s, actual: %s, slotId=%d, presenter=%s",
                    approvalToken, savedRegistration.getApprovalToken(), registration.getSlotId(), registration.getPresenterUsername());
            logger.error("📧 {}", errorMsg);
            IllegalArgumentException exception = new IllegalArgumentException(errorMsg);
            databaseLoggerService.logError("EMAIL_REGISTRATION_TOKEN_NOT_SAVED",
                    errorMsg, exception, registration.getPresenterUsername(),
                    String.format("slotId=%d,presenter=%s,expectedToken=%s,actualToken=%s", 
                            registration.getSlotId(), registration.getPresenterUsername(), approvalToken, savedRegistration.getApprovalToken()));
            throw exception;
        }
        
        logger.info("📧 [RegistrationApprovalService] Approval token saved and verified - slotId: {}, presenter: {}, token: {} (first 8 chars)", 
                registration.getSlotId(), registration.getPresenterUsername(), 
                approvalToken.substring(0, Math.min(8, approvalToken.length())));
        
        // CRITICAL: Verify token can be found by findByApprovalToken query (ensures transaction committed)
        Optional<SeminarSlotRegistration> tokenLookup = registrationRepository.findByApprovalToken(approvalToken);
        if (tokenLookup.isPresent()) {
            logger.info("📧 [RegistrationApprovalService] Token lookup SUCCESS - token can be found in database immediately after save");
            databaseLoggerService.logAction("INFO", "EMAIL_TOKEN_LOOKUP_VERIFIED",
                    "Token lookup verified immediately after save",
                    registration.getPresenterUsername(),
                    String.format("slotId=%d,tokenPrefix=%s", registration.getSlotId(), approvalToken.substring(0, Math.min(8, approvalToken.length()))));
        } else {
            logger.error("📧 [RegistrationApprovalService] Token lookup FAILED - token cannot be found immediately after save! This indicates transaction not committed.");
            databaseLoggerService.logError("EMAIL_TOKEN_LOOKUP_FAILED",
                    "Token cannot be found immediately after save - transaction may not have committed",
                    null, registration.getPresenterUsername(),
                    String.format("slotId=%d,tokenPrefix=%s", registration.getSlotId(), approvalToken.substring(0, Math.min(8, approvalToken.length()))));
        }
    }

    /**
     * Approve registration via token
     */
    @Transactional
    public void approveRegistration(Long slotId, String presenterUsername, String token) {
        // Log approval attempt
        databaseLoggerService.logAction("INFO", "APPROVAL_ATTEMPT",
                String.format("Approval attempt for slotId=%d, presenter=%s", slotId, presenterUsername),
                presenterUsername,
                String.format("slotId=%d,presenterUsername=%s,tokenProvided=%s", slotId, presenterUsername, token != null));

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
            throw new IllegalStateException("This approval link has expired. Please ask the student to register again.");
        }

        // Handle different approval statuses
        if (registration.getApprovalStatus() == ApprovalStatus.APPROVED) {
            // Already approved - idempotent operation, return successfully
            logger.info("Registration already approved for slotId={}, presenter={} - returning success", slotId, presenterUsername);
            databaseLoggerService.logAction("INFO", "REGISTRATION_ALREADY_APPROVED",
                    String.format("Registration already approved for slotId=%d, presenter=%s (idempotent)", slotId, presenterUsername),
                    presenterUsername, String.format("slotId=%d", slotId));
            return; // Success - already approved
        }
        
        if (registration.getApprovalStatus() != ApprovalStatus.PENDING) {
            // DECLINED or EXPIRED - cannot approve
            String errorMsg = String.format("Cannot approve registration: status is %s (only PENDING registrations can be approved)", 
                    registration.getApprovalStatus());
            logger.error("📧 {}", errorMsg);
            databaseLoggerService.logError("REGISTRATION_APPROVAL_INVALID_STATUS",
                    errorMsg, null, presenterUsername, String.format("slotId=%d,status=%s", slotId, registration.getApprovalStatus()));
            throw new IllegalStateException("Registration is not pending approval");
        }

        // Update registration status to APPROVED and record supervisor approval timestamp
        registration.setApprovalStatus(ApprovalStatus.APPROVED);
        registration.setSupervisorApprovedAt(LocalDateTime.now());
        registrationRepository.save(registration);
        
        // Update WaitingListPromotion status to APPROVED if exists
        updateWaitingListPromotionStatus(slotId, presenterUsername, WaitingListPromotion.PromotionStatus.APPROVED);

        logger.info("Registration approved for slotId={}, presenter={}", slotId, presenterUsername);
        databaseLoggerService.logBusinessEvent("REGISTRATION_APPROVED",
                String.format("Registration approved for slotId=%d, presenter=%s", slotId, presenterUsername),
                presenterUsername);

        // BUSINESS RULE: Users can only have 1 approved registration at a time
        // Automatically cancel all other pending registrations for this user
        List<SeminarSlotRegistration> allUserRegistrations = registrationRepository.findByIdPresenterUsername(presenterUsername);
        List<SeminarSlotRegistration> otherPendingRegistrations = allUserRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.PENDING)
                .filter(reg -> !reg.getSlotId().equals(slotId)) // Exclude the one just approved
                .toList();
        
        if (!otherPendingRegistrations.isEmpty()) {
            logger.info("User {} approved for slotId={}. Cancelling {} other pending registration(s) due to 'one approved registration' rule",
                    presenterUsername, slotId, otherPendingRegistrations.size());
            
            for (SeminarSlotRegistration pendingReg : otherPendingRegistrations) {
                registrationRepository.delete(pendingReg);
                logger.info("Cancelled pending registration for user {} in slotId={} (user approved for slotId={})",
                        presenterUsername, pendingReg.getSlotId(), slotId);
                databaseLoggerService.logBusinessEvent("REGISTRATION_AUTO_CANCELLED_ON_APPROVAL",
                        String.format("Pending registration auto-cancelled: user %s, slotId=%d (user approved for slotId=%d)",
                                presenterUsername, pendingReg.getSlotId(), slotId),
                        presenterUsername);
            }
            
            databaseLoggerService.logBusinessEvent("REGISTRATION_MULTIPLE_PENDING_CANCELLED",
                    String.format("User %s approved for slotId=%d. Automatically cancelled %d other pending registration(s)",
                            presenterUsername, slotId, otherPendingRegistrations.size()),
                    presenterUsername);
        }

        // BUSINESS RULE: Users can only have 1 approved registration at a time
        // Automatically remove user from all waiting lists when approved
        try {
            Optional<edu.bgu.semscanapi.entity.WaitingListEntry> waitingListEntry =
                    waitingListService.getWaitingListEntryForUser(presenterUsername);
            if (waitingListEntry.isPresent()) {
                edu.bgu.semscanapi.entity.WaitingListEntry entry = waitingListEntry.get();
                // Remove from waiting list without sending cancellation email (user got approved elsewhere)
                waitingListService.removeFromWaitingList(entry.getSlotId(), presenterUsername, false);
                logger.info("Removed user {} from waiting list for slotId={} (user approved for slotId={})",
                        presenterUsername, entry.getSlotId(), slotId);
                databaseLoggerService.logBusinessEvent("WAITING_LIST_AUTO_REMOVED_ON_APPROVAL",
                        String.format("User %s removed from waiting list for slotId=%d (approved for slotId=%d)",
                                presenterUsername, entry.getSlotId(), slotId),
                        presenterUsername);
            }
        } catch (Exception e) {
            logger.error("Failed to remove user {} from waiting lists after approval: {}", presenterUsername, e.getMessage(), e);
            // Don't fail approval if waiting list removal fails
        }

        // Send approval notification email OUTSIDE transaction to prevent holding database locks during SMTP operations
        try {
            sendApprovalNotificationEmail(registration);
        } catch (Exception e) {
            logger.error("Failed to send approval notification email to presenter {} for slot {}", presenterUsername, slotId, e);
            // Don't fail approval if email fails
        }

        // Send push notification
        try {
            SeminarSlot slot = slotRepository.findById(slotId).orElse(null);
            String slotDate = slot != null ? slot.getSlotDate().format(DATE_FORMAT) : "your slot";
            fcmService.sendApprovalNotification(presenterUsername, slotId, slotDate, true, null);
        } catch (Exception e) {
            logger.error("Failed to send push notification to presenter {} for slot {}", presenterUsername, slotId, e);
            // Don't fail approval if push notification fails
        }
    }

    /**
     * Approve registration via token only (mobile-compatible)
     */
    @Transactional
    public void approveRegistrationByToken(String approvalToken) {
        logger.info("📧 [RegistrationApprovalService] approveRegistrationByToken() called with token: {}", 
                approvalToken != null && approvalToken.length() > 8 ? approvalToken.substring(0, 8) + "..." : "null");
        
        // Validate token format (UUID should be 36 characters)
        if (approvalToken == null || approvalToken.trim().isEmpty()) {
            String errorMsg = "Approval token is null or empty";
            IllegalArgumentException exception = new IllegalArgumentException("Approval token is required");
            logger.error("📧 {}", errorMsg, exception);
            databaseLoggerService.logError("REGISTRATION_APPROVAL_TOKEN_INVALID",
                    errorMsg, exception, null, "token=null_or_empty");
            throw exception;
        }
        
        // Find registration by approval token with enhanced error handling
        Optional<SeminarSlotRegistration> registrationOpt = registrationRepository.findByApprovalToken(approvalToken);
        
        if (registrationOpt.isEmpty()) {
            // Enhanced diagnostics: check for potential issues
            String tokenPrefix = approvalToken.length() > 8 ? approvalToken.substring(0, 8) : approvalToken;
            boolean isValidUUID = approvalToken.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            
            // Count pending registrations with null tokens (indicates token was never generated)
            List<SeminarSlotRegistration> pendingRegistrations = registrationRepository.findByApprovalStatus(ApprovalStatus.PENDING);
            long registrationsWithNullTokens = pendingRegistrations.stream()
                    .filter(r -> r.getApprovalToken() == null)
                    .count();
            
            String diagnosticInfo = String.format(
                    "tokenPrefix=%s,tokenLength=%d,tokenFormat=%s,pendingRegistrationsWithNullTokens=%d,totalPendingRegistrations=%d",
                    tokenPrefix,
                    approvalToken.length(),
                    isValidUUID ? "validUUID" : "invalidUUID",
                    registrationsWithNullTokens,
                    pendingRegistrations.size()
            );
            
            // Provide helpful error message based on diagnostics
            String userMessage = "Registration not found for this token. ";
            if (!isValidUUID) {
                userMessage += "The token format is invalid (expected UUID format). ";
            }
            if (registrationsWithNullTokens > 0) {
                userMessage += String.format("Found %d pending registration(s) with no token - this indicates the approval email was not sent (likely missing supervisor email). ", registrationsWithNullTokens);
            }
            userMessage += "The token may be invalid, expired, already used, or the approval email may not have been sent yet. ";
            userMessage += "Please check app_logs for 'EMAIL_REGISTRATION_APPROVAL_EMAIL_MISSING_SUPERVISOR' or 'EMAIL_REGISTRATION_APPROVAL_EMAIL_FAILED' errors.";
            
            // Create exception first to capture stack trace
            IllegalArgumentException exception = new IllegalArgumentException(userMessage);
            
            String errorMsg = "Registration not found for this token";
            logger.error("📧 {} - Token prefix: {}, Diagnostics: {}", errorMsg, tokenPrefix, diagnosticInfo, exception);
            databaseLoggerService.logError("REGISTRATION_APPROVAL_TOKEN_NOT_FOUND",
                    errorMsg, exception, null, diagnosticInfo);
            
            // Throw the exception after logging
            throw exception;
        }
        
        SeminarSlotRegistration registration = registrationOpt.get();
        Long slotId = registration.getSlotId();
        String presenterUsername = registration.getPresenterUsername();
        
        logger.info("📧 [RegistrationApprovalService] Registration found for token - slotId: {}, presenter: {}, status: {}", 
                slotId, presenterUsername, registration.getApprovalStatus());
        
        approveRegistration(slotId, presenterUsername, approvalToken);
    }

    /**
     * Decline registration via token only (mobile-compatible)
     */
    @Transactional
    public void declineRegistrationByToken(String approvalToken, String reason) {
        logger.info("📧 [RegistrationApprovalService] declineRegistrationByToken() called with token: {}", 
                approvalToken != null && approvalToken.length() > 8 ? approvalToken.substring(0, 8) + "..." : "null");
        
        // Validate token format (UUID should be 36 characters)
        if (approvalToken == null || approvalToken.trim().isEmpty()) {
            String errorMsg = "Approval token is null or empty";
            IllegalArgumentException exception = new IllegalArgumentException("Approval token is required");
            logger.error("📧 {}", errorMsg, exception);
            databaseLoggerService.logError("REGISTRATION_DECLINE_TOKEN_INVALID",
                    errorMsg, exception, null, "token=null_or_empty");
            throw exception;
        }
        
        // Find registration by approval token with enhanced error handling
        Optional<SeminarSlotRegistration> registrationOpt = registrationRepository.findByApprovalToken(approvalToken);
        
        if (registrationOpt.isEmpty()) {
            // Enhanced diagnostics: check for potential issues
            String tokenPrefix = approvalToken.length() > 8 ? approvalToken.substring(0, 8) : approvalToken;
            boolean isValidUUID = approvalToken.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            
            String diagnosticInfo = String.format(
                    "tokenPrefix=%s,tokenLength=%d,tokenFormat=%s,reason=%s",
                    tokenPrefix,
                    approvalToken.length(),
                    isValidUUID ? "validUUID" : "invalidUUID",
                    reason != null ? reason : "null"
            );
            
            // Provide helpful error message based on diagnostics
            String userMessage = "Registration not found for this token. ";
            if (!isValidUUID) {
                userMessage += "The token format is invalid (expected UUID format). ";
            }
            userMessage += "The token may be invalid, expired, already used, or the approval email may not have been sent yet.";
            
            // Create exception first to capture stack trace
            IllegalArgumentException exception = new IllegalArgumentException(userMessage);
            
            String errorMsg = "Registration not found for this token";
            logger.error("📧 {} - Token prefix: {}, Diagnostics: {}", errorMsg, tokenPrefix, diagnosticInfo, exception);
            databaseLoggerService.logError("REGISTRATION_DECLINE_TOKEN_NOT_FOUND",
                    errorMsg, exception, null, diagnosticInfo);
            
            // Throw the exception after logging
            throw exception;
        }
        
        SeminarSlotRegistration registration = registrationOpt.get();
        Long slotId = registration.getSlotId();
        String presenterUsername = registration.getPresenterUsername();
        
        logger.info("📧 [RegistrationApprovalService] Registration found for token - slotId: {}, presenter: {}, status: {}", 
                slotId, presenterUsername, registration.getApprovalStatus());
        
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
            throw new IllegalStateException("This approval link has expired. Please ask the student to register again.");
        }

        // Handle different approval statuses
        if (registration.getApprovalStatus() == ApprovalStatus.DECLINED) {
            // Already declined - idempotent operation, return successfully
            logger.info("Registration already declined for slotId={}, presenter={} - returning success", slotId, presenterUsername);
            databaseLoggerService.logAction("INFO", "REGISTRATION_ALREADY_DECLINED",
                    String.format("Registration already declined for slotId=%d, presenter=%s (idempotent)", slotId, presenterUsername),
                    presenterUsername, String.format("slotId=%d", slotId));
            return; // Success - already declined
        }
        
        if (registration.getApprovalStatus() != ApprovalStatus.PENDING) {
            // APPROVED or EXPIRED - cannot decline
            String errorMsg = String.format("Cannot decline registration: status is %s (only PENDING registrations can be declined)", 
                    registration.getApprovalStatus());
            logger.error("📧 {}", errorMsg);
            databaseLoggerService.logError("REGISTRATION_DECLINE_INVALID_STATUS",
                    errorMsg, null, presenterUsername, String.format("slotId=%d,status=%s", slotId, registration.getApprovalStatus()));
            throw new IllegalStateException("Registration is not pending approval");
        }

        // Decline registration
        registration.setApprovalStatus(ApprovalStatus.DECLINED);
        registration.setSupervisorDeclinedAt(LocalDateTime.now());
        registration.setSupervisorDeclinedReason(reason);
        registrationRepository.save(registration);

        // Update WaitingListPromotion status to DECLINED if exists
        updateWaitingListPromotionStatus(slotId, presenterUsername, WaitingListPromotion.PromotionStatus.DECLINED);

        logger.info("Registration declined for slotId={}, presenter={}, reason={}", slotId, presenterUsername, reason);
        databaseLoggerService.logBusinessEvent("REGISTRATION_DECLINED",
                String.format("Registration declined for slotId=%d, presenter=%s, reason=%s", slotId, presenterUsername, reason),
                presenterUsername);

        // CRITICAL: When a PENDING registration is declined, capacity is freed - promote next from waiting list
        try {
            SeminarSlot slot = slotRepository.findById(slotId)
                    .orElse(null);
            if (slot != null) {
                Optional<edu.bgu.semscanapi.entity.WaitingListEntry> promotedEntry =
                        presenterHomeService.promoteFromWaitingListAfterCancellation(slotId, slot);
                if (promotedEntry.isPresent()) {
                    logger.info("📧 Successfully promoted next person {} from waiting list for slot {} after supervisor declined",
                            promotedEntry.get().getPresenterUsername(), slotId);
                    databaseLoggerService.logBusinessEvent("WAITING_LIST_AUTO_PROMOTED_AFTER_DECLINE",
                            String.format("Next person %s automatically promoted from waiting list for slotId=%d after supervisor declined previous registration",
                                    promotedEntry.get().getPresenterUsername(), slotId),
                            promotedEntry.get().getPresenterUsername());
                } else {
                    logger.info("📧 No one to promote from waiting list for slot {} after decline (waiting list empty or slot full)", slotId);
                }
            }
        } catch (Exception e) {
            logger.error("📧 Failed to promote next person from waiting list for slot {} after decline: {}",
                    slotId, e.getMessage(), e);
            databaseLoggerService.logError("WAITING_LIST_AUTO_PROMOTE_AFTER_DECLINE_FAILED",
                    String.format("Failed to automatically promote next person from waiting list for slotId=%d after decline: %s",
                            slotId, e.getMessage()),
                    e, presenterUsername, String.format("slotId=%d", slotId));
            // Don't fail the decline operation if promotion fails
        }

        // Send push notification for decline
        try {
            SeminarSlot slot = slotRepository.findById(slotId).orElse(null);
            String slotDate = slot != null ? slot.getSlotDate().format(DATE_FORMAT) : "your slot";
            fcmService.sendApprovalNotification(presenterUsername, slotId, slotDate, false, reason);
        } catch (Exception e) {
            logger.error("Failed to send push notification to presenter {} for slot {}", presenterUsername, slotId, e);
            // Don't fail decline if push notification fails
        }
    }

    /**
     * Helper method to update WaitingListPromotion status
     */
    private void updateWaitingListPromotionStatus(Long slotId, String presenterUsername, 
                                                   WaitingListPromotion.PromotionStatus newStatus) {
        Optional<WaitingListPromotion> promotionOpt = waitingListPromotionRepository
                .findByRegistrationSlotIdAndRegistrationPresenterUsername(slotId, presenterUsername);
        if (promotionOpt.isPresent()) {
            WaitingListPromotion promotion = promotionOpt.get();
            promotion.setStatus(newStatus);
            waitingListPromotionRepository.save(promotion);
            logger.debug("Updated WaitingListPromotion status to {} for slotId={}, presenter={}", 
                    newStatus, slotId, presenterUsername);
        }
    }

    /**
     * Generate HTML email content for approval request
     */
    private String generateApprovalEmailHtml(SeminarSlotRegistration registration, SeminarSlot slot,
                                             String approveUrl, String declineUrl, LocalDateTime expiresAt,
                                             String studentFullName) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0;">
                <table width="100%%" cellpadding="0" cellspacing="0" border="0">
                    <tr>
                        <td align="center">
                            <table width="600" cellpadding="0" cellspacing="0" border="0" style="max-width: 600px;">
                                <!-- Header -->
                                <tr>
                                    <td style="background-color: #4CAF50; color: white; padding: 20px; text-align: center;">
                                        <h1 style="margin: 0; font-size: 24px;">SemScan - Approval Required</h1>
                                    </td>
                                </tr>
                                <!-- Content -->
                                <tr>
                                    <td style="padding: 20px; background-color: #f9f9f9;">
                                        <p>Dear %s,</p>
                                        <p>Your student <strong>%s</strong> has requested to register for a seminar slot and requires your approval.</p>

                                        <!-- Info Box -->
                                        <table width="100%%" cellpadding="15" cellspacing="0" border="0" style="background-color: #e3f2fd; margin: 15px 0; border-left: 4px solid #2196F3;">
                                            <tr>
                                                <td>
                                                    <h3 style="margin-top: 0;">Registration Details:</h3>
                                                    <p style="margin: 5px 0;"><strong>Student:</strong> %s</p>
                                                    <p style="margin: 5px 0;"><strong>Slot Date:</strong> %s</p>
                                                    <p style="margin: 5px 0;"><strong>Time:</strong> %s - %s</p>
                                                    <p style="margin: 5px 0;"><strong>Topic:</strong> %s</p>
                                                    <p style="margin: 5px 0;"><strong>Degree:</strong> %s</p>
                                                </td>
                                            </tr>
                                        </table>

                                        <!-- Abstract Box -->
                                        <table width="100%%" cellpadding="15" cellspacing="0" border="0" style="background-color: #fff3e0; margin: 15px 0; border-left: 4px solid #FF9800;">
                                            <tr>
                                                <td>
                                                    <h3 style="margin-top: 0;">Seminar Abstract:</h3>
                                                    <p style="margin: 5px 0; white-space: pre-wrap;">%s</p>
                                                </td>
                                            </tr>
                                        </table>

                                        <p>Please review the registration and choose one of the following options:</p>

                                        <!-- Buttons -->
                                        <table width="100%%" cellpadding="0" cellspacing="0" border="0" style="margin: 30px 0;">
                                            <tr>
                                                <td align="center">
                                                    <table cellpadding="0" cellspacing="0" border="0">
                                                        <tr>
                                                            <td style="padding: 0 10px;">
                                                                <a href="%s" style="display: inline-block; padding: 14px 28px; background-color: #4CAF50; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 14px;">✓ Approve</a>
                                                            </td>
                                                            <td style="padding: 0 10px;">
                                                                <a href="%s" style="display: inline-block; padding: 14px 28px; background-color: #f44336; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 14px;">✗ Decline</a>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="color: #666; font-size: 12px;">
                                            <strong>Note:</strong> This approval link will expire on %s.
                                        </p>
                                    </td>
                                </tr>
                                <!-- Footer -->
                                <tr>
                                    <td style="text-align: center; padding: 20px; color: #666; font-size: 12px;">
                                        <p style="margin: 5px 0;">This is an automated message from SemScan Attendance System.</p>
                                        <p style="margin: 5px 0;">If you did not expect this email, please ignore it.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """,
            registration.getSupervisorName() != null ? registration.getSupervisorName() : "Supervisor",
            studentFullName,
            studentFullName,
            slot.getSlotDate().toString(),
            slot.getStartTime().toString(),
            slot.getEndTime().toString(),
            registration.getTopic() != null ? registration.getTopic() : "Not specified",
            registration.getDegree().toString(),
            registration.getSeminarAbstract() != null ? registration.getSeminarAbstract() : "Not provided",
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

        // Queue email (with retry support)
        emailQueueService.queueEmail(
            EmailQueue.EmailType.APPROVAL_NOTIFICATION,
            presenterEmail,
            subject,
            htmlContent,
            null,
            registration.getSlotId(),
            registration.getPresenterUsername()
        );

        logger.info("Approval notification email queued for presenter {} ({}) for registration slotId={}",
                registration.getPresenterUsername(), presenterEmail, registration.getSlotId());
        databaseLoggerService.logBusinessEvent("REGISTRATION_APPROVAL_NOTIFICATION_QUEUED",
                String.format("Approval notification email queued for presenter %s (%s) for registration slotId=%d",
                        registration.getPresenterUsername(), presenterEmail, registration.getSlotId()),
                registration.getPresenterUsername());
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

