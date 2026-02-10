package edu.bgu.semscanapi.config;

import edu.bgu.semscanapi.service.AppConfigService;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Global Configuration Class for SemScan API
 * Centralizes all application-wide configuration settings
 * 
 * NOTE: For email and time window settings, this class now integrates with AppConfigService
 * to read from the app_config database table, with fallback to @Value properties for backward compatibility.
 */
@Component
public class GlobalConfig {
    
    @Autowired(required = false)
    private AppConfigService appConfigService;
    
    @Autowired(required = false)
    private DatabaseLoggerService databaseLoggerService;

    // =============================================
    // SERVER CONFIGURATION
    // =============================================
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    @Value("${server.address:0.0.0.0}")
    private String serverAddress;
    
    @Value("${server.servlet.context-path:/}")
    private String contextPath;
    
    // =============================================
    // DATABASE CONFIGURATION
    // =============================================
    
    @Value("${spring.datasource.url}")
    private String databaseUrl;
    
    @Value("${spring.datasource.username}")
    private String databaseUsername;
    
    @Value("${spring.datasource.driver-class-name}")
    private String databaseDriver;
    
    // =============================================
    // API CONFIGURATION
    // =============================================
    
    @Value("${app.api.version:v1}")
    private String apiVersion;
    
    @Value("${app.api.base-path:/api}")
    private String apiBasePath;
    
    // =============================================
    // SECURITY CONFIGURATION
    // =============================================
    
    @Value("${app.security.api-key-header:x-api-key}")
    private String apiKeyHeader;
    
    @Value("${app.security.cors.allowed-origins:*}")
    private String corsAllowedOrigins;
    
    // =============================================
    // LOGGING CONFIGURATION
    // =============================================
    
    @Value("${app.logging.correlation-id-header:X-Correlation-ID}")
    private String correlationIdHeader;
    
    @Value("${app.logging.enable-request-logging:true}")
    private boolean enableRequestLogging;
    
    // =============================================
    // MANUAL ATTENDANCE CONFIGURATION
    // =============================================
    // NOTE: These values are now primarily managed via app_config table (student_attendance_window_before_minutes,
    // student_attendance_window_after_minutes). The @Value defaults below are fallbacks for backward compatibility.
    
    @Value("${app.attendance.manual.window-before-minutes:10}")
    private int manualAttendanceWindowBeforeMinutesDefault;
    
    @Value("${app.attendance.manual.window-after-minutes:15}")
    private int manualAttendanceWindowAfterMinutesDefault;
    
    @Value("${app.attendance.manual.auto-approve-cap-percentage:5}")
    private int manualAttendanceAutoApproveCapPercentage;
    
    @Value("${app.attendance.manual.auto-approve-min-cap:5}")
    private int manualAttendanceAutoApproveMinCap;
    
    // =============================================
    // EXPORT CONFIGURATION
    // =============================================
    
    @Value("${app.export.max-file-size-mb:50}")
    private int maxExportFileSizeMb;
    
    @Value("${app.export.allowed-formats:csv,xlsx}")
    private String allowedExportFormats;

    @Value("${app.export.email-recipients:attendance@example.com,admin@example.com}")
    private String emailRecipients;
    
    // =============================================
    // FILE UPLOAD CONFIGURATION
    // =============================================

    private static final String UPLOAD_SERVER_URL = "http://132.72.50.53:8080/api/v1/upload";

    // =============================================
    // EMAIL CONFIGURATION
    // =============================================
    
    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;
    
    @Value("${spring.mail.port:587}")
    private int mailPort;
    
    @Value("${spring.mail.username:}")
    private String mailUsername;
    
    @Value("${spring.mail.from:SemScan Attendance System <noreply@semscan.com>}")
    private String mailFrom;
    
    // =============================================
    // APPLICATION INFO
    // =============================================
    
    @Value("${app.name:SemScan API}")
    private String applicationName;
    
    @Value("${app.version:1.0.0}")
    private String applicationVersion;
    
    @Value("${app.description:SemScan Attendance System API}")
    private String applicationDescription;
    
    // =============================================
    // GETTER METHODS
    // =============================================
    
    // Server Configuration
    public int getServerPort() {
        return serverPort;
    }
    
    public String getContextPath() {
        return contextPath;
    }
    
