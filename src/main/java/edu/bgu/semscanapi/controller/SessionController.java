package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.ErrorResponse;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.SessionService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REST Controller for Session operations
 * Provides comprehensive logging for all API endpoints
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {
    
    private static final Logger logger = LoggerUtil.getLogger(SessionController.class);
    
    @Autowired
    private SessionService sessionService;
    
    @Autowired
    private DatabaseLoggerService databaseLoggerService;
    
    /**
     * Create a new session
     */
    @PostMapping
    public ResponseEntity<Object> createSession(@RequestBody Session session) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Creating session for seminar: {}", session.getSeminarId());
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/sessions", session.toString());
        
        try {
            Session createdSession = sessionService.createSession(session);
            logger.info("Session created successfully - ID: {}, Seminar: {}", 
                createdSession.getSessionId(), createdSession.getSeminarId());
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/sessions", 201, createdSession.toString());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(createdSession);
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid session data: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/sessions", 400, "Bad Request: " + e.getMessage());
            
            String userId = LoggerUtil.getCurrentUserId();
            Long userIdLong = null;
            try {
                userIdLong = userId != null && !userId.isEmpty() ? Long.parseLong(userId) : null;
            } catch (NumberFormatException ignored) {}
            String payload = String.format("correlationId=%s", LoggerUtil.getCurrentCorrelationId());
            databaseLoggerService.logError("SESSION_VALIDATION_ERROR", e.getMessage(), e, userIdLong, payload);
            
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Bad Request",
                400,
                "/api/v1/sessions"
            );
            return ResponseEntity.badRequest().body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Failed to create session", e);
            LoggerUtil.logError(logger, "Failed to create session", e);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/sessions", 500, "Internal Server Error");
            
            String userId = LoggerUtil.getCurrentUserId();
            Long userIdLong = null;
            try {
                userIdLong = userId != null && !userId.isEmpty() ? Long.parseLong(userId) : null;
            } catch (NumberFormatException ignored) {}
            String payload = String.format("correlationId=%s", LoggerUtil.getCurrentCorrelationId());
            databaseLoggerService.logError("SESSION_CREATION_ERROR", "Failed to create session", e, userIdLong, payload);
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while creating the session",
                "Internal Server Error",
                500,
                "/api/v1/sessions"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get session by ID
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<Object> getSessionById(@PathVariable Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving session by ID: {}", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/sessions/" + sessionId, null);
        
        try {
            Optional<Session> session = sessionService.getSessionById(sessionId);
            
            if (session.isPresent()) {
                logger.info("Session found - ID: {}, Seminar: {}, Status: {}", 
                    sessionId, session.get().getSeminarId(), session.get().getStatus());
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/" + sessionId, 200, session.get().toString());
                return ResponseEntity.ok(session.get());
            } else {
                logger.warn("Session not found: {}", sessionId);
                LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/" + sessionId, 404, "Not Found");
                
                ErrorResponse errorResponse = new ErrorResponse(
                    "Session not found with ID: " + sessionId,
                    "Not Found",
                    404,
                    "/api/v1/sessions/" + sessionId
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
        } catch (Exception e) {
            logger.error("Failed to retrieve session: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to retrieve session", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/" + sessionId, 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving the session",
                "Internal Server Error",
                500,
                "/api/v1/sessions/" + sessionId
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get sessions by seminar
     */
    @GetMapping("/seminar/{seminarId}")
    public ResponseEntity<Object> getSessionsBySeminar(@PathVariable Long seminarId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving sessions for seminar: {}", seminarId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/sessions/seminar/" + seminarId, null);
        
        try {
            List<Session> sessions = sessionService.getSessionsBySeminar(seminarId);
            logger.info("Retrieved {} sessions for seminar: {}", sessions.size(), seminarId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/seminar/" + seminarId, 200, 
                "List of " + sessions.size() + " sessions");
            
            return ResponseEntity.ok(sessions);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve sessions for seminar: {}", seminarId, e);
            LoggerUtil.logError(logger, "Failed to retrieve sessions for seminar", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/seminar/" + seminarId, 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving sessions for seminar",
                "Internal Server Error",
                500,
                "/api/v1/sessions/seminar/" + seminarId
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get open sessions
     */
    @GetMapping("/open")
    public ResponseEntity<Object> getOpenSessions() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving all open sessions");
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/sessions/open", null);
        
        try {
            List<Session> sessions = sessionService.getOpenSessions();
            logger.info("Retrieved {} open sessions", sessions.size());
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/open", 200, 
                "List of " + sessions.size() + " open sessions");
            
            return ResponseEntity.ok(sessions);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve open sessions", e);
            LoggerUtil.logError(logger, "Failed to retrieve open sessions", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/open", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving open sessions",
                "Internal Server Error",
                500,
                "/api/v1/sessions/open"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get closed sessions
     */
    @GetMapping("/closed")
    public ResponseEntity<Object> getClosedSessions() {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving all closed sessions");
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/sessions/closed", null);
        
        try {
            List<Session> sessions = sessionService.getClosedSessions();
            logger.info("Retrieved {} closed sessions", sessions.size());
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/closed", 200, 
                "List of " + sessions.size() + " closed sessions");
            
            return ResponseEntity.ok(sessions);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve closed sessions", e);
            LoggerUtil.logError(logger, "Failed to retrieve closed sessions", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/closed", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving closed sessions",
                "Internal Server Error",
                500,
                "/api/v1/sessions/closed"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Update session status
     */
    @PutMapping("/{sessionId}/status")
    public ResponseEntity<Object> updateSessionStatus(@PathVariable Long sessionId, 
                                                     @RequestParam Session.SessionStatus status) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Updating session status - ID: {}, Status: {}", sessionId, status);
        LoggerUtil.logApiRequest(logger, "PUT", "/api/v1/sessions/" + sessionId + "/status", "status=" + status);
        
        try {
            Session updatedSession = sessionService.updateSessionStatus(sessionId, status);
            logger.info("Session status updated successfully - ID: {}, Status: {}", 
                sessionId, updatedSession.getStatus());
            LoggerUtil.logApiResponse(logger, "PUT", "/api/v1/sessions/" + sessionId + "/status", 200, updatedSession.toString());
            
            return ResponseEntity.ok(updatedSession);
            
        } catch (IllegalArgumentException e) {
            logger.error("Session not found for status update: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "PUT", "/api/v1/sessions/" + sessionId + "/status", 404, "Not Found");
            
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Not Found",
                404,
                "/api/v1/sessions/" + sessionId + "/status"
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Failed to update session status: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to update session status", e);
            LoggerUtil.logApiResponse(logger, "PUT", "/api/v1/sessions/" + sessionId + "/status", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while updating session status",
                "Internal Server Error",
                500,
                "/api/v1/sessions/" + sessionId + "/status"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Close session (PATCH method for mobile app compatibility)
     */
    @PatchMapping("/{sessionId}/close")
    public ResponseEntity<Object> closeSessionPatch(@PathVariable Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Closing session (PATCH): {}", sessionId);
        LoggerUtil.logApiRequest(logger, "PATCH", "/api/v1/sessions/" + sessionId + "/close", null);
        
        try {
            Session closedSession = sessionService.closeSession(sessionId);
            logger.info("Session closed successfully (PATCH) - ID: {}", sessionId);
            LoggerUtil.logApiResponse(logger, "PATCH", "/api/v1/sessions/" + sessionId + "/close", 200, closedSession.toString());
            
            return ResponseEntity.ok(closedSession);
            
        } catch (IllegalArgumentException e) {
            logger.error("Session not found for closing (PATCH): {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "PATCH", "/api/v1/sessions/" + sessionId + "/close", 404, "Not Found");
            
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Not Found",
                404,
                "/api/v1/sessions/" + sessionId + "/close"
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Failed to close session (PATCH): {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to close session (PATCH)", e);
            LoggerUtil.logApiResponse(logger, "PATCH", "/api/v1/sessions/" + sessionId + "/close", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while closing session",
                "Internal Server Error",
                500,
                "/api/v1/sessions/" + sessionId + "/close"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Close session (PUT method)
     */
    @PutMapping("/{sessionId}/close")
    public ResponseEntity<Object> closeSession(@PathVariable Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Closing session: {}", sessionId);
        LoggerUtil.logApiRequest(logger, "PUT", "/api/v1/sessions/" + sessionId + "/close", null);
        
        try {
            Session closedSession = sessionService.closeSession(sessionId);
            logger.info("Session closed successfully - ID: {}", sessionId);
            LoggerUtil.logApiResponse(logger, "PUT", "/api/v1/sessions/" + sessionId + "/close", 200, closedSession.toString());
            
            return ResponseEntity.ok(closedSession);
            
        } catch (IllegalArgumentException e) {
            logger.error("Session not found for closing: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "PUT", "/api/v1/sessions/" + sessionId + "/close", 404, "Not Found");
            
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Not Found",
                404,
                "/api/v1/sessions/" + sessionId + "/close"
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Failed to close session: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to close session", e);
            LoggerUtil.logApiResponse(logger, "PUT", "/api/v1/sessions/" + sessionId + "/close", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while closing session",
                "Internal Server Error",
                500,
                "/api/v1/sessions/" + sessionId + "/close"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Open session
     */
    @PutMapping("/{sessionId}/open")
    public ResponseEntity<Object> openSession(@PathVariable Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Opening session: {}", sessionId);
        LoggerUtil.logApiRequest(logger, "PUT", "/api/v1/sessions/" + sessionId + "/open", null);
        
        try {
            Session openedSession = sessionService.openSession(sessionId);
            logger.info("Session opened successfully - ID: {}", sessionId);
            LoggerUtil.logApiResponse(logger, "PUT", "/api/v1/sessions/" + sessionId + "/open", 200, openedSession.toString());
            
            return ResponseEntity.ok(openedSession);
            
        } catch (IllegalArgumentException e) {
            logger.error("Session not found for opening: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "PUT", "/api/v1/sessions/" + sessionId + "/open", 404, "Not Found");
            
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Not Found",
                404,
                "/api/v1/sessions/" + sessionId + "/open"
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Failed to open session: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to open session", e);
            LoggerUtil.logApiResponse(logger, "PUT", "/api/v1/sessions/" + sessionId + "/open", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while opening session",
                "Internal Server Error",
                500,
                "/api/v1/sessions/" + sessionId + "/open"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Delete session
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Object> deleteSession(@PathVariable Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Deleting session: {}", sessionId);
        LoggerUtil.logApiRequest(logger, "DELETE", "/api/v1/sessions/" + sessionId, null);
        
        try {
            sessionService.deleteSession(sessionId);
            logger.info("Session deleted successfully: {}", sessionId);
            LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/sessions/" + sessionId, 204, "No Content");
            
            return ResponseEntity.noContent().build();
            
        } catch (IllegalArgumentException e) {
            logger.error("Session not found for deletion: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/sessions/" + sessionId, 404, "Not Found");
            
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Not Found",
                404,
                "/api/v1/sessions/" + sessionId
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Failed to delete session: {}", sessionId, e);
            LoggerUtil.logError(logger, "Failed to delete session", e);
            LoggerUtil.logApiResponse(logger, "DELETE", "/api/v1/sessions/" + sessionId, 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while deleting session",
                "Internal Server Error",
                500,
                "/api/v1/sessions/" + sessionId
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get sessions within date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<Object> getSessionsBetweenDates(
            @RequestParam String startDate, 
            @RequestParam String endDate) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving sessions between dates: {} and {}", startDate, endDate);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/sessions/date-range", 
            "startDate=" + startDate + "&endDate=" + endDate);
        
        try {
            LocalDateTime start = LocalDateTime.parse(startDate);
            LocalDateTime end = LocalDateTime.parse(endDate);
            
            List<Session> sessions = sessionService.getSessionsBetweenDates(start, end);
            logger.info("Retrieved {} sessions between dates", sessions.size());
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/date-range", 200, 
                "List of " + sessions.size() + " sessions");
            
            return ResponseEntity.ok(sessions);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve sessions between dates", e);
            LoggerUtil.logError(logger, "Failed to retrieve sessions between dates", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/date-range", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving sessions between dates",
                "Internal Server Error",
                500,
                "/api/v1/sessions/date-range"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get active sessions for a seminar
     */
    @GetMapping("/seminar/{seminarId}/active")
    public ResponseEntity<Object> getActiveSessionsBySeminar(@PathVariable Long seminarId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving active sessions for seminar: {}", seminarId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/sessions/seminar/" + seminarId + "/active", null);
        
        try {
            List<Session> sessions = sessionService.getActiveSessionsBySeminar(seminarId);
            logger.info("Retrieved {} active sessions for seminar: {}", sessions.size(), seminarId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/seminar/" + seminarId + "/active", 200, 
                "List of " + sessions.size() + " active sessions");
            
            return ResponseEntity.ok(sessions);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve active sessions for seminar: {}", seminarId, e);
            LoggerUtil.logError(logger, "Failed to retrieve active sessions for seminar", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/sessions/seminar/" + seminarId + "/active", 500, "Internal Server Error");
            
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving active sessions for seminar",
                "Internal Server Error",
                500,
                "/api/v1/sessions/seminar/" + seminarId + "/active"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
}
