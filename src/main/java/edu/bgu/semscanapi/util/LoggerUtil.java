package edu.bgu.semscanapi.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Utility class for consistent logging across the SemScan API
 * Provides structured logging with correlation IDs and context information
 */
public class LoggerUtil {
    
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String USER_ID_KEY = "userId";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String SEMINAR_ID_KEY = "seminarId";
    private static final String REQUEST_ID_KEY = "requestId";
    
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Get a logger for the specified class
     */
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }
    
    /**
     * Set correlation ID for request tracing
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isEmpty()) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }
    }
    
    /**
     * Generate and set a new correlation ID
     */
    public static String generateAndSetCorrelationId() {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        setCorrelationId(correlationId);
        return correlationId;
    }
    
    /**
     * Set user ID for context
     */
    public static void setUserId(String userId) {
        if (userId != null && !userId.isEmpty()) {
            MDC.put(USER_ID_KEY, userId);
        }
    }
    
    /**
     * Set student ID for context (alias for setUserId)
     */
    public static void setStudentId(String studentId) {
        if (studentId != null && !studentId.isEmpty()) {
            MDC.put(USER_ID_KEY, studentId);
        }
    }
    
    /**
     * Set session ID for context
     */
    public static void setSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            MDC.put(SESSION_ID_KEY, sessionId);
        }
    }
    
    /**
     * Set seminar ID for context
     */
    public static void setSeminarId(String seminarId) {
        if (seminarId != null && !seminarId.isEmpty()) {
            MDC.put(SEMINAR_ID_KEY, seminarId);
        }
    }
    
    /**
     * Set request ID for context
     */
    public static void setRequestId(String requestId) {
        if (requestId != null && !requestId.isEmpty()) {
            MDC.put(REQUEST_ID_KEY, requestId);
        }
    }
    
    /**
     * Clear all MDC context
     */
    public static void clearContext() {
        MDC.clear();
    }
    
    /**
     * Clear specific MDC key
     */
    public static void clearKey(String key) {
        MDC.remove(key);
    }
    
    /**
     * Log API request with structured information
     */
    public static void logApiRequest(Logger logger, String method, String endpoint, String requestBody) {
        logger.info("API Request - Method: {}, Endpoint: {}, Body: {}", method, endpoint, requestBody);
    }
    
    /**
     * Log API response with structured information
     */
    public static void logApiResponse(Logger logger, String method, String endpoint, int statusCode, String responseBody) {
        logger.info("API Response - Method: {}, Endpoint: {}, Status: {}, Body: {}", method, endpoint, statusCode, responseBody);
    }
    
    /**
     * Log database operation
     */
    public static void logDatabaseOperation(Logger logger, String operation, String table, String recordId) {
        logger.debug("Database Operation - {} on table {} with ID: {}", operation, table, recordId);
    }
    
    /**
     * Log authentication event
     */
    public static void logAuthentication(Logger logger, String event, String userId, String apiKey) {
        logger.info("Authentication - Event: {}, User: {}, API Key: {}", event, userId, apiKey != null ? "***" + apiKey.substring(Math.max(0, apiKey.length() - 4)) : "null");
    }
    
    /**
     * Log attendance event
     */
    public static void logAttendanceEvent(Logger logger, String event, String studentId, String sessionId, String method) {
        logger.info("Attendance Event - Event: {}, Student: {}, Session: {}, Method: {}", event, studentId, sessionId, method);
    }
    
    /**
     * Log session event
     */
    public static void logSessionEvent(Logger logger, String event, String sessionId, String seminarId, String presenterId) {
        logger.info("Session Event - Event: {}, Session: {}, Seminar: {}, Presenter: {}", event, sessionId, seminarId, presenterId);
    }
    
    /**
     * Log error with context
     */
    public static void logError(Logger logger, String message, Throwable throwable) {
        logger.error("Error - Message: {}, Exception: {}", message, throwable != null ? throwable.getMessage() : "null", throwable);
    }
    
    /**
     * Log performance metrics
     */
    public static void logPerformance(Logger logger, String operation, long durationMs) {
        logger.info("Performance - Operation: {}, Duration: {}ms", operation, durationMs);
    }
    
    /**
     * Log business logic event
     */
    public static void logBusinessEvent(Logger logger, String event, String details) {
        logger.info("Business Event - Event: {}, Details: {}", event, details);
    }
    
    /**
     * Get current timestamp as string
     */
    public static String getCurrentTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }
    
    /**
     * Get current correlation ID
     */
    public static String getCurrentCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }
    
    /**
     * Get current user ID
     */
    public static String getCurrentUserId() {
        return MDC.get(USER_ID_KEY);
    }
    
    /**
     * Get current session ID
     */
    public static String getCurrentSessionId() {
        return MDC.get(SESSION_ID_KEY);
    }
    
    /**
     * Get current seminar ID
     */
    public static String getCurrentSeminarId() {
        return MDC.get(SEMINAR_ID_KEY);
    }
    
    /**
     * Get current request ID
     */
    public static String getCurrentRequestId() {
        return MDC.get(REQUEST_ID_KEY);
    }
}
