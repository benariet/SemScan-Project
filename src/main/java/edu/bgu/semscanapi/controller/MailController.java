package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.service.MailService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Controller for sending emails via REST API
 */
@RestController
@RequestMapping("/api/v1/mail")
@CrossOrigin(origins = "*")
public class MailController {

    private static final Logger logger = LoggerUtil.getLogger(MailController.class);

    private final MailService mailService;

    public MailController(MailService mailService) {
        this.mailService = mailService;
    }

    /**
     * DTO for email request
     */
    public static class EmailRequest {
        @NotBlank(message = "Recipient email is required")
        @Email(message = "Invalid email format")
        private String to;

        @NotBlank(message = "Subject is required")
        private String subject;

        @NotBlank(message = "HTML content is required")
        private String htmlContent;

        private String plainTextContent;

        // File attachment support
        private String attachmentFileName;
        private String attachmentContentType;
        private String attachmentBase64; // Base64 encoded file content

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getHtmlContent() {
            return htmlContent;
        }

        public void setHtmlContent(String htmlContent) {
            this.htmlContent = htmlContent;
        }

        public String getPlainTextContent() {
            return plainTextContent;
        }

        public void setPlainTextContent(String plainTextContent) {
            this.plainTextContent = plainTextContent;
        }

        public String getAttachmentFileName() {
            return attachmentFileName;
        }

        public void setAttachmentFileName(String attachmentFileName) {
            this.attachmentFileName = attachmentFileName;
        }

        public String getAttachmentContentType() {
            return attachmentContentType;
        }

        public void setAttachmentContentType(String attachmentContentType) {
            this.attachmentContentType = attachmentContentType;
        }

        public String getAttachmentBase64() {
            return attachmentBase64;
        }

        public void setAttachmentBase64(String attachmentBase64) {
            this.attachmentBase64 = attachmentBase64;
        }
    }

    /**
     * DTO for bulk email request
     */
    public static class BulkEmailRequest {
        @NotBlank(message = "At least one recipient email is required")
        private List<@Email(message = "Invalid email format") String> to;

        @NotBlank(message = "Subject is required")
        private String subject;

        @NotBlank(message = "HTML content is required")
        private String htmlContent;

        private String plainTextContent;

        public List<String> getTo() {
            return to;
        }

        public void setTo(List<String> to) {
            this.to = to;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getHtmlContent() {
            return htmlContent;
        }

        public void setHtmlContent(String htmlContent) {
            this.htmlContent = htmlContent;
        }

        public String getPlainTextContent() {
            return plainTextContent;
        }

        public void setPlainTextContent(String plainTextContent) {
            this.plainTextContent = plainTextContent;
        }
    }

    /**
     * Send HTML email to a single recipient
     * POST /api/v1/mail/send
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendEmail(@Valid @RequestBody EmailRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/mail/send";
        LoggerUtil.logApiRequest(logger, "POST", endpoint, String.format("to=%s, subject=%s", request.getTo(), request.getSubject()));

        try {
            if (!mailService.isEmailConfigured()) {
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.SERVICE_UNAVAILABLE.value(), "Email service not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("success", false, "message", "Email service is not configured", "code", "EMAIL_NOT_CONFIGURED"));
            }

            boolean sent;
            
            // Check if attachment is provided
            if (request.getAttachmentFileName() != null && !request.getAttachmentFileName().trim().isEmpty() 
                && request.getAttachmentBase64() != null && !request.getAttachmentBase64().trim().isEmpty()) {
                // Send email with attachment
                sent = mailService.sendHtmlEmailWithAttachment(
                        request.getTo(),
                        request.getSubject(),
                        request.getHtmlContent(),
                        request.getAttachmentFileName(),
                        request.getAttachmentBase64(),
                        request.getAttachmentContentType()
                );
            } else {
                // Send email without attachment
                sent = mailService.sendHtmlEmail(
                        request.getTo(),
                        request.getSubject(),
                        request.getHtmlContent()
                );
            }

            if (sent) {
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.OK.value(), "Email sent successfully");
                Map<String, Object> response;
                if (request.getAttachmentFileName() != null) {
                    response = Map.of("success", true, "message", "Email sent successfully", "code", "EMAIL_SENT", 
                                     "attachment", request.getAttachmentFileName());
                } else {
                    response = Map.of("success", true, "message", "Email sent successfully", "code", "EMAIL_SENT");
                }
                return ResponseEntity.ok(response);
            } else {
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to send email");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("success", false, "message", "Failed to send email", "code", "EMAIL_ERROR"));
            }
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error during send email", ex);
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Internal Server Error", "code", "INTERNAL_ERROR"));
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Send HTML email to multiple recipients
     * POST /api/v1/mail/send-bulk
     */
    @PostMapping("/send-bulk")
    public ResponseEntity<Map<String, Object>> sendBulkEmail(@Valid @RequestBody BulkEmailRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/mail/send-bulk";
        LoggerUtil.logApiRequest(logger, "POST", endpoint, String.format("to=%d recipients, subject=%s", request.getTo().size(), request.getSubject()));

        try {
            if (!mailService.isEmailConfigured()) {
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.SERVICE_UNAVAILABLE.value(), "Email service not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("success", false, "message", "Email service is not configured", "code", "EMAIL_NOT_CONFIGURED"));
            }

            boolean sent = mailService.sendHtmlEmail(
                    request.getTo(),
                    request.getSubject(),
                    request.getHtmlContent(),
                    request.getPlainTextContent()
            );

            if (sent) {
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.OK.value(), "Email sent successfully");
                return ResponseEntity.ok(Map.of("success", true, "message", "Email sent successfully", "code", "EMAIL_SENT"));
            } else {
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to send email");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("success", false, "message", "Failed to send email", "code", "EMAIL_ERROR"));
            }
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error during send bulk email", ex);
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Internal Server Error", "code", "INTERNAL_ERROR"));
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Check if email service is configured
     * GET /api/v1/mail/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getEmailStatus() {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/mail/status";
        LoggerUtil.logApiRequest(logger, "GET", endpoint, null);

        try {
            boolean configured = mailService.isEmailConfigured();
            String fromEmail = mailService.getFromEmail();

            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.OK.value(), "Email status retrieved");
            return ResponseEntity.ok(Map.of(
                    "configured", configured,
                    "fromEmail", fromEmail != null ? fromEmail : "Not configured"
            ));
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error during get email status", ex);
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal Server Error"));
        } finally {
            LoggerUtil.clearContext();
        }
    }
}

