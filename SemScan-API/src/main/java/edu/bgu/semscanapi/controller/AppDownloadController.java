package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.MailService;
import edu.bgu.semscanapi.util.LoggerUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for app download page and sending download links via email
 */
@RestController
@CrossOrigin(origins = "*")
public class AppDownloadController {

    private static final Logger logger = LoggerUtil.getLogger(AppDownloadController.class);

    @Autowired
    private GlobalConfig globalConfig;

    @Autowired
    private MailService mailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseLoggerService databaseLoggerService;

    /**
     * Serve the APK file from server filesystem
     * GET /app/download/semscan.apk
     * APK file location: /opt/semscan-api/semscan.apk
     */
    @GetMapping("/app/download/semscan.apk")
    public ResponseEntity<?> downloadApk(HttpServletRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/app/download/semscan.apk";
        LoggerUtil.logApiRequest(logger, "GET", endpoint, null);

        // Extract client info for logging
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        try {
            // APK file location on server
            Path apkPath = Paths.get("/opt/semscan-api/semscan.apk");
            File apkFile = apkPath.toFile();

            if (!apkFile.exists() || !apkFile.isFile()) {
                logger.warn("APK file not found at: {}", apkPath.toAbsolutePath());
                LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.NOT_FOUND.value(), "APK file not found");
                String errorHtml = "<!DOCTYPE html>\n" +
                        "<html lang=\"en\">\n" +
                        "<head>\n" +
                        "    <meta charset=\"UTF-8\">\n" +
                        "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                        "    <title>APK Not Available</title>\n" +
                        "    <style>\n" +
                        "        body { font-family: Arial, sans-serif; text-align: center; padding: 50px; background-color: #f4f4f4; }\n" +
                        "        .error-box { background: white; padding: 30px; border-radius: 10px; display: inline-block; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n" +
                        "        h1 { color: #d32f2f; }\n" +
                        "        p { color: #666; font-size: 16px; }\n" +
                        "    </style>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "    <div class=\"error-box\">\n" +
                        "        <h1>⚠️ APK File Not Available</h1>\n" +
                        "        <p>The SemScan APK file has not been uploaded yet.</p>\n" +
                        "        <p>Please contact the administrator.</p>\n" +
                        "        <p><a href=\"/app/download\">← Back to Download Page</a></p>\n" +
                        "    </div>\n" +
                        "</body>\n" +
                        "</html>";
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                        .body(errorHtml);
            }

            Resource resource = new FileSystemResource(apkFile);
            long fileSize = Files.size(apkPath);

            logger.info("Serving APK file: {} ({} bytes)", apkPath.toAbsolutePath(), fileSize);
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.OK.value(),
                    String.format("APK file served (%d bytes)", fileSize));

