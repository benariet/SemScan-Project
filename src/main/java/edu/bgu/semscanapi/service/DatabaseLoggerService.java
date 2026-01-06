package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.AppLog;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.AppLogRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for logging actions and errors to the database
 * All API actions and errors are automatically logged to app_logs table
 */
@Service
public class DatabaseLoggerService {

    private static final Logger logger = LoggerUtil.getLogger(DatabaseLoggerService.class);

    @Autowired
    private AppLogRepository appLogRepository;

    @Autowired
    private UserRepository userRepository;

    // Self-injection to bypass Spring proxy limitation for @Transactional on self-invocations
    // @Lazy breaks the circular dependency during bean creation
    @Lazy
    @Autowired
    private DatabaseLoggerService self;
    
    // Thread-local storage for device info (set by controllers/interceptors)
    private static final ThreadLocal<String> currentDeviceInfo = new ThreadLocal<>();
    private static final ThreadLocal<String> currentAppVersion = new ThreadLocal<>();

    /**
     * Set device info for current request thread (call this from controller/interceptor)
     */
    public static void setDeviceInfo(String deviceInfo, String appVersion) {
        currentDeviceInfo.set(deviceInfo);
        currentAppVersion.set(appVersion);
    }

    /**
     * Clear device info for current request thread
     */
    public static void clearDeviceInfo() {
        currentDeviceInfo.remove();
        currentAppVersion.remove();
    }

    /**
     * Log an action to the database
     * @param level Log level (INFO, WARN, ERROR, etc.)
     * @param tag Tag/category for the log
     * @param message Log message
     * @param bguUsername Optional user BGU username
     * @param payload Optional payload/context
     */
    public void logAction(String level, String tag, String message, String bguUsername, String payload) {
        // Capture ThreadLocal values synchronously before transactional call
        String deviceInfo = currentDeviceInfo.get();
        String appVersion = currentAppVersion.get();
        // Use self-injection to go through proxy for @Transactional to work
        self.logActionWithDevice(level, tag, message, bguUsername, payload, deviceInfo, appVersion);
    }

