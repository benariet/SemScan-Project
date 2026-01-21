package org.example.semscan.utils;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Safe Retrofit callback wrapper that prevents crashes from API responses.
 * Handles null responses, network errors, and activity destruction gracefully.
 *
 * @param <T> The response type
 */
public abstract class SafeCallback<T> implements Callback<T> {

    private static final String TAG = "SafeCallback";
    private final Activity activity;
    private final String operationName;

    /**
     * Create a SafeCallback without activity binding (for service calls)
     */
    public SafeCallback() {
        this(null, "API Call");
    }

    /**
     * Create a SafeCallback with activity binding
     * Callbacks won't fire if activity is destroyed
     */
    public SafeCallback(@Nullable Activity activity) {
        this(activity, "API Call");
    }

    /**
     * Create a SafeCallback with activity binding and operation name for logging
     */
    public SafeCallback(@Nullable Activity activity, @NonNull String operationName) {
        this.activity = activity;
        this.operationName = operationName;
    }

    @Override
    public final void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
        // Check if activity is still valid
        if (activity != null && (activity.isFinishing() || activity.isDestroyed())) {
            Logger.w(TAG, operationName + " - Activity destroyed, ignoring response");
            return;
        }

        try {
            if (response.isSuccessful()) {
                T body = response.body();
                if (body != null) {
                    onSuccess(body);
                } else {
                    Logger.w(TAG, operationName + " - Response body is null");
                    onNullBody(response);
                }
            } else {
                Logger.w(TAG, operationName + " - Error response: " + response.code());
                onError(response.code(), getErrorMessage(response));
            }
        } catch (Exception e) {
            Logger.e(TAG, operationName + " - Exception in callback", e);
            onException(e);
        }
    }

    @Override
    public final void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
        // Check if activity is still valid
        if (activity != null && (activity.isFinishing() || activity.isDestroyed())) {
            Logger.w(TAG, operationName + " - Activity destroyed, ignoring failure");
            return;
        }

        try {
            Logger.e(TAG, operationName + " - Network failure", t);
            onNetworkError(t);
        } catch (Exception e) {
            Logger.e(TAG, operationName + " - Exception handling failure", e);
            onException(e);
        }
    }

    // ==================== OVERRIDE THESE ====================

    /**
     * Called when response is successful and body is not null
     */
    protected abstract void onSuccess(@NonNull T response);

    /**
     * Called when response is successful but body is null
     * Default: calls onError with code 204
     */
    protected void onNullBody(@NonNull Response<T> response) {
        onError(204, "Empty response");
    }

    /**
     * Called when server returns an error response (4xx, 5xx)
     */
    protected void onError(int code, @NonNull String message) {
        // Default: log the error
        Logger.e(TAG, operationName + " - HTTP Error " + code + ": " + message);
    }

    /**
     * Called when network request fails (no connection, timeout, etc.)
     */
    protected void onNetworkError(@NonNull Throwable t) {
        // Default: treat as error
        String message = t.getMessage() != null ? t.getMessage() : "Network error";
        onError(0, message);
    }

    /**
     * Called when an exception occurs in any callback method
     */
    protected void onException(@NonNull Exception e) {
        // Default: log it
        Logger.e(TAG, operationName + " - Exception", e);
    }

    // ==================== UTILITY ====================

    /**
     * Extract error message from error response
     */
    @NonNull
    private String getErrorMessage(@NonNull Response<T> response) {
        try {
            if (response.errorBody() != null) {
                String errorBody = response.errorBody().string();
                if (errorBody != null && !errorBody.isEmpty()) {
                    return errorBody;
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error reading error body", e);
        }
        return response.message() != null ? response.message() : "Unknown error";
    }

    /**
     * Get the activity (may be null)
     */
    @Nullable
    protected Activity getActivity() {
        return activity;
    }

    /**
     * Check if activity is still valid
     */
    protected boolean isActivityValid() {
        return activity == null || (!activity.isFinishing() && !activity.isDestroyed());
    }

    /**
     * Run code on UI thread if activity is valid
     */
    protected void runOnUiThread(@NonNull Runnable action) {
        if (activity != null && isActivityValid()) {
            activity.runOnUiThread(() -> {
                if (isActivityValid()) {
                    try {
                        action.run();
                    } catch (Exception e) {
                        Logger.e(TAG, "Error in UI thread action", e);
                    }
                }
            });
        }
    }
}
