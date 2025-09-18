package edu.bgu.semscanapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot Application class for SemScan API
 * 
 * This application provides REST API endpoints for the SemScan QR Attendance System.
 */
@SpringBootApplication
public class SemScanApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SemScanApiApplication.class, args);
        System.out.println("🚀 SemScan API started successfully!");
        System.out.println("📱 API Base URL: http://localhost:8080/");
    }

}
