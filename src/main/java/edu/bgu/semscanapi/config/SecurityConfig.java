package edu.bgu.semscanapi.config;

import edu.bgu.semscanapi.filter.ApiKeyAuthenticationFilter;
import edu.bgu.semscanapi.service.AuthenticationService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Security configuration for SemScan API
 * Provides comprehensive logging for security events
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private static final Logger logger = LoggerUtil.getLogger(SecurityConfig.class);
    
    @Autowired
    private AuthenticationService authenticationService;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        logger.info("Configuring Spring Security filter chain");
        
        try {
            http
                // Disable CSRF for API
                .csrf(AbstractHttpConfigurer::disable)
                
                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // Configure session management
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Configure authorization
                .authorizeHttpRequests(authz -> authz
                    // Public endpoints
                    .requestMatchers("/api/v1/attendance/**").permitAll()
                    .requestMatchers("/api/v1/info/**").permitAll()
                    .requestMatchers("/api/v1/qr/**").permitAll() // QR code endpoints
                    .requestMatchers("/api/v1/logs/**").permitAll() // App logs endpoints
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/actuator/info").permitAll()
                    
                    // Protected endpoints (require API key)
                    .requestMatchers("/api/v1/seminars/**").authenticated()
                    .requestMatchers("/api/v1/sessions/**").authenticated()
                    .requestMatchers("/actuator/**").authenticated()
                    
                    // All other requests require authentication
                    .anyRequest().authenticated()
                )
                
                // Add custom API key authentication filter
                .addFilterBefore(new ApiKeyAuthenticationFilter(authenticationService), 
                    UsernamePasswordAuthenticationFilter.class)
                
                // Configure exception handling
                .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, authException) -> {
                        logger.warn("Authentication failed for request: {} - {}", 
                            request.getMethod(), request.getRequestURI());
                        LoggerUtil.logAuthentication(logger, "AUTHENTICATION_FAILED", null, 
                            request.getHeader("X-API-Key"));
                        response.setStatus(401);
                        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Valid API key required\"}");
                    })
                    .accessDeniedHandler((request, response, accessDeniedException) -> {
                        logger.warn("Access denied for request: {} - {}", 
                            request.getMethod(), request.getRequestURI());
                        LoggerUtil.logAuthentication(logger, "ACCESS_DENIED", null, 
                            request.getHeader("X-API-Key"));
                        response.setStatus(403);
                        response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Insufficient permissions\"}");
                    })
                );
            
            logger.info("Spring Security filter chain configured successfully");
            return http.build();
            
        } catch (Exception e) {
            logger.error("Failed to configure Spring Security filter chain", e);
            throw e;
        }
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        logger.info("Configuring CORS policy");
        
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        logger.info("CORS policy configured successfully");
        return source;
    }
}
