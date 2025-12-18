package org.example.semscan.constants;

/**
 * Global API Constants for SemScan Application
 * Centralized configuration for all API endpoints and settings
 */
public class ApiConstants {
    
    // =============================================
    // BASE CONFIGURATION
    // =============================================
    // Production server URL (HTTP)
    // NOTE: Configurable values (server URL, email addresses, timeouts) have been moved to ConfigManager
    // These hardcoded values are now used as fallback defaults only
    // The app fetches configuration from backend API endpoint /api/v1/config/mobile on every login
    // See: org.example.semscan.utils.ConfigManager
    public static final String SERVER_URL = "http://132.72.50.53:8080"; // Fallback default - use ConfigManager.getServerUrl()
    public static final String API_BASE_URL = "http://132.72.50.53:8080/api/v1"; // Fallback default
    public static final String API_VERSION = "v1";
    // =============================================
    // API ENDPOINTS
    // =============================================
    
    // Seminars
    public static final String SEMINARS_ENDPOINT = API_BASE_URL + "/seminars";
    public static final String CREATE_SEMINAR_ENDPOINT = API_BASE_URL + "/seminars";
    
    // Sessions
    public static final String SESSIONS_ENDPOINT = API_BASE_URL + "/sessions";
    public static final String OPEN_SESSIONS_ENDPOINT = API_BASE_URL + "/sessions/open";
    public static final String CREATE_SESSION_ENDPOINT = API_BASE_URL + "/sessions";
    public static final String CLOSE_SESSION_ENDPOINT = API_BASE_URL + "/sessions/{sessionId}/close";
    
    // Attendance
    public static final String ATTENDANCE_ENDPOINT = API_BASE_URL + "/attendance";
    public static final String ATTENDANCE_BY_SESSION_ENDPOINT = API_BASE_URL + "/attendance";
    public static final String CREATE_ATTENDANCE_ENDPOINT = API_BASE_URL + "/attendance";
    
    // =============================================
    // MANUAL ATTENDANCE ENDPOINTS
    // =============================================
    public static final String MANUAL_ATTENDANCE_ENDPOINT = API_BASE_URL + "/attendance/manual";
    public static final String PENDING_REQUESTS_ENDPOINT = API_BASE_URL + "/attendance/pending-requests";
    public static final String APPROVE_REQUEST_ENDPOINT = API_BASE_URL + "/attendance/{attendanceId}/approve";
    public static final String REJECT_REQUEST_ENDPOINT = API_BASE_URL + "/attendance/{attendanceId}/reject";
    
    // =============================================
    // EXPORT ENDPOINTS
    // =============================================
    public static final String EXPORT_CSV_ENDPOINT = API_BASE_URL + "/export/csv";
    public static final String EXPORT_XLSX_ENDPOINT = API_BASE_URL + "/export/xlsx";
    
    // =============================================
    // API INFO ENDPOINTS
    // =============================================
    public static final String API_ENDPOINTS_INFO = API_BASE_URL + "/info/endpoints";
    public static final String API_CONFIG_INFO = API_BASE_URL + "/info/config";
    
    // =============================================
    // MANUAL ATTENDANCE CONFIGURATION
    // =============================================
    // NOTE: These values are now configurable via ConfigManager
    // These are fallback defaults - use ConfigManager.getManualAttendanceWindowBeforeMinutes() etc.
    public static final int MANUAL_ATTENDANCE_WINDOW_BEFORE_MINUTES = 10; // Fallback default
    public static final int MANUAL_ATTENDANCE_WINDOW_AFTER_MINUTES = 15; // Fallback default
    public static final int MANUAL_ATTENDANCE_AUTO_APPROVE_CAP_PERCENTAGE = 5;
    public static final int MANUAL_ATTENDANCE_AUTO_APPROVE_MIN_CAP = 5;
    
    // =============================================
    // EXPORT CONFIGURATION
    // =============================================
    // NOTE: MAX_EXPORT_FILE_SIZE_MB and EXPORT_EMAIL_RECIPIENTS are now configurable via ConfigManager
    // These are fallback defaults - use ConfigManager.getMaxExportFileSizeMb() and getExportEmailRecipients()
    public static final int MAX_EXPORT_FILE_SIZE_MB = 50; // Fallback default
    public static final String ALLOWED_EXPORT_FORMATS = "csv,xlsx";
    // Multiple email recipients - separate with commas
    // Example: "attendance@example.com,admin@example.com"
    public static final String EXPORT_EMAIL_RECIPIENTS = "benariet@bgu.ac.il,talbnwork@gmail.com"; // Fallback default - use ConfigManager.getExportEmailRecipients()
    
