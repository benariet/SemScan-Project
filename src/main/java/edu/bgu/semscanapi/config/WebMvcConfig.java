package edu.bgu.semscanapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration
 * Configures static resource handling
 * Note: Controller mappings have higher priority than static resources by default
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Note: /app/download and /app/download/semscan.apk are handled by AppDownloadController
        // Do not configure static resource handlers for these paths to avoid conflicts
        
        // Default static resource handling for other paths
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600)
                .resourceChain(false);
    }
}

