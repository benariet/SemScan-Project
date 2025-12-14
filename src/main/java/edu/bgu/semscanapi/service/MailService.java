package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Base64;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;

/**
 * Service for sending HTML emails via SMTP
 */
@Service
public class MailService {

    private static final Logger logger = LoggerUtil.getLogger(MailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired(required = false)
    private DatabaseLoggerService databaseLoggerService;

    @Value("${spring.mail.from:SemScan Attendance System <noreply@semscan.com>}")
    private String fromEmail;

    @Value("${app.email.monitoring-cc:benariet@bgu.ac.il}")
    private String monitoringEmail;

    @PostConstruct
    public void init() {
        logger.info("MailService initialized - Monitoring BCC email: {}", 
                monitoringEmail != null && !monitoringEmail.trim().isEmpty() ? monitoringEmail : "NOT CONFIGURED");
    }

    /**
     * Send HTML email to a single recipient
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param htmlContent HTML content of the email
     * @return true if email was sent successfully, false otherwise
     */
    public boolean sendHtmlEmail(String to, String subject, String htmlContent) {
        return sendHtmlEmail(List.of(to), subject, htmlContent, null);
    }

    /**
     * Send HTML email to multiple recipients
     *
     * @param to List of recipient email addresses
     * @param subject Email subject
     * @param htmlContent HTML content of the email
     * @param plainTextContent Optional plain text fallback content
     * @return true if email was sent successfully, false otherwise
     */
    public boolean sendHtmlEmail(List<String> to, String subject, String htmlContent, String plainTextContent) {
        if (mailSender == null) {
            logger.warn("JavaMailSender is not configured. Email sending is disabled.");
            return false;
        }

        if (to == null || to.isEmpty()) {
            logger.warn("No recipients provided. Cannot send email.");
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set sender
            helper.setFrom(fromEmail);

            // Set recipients
            helper.setTo(to.toArray(new String[0]));

            // Add monitoring BCC if configured
            if (monitoringEmail != null && !monitoringEmail.trim().isEmpty()) {
                try {
                    helper.setBcc(monitoringEmail);
                    logger.info("Added monitoring BCC: {} (email to: {})", monitoringEmail, to);
                } catch (MessagingException e) {
                    logger.error("Failed to set BCC to {}: {}", monitoringEmail, e.getMessage());
                    // Continue sending email even if BCC fails
                }
            } else {
                logger.warn("Monitoring email not configured - BCC will not be added. Check app.email.monitoring-cc property.");
            }

            // Set subject
            helper.setSubject(subject);

            // Set HTML content
            helper.setText(htmlContent, plainTextContent != null ? plainTextContent : htmlContent);

            // Send email
            mailSender.send(message);
            logger.info("HTML email sent successfully to {} recipients: {}", to.size(), to);
            
            // Log to app_log database
            if (databaseLoggerService != null) {
                String recipientsStr = String.join(", ", to);
                String bccInfo = monitoringEmail != null && !monitoringEmail.trim().isEmpty() 
                    ? String.format(" (BCC: %s)", monitoringEmail) : "";
                databaseLoggerService.logBusinessEvent("EMAIL_SENT",
                        String.format("Email sent to: %s%s, Subject: %s", recipientsStr, bccInfo, subject),
                        null);
            }
            
            return true;

        } catch (MessagingException e) {
            logger.error("Failed to send HTML email to {}", to, e);
            
            // Log error to app_log database
            if (databaseLoggerService != null) {
                String recipientsStr = String.join(", ", to);
                databaseLoggerService.logError("EMAIL_SEND_FAILED",
                        String.format("Failed to send email to: %s, Subject: %s, Error: %s", 
                                recipientsStr, subject, e.getMessage()),
                        e, null, String.format("recipients=%s,subject=%s", recipientsStr, subject));
            }
            
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error sending HTML email to {}", to, e);
            
            // Log error to app_log database
            if (databaseLoggerService != null) {
                String recipientsStr = String.join(", ", to);
                databaseLoggerService.logError("EMAIL_SEND_ERROR",
                        String.format("Unexpected error sending email to: %s, Subject: %s, Error: %s", 
                                recipientsStr, subject, e.getMessage()),
                        e, null, String.format("recipients=%s,subject=%s", recipientsStr, subject));
            }
            
            return false;
        }
    }

    /**
     * Send HTML email with attachment (base64 encoded)
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param htmlContent HTML content of the email
     * @param attachmentFileName Name of the attachment file
     * @param attachmentBase64 Base64 encoded file content
     * @param attachmentContentType Content type of the attachment (e.g., "application/pdf", "text/csv")
     * @return true if email was sent successfully, false otherwise
     */
    public boolean sendHtmlEmailWithAttachment(String to, String subject, String htmlContent,
                                               String attachmentFileName, String attachmentBase64, String attachmentContentType) {
        if (mailSender == null) {
            logger.warn("JavaMailSender is not configured. Email sending is disabled.");
            return false;
        }

        if (to == null || to.trim().isEmpty()) {
            logger.warn("No recipient provided. Cannot send email.");
            return false;
        }

        try {
            // Decode base64 to byte array
            byte[] fileBytes;
            try {
                fileBytes = Base64.getDecoder().decode(attachmentBase64);
            } catch (IllegalArgumentException e) {
                logger.error("Failed to decode base64 attachment: {}", e.getMessage());
                return false;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set sender
            helper.setFrom(fromEmail);

            // Set recipient
            helper.setTo(to);

            // Add monitoring BCC if configured
            if (monitoringEmail != null && !monitoringEmail.trim().isEmpty()) {
                try {
                    helper.setBcc(monitoringEmail);
                    logger.info("Added monitoring BCC: {} (email to: {})", monitoringEmail, to);
                } catch (MessagingException e) {
                    logger.error("Failed to set BCC to {}: {}", monitoringEmail, e.getMessage());
                    // Continue sending email even if BCC fails
                }
            } else {
                logger.warn("Monitoring email not configured - BCC will not be added. Check app.email.monitoring-cc property.");
            }

            // Set subject
            helper.setSubject(subject);

            // Set HTML content
            helper.setText(htmlContent, true);

            // Determine content type
            String contentType = (attachmentContentType != null && !attachmentContentType.trim().isEmpty())
                ? attachmentContentType
                : "application/octet-stream";

            // Create ByteArrayResource from decoded bytes
            ByteArrayResource attachment = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return attachmentFileName;
                }
            };

            // Attach file
            helper.addAttachment(attachmentFileName, attachment, contentType);

            logger.info("Email attachment added: {} ({} bytes, {})", attachmentFileName, fileBytes.length, contentType);

            // Send email
            mailSender.send(message);
            logger.info("HTML email with attachment sent successfully to {}", to);
            return true;

        } catch (MessagingException e) {
            logger.error("Failed to send HTML email with attachment to {}", to, e);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error sending HTML email with attachment to {}", to, e);
            return false;
        }
    }

