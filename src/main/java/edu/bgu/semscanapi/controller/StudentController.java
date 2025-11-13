package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.service.SessionService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Student-specific controller for student-facing endpoints
 * Provides filtered data appropriate for students
 */
@RestController
@RequestMapping("/api/v1/student")
@CrossOrigin(origins = "*")
public class StudentController {

    private static final Logger logger = LoggerUtil.getLogger(StudentController.class);

    @Autowired
    private SessionService sessionService;

    /**
     * Get open sessions for manual attendance (student-specific)
     * Returns only sessions that are currently open and relevant for attendance
     * This endpoint is public (no API key required) for student access
     */
    @GetMapping("/sessions/open")
    public ResponseEntity<Object> getOpenSessionsForStudent() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving open sessions for student manual attendance - Correlation ID: {}", correlationId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/student/sessions/open", null);

        try {
            // Get all open sessions (already filtered and ordered by repository query)
            List<Session> allOpenSessions = sessionService.getOpenSessions();
            
            // Filter to only show sessions that are currently active and relevant
            // Note: Status is already filtered by repository, but double-check for safety
            List<Session> relevantSessions = allOpenSessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.OPEN)
                .filter(session -> session.getStartTime() != null)
                // No limit - return all open sessions so participants can see newly opened sessions
                .toList();
            
            logger.info("Retrieved {} relevant open sessions for student - Correlation ID: {}", 
                relevantSessions.size(), correlationId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/student/sessions/open", 200, 
                "List of " + relevantSessions.size() + " relevant sessions");
            
            // Return with metadata for better UI handling
            Map<String, Object> response = new HashMap<>();
            response.put("sessions", relevantSessions);
            response.put("totalCount", relevantSessions.size());
            response.put("message", "Open sessions available for manual attendance");
            response.put("correlationId", correlationId);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to retrieve open sessions for student - Correlation ID: {}", correlationId, e);
            LoggerUtil.logError(logger, "Failed to retrieve open sessions for student", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/student/sessions/open", 500, "Internal Server Error");
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve open sessions");
            errorResponse.put("message", "An error occurred while retrieving sessions");
            errorResponse.put("correlationId", correlationId);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Get sessions by student ID (if student enrollment is tracked)
     */
    @GetMapping("/sessions/student/{studentUsername}")
    public ResponseEntity<Object> getSessionsForStudent(@PathVariable String studentUsername) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving sessions for student: {} - Correlation ID: {}", studentUsername, correlationId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/student/sessions/student/" + studentUsername, null);

        try {
            // For now, return open sessions (in the future, this could filter by student enrollment)
            List<Session> sessions = sessionService.getOpenSessions();
            
            // Return all open sessions (ordered by most recent first)
            // No limit to ensure participants can see all newly opened sessions
            List<Session> limitedSessions = sessions.stream()
                .filter(session -> session.getStatus() == Session.SessionStatus.OPEN)
                .toList();
            
            logger.info("Retrieved {} sessions for student: {} - Correlation ID: {}", 
                limitedSessions.size(), studentUsername, correlationId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/student/sessions/student/" + studentUsername, 200, 
                "List of " + limitedSessions.size() + " sessions");
            
            Map<String, Object> response = new HashMap<>();
            response.put("sessions", limitedSessions);
            response.put("studentUsername", studentUsername);
            response.put("totalCount", limitedSessions.size());
            response.put("correlationId", correlationId);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to retrieve sessions for student: {} - Correlation ID: {}", studentUsername, correlationId, e);
            LoggerUtil.logError(logger, "Failed to retrieve sessions for student", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/student/sessions/student/" + studentUsername, 500, "Internal Server Error");
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve sessions for student");
            errorResponse.put("message", "An error occurred while retrieving sessions");
            errorResponse.put("studentUsername", studentUsername);
            errorResponse.put("correlationId", correlationId);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
}
