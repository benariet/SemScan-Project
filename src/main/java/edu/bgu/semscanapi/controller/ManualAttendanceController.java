package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.ErrorResponse;
import edu.bgu.semscanapi.dto.ManualAttendanceRequest;
import edu.bgu.semscanapi.dto.ManualAttendanceResponse;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.service.AuthenticationService;
import edu.bgu.semscanapi.service.ManualAttendanceService;
import edu.bgu.semscanapi.util.LoggerUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/attendance/manual")
@CrossOrigin(origins = "*")
public class ManualAttendanceController {
    
    private static final Logger logger = LoggerUtil.getLogger(ManualAttendanceController.class);
    
    @Autowired
    private ManualAttendanceService manualAttendanceService;
    
    @Autowired
    private AuthenticationService authenticationService;
    
    /**
     * Get the first available presenter for POC
     * In a real application, this would be determined by the authenticated user
     */
    private String getDefaultPresenterId() {
        // For POC, get the first presenter from the database
        // In production, this would come from the authenticated user context
        List<User> presenters = authenticationService.findPresenters();
        if (!presenters.isEmpty()) {
            Long userId = presenters.get(0).getUserId();
            return userId != null ? userId.toString() : null;
        }
        throw new RuntimeException("No presenters found in the system");
    }
    
    /**
     * Create a manual attendance request
     * POST /api/v1/attendance/manual-request
     */
    @PostMapping
    public ResponseEntity<Object> createManualAttendance(@RequestBody ManualAttendanceRequest request) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Creating manual attendance request: sessionId={}, studentId={}", request.getSessionId(), request.getStudentId());
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/attendance/manual", request.toString());
        
        try {
            ManualAttendanceResponse response = manualAttendanceService.createManualRequest(request);
            logger.info("Manual attendance request created successfully - ID: {}", response.getAttendanceId());
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance/manual", 201, response.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid manual attendance request: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance/manual", 400, "Bad Request: " + e.getMessage());
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Bad Request",
                400,
                "/api/v1/attendance/manual"
            );
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("Failed to create manual attendance request", e);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance/manual", 500, "Internal Server Error");
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while creating the manual attendance request",
                "Internal Server Error",
                500,
                "/api/v1/attendance/manual"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Get pending manual attendance requests for a session
     * GET /api/v1/attendance/pending-requests?sessionId=session-123
     */
    @GetMapping("/pending-requests")
    public ResponseEntity<Object> getPendingRequests(@RequestParam Long sessionId) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Retrieving pending manual attendance requests for session: {}", sessionId);
        LoggerUtil.logApiRequest(logger, "GET", "/api/v1/attendance/manual/pending-requests?sessionId=" + sessionId, null);
        
        try {
            List<ManualAttendanceResponse> responses = manualAttendanceService.getPendingRequests(sessionId);
            logger.info("Retrieved {} pending requests for session: {}", responses.size(), sessionId);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/manual/pending-requests", 200, "List size: " + responses.size());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            logger.error("Failed to retrieve pending manual attendance requests", e);
            LoggerUtil.logApiResponse(logger, "GET", "/api/v1/attendance/manual/pending-requests", 500, "Internal Server Error");
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while retrieving pending requests",
                "Internal Server Error",
                500,
                "/api/v1/attendance/manual/pending-requests"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Approve a manual attendance request
     * POST /api/v1/attendance/{attendanceId}/approve
     */
    @PostMapping("/{attendanceId}/approve")
    public ResponseEntity<Object> approveManualAttendance(@PathVariable Long attendanceId,
                                                         @RequestParam Long approvedBy) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Approving manual attendance request: {} by: {}", attendanceId, approvedBy);
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/attendance/manual/" + attendanceId + "/approve", "approvedBy=" + approvedBy);
        
        try {
            ManualAttendanceResponse response = manualAttendanceService.approveRequest(attendanceId, approvedBy);
            logger.info("Manual attendance request approved - ID: {}", attendanceId);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance/manual/" + attendanceId + "/approve", 200, response.toString());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Manual attendance request not found or invalid state: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance/manual/" + attendanceId + "/approve", 400, "Bad Request: " + e.getMessage());
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Bad Request",
                400,
                "/api/v1/attendance/manual/" + attendanceId + "/approve"
            );
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("Failed to approve manual attendance request", e);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance/manual/" + attendanceId + "/approve", 500, "Internal Server Error");
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while approving the manual attendance request",
                "Internal Server Error",
                500,
                "/api/v1/attendance/manual/" + attendanceId + "/approve"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Reject a manual attendance request
     * POST /api/v1/attendance/{attendanceId}/reject
     */
    @PostMapping("/{attendanceId}/reject")
    public ResponseEntity<Object> rejectManualAttendance(@PathVariable Long attendanceId,
                                                        @RequestParam Long approvedBy) {
        String correlationId = LoggerUtil.generateAndSetCorrelationId();
        logger.info("Rejecting manual attendance request: {} by: {}", attendanceId, approvedBy);
        LoggerUtil.logApiRequest(logger, "POST", "/api/v1/attendance/manual/" + attendanceId + "/reject", "approvedBy=" + approvedBy);
        
        try {
            ManualAttendanceResponse response = manualAttendanceService.rejectRequest(attendanceId, approvedBy);
            logger.info("Manual attendance request rejected - ID: {}", attendanceId);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance/manual/" + attendanceId + "/reject", 200, response.toString());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Manual attendance request not found or invalid state: {}", e.getMessage());
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance/manual/" + attendanceId + "/reject", 400, "Bad Request: " + e.getMessage());
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Bad Request",
                400,
                "/api/v1/attendance/manual/" + attendanceId + "/reject"
            );
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("Failed to reject manual attendance request", e);
            LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance/manual/" + attendanceId + "/reject", 500, "Internal Server Error");
            ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred while rejecting the manual attendance request",
                "Internal Server Error",
                500,
                "/api/v1/attendance/manual/" + attendanceId + "/reject"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            LoggerUtil.clearContext();
        }
    }
    
    /**
     * Check if session has pending requests (for export validation)
     * GET /api/v1/attendance/pending-count?sessionId=session-123
     */
    @GetMapping("/pending-count")
    public ResponseEntity<Object> getPendingRequestCount(@RequestParam Long sessionId) {
        logger.info("Checking pending request count for session: {}", sessionId);
        
        try {
            long count = manualAttendanceService.getPendingRequestCount(sessionId);
            return ResponseEntity.ok(Map.of("sessionId", sessionId, "pendingCount", count));
            
        } catch (Exception e) {
            logger.error("Error checking pending request count: {}", e.getMessage(), e);
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to check pending request count",
                "Internal Server Error",
                500,
                "/api/v1/attendance/pending-count"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Extract presenter ID from API key
     * No API key authentication for POC - return null
     */
    private String extractPresenterIdFromApiKey(String apiKey) {
        // No API key authentication for POC
        return null;
    }
}
