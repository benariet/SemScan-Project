package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.dto.ErrorResponse;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.service.SessionService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * QR Code Controller for generating QR codes with proper URLs
 * This ensures QR codes always use the correct server URL
 */
@RestController
@RequestMapping("/api/v1/qr")
@CrossOrigin(origins = "*")
public class QRCodeController {

    private static final Logger logger = LoggerUtil.getLogger(QRCodeController.class);

    @Autowired
    private SessionService sessionService;

    @Autowired
    private GlobalConfig globalConfig;

    /**
     * Generate QR code data for a session
     * Returns the URL that should be encoded in the QR code
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Object> generateSessionQR(@PathVariable String sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Generating QR code for session: {} - Correlation ID: {}", sessionId, correlationId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/qr/session/" + sessionId, null);

        try {
            // Get session details
            Optional<Session> sessionOpt = sessionService.getSessionById(sessionId);
            if (sessionOpt.isEmpty()) {
                logger.warn("Session not found: {} - Correlation ID: {}", sessionId, correlationId);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/qr/session/" + sessionId, 404, "Session not found");
                
                ErrorResponse errorResponse = new ErrorResponse(
                    "Session not found: " + sessionId,
                    "Not Found",
                    404,
                    "/api/v1/qr/session/" + sessionId
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            Session session = sessionOpt.get();
            
            // Generate QR code data with proper URL
            Map<String, Object> qrData = generateQRCodeData(session);
            
            logger.info("QR code generated successfully for session: {} - Correlation ID: {}", sessionId, correlationId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/qr/session/" + sessionId, 200, "QR code data");
            
            return ResponseEntity.ok(qrData);

        } catch (Exception e) {
            logger.error("Error generating QR code for session: {} - Correlation ID: {}", sessionId, correlationId, e);
            LoggerUtil.logError(logger, "Error generating QR code", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/qr/session/" + sessionId, 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to generate QR code",
                "Internal Server Error",
                500,
                "/api/v1/qr/session/" + sessionId
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Generate QR code data for multiple sessions
     */
    @PostMapping("/sessions/batch")
    public ResponseEntity<Object> generateBatchQRCodes(@RequestBody Map<String, Object> request) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Generating batch QR codes - Correlation ID: {}", correlationId);
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/qr/sessions/batch", request.toString());

        try {
            // This could be extended to handle multiple session IDs
            // For now, return a simple response
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Batch QR code generation not yet implemented");
            response.put("correlationId", correlationId);
            
            logger.info("Batch QR code generation completed - Correlation ID: {}", correlationId);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/qr/sessions/batch", 200, "Batch QR codes");
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error generating batch QR codes - Correlation ID: {}", correlationId, e);
            LoggerUtil.logError(logger, "Error generating batch QR codes", e);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/qr/sessions/batch", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to generate batch QR codes",
                "Internal Server Error",
                500,
                "/api/v1/qr/sessions/batch"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Get QR code configuration for the current server
     */
    @GetMapping("/config")
    public ResponseEntity<Object> getQRConfig() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving QR code configuration - Correlation ID: {}", correlationId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/qr/config", null);

        try {
            Map<String, Object> config = new HashMap<>();
            config.put("serverUrl", globalConfig.getServerUrl());
            config.put("apiBaseUrl", globalConfig.getApiBaseUrl());
            config.put("sessionsEndpoint", globalConfig.getSessionsEndpoint());
            config.put("environment", globalConfig.getEnvironment());
            config.put("isDevelopment", globalConfig.isDevelopmentMode());
            
            // QR code specific settings
            config.put("qrCodeFormat", "URL");
            config.put("qrCodeVersion", "1.0");
            config.put("supportedFormats", new String[]{"URL", "SESSION_ID", "RELATIVE_PATH"});
            
            logger.info("QR code configuration retrieved successfully - Correlation ID: {}", correlationId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/qr/config", 200, "QR configuration");
            
            return ResponseEntity.ok(config);

        } catch (Exception e) {
            logger.error("Error retrieving QR configuration - Correlation ID: {}", correlationId, e);
            LoggerUtil.logError(logger, "Error retrieving QR configuration", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/qr/config", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to retrieve QR configuration",
                "Internal Server Error",
                500,
                "/api/v1/qr/config"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Generate QR code data for a session
     */
    private Map<String, Object> generateQRCodeData(Session session) {
        Map<String, Object> qrData = new HashMap<>();
        
        // Generate different URL formats for flexibility
        String fullUrl = globalConfig.getSessionsEndpoint() + "/" + session.getSessionId();
        String relativePath = "/api/v1/sessions/" + session.getSessionId();
        String sessionIdOnly = session.getSessionId();
        
        qrData.put("sessionId", session.getSessionId());
        qrData.put("seminarId", session.getSeminarId());
        qrData.put("status", session.getStatus().toString());
        qrData.put("startTime", session.getStartTime());
        
        // QR code content options
        Map<String, String> qrContent = new HashMap<>();
        qrContent.put("fullUrl", fullUrl);
        qrContent.put("relativePath", relativePath);
        qrContent.put("sessionIdOnly", sessionIdOnly);
        qrContent.put("recommended", fullUrl); // Recommended format
        
        qrData.put("qrContent", qrContent);
        
        // Server information
        Map<String, String> serverInfo = new HashMap<>();
        serverInfo.put("serverUrl", globalConfig.getServerUrl());
        serverInfo.put("apiBaseUrl", globalConfig.getApiBaseUrl());
        serverInfo.put("environment", globalConfig.getEnvironment());
        
        qrData.put("serverInfo", serverInfo);
        
        // QR code metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generatedAt", java.time.LocalDateTime.now());
        metadata.put("version", "1.0");
        metadata.put("format", "URL");
        metadata.put("description", "Session QR code for attendance scanning");
        
        qrData.put("metadata", metadata);
        
        return qrData;
    }
}
