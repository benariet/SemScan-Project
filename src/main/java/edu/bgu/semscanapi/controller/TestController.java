package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Test controller for development and testing purposes
 * Provides test endpoints and system information
 */
@RestController
@RequestMapping("/api/v1/test")
@CrossOrigin(origins = "*")
public class TestController {

    private static final Logger logger = LoggerUtil.getLogger(TestController.class);

    /**
     * Get system information for development
     */
    @GetMapping("/info")
    public ResponseEntity<Object> getSystemInfo() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Providing system info - Correlation ID: {}", correlationId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "SemScan API is running");
        response.put("note", "No authentication required for POC");
        response.put("status", "ready");
        response.put("correlationId", correlationId);

        logger.info("System info provided successfully - Correlation ID: {}", correlationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all available test endpoints
     */
    @GetMapping("/endpoints")
    public ResponseEntity<Object> getTestEndpoints() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Providing test endpoints - Correlation ID: {}", correlationId);

        Map<String, Object> response = new HashMap<>();
        response.put("testEndpoints", new String[]{
            "/api/v1/test/info",
            "/api/v1/test/endpoints",
            "/api/v1/info/endpoints"
        });
        response.put("swaggerUI", "http://localhost:8080/swagger-ui.html");
        response.put("correlationId", correlationId);

        logger.info("Test endpoints provided successfully - Correlation ID: {}", correlationId);
        return ResponseEntity.ok(response);
    }
}
