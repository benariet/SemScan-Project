package org.example.semscan.utils;

import android.content.Context;
import org.example.semscan.R;

/**
 * Helper class to format user-friendly error messages
 * Converts technical errors into understandable, actionable messages
 */
public class ErrorMessageHelper {
    
    /**
     * Get user-friendly error message for network errors
     */
    public static String getNetworkErrorMessage(Context context, Throwable throwable) {
        if (throwable instanceof java.net.SocketTimeoutException) {
            return context.getString(R.string.error_network_timeout);
        } else if (throwable instanceof java.net.ConnectException) {
            return context.getString(R.string.error_no_internet_retry);
        } else if (throwable instanceof java.net.UnknownHostException) {
            return context.getString(R.string.error_server_unavailable);
        } else if (throwable instanceof java.io.IOException) {
            String message = throwable.getMessage();
            if (message != null && message.toLowerCase().contains("reset")) {
                return context.getString(R.string.error_connection_reset);
            }
            return context.getString(R.string.error_network_connection);
        } else {
            return context.getString(R.string.error_network_connection);
        }
    }

    /**
     * Check if an error is retryable (network issues that may resolve)
     */
    public static boolean isRetryableError(Throwable throwable) {
        if (throwable == null) return false;

        return throwable instanceof java.net.SocketTimeoutException
            || throwable instanceof java.net.ConnectException
            || throwable instanceof java.net.UnknownHostException
            || (throwable instanceof java.io.IOException &&
                !(throwable instanceof java.io.FileNotFoundException));
    }

    /**
     * Check if HTTP status code indicates a retryable error
     */
    public static boolean isRetryableHttpError(int statusCode) {
        // 5xx server errors and 408 timeout are retryable
        return statusCode == 408 || statusCode == 429 ||
               (statusCode >= 500 && statusCode <= 599);
    }
    
    /**
     * Get user-friendly error message for HTTP response codes
     */
    public static String getHttpErrorMessage(Context context, int statusCode, String backendMessage) {
        // If backend provides a user-friendly message, use it
        if (backendMessage != null && !backendMessage.trim().isEmpty()) {
            // Clean up technical error messages
            String cleaned = cleanBackendMessage(backendMessage);
            if (!cleaned.equals(backendMessage)) {
                return cleaned;
            }
        }
        
        // Fallback to status code-based messages
        switch (statusCode) {
            case 400:
                return context.getString(R.string.error_invalid_request);
            case 401:
                return context.getString(R.string.error_session_expired);
            case 403:
                return context.getString(R.string.error_permission_denied);
            case 404:
                return context.getString(R.string.error_not_found);
            case 409:
                return context.getString(R.string.error_conflict);
            case 422:
                return context.getString(R.string.error_validation_failed);
            case 429:
                return context.getString(R.string.error_too_many_requests);
            case 500:
            case 502:
            case 503:
                return context.getString(R.string.error_server_busy_retry);
            case 504:
                return context.getString(R.string.error_gateway_timeout);
            default:
                return context.getString(R.string.error_unknown);
        }
    }
    
    /**
     * Clean up technical backend messages to be more user-friendly
     */
    public static String cleanBackendMessage(String message) {
        if (message == null) {
            return null;
        }
        
        String lower = message.toLowerCase();
        
        // Database errors
        if (lower.contains("lock wait timeout") || lower.contains("database lock") || lower.contains("deadlock")) {
            return "The system is busy. Please wait a few moments and try again.";
        }
        
        // Session errors
        if (lower.contains("session is not open") || lower.contains("status: closed") || 
            lower.contains("session is closed") || lower.contains("not open")) {
            return "This session is no longer accepting attendance. Please check with your presenter.";
        }
        
        if (lower.contains("session not found") || lower.contains("session does not exist")) {
            return "This session is no longer available. Please refresh and select a different session.";
        }
        
        // Already attended
        if (lower.contains("already attended") || lower.contains("already present")) {
            return "You have already marked your attendance for this session.";
        }
        
        // Already registered
        if (lower.contains("already registered") || lower.contains("already in slot")) {
            return "You are already registered for this slot.";
        }
        
        // Slot full
        if (lower.contains("slot full") || lower.contains("slot is full")) {
            return "This slot is full. You can join the waiting list instead.";
        }
        
        // Waiting list errors
        if (lower.contains("already on waiting list") || lower.contains("already in waiting list")) {
            return "You are already on the waiting list for this slot.";
        }
        
        if (lower.contains("not on waiting list") || lower.contains("not in waiting list")) {
            return "You are not on the waiting list for this slot.";
        }
        
        if (lower.contains("waiting list full") || lower.contains("waiting list is full")) {
            return "The waiting list is full. Please try again later.";
        }
        
        if (lower.contains("cannot join waiting list") || lower.contains("unable to join waiting list")) {
            return "Cannot join waiting list. You may already be registered or on another waiting list.";
        }
        
        // Supervisor info errors
        if (lower.contains("supervisor") && (lower.contains("required") || lower.contains("missing"))) {
            return "Supervisor information is required. Please set your supervisor details in Settings first.";
        }
        
        if (lower.contains("supervisor") && lower.contains("email") && lower.contains("invalid")) {
            return "Invalid supervisor email. Please check your supervisor email in Settings.";
        }
        
        // Validation errors
        if (lower.contains("invalid") && lower.contains("session")) {
            return "Invalid session. Please scan the QR code again or contact your presenter.";
        }
        
        // Remove technical details
        if (message.contains("backend error") || message.contains("backend bug")) {
            return "A system error occurred. Please try again or contact support if the problem persists.";
        }
        
        // Remove URLs and technical paths
        if (message.contains("http://") || message.contains("https://") || message.contains("api/v1/")) {
            return "A connection error occurred. Please check your internet connection and try again.";
        }
        
        return message;
    }
    
