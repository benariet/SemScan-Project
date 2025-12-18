package edu.bgu.semscanapi.controller;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for providing mobile app configuration
 * Returns configs where target_system='MOBILE' or 'BOTH'
 */
@RestController
@RequestMapping("/api/v1/config")
public class MobileConfigController {

    private static final Logger logger = LoggerUtil.getLogger(MobileConfigController.class);

    @Autowired
    private AppConfigService appConfigService;

    @Autowired(required = false)
    private DatabaseLoggerService databaseLoggerService;

    /**
     * Get all configuration values for mobile app
     * Returns configs where target_system='MOBILE' or 'BOTH'
     * 
     * @return JSON response with configs array
     */
    @GetMapping("/mobile")
    public ResponseEntity<Map<String, Object>> getMobileConfigs() {
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/config/mobile", null);

        try {
            List<AppConfig> configs = appConfigService.getConfigsForMobile();

            // Convert to DTO format for JSON response
            List<Map<String, Object>> configList = configs.stream()
                    .map(config -> {
                        Map<String, Object> configMap = new HashMap<>();
                        configMap.put("configKey", config.getConfigKey());
                        configMap.put("configValue", config.getConfigValue());
                        configMap.put("configType", config.getConfigType().name());
                        configMap.put("category", config.getCategory());
                        configMap.put("targetSystem", config.getTargetSystem().name());
                        configMap.put("description", config.getDescription());
                        return configMap;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("configs", configList);

            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/config/mobile", HttpStatus.OK.value(),
                    "Returned " + configList.size() + " configs for mobile");

            // Log to app_logs
            if (databaseLoggerService != null) {
                databaseLoggerService.logAction("INFO", "MOBILE_CONFIG_API_CALLED",
                        String.format("Mobile config API called - returned %d config(s)", configList.size()),
                        null, String.format("configCount=%d", configList.size()));
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
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve mobile configurations"));
        }
    }
}
