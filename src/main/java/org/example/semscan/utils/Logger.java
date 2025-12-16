package org.example.semscan.utils;

import android.util.Log;
import android.content.Context;

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
    
    // Log levels
    public static final boolean DEBUG_ENABLED = true; // Set to false for production
    private static Context applicationContext; // For forwarding errors to ServerLogger

    /**
     * Initialize logger with application context to enable server forwarding
     */
    public static void init(Context context) {
        applicationContext = context.getApplicationContext();
    }
    
    /**
     * Log debug messages
     */
    public static void d(String tag, String message) {
        if (DEBUG_ENABLED) {
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
        if (DEBUG_ENABLED) {
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
        if (DEBUG_ENABLED) {
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
        if (DEBUG_ENABLED) {
            Log.i(TAG_PREFS, String.format("Preference: %s = %s", key, value));
        }
    }
}
