package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/info")
@CrossOrigin(origins = "*")
public class ApiInfoController {

    private static final Logger logger = LoggerUtil.getLogger(ApiInfoController.class);

    @Autowired
    private GlobalConfig globalConfig;

    @GetMapping("/endpoints")
    public ResponseEntity<Map<String, Object>> getApiEndpoints() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving API endpoints for Android app");
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/info/endpoints", null);

        try {
            Map<String, Object> endpoints = new HashMap<>();
            
            // Base URLs
            endpoints.put("serverUrl", globalConfig.getServerUrl());
            endpoints.put("apiBaseUrl", globalConfig.getApiBaseUrl());
            
            // API Endpoints
            Map<String, String> apiEndpoints = new HashMap<>();
            apiEndpoints.put("seminars", globalConfig.getSeminarsEndpoint());
            apiEndpoints.put("sessions", globalConfig.getSessionsEndpoint());
            apiEndpoints.put("openSessions", globalConfig.getOpenSessionsEndpoint());
            apiEndpoints.put("attendance", globalConfig.getAttendanceEndpoint());
            apiEndpoints.put("manualAttendance", globalConfig.getManualAttendanceEndpoint());
            apiEndpoints.put("pendingRequests", globalConfig.getPendingRequestsEndpoint());
            apiEndpoints.put("exportCsv", globalConfig.getExportCsvEndpoint());
            apiEndpoints.put("exportXlsx", globalConfig.getExportXlsxEndpoint());
            apiEndpoints.put("exportUpload", globalConfig.getExportUploadEndpoint());
            
            endpoints.put("endpoints", apiEndpoints);
            
            // Configuration
            Map<String, Object> config = new HashMap<>();
            config.put("apiVersion", globalConfig.getApiVersion());
            config.put("apiKeyHeader", globalConfig.getApiKeyHeader());
            config.put("environment", globalConfig.getEnvironment());
            config.put("applicationName", globalConfig.getApplicationName());
            config.put("applicationVersion", globalConfig.getApplicationVersion());
            
            endpoints.put("config", config);
            
            // Manual Attendance Configuration
            Map<String, Object> manualAttendanceConfig = new HashMap<>();
            manualAttendanceConfig.put("windowBeforeMinutes", globalConfig.getManualAttendanceWindowBeforeMinutes());
            manualAttendanceConfig.put("windowAfterMinutes", globalConfig.getManualAttendanceWindowAfterMinutes());
            manualAttendanceConfig.put("autoApproveCapPercentage", globalConfig.getManualAttendanceAutoApproveCapPercentage());
            manualAttendanceConfig.put("autoApproveMinCap", globalConfig.getManualAttendanceAutoApproveMinCap());
            
            endpoints.put("manualAttendanceConfig", manualAttendanceConfig);

            logger.info("API endpoints retrieved successfully");
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/info/endpoints", 200, "API endpoints data");
            
            return ResponseEntity.ok(endpoints);

        } catch (Exception e) {
            logger.error("Error retrieving API endpoints: {}", e.getMessage(), e);
            LoggerUtil.logError(logger, "Error retrieving API endpoints", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/info/endpoints", 500, "Internal Server Error");
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve API endpoints");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getApiConfig() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving API configuration");
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/info/config", null);

        try {
            Map<String, Object> config = new HashMap<>();
            
            // Server Configuration
            config.put("serverUrl", globalConfig.getServerUrl());
            config.put("apiBaseUrl", globalConfig.getApiBaseUrl());
            config.put("environment", globalConfig.getEnvironment());
            
            // API Configuration
            config.put("apiVersion", globalConfig.getApiVersion());
            config.put("apiKeyHeader", globalConfig.getApiKeyHeader());
            config.put("corsAllowedOrigins", globalConfig.getCorsAllowedOrigins());
            
            // Application Info
            config.put("applicationName", globalConfig.getApplicationName());
            config.put("applicationVersion", globalConfig.getApplicationVersion());
            config.put("applicationDescription", globalConfig.getApplicationDescription());
            
            // Manual Attendance Configuration
            config.put("manualAttendanceWindowBeforeMinutes", globalConfig.getManualAttendanceWindowBeforeMinutes());
            config.put("manualAttendanceWindowAfterMinutes", globalConfig.getManualAttendanceWindowAfterMinutes());
            config.put("manualAttendanceAutoApproveCapPercentage", globalConfig.getManualAttendanceAutoApproveCapPercentage());
            config.put("manualAttendanceAutoApproveMinCap", globalConfig.getManualAttendanceAutoApproveMinCap());
            
            // Export Configuration
            config.put("maxExportFileSizeMb", globalConfig.getMaxExportFileSizeMb());
            config.put("allowedExportFormats", globalConfig.getAllowedExportFormats());

            logger.info("API configuration retrieved successfully");
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/info/config", 200, "API configuration data");
            
            return ResponseEntity.ok(config);

        } catch (Exception e) {
            logger.error("Error retrieving API configuration: {}", e.getMessage(), e);
            LoggerUtil.logError(logger, "Error retrieving API configuration", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/info/config", 500, "Internal Server Error");
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve API configuration");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
}