    // =============================================
    // APPLICATION CONFIGURATION
    // =============================================
    public static final String ENVIRONMENT = "development";
    public static final String APPLICATION_NAME = "SemScan";
    public static final String APPLICATION_VERSION = "1.0.0";
    public static final String APPLICATION_DESCRIPTION = "SemScan Attendance System";
    
    // =============================================
    // HTTP CONFIGURATION
    // =============================================
    // NOTE: These timeout values are now configurable via ConfigManager
    // These are fallback defaults - use ConfigManager.getConnectionTimeoutSeconds() etc.
    public static final int CONNECTION_TIMEOUT_SECONDS = 10;  // Fallback default - use ConfigManager.getConnectionTimeoutSeconds()
    public static final int READ_TIMEOUT_SECONDS = 10;        // Fallback default - use ConfigManager.getReadTimeoutSeconds()
    public static final int WRITE_TIMEOUT_SECONDS = 10;       // Fallback default - use ConfigManager.getWriteTimeoutSeconds()
    
    // =============================================
    // ERROR CODES
    // =============================================
    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_CONFLICT = 409;
    public static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    
    // =============================================
    // REQUEST METHODS
    // =============================================
    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_PUT = "PUT";
    public static final String METHOD_DELETE = "DELETE";
    public static final String METHOD_PATCH = "PATCH";
    
    // =============================================
    // CONTENT TYPES
    // =============================================
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    public static final String CONTENT_TYPE_MULTIPART = "multipart/form-data";
    
    // =============================================
    // UTILITY METHODS
    // =============================================
    
    /**
     * Build URL with session ID parameter
     */
    public static String buildSessionUrl(long sessionId) {
        return ATTENDANCE_BY_SESSION_ENDPOINT + "?sessionId=" + sessionId;
    }
    
    /**
     * Build URL for pending requests with session ID
     */
    public static String buildPendingRequestsUrl(long sessionId) {
        return PENDING_REQUESTS_ENDPOINT + "?sessionId=" + sessionId;
    }
    
    /**
     * Build URL for approve request with attendance ID
     */
    public static String buildApproveRequestUrl(long attendanceId) {
        return APPROVE_REQUEST_ENDPOINT.replace("{attendanceId}", String.valueOf(attendanceId));
    }
    
    /**
     * Build URL for reject request with attendance ID
     */
    public static String buildRejectRequestUrl(long attendanceId) {
        return REJECT_REQUEST_ENDPOINT.replace("{attendanceId}", String.valueOf(attendanceId));
    }
    
    /**
     * Build URL for close session with session ID
     */
    public static String buildCloseSessionUrl(long sessionId) {
        return CLOSE_SESSION_ENDPOINT.replace("{sessionId}", String.valueOf(sessionId));
    }
    
    /**
     * Build URL for export CSV with session ID
     */
    public static String buildExportCsvUrl(long sessionId) {
        return EXPORT_CSV_ENDPOINT + "?sessionId=" + sessionId;
    }
    
    /**
     * Build URL for export XLSX with session ID
     */
    public static String buildExportXlsxUrl(long sessionId) {
        return EXPORT_XLSX_ENDPOINT + "?sessionId=" + sessionId;
    }
    
    /**
     * Get full API URL with endpoint
     */
    public static String getFullApiUrl(String endpoint) {
        return API_BASE_URL + endpoint;
    }
    
    /**
     * Check if response code indicates success
     */
    public static boolean isSuccessResponse(int responseCode) {
        return responseCode >= 200 && responseCode < 300;
    }
    
    /**
     * Check if response code indicates client error
     */
    public static boolean isClientError(int responseCode) {
        return responseCode >= 400 && responseCode < 500;
    }
    
    /**
     * Check if response code indicates server error
     */
    public static boolean isServerError(int responseCode) {
        return responseCode >= 500 && responseCode < 600;
    }
    
    // =============================================
    // TOAST MESSAGE CONFIGURATION
    // =============================================
    // NOTE: Toast durations are now configurable via ConfigManager
    // These are fallback defaults - use ConfigManager.getToastDurationError() etc.
    public static final int TOAST_DURATION_ERROR = 10000;  // Fallback default - use ConfigManager.getToastDurationError()
    public static final int TOAST_DURATION_SUCCESS = 5000;  // Fallback default - use ConfigManager.getToastDurationSuccess()
    public static final int TOAST_DURATION_INFO = 6000;  // Fallback default - use ConfigManager.getToastDurationInfo()
    public static final int TOAST_DURATION_DEBUG = 7000;  // 7 seconds for debug (not configurable)
}
