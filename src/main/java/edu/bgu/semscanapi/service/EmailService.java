package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
        if (mailSender == null) {
            logger.warn("JavaMailSender is not configured. Email sending is disabled.");
            return false;
        }

        try {
            List<String> recipients = parseEmailRecipients(emailRecipients);
            if (recipients.isEmpty()) {
                logger.warn("No email recipients configured. Cannot send export email.");
                return false;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set sender
            helper.setFrom(fromEmail);

            // Set recipients
            helper.setTo(recipients.toArray(new String[0]));

            // Set subject
            String subject = String.format("Attendance Export - Session %d", sessionId);
            helper.setSubject(subject);

            // Set body
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
            // Convert plain text to HTML
            String htmlBody = convertToHtml(body);
            helper.setText(htmlBody, true); // true = HTML content

            // Add attachment
            String contentType = format.equalsIgnoreCase("xlsx")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv";
            helper.addAttachment(filename, () -> new java.io.ByteArrayInputStream(fileData), contentType);

            // Send email
            mailSender.send(message);
            logger.info("Export email sent successfully to {} recipients for session {}", recipients.size(), sessionId);
            return true;

        } catch (AuthenticationFailedException e) {
            logger.error("Authentication failed when sending export email for session {}. " +
                    "Email server authentication failed. Please check SMTP credentials in backend configuration. " +
                    "Full error: {}", sessionId, getFullErrorMessage(e), e);
            return false;
        } catch (MessagingException e) {
            logger.error("MessagingException when sending export email for session {}. Full error: {}", 
                sessionId, getFullErrorMessage(e), e);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error sending export email for session {}. Full error: {}", 
                sessionId, getFullErrorMessage(e), e);
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

            // Convert plain text to HTML
            String htmlBody = convertToHtml(body);
            helper.setText(htmlBody, true); // true = HTML content

            // Send email
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

