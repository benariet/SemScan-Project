package edu.bgu.semscanapi.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Utility class for session-specific logging.
 * Creates separate log files for each session with date and session ID in the filename.
 */
public class SessionLoggerUtil {

    private static final String SESSION_LOGGER_NAME = "edu.bgu.semscanapi.session";
    private static final String SESSION_ID_KEY = "SESSION_ID";
    private static final String DATE_KEY = "DATE";
    
    private static final Logger sessionLogger = LoggerFactory.getLogger(SESSION_LOGGER_NAME);

    /**
     * Get the session-specific logger.
     * 
     * @return Logger instance for session logging
     */
    public static Logger getSessionLogger() {
        return sessionLogger;
    }

    /**
     * Set session context for logging.
     * This will create a separate log file for the session.
     * 
     * @param sessionId The session ID
     */
    public static void setSessionContext(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            MDC.put(SESSION_ID_KEY, sessionId);
            MDC.put(DATE_KEY, LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
    }

    /**
     * Clear session context from MDC.
     */
    public static void clearSessionContext() {
        MDC.remove(SESSION_ID_KEY);
        MDC.remove(DATE_KEY);
    }

    /**
     * Log session creation event.
     * 
     * @param sessionId The session ID
     * @param seminarId The seminar ID
     * @param presenterId The presenter ID
     */
    public static void logSessionCreated(String sessionId, String seminarId, String presenterId) {
        setSessionContext(sessionId);
        sessionLogger.info("SESSION_CREATED - Session: {}, Seminar: {}, Presenter: {}", 
                          sessionId, seminarId, presenterId);
    }

    /**
     * Log session status change.
     * 
     * @param sessionId The session ID
     * @param oldStatus The previous status
     * @param newStatus The new status
     */
    public static void logSessionStatusChange(String sessionId, String oldStatus, String newStatus) {
        setSessionContext(sessionId);
        sessionLogger.info("SESSION_STATUS_CHANGED - Session: {}, Status: {} -> {}", 
                          sessionId, oldStatus, newStatus);
    }

    /**
     * Log session closure.
     * 
     * @param sessionId The session ID
     * @param durationMinutes Duration of the session in minutes
     * @param attendanceCount Number of students who attended
     */
    public static void logSessionClosed(String sessionId, long durationMinutes, int attendanceCount) {
        setSessionContext(sessionId);
        sessionLogger.info("SESSION_CLOSED - Session: {}, Duration: {} minutes, Attendance: {} students", 
                          sessionId, durationMinutes, attendanceCount);
    }

    /**
     * Log attendance event for a session.
     * 
     * @param sessionId The session ID
     * @param studentId The student ID
     * @param method The attendance method (QR_SCAN, MANUAL, etc.)
     * @param timestamp The attendance timestamp
     */
    public static void logAttendance(String sessionId, String studentId, String method, String timestamp) {
        setSessionContext(sessionId);
        sessionLogger.info("ATTENDANCE_RECORDED - Session: {}, Student: {}, Method: {}, Time: {}", 
                          sessionId, studentId, method, timestamp);
    }

    /**
     * Log session error.
     * 
     * @param sessionId The session ID
     * @param errorMessage The error message
     * @param throwable The exception (if any)
     */
    public static void logSessionError(String sessionId, String errorMessage, Throwable throwable) {
        setSessionContext(sessionId);
        if (throwable != null) {
            sessionLogger.error("SESSION_ERROR - Session: {}, Error: {}", sessionId, errorMessage, throwable);
        } else {
            sessionLogger.error("SESSION_ERROR - Session: {}, Error: {}", sessionId, errorMessage);
        }
    }

    /**
     * Log session activity (general purpose).
     * 
     * @param sessionId The session ID
     * @param activity The activity description
     * @param details Additional details
     */
    public static void logSessionActivity(String sessionId, String activity, String details) {
        setSessionContext(sessionId);
        sessionLogger.info("SESSION_ACTIVITY - Session: {}, Activity: {}, Details: {}", 
                          sessionId, activity, details);
    }

    /**
     * Log session statistics.
     * 
     * @param sessionId The session ID
     * @param totalStudents Total number of students enrolled
     * @param attendedStudents Number of students who attended
     * @param attendanceRate Attendance rate percentage
     */
    public static void logSessionStatistics(String sessionId, int totalStudents, int attendedStudents, double attendanceRate) {
        setSessionContext(sessionId);
        sessionLogger.info("SESSION_STATISTICS - Session: {}, Total: {}, Attended: {}, Rate: {:.2f}%", 
                          sessionId, totalStudents, attendedStudents, attendanceRate);
    }

    /**
     * Generate a unique session log identifier.
     * 
     * @return A unique identifier for session logging
     */
    public static String generateSessionLogId() {
        return "session-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
