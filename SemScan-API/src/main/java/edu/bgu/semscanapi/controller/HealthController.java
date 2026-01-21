package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.util.LoggerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller
 * Provides simple health endpoint for monitoring and load balancers
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class HealthController {

    /**
     * Simple health check endpoint
     * GET /api/v1/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Server is running");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("correlationId", LoggerUtil.getCurrentCorrelationId());
        
        return ResponseEntity.ok(response);
    }
}
