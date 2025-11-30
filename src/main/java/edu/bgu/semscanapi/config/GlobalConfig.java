package edu.bgu.semscanapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Global Configuration Class for SemScan API
 * Centralizes all application-wide configuration settings
 */
@Component
public class GlobalConfig {

    // =============================================
    // SERVER CONFIGURATION
    // =============================================
    
    @Value("${server.port:8080}")
    private int serverPort;
    
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
    
    @Value("${app.attendance.manual.window-before-minutes:10}")
    private int manualAttendanceWindowBeforeMinutes;
    
    @Value("${app.attendance.manual.window-after-minutes:15}")
    private int manualAttendanceWindowAfterMinutes;
    
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
    
    // TODO: Make this configurable via properties file later
    // Hardcoded for testing - will be replaced with configurable property
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
    public int getManualAttendanceWindowBeforeMinutes() {
        return manualAttendanceWindowBeforeMinutes;
    }
    
    public int getManualAttendanceWindowAfterMinutes() {
        return manualAttendanceWindowAfterMinutes;
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
    // UTILITY METHODS
    // =============================================
    
    public boolean isDevelopmentMode() {
        return getServerUrl().contains("localhost") || getServerUrl().contains("127.0.0.1");
    }
    
    public boolean isProductionMode() {
        return !isDevelopmentMode();
    }
    
    public String getEnvironment() {
        return isDevelopmentMode() ? "development" : "production";
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
            "⏰ Manual Attendance Window: -%d to +%d minutes\n" +
            "📁 Max Export Size: %d MB\n" +
            "📝 Request Logging: %s",
            applicationName,
            applicationVersion,
            getServerUrl(),
            getApiBaseUrl(),
            databaseUrl,
            getEnvironment(),
            manualAttendanceWindowBeforeMinutes,
            manualAttendanceWindowAfterMinutes,
            maxExportFileSizeMb,
            enableRequestLogging ? "Enabled" : "Disabled"
        );
    }
}
