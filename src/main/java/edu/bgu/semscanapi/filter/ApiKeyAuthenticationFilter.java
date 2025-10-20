package edu.bgu.semscanapi.filter;

import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.service.AuthenticationService;
import edu.bgu.semscanapi.util.LoggerUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Custom authentication filter for API key validation
 * Provides comprehensive logging for authentication events
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerUtil.getLogger(ApiKeyAuthenticationFilter.class);
    
    private final AuthenticationService authenticationService;
    
    public ApiKeyAuthenticationFilter(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        String apiKey = request.getHeader("x-api-key");
        
        logger.debug("Processing authentication for request: {} {}", request.getMethod(), request.getRequestURI());
        
        try {
            // Skip authentication for public endpoints
            if (isPublicEndpoint(request)) {
                logger.debug("Skipping authentication for public endpoint: {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }
            
            // Check if API key is provided
            if (apiKey == null || apiKey.trim().isEmpty()) {
                logger.warn("No API key provided for protected endpoint: {}", request.getRequestURI());
                LoggerUtil.logAuthentication(logger, "NO_API_KEY", null, null);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"API key required\"}");
                return;
            }
            
            // Validate API key
            logger.debug("Validating API key for request: {}", request.getRequestURI());
            var presenter = authenticationService.validateApiKey(apiKey);
            
            if (presenter.isEmpty()) {
                logger.warn("Invalid API key provided for request: {}", request.getRequestURI());
                LoggerUtil.logAuthentication(logger, "INVALID_API_KEY", null, apiKey);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid API key\"}");
                return;
            }
            
            User user = presenter.get();
            logger.info("API key validation successful for presenter: {} - {}", 
                user.getUserId(), user.getEmail());
            LoggerUtil.setUserId(user.getUserId());
            LoggerUtil.logAuthentication(logger, "AUTHENTICATION_SUCCESS", user.getUserId(), apiKey);
            
            // Set authentication in security context
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getUserId(), 
                null, 
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PRESENTER"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Continue with the filter chain
            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            logger.error("Error during API key authentication", e);
            LoggerUtil.logAuthentication(logger, "AUTHENTICATION_ERROR", null, apiKey);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Internal Server Error\",\"message\":\"Authentication failed\"}");
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Check if the request is for a public endpoint
     */
    private boolean isPublicEndpoint(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        
        // Public endpoints that don't require authentication
        return requestURI.startsWith("/api/v1/attendance") ||
               requestURI.startsWith("/api/v1/info") ||
               requestURI.startsWith("/api/v1/qr") || // QR code endpoints
               requestURI.startsWith("/api/v1/student") || // Student endpoints
               requestURI.startsWith("/api/v1/debug") || // Debug endpoints
               requestURI.startsWith("/api/v1/logs") || // App logs endpoints
               requestURI.equals("/actuator/health") ||
               requestURI.equals("/actuator/info") ||
               requestURI.startsWith("/api/v1/logging-example"); // For testing
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Skip filtering for static resources and error pages
        String requestURI = request.getRequestURI();
        return requestURI.startsWith("/static/") ||
               requestURI.startsWith("/css/") ||
               requestURI.startsWith("/js/") ||
               requestURI.startsWith("/images/") ||
               requestURI.equals("/favicon.ico") ||
               requestURI.equals("/error");
    }
}
