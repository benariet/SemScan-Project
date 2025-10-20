package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug controller for database inspection
 * Provides endpoints to check database state
 */
@RestController
@RequestMapping("/api/v1/debug")
@CrossOrigin(origins = "*")
public class DebugController {

    private static final Logger logger = LoggerUtil.getLogger(DebugController.class);

    @Autowired
    private SessionRepository sessionRepository;

    /**
     * Get all open sessions from database
     */
    @GetMapping("/sessions/open")
    public ResponseEntity<Object> getOpenSessionsFromDB() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Debug: Retrieving all open sessions from database - Correlation ID: {}", correlationId);

        try {
            List<Session> openSessions = sessionRepository.findOpenSessions();
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalOpenSessions", openSessions.size());
            response.put("sessions", openSessions);
            response.put("correlationId", correlationId);
            
            logger.info("Debug: Found {} open sessions in database - Correlation ID: {}", openSessions.size(), correlationId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Debug: Error retrieving open sessions - Correlation ID: {}", correlationId, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve open sessions");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("correlationId", correlationId);
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get session count by status
     */
    @GetMapping("/sessions/count")
    public ResponseEntity<Object> getSessionCounts() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Debug: Getting session counts by status - Correlation ID: {}", correlationId);

        try {
            List<Session> openSessions = sessionRepository.findOpenSessions();
            List<Session> closedSessions = sessionRepository.findClosedSessions();
            
            Map<String, Object> response = new HashMap<>();
            response.put("openSessions", openSessions.size());
            response.put("closedSessions", closedSessions.size());
            response.put("totalSessions", openSessions.size() + closedSessions.size());
            response.put("correlationId", correlationId);
            
            logger.info("Debug: Session counts - Open: {}, Closed: {} - Correlation ID: {}", 
                openSessions.size(), closedSessions.size(), correlationId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Debug: Error getting session counts - Correlation ID: {}", correlationId, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get session counts");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("correlationId", correlationId);
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