    /**
     * Log an action with explicit device info
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActionWithDevice(String level, String tag, String message, String bguUsername, String payload,
                                    String deviceInfo, String appVersion) {
        try {
            AppLog appLog = new AppLog();
            appLog.setLogTimestamp(LocalDateTime.now());
            appLog.setLevel(level != null ? level.toUpperCase() : "INFO");
            appLog.setTag(tag);
            appLog.setMessage(message);
            appLog.setSource(AppLog.Source.API); // API source for server-side actions
            appLog.setCorrelationId(LoggerUtil.getCurrentCorrelationId());

            // Set device info if available
            if (deviceInfo != null && !deviceInfo.isBlank()) {
                appLog.setDeviceInfo(deviceInfo);
            }
            if (appVersion != null && !appVersion.isBlank()) {
                appLog.setAppVersion(appVersion);
            }

            // Only set bgu_username if the user exists in the database (foreign key constraint)
            if (bguUsername != null && !bguUsername.isBlank()) {
                String normalizedUsername = bguUsername.trim().toLowerCase();
                try {
                    Optional<User> user = userRepository.findByBguUsername(normalizedUsername);
                    if (user.isPresent()) {
                        // User exists - safe to set bgu_username and full name
                        User u = user.get();
                        appLog.setBguUsername(normalizedUsername);
                        appLog.setUserFullName(buildFullName(u));
                        appLog.setUserRole(deriveUserRole(u));
                        logger.debug("Set bgu_username={} for log action (user exists)", normalizedUsername);
                    } else {
                        // User doesn't exist - don't set bgu_username to avoid foreign key violation
                        logger.warn("BGU username '{}' does not exist in users table - skipping bgu_username for this log entry", normalizedUsername);
                    }
                } catch (Exception e) {
                    logger.error("Error looking up user by BGU username '{}': {}", normalizedUsername, e.getMessage(), e);
                    // Don't set bgu_username if lookup fails
                }
            }

            appLog.setPayload(payload);

            appLogRepository.save(appLog);
        } catch (Exception e) {
            logger.error("Failed to log action to database: {}", message, e);
        }
    }
    
    /**
     * Log an error to the database
     * @param tag Tag/category for the error
     * @param message Error message
     * @param throwable Exception (if any)
     * @param bguUsername Optional user BGU username
     * @param payload Optional payload/context
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logError(String tag, String message, Throwable throwable, String bguUsername, String payload) {
        // Capture ThreadLocal values synchronously
        String deviceInfo = currentDeviceInfo.get();
        String appVersion = currentAppVersion.get();
        try {
            AppLog appLog = new AppLog();
            appLog.setLogTimestamp(LocalDateTime.now());
            appLog.setLevel("ERROR");
            appLog.setTag(tag);
            appLog.setMessage(message);
            appLog.setSource(AppLog.Source.API); // API source for server-side errors
            appLog.setCorrelationId(LoggerUtil.getCurrentCorrelationId());

            // Set device info if available (already captured above)
            if (deviceInfo != null && !deviceInfo.isBlank()) {
                appLog.setDeviceInfo(deviceInfo);
            }
            if (appVersion != null && !appVersion.isBlank()) {
                appLog.setAppVersion(appVersion);
            }

            if (throwable != null) {
                appLog.setExceptionType(throwable.getClass().getName());
                appLog.setStackTrace(getStackTrace(throwable));
            }

            // Only set bgu_username if the user exists in the database (foreign key constraint)
            if (bguUsername != null && !bguUsername.isBlank()) {
                String normalizedUsername = bguUsername.trim().toLowerCase();
                try {
                    Optional<User> user = userRepository.findByBguUsername(normalizedUsername);
                    if (user.isPresent()) {
                        // User exists - safe to set bgu_username and full name
                        User u = user.get();
                        appLog.setBguUsername(normalizedUsername);
                        appLog.setUserFullName(buildFullName(u));
                        appLog.setUserRole(deriveUserRole(u));
                        logger.debug("Set bgu_username={} for log error (user exists)", normalizedUsername);
                    } else {
                        // User doesn't exist - don't set bgu_username to avoid foreign key violation
                        logger.warn("BGU username '{}' does not exist in users table - skipping bgu_username for this log entry", normalizedUsername);
                    }
                } catch (Exception e) {
                    logger.error("Error looking up user by BGU username '{}': {}", normalizedUsername, e.getMessage(), e);
                    // Don't set bgu_username if lookup fails
                }
            }

            appLog.setPayload(payload);

            appLogRepository.save(appLog);
        } catch (Exception e) {
            // Don't throw exception if database logging fails - just log to file
            logger.error("Failed to log error to database: {}", message, e);
        }
    }
    
    /**
     * Log API request action
     */
    public void logApiAction(String method, String endpoint, String tag, String bguUsername, String payload) {
        logAction("INFO", tag != null ? tag : "API_ACTION",
            String.format("%s %s", method, endpoint), bguUsername, payload);
    }
    
    /**
     * Log API request with body - derives feature from endpoint URL
     */
    public void logApiRequest(String method, String endpoint, String bguUsername, String requestBody) {
        String feature = deriveFeatureFromEndpoint(endpoint);
        // Truncate body to prevent huge database entries (max 10000 characters)
        String truncatedBody = truncateString(requestBody, 10000);
        String payload = truncatedBody != null ? String.format("requestBody=%s", truncatedBody) : null;
        logAction("INFO", feature + "_API_REQUEST",
            String.format("%s %s", method, endpoint), bguUsername, payload);
    }

    /**
     * Log API response - derives feature from endpoint URL
     */
    public void logApiResponse(String method, String endpoint, int statusCode, String bguUsername) {
        String feature = deriveFeatureFromEndpoint(endpoint);
        String level = statusCode >= 500 ? "ERROR" : (statusCode >= 400 ? "WARN" : "INFO");
        logAction(level, feature + "_API_RESPONSE",
            String.format("%s %s - Status: %d", method, endpoint, statusCode), bguUsername, null);
    }

    /**
     * Log API response with body - derives feature from endpoint URL
     */
    public void logApiResponse(String method, String endpoint, int statusCode, String bguUsername, String responseBody) {
        String feature = deriveFeatureFromEndpoint(endpoint);
        String level = statusCode >= 500 ? "ERROR" : (statusCode >= 400 ? "WARN" : "INFO");
        // Truncate body to prevent huge database entries (max 10000 characters)
        String truncatedBody = truncateString(responseBody, 10000);
        String payload = truncatedBody != null ? String.format("responseBody=%s", truncatedBody) : null;
        logAction(level, feature + "_API_RESPONSE",
            String.format("%s %s - Status: %d", method, endpoint, statusCode), bguUsername, payload);
    }

