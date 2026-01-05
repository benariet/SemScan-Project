package org.example.semscan.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

/**
 * Centralized logging utility for the SemScan app
 * Provides consistent logging across all components
 */
public class Logger {

    // Main app tag
    private static final String APP_TAG = "SemScan";

    // Component tags - use FEATURE_ACTION pattern
    public static final String TAG_API = "API_CALL";
    public static final String TAG_UI = "UI_ACTION";
    public static final String TAG_PREFS = "CONFIG_PREFERENCES";
    public static final String TAG_QR = "QR_EVENT";
    public static final String TAG_ATTENDANCE = "ATTENDANCE_EVENT";
    public static final String TAG_SESSION = "SESSION_EVENT";

    // ========== FEATURE_ACTION_RESULT Tag Paradigm ==========
    // Format: FEATURE_ACTION or FEATURE_ACTION_RESULT
    // Easy to filter: WHERE tag LIKE 'AUTH_%' or 'REGISTRATION_%'

    // AUTH - Authentication & Login
    public static final String TAG_LOGIN_ATTEMPT = "AUTH_LOGIN_ATTEMPT";
    public static final String TAG_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    public static final String TAG_LOGIN_FAILED = "AUTH_LOGIN_FAILED";
    public static final String TAG_LOGOUT = "AUTH_LOGOUT";
    public static final String TAG_ACCOUNT_SETUP = "AUTH_ACCOUNT_SETUP";
    public static final String TAG_SESSION_EXPIRED = "AUTH_SESSION_EXPIRED";

    // REGISTRATION - Slot Registration
    public static final String TAG_REGISTER_REQUEST = "REGISTRATION_REQUEST";
    public static final String TAG_REGISTER_RESPONSE = "REGISTRATION_RESPONSE";
    public static final String TAG_REGISTRATION_SUCCESS = "REGISTRATION_SUCCESS";
    public static final String TAG_REGISTRATION_FAILED = "REGISTRATION_FAILED";
    public static final String TAG_CANCEL_REQUEST = "REGISTRATION_CANCEL_REQUEST";
    public static final String TAG_CANCEL_RESPONSE = "REGISTRATION_CANCEL_RESPONSE";
    public static final String TAG_CANCEL_SUCCESS = "REGISTRATION_CANCEL_SUCCESS";
    public static final String TAG_CANCEL_FAILED = "REGISTRATION_CANCEL_FAILED";

    // WAITING_LIST - Waiting List Operations
    public static final String TAG_WAITING_LIST_JOIN = "WAITING_LIST_JOIN_REQUEST";
    public static final String TAG_WAITING_LIST_LEAVE = "WAITING_LIST_LEAVE_REQUEST";
    public static final String TAG_WAITING_LIST_SUCCESS = "WAITING_LIST_SUCCESS";
    public static final String TAG_WAITING_LIST_FAILED = "WAITING_LIST_FAILED";
    public static final String TAG_WAITING_LIST_DEGREE_MISMATCH = "WAITING_LIST_DEGREE_MISMATCH";

    // SLOT - Slot Loading & Viewing
    public static final String TAG_SLOTS_LOAD = "SLOT_LOAD";
    public static final String TAG_SLOTS_REFRESH = "SLOT_REFRESH";
    public static final String TAG_SLOT_DETAILS = "SLOT_VIEW";

    // ATTENDANCE - QR & Attendance
    public static final String TAG_QR_SCAN = "ATTENDANCE_QR_SCAN";
    public static final String TAG_QR_GENERATE = "ATTENDANCE_QR_GENERATE";
    public static final String TAG_ATTENDANCE_OPEN = "ATTENDANCE_SESSION_OPEN";
    public static final String TAG_ATTENDANCE_CLOSE = "ATTENDANCE_SESSION_CLOSE";
    public static final String TAG_ATTENDANCE_MARK = "ATTENDANCE_MARK";
    public static final String TAG_MANUAL_ATTENDANCE = "ATTENDANCE_MANUAL_REQUEST";

    // CONFIG - Settings & Configuration
    public static final String TAG_CONFIG_LOAD = "CONFIG_LOAD";
    public static final String TAG_CONFIG_REFRESH = "CONFIG_REFRESH";
    public static final String TAG_SETTINGS_CHANGE = "CONFIG_SETTINGS_SAVE";
    public static final String TAG_PREFERENCES = "CONFIG_PREFERENCES";

    // NAV - Navigation & Screen Views
    public static final String TAG_SCREEN_VIEW = "NAV_SCREEN_VIEW";
    public static final String TAG_NAVIGATION = "NAV_NAVIGATE";

    // EXPORT - Data Export
    public static final String TAG_EXPORT_REQUEST = "EXPORT_REQUEST";
    public static final String TAG_EXPORT_SUCCESS = "EXPORT_SUCCESS";
    public static final String TAG_EXPORT_FAILED = "EXPORT_FAILED";

    // APP - App Lifecycle
    public static final String TAG_APP_START = "APP_START";
    public static final String TAG_APP_RESUME = "APP_RESUME";

