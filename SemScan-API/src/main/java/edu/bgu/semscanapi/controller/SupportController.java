package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.entity.EmailQueue;
import edu.bgu.semscanapi.service.AppConfigService;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.EmailQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private static final Logger logger = LoggerFactory.getLogger(SupportController.class);

    @Autowired
    private EmailQueueService emailQueueService;

    @Autowired
    private AppConfigService appConfigService;

    @Autowired
    private DatabaseLoggerService databaseLoggerService;

    @PostMapping("/report-bug")
    public ResponseEntity<?> reportBug(@RequestBody Map<String, Object> reportData) {
        try {
            String category = (String) reportData.getOrDefault("category", "other");
            String subject = (String) reportData.getOrDefault("subject", "Bug Report");
            String description = (String) reportData.getOrDefault("description", "");
            String username = (String) reportData.getOrDefault("username", "unknown");
            String platform = (String) reportData.getOrDefault("platform", "unknown");
            String browser = (String) reportData.getOrDefault("browser", "");
            String url = (String) reportData.getOrDefault("url", "");
            String timestamp = (String) reportData.getOrDefault("timestamp", "");

            // Get support email from config
            String supportEmail = appConfigService.getStringConfig("support_email", "benariet@bgu.ac.il");

            // Build email subject
            String emailSubject = String.format("[SemScan %s] %s - %s",
                    category.toUpperCase(), subject, username);

            // Build email body
            StringBuilder body = new StringBuilder();
            body.append("<h2>Bug Report from SemScan Web App</h2>");
            body.append("<hr>");
            body.append("<p><strong>Category:</strong> ").append(category).append("</p>");
            body.append("<p><strong>Subject:</strong> ").append(subject).append("</p>");
            body.append("<p><strong>Username:</strong> ").append(username).append("</p>");
            body.append("<p><strong>Platform:</strong> ").append(platform).append("</p>");
            body.append("<p><strong>Timestamp:</strong> ").append(timestamp).append("</p>");
            body.append("<hr>");
            body.append("<h3>Description:</h3>");
            body.append("<p>").append(description.replace("\n", "<br>")).append("</p>");
            body.append("<hr>");
            body.append("<h4>Technical Details:</h4>");
            body.append("<p><strong>Browser:</strong> ").append(browser).append("</p>");
            body.append("<p><strong>URL:</strong> ").append(url).append("</p>");

            // Queue the email (using correct method signature)
            emailQueueService.queueEmail(
                    EmailQueue.EmailType.BUG_REPORT,
                    supportEmail,
                    emailSubject,
                    body.toString(),
                    null,  // registrationId
                    null,  // slotId
                    username
            );

            // Log the report
            databaseLoggerService.logAction(
                    "INFO",
                    "BUG_REPORT_SUBMITTED",
                    String.format("Bug report submitted: %s - %s", category, subject),
                    username,
                    String.format("category=%s, subject=%s", category, subject)
            );

            logger.info("Bug report submitted by {} - Category: {}, Subject: {}", username, category, subject);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Bug report submitted successfully"
            ));

        } catch (Exception e) {
            logger.error("Failed to submit bug report", e);
            databaseLoggerService.logError(
                    "BUG_REPORT_FAILED",
                    "Failed to submit bug report: " + e.getMessage(),
                    e,
                    (String) reportData.getOrDefault("username", "unknown"),
                    null
            );
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to submit bug report: " + e.getMessage()
            ));
        }
    }
}
