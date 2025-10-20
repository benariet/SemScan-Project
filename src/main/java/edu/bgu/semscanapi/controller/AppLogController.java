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
            @RequestHeader("x-api-key") String apiKey,
            @RequestBody LogRequest request,
            HttpServletRequest httpRequest) {
        
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Received logs request from: {} - Correlation ID: {}", 
                   httpRequest.getRemoteAddr(), correlationId);
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/logs", request != null ? request.toString() : null);
        
        try {
            // Validate API key
            if (apiKey == null || apiKey.trim().isEmpty()) {
                logger.warn("No API key provided for logs request - Correlation ID: {}", correlationId);
                LoggerUtil.logApiResponse(logger, "POST", "/api/v1/logs", 401, "Unauthorized - No API key");
                
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("success", "false");
                errorResponse.put("message", "API key is required");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            var presenter = authenticationService.validateApiKey(apiKey);
            if (presenter.isEmpty()) {
                logger.warn("Invalid API key provided for logs request - Correlation ID: {}", correlationId);
                LoggerUtil.logApiResponse(logger, "POST", "/api/v1/logs", 401, "Unauthorized - Invalid API key");
                
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("success", "false");
                errorResponse.put("message", "Invalid API key");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            // Process logs
            LogResponse response = appLogService.processLogs(request);
            
            if (response.isSuccess()) {
                logger.info("Logs processed successfully - {} entries by presenter: {} - Correlation ID: {}", 
                           response.getProcessedCount(), presenter.get().getUserId(), correlationId);
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
    public ResponseEntity<Object> getErrorLogs(@RequestHeader("x-api-key") String apiKey) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving error logs - Correlation ID: {}", correlationId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/logs/errors", null);
        
        try {
            // Validate API key
            if (apiKey == null || apiKey.trim().isEmpty()) {
                logger.warn("No API key provided for error logs request - Correlation ID: {}", correlationId);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/logs/errors", 401, "Unauthorized - No API key");
                
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "API key is required");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            var presenter = authenticationService.validateApiKey(apiKey);
            if (presenter.isEmpty()) {
                logger.warn("Invalid API key provided for error logs request - Correlation ID: {}", correlationId);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/logs/errors", 401, "Unauthorized - Invalid API key");
                
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid API key");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
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
            @RequestHeader("x-api-key") String apiKey,
            @RequestParam(defaultValue = "100") int limit) {
        
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving recent logs (limit: {}) - Correlation ID: {}", limit, correlationId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/logs/recent?limit=" + limit, null);
        
        try {
            // Validate API key
            if (apiKey == null || apiKey.trim().isEmpty()) {
                logger.warn("No API key provided for recent logs request - Correlation ID: {}", correlationId);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/logs/recent", 401, "Unauthorized - No API key");
                
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "API key is required");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            var presenter = authenticationService.validateApiKey(apiKey);
            if (presenter.isEmpty()) {
                logger.warn("Invalid API key provided for recent logs request - Correlation ID: {}", correlationId);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/logs/recent", 401, "Unauthorized - Invalid API key");
                
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid API key");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
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
    public ResponseEntity<Object> getLogStats(@RequestHeader("x-api-key") String apiKey) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving log statistics - Correlation ID: {}", correlationId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/logs/stats", null);
        
        try {
            // Validate API key
            if (apiKey == null || apiKey.trim().isEmpty()) {
                logger.warn("No API key provided for log stats request - Correlation ID: {}", correlationId);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/logs/stats", 401, "Unauthorized - No API key");
                
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "API key is required");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            var presenter = authenticationService.validateApiKey(apiKey);
            if (presenter.isEmpty()) {
                logger.warn("Invalid API key provided for log stats request - Correlation ID: {}", correlationId);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/logs/stats", 401, "Unauthorized - Invalid API key");
                
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid API key");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
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
}
