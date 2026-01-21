package org.example.semscan.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Configuration for logging system
 * Manages logging preferences and settings
 */
public class LoggingConfig {
    
    private static final String PREFS_NAME = "logging_config";
    private static final String KEY_SERVER_LOGGING_ENABLED = "server_logging_enabled";
    private static final String KEY_LOG_LEVEL = "log_level";
    private static final String KEY_BATCH_SIZE = "batch_size";
    private static final String KEY_SEND_INTERVAL = "send_interval";
    private static final String KEY_INCLUDE_DEVICE_INFO = "include_device_info";
    private static final String KEY_INCLUDE_USER_INFO = "include_user_info";
    
    private static LoggingConfig instance;
    private SharedPreferences prefs;
    
    private LoggingConfig(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized LoggingConfig getInstance(Context context) {
        if (instance == null) {
            instance = new LoggingConfig(context);
        }
        return instance;
    }
    
    /**
     * Enable or disable server logging
     */
    public void setServerLoggingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SERVER_LOGGING_ENABLED, enabled).apply();
    }
    
    public boolean isServerLoggingEnabled() {
        return prefs.getBoolean(KEY_SERVER_LOGGING_ENABLED, true);
    }
    
    /**
     * Set minimum log level to send to server
     */
    public void setLogLevel(int level) {
        prefs.edit().putInt(KEY_LOG_LEVEL, level).apply();
    }
    
    public int getLogLevel() {
        return prefs.getInt(KEY_LOG_LEVEL, ServerLogger.INFO);
    }
    
    /**
     * Set batch size for sending logs
     */
    public void setBatchSize(int size) {
        prefs.edit().putInt(KEY_BATCH_SIZE, size).apply();
    }
    
    public int getBatchSize() {
        return prefs.getInt(KEY_BATCH_SIZE, 10);
    }
    
    /**
     * Set interval for sending logs (in seconds)
     */
    public void setSendInterval(int interval) {
        prefs.edit().putInt(KEY_SEND_INTERVAL, interval).apply();
    }
    
    public int getSendInterval() {
        return prefs.getInt(KEY_SEND_INTERVAL, 30);
    }
    
    /**
     * Include device information in logs
     */
    public void setIncludeDeviceInfo(boolean include) {
        prefs.edit().putBoolean(KEY_INCLUDE_DEVICE_INFO, include).apply();
    }
    
    public boolean shouldIncludeDeviceInfo() {
        return prefs.getBoolean(KEY_INCLUDE_DEVICE_INFO, true);
    }
    
    /**
     * Include user information in logs
     */
    public void setIncludeUserInfo(boolean include) {
        prefs.edit().putBoolean(KEY_INCLUDE_USER_INFO, include).apply();
    }
    
    public boolean shouldIncludeUserInfo() {
        return prefs.getBoolean(KEY_INCLUDE_USER_INFO, true);
    }
    
    /**
     * Reset to default settings
     */
    public void resetToDefaults() {
        prefs.edit().clear().apply();
    }
}