    /**
     * Get user-friendly registration error message
     */
    public static String getRegistrationErrorMessage(Context context, String errorCode, String backendMessage) {
        if ("ALREADY_REGISTERED".equals(errorCode) || "ALREADY_IN_SLOT".equals(errorCode)) {
            return context.getString(R.string.presenter_home_register_already);
        } else if ("SLOT_FULL".equals(errorCode)) {
            return context.getString(R.string.presenter_home_register_full);
        } else if (backendMessage != null && !backendMessage.trim().isEmpty()) {
            return cleanBackendMessage(backendMessage);
        } else {
            return context.getString(R.string.error_registration_failed);
        }
    }
    
    /**
     * Get user-friendly attendance error message
     */
    public static String getAttendanceErrorMessage(Context context, int statusCode, String backendMessage) {
        String cleaned = cleanBackendMessage(backendMessage);
        if (cleaned != null && !cleaned.equals(backendMessage)) {
            return cleaned;
        }
        
        return getHttpErrorMessage(context, statusCode, backendMessage);
    }
    
    /**
     * Get user-friendly waiting list error message
     */
    public static String getWaitingListErrorMessage(Context context, int statusCode, String backendMessage) {
        if (backendMessage != null && !backendMessage.trim().isEmpty()) {
            String cleaned = cleanBackendMessage(backendMessage);
            if (!cleaned.equals(backendMessage)) {
                return cleaned;
            }
        }
        
        // Waiting list specific error messages
        if (backendMessage != null) {
            String lower = backendMessage.toLowerCase();
            
            if (lower.contains("already on waiting list") || lower.contains("already in waiting list")) {
                return "You are already on the waiting list for this slot.";
            }
            
            if (lower.contains("not on waiting list") || lower.contains("not in waiting list")) {
                return "You are not on the waiting list for this slot.";
            }
            
            if (lower.contains("waiting list full") || lower.contains("waiting list is full")) {
                return "The waiting list is full. Please try again later.";
            }
            
            if (lower.contains("already registered") || lower.contains("already in slot")) {
                return "You are already registered for this slot. You cannot join the waiting list.";
            }
            
            if (lower.contains("slot not found") || lower.contains("slot does not exist")) {
                return "This slot is no longer available.";
            }
            
            // Supervisor info errors
            if (lower.contains("supervisor") && (lower.contains("required") || lower.contains("missing"))) {
                return "Please set your supervisor details in Settings before joining the waiting list.";
            }
            
            if (lower.contains("supervisor") && lower.contains("email") && lower.contains("invalid")) {
                return "Invalid supervisor email. Please check your supervisor email in Settings.";
            }
        }
        
        // Fallback to HTTP status code messages
        switch (statusCode) {
            case 400:
                return "Invalid request. Please check your information and try again.";
            case 404:
                return "This slot is no longer available or was not found.";
            case 409:
                return "You are already on the waiting list for this slot.";
            case 422:
                return "Cannot join waiting list. You may already be registered or on another waiting list.";
            default:
                return getHttpErrorMessage(context, statusCode, backendMessage);
        }
    }
    
    /**
     * Format error with actionable next steps
     */
    public static String formatErrorWithSteps(String errorMessage, String... steps) {
        if (steps == null || steps.length == 0) {
            return errorMessage;
        }
        
        StringBuilder sb = new StringBuilder(errorMessage);
        sb.append("\n\nWhat you can do:");
        for (int i = 0; i < steps.length; i++) {
            sb.append("\n").append(i + 1).append(". ").append(steps[i]);
        }
        
        return sb.toString();
    }
}

