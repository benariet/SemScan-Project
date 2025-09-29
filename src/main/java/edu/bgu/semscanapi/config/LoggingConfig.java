package edu.bgu.semscanapi.config;

import edu.bgu.semscanapi.filter.RequestLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * Configuration class for logging setup
 * Configures request logging and other logging-related beans
 */
@Configuration
public class LoggingConfig {
    
    /**
     * Configure CommonsRequestLoggingFilter for detailed request logging
     */
    @Bean
    public CommonsRequestLoggingFilter commonsRequestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(1000);
        filter.setIncludeHeaders(true);
        filter.setAfterMessagePrefix("REQUEST DATA: ");
        return filter;
    }
    
    /**
     * Register the custom request logging filter
     */
    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> customRequestLoggingFilterRegistration() {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestLoggingFilter());
        registration.addUrlPatterns("/api/*");
        registration.setName("customRequestLoggingFilter");
        registration.setOrder(1);
        return registration;
    }
}