    /**
     * Send HTML email with attachment (byte array)
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param htmlContent HTML content of the email
     * @param attachmentName Name of the attachment file
     * @param attachmentData Attachment file content as byte array
     * @param attachmentContentType Content type of the attachment (e.g., "application/pdf", "text/csv")
     * @return true if email was sent successfully, false otherwise
     */
    public boolean sendHtmlEmailWithAttachment(String to, String subject, String htmlContent,
                                               String attachmentName, byte[] attachmentData, String attachmentContentType) {
        if (mailSender == null) {
            logger.warn("JavaMailSender is not configured. Email sending is disabled.");
            return false;
        }

        if (to == null || to.trim().isEmpty()) {
            logger.warn("No recipient provided. Cannot send email.");
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set sender
            helper.setFrom(fromEmail);

            // Set recipient
            helper.setTo(to);

            // Add monitoring BCC if configured
            if (monitoringEmail != null && !monitoringEmail.trim().isEmpty()) {
                try {
                    helper.setBcc(monitoringEmail);
                    logger.info("Added monitoring BCC: {} (email to: {})", monitoringEmail, to);
                } catch (MessagingException e) {
                    logger.error("Failed to set BCC to {}: {}", monitoringEmail, e.getMessage());
                    // Continue sending email even if BCC fails
                }
            } else {
                logger.warn("Monitoring email not configured - BCC will not be added. Check app.email.monitoring-cc property.");
            }

            // Set subject
            helper.setSubject(subject);

            // Set HTML content
            helper.setText(htmlContent, true);

            // Add attachment
            if (attachmentData != null && attachmentName != null) {
                helper.addAttachment(attachmentName, () -> new java.io.ByteArrayInputStream(attachmentData), attachmentContentType);
            }

            // Send email
            mailSender.send(message);
            logger.info("HTML email with attachment sent successfully to {}", to);
            
            // Log to app_log database
            if (databaseLoggerService != null) {
                String bccInfo = monitoringEmail != null && !monitoringEmail.trim().isEmpty() 
                    ? String.format(" (BCC: %s)", monitoringEmail) : "";
                databaseLoggerService.logBusinessEvent("EMAIL_SENT_WITH_ATTACHMENT",
                        String.format("Email with attachment sent to: %s%s, Subject: %s, Attachment: %s", 
                                to, bccInfo, subject, attachmentName),
                        null);
            }
            
            return true;

        } catch (MessagingException e) {
            logger.error("Failed to send HTML email with attachment to {}", to, e);
            
            // Log error to app_log database
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("EMAIL_SEND_WITH_ATTACHMENT_FAILED",
                        String.format("Failed to send email with attachment to: %s, Subject: %s, Error: %s", 
                                to, subject, e.getMessage()),
                        e, null, String.format("recipient=%s,subject=%s,attachment=%s", to, subject, attachmentName));
            }
            
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error sending HTML email with attachment to {}", to, e);
            
            // Log error to app_log database
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("EMAIL_SEND_WITH_ATTACHMENT_ERROR",
                        String.format("Unexpected error sending email with attachment to: %s, Subject: %s, Error: %s", 
                                to, subject, e.getMessage()),
                        e, null, String.format("recipient=%s,subject=%s,attachment=%s", to, subject, attachmentName));
            }
            
            return false;
        }
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
     * Get the configured sender email address
     *
     * @return Sender email address
     */
    public String getFromEmail() {
        return fromEmail;
    }

    /**
     * Get the configured monitoring BCC email address
     *
     * @return Monitoring BCC email address
     */
    public String getMonitoringEmail() {
        return monitoringEmail;
    }
}

