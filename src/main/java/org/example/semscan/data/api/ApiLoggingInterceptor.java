package org.example.semscan.data.api;

import android.content.Context;
import android.util.Log;

import org.example.semscan.utils.ServerLogger;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

/**
 * Custom OkHttp interceptor that logs all API request and response bodies to ServerLogger (app_logs)
 */
public class ApiLoggingInterceptor implements Interceptor {
    private static final String TAG = "ApiLoggingInterceptor";
    private final Context context;
    private volatile ServerLogger serverLogger; // Lazy-loaded to avoid circular dependency
    
    public ApiLoggingInterceptor(Context context) {
        this.context = context.getApplicationContext();
        // Don't initialize ServerLogger here - lazy load it in intercept() to avoid circular dependency
        // ServerLogger -> ApiClient -> ApiLoggingInterceptor -> ServerLogger (circular!)
    }
    
    /**
     * Get ServerLogger instance lazily to avoid circular dependency during initialization
     */
    private ServerLogger getServerLogger() {
        if (serverLogger == null) {
            synchronized (this) {
                if (serverLogger == null) {
                    serverLogger = ServerLogger.getInstance(context);
                }
            }
        }
        return serverLogger;
    }
    
    /**
     * Sanitize passwords from JSON strings to prevent logging sensitive data.
     * Replaces password values with "***" while preserving JSON structure.
     */
    private String sanitizePasswords(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return jsonString;
        }
        
        // Pattern to match "password":"value" or "password": "value" in JSON
        // Handles escaped quotes and any characters in the password value
        // CASE_INSENSITIVE handles "password", "Password", "PASSWORD", etc.
        // Matches: "password":"any value here" or "password": "any value here"
        Pattern passwordPattern = Pattern.compile(
            "\"password\"\\s*:\\s*\"(?:[^\"\\\\]|\\\\.)*\"",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = passwordPattern.matcher(jsonString);
        return matcher.replaceAll("\"password\":\"***\"");
    }
    
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        String method = originalRequest.method();
        String url = originalRequest.url().toString();
        String path = originalRequest.url().encodedPath();
        
        // Check if this is the logs endpoint - skip ServerLogger to avoid recursion
        // but still log to Android Logcat for debugging
        boolean isLogsEndpoint = "/api/v1/logs".equals(path);
        
        // Log request body (read and recreate to avoid consuming)
        String requestBody = null;
        Request request = originalRequest;
        if (originalRequest.body() != null) {
            RequestBody originalRequestBody = originalRequest.body();
            Buffer buffer = new Buffer();
            originalRequestBody.writeTo(buffer);
            byte[] bodyBytes = buffer.readByteArray();
            
            // Read as string for logging
            Charset charset = StandardCharsets.UTF_8;
            MediaType contentType = originalRequestBody.contentType();
            if (contentType != null) {
                charset = contentType.charset(StandardCharsets.UTF_8);
            }
            requestBody = new String(bodyBytes, charset);
            
            if (requestBody != null && !requestBody.isEmpty()) {
                // Sanitize passwords before logging
                String sanitizedRequestBody = sanitizePasswords(requestBody);
                
                // Always log to Android Logcat (with sanitized password)
                String requestLog = String.format("Request Body: %s", sanitizedRequestBody);
                Log.i(TAG, method + " " + url + " - " + requestLog);
                
                // Only log to ServerLogger if NOT the logs endpoint (to avoid recursion)
                if (!isLogsEndpoint) {
                    getServerLogger().d(ServerLogger.TAG_API, requestLog);
                }
            }
            
            // Recreate request with new body (since we consumed the original)
            RequestBody newRequestBody = RequestBody.create(contentType, bodyBytes);
            request = originalRequest.newBuilder()
                .method(method, newRequestBody)
                .build();
        }
        
        // Log request details (with sanitized password)
        String requestDetails = String.format("Request: %s %s", method, path);
        if (requestBody != null && !requestBody.isEmpty()) {
            String sanitizedRequestBody = sanitizePasswords(requestBody);
            requestDetails += " - Body: " + sanitizedRequestBody;
        }
        
        // Only log to ServerLogger if NOT the logs endpoint
        if (!isLogsEndpoint) {
            getServerLogger().api(method, path, requestDetails);
        } else {
            // Still log to Android Logcat for debugging
            Log.i(TAG, requestDetails);
        }
        
        // Execute request
        long startTime = System.currentTimeMillis();
        Response response = chain.proceed(request);
        long duration = System.currentTimeMillis() - startTime;
        
        // Log response
        ResponseBody responseBody = response.body();
        String responseBodyString = null;
        
        if (responseBody != null) {
            // Read response body (we need to create a new response body for the chain)
            BufferedSource source = responseBody.source();
            source.request(Long.MAX_VALUE); // Request all bytes
            Buffer buffer = source.buffer();
            
            Charset charset = StandardCharsets.UTF_8;
            MediaType contentType = responseBody.contentType();
            if (contentType != null) {
                charset = contentType.charset(StandardCharsets.UTF_8);
            }
            
            responseBodyString = buffer.clone().readString(charset);
            
            // Always log to Android Logcat (with sanitized password if present)
            if (responseBodyString != null && !responseBodyString.isEmpty()) {
                // Sanitize passwords from response body as well (in case server returns password)
                String sanitizedResponseBody = sanitizePasswords(responseBodyString);
                String responseLog = String.format("Response Body: %s", sanitizedResponseBody);
                Log.i(TAG, method + " " + url + " - " + responseLog);
                
                // Only log to ServerLogger if NOT the logs endpoint
                if (!isLogsEndpoint) {
                    getServerLogger().d(ServerLogger.TAG_API, responseLog);
                }
            }
            
            // Create new response body with the same content
            ResponseBody newResponseBody = ResponseBody.create(
                responseBody.contentType(),
                responseBodyString
            );
            
            // Build new response with the logged body
            response = response.newBuilder()
                .body(newResponseBody)
                .build();
        }
        
        // Log response details (with sanitized password if present)
        int statusCode = response.code();
        String responseDetails = String.format("Status: %d, Duration: %dms", statusCode, duration);
        if (responseBodyString != null && !responseBodyString.isEmpty()) {
            String sanitizedResponseBody = sanitizePasswords(responseBodyString);
            responseDetails += " - Body: " + sanitizedResponseBody;
        }
        
        // Only log to ServerLogger if NOT the logs endpoint
        if (!isLogsEndpoint) {
            if (response.isSuccessful()) {
                getServerLogger().apiResponse(method, path, statusCode, responseDetails);
            } else {
                // responseDetails already includes sanitized response body (if present)
                getServerLogger().apiError(method, path, statusCode, responseDetails);
            }
        } else {
            // Still log to Android Logcat for debugging
            Log.i(TAG, responseDetails);
        }
        
        return response;
    }
    
}

