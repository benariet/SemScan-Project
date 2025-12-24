package org.example.semscan.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.example.semscan.utils.Logger;
import org.example.semscan.utils.SafeUtils;

/**
 * Base Activity class that provides crash protection and safe utility methods.
 * All Activities should extend this class for consistent error handling.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private static final String TAG = "BaseActivity";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ==================== LIFECYCLE PROTECTION ====================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            onCreateSafe(savedInstanceState);
        } catch (Exception e) {
            Logger.e(TAG, "Error in onCreate: " + getClass().getSimpleName(), e);
            handleLifecycleError(e);
        }
    }

    @Override
    protected void onStart() {
        try {
            super.onStart();
            onStartSafe();
        } catch (Exception e) {
            Logger.e(TAG, "Error in onStart: " + getClass().getSimpleName(), e);
        }
    }

    @Override
    protected void onResume() {
        try {
            super.onResume();
            onResumeSafe();
        } catch (Exception e) {
            Logger.e(TAG, "Error in onResume: " + getClass().getSimpleName(), e);
        }
    }

    @Override
    protected void onPause() {
        try {
            onPauseSafe();
        } catch (Exception e) {
            Logger.e(TAG, "Error in onPause: " + getClass().getSimpleName(), e);
        } finally {
            super.onPause();
        }
    }

    @Override
    protected void onStop() {
        try {
            onStopSafe();
        } catch (Exception e) {
            Logger.e(TAG, "Error in onStop: " + getClass().getSimpleName(), e);
        } finally {
            super.onStop();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            // Clear all pending handler callbacks to prevent memory leaks
            mainHandler.removeCallbacksAndMessages(null);
            onDestroySafe();
        } catch (Exception e) {
            Logger.e(TAG, "Error in onDestroy: " + getClass().getSimpleName(), e);
        } finally {
            super.onDestroy();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        try {
            super.onActivityResult(requestCode, resultCode, data);
            onActivityResultSafe(requestCode, resultCode, data);
        } catch (Exception e) {
            Logger.e(TAG, "Error in onActivityResult: " + getClass().getSimpleName(), e);
        }
    }

    // ==================== OVERRIDE THESE IN SUBCLASSES ====================

    /**
     * Safe onCreate - override this instead of onCreate
     */
    protected void onCreateSafe(@Nullable Bundle savedInstanceState) {
        // Override in subclass
    }

    /**
     * Safe onStart - override this instead of onStart
     */
    protected void onStartSafe() {
        // Override in subclass
    }

    /**
     * Safe onResume - override this instead of onResume
     */
    protected void onResumeSafe() {
        // Override in subclass
    }

    /**
     * Safe onPause - override this instead of onPause
     */
    protected void onPauseSafe() {
        // Override in subclass
    }

    /**
     * Safe onStop - override this instead of onStop
     */
    protected void onStopSafe() {
        // Override in subclass
    }

    /**
     * Safe onDestroy - override this instead of onDestroy
     */
    protected void onDestroySafe() {
        // Override in subclass
    }

    /**
     * Safe onActivityResult - override this instead of onActivityResult
     */
    protected void onActivityResultSafe(int requestCode, int resultCode, @Nullable Intent data) {
        // Override in subclass
    }

    // ==================== SAFE UTILITY METHODS ====================

    /**
     * Check if this activity is still valid for UI operations
     */
    protected boolean isActivityValid() {
        return !isFinishing() && !isDestroyed();
    }

    /**
     * Run code on UI thread only if activity is valid
     */
    protected void runSafe(@NonNull Runnable action) {
        if (!isActivityValid()) return;

        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                action.run();
            } catch (Exception e) {
                Logger.e(TAG, "Error in runSafe", e);
            }
        } else {
            runOnUiThread(() -> {
                if (isActivityValid()) {
                    try {
                        action.run();
                    } catch (Exception e) {
                        Logger.e(TAG, "Error in runSafe (posted)", e);
                    }
                }
            });
        }
    }

    /**
     * Run code after a delay, only if activity is still valid
     */
    protected void runDelayed(@NonNull Runnable action, long delayMs) {
        mainHandler.postDelayed(() -> {
            if (isActivityValid()) {
                try {
                    action.run();
                } catch (Exception e) {
                    Logger.e(TAG, "Error in runDelayed", e);
                }
            }
        }, delayMs);
    }

    /**
     * Safely finish this activity
     */
    protected void finishSafe() {
        if (isActivityValid()) {
            try {
                finish();
            } catch (Exception e) {
                Logger.e(TAG, "Error in finishSafe", e);
            }
        }
    }

    // ==================== SAFE INTENT EXTRAS ====================

    @NonNull
    protected String getStringExtraSafe(@NonNull String key, @NonNull String defaultValue) {
        return SafeUtils.getStringExtra(getIntent(), key, defaultValue);
    }

    protected long getLongExtraSafe(@NonNull String key, long defaultValue) {
        return SafeUtils.getLongExtra(getIntent(), key, defaultValue);
    }

    protected int getIntExtraSafe(@NonNull String key, int defaultValue) {
        return SafeUtils.getIntExtra(getIntent(), key, defaultValue);
    }

    protected boolean getBooleanExtraSafe(@NonNull String key, boolean defaultValue) {
        return SafeUtils.getBooleanExtra(getIntent(), key, defaultValue);
    }

    // ==================== SAFE VIEW OPERATIONS ====================

    /**
     * Safely find view by ID with null check
     */
    @Nullable
    protected <T extends View> T findViewSafe(int id) {
        try {
            return findViewById(id);
        } catch (Exception e) {
            Logger.e(TAG, "Error finding view: " + id, e);
            return null;
        }
    }

    /**
     * Safely set view visibility
     */
    protected void setVisibilitySafe(@Nullable View view, int visibility) {
        SafeUtils.setVisibility(view, visibility);
    }

    /**
     * Safely set text on TextView
     */
    protected void setTextSafe(@Nullable TextView textView, @Nullable String text) {
        SafeUtils.setText(textView, text);
    }

    // ==================== SAFE TOAST ====================

    /**
     * Safely show a toast message
     */
    protected void showToast(@NonNull String message) {
        runSafe(() -> {
            try {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Logger.e(TAG, "Error showing toast", e);
            }
        });
    }

    /**
     * Safely show a long toast message
     */
    protected void showToastLong(@NonNull String message) {
        runSafe(() -> {
            try {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Logger.e(TAG, "Error showing toast", e);
            }
        });
    }

    // ==================== ERROR HANDLING ====================

    /**
     * Handle errors in lifecycle methods
     */
    protected void handleLifecycleError(Exception e) {
        try {
            showToast("An error occurred");
            finishSafe();
        } catch (Exception ignored) {
            // Last resort - just finish
            try {
                finish();
            } catch (Exception ignored2) {}
        }
    }
}
