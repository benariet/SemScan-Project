package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.MobileConfigResponse;
import edu.bgu.semscanapi.entity.AppConfig;
import edu.bgu.semscanapi.service.AppConfigService;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for providing mobile app configuration
 * Returns configs where target_system='MOBILE' or 'BOTH'
 */
@RestController
@RequestMapping("/api/v1/config")
public class MobileConfigController {

    private static final Logger logger = LoggerUtil.getLogger(MobileConfigController.class);
    private static final LocalDateTime SERVER_START_TIME = LocalDateTime.now();

    @Autowired
    private AppConfigService appConfigService;

    @Autowired(required = false)
    private DatabaseLoggerService databaseLoggerService;

    /**
     * Get all configuration values for mobile app
     * Returns configs where target_system='MOBILE' or 'BOTH'
     * Returns flat response structure matching mobile app's expected format
     *
     * @return JSON response with flat config fields
     */
    @GetMapping("/mobile")
    public ResponseEntity<MobileConfigResponse> getMobileConfigs() {
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/config/mobile", null);

        try {
            List<AppConfig> configs = appConfigService.getConfigsForMobile();

            // Convert to flat DTO format for mobile app
            MobileConfigResponse response = MobileConfigResponse.fromConfigList(configs);

            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/config/mobile", HttpStatus.OK.value(),
                    "Returned " + configs.size() + " configs for mobile (flat format)");

            // Log to app_logs
            if (databaseLoggerService != null) {
                databaseLoggerService.logAction("INFO", "MOBILE_CONFIG_API_CALLED",
                        String.format("Mobile config API called - returned %d config(s) in flat format", configs.size()),
                        null, String.format("configCount=%d,appVersion=%s", configs.size(), response.getAppVersion()));
            }

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Error retrieving mobile configs", ex);

            // Log error to app_logs
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("MOBILE_CONFIG_API_ERROR",
                        "Error retrieving mobile configurations: " + ex.getMessage(),
                        ex, null, "endpoint=/api/v1/config/mobile");
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get web app configuration (version info)
     * Returns WEB_VERSION from app_config table
     */
    @GetMapping("/web")
    public ResponseEntity<Map<String, String>> getWebConfig() {
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/config/web", null);

        try {
            String webVersion = appConfigService.getStringConfig("WEB_VERSION", "1.0.0");
            String deployTime = SERVER_START_TIME.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

            Map<String, String> response = new HashMap<>();
            response.put("version", webVersion);
            response.put("deployed", deployTime);

            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/config/web", HttpStatus.OK.value(),
                    "Returned web version: " + webVersion + " deployed: " + deployTime);

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Error retrieving web config", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
