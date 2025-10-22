package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.dto.AppLogEntry;
import edu.bgu.semscanapi.dto.LogRequest;
import edu.bgu.semscanapi.dto.LogResponse;
import edu.bgu.semscanapi.entity.AppLog;
import edu.bgu.semscanapi.repository.AppLogRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
        appLog.setTimestamp(logEntry.getTimestamp());

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
        appLog.setUserId(logEntry.getUserId());
        appLog.setUserRole(logEntry.getUserRole());
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
    public List<AppLog> getLogsByUser(String userId) {
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
        return appLogRepository.findByUserRole(userRole);
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
}
