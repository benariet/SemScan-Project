package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Diagnostic controller for troubleshooting
 * Provides endpoints to verify server is running and receiving requests
 */
@RestController
@RequestMapping("/api/v1/diagnostic")
@CrossOrigin(origins = "*")
public class DiagnosticController {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticController.class);

    /**
     * Simple ping endpoint to verify server is reachable
     * GET /api/v1/diagnostic/ping
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        System.out.println("=========================================");
        System.out.println("DIAGNOSTIC PING ENDPOINT CALLED");
        System.out.println("Time: " + LocalDateTime.now());
        System.out.println("=========================================");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Server is running");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("correlationId", LoggerUtil.getCurrentCorrelationId());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Echo endpoint - returns the request body to verify request parsing works
     * POST /api/v1/diagnostic/echo
     */
    @PostMapping("/echo")
    public ResponseEntity<Map<String, Object>> echo(@RequestBody(required = false) Map<String, Object> body) {
        System.out.println("=========================================");
        System.out.println("DIAGNOSTIC ECHO ENDPOINT CALLED");
        System.out.println("Time: " + LocalDateTime.now());
        System.out.println("Body: " + (body != null ? body.toString() : "null"));
        System.out.println("=========================================");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("received", body);
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("correlationId", LoggerUtil.getCurrentCorrelationId());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Test login endpoint structure (without actual authentication)
     * POST /api/v1/diagnostic/test-login
     */
    @PostMapping("/test-login")
    public ResponseEntity<Map<String, Object>> testLogin(@RequestBody(required = false) Map<String, Object> body) {
        System.out.println("=========================================");
        System.out.println("DIAGNOSTIC TEST LOGIN ENDPOINT CALLED");
        System.out.println("Time: " + LocalDateTime.now());
        System.out.println("Body: " + (body != null ? body.toString() : "null"));
        System.out.println("=========================================");
        
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("message", "Test login endpoint reached successfully");
        response.put("receivedBody", body);
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("correlationId", LoggerUtil.getCurrentCorrelationId());
        
        logger.info("Diagnostic test login called - Body: {}", body);
        
        return ResponseEntity.ok(response);
    }
}
