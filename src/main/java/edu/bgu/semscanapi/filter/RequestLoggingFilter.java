package edu.bgu.semscanapi.filter;

import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
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
        
        // Generate correlation ID if not present
        String correlationId = httpRequest.getHeader("X-Correlation-ID");
        if (!StringUtils.hasText(correlationId)) {
            correlationId = LoggerUtil.generateAndSetCorrelationId();
        } else {
            LoggerUtil.setCorrelationId(correlationId);
        }
        
        // Add correlation ID to response headers
        httpResponse.setHeader("X-Correlation-ID", correlationId);
        
        // Log request
        logRequest(httpRequest);
        
        // Measure request duration
        long startTime = System.currentTimeMillis();
        
        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logResponse(httpRequest, httpResponse, duration);
            LoggerUtil.clearContext();
        }
    }
    
    private void logRequest(HttpServletRequest request) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();
            String fullUrl = queryString != null ? uri + "?" + queryString : uri;
            
            // Log to file
            logger.info("Incoming Request - Method: {}, URL: {}, Remote Address: {}", 
                       method, fullUrl, getClientIpAddress(request));
            
            // Log to database
            String userId = LoggerUtil.getCurrentUserId();
            Long userIdLong = null;
            try {
                userIdLong = userId != null && !userId.isEmpty() ? Long.parseLong(userId) : null;
            } catch (NumberFormatException e) {
                // User ID is not a valid number, skip
            }
            String payload = String.format("url=%s,remoteAddr=%s,correlationId=%s", 
                fullUrl, getClientIpAddress(request), LoggerUtil.getCurrentCorrelationId());
            if (databaseLoggerService != null) {
                databaseLoggerService.logApiAction(method, uri, "API_REQUEST", userIdLong, payload);
            }
            
            // Log headers (excluding sensitive ones)
            Map<String, String> headers = getFilteredHeaders(request);
            if (!headers.isEmpty()) {
                logger.debug("Request Headers - {}", headers);
            }
            
            // Log request body for POST/PUT requests
            if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                String contentType = request.getContentType();
                if (contentType != null && contentType.contains("application/json")) {
                    logger.debug("Request Content-Type: {}", contentType);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error logging request", e);
        }
    }
    
    private void logResponse(HttpServletRequest request, HttpServletResponse response, long duration) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int statusCode = response.getStatus();
            
            // Log to file
            logger.info("Outgoing Response - Method: {}, URL: {}, Status: {}, Duration: {}ms", 
                       method, uri, statusCode, duration);
            
            // Log to database
            String userId = LoggerUtil.getCurrentUserId();
            Long userIdLong = null;
            try {
                userIdLong = userId != null && !userId.isEmpty() ? Long.parseLong(userId) : null;
            } catch (NumberFormatException e) {
                // User ID is not a valid number, skip
            }
            if (databaseLoggerService != null) {
                databaseLoggerService.logApiResponse(method, uri, statusCode, userIdLong);
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
                        String.format("Slow request: %s %s took %dms", method, uri, duration), userIdLong, slowPayload);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error logging response", e);
        }
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
}