    /**
     * Derive feature name from endpoint URL pattern
     */
    private String deriveFeatureFromEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "API";
        }
        String lowerEndpoint = endpoint.toLowerCase();

        // Registration endpoints
        if (lowerEndpoint.contains("/register")) {
            return "REGISTRATION";
        }
        // Waiting list endpoints
        if (lowerEndpoint.contains("/waiting-list")) {
            return "WAITING_LIST";
        }
        // Auth endpoints
        if (lowerEndpoint.contains("/auth/login")) {
            return "AUTH_LOGIN";
        }
        if (lowerEndpoint.contains("/auth/setup")) {
            return "AUTH_SETUP";
        }
        // Attendance endpoints
        if (lowerEndpoint.contains("/attendance")) {
            return "ATTENDANCE";
        }
        // Approval endpoints
        if (lowerEndpoint.contains("/approve") || lowerEndpoint.contains("/decline")) {
            return "APPROVAL";
        }
        // Config endpoints
        if (lowerEndpoint.contains("/config")) {
            return "CONFIG";
        }
        // User endpoints
        if (lowerEndpoint.contains("/users")) {
            return "USER";
        }
        // Presenter home endpoints
        if (lowerEndpoint.contains("/presenters") && lowerEndpoint.contains("/home")) {
            return "PRESENTER_HOME";
        }
        // Slots endpoints
        if (lowerEndpoint.contains("/slots")) {
            return "SLOT";
        }
        // Export endpoints
        if (lowerEndpoint.contains("/export")) {
            return "EXPORT";
        }
        // QR endpoints
        if (lowerEndpoint.contains("/qr")) {
            return "QR";
        }
        // FCM endpoints
        if (lowerEndpoint.contains("/fcm")) {
            return "FCM";
        }
        // Default
        return "API";
    }
    
    /**
     * Log business event/action
     */
    public void logBusinessEvent(String event, String details, String bguUsername) {
        logAction("INFO", "BUSINESS_EVENT", String.format("%s: %s", event, details), bguUsername, null);
    }

    /**
     * Log authentication event
     */
    public void logAuthentication(String event, String bguUsername, String details) {
        logAction("INFO", "AUTHENTICATION", String.format("%s - User: %s", event, bguUsername), bguUsername, details);
    }

    /**
     * Log attendance event
     */
    public void logAttendance(String event, String studentUsername, Long sessionId, String method) {
        logAction("INFO", "ATTENDANCE",
            String.format("%s - Student: %s, Session: %s, Method: %s", event, studentUsername, sessionId, method),
            studentUsername, String.format("sessionId=%s,method=%s", sessionId, method));
    }

    /**
     * Log session event
     */
    public void logSessionEvent(String event, Long sessionId, Long seminarId, String presenterUsername) {
        logAction("INFO", "SESSION",
            String.format("%s - Session: %s, Seminar: %s, Presenter: %s", event, sessionId, seminarId, presenterUsername),
            presenterUsername, String.format("sessionId=%s,seminarId=%s", sessionId, seminarId));
    }
    
    /**
     * Get stack trace as string
     */
    private String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Derive log role from user participation flags
     */
    private AppLog.UserRole deriveUserRole(User user) {
        if (user == null) {
            return AppLog.UserRole.UNKNOWN;
        }
        boolean presenter = Boolean.TRUE.equals(user.getIsPresenter());
        boolean participant = Boolean.TRUE.equals(user.getIsParticipant());

        if (presenter && participant) {
            return AppLog.UserRole.BOTH;
        }
        if (presenter) {
            return AppLog.UserRole.PRESENTER;
        }
        if (participant) {
            return AppLog.UserRole.PARTICIPANT;
        }
        return AppLog.UserRole.UNKNOWN;
    }

    /**
     * Build full name from user's first and last name
     */
    private String buildFullName(User user) {
        if (user == null) {
            return null;
        }
        String firstName = user.getFirstName();
        String lastName = user.getLastName();

        if ((firstName == null || firstName.isBlank()) && (lastName == null || lastName.isBlank())) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            sb.append(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(lastName.trim());
        }
        return sb.toString();
    }
    
    /**
     * Truncate string to prevent huge database entries
     */
    private String truncateString(String str, int maxLength) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        if (str.length() <= maxLength) {
            return str;
        }
        
        return str.substring(0, maxLength) + "... (truncated, original length: " + str.length() + ")";
    }
}

