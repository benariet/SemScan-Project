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
            return context.getString(R.string.error_network_connection);
        } else if (throwable instanceof java.net.UnknownHostException) {
            return context.getString(R.string.error_server_unavailable);
        } else {
            return context.getString(R.string.error_network_connection);
        }
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
                return "Invalid request. Please check your information and try again.";
            case 401:
                return "Authentication required. Please log in again.";
            case 403:
                return "You don't have permission to perform this action.";
            case 404:
                return "The requested item was not found. It may have been removed or doesn't exist.";
            case 409:
                return "This action conflicts with existing data. Please refresh and try again.";
            case 422:
                return "The information you provided is invalid. Please check and try again.";
            case 429:
                return "Too many requests. Please wait a moment and try again.";
            case 500:
            case 502:
            case 503:
                return context.getString(R.string.error_server_error);
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

