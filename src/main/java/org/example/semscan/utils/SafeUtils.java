package org.example.semscan.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Utility class for safe operations that prevent crashes.
 * Provides null-safe methods for common Android operations.
 */
public class SafeUtils {

    private static final String TAG = "SafeUtils";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ==================== INTENT EXTRAS ====================

    /**
     * Safely get a String extra from an Intent
     */
    @NonNull
    public static String getStringExtra(@Nullable Intent intent, @NonNull String key, @NonNull String defaultValue) {
        if (intent == null) return defaultValue;
        try {
            String value = intent.getStringExtra(key);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            Logger.e(TAG, "Error getting string extra: " + key, e);
            return defaultValue;
        }
    }

    /**
     * Safely get a long extra from an Intent
     */
    public static long getLongExtra(@Nullable Intent intent, @NonNull String key, long defaultValue) {
        if (intent == null) return defaultValue;
        try {
            return intent.getLongExtra(key, defaultValue);
        } catch (Exception e) {
            Logger.e(TAG, "Error getting long extra: " + key, e);
            return defaultValue;
        }
    }

    /**
     * Safely get an int extra from an Intent
     */
    public static int getIntExtra(@Nullable Intent intent, @NonNull String key, int defaultValue) {
        if (intent == null) return defaultValue;
        try {
            return intent.getIntExtra(key, defaultValue);
        } catch (Exception e) {
            Logger.e(TAG, "Error getting int extra: " + key, e);
            return defaultValue;
        }
    }

    /**
     * Safely get a boolean extra from an Intent
     */
    public static boolean getBooleanExtra(@Nullable Intent intent, @NonNull String key, boolean defaultValue) {
        if (intent == null) return defaultValue;
        try {
            return intent.getBooleanExtra(key, defaultValue);
        } catch (Exception e) {
            Logger.e(TAG, "Error getting boolean extra: " + key, e);
            return defaultValue;
        }
    }

    // ==================== ACTIVITY SAFETY ====================

    /**
     * Check if an Activity is still valid for UI operations
     */
    public static boolean isActivityValid(@Nullable Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    /**
     * Safely run code on the UI thread if activity is valid
     */
    public static void runOnUiThreadSafe(@Nullable Activity activity, @NonNull Runnable action) {
        if (!isActivityValid(activity)) return;

        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                action.run();
            } else {
                activity.runOnUiThread(() -> {
                    if (isActivityValid(activity)) {
                        try {
                            action.run();
                        } catch (Exception e) {
                            Logger.e(TAG, "Error in UI thread action", e);
                        }
                    }
                });
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error running on UI thread", e);
        }
    }

    /**
     * Safely run code with a delay, checking activity validity
     */
    public static void runDelayedSafe(@Nullable Activity activity, @NonNull Runnable action, long delayMs) {
        mainHandler.postDelayed(() -> {
            if (isActivityValid(activity)) {
                try {
                    action.run();
                } catch (Exception e) {
                    Logger.e(TAG, "Error in delayed action", e);
                }
            }
        }, delayMs);
    }

    /**
     * Safely finish an activity
     */
    public static void finishSafe(@Nullable Activity activity) {
        if (isActivityValid(activity)) {
            try {
                activity.finish();
            } catch (Exception e) {
                Logger.e(TAG, "Error finishing activity", e);
            }
        }
    }

    // ==================== VIEW SAFETY ====================

    /**
     * Safely set view visibility
     */
    public static void setVisibility(@Nullable View view, int visibility) {
        if (view == null) return;
        try {
            view.setVisibility(visibility);
        } catch (Exception e) {
            Logger.e(TAG, "Error setting visibility", e);
        }
    }

    /**
     * Safely set text on a TextView
     */
    public static void setText(@Nullable android.widget.TextView textView, @Nullable CharSequence text) {
        if (textView == null) return;
        try {
            textView.setText(text != null ? text : "");
        } catch (Exception e) {
            Logger.e(TAG, "Error setting text", e);
        }
    }

    /**
     * Safely set click listener
     */
    public static void setOnClickListener(@Nullable View view, @Nullable View.OnClickListener listener) {
        if (view == null) return;
        try {
            view.setOnClickListener(listener);
        } catch (Exception e) {
            Logger.e(TAG, "Error setting click listener", e);
        }
    }

    // ==================== STRING SAFETY ====================

    /**
     * Safely get a non-null string
     */
    @NonNull
    public static String safe(@Nullable String value) {
        return value != null ? value : "";
    }

    /**
     * Safely get a non-null string with default
     */
    @NonNull
    public static String safe(@Nullable String value, @NonNull String defaultValue) {
        return value != null && !value.isEmpty() ? value : defaultValue;
    }

    /**
     * Check if string is null or empty
     */
    public static boolean isEmpty(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    // ==================== NUMBER SAFETY ====================

    /**
     * Safely parse an integer
     */
    public static int parseInt(@Nullable String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Safely parse a long
     */
    public static long parseLong(@Nullable String value, long defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Safely parse a double
     */
    public static double parseDouble(@Nullable String value, double defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
