package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

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
            helper.setText(body);

            // Add attachment
            String contentType = format.equalsIgnoreCase("xlsx")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv";
            helper.addAttachment(filename, () -> new java.io.ByteArrayInputStream(fileData), contentType);

            // Send email
            mailSender.send(message);
            logger.info("Export email sent successfully to {} recipients for session {}", recipients.size(), sessionId);
            return true;

        } catch (MessagingException e) {
            logger.error("Failed to send export email for session {}", sessionId, e);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error sending export email for session {}", sessionId, e);
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
}

