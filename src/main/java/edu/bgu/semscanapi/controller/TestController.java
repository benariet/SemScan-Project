package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Test controller for development and testing purposes
 * Provides test endpoints and API key information
 */
@RestController
@RequestMapping("/api/v1/test")
@CrossOrigin(origins = "*")
public class TestController {

    private static final Logger logger = LoggerUtil.getLogger(TestController.class);

    /**
     * Get test API key for development
     */
    @GetMapping("/api-key")
    public ResponseEntity<Object> getTestApiKey() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Providing test API key - Correlation ID: {}", correlationId);

        Map<String, Object> response = new HashMap<>();
        response.put("testApiKey", "presenter-001-api-key-12345");
        response.put("message", "Use this API key for testing in Swagger UI");
        response.put("instructions", "Click 'Authorize' button in Swagger UI and enter this key");
        response.put("correlationId", correlationId);

        logger.info("Test API key provided successfully - Correlation ID: {}", correlationId);
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
            "/api/v1/test/api-key",
            "/api/v1/test/endpoints",
            "/api/v1/info/endpoints"
        });
        response.put("swaggerUI", "http://localhost:8080/swagger-ui.html");
        response.put("correlationId", correlationId);

        logger.info("Test endpoints provided successfully - Correlation ID: {}", correlationId);
        return ResponseEntity.ok(response);
    }
}
