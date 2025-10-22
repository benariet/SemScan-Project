package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.LogRequest;
import edu.bgu.semscanapi.dto.LogResponse;
import edu.bgu.semscanapi.entity.AppLog;
import edu.bgu.semscanapi.service.AppLogService;
import edu.bgu.semscanapi.service.AuthenticationService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for mobile app logging system
 * Provides endpoints for receiving and retrieving app logs
 */
@RestController
@RequestMapping("/api/v1/logs")
@CrossOrigin(origins = "*")
public class AppLogController {
    
    private static final Logger logger = LoggerUtil.getLogger(AppLogController.class);
    
    @Autowired
    private AppLogService appLogService;
    
    @Autowired
    private AuthenticationService authenticationService;
    
    /**
     * Receive logs from mobile applications
     * POST /api/v1/logs
     */
    @PostMapping
    public ResponseEntity<Object> receiveLogs(
            @RequestBody LogRequest request,
            HttpServletRequest httpRequest) {
        
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        
        // Enhanced debug logging
        System.out.println("=== LOG REQUEST DEBUG ===");
        System.out.println("Correlation ID: " + correlationId);
        System.out.println("API Key: Not required for POC");
        System.out.println("Remote Address: " + httpRequest.getRemoteAddr());
        System.out.println("Content-Type: " + httpRequest.getContentType());
        System.out.println("Request Body: " + (request != null ? request.toString() : "null"));
        System.out.println("Number of logs: " + (request != null && request.getLogs() != null ? request.getLogs().size() : 0));
        System.out.println("=== END DEBUG ===");
        
        logger.info("Received logs request from: {} - Correlation ID: {}", 
                   httpRequest.getRemoteAddr(), correlationId);
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/logs", request != null ? request.toString() : null);
        
        try {
            // No API key validation for POC
            
            // Process logs
            LogResponse response = appLogService.processLogs(request);
            
            if (response.isSuccess()) {
                logger.info("Logs processed successfully - {} entries - Correlation ID: {}", 
                           response.getProcessedCount(), correlationId);
                LoggerUtil.logApiResponse(logger, "POST", "/api/v1/logs", 200, 
                    "Processed " + response.getProcessedCount() + " log entries");
                
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Failed to process logs: {} - Correlation ID: {}", 
                           response.getMessage(), correlationId);
                LoggerUtil.logApiResponse(logger, "POST", "/api/v1/logs", 400, response.getMessage());
                
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error processing logs request - Correlation ID: {}", correlationId, e);
            LoggerUtil.logError(logger, "Error processing logs request", e);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/logs", 500, "Internal Server Error");
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("success", "false");
            errorResponse.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get error logs
     * GET /api/v1/logs/errors
     */
    @GetMapping("/errors")
    public ResponseEntity<Object> getErrorLogs() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving error logs - Correlation ID: {}", correlationId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/logs/errors", null);
        
        try {
            // No API key validation for POC
            
            List<AppLog> errorLogs = appLogService.getErrorLogs();
            
            logger.info("Retrieved {} error logs - Correlation ID: {}", errorLogs.size(), correlationId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/logs/errors", 200, 
                "Retrieved " + errorLogs.size() + " error logs");
            
            return ResponseEntity.ok(errorLogs);
            
        } catch (Exception e) {
            logger.error("Error retrieving error logs - Correlation ID: {}", correlationId, e);
            LoggerUtil.logError(logger, "Error retrieving error logs", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/logs/errors", 500, "Internal Server Error");
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get recent logs
     * GET /api/v1/logs/recent
     */
    @GetMapping("/recent")
    public ResponseEntity<Object> getRecentLogs(
            @RequestParam(defaultValue = "100") int limit) {
        
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving recent logs (limit: {}) - Correlation ID: {}", limit, correlationId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/logs/recent?limit=" + limit, null);
        
        try {
            // No API key validation for POC
            
            List<AppLog> recentLogs = appLogService.getRecentLogs(limit);
            
            logger.info("Retrieved {} recent logs - Correlation ID: {}", recentLogs.size(), correlationId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/logs/recent", 200, 
                "Retrieved " + recentLogs.size() + " recent logs");
            
            return ResponseEntity.ok(recentLogs);
            
        } catch (Exception e) {
            logger.error("Error retrieving recent logs - Correlation ID: {}", correlationId, e);
            LoggerUtil.logError(logger, "Error retrieving recent logs", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/logs/recent", 500, "Internal Server Error");
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get log statistics
     * GET /api/v1/logs/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Object> getLogStats() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving log statistics - Correlation ID: {}", correlationId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/logs/stats", null);
        
        try {
            // No API key validation for POC
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalLogs", appLogService.getTotalLogCount());
            stats.put("errorLogs", appLogService.getLogCountByLevel("ERROR"));
            stats.put("infoLogs", appLogService.getLogCountByLevel("INFO"));
            stats.put("warnLogs", appLogService.getLogCountByLevel("WARN"));
            stats.put("debugLogs", appLogService.getLogCountByLevel("DEBUG"));
            
            logger.info("Retrieved log statistics - Correlation ID: {}", correlationId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/logs/stats", 200, "Log statistics");
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            logger.error("Error retrieving log statistics - Correlation ID: {}", correlationId, e);
            LoggerUtil.logError(logger, "Error retrieving log statistics", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/logs/stats", 500, "Internal Server Error");
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Check database health
     * GET /api/v1/logs/health
     */
    @GetMapping("/health")
    public ResponseEntity<Object> checkDatabaseHealth() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Checking database health - Correlation ID: {}", correlationId);
        
        try {
            // Test database connection
            long logCount = appLogService.getTotalLogCount();
            
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("database", "Connected");
            health.put("totalLogs", logCount);
            health.put("timestamp", System.currentTimeMillis());
            health.put("correlationId", correlationId);
            
            logger.info("Database health check successful - {} logs found - Correlation ID: {}", 
                       logCount, correlationId);
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            logger.error("Database health check failed - Correlation ID: {}", correlationId, e);
            
            Map<String, Object> health = new HashMap<>();
            health.put("status", "DOWN");
            health.put("database", "Disconnected");
            health.put("error", e.getMessage());
            health.put("timestamp", System.currentTimeMillis());
            health.put("correlationId", correlationId);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        } finally {
            LoggerUtil.clearContext();
        }
    }
}
