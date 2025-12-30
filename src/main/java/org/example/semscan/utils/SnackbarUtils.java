package org.example.semscan.utils;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.snackbar.Snackbar;

import org.example.semscan.R;

/**
 * Utility class for showing Snackbar messages with common patterns.
 * Provides easy-to-use methods for error messages with retry actions.
 */
public class SnackbarUtils {

    private static final String TAG = "SnackbarUtils";

    /**
     * Show a retry error Snackbar with a custom message and retry action
     *
     * @param view        The view to attach the Snackbar to
     * @param message     The error message to display
     * @param retryAction The action to perform when retry is clicked
     */
    public static void showRetryError(@NonNull View view, @NonNull String message,
                                       @Nullable Runnable retryAction) {
        try {
            Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
            if (retryAction != null) {
                snackbar.setAction(R.string.retry, v -> {
                    try {
                        retryAction.run();
                    } catch (Exception e) {
                        Logger.e(TAG, "Error executing retry action", e);
                    }
                });
            }
            snackbar.show();
        } catch (Exception e) {
            Logger.e(TAG, "Error showing snackbar", e);
        }
    }

    /**
     * Show a retry error Snackbar with a string resource message
     *
     * @param view        The view to attach the Snackbar to
     * @param messageResId The string resource ID for the error message
     * @param retryAction The action to perform when retry is clicked
     */
    public static void showRetryError(@NonNull View view, @StringRes int messageResId,
                                       @Nullable Runnable retryAction) {
        try {
            String message = view.getContext().getString(messageResId);
            showRetryError(view, message, retryAction);
        } catch (Exception e) {
            Logger.e(TAG, "Error showing snackbar with resource", e);
        }
    }

    /**
     * Show a simple error Snackbar without retry action
     *
     * @param view    The view to attach the Snackbar to
     * @param message The error message to display
     */
    public static void showError(@NonNull View view, @NonNull String message) {
        try {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            Logger.e(TAG, "Error showing snackbar", e);
        }
    }

    /**
     * Show a simple error Snackbar with a string resource
     *
     * @param view        The view to attach the Snackbar to
     * @param messageResId The string resource ID for the error message
     */
    public static void showError(@NonNull View view, @StringRes int messageResId) {
        try {
            Snackbar.make(view, messageResId, Snackbar.LENGTH_LONG).show();
        } catch (Exception e) {
            Logger.e(TAG, "Error showing snackbar with resource", e);
        }
    }

    /**
     * Show a success Snackbar with a short duration
     *
     * @param view    The view to attach the Snackbar to
     * @param message The success message to display
     */
    public static void showSuccess(@NonNull View view, @NonNull String message) {
        try {
            Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
        } catch (Exception e) {
            Logger.e(TAG, "Error showing snackbar", e);
        }
    }

    /**
     * Show a network error Snackbar with retry action
     *
     * @param view        The view to attach the Snackbar to
     * @param throwable   The network error (used to determine appropriate message)
     * @param retryAction The action to perform when retry is clicked
     */
    public static void showNetworkError(@NonNull View view, @Nullable Throwable throwable,
                                         @Nullable Runnable retryAction) {
        String message = ErrorMessageHelper.getNetworkErrorMessage(view.getContext(), throwable);
        showRetryError(view, message, retryAction);
    }

    /**
     * Show an HTTP error Snackbar, with retry if the error is retryable
     *
     * @param view           The view to attach the Snackbar to
     * @param statusCode     The HTTP status code
     * @param backendMessage Optional message from backend
     * @param retryAction    The action to perform when retry is clicked (only shown if retryable)
     */
    public static void showHttpError(@NonNull View view, int statusCode,
                                      @Nullable String backendMessage,
                                      @Nullable Runnable retryAction) {
        String message = ErrorMessageHelper.getHttpErrorMessage(view.getContext(), statusCode, backendMessage);

        // Only show retry for retryable errors
        if (ErrorMessageHelper.isRetryableHttpError(statusCode)) {
            showRetryError(view, message, retryAction);
        } else {
            showError(view, message);
        }
    }

    /**
     * Show an indefinite Snackbar for offline mode with reconnect action
     *
     * @param view            The view to attach the Snackbar to
     * @param reconnectAction The action to perform when reconnect is clicked
     * @return The Snackbar instance (can be dismissed later)
     */
    @Nullable
    public static Snackbar showOfflineMode(@NonNull View view, @Nullable Runnable reconnectAction) {
        try {
            Snackbar snackbar = Snackbar.make(view,
                    R.string.error_no_internet_retry,
                    Snackbar.LENGTH_INDEFINITE);
            if (reconnectAction != null) {
                snackbar.setAction(R.string.retry, v -> {
                    try {
                        reconnectAction.run();
                    } catch (Exception e) {
                        Logger.e(TAG, "Error executing reconnect action", e);
                    }
                });
            }
            snackbar.show();
            return snackbar;
        } catch (Exception e) {
            Logger.e(TAG, "Error showing offline snackbar", e);
            return null;
        }
    }
}
