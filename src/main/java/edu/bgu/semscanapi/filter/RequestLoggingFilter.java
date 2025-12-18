package edu.bgu.semscanapi.filter;

import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Filter for logging HTTP requests and responses
 * Provides detailed logging of API calls with correlation IDs
 * Logs all actions to both file and database
 */
public class RequestLoggingFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);
    
    private DatabaseLoggerService databaseLoggerService;
    
    // Constructor injection for proper dependency injection
    public RequestLoggingFilter(DatabaseLoggerService databaseLoggerService) {
        this.databaseLoggerService = databaseLoggerService;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Wrap request and response to capture bodies
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);
        
        // Generate correlation ID if not present
        String correlationId = wrappedRequest.getHeader("X-Correlation-ID");
        if (!StringUtils.hasText(correlationId)) {
            correlationId = LoggerUtil.generateAndSetCorrelationId();
        } else {
            LoggerUtil.setCorrelationId(correlationId);
        }
        
        // Add correlation ID to response headers
        wrappedResponse.setHeader("X-Correlation-ID", correlationId);
        
        // Measure request duration
        long startTime = System.currentTimeMillis();
        
        Exception caughtException = null;
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } catch (Exception e) {
            // Catch exceptions in filter to log immediately
            caughtException = e;
            logger.error("Exception caught in filter - Method: {}, URI: {}, Correlation ID: {}, Exception: {}", 
                        httpRequest.getMethod(), httpRequest.getRequestURI(), correlationId,
                        e.getClass().getSimpleName(), e);
            
            // Log to app_logs table
            if (databaseLoggerService != null) {
                try {
                    String bguUsername = LoggerUtil.getCurrentBguUsername();
                    if (bguUsername == null || bguUsername.trim().isEmpty()) {
                        String requestBody = getRequestBody(wrappedRequest);
                        bguUsername = extractUsernameFromRequest(httpRequest, requestBody);
                    }
                    String requestBody = getRequestBody(wrappedRequest);
                    String payload = String.format("correlationId=%s,method=%s,uri=%s,exceptionType=%s,message=%s",
                                                  correlationId, httpRequest.getMethod(), httpRequest.getRequestURI(),
                                                  e.getClass().getSimpleName(), 
                                                  e.getMessage() != null ? e.getMessage() : "null");
                    databaseLoggerService.logError("FILTER_EXCEPTION", 
                                                  "Exception caught in RequestLoggingFilter: " + e.getMessage(),
                                                  e, bguUsername, payload);
                } catch (Exception logEx) {
                    logger.error("Failed to log exception to app_logs table", logEx);
                }
            }
            
            // Re-throw to let Spring handle it
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // Extract request body
            String requestBody = getRequestBody(wrappedRequest);
            
            // Extract response body
            String responseBody = getResponseBody(wrappedResponse);
            
            // Log request with body
            logRequest(wrappedRequest, requestBody);
            
            // Log response with body (or exception if one occurred)
            if (caughtException != null) {
                logger.error("Request failed - Method: {}, URI: {}, Duration: {}ms, Exception: {}", 
                           httpRequest.getMethod(), httpRequest.getRequestURI(), duration,
                           caughtException.getClass().getSimpleName());
            } else {
                logResponse(wrappedRequest, wrappedResponse, duration, responseBody);
            }
            
            // Copy response body back to original response
            try {
                wrappedResponse.copyBodyToResponse();
            } catch (Exception e) {
                logger.error("Failed to copy response body", e);
            }
            
            LoggerUtil.clearContext();
        }
    }
    
    private void logRequest(HttpServletRequest request, String requestBody) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();
            String fullUrl = queryString != null ? uri + "?" + queryString : uri;
            
            // Sanitize request body to remove passwords before logging
            String sanitizedBody = sanitizePasswordFromBody(requestBody);
            
            // Truncate body for logging if too long (max 2000 characters)
            String truncatedBody = truncateBody(sanitizedBody, 2000);
            
            // Log to file with body
            if (truncatedBody != null && !truncatedBody.isEmpty()) {
                logger.info("Incoming Request - Method: {}, URL: {}, Remote Address: {}, Body: {}", 
                           method, fullUrl, getClientIpAddress(request), truncatedBody);
            } else {
                logger.info("Incoming Request - Method: {}, URL: {}, Remote Address: {}", 
                           method, fullUrl, getClientIpAddress(request));
            }
            
            // Log to database with sanitized body (full body, but will be truncated in database if needed)
            // Try to extract username from request if not already in MDC context
            String bguUsername = LoggerUtil.getCurrentBguUsername();
            if (bguUsername == null || bguUsername.trim().isEmpty()) {
                // Try to extract username from request body or query parameters
                bguUsername = extractUsernameFromRequest(request, sanitizedBody);
                if (bguUsername != null && !bguUsername.trim().isEmpty()) {
                    LoggerUtil.setBguUsername(bguUsername);
                }
            }
            
            if (databaseLoggerService != null) {
                databaseLoggerService.logApiRequest(method, uri, bguUsername, sanitizedBody);
            }
            
            // Log headers (excluding sensitive ones)
            Map<String, String> headers = getFilteredHeaders(request);
            if (!headers.isEmpty()) {
                logger.debug("Request Headers - {}", headers);
            }
            
        } catch (Exception e) {
            logger.error("Error logging request", e);
        }
    }
    
    private void logResponse(HttpServletRequest request, HttpServletResponse response, long duration, String responseBody) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int statusCode = response.getStatus();
            
            // Truncate body for logging if too long (max 2000 characters)
            String truncatedBody = truncateBody(responseBody, 2000);
            
            // Log to file with body
            if (truncatedBody != null && !truncatedBody.isEmpty()) {
                logger.info("Outgoing Response - Method: {}, URL: {}, Status: {}, Duration: {}ms, Body: {}", 
                           method, uri, statusCode, duration, truncatedBody);
            } else {
                logger.info("Outgoing Response - Method: {}, URL: {}, Status: {}, Duration: {}ms", 
                           method, uri, statusCode, duration);
            }
            
            // Log to database with body (full body, but will be truncated in database if needed)
            // Try to extract username from request if not already in MDC context
            String bguUsername = LoggerUtil.getCurrentBguUsername();
            if (bguUsername == null || bguUsername.trim().isEmpty()) {
                // Try to extract username from request body or query parameters
                String requestBody = null;
                if (request instanceof ContentCachingRequestWrapper) {
                    requestBody = getRequestBody((ContentCachingRequestWrapper) request);
                }
                bguUsername = extractUsernameFromRequest(request, requestBody);
                if (bguUsername != null && !bguUsername.trim().isEmpty()) {
                    LoggerUtil.setBguUsername(bguUsername);
                }
            }
            
            if (databaseLoggerService != null) {
                databaseLoggerService.logApiResponse(method, uri, statusCode, bguUsername, responseBody);
            }
            
            // Log response headers
            Map<String, String> responseHeaders = getResponseHeaders(response);
            if (!responseHeaders.isEmpty()) {
                logger.debug("Response Headers - {}", responseHeaders);
            }
            
            // Log performance warning for slow requests
            if (duration > 1000) {
                logger.warn("Slow Request - Method: {}, URL: {}, Duration: {}ms", method, uri, duration);
                // Log slow request to database
                if (databaseLoggerService != null) {
                    String slowPayload = String.format("duration=%dms,correlationId=%s", duration, LoggerUtil.getCurrentCorrelationId());
                    databaseLoggerService.logAction("WARN", "PERFORMANCE", 
                        String.format("Slow request: %s %s took %dms", method, uri, duration), bguUsername,
                        slowPayload);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error logging response", e);
        }
    }
    
    private String getRequestBody(ContentCachingRequestWrapper request) {
        try {
            byte[] contentAsByteArray = request.getContentAsByteArray();
            if (contentAsByteArray != null && contentAsByteArray.length > 0) {
                String contentType = request.getContentType();
                if (contentType != null && contentType.contains("application/json")) {
                    return new String(contentAsByteArray, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read request body: {}", e.getMessage());
        }
        return null;
    }
    
    private String getResponseBody(ContentCachingResponseWrapper response) {
        try {
            byte[] contentAsByteArray = response.getContentAsByteArray();
            if (contentAsByteArray != null && contentAsByteArray.length > 0) {
                String contentType = response.getContentType();
                
                // Try to read as text for JSON and text content types
                if (contentType != null && (contentType.contains("application/json") 
                        || contentType.contains("text/") 
                        || contentType.contains("application/xml")
                        || contentType.contains("application/x-www-form-urlencoded"))) {
                    try {
                        String body = new String(contentAsByteArray, StandardCharsets.UTF_8);
                        // Limit body size to prevent huge logs (max 10000 characters for database)
                        if (body.length() > 10000) {
                            return body.substring(0, 10000) + "... (truncated)";
                        }
                        return body;
                    } catch (Exception e) {
                        logger.debug("Could not decode response body as text: {}", e.getMessage());
                    }
                }
                
                // For binary content, log content type and size
                if (contentType != null) {
                    return String.format("[Binary Content: %s, Size: %d bytes]", contentType, contentAsByteArray.length);
                } else {
                    return String.format("[Binary Content: Size: %d bytes]", contentAsByteArray.length);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read response body: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Truncate body string to prevent huge logs and escape issues
     */
    private String truncateBody(String body, int maxLength) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        
        if (body.length() <= maxLength) {
            return body;
        }
        
        return body.substring(0, maxLength) + "... (truncated, original length: " + body.length() + ")";
    }
    
    private Map<String, String> getFilteredHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            
            // Filter out sensitive headers
            if (!isSensitiveHeader(headerName)) {
                headers.put(headerName, headerValue);
            }
        }
        
        return headers;
    }
    
    private Map<String, String> getResponseHeaders(HttpServletResponse response) {
        Map<String, String> headers = new HashMap<>();
        
        // Get common response headers
        String contentType = response.getContentType();
        if (contentType != null) {
            headers.put("Content-Type", contentType);
        }
        
        String contentLength = response.getHeader("Content-Length");
        if (contentLength != null) {
            headers.put("Content-Length", contentLength);
        }
        
        return headers;
    }
    
    private boolean isSensitiveHeader(String headerName) {
        String lowerHeaderName = headerName.toLowerCase();
        return lowerHeaderName.contains("authorization") ||
               lowerHeaderName.contains("cookie") ||
               lowerHeaderName.contains("x-api-key") ||
               lowerHeaderName.contains("password") ||
               lowerHeaderName.contains("token");
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Sanitize password fields from request body to prevent logging sensitive data
     * Handles JSON format: {"password":"secret"} -> {"password":"***"}
     */
    private String sanitizePasswordFromBody(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        
        // Only process JSON bodies
        if (!body.trim().startsWith("{") && !body.trim().startsWith("[")) {
            return body;
        }
        
        try {
            // Replace password field values in JSON
            // Pattern: "password":"value" or "password": "value" or "password":"value", or "password": "value",
            // Also handles: "Password", "PASSWORD", etc. (case-insensitive)
            String sanitized = body.replaceAll(
                "(?i)(\"password\"\\s*:\\s*\")([^\"]+)(\")",
                "$1***$3"
            );
            
            // Also handle password in form-encoded format: password=value
            sanitized = sanitized.replaceAll(
                "(?i)(password=)([^&\\s]+)",
                "$1***"
            );
            
            return sanitized;
        } catch (Exception e) {
            // If sanitization fails, return original body but log warning
            logger.warn("Failed to sanitize password from request body: {}", e.getMessage());
            return body;
        }
    }
    
    /**
     * Extract username from request body or query parameters
     * This helps populate MDC context for logging when username is not yet set
     */
    private String extractUsernameFromRequest(HttpServletRequest request, String requestBody) {
        try {
            // Try to extract from query parameters first (for DELETE requests)
            String usernameParam = request.getParameter("username");
            if (usernameParam != null && !usernameParam.trim().isEmpty()) {
                return usernameParam.trim();
            }
            
            // Try to extract from request body (for POST requests with JSON)
            if (requestBody != null && !requestBody.trim().isEmpty() && requestBody.trim().startsWith("{")) {
                // Simple JSON parsing to extract username fields
                // Look for: "username", "presenterUsername", "bguUsername", "presenter_username"
                String[] usernameFields = {"username", "presenterUsername", "bguUsername", "presenter_username"};
                for (String field : usernameFields) {
                    // Pattern: "field":"value" or "field": "value"
                    String pattern = "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"";
                    java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
                    java.util.regex.Matcher matcher = regex.matcher(requestBody);
                    if (matcher.find()) {
                        String username = matcher.group(1);
                        if (username != null && !username.trim().isEmpty()) {
                            return username.trim();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If extraction fails, just return null - don't break request processing
            logger.debug("Failed to extract username from request: {}", e.getMessage());
        }
        
        return null;
    }
}
