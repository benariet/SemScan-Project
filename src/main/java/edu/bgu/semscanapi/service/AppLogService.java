package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.dto.AppLogEntry;
import edu.bgu.semscanapi.dto.LogRequest;
import edu.bgu.semscanapi.dto.LogResponse;
import edu.bgu.semscanapi.entity.AppLog;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.AppLogRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for processing mobile app logs
 * Handles log validation, storage, and analytics
 */
@Service
public class AppLogService {
    
    private static final Logger logger = LoggerUtil.getLogger(AppLogService.class);
    
    @Autowired
    private AppLogRepository appLogRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Process logs from mobile applications
     */
    @Transactional
    public LogResponse processLogs(LogRequest request) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Processing logs request - Correlation ID: {}", correlationId);
        
        try {
            List<AppLogEntry> logEntries = request.getLogs();
            
            // Validate logs
            validateLogs(logEntries);
            
            // Handle empty logs list gracefully
            if (logEntries.isEmpty()) {
                logger.info("No log entries to process - Correlation ID: {}", correlationId);
                return LogResponse.success(0);
            }
            
            // Convert DTOs to entities
            List<AppLog> appLogs = convertToEntities(logEntries);
            
            // Save logs synchronously to ensure they are persisted
            try {
                appLogRepository.saveAll(appLogs);
                logger.info("Successfully saved {} log entries - Correlation ID: {}", 
                           appLogs.size(), correlationId);
            } catch (Exception e) {
                logger.error("Error saving log entries - Correlation ID: {}", correlationId, e);
                throw new RuntimeException("Failed to save log entries", e);
            }
            
            // Update analytics asynchronously
            updateAnalytics(appLogs);
            
            logger.info("Logs processed successfully - {} entries - Correlation ID: {}", 
                       appLogs.size(), correlationId);
            
            return LogResponse.success(appLogs.size());
            
        } catch (Exception e) {
            logger.error("Error processing logs - Correlation ID: {}", correlationId, e);
            LoggerUtil.logError(logger, "Error processing logs", e);
            return LogResponse.error("Error processing logs: " + e.getMessage());
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Validate log entries
     */
    private void validateLogs(List<AppLogEntry> logs) {
        if (logs == null) {
            throw new IllegalArgumentException("Logs list is null");
        }
        
        // Allow empty logs list - mobile app may send 0 entries
        if (logs.isEmpty()) {
            logger.debug("Received empty logs list - this is valid");
            return;
        }
        
        for (AppLogEntry log : logs) {
            if (log.getTimestamp() == null || log.getLevel() == null || 
                log.getTag() == null || log.getMessage() == null) {
                throw new IllegalArgumentException("Invalid log entry: missing required fields");
            }
            
            // Validate log level
            if (!isValidLogLevel(log.getLevel())) {
                throw new IllegalArgumentException("Invalid log level: " + log.getLevel());
            }
            
            // Validate message length
            if (log.getMessage().length() > 10000) {
                throw new IllegalArgumentException("Log message too long");
            }
        }
    }
    
    /**
     * Check if log level is valid
     */
    private boolean isValidLogLevel(String level) {
        if (level == null) {
            return false;
        }
        String upper = level.toUpperCase();
        return upper.equals("DEBUG") || upper.equals("INFO") || upper.equals("WARN") || upper.equals("ERROR");
    }
    
    /**
     * Convert DTOs to entities
     */
    private List<AppLog> convertToEntities(List<AppLogEntry> logEntries) {
        return logEntries.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * Convert single DTO to entity
     */
    private AppLog convertToEntity(AppLogEntry logEntry) {
        AppLog appLog = new AppLog();
        appLog.setLogTimestamp(toLocalDateTime(logEntry.getTimestamp()));

        // Normalize level and auto-upgrade to ERROR if exception context is present
        String incomingLevel = logEntry.getLevel();
        String normalizedLevel = incomingLevel != null ? incomingLevel.toUpperCase() : null;
        boolean hasExceptionInfo =
                (logEntry.getStackTrace() != null && !logEntry.getStackTrace().trim().isEmpty()) ||
                (logEntry.getExceptionType() != null && !logEntry.getExceptionType().trim().isEmpty());
        if (hasExceptionInfo) {
            normalizedLevel = "ERROR";
        }
        if (normalizedLevel == null) {
            normalizedLevel = "INFO";
        }
        appLog.setLevel(normalizedLevel);

        appLog.setTag(logEntry.getTag());
        appLog.setMessage(logEntry.getMessage());
        appLog.setSource(AppLog.Source.MOBILE); // Mobile logs come from mobile apps
        // Set correlation_id from current context (set by RequestLoggingFilter) or from log entry if provided
        String correlationId = LoggerUtil.getCurrentCorrelationId();
        appLog.setCorrelationId(correlationId);
        
        // Validate bguUsername from mobile log - it's the BGU username from login
        String bguUsername = logEntry.getBguUsername();
        Long userIdLong = null;
        
        if (bguUsername != null && !bguUsername.trim().isEmpty()) {
            try {
                // Mobile sends BGU username, so we need to look it up by bgu_username
                Optional<User> user = userRepository.findByBguUsername(bguUsername);
                if (user.isPresent()) {
                    // Found user by BGU username - use the local id for the log entry
                    userIdLong = user.get().getId();
                } else {
                    // User doesn't exist with this BGU username - auto-create user on first login/log
                    logger.info("User with BGU username {} not found, creating new user record", bguUsername);
                    User newUser = createUserFromBguUsername(bguUsername);
                    if (newUser != null) {
                        userIdLong = newUser.getId();
                        logger.info("Successfully created user with BGU username {} (local id: {})", bguUsername, userIdLong);
                    } else {
                        logger.warn("Failed to create user with BGU username {}, setting userId to null", bguUsername);
                        userIdLong = null;
                    }
                }
            } catch (Exception e) {
                // If user lookup or creation fails, set to null to avoid constraint violation
                logger.error("Error looking up or creating user by BGU username {} from mobile log: {}", bguUsername, e.getMessage(), e);
                userIdLong = null;
            }
        }
        
        appLog.setUserId(userIdLong);
        appLog.setUserRole(parseUserRole(logEntry.getUserRole()));
        appLog.setDeviceInfo(logEntry.getDeviceInfo());
        appLog.setAppVersion(logEntry.getAppVersion());
        appLog.setStackTrace(logEntry.getStackTrace());
        appLog.setExceptionType(logEntry.getExceptionType());
        return appLog;
    }
    
    /**
     * Update analytics data
     */
    private void updateAnalytics(List<AppLog> appLogs) {
        try {
            // This could be expanded to update analytics tables
            // For now, we just log the analytics update
            logger.debug("Analytics update completed for {} log entries", appLogs.size());
        } catch (Exception e) {
            logger.error("Error updating analytics", e);
        }
    }
    
    /**
     * Get logs by level
     */
    public List<AppLog> getLogsByLevel(String level) {
        return appLogRepository.findByLevel(level);
    }
    
    /**
     * Get logs by user ID
     */
    public List<AppLog> getLogsByUser(Long userId) {
        return appLogRepository.findByUserId(userId);
    }
    
    /**
     * Get error logs
     */
    public List<AppLog> getErrorLogs() {
        return appLogRepository.findErrorLogs();
    }
    
    /**
     * Get recent logs
     */
    public List<AppLog> getRecentLogs(int limit) {
        return appLogRepository.findRecentLogs(limit);
    }
    
    /**
     * Get logs by tag
     */
    public List<AppLog> getLogsByTag(String tag) {
        return appLogRepository.findByTag(tag);
    }
    
    /**
     * Get logs by user role
     */
    public List<AppLog> getLogsByUserRole(String userRole) {
        return appLogRepository.findByUserRole(parseUserRole(userRole));
    }
    
    /**
     * Get logs with exceptions
     */
    public List<AppLog> getLogsWithExceptions() {
        return appLogRepository.findLogsWithExceptions();
    }
    
    /**
     * Get log statistics
     */
    public long getTotalLogCount() {
        return appLogRepository.countTotalLogs();
    }
    
    /**
     * Get log count by level
     */
    public long getLogCountByLevel(String level) {
        return appLogRepository.countByLevel(level);
    }
    
    /**
     * Clean up old logs
     */
    @Transactional
    public int cleanupOldLogs(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        return appLogRepository.deleteOldLogs(cutoffDate);
    }

    private LocalDateTime toLocalDateTime(Long epochMillis) {
        if (epochMillis == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofEpochSecond(epochMillis / 1000, (int) (epochMillis % 1000) * 1_000_000,
                java.time.ZoneOffset.UTC);
    }

    /**
     * Auto-create a user from BGU username when first encountered in mobile logs
     * This ensures users are created quickly on first login/log
     */
    @Transactional
    private User createUserFromBguUsername(String bguUsername) {
        try {
            // Check again to avoid race condition
            Optional<User> existingUser = userRepository.findByBguUsername(bguUsername);
            if (existingUser.isPresent()) {
                return existingUser.get();
            }
            
            // Create minimal user record with BGU username
            // Other details can be filled in later during proper login/registration
            User newUser = new User();
            newUser.setBguUsername(bguUsername);
            newUser.setRole(User.UserRole.STUDENT); // Default role, can be updated later
            newUser.setFirstName("User"); // Placeholder, should be updated from BGU Auth
            newUser.setLastName(bguUsername); // Placeholder, should be updated from BGU Auth
            newUser.setEmail(bguUsername + "@bgu.ac.il"); // Placeholder email, should be updated from BGU Auth
            
            User savedUser = userRepository.save(newUser);
            logger.info("Auto-created user with BGU username {} (local id: {})", bguUsername, savedUser.getId());
            return savedUser;
        } catch (Exception e) {
            logger.error("Failed to auto-create user with BGU username {}: {}", bguUsername, e.getMessage(), e);
            return null;
        }
    }

    private AppLog.UserRole parseUserRole(String role) {
        if (role == null) {
            return null;
        }
        try {
            return AppLog.UserRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException ex) {
            logger.warn("Unknown user role received: {}", role);
            return null;
        }
    }
}
