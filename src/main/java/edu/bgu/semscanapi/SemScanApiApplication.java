package edu.bgu.semscanapi;

import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot Application class for SemScan API
 * 
 * This application provides REST API endpoints for the SemScan QR Attendance System.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class SemScanApiApplication {

    private static final Logger logger = LoggerUtil.getLogger(SemScanApiApplication.class);

    public static void main(String[] args) {
        logger.info("Starting SemScan API Application...");
        
        try {
            ConfigurableApplicationContext context = SpringApplication.run(SemScanApiApplication.class, args);
            
            // Log successful startup
            logger.info("🚀 SemScan API started successfully!");
            logger.info("📱 API Base URL: http://localhost:8080/");
            logger.info("📊 Actuator endpoints available at: http://localhost:8080/actuator");
            logger.info("🔐 Security password generated - check logs for details");
            
            // Log application info
            String appName = context.getEnvironment().getProperty("app.name", "SemScan Attendance System");
            String appVersion = context.getEnvironment().getProperty("app.version", "1.0.0");
            logger.info("Application: {} v{}", appName, appVersion);
            
            // Log database connection info
            String dbUrl = context.getEnvironment().getProperty("spring.datasource.url");
            if (dbUrl != null) {
                logger.info("Database connected: {}", dbUrl.replaceAll("password=[^&]*", "password=***"));
            }
            
        } catch (Exception e) {
            logger.error("Failed to start SemScan API Application", e);
            System.exit(1);
        }
    }
}
