package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for sending emails with attachments
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerUtil.getLogger(EmailService.class);

    @Lazy
    @Autowired
    private DatabaseLoggerService databaseLoggerService;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.from:SemScan Attendance System <noreply@semscan.com>}")
    private String fromEmail;

    @Value("${app.export.email-recipients:attendance@example.com,admin@example.com}")
    private String emailRecipients;

    /**
     * Send export file via email
     *
     * @param sessionId The session ID
     * @param filename The filename (e.g., "9_11_2025_john_doe_13-15.csv")
     * @param fileData The file content as byte array
     * @param format The file format ("csv" or "xlsx")
     * @return true if email was sent successfully, false otherwise
     */
    public boolean sendExportEmail(Long sessionId, String filename, byte[] fileData, String format) {
        String username = LoggerUtil.getCurrentBguUsername();
        String deviceInfo = DatabaseLoggerService.getDeviceInfo();

        logger.info("[EXPORT_EMAIL_START] Starting export email process for sessionId={}, filename={}, format={}, fileSize={} bytes, user={}, device={}",
            sessionId, filename, format, fileData != null ? fileData.length : 0, username, deviceInfo);

        // Log to database
        databaseLoggerService.logAction("INFO", "EXPORT_EMAIL_START",
            String.format("Starting export email for session %d, file: %s, format: %s", sessionId, filename, format),
            username, String.format("sessionId=%d,filename=%s,format=%s,fileSize=%d,device=%s",
                sessionId, filename, format, fileData != null ? fileData.length : 0, deviceInfo));

        if (mailSender == null) {
            logger.error("[EXPORT_EMAIL_NOT_CONFIGURED] JavaMailSender is NULL - email sending is disabled. " +
                "Check SMTP configuration in application.properties. sessionId={}", sessionId);
            databaseLoggerService.logError("EXPORT_EMAIL_NOT_CONFIGURED",
                "JavaMailSender is NULL - email sending disabled", null, username,
                String.format("sessionId=%d,device=%s", sessionId, deviceInfo));
            return false;
        }
        logger.info("[EXPORT_EMAIL_MAILSENDER_OK] JavaMailSender is configured and available");

        try {
            // Parse recipients from configuration
            logger.info("[EXPORT_EMAIL_PARSING_RECIPIENTS] Raw recipients config: '{}'", emailRecipients);
            List<String> recipients = parseEmailRecipients(emailRecipients);

            if (recipients.isEmpty()) {
                logger.error("[EXPORT_EMAIL_NO_RECIPIENTS] No email recipients configured after parsing. " +
                    "Raw config was: '{}'. sessionId={}", emailRecipients, sessionId);
                databaseLoggerService.logError("EXPORT_EMAIL_NO_RECIPIENTS",
                    String.format("No email recipients configured. Raw config: '%s'", emailRecipients),
                    null, username, String.format("sessionId=%d,rawConfig=%s,device=%s", sessionId, emailRecipients, deviceInfo));
                return false;
            }

            String recipientList = String.join(", ", recipients);

            // Log each recipient address explicitly
            logger.info("[EXPORT_EMAIL_RECIPIENTS_PARSED] Found {} recipient(s) for sessionId={}: {}",
                recipients.size(), sessionId, recipientList);
            databaseLoggerService.logAction("INFO", "EXPORT_EMAIL_RECIPIENTS",
                String.format("Email recipients for session %d: %s", sessionId, recipientList),
                username, String.format("sessionId=%d,recipientCount=%d,recipients=%s,device=%s",
                    sessionId, recipients.size(), recipientList, deviceInfo));

            logger.info("[EXPORT_EMAIL_CREATING_MESSAGE] Creating MimeMessage with attachment for sessionId={}", sessionId);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Configure email: sender address, recipient list, and subject line with session ID
            logger.info("[EXPORT_EMAIL_SET_FROM] Setting From address: '{}'", fromEmail);
            helper.setFrom(fromEmail);

            logger.info("[EXPORT_EMAIL_SET_TO] Setting To addresses: {}", recipientList);
            helper.setTo(recipients.toArray(new String[0]));

            String subject = String.format("Attendance Export - Session %d", sessionId);
            logger.info("[EXPORT_EMAIL_SET_SUBJECT] Setting Subject: '{}'", subject);
            helper.setSubject(subject);

            // Generate plain text email body with session details and file information
            String body = String.format(
                "Dear Administrator,\n\n" +
                "Please find attached the attendance export for session %d.\n\n" +
                "File: %s\n" +
                "Format: %s\n" +
                "Size: %.2f KB\n\n" +
                "This is an automated message from the SemScan Attendance System.\n\n" +
                "Best regards,\n" +
                "SemScan System",
                sessionId,
                filename,
                format.toUpperCase(),
                fileData.length / 1024.0
            );
            // Convert plain text body to HTML format (line breaks -> <br> tags) for email rendering
            String htmlBody = convertToHtml(body);
            helper.setText(htmlBody, true);
            logger.info("[EXPORT_EMAIL_BODY_SET] Email body set (HTML format), length={} chars", htmlBody.length());

            // Attach exported file: determine MIME type (Excel XLSX or CSV) and attach file data
            String contentType = format.equalsIgnoreCase("xlsx")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv";
            logger.info("[EXPORT_EMAIL_ATTACHING] Attaching file: filename='{}', contentType='{}', size={} bytes",
                filename, contentType, fileData.length);
            helper.addAttachment(filename, () -> new java.io.ByteArrayInputStream(fileData), contentType);

            // Send email with attachment via SMTP server
            logger.info("[EXPORT_EMAIL_SENDING] Sending email via SMTP... sessionId={}, recipients={}",
                sessionId, recipientList);
            databaseLoggerService.logAction("INFO", "EXPORT_EMAIL_SENDING",
                String.format("Sending export email via SMTP for session %d to: %s", sessionId, recipientList),
                username, String.format("sessionId=%d,recipients=%s,filename=%s,from=%s,subject=%s,device=%s",
                    sessionId, recipientList, filename, fromEmail, subject, deviceInfo));

            mailSender.send(message);

            logger.info("[EXPORT_EMAIL_SUCCESS] Export email sent successfully! sessionId={}, recipientCount={}, recipients=[{}], filename={}, fileSize={} bytes",
                sessionId, recipients.size(), recipientList, filename, fileData.length);
            databaseLoggerService.logAction("INFO", "EXPORT_EMAIL_SUCCESS",
                String.format("Export email sent successfully for session %d to %d recipients: %s",
                    sessionId, recipients.size(), recipientList),
                username, String.format("sessionId=%d,recipientCount=%d,recipients=%s,filename=%s,fileSize=%d,from=%s,device=%s",
                    sessionId, recipients.size(), recipientList, filename, fileData.length, fromEmail, deviceInfo));
            return true;

        } catch (AuthenticationFailedException e) {
            logger.error("[EXPORT_EMAIL_AUTH_FAILED] SMTP authentication failed for sessionId={}. " +
                    "Check SMTP credentials. Recipients would have been: '{}'. Error: {}",
                    sessionId, emailRecipients, getFullErrorMessage(e), e);
            databaseLoggerService.logError("EXPORT_EMAIL_AUTH_FAILED",
                String.format("SMTP auth failed for session %d. Recipients: %s", sessionId, emailRecipients),
                e, username, String.format("sessionId=%d,recipients=%s,device=%s", sessionId, emailRecipients, deviceInfo));
            return false;
        } catch (MessagingException e) {
            logger.error("[EXPORT_EMAIL_MESSAGING_ERROR] MessagingException for sessionId={}. " +
                    "Recipients: '{}'. Filename: '{}'. Error: {}",
                    sessionId, emailRecipients, filename, getFullErrorMessage(e), e);
            databaseLoggerService.logError("EXPORT_EMAIL_MESSAGING_ERROR",
                String.format("MessagingException for session %d. Recipients: %s", sessionId, emailRecipients),
                e, username, String.format("sessionId=%d,recipients=%s,filename=%s,device=%s",
                    sessionId, emailRecipients, filename, deviceInfo));
            return false;
        } catch (Exception e) {
            logger.error("[EXPORT_EMAIL_ERROR] Unexpected error for sessionId={}. " +
                    "Recipients: '{}'. Filename: '{}'. Error: {}",
                    sessionId, emailRecipients, filename, getFullErrorMessage(e), e);
            databaseLoggerService.logError("EXPORT_EMAIL_ERROR",
                String.format("Unexpected error sending email for session %d. Recipients: %s", sessionId, emailRecipients),
                e, username, String.format("sessionId=%d,recipients=%s,filename=%s,device=%s",
                    sessionId, emailRecipients, filename, deviceInfo));
            return false;
        }
    }

    /**
     * Parse comma-separated email recipients
     *
     * @param recipientsString Comma-separated email addresses
     * @return List of trimmed email addresses
     */
    private List<String> parseEmailRecipients(String recipientsString) {
        if (recipientsString == null || recipientsString.trim().isEmpty()) {
            return List.of();
        }

        return Arrays.stream(recipientsString.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Check if email service is configured
     *
     * @return true if email service is available, false otherwise
     */
    public boolean isEmailConfigured() {
        return mailSender != null;
    }

    /**
     * Get configured email recipients
     *
     * @return List of email recipients
     */
    public List<String> getEmailRecipients() {
        return parseEmailRecipients(emailRecipients);
    }

    /**
     * Result class for email sending operations
     */
    public static class EmailResult {
        private final boolean success;
        private final String errorMessage;
        private final String errorCode;

        private EmailResult(boolean success, String errorMessage, String errorCode) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
        }

        public static EmailResult success() {
            return new EmailResult(true, null, null);
        }

        public static EmailResult failure(String errorMessage, String errorCode) {
            return new EmailResult(false, errorMessage, errorCode);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    /**
     * Get full error message including cause chain
     */
    private String getFullErrorMessage(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
        Throwable cause = e.getCause();
        int depth = 0;
        while (cause != null && depth < 5) {
            sb.append(" -> ").append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
            cause = cause.getCause();
            depth++;
        }
        return sb.toString();
    }

    /**
     * Send notification email to supervisor when a presenter registers for a slot
     *
     * @param supervisorEmail The supervisor's email address
     * @param supervisorName The supervisor's name
     * @param presenterName The presenter's full name (first name + last name)
     * @param presenterUsername The presenter's BGU username
     * @param presenterDegree The presenter's degree (MSc or PhD)
     * @param slotDate The slot date
     * @param slotStartTime The slot start time
     * @param slotEndTime The slot end time
     * @param slotBuilding The building name
     * @param slotRoom The room number
     * @param topic The presentation topic
     * @return EmailResult with success status and error details
     */
    public EmailResult sendSupervisorNotificationEmail(String supervisorEmail, String supervisorName,
                                                       String presenterName, String presenterUsername,
                                                       String presenterDegree, String slotDate,
                                                       String slotStartTime, String slotEndTime,
                                                       String slotBuilding, String slotRoom,
                                                       String topic) {
        if (mailSender == null) {
            String errorMsg = "JavaMailSender is not configured. Please check SMTP settings in application.properties.";
            logger.warn("{} Supervisor notification email cannot be sent to {} for presenter {}", 
                errorMsg, supervisorEmail, presenterUsername);
            return EmailResult.failure(errorMsg, "EMAIL_NOT_CONFIGURED");
        }

        if (supervisorEmail == null || supervisorEmail.trim().isEmpty()) {
            logger.debug("No supervisor email provided. Skipping supervisor notification.");
            return EmailResult.failure("No supervisor email provided", "NO_SUPERVISOR_EMAIL");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            // Set sender
            helper.setFrom(fromEmail);

            // Set recipient
            helper.setTo(supervisorEmail);

            // Set subject
            String subject = String.format("Seminar Registration Notification - %s", presenterName);
            helper.setSubject(subject);

            // Build location string
            String location = "";
            if (slotBuilding != null && !slotBuilding.trim().isEmpty()) {
                location = "Building " + slotBuilding;
                if (slotRoom != null && !slotRoom.trim().isEmpty()) {
                    location += ", Room " + slotRoom;
                }
            } else if (slotRoom != null && !slotRoom.trim().isEmpty()) {
                location = "Room " + slotRoom;
            }

            // Set body
            String greeting = supervisorName != null && !supervisorName.trim().isEmpty()
                    ? String.format("Dear %s,", supervisorName)
                    : "Dear Supervisor,";

            String body = String.format(
                "%s\n\n" +
                "This is to notify you that your student, %s (%s, %s), has registered for a seminar presentation slot.\n\n" +
                "Registration Details:\n" +
                "  • Student: %s (%s)\n" +
                "  • Degree: %s\n" +
                "  • Date: %s\n" +
                "  • Time: %s - %s\n" +
                "%s" +
                "  • Topic: %s\n\n" +
                "Please note that this is an automated notification from the SemScan Attendance System.\n\n" +
                "Best regards,\n" +
                "SemScan System",
                greeting,
                presenterName, presenterUsername, presenterDegree,
                presenterName, presenterUsername,
                presenterDegree,
                slotDate,
                slotStartTime, slotEndTime,
                location.isEmpty() ? "" : "  • Location: " + location + "\n",
                topic != null && !topic.trim().isEmpty() ? topic : "Not specified"
            );

            // Convert plain text body to HTML (line breaks -> <br> tags) for proper email rendering
            String htmlBody = convertToHtml(body);
            helper.setText(htmlBody, true);

            // Send notification email to supervisor via SMTP
            mailSender.send(message);
            logger.info("Supervisor notification email sent successfully to {} for presenter {}", supervisorEmail, presenterUsername);
            return EmailResult.success();

        } catch (AuthenticationFailedException e) {
            String errorMsg = "Email server authentication failed. Please check SMTP credentials in backend configuration. " +
                    "For Gmail, use an App Password instead of your regular password. " +
                    "Error: " + e.getMessage();
            logger.error("Authentication failed when sending supervisor notification email to {} for presenter {}. " +
                    "Full error: {}", supervisorEmail, presenterUsername, getFullErrorMessage(e), e);
            return EmailResult.failure(errorMsg, "EMAIL_AUTH_FAILED");
        } catch (MessagingException e) {
            String errorMsg = "Failed to send email: " + e.getMessage();
            logger.error("MessagingException when sending supervisor notification email to {} for presenter {}. " +
                    "Full error: {}", supervisorEmail, presenterUsername, getFullErrorMessage(e), e);
            return EmailResult.failure(errorMsg, "EMAIL_SEND_FAILED");
        } catch (Exception e) {
            String errorMsg = "Unexpected error sending email: " + e.getMessage();
            logger.error("Unexpected error sending supervisor notification email to {} for presenter {}. " +
                    "Full error: {}", supervisorEmail, presenterUsername, getFullErrorMessage(e), e);
            return EmailResult.failure(errorMsg, "EMAIL_ERROR");
        }
    }

    /**
     * Send notification email to student when their manual attendance request is approved or rejected
     *
     * @param studentEmail The student's email address
     * @param studentName The student's full name
     * @param isApproved true if approved, false if rejected
     * @param sessionDate The session date
     * @param sessionTime The session time
     * @param slotTitle The slot/seminar title
     * @param presenterName The presenter who made the decision
     * @return EmailResult with success status and error details
     */
    public EmailResult sendManualAttendanceNotificationEmail(String studentEmail, String studentName,
                                                              boolean isApproved, String sessionDate,
                                                              String sessionTime, String slotTitle,
                                                              String presenterName) {
        if (mailSender == null) {
            String errorMsg = "JavaMailSender is not configured. Email notification cannot be sent.";
            logger.warn("{} Manual attendance notification email cannot be sent to {}", errorMsg, studentEmail);
            return EmailResult.failure(errorMsg, "EMAIL_NOT_CONFIGURED");
        }

        if (studentEmail == null || studentEmail.trim().isEmpty()) {
            logger.debug("No student email provided. Skipping manual attendance notification.");
            return EmailResult.failure("No student email provided", "NO_STUDENT_EMAIL");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(studentEmail);

            String status = isApproved ? "Approved" : "Rejected";
            String subject = String.format("Manual Attendance Request %s - SemScan", status);
            helper.setSubject(subject);

            String greeting = studentName != null && !studentName.trim().isEmpty()
                    ? String.format("Hi %s,", studentName)
                    : "Hi,";

            String resultMessage = isApproved
                    ? "Your manual attendance request has been APPROVED. Your attendance has been recorded."
                    : "Your manual attendance request has been REJECTED. If you believe this is an error, please contact your presenter.";

            String body = String.format(
                "%s\n\n" +
                "%s\n\n" +
                "Session Details:\n" +
                "  • Seminar: %s\n" +
                "  • Date: %s\n" +
                "  • Time: %s\n" +
                "  • Decision by: %s\n\n" +
                "Best regards,\n" +
                "SemScan System",
                greeting,
                resultMessage,
                slotTitle != null ? slotTitle : "N/A",
                sessionDate != null ? sessionDate : "N/A",
                sessionTime != null ? sessionTime : "N/A",
                presenterName != null ? presenterName : "Presenter"
            );

            String htmlBody = convertToHtmlWithStatus(body, isApproved);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            logger.info("Manual attendance {} notification email sent to {} for session on {}",
                status.toLowerCase(), studentEmail, sessionDate);
            return EmailResult.success();

        } catch (AuthenticationFailedException e) {
            String errorMsg = "Email server authentication failed: " + e.getMessage();
            logger.error("Authentication failed when sending manual attendance notification to {}. Error: {}",
                studentEmail, getFullErrorMessage(e), e);
            return EmailResult.failure(errorMsg, "EMAIL_AUTH_FAILED");
        } catch (MessagingException e) {
            String errorMsg = "Failed to send email: " + e.getMessage();
            logger.error("MessagingException when sending manual attendance notification to {}. Error: {}",
                studentEmail, getFullErrorMessage(e), e);
            return EmailResult.failure(errorMsg, "EMAIL_SEND_FAILED");
        } catch (Exception e) {
            String errorMsg = "Unexpected error sending email: " + e.getMessage();
            logger.error("Unexpected error sending manual attendance notification to {}. Error: {}",
                studentEmail, getFullErrorMessage(e), e);
            return EmailResult.failure(errorMsg, "EMAIL_ERROR");
        }
    }

    /**
     * Convert plain text to HTML format with status-specific styling
     */
    private String convertToHtmlWithStatus(String plainText, boolean isApproved) {
        if (plainText == null || plainText.trim().isEmpty()) {
            return "";
        }

        String escaped = plainText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");

        String withBreaks = escaped.replace("\n", "<br>");

        String headerColor = isApproved ? "#4CAF50" : "#F44336"; // Green for approved, Red for rejected
        String statusText = isApproved ? "Approved" : "Rejected";

        return String.format(
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }\n" +
                "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }\n" +
                "        .header { background-color: %s; color: white; padding: 15px; text-align: center; }\n" +
                "        .content { padding: 20px; background-color: #f9f9f9; }\n" +
                "        .footer { text-align: center; padding: 10px; color: #666; font-size: 12px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <h2>Manual Attendance Request %s</h2>\n" +
                "        </div>\n" +
                "        <div class=\"content\">\n" +
                "            %s\n" +
                "        </div>\n" +
                "        <div class=\"footer\">\n" +
                "            <p>This is an automated message from the SemScan Attendance System.</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>",
                headerColor,
                statusText,
                withBreaks
        );
    }

    /**
     * Convert plain text to HTML format
     *
     * @param plainText Plain text content
     * @return HTML formatted content
     */
    private String convertToHtml(String plainText) {
        if (plainText == null || plainText.trim().isEmpty()) {
            return "";
        }

        // Escape HTML special characters
        String escaped = plainText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");

        // Convert line breaks to <br>
        String withBreaks = escaped.replace("\n", "<br>");

        // Wrap in HTML structure
        return String.format(
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }\n" +
                "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }\n" +
                "        .header { background-color: #4CAF50; color: white; padding: 15px; text-align: center; }\n" +
                "        .content { padding: 20px; background-color: #f9f9f9; }\n" +
                "        .footer { text-align: center; padding: 10px; color: #666; font-size: 12px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <h2>SemScan Attendance System</h2>\n" +
                "        </div>\n" +
                "        <div class=\"content\">\n" +
                "            %s\n" +
                "        </div>\n" +
                "        <div class=\"footer\">\n" +
                "            <p>This is an automated message from the SemScan Attendance System.</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>",
                withBreaks
        );
    }
}