    public String getServerUrl() {
        // Read server_url from app_config table (allows per-environment configuration)
        if (appConfigService != null) {
            try {
                String configuredUrl = appConfigService.getStringConfig("server_url", null);
                if (configuredUrl != null && !configuredUrl.isEmpty()) {
                    // Remove trailing slash if present, then add context path
                    if (configuredUrl.endsWith("/")) {
                        configuredUrl = configuredUrl.substring(0, configuredUrl.length() - 1);
                    }
                    return configuredUrl + contextPath;
                }
            } catch (Exception e) {
                // Fall through to default logic
            }
        }

        // Fallback: Use HTTPS on standard port 443 (via nginx) for production
        // This eliminates browser warnings about non-standard ports
        // Test mode (port 8081) should use HTTP with explicit port
        if (isProductionMode()) {
            return "https://132.72.50.53" + contextPath;
        }
        // Development mode or Test mode - use HTTP with explicit port
        return "http://132.72.50.53:" + serverPort + contextPath;
    }
    
    public String getApiBaseUrl() {
        String serverUrl = getServerUrl();
        if (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
        }
        return serverUrl + apiBasePath + "/" + apiVersion;
    }
    
    // Database Configuration
    public String getDatabaseUrl() {
        return databaseUrl;
    }
    
    public String getDatabaseUsername() {
        return databaseUsername;
    }
    
    public String getDatabaseDriver() {
        return databaseDriver;
    }
    
    // API Configuration
    public String getApiVersion() {
        return apiVersion;
    }
    
    public String getApiBasePath() {
        return apiBasePath;
    }
    
    // Security Configuration
    public String getApiKeyHeader() {
        return apiKeyHeader;
    }
    