    // Debug flag - determined at runtime based on app's debuggable flag
    // This is more reliable than BuildConfig.DEBUG and avoids compile-time issues
    private static boolean debugEnabled = true; // Default to true until init() is called
    private static boolean initialized = false;
    private static Context applicationContext; // For forwarding errors to ServerLogger

    /**
     * Initialize logger with application context to enable server forwarding
     * Also determines if debug logging should be enabled based on app's debuggable flag
     */
    public static void init(Context context) {
        applicationContext = context.getApplicationContext();

        // Determine debug mode from app's debuggable flag
        // This is set automatically by the build system (true for debug, false for release)
        if (!initialized && applicationContext != null) {
            try {
                ApplicationInfo appInfo = applicationContext.getApplicationInfo();
                debugEnabled = (appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                initialized = true;
                Log.i(APP_TAG, "Logger initialized - debug mode: " + debugEnabled);
            } catch (Exception e) {
                // If we can't determine, default to true for safety
                debugEnabled = true;
                Log.w(APP_TAG, "Could not determine debug mode, defaulting to enabled");
            }
        }
    }

    /**
     * Check if debug logging is enabled
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Log debug messages
     */
    public static void d(String tag, String message) {
        if (debugEnabled) {
            Log.i(tag, message);
        }
    }
    
    /**
     * Log info messages
     */
    public static void i(String tag, String message) {
        Log.i(tag, message);
    }
    
    /**
     * Log warning messages
     */
    public static void w(String tag, String message) {
        Log.w(tag, message);
    }
    
    /**
     * Log error messages
     */
    public static void e(String tag, String message) {
        Log.e(tag, message);
        // Forward to server as ERROR (no throwable)
        try {
            if (applicationContext != null) {
                ServerLogger.getInstance(applicationContext).e(tag, message);
            }
        } catch (Throwable ignored) {
            // Avoid crashing logging path
        }
    }
    
    /**
     * Log error messages with throwable
     */
    public static void e(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
        // Forward to server as ERROR with stackTrace/exceptionType
        try {
            if (applicationContext != null) {
                ServerLogger.getInstance(applicationContext).e(tag, message, throwable);
                ServerLogger.getInstance(applicationContext).flushLogs();
            }
        } catch (Throwable ignored) {
            // Avoid crashing logging path
        }
    }
    
    /**
     * Log API calls
     */
    public static void api(String method, String url, String requestBody) {
        if (debugEnabled) {
            Log.i(TAG_API, String.format("API %s: %s", method, url));
            if (requestBody != null && !requestBody.isEmpty()) {
                Log.i(TAG_API, "Request Body: " + requestBody);
            }
        }
    }
    
    /**
     * Log API responses
     */
    public static void apiResponse(String method, String url, int statusCode, String responseBody) {
        if (debugEnabled) {
            Log.i(TAG_API, String.format("API Response %s %s: %d", method, url, statusCode));
            if (responseBody != null && !responseBody.isEmpty()) {
                Log.i(TAG_API, "Response Body: " + responseBody);
            }
        }
    }
    
    /**
     * Log API errors
     * 4xx errors (client/business rule errors) → WARN
     * 5xx errors (server errors) → ERROR
     */
    public static void apiError(String method, String url, int statusCode, String errorBody) {
        String msg = String.format("API Error %s %s: %d", method, url, statusCode);
        // 4xx = business/client errors (WARN), 5xx = server errors (ERROR)
        if (statusCode >= 500) {
            Log.e(TAG_API, msg);
            if (errorBody != null && !errorBody.isEmpty()) {
                Log.e(TAG_API, "Error Body: " + errorBody);
            }
        } else {
            Log.w(TAG_API, msg);
            if (errorBody != null && !errorBody.isEmpty()) {
                Log.w(TAG_API, "Error Body: " + errorBody);
            }
        }
        // Forward to server as structured API error
        try {
            if (applicationContext != null) {
                ServerLogger.getInstance(applicationContext).apiError(method, url, statusCode, errorBody != null ? errorBody : "");
                ServerLogger.getInstance(applicationContext).flushLogs();
            }
        } catch (Throwable ignored) {
        }
    }
    
    /**
     * Log user actions
     */
    public static void userAction(String action, String details) {
        Log.i(TAG_UI, String.format("User Action: %s - %s", action, details));
    }
    
    /**
     * Log session events
     */
    public static void session(String event, String details) {
        Log.i(TAG_SESSION, String.format("Session Event: %s - %s", event, details));
    }
    
    /**
     * Log attendance events
     */
    public static void attendance(String event, String details) {
        Log.i(TAG_ATTENDANCE, String.format("Attendance Event: %s - %s", event, details));
    }
    
    /**
     * Log QR code events
     */
    public static void qr(String event, String details) {
        Log.i(TAG_QR, String.format("QR Event: %s - %s", event, details));
    }
    
    /**
     * Log preferences changes
     */
    public static void prefs(String key, String value) {
        if (debugEnabled) {
            Log.i(TAG_PREFS, String.format("Preference: %s = %s", key, value));
        }
    }
}
