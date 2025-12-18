package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.AppConfig;
import edu.bgu.semscanapi.repository.AppConfigRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing application configuration from database
 * Supports both backend API and mobile app with target_system filtering
 */
@Service
public class AppConfigService {

    private static final Logger logger = LoggerUtil.getLogger(AppConfigService.class);

    @Autowired
    private AppConfigRepository appConfigRepository;

    @Autowired(required = false)
    private DatabaseLoggerService databaseLoggerService;

    /**
     * Get config value with type conversion
     * @param key Configuration key
     * @param type Expected type (String, Integer, Boolean)
     * @param defaultValue Default value if config not found
     * @return Config value converted to requested type, or defaultValue
     */
    @Cacheable(value = "appConfigCache", key = "#key")
    public <T> T getConfigValue(String key, Class<T> type, T defaultValue) {
        Optional<AppConfig> configOpt = appConfigRepository.findByConfigKey(key);
        
        if (configOpt.isEmpty()) {
            logger.debug("Config key '{}' not found, using default value: {}", key, defaultValue);
            if (databaseLoggerService != null) {
                databaseLoggerService.logAction("INFO", "APP_CONFIG_NOT_FOUND",
                        String.format("Config key '%s' not found in database, using default value: %s", key, defaultValue),
                        null, String.format("key=%s,defaultValue=%s", key, defaultValue));
            }
            return defaultValue;
        }

        AppConfig config = configOpt.get();
        String value = config.getConfigValue();

        try {
            T result;
            if (type == String.class) {
                result = type.cast(value);
            } else if (type == Integer.class) {
                result = type.cast(Integer.parseInt(value.trim()));
            } else if (type == Boolean.class) {
                result = type.cast(Boolean.parseBoolean(value.trim()));
            } else {
                logger.warn("Unsupported config type: {} for key: {}, returning default", type.getSimpleName(), key);
                if (databaseLoggerService != null) {
                    databaseLoggerService.logError("APP_CONFIG_UNSUPPORTED_TYPE",
                            String.format("Unsupported config type: %s for key: %s", type.getSimpleName(), key),
                            null, null, String.format("key=%s,type=%s", key, type.getSimpleName()));
                }
                return defaultValue;
            }
            
            // Log successful config retrieval
            if (databaseLoggerService != null) {
                databaseLoggerService.logAction("INFO", "APP_CONFIG_RETRIEVED",
                        String.format("Config key '%s' retrieved from database: %s (type: %s)", key, value, type.getSimpleName()),
                        null, String.format("key=%s,value=%s,type=%s,targetSystem=%s", key, value, type.getSimpleName(), config.getTargetSystem()));
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Error converting config value for key '{}': {}, using default value: {}", 
                    key, e.getMessage(), defaultValue, e);
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("APP_CONFIG_CONVERSION_ERROR",
                        String.format("Error converting config value for key '%s': %s, using default: %s", key, e.getMessage(), defaultValue),
                        e, null, String.format("key=%s,value=%s,type=%s", key, value, type.getSimpleName()));
            }
            return defaultValue;
        }
    }

    /**
     * Get string config value
     */
    public String getStringConfig(String key, String defaultValue) {
        return getConfigValue(key, String.class, defaultValue);
    }

    /**
     * Get integer config value
     */
    public Integer getIntegerConfig(String key, Integer defaultValue) {
        return getConfigValue(key, Integer.class, defaultValue);
    }

    /**
     * Get boolean config value
     */
    public Boolean getBooleanConfig(String key, Boolean defaultValue) {
        return getConfigValue(key, Boolean.class, defaultValue);
    }

    /**
     * Get all configs for API (target_system='API' or 'BOTH')
     */
    public List<AppConfig> getConfigsForApi() {
        List<AppConfig.TargetSystem> targetSystems = Arrays.asList(
                AppConfig.TargetSystem.API, 
                AppConfig.TargetSystem.BOTH
        );
        List<AppConfig> configs = appConfigRepository.findByTargetSystemIn(targetSystems);
        
        if (databaseLoggerService != null) {
            databaseLoggerService.logAction("INFO", "APP_CONFIG_API_RETRIEVED",
                    String.format("Retrieved %d config(s) for API (target_system=API or BOTH)", configs.size()),
                    null, String.format("count=%d", configs.size()));
        }
        
        return configs;
    }

    /**
     * Get all configs for Mobile (target_system='MOBILE' or 'BOTH')
     */
    public List<AppConfig> getConfigsForMobile() {
        List<AppConfig.TargetSystem> targetSystems = Arrays.asList(
                AppConfig.TargetSystem.MOBILE, 
                AppConfig.TargetSystem.BOTH
        );
        List<AppConfig> configs = appConfigRepository.findByTargetSystemIn(targetSystems);
        
        if (databaseLoggerService != null) {
            databaseLoggerService.logAction("INFO", "APP_CONFIG_MOBILE_RETRIEVED",
                    String.format("Retrieved %d config(s) for mobile app (target_system=MOBILE or BOTH)", configs.size()),
                    null, String.format("count=%d", configs.size()));
        }
        
        return configs;
    }

    /**
     * Get configs by category
     */
    public List<AppConfig> getConfigsByCategory(String category) {
        return appConfigRepository.findByCategory(category);
    }
}
