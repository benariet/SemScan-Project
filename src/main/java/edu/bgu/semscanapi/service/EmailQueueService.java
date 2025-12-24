package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.entity.EmailLog;
import edu.bgu.semscanapi.entity.EmailQueue;
import edu.bgu.semscanapi.repository.EmailLogRepository;
import edu.bgu.semscanapi.repository.EmailQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Service for managing email queue with retry support.
 * All emails go through this service for reliable delivery with comprehensive logging.
 */
@Service
public class EmailQueueService {

    private static final Logger logger = LoggerFactory.getLogger(EmailQueueService.class);

    // Email validation pattern (RFC 5322 simplified)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    // Human-readable datetime format
    public static final DateTimeFormatter HUMAN_DATETIME_FORMAT =
        DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH);

    @Autowired
    private EmailQueueRepository emailQueueRepository;

    @Autowired
    private EmailLogRepository emailLogRepository;

    @Autowired
    private MailService mailService;

    @Autowired
    private DatabaseLoggerService dbLogger;

    @Autowired
    private GlobalConfig globalConfig;

    // ==================== EMAIL VALIDATION ====================

    /**
     * Validate email address format
     * @return null if valid, error message if invalid
     */
    public String validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "Email address is empty";
        }

        String trimmed = email.trim().toLowerCase();

        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            return "Invalid email format: " + email;
        }

        // Check for common typos/issues
        if (trimmed.contains("..")) {
            return "Invalid email: contains consecutive dots";
        }

        if (trimmed.startsWith(".") || trimmed.endsWith(".")) {
            return "Invalid email: starts or ends with dot";
        }

        // Check for suspicious domains
        if (trimmed.endsWith("@example.com") || trimmed.endsWith("@test.com")) {
            return "Invalid email: test/example domain not allowed";
        }

        return null; // Valid
    }

    /**
     * Check if supervisor email is valid and ready for use
     */
    public EmailValidationResult validateSupervisorEmail(String supervisorEmail, String studentUsername) {
        String logPrefix = "[EMAIL_VALIDATION] Student: " + studentUsername + " - ";

        logger.info(logPrefix + "Validating supervisor email: {}", supervisorEmail);
        dbLogger.logAction("INFO", "EMAIL_SUPERVISOR_VALIDATION_START",
            logPrefix + "Validating supervisor email: " + maskEmail(supervisorEmail),
            studentUsername, null);

        // Check if email is provided
        if (supervisorEmail == null || supervisorEmail.trim().isEmpty()) {
            String error = "Supervisor email is missing. Please update your supervisor email in Settings.";
            logger.warn(logPrefix + "VALIDATION FAILED - Supervisor email is null/empty");
            dbLogger.logAction("WARN", "EMAIL_SUPERVISOR_VALIDATION_FAILED",
                logPrefix + "Supervisor email is null or empty",
                studentUsername, null);
            return new EmailValidationResult(false, error, "SUPERVISOR_EMAIL_MISSING");
        }

        // Validate format
        String formatError = validateEmail(supervisorEmail);
        if (formatError != null) {
            String error = "Your supervisor email (" + supervisorEmail + ") appears to be invalid. " +
                          "Please update it in Settings. Error: " + formatError;
            logger.warn(logPrefix + "VALIDATION FAILED - Invalid format: {}", formatError);
            dbLogger.logAction("WARN", "EMAIL_SUPERVISOR_VALIDATION_FAILED",
                logPrefix + "Invalid email format: " + formatError,
                studentUsername, null);
            return new EmailValidationResult(false, error, "SUPERVISOR_EMAIL_INVALID_FORMAT");
        }

        // All checks passed
        logger.info(logPrefix + "VALIDATION PASSED - Supervisor email is valid");
        dbLogger.logAction("INFO", "EMAIL_SUPERVISOR_VALIDATION_PASSED",
            logPrefix + "Supervisor email validated: " + maskEmail(supervisorEmail),
            studentUsername, null);

        return new EmailValidationResult(true, null, null);
    }

    // ==================== QUEUE OPERATIONS ====================

    /**
     * Queue an email for sending
     */
    @Transactional
    public EmailQueue queueEmail(EmailQueue.EmailType emailType, String toEmail, String subject,
                                  String htmlContent, Long registrationId, Long slotId, String username) {
        return queueEmail(emailType, toEmail, subject, htmlContent, registrationId, slotId, username, LocalDateTime.now());
    }

    /**
     * Queue an email for sending at a specific time
     */
    @Transactional
    public EmailQueue queueEmail(EmailQueue.EmailType emailType, String toEmail, String subject,
                                  String htmlContent, Long registrationId, Long slotId, String username,
                                  LocalDateTime scheduledAt) {
        String logPrefix = "[EMAIL_QUEUE] ";

        logger.info(logPrefix + "Queueing {} email to {} for registration {}",
            emailType, maskEmail(toEmail), registrationId);

        EmailQueue email = new EmailQueue();
        email.setEmailType(emailType);
        email.setToEmail(toEmail);
        email.setSubject(subject);
        email.setHtmlContent(htmlContent);
        email.setRegistrationId(registrationId);
        email.setSlotId(slotId);
        email.setUsername(username);
        email.setStatus(EmailQueue.Status.PENDING);
        email.setScheduledAt(scheduledAt);
        email.setRetryCount(0);
        email.setMaxRetries(globalConfig.getEmailQueueMaxRetries());

        email = emailQueueRepository.save(email);

        // Log to email_log
        logEmailActivity(email, EmailLog.Status.QUEUED, null, null);

        // Log to app_logs
        dbLogger.logAction("INFO", "EMAIL_QUEUED",
            logPrefix + "Email queued - Type: " + emailType + ", To: " + maskEmail(toEmail) +
            ", RegistrationId: " + registrationId + ", QueueId: " + email.getId() +
            ", ScheduledAt: " + formatDateTime(scheduledAt),
            username, "registrationId=" + registrationId + ",slotId=" + slotId);

        logger.info(logPrefix + "Email queued successfully - QueueId: {}", email.getId());

        return email;
    }

    /**
     * Process all pending emails in the queue
     * Called by scheduled job
     */
    @Transactional
    public int processQueue() {
        String logPrefix = "[EMAIL_PROCESSOR] ";
        LocalDateTime now = LocalDateTime.now();

        // Reset any stuck processing emails (processing > 5 minutes)
        int resetCount = emailQueueRepository.resetStuckProcessingEmails(now.minusMinutes(5));
        if (resetCount > 0) {
            logger.warn(logPrefix + "Reset {} stuck processing emails back to PENDING", resetCount);
            dbLogger.logAction("WARN", "EMAIL_STUCK_RESET",
                logPrefix + "Reset " + resetCount + " stuck processing emails",
                null, null);
        }

        // Get pending emails ready to send (limit to 50 per batch)
        List<EmailQueue> pendingEmails = emailQueueRepository.findPendingEmailsReadyToSendWithLimit(now, globalConfig.getEmailQueueBatchSize());

        if (pendingEmails.isEmpty()) {
            return 0;
        }

        logger.info(logPrefix + "Processing {} pending emails", pendingEmails.size());
        dbLogger.logAction("INFO", "EMAIL_BATCH_PROCESSING_START",
            logPrefix + "Processing batch of " + pendingEmails.size() + " emails",
            null, null);

        int sentCount = 0;
        int failedCount = 0;

        for (EmailQueue email : pendingEmails) {
            try {
                boolean sent = processEmail(email);
                if (sent) {
                    sentCount++;
                } else {
                    failedCount++;
                }
            } catch (Exception e) {
                logger.error(logPrefix + "Exception processing email {}: {}", email.getId(), e.getMessage(), e);
                failedCount++;
            }
        }

        logger.info(logPrefix + "Batch complete - Sent: {}, Failed: {}", sentCount, failedCount);
        dbLogger.logAction("INFO", "EMAIL_BATCH_PROCESSING_COMPLETE",
            logPrefix + "Batch processing complete - Sent: " + sentCount + ", Failed: " + failedCount,
            null, null);

        return sentCount;
    }

    /**
     * Process a single email
     */
    @Transactional
    public boolean processEmail(EmailQueue email) {
        String logPrefix = "[EMAIL_SEND] QueueId: " + email.getId() + " - ";

        logger.info(logPrefix + "Processing {} email to {}", email.getEmailType(), maskEmail(email.getToEmail()));

        // Mark as processing
        email.setStatus(EmailQueue.Status.PROCESSING);
        email.setLastAttemptAt(LocalDateTime.now());
        email.setRetryCount(email.getRetryCount() + 1);
        emailQueueRepository.save(email);

        try {
            // Send the email
            boolean sent = mailService.sendHtmlEmail(
                email.getToEmail(),
                email.getSubject(),
                email.getHtmlContent()
            );

            if (sent) {
                // Success!
                email.setStatus(EmailQueue.Status.SENT);
                email.setSentAt(LocalDateTime.now());
                email.setLastError(null);
                email.setLastErrorCode(null);
                emailQueueRepository.save(email);

                // Log success
                logEmailActivity(email, EmailLog.Status.SENT, null, null);
                dbLogger.logAction("INFO", "EMAIL_SENT_SUCCESS",
                    logPrefix + "Email sent successfully - Type: " + email.getEmailType() +
                    ", To: " + maskEmail(email.getToEmail()) + ", Attempt: " + email.getRetryCount(),
                    email.getUsername(),
                    "registrationId=" + email.getRegistrationId() + ",slotId=" + email.getSlotId());

                logger.info(logPrefix + "Email sent successfully on attempt {}", email.getRetryCount());
                return true;

            } else {
                // Failed to send
                return handleSendFailure(email, "Email sending returned false", "EMAIL_SEND_RETURNED_FALSE");
            }

        } catch (Exception e) {
            return handleSendFailure(email, e.getMessage(), "EMAIL_EXCEPTION");
        }
    }

    /**
     * Handle email send failure
     */
    private boolean handleSendFailure(EmailQueue email, String errorMessage, String errorCode) {
        String logPrefix = "[EMAIL_FAILED] QueueId: " + email.getId() + " - ";

        email.setLastError(errorMessage);
        email.setLastErrorCode(errorCode);

        if (email.getRetryCount() >= email.getMaxRetries()) {
            // Max retries reached - mark as permanently failed
            email.setStatus(EmailQueue.Status.FAILED);

            logger.error(logPrefix + "PERMANENTLY FAILED after {} attempts - Error: {}",
                email.getRetryCount(), errorMessage);

            // Log failure
            logEmailActivity(email, EmailLog.Status.FAILED, errorMessage, errorCode);
            dbLogger.logError("EMAIL_PERMANENTLY_FAILED",
                logPrefix + "Email permanently failed after " + email.getRetryCount() + " attempts - " +
                "Type: " + email.getEmailType() + ", To: " + maskEmail(email.getToEmail()) +
                ", Error: " + errorMessage + ", ErrorCode: " + errorCode,
                null, email.getUsername(),
                "registrationId=" + email.getRegistrationId() + ",slotId=" + email.getSlotId() + ",errorCode=" + errorCode);

        } else {
            // Will retry - mark back to pending with exponential backoff
            email.setStatus(EmailQueue.Status.PENDING);

            // Exponential backoff: 5min, 15min, 45min
            int backoffMinutes = globalConfig.getEmailQueueInitialBackoffMinutes() *
                (int) Math.pow(globalConfig.getEmailQueueBackoffMultiplier(), email.getRetryCount() - 1);
            email.setScheduledAt(LocalDateTime.now().plusMinutes(backoffMinutes));

            logger.warn(logPrefix + "Failed attempt {} of {} - Will retry in {} minutes - Error: {}",
                email.getRetryCount(), email.getMaxRetries(), backoffMinutes, errorMessage);

            dbLogger.logAction("WARN", "EMAIL_SEND_FAILED_WILL_RETRY",
                logPrefix + "Attempt " + email.getRetryCount() + " of " + email.getMaxRetries() + " failed - " +
                "Type: " + email.getEmailType() + ", To: " + maskEmail(email.getToEmail()) +
                ", Will retry in " + backoffMinutes + " minutes - Error: " + errorMessage,
                email.getUsername(),
                "registrationId=" + email.getRegistrationId() + ",slotId=" + email.getSlotId());
        }

        emailQueueRepository.save(email);
        return false;
    }

    /**
     * Cancel all pending emails for a registration
     */
    @Transactional
    public int cancelEmailsForRegistration(Long registrationId) {
        int cancelled = emailQueueRepository.cancelPendingEmailsForRegistration(registrationId);
        if (cancelled > 0) {
            logger.info("[EMAIL_CANCEL] Cancelled {} pending emails for registration {}", cancelled, registrationId);
            dbLogger.logAction("INFO", "EMAIL_CANCELLED_FOR_REGISTRATION",
                "Cancelled " + cancelled + " pending emails for registration " + registrationId,
                null, "registrationId=" + registrationId);
        }
        return cancelled;
    }

    // ==================== LOGGING ====================

    /**
     * Log email activity to email_log table AND to file/app_logs
     */
    private void logEmailActivity(EmailQueue email, EmailLog.Status status, String errorMessage, String errorCode) {
        String logPrefix = "[EMAIL_LOG] ";
        String logMessage = String.format("QueueId=%d, Type=%s, To=%s, Status=%s",
            email.getId(), email.getEmailType(), maskEmail(email.getToEmail()), status);

        try {
            // 1. Save to email_log table
            EmailLog log = new EmailLog();
            log.setToEmail(email.getToEmail());
            log.setSubject(email.getSubject());
            log.setEmailType(email.getEmailType().name());
            log.setStatus(status);
            log.setErrorMessage(errorMessage);
            log.setErrorCode(errorCode);
            log.setRegistrationId(email.getRegistrationId());
            log.setSlotId(email.getSlotId());
            log.setUsername(email.getUsername());
            log.setQueueId(email.getId());
            EmailLog savedLog = emailLogRepository.save(log);

            // 2. Log to file
            if (status == EmailLog.Status.FAILED) {
                logger.error(logPrefix + logMessage + ", Error: " + errorMessage);
            } else {
                logger.info(logPrefix + logMessage);
            }

            // 3. Log to app_logs table
            String payload = String.format("emailLogId=%d,queueId=%d,registrationId=%s,slotId=%s,errorCode=%s",
                savedLog.getId(), email.getId(), email.getRegistrationId(), email.getSlotId(),
                errorCode != null ? errorCode : "null");

            if (status == EmailLog.Status.FAILED) {
                dbLogger.logError("EMAIL_LOG_" + status.name(),
                    logPrefix + logMessage + (errorMessage != null ? ", Error: " + errorMessage : ""),
                    null, email.getUsername(), payload);
            } else {
                dbLogger.logAction("INFO", "EMAIL_LOG_" + status.name(),
                    logPrefix + logMessage,
                    email.getUsername(), payload);
            }

        } catch (Exception e) {
            logger.error("Failed to log email activity: {}", e.getMessage());
            // Still try to log the failure to app_logs
            try {
                dbLogger.logError("EMAIL_LOG_SAVE_FAILED",
                    logPrefix + "Failed to save email log: " + e.getMessage(),
                    e, email.getUsername(), "queueId=" + email.getId());
            } catch (Exception ignored) {
                // Ignore if this also fails
            }
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Format datetime in human-readable format
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "N/A";
        return dateTime.format(HUMAN_DATETIME_FORMAT);
    }

    /**
     * Mask email for logging (show first 3 chars + domain)
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 5) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex < 3) return "***" + email.substring(atIndex);
        return email.substring(0, 3) + "***" + email.substring(atIndex);
    }

    // ==================== RESULT CLASSES ====================

    /**
     * Result class for email validation
     */
    public static class EmailValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final String errorCode;

        public EmailValidationResult(boolean valid, String errorMessage, String errorCode) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
