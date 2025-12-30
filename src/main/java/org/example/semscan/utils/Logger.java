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

    // Component tags
    public static final String TAG_API = "SemScan-API";
    public static final String TAG_UI = "SemScan-UI";
    public static final String TAG_PREFS = "SemScan-Prefs";
    public static final String TAG_QR = "SemScan-QR";
    public static final String TAG_ATTENDANCE = "SemScan-Attendance";
    public static final String TAG_SESSION = "SemScan-Session";

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
     */
    public static void apiError(String method, String url, int statusCode, String errorBody) {
        Log.e(TAG_API, String.format("API Error %s %s: %d", method, url, statusCode));
        if (errorBody != null && !errorBody.isEmpty()) {
            Log.e(TAG_API, "Error Body: " + errorBody);
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