    public String getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }
    
    // Logging Configuration
    public String getCorrelationIdHeader() {
        return correlationIdHeader;
    }
    
    public boolean isEnableRequestLogging() {
        return enableRequestLogging;
    }
    
    // Manual Attendance Configuration
    // These methods now read from AppConfigService first, with fallback to @Value defaults
    public int getManualAttendanceWindowBeforeMinutes() {
        if (appConfigService != null) {
            try {
                int value = appConfigService.getIntegerConfig("student_attendance_window_before_minutes", 
                        manualAttendanceWindowBeforeMinutesDefault);
                // Logging should never break the main flow - wrap in try-catch
                if (databaseLoggerService != null) {
                    try {
                        databaseLoggerService.logAction("INFO", "GLOBAL_CONFIG_READ_FROM_APP_CONFIG",
                                String.format("Read student_attendance_window_before_minutes=%d from app_config table", value),
                                null, String.format("configKey=student_attendance_window_before_minutes,value=%d", value));
                    } catch (Exception logEx) {
                        // Ignore logging exceptions - don't break main flow
                    }
                }
                return value;
            } catch (Exception e) {
                // Fallback to default if config service fails
                // Logging should never break the main flow - wrap in try-catch
                if (databaseLoggerService != null) {
                    try {
                        databaseLoggerService.logError("GLOBAL_CONFIG_FALLBACK_TO_DEFAULT",
                                String.format("Failed to read student_attendance_window_before_minutes from app_config, using default: %d. Error: %s",
                                        manualAttendanceWindowBeforeMinutesDefault, e.getMessage()),
                                e, null, String.format("configKey=student_attendance_window_before_minutes,defaultValue=%d", 
                                        manualAttendanceWindowBeforeMinutesDefault));
                    } catch (Exception logEx) {
                        // Ignore logging exceptions - don't break main flow
                    }
                }
            }
        } else {
            // Logging should never break the main flow - wrap in try-catch
            if (databaseLoggerService != null) {
                try {
                    databaseLoggerService.logAction("INFO", "GLOBAL_CONFIG_APP_CONFIG_SERVICE_UNAVAILABLE",
                            String.format("AppConfigService not available, using default for student_attendance_window_before_minutes: %d",
                                    manualAttendanceWindowBeforeMinutesDefault),
                            null, String.format("configKey=student_attendance_window_before_minutes,defaultValue=%d",
                                    manualAttendanceWindowBeforeMinutesDefault));
                } catch (Exception logEx) {
                    // Ignore logging exceptions - don't break main flow
                }
            }
        }
        return manualAttendanceWindowBeforeMinutesDefault;
    }
    
    public int getManualAttendanceWindowAfterMinutes() {
        if (appConfigService != null) {
            try {
                int value = appConfigService.getIntegerConfig("student_attendance_window_after_minutes", 
                        manualAttendanceWindowAfterMinutesDefault);
                // Logging should never break the main flow - wrap in try-catch
                if (databaseLoggerService != null) {
                    try {
                        databaseLoggerService.logAction("INFO", "GLOBAL_CONFIG_READ_FROM_APP_CONFIG",
                                String.format("Read student_attendance_window_after_minutes=%d from app_config table", value),
                                null, String.format("configKey=student_attendance_window_after_minutes,value=%d", value));
                    } catch (Exception logEx) {
                        // Ignore logging exceptions - don't break main flow
                    }
                }
                return value;
            } catch (Exception e) {
                // Fallback to default if config service fails
                // Logging should never break the main flow - wrap in try-catch
                if (databaseLoggerService != null) {
                    try {
                        databaseLoggerService.logError("GLOBAL_CONFIG_FALLBACK_TO_DEFAULT",
                                String.format("Failed to read student_attendance_window_after_minutes from app_config, using default: %d. Error: %s",
                                        manualAttendanceWindowAfterMinutesDefault, e.getMessage()),
                                e, null, String.format("configKey=student_attendance_window_after_minutes,defaultValue=%d",
                                        manualAttendanceWindowAfterMinutesDefault));
                    } catch (Exception logEx) {
                        // Ignore logging exceptions - don't break main flow
                    }
                }
            }
        } else {
            // Logging should never break the main flow - wrap in try-catch
            if (databaseLoggerService != null) {
                try {
                    databaseLoggerService.logAction("INFO", "GLOBAL_CONFIG_APP_CONFIG_SERVICE_UNAVAILABLE",
                            String.format("AppConfigService not available, using default for student_attendance_window_after_minutes: %d",
                                    manualAttendanceWindowAfterMinutesDefault),
                            null, String.format("configKey=student_attendance_window_after_minutes,defaultValue=%d",
                                    manualAttendanceWindowAfterMinutesDefault));
                } catch (Exception logEx) {
                    // Ignore logging exceptions - don't break main flow
                }
            }
        }
        return manualAttendanceWindowAfterMinutesDefault;
    }
    
    public int getManualAttendanceAutoApproveCapPercentage() {
        return manualAttendanceAutoApproveCapPercentage;
    }
    
    public int getManualAttendanceAutoApproveMinCap() {
        return manualAttendanceAutoApproveMinCap;
    }
    
    // Export Configuration
    public int getMaxExportFileSizeMb() {
        return maxExportFileSizeMb;
    }
    
    public String[] getAllowedExportFormats() {
        return allowedExportFormats.split(",");
    }

    public String getEmailRecipients() {
        return emailRecipients;
    }
    
    // File Upload Configuration
    public String getUploadServerUrl() {
        return UPLOAD_SERVER_URL;
    }
    
    // Email Configuration
    public String getMailHost() {
        return mailHost;
    }
    
    public int getMailPort() {
        return mailPort;
    }
    
    public String getMailUsername() {
        return mailUsername;
    }
    
    public String getMailFrom() {
        return mailFrom;
    }
    
    public boolean isEmailConfigured() {
        return mailUsername != null && !mailUsername.trim().isEmpty() 
            && !mailUsername.equals("your-email@gmail.com");
    }
    
    // Application Info
    public String getApplicationName() {
        return applicationName;
    }
    
    public String getApplicationVersion() {
        return applicationVersion;
    }
    
    public String getApplicationDescription() {
        return applicationDescription;
    }
    
    // =============================================
    // API ENDPOINT HELPERS
    // =============================================
    
    public String getSeminarsEndpoint() {
        return getApiBaseUrl() + "/seminars";
    }
    
    public String getSessionsEndpoint() {
        return getApiBaseUrl() + "/sessions";
    }
    
    public String getOpenSessionsEndpoint() {
        return getApiBaseUrl() + "/sessions/open";
    }
    
    public String getAttendanceEndpoint() {
        return getApiBaseUrl() + "/attendance";
    }
    
    public String getManualAttendanceEndpoint() {
        return getApiBaseUrl() + "/attendance/manual-request";
    }
    
    public String getPendingRequestsEndpoint() {
        return getApiBaseUrl() + "/attendance/pending-requests";
    }
    
    public String getApproveRequestEndpoint(String attendanceId) {
        return getApiBaseUrl() + "/attendance/" + attendanceId + "/approve";
    }
    
    public String getRejectRequestEndpoint(String attendanceId) {
        return getApiBaseUrl() + "/attendance/" + attendanceId + "/reject";
    }
    
    public String getExportCsvEndpoint() {
        return getApiBaseUrl() + "/export/csv";
    }
    
    public String getExportXlsxEndpoint() {
        return getApiBaseUrl() + "/export/xlsx";
    }

    public String getExportUploadEndpoint() {
        return getApiBaseUrl() + "/export/upload";
    }

    // =============================================
    // REGISTRATION LIMITS CONFIGURATION
    // =============================================

    /**
     * Maximum approved registrations per user (regardless of degree)
     * Default: 1 - user can only present once per degree (one approved slot ever)
     */
    public int getMaxApprovedRegistrations() {
        if (appConfigService != null) {
            try {
                return appConfigService.getIntegerConfig("registration.max_approved", 1);
            } catch (Exception e) {
                // Fallback to default
            }
        }
        return 1;
    }

    /**
     * Maximum pending registrations for PhD students
     * Default: 1 - PhD can only have one pending registration at a time
     */
    public int getMaxPendingRegistrationsPhd() {
        if (appConfigService != null) {
            try {
                return appConfigService.getIntegerConfig("registration.max_pending.phd", 1);
            } catch (Exception e) {
                // Fallback to default
            }
        }
        return 1;
    }

    /**
     * Maximum pending registrations for MSc students
     * Default: 2 - MSc can have up to 2 pending registrations
     */
    public int getMaxPendingRegistrationsMsc() {
        if (appConfigService != null) {
            try {
                return appConfigService.getIntegerConfig("registration.max_pending.msc", 2);
            } catch (Exception e) {
                // Fallback to default
            }
        }
        return 2;
    }

    // =============================================
    // EMAIL QUEUE CONFIGURATION
    // =============================================

    public int getEmailQueueMaxRetries() {
        if (appConfigService != null) {
            try {
                return appConfigService.getIntegerConfig("email_queue_max_retries", 3);
            } catch (Exception e) {
                // Fallback to default
            }
        }
        return 3;
    }

    public int getEmailQueueInitialBackoffMinutes() {
        if (appConfigService != null) {
            try {
                return appConfigService.getIntegerConfig("email_queue_initial_backoff_minutes", 5);
            } catch (Exception e) {
                // Fallback to default
            }
        }
        return 5;
    }

    public int getEmailQueueBackoffMultiplier() {
        if (appConfigService != null) {
            try {
                return appConfigService.getIntegerConfig("email_queue_backoff_multiplier", 3);
            } catch (Exception e) {
                // Fallback to default
            }
        }
        return 3;
    }

    public int getEmailQueueBatchSize() {
        if (appConfigService != null) {
            try {
                return appConfigService.getIntegerConfig("email_queue_batch_size", 50);
            } catch (Exception e) {
                // Fallback to default
            }
        }
        return 50;
    }

    // =============================================
    // UTILITY METHODS
    // =============================================

    public boolean isDevelopmentMode() {
        // Check server address to determine environment (avoids circular dependency with getServerUrl)
        // Development typically binds to localhost/127.0.0.1, production binds to 0.0.0.0 (all interfaces)
        // Also check if server address is explicitly set to localhost
        if (serverAddress != null) {
            String addr = serverAddress.toLowerCase();
            return addr.equals("localhost") || addr.equals("127.0.0.1") || addr.startsWith("127.");
        }
        // Default: if not explicitly set to localhost, assume production
        return false;
    }

    public boolean isTestMode() {
        // Port 8081 is always the test environment
        return serverPort == 8081;
    }

    public boolean isProductionMode() {
        // Production = not development AND not test
        return !isDevelopmentMode() && !isTestMode();
    }

    public String getEnvironment() {
        if (isDevelopmentMode()) {
            return "development";
        } else if (isTestMode()) {
            return "test";
        }
        return "production";
    }
    
    // =============================================
    // CONFIGURATION SUMMARY
    // =============================================
    
    public String getConfigurationSummary() {
        return String.format(
            "🚀 %s v%s\n" +
            "📱 Server URL: %s\n" +
            "🔗 API Base URL: %s\n" +
            "🗄️ Database: %s\n" +
            "🔐 Security: API Key Header: %s\n" +
            "📊 Environment: %s\n" +
            "⏰ Manual Attendance Window: -%d to +%d minutes (from %s)\n" +
            "📁 Max Export Size: %d MB\n" +
            "📝 Request Logging: %s",
            applicationName,
            applicationVersion,
            getServerUrl(),
            getApiBaseUrl(),
            databaseUrl,
            getApiKeyHeader(),
            getEnvironment(),
            getManualAttendanceWindowBeforeMinutes(),
            getManualAttendanceWindowAfterMinutes(),
            appConfigService != null ? "app_config table" : "application.properties",
            maxExportFileSizeMb,
            enableRequestLogging ? "Enabled" : "Disabled"
        );
    }
}