            // Log download to database
            String device = parseUserAgent(userAgent);
            String payload = String.format("ip=%s, device=%s", clientIp, device);
            databaseLoggerService.logAction("INFO", "APK_DOWNLOAD", "APK downloaded", null, payload);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"semscan.apk\"")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                    .body(resource);
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error serving APK file", ex);
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Serve the app download page
     * GET /app/download
     */
    @GetMapping(value = "/app/download", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getDownloadPage() {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/app/download";
        LoggerUtil.logApiRequest(logger, "GET", endpoint, null);

        try {
            String serverUrl = globalConfig.getServerUrl();
            // Remove trailing slash if present to avoid double slashes
            if (serverUrl.endsWith("/")) {
                serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
            }
            String downloadUrl = serverUrl + "/app/download/semscan.apk";
            
            String html = generateDownloadPageHtml(downloadUrl);
            
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.OK.value(), "Download page served");
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .body(html);
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error serving download page", ex);
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .body("<html><body><h1>Error</h1><p>Failed to load download page.</p></body></html>");
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Send download link email to users
     * POST /api/v1/app/send-download-link?filter=all|participants|presenters
     * 
     * @param filter Optional filter: "all" (default), "participants", or "presenters"
     */
    @PostMapping("/api/v1/app/send-download-link")
    public ResponseEntity<Map<String, Object>> sendDownloadLinkToUsers(
            @RequestParam(value = "filter", defaultValue = "all") String filter) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = "/api/v1/app/send-download-link";
        LoggerUtil.logApiRequest(logger, "POST", endpoint, "filter=" + filter);

        try {
            if (!mailService.isEmailConfigured()) {
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.SERVICE_UNAVAILABLE.value(), "Email service not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("success", false, "message", "Email service is not configured", "code", "EMAIL_NOT_CONFIGURED"));
            }

            // Get users based on filter
            List<User> users;
            switch (filter.toLowerCase()) {
                case "participants":
                    users = userRepository.findByIsParticipantTrue();
                    logger.info("Filtering by participants only");
                    break;
                case "presenters":
                    users = userRepository.findByIsPresenterTrue();
                    logger.info("Filtering by presenters only");
                    break;
                case "all":
                default:
                    users = userRepository.findAll();
                    logger.info("Sending to all users");
                    break;
            }

            // Extract emails from users
            List<String> userEmails = users.stream()
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.trim().isEmpty())
                    .collect(Collectors.toList());

            if (userEmails.isEmpty()) {
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.BAD_REQUEST.value(), 
                        String.format("No users with email addresses found for filter: %s", filter));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success", false, 
                                "message", String.format("No users with email addresses found for filter: %s", filter), 
                                "code", "NO_USERS",
                                "filter", filter
                        ));
            }

            // Generate download link
            String serverUrl = globalConfig.getServerUrl();
            // Remove trailing slash if present to avoid double slashes
            if (serverUrl.endsWith("/")) {
                serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
            }
            String downloadLink = serverUrl + "/app/download";

            // Generate email content
            String subject = "SemScan App - Download Now";
            String htmlContent = generateDownloadEmailHtml(downloadLink);
            String plainTextContent = generateDownloadEmailPlainText(downloadLink);

            // Send email to all users
            boolean sent = mailService.sendHtmlEmail(userEmails, subject, htmlContent, plainTextContent);

            if (sent) {
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.OK.value(), 
                        String.format("Download link email sent to %d users (filter: %s)", userEmails.size(), filter));
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Download link email sent successfully",
                        "code", "EMAIL_SENT",
                        "recipientsCount", userEmails.size(),
                        "filter", filter
                ));
            } else {
                LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to send email");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("success", false, "message", "Failed to send email", "code", "EMAIL_ERROR"));
            }
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error sending download link emails", ex);
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Internal Server Error", "code", "INTERNAL_ERROR"));
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Generate HTML for download page
     */
    private String generateDownloadPageHtml(String downloadUrl) {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>SemScan Download</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: Arial, sans-serif;\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            height: 100vh;\n" +
                "            margin: 0;\n" +
                "            background-color: #f4f4f4;\n" +
                "        }\n" +
                "        h1 { color: #333; margin-bottom: 30px; }\n" +
                "        .download-btn {\n" +
                "            background-color: #007bff;\n" +
                "            color: white;\n" +
                "            padding: 20px 40px;\n" +
                "            font-size: 24px;\n" +
                "            text-decoration: none;\n" +
                "            border-radius: 50px;\n" +
                "            box-shadow: 0 4px 6px rgba(0,0,0,0.1);\n" +
                "            transition: background-color 0.3s;\n" +
                "        }\n" +
                "        .download-btn:active { background-color: #0056b3; transform: scale(0.98); }\n" +
                "        .note { margin-top: 20px; color: #666; font-size: 14px; text-align: center; padding: 0 20px;}\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>SemScan App</h1>\n" +
                "    <a href=\"" + downloadUrl + "\" class=\"download-btn\">⬇️ Download App</a>\n" +
                "    <p class=\"note\">If prompted, allow installation from unknown sources.</p>\n" +
                "</body>\n" +
                "</html>";
    }

    /**
     * Generate HTML email content
     */
    private String generateDownloadEmailHtml(String downloadLink) {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "</head>\n" +
                "<body style=\"font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0;\">\n" +
                "    <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n" +
                "        <tr>\n" +
                "            <td align=\"center\">\n" +
                "                <table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"max-width: 600px;\">\n" +
                "                    <tr>\n" +
                "                        <td style=\"background-color: #007bff; color: white; padding: 20px; text-align: center;\">\n" +
                "                            <h1 style=\"margin: 0; font-size: 24px;\">SemScan App Available</h1>\n" +
                "                        </td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style=\"padding: 30px; background-color: #f9f9f9;\">\n" +
                "                            <p>Hello,</p>\n" +
                "                            <p>The SemScan app is now available for download!</p>\n" +
                "                            <p>Click the button below to download and install the app on your Android device:</p>\n" +
                "                            <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin: 25px 0;\">\n" +
                "                                <tr>\n" +
                "                                    <td align=\"center\">\n" +
                "                                        <a href=\"" + downloadLink + "\" style=\"display: inline-block; padding: 15px 30px; background-color: #007bff; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 16px;\">Download SemScan App</a>\n" +
                "                                    </td>\n" +
                "                                </tr>\n" +
                "                            </table>\n" +
                "                            <p><strong>Installation Instructions:</strong></p>\n" +
                "                            <ol style=\"padding-left: 20px;\">\n" +
                "                                <li>Click the download button above</li>\n" +
                "                                <li>If prompted, allow installation from unknown sources</li>\n" +
                "                                <li>Open the downloaded APK file</li>\n" +
                "                                <li>Follow the installation prompts</li>\n" +
                "                            </ol>\n" +
                "                            <p>If you have any questions or need assistance, please contact the system administrator.</p>\n" +
                "                        </td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style=\"text-align: center; padding: 20px; color: #666; font-size: 12px;\">\n" +
                "                            <p style=\"margin: 5px 0;\">This is an automated message from SemScan Attendance System</p>\n" +
                "                        </td>\n" +
                "                    </tr>\n" +
                "                </table>\n" +
                "            </td>\n" +
                "        </tr>\n" +
                "    </table>\n" +
                "</body>\n" +
                "</html>";
    }

    /**
     * Generate plain text email content
     */
    private String generateDownloadEmailPlainText(String downloadLink) {
        return "SemScan App Available\n\n" +
                "Hello,\n\n" +
                "The SemScan app is now available for download!\n\n" +
                "Download link: " + downloadLink + "\n\n" +
                "Installation Instructions:\n" +
                "1. Click the download link above\n" +
                "2. If prompted, allow installation from unknown sources\n" +
                "3. Open the downloaded APK file\n" +
                "4. Follow the installation prompts\n\n" +
                "If you have any questions or need assistance, please contact the system administrator.\n\n" +
                "This is an automated message from SemScan Attendance System";
    }

    /**
     * Get client IP address, considering proxy headers
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    /**
     * Parse User-Agent into readable format: "Samsung SM-G991B, Android 10, Chrome 143"
     */
    private String parseUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }

        StringBuilder result = new StringBuilder();

        // Detect OS and device
        if (userAgent.contains("Android")) {
            // Try to extract device model: "Android 10; SM-G991B" or "Android 10; K"
            // Pattern: (Linux; Android VERSION; DEVICE)
            int startIdx = userAgent.indexOf("Android");
            int endIdx = userAgent.indexOf(")", startIdx);

            if (startIdx >= 0 && endIdx > startIdx) {
                String androidPart = userAgent.substring(startIdx, endIdx); // "Android 10; SM-G991B"
                String[] parts = androidPart.split(";");

                // First part: "Android 10"
                String version = parts[0].replace("Android", "").trim();

                // Second part: device model (if not "K" which means hidden)
                String device = null;
                if (parts.length > 1) {
                    device = parts[1].trim();
                    if (device.equals("K") || device.isEmpty()) {
                        device = null; // Hidden by browser privacy
                    }
                }

                if (device != null) {
                    result.append(device).append(", ");
                }
                result.append("Android");
                if (!version.isEmpty()) {
                    result.append(" ").append(version);
                }
            } else {
                // Fallback if parsing fails
                result.append("Android");
            }
        } else if (userAgent.contains("iPhone")) {
            result.append("iPhone");
        } else if (userAgent.contains("iPad")) {
            result.append("iPad");
        } else if (userAgent.contains("Windows")) {
            result.append("Windows PC");
        } else if (userAgent.contains("Macintosh")) {
            result.append("Mac");
        } else if (userAgent.contains("Linux")) {
            result.append("Linux PC");
        } else {
            result.append("Unknown");
        }

        // Detect browser
        if (userAgent.contains("Chrome/")) {
            int idx = userAgent.indexOf("Chrome/");
            String afterChrome = userAgent.substring(idx + 7);
            String[] versionParts = afterChrome.split("[. ]");
            if (versionParts.length > 0 && !versionParts[0].isEmpty()) {
                result.append(", Chrome ").append(versionParts[0]);
            }
        } else if (userAgent.contains("Safari/") && !userAgent.contains("Chrome")) {
            result.append(", Safari");
        } else if (userAgent.contains("Firefox/")) {
            int idx = userAgent.indexOf("Firefox/");
            String afterFirefox = userAgent.substring(idx + 8);
            String[] versionParts = afterFirefox.split("[. ]");
            if (versionParts.length > 0 && !versionParts[0].isEmpty()) {
                result.append(", Firefox ").append(versionParts[0]);
            }
        }

        return result.length() > 0 ? result.toString() : "Unknown";
    }
}

