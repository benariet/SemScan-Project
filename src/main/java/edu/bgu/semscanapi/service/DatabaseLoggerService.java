package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.AppLog;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.AppLogRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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
    
    /**
     * Log an action to the database
     * @param level Log level (INFO, WARN, ERROR, etc.)
     * @param tag Tag/category for the log
     * @param message Log message
     * @param userUsername Optional user username
     * @param payload Optional payload/context
     */
    @Async
    @Transactional
    public void logAction(String level, String tag, String message, String userUsername, String payload) {
        try {
            AppLog appLog = new AppLog();
            appLog.setLogTimestamp(LocalDateTime.now());
            appLog.setLevel(level != null ? level.toUpperCase() : "INFO");
            appLog.setTag(tag);
            appLog.setMessage(message);
            appLog.setSource(AppLog.Source.API); // API source for server-side actions
            appLog.setCorrelationId(LoggerUtil.getCurrentCorrelationId());
            appLog.setUserUsername(userUsername);

            if (userUsername != null && !userUsername.isBlank()) {
                try {
                    Optional<User> user = userRepository.findByBguUsername(userUsername);
                    user.map(this::deriveUserRole).ifPresent(appLog::setUserRole);
                } catch (Exception e) {
                    logger.debug("Failed to fetch user role for username: {}", userUsername, e);
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
     * @param userUsername Optional user username
     * @param payload Optional payload/context
     */
    @Async
    @Transactional
    public void logError(String tag, String message, Throwable throwable, String userUsername, String payload) {
        try {
            AppLog appLog = new AppLog();
            appLog.setLogTimestamp(LocalDateTime.now());
            appLog.setLevel("ERROR");
            appLog.setTag(tag);
            appLog.setMessage(message);
            appLog.setSource(AppLog.Source.API); // API source for server-side errors
            appLog.setCorrelationId(LoggerUtil.getCurrentCorrelationId());
            
            if (throwable != null) {
                appLog.setExceptionType(throwable.getClass().getName());
                appLog.setStackTrace(getStackTrace(throwable));
            }
            
            appLog.setUserUsername(userUsername);
            
            if (userUsername != null && !userUsername.isBlank()) {
                try {
                    Optional<User> user = userRepository.findByBguUsername(userUsername);
                    user.map(this::deriveUserRole).ifPresent(appLog::setUserRole);
                } catch (Exception e) {
                    // Don't fail logging if user lookup fails
                    logger.debug("Failed to fetch user role for username: {}", userUsername, e);
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
    @Async
    @Transactional
    public void logApiAction(String method, String endpoint, String tag, String userUsername, String payload) {
        logAction("INFO", tag != null ? tag : "API_REQUEST", 
            String.format("%s %s", method, endpoint), userUsername, payload);
    }
    
    /**
     * Log API response
     */
    @Async
    @Transactional
    public void logApiResponse(String method, String endpoint, int statusCode, String userUsername) {
        String level = statusCode >= 500 ? "ERROR" : (statusCode >= 400 ? "WARN" : "INFO");
        logAction(level, "API_RESPONSE", 
            String.format("%s %s - Status: %d", method, endpoint, statusCode), userUsername, null);
    }
    
    /**
     * Log business event/action
     */
    @Async
    @Transactional
    public void logBusinessEvent(String event, String details, String userUsername) {
        logAction("INFO", "BUSINESS_EVENT", String.format("%s: %s", event, details), userUsername, null);
    }
    
    /**
     * Log authentication event
     */
    @Async
    @Transactional
    public void logAuthentication(String event, String userUsername, String details) {
        logAction("INFO", "AUTHENTICATION", String.format("%s - User: %s", event, userUsername), userUsername, details);
    }
    
    /**
     * Log attendance event
     */
    @Async
    @Transactional
    public void logAttendance(String event, String studentUsername, Long sessionId, String method) {
        logAction("INFO", "ATTENDANCE", 
            String.format("%s - Student: %s, Session: %s, Method: %s", event, studentUsername, sessionId, method),
            studentUsername, String.format("sessionId=%s,method=%s", sessionId, method));
    }
    
    /**
     * Log session event
     */
    @Async
    @Transactional
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
}

