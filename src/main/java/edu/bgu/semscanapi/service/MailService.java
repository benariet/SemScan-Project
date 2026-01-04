package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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

    @Autowired
    private Environment environment;

    @Autowired(required = false)
    private AppConfigService appConfigService;

    @Value("${spring.mail.from:SemScan Attendance System <noreply@semscan.com>}")
    private String fromEmail;

    @Value("${app.email.monitoring-cc:benariet@bgu.ac.il}")
    private String monitoringEmail;

    @PostConstruct
    public void init() {
        logger.info("MailService initialized - Monitoring BCC email: {}", 
                monitoringEmail != null && !monitoringEmail.trim().isEmpty() ? monitoringEmail : "NOT CONFIGURED");
        
        // Log SMTP configuration (with masked password for security)
        String smtpHost = environment.getProperty("spring.mail.host", "NOT SET");
        String smtpPort = environment.getProperty("spring.mail.port", "NOT SET");
        String smtpUsername = environment.getProperty("spring.mail.username", "NOT SET");
        String smtpPassword = environment.getProperty("spring.mail.password", "NOT SET");
        String smtpFrom = environment.getProperty("spring.mail.from", "NOT SET");
        
        // Mask password for logging (show first 2 and last 2 characters)
        String maskedPassword = "NOT SET";
        if (smtpPassword != null && !smtpPassword.equals("NOT SET") && smtpPassword.length() > 4) {
            maskedPassword = smtpPassword.substring(0, 2) + "***" + smtpPassword.substring(smtpPassword.length() - 2);
        } else if (smtpPassword != null && !smtpPassword.equals("NOT SET")) {
            maskedPassword = "***";
        }
        
        logger.info("📧 SMTP Configuration:");
        logger.info("   Host: {}", smtpHost);
        logger.info("   Port: {}", smtpPort);
        logger.info("   Username: {}", smtpUsername);
        logger.info("   Password: {} (masked: {})", maskedPassword, smtpPassword != null && !smtpPassword.equals("NOT SET") ? "YES" : "NO");
        logger.info("   From: {}", smtpFrom);
        logger.info("   STARTTLS: {}", environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable", "NOT SET"));
        logger.info("   Auth: {}", environment.getProperty("spring.mail.properties.mail.smtp.auth", "NOT SET"));
        
        // Log to app_logs
        if (databaseLoggerService != null) {
            databaseLoggerService.logAction("INFO", "EMAIL_CONFIG_LOADED",
                    String.format("SMTP Config - Host: %s, Port: %s, Username: %s, Password: %s (masked), From: %s",
                            smtpHost, smtpPort, smtpUsername, maskedPassword, smtpFrom),
                    null, String.format("host=%s,port=%s,username=%s,passwordSet=%s", 
                            smtpHost, smtpPort, smtpUsername, smtpPassword != null && !smtpPassword.equals("NOT SET") ? "YES" : "NO"));
        }
        
        // CRITICAL: Check if JavaMailSender is configured
        if (mailSender == null) {
            logger.error("CRITICAL: JavaMailSender is NULL! Email sending will fail. Check Spring Mail configuration.");
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("EMAIL_MAILSERVICE_INIT_MAILSENDER_NULL",
                        "JavaMailSender is null during MailService initialization. Email sending is disabled.",
                        null, null, "mailSender=null,check=spring.mail.configuration");
            }
        } else {
            logger.info("JavaMailSender is configured and ready");
            if (databaseLoggerService != null) {
                databaseLoggerService.logAction("INFO", "EMAIL_MAILSERVICE_INIT_SUCCESS",
                        "MailService initialized successfully with JavaMailSender",
                        null, "mailSender=configured");
            }
        }
        
        // Log email configuration
        logger.info("Email configuration - From: {}, Monitoring BCC: {}", fromEmail, monitoringEmail);
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
        logger.info("📧 [MailService] sendHtmlEmail() called - recipients: {}, subject: {}", to, subject);
        
        if (mailSender == null) {
            logger.error("📧 [MailService] JavaMailSender is not configured. Email sending is disabled.");
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("EMAIL_MAILSENDER_NULL",
                        "JavaMailSender is not configured. Email sending is disabled.",
                        null, null, "mailSender=null");
            }
            return false;
        }
        logger.info("📧 [MailService] JavaMailSender is configured");

        if (to == null || to.isEmpty()) {
            logger.error("📧 [MailService] No recipients provided. Cannot send email.");
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("EMAIL_NO_RECIPIENTS",
                        "No recipients provided. Cannot send email.",
                        null, null, "recipients=null_or_empty");
            }
            return false;
        }
        logger.info("📧 [MailService] Recipients validated: {}", to);

        long startTime = System.currentTimeMillis();
        try {
            logger.info("📧 [MailService] Creating MimeMessage...");
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            logger.info("📧 [MailService] MimeMessage created");

            // Configure email sender (from address) and recipient list (to addresses)
            String emailFromName = appConfigService != null 
                    ? appConfigService.getStringConfig("email_from_name", null) 
                    : null;
            
            if (emailFromName != null && !emailFromName.trim().isEmpty()) {
                // Use configured from name with email address
                String fromAddress = fromEmail.contains("<") ? fromEmail : emailFromName + " <" + fromEmail + ">";
                logger.info("📧 [MailService] Setting from address with name: {}", fromAddress);
                helper.setFrom(fromAddress);
                
                if (databaseLoggerService != null) {
                    databaseLoggerService.logAction("INFO", "EMAIL_CONFIG_FROM_NAME_USED",
                            String.format("Using email_from_name from app_config: %s", emailFromName),
                            null, String.format("fromName=%s,fromEmail=%s", emailFromName, fromEmail));
                }
            } else {
                logger.info("📧 [MailService] Setting from address: {}", fromEmail);
            helper.setFrom(fromEmail);
            }
            
            logger.info("📧 [MailService] Setting to addresses: {}", to);
            helper.setTo(to.toArray(new String[0]));

            // Set reply-to address if configured
            String emailReplyTo = appConfigService != null 
                    ? appConfigService.getStringConfig("email_reply_to", null) 
                    : null;
            if (emailReplyTo != null && !emailReplyTo.trim().isEmpty()) {
                try {
                    logger.info("📧 [MailService] Setting reply-to: {}", emailReplyTo);
                    helper.setReplyTo(emailReplyTo);
                    
                    if (databaseLoggerService != null) {
                        databaseLoggerService.logAction("INFO", "EMAIL_CONFIG_REPLY_TO_USED",
                                String.format("Using email_reply_to from app_config: %s", emailReplyTo),
                                null, String.format("replyTo=%s", emailReplyTo));
                    }
                } catch (MessagingException e) {
                    logger.error("📧 [MailService] Failed to set reply-to to {}: {}", emailReplyTo, e.getMessage());
                    if (databaseLoggerService != null) {
                        databaseLoggerService.logError("EMAIL_CONFIG_REPLY_TO_FAILED",
                                String.format("Failed to set reply-to address from app_config: %s", emailReplyTo),
                                e, null, String.format("replyTo=%s", emailReplyTo));
                    }
                    // Continue sending email even if reply-to fails
                }
            }

            // Add BCC recipients if configured (from app_config or fallback to monitoringEmail)
            String emailBccList = appConfigService != null 
                    ? appConfigService.getStringConfig("email_bcc_list", null) 
                    : null;
            
            String bccToUse = (emailBccList != null && !emailBccList.trim().isEmpty()) 
                    ? emailBccList 
                    : (monitoringEmail != null && !monitoringEmail.trim().isEmpty() ? monitoringEmail : null);
            
            if (bccToUse != null && !bccToUse.trim().isEmpty()) {
                try {
                    // Parse comma-separated BCC list
                    String[] bccAddresses = bccToUse.split(",");
                    List<String> bccList = new java.util.ArrayList<>();
                    for (String bcc : bccAddresses) {
                        String trimmed = bcc.trim();
                        if (!trimmed.isEmpty()) {
                            bccList.add(trimmed);
                        }
                    }
                    
                    if (!bccList.isEmpty()) {
                        logger.info("📧 [MailService] Adding BCC recipients: {}", bccList);
                        helper.setBcc(bccList.toArray(new String[0]));
                        logger.info("📧 [MailService] Added BCC: {} (email to: {})", bccList, to);
                        
                        if (databaseLoggerService != null) {
                            String source = (emailBccList != null && !emailBccList.trim().isEmpty()) ? "app_config" : "properties";
                            databaseLoggerService.logAction("INFO", "EMAIL_CONFIG_BCC_USED",
                                    String.format("Using BCC from %s: %s", source, bccList),
                                    null, String.format("bccList=%s,source=%s", String.join(",", bccList), source));
                        }
                    }
                } catch (MessagingException e) {
                    logger.error("📧 [MailService] Failed to set BCC to {}: {}", bccToUse, e.getMessage());
                    if (databaseLoggerService != null) {
                        databaseLoggerService.logError("EMAIL_CONFIG_BCC_FAILED",
                                String.format("Failed to set BCC from app_config: %s", bccToUse),
                                e, null, String.format("bccList=%s", bccToUse));
                    }
                    // Continue sending email even if BCC fails
                }
            } else {
                logger.debug("📧 [MailService] No BCC configured - skipping BCC");
            }

            // Set email subject line and HTML body content (with optional plain text fallback)
            logger.info("📧 [MailService] Setting subject: {}", subject);
            helper.setSubject(subject);
            logger.info("📧 [MailService] Setting HTML content (length: {} chars)", htmlContent != null ? htmlContent.length() : 0);
            String htmlText = htmlContent != null ? htmlContent : "";
            String plainText = plainTextContent != null ? plainTextContent : htmlText;
            helper.setText(htmlText, plainText);

            // Send email via configured SMTP server (JavaMailSender)
            logger.info("📧 [MailService] Sending email via JavaMailSender to SMTP server...");
            mailSender.send(message);
            long duration = System.currentTimeMillis() - startTime;
            logger.info("📧 [MailService] HTML email sent successfully to {} recipients: {} (took {}ms)", 
                    to.size(), to, duration);
            
            // Log to app_log database
            if (databaseLoggerService != null) {
                String recipientsStr = String.join(", ", to);
                String bccInfo = bccToUse != null && !bccToUse.trim().isEmpty() 
                    ? String.format(" (BCC: %s)", bccToUse) : "";
                databaseLoggerService.logBusinessEvent("EMAIL_SENT",
                        String.format("Email sent to: %s%s, Subject: %s (took %dms)", recipientsStr, bccInfo, subject, duration),
                        null);
            }
            
            return true;

        } catch (jakarta.mail.AuthenticationFailedException e) {
            long duration = System.currentTimeMillis() - startTime;
            
            // Check if the root cause is a timeout
            Throwable rootCause = e;
            boolean isTimeout = false;
            String rootCauseMessage = "";
            while (rootCause != null) {
                if (rootCause instanceof java.net.SocketTimeoutException) {
                    isTimeout = true;
                    rootCauseMessage = rootCause.getMessage();
                    break;
                }
                rootCause = rootCause.getCause();
            }
            
            String errorType = isTimeout ? "TIMEOUT during authentication" : "Authentication failed";
            String errorDetails;
            String troubleshootingHint;
            
            if (isTimeout) {
                errorDetails = String.format("SMTP server timeout during authentication (likely network/SMTP server slow response). " +
                        "Timeout after %dms. Original error: %s", duration, e.getMessage());
                troubleshootingHint = "Check network connectivity, firewall settings, and SMTP server response time. " +
                        "Consider increasing timeout values if server is consistently slow.";
            } else {
                // Check for authentication mechanism mismatch
                boolean isAuthMismatch = e.getMessage() != null && 
                    (e.getMessage().contains("No authentication mechanisms") || 
                     e.getMessage().contains("authentication mechanism"));
                
                if (isAuthMismatch) {
                    errorDetails = String.format("SMTP authentication mechanism mismatch. Error: %s", e.getMessage());
                    troubleshootingHint = "Authentication Mechanism Issues:\n" +
                            "  1. Office 365 SMTP with basic auth uses PLAIN or LOGIN (not XOAUTH2)\n" +
                            "  2. Remove 'mail.smtp.auth.mechanisms' property to let JavaMail auto-detect\n" +
                            "  3. If using OAuth2, you need OAuth tokens (not username/password)\n" +
                            "  4. Verify STARTTLS is enabled (required for Office 365 port 587)";
                } else {
                    errorDetails = String.format("SMTP authentication failed - Office 365 rejected credentials. Error: %s", e.getMessage());
                    troubleshootingHint = "Office 365 Authentication Issues:\n" +
                            "  1. Verify username/password in application-global.properties\n" +
                            "  2. If MFA is enabled, use App-Specific Password (not regular password)\n" +
                            "  3. Check if account password has expired\n" +
                            "  4. Verify account is not locked or disabled\n" +
                            "  5. Check Office 365 admin security settings (SMTP AUTH may be disabled)\n" +
                            "  6. Verify account has 'Send As' permission for shared mailbox (if using shared mailbox)";
                }
            }
            
            logger.error("📧 [MailService] {} after {}ms\n" +
                    "Details: {}\n" +
                    "Troubleshooting: {}\n" +
                    "Full error:", errorType, duration, errorDetails, troubleshootingHint, e);
            
            if (databaseLoggerService != null) {
                String recipientsStr = String.join(", ", to);
                String errorCode = isTimeout ? "EMAIL_AUTH_TIMEOUT" : "EMAIL_AUTH_FAILED";
                databaseLoggerService.logError(errorCode,
                        String.format("%s | %s", errorDetails, troubleshootingHint),
                        e, null, String.format("recipients=%s,subject=%s,duration=%dms,isTimeout=%s,rootCause=%s", 
                                recipientsStr, subject, duration, isTimeout, rootCauseMessage));
            }
            return false;
        } catch (jakarta.mail.SendFailedException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("📧 [MailService] Email send failed after {}ms - Invalid recipient or SMTP error. Error: {}", 
                    duration, e.getMessage(), e);
            if (databaseLoggerService != null) {
                String recipientsStr = String.join(", ", to);
                databaseLoggerService.logError("EMAIL_SEND_FAILED",
                        String.format("Email send failed: %s", e.getMessage()),
                        e, null, String.format("recipients=%s,subject=%s,duration=%dms", recipientsStr, subject, duration));
            }
            return false;
        } catch (MessagingException e) {
            long duration = System.currentTimeMillis() - startTime;
            
            // Check root cause for timeout or SSL issues
            Throwable rootCause = e;
            boolean isTimeout = false;
            boolean isSslError = false;
            String sslErrorDetails = "";
            
            while (rootCause != null) {
                if (rootCause instanceof java.net.SocketTimeoutException) {
                    isTimeout = true;
                    break;
                }
                if (rootCause instanceof javax.net.ssl.SSLException) {
                    isSslError = true;
                    sslErrorDetails = rootCause.getMessage();
                    break;
                }
                rootCause = rootCause.getCause();
            }
            
            String errorType;
            String troubleshootingHint;
            
            if (isSslError) {
                errorType = "SSL/TLS Connection Error";
                troubleshootingHint = "SSL/TLS Handshake Failed:\n" +
                        "  1. Office 365 port 587 uses STARTTLS (not direct SSL)\n" +
                        "  2. Check that 'socketFactory.class' is NOT set in SMTP properties\n" +
                        "  3. Verify STARTTLS is enabled: mail.smtp.starttls.enable=true\n" +
                        "  4. Check firewall/proxy settings - they may interfere with STARTTLS\n" +
                        "  5. Error: " + sslErrorDetails;
            } else if (isTimeout) {
                errorType = "TIMEOUT";
                troubleshootingHint = "Check network connectivity, firewall settings, and SMTP server response time.";
            } else {
                errorType = "MessagingException";
                troubleshootingHint = "General SMTP messaging error. Check SMTP server configuration and network connectivity.";
            }
            
            logger.error("📧 [MailService] {} sending HTML email to {} after {}ms\n" +
                    "Error: {}\n" +
                    "Troubleshooting: {}", 
                    errorType, to, duration, e.getMessage(), troubleshootingHint, e);
            
            // Log error to app_log database
            if (databaseLoggerService != null) {
                String recipientsStr = String.join(", ", to);
                String errorCode;
                if (isSslError) {
                    errorCode = "EMAIL_SSL_ERROR";
                } else if (isTimeout) {
                    errorCode = "EMAIL_SEND_TIMEOUT";
                } else {
                    errorCode = "EMAIL_SEND_FAILED";
                }
                databaseLoggerService.logError(errorCode,
                        String.format("Failed to send email to: %s, Subject: %s, Error: %s | %s", 
                                recipientsStr, subject, e.getMessage(), troubleshootingHint),
                        e, null, String.format("recipients=%s,subject=%s,errorType=%s,isTimeout=%s,isSslError=%s,duration=%dms", 
                                recipientsStr, subject, errorType, isTimeout, isSslError, duration));
            }
            
            return false;
        } catch (Exception e) {
            logger.error("📧 [MailService] Unexpected error sending HTML email to {} - Error: {}", to, e.getMessage(), e);
            
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

