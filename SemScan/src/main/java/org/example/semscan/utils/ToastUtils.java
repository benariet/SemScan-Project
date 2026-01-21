package org.example.semscan.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Utility class for showing custom duration Toast messages
 */
public class ToastUtils {
    
    /**
     * Show a Toast message with custom duration in milliseconds
     * @param context The context to show the toast in
     * @param message The message to display
     * @param durationMs Duration in milliseconds
     */
    public static void showCustomDuration(Context context, String message, int durationMs) {
        if (context == null || message == null) {
            return;
        }
        
        // Show the toast
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
        toast.show();
        
        // Cancel it after the custom duration
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                toast.cancel();
            }
        }, durationMs);
    }
    
    /**
     * Show an error message with custom duration
     * @param context The context to show the toast in
     * @param message The error message to display
     */
    public static void showError(Context context, String message) {
        showCustomDuration(context, "❌ " + message, org.example.semscan.constants.ApiConstants.TOAST_DURATION_ERROR);
    }
    
    /**
     * Show a success message with custom duration
     * @param context The context to show the toast in
     * @param message The success message to display
     */
    public static void showSuccess(Context context, String message) {
        showCustomDuration(context, "✅ " + message, org.example.semscan.constants.ApiConstants.TOAST_DURATION_SUCCESS);
    }
    
    /**
     * Show an info message with custom duration
     * @param context The context to show the toast in
     * @param message The info message to display
     */
    public static void showInfo(Context context, String message) {
        showCustomDuration(context, "ℹ️ " + message, org.example.semscan.constants.ApiConstants.TOAST_DURATION_INFO);
    }
    
    /**
     * Show a debug message with custom duration
     * @param context The context to show the toast in
     * @param message The debug message to display
     */
    public static void showDebug(Context context, String message) {
        showCustomDuration(context, "🐛 " + message, org.example.semscan.constants.ApiConstants.TOAST_DURATION_DEBUG);
    }
}
