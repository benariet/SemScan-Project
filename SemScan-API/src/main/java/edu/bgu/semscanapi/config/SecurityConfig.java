package edu.bgu.semscanapi.config;

import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
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
                
                // Configure authorization - ALL ENDPOINTS PUBLIC FOR POC
                .authorizeHttpRequests(authz -> authz
                    // All API endpoints are public (no authentication required for POC)
                    .requestMatchers("/api/**").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    
                    // All other requests are also public
                    .anyRequest().permitAll()
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
