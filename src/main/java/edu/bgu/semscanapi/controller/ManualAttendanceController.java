package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.ErrorResponse;
import edu.bgu.semscanapi.dto.ManualAttendanceRequest;
import edu.bgu.semscanapi.dto.ManualAttendanceResponse;
import edu.bgu.semscanapi.service.ManualAttendanceService;
import edu.bgu.semscanapi.util.LoggerUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/attendance")
@CrossOrigin(origins = "*")
public class ManualAttendanceController {
    
    private static final Logger logger = LoggerUtil.getLogger(ManualAttendanceController.class);
    
    @Autowired
    private ManualAttendanceService manualAttendanceService;
    
    /**
     * Create a manual attendance request
     * POST /api/v1/attendance/manual-request
     */
    @PostMapping("/manual-request")
    public ResponseEntity<Object> createManualRequest(@Valid @RequestBody ManualAttendanceRequest request) {
        logger.info("Creating manual attendance request - Session: {}, Student: {}", 
                   request.getSessionId(), request.getStudentId());
        
        try {
            ManualAttendanceResponse response = manualAttendanceService.createManualRequest(request);
            logger.info("Manual attendance request created successfully - ID: {}", response.getAttendanceId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid manual attendance request: {}", e.getMessage());
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Bad Request",
                400,
                "/api/v1/attendance/manual-request"
            );
            return ResponseEntity.badRequest().body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Error creating manual attendance request: {}", e.getMessage(), e);
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to create manual attendance request",
                "Internal Server Error",
                500,
                "/api/v1/attendance/manual-request"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get pending manual attendance requests for a session
     * GET /api/v1/attendance/pending-requests?sessionId=session-123
     */
    @GetMapping("/pending-requests")
    public ResponseEntity<Object> getPendingRequests(@RequestParam String sessionId) {
        logger.info("Retrieving pending manual attendance requests for session: {}", sessionId);
        
        try {
            List<ManualAttendanceResponse> requests = manualAttendanceService.getPendingRequests(sessionId);
            logger.info("Retrieved {} pending requests for session: {}", requests.size(), sessionId);
            return ResponseEntity.ok(requests);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid session ID for pending requests: {}", e.getMessage());
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Bad Request",
                400,
                "/api/v1/attendance/pending-requests"
            );
            return ResponseEntity.badRequest().body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Error retrieving pending requests: {}", e.getMessage(), e);
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to retrieve pending requests",
                "Internal Server Error",
                500,
                "/api/v1/attendance/pending-requests"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Approve a manual attendance request
     * POST /api/v1/attendance/{attendanceId}/approve
     */
    @PostMapping("/{attendanceId}/approve")
    public ResponseEntity<Object> approveRequest(@PathVariable String attendanceId,
                                               @RequestHeader("x-api-key") String apiKey) {
        logger.info("Approving manual attendance request: {}", attendanceId);
        
        try {
            // Extract presenter ID from API key (you might want to add this to your authentication service)
            String presenterId = extractPresenterIdFromApiKey(apiKey);
            if (presenterId == null) {
                ErrorResponse errorResponse = new ErrorResponse(
                    "Invalid API key",
                    "Unauthorized",
                    401,
                    "/api/v1/attendance/" + attendanceId + "/approve"
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            ManualAttendanceResponse response = manualAttendanceService.approveRequest(attendanceId, presenterId);
            logger.info("Manual attendance request approved successfully - ID: {}", attendanceId);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid approval request: {}", e.getMessage());
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Bad Request",
                400,
                "/api/v1/attendance/" + attendanceId + "/approve"
            );
            return ResponseEntity.badRequest().body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Error approving manual attendance request: {}", e.getMessage(), e);
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to approve request",
                "Internal Server Error",
                500,
                "/api/v1/attendance/" + attendanceId + "/approve"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Reject a manual attendance request
     * POST /api/v1/attendance/{attendanceId}/reject
     */
    @PostMapping("/{attendanceId}/reject")
    public ResponseEntity<Object> rejectRequest(@PathVariable String attendanceId,
                                              @RequestHeader("x-api-key") String apiKey) {
        logger.info("Rejecting manual attendance request: {}", attendanceId);
        
        try {
            // Extract presenter ID from API key
            String presenterId = extractPresenterIdFromApiKey(apiKey);
            if (presenterId == null) {
                ErrorResponse errorResponse = new ErrorResponse(
                    "Invalid API key",
                    "Unauthorized",
                    401,
                    "/api/v1/attendance/" + attendanceId + "/reject"
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            ManualAttendanceResponse response = manualAttendanceService.rejectRequest(attendanceId, presenterId);
            logger.info("Manual attendance request rejected - ID: {}", attendanceId);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid rejection request: {}", e.getMessage());
            ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "Bad Request",
                400,
                "/api/v1/attendance/" + attendanceId + "/reject"
            );
            return ResponseEntity.badRequest().body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Error rejecting manual attendance request: {}", e.getMessage(), e);
            ErrorResponse errorResponse = new ErrorResponse(
                "Failed to reject request",
                "Internal Server Error",
                500,
                "/api/v1/attendance/" + attendanceId + "/reject"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Check if session has pending requests (for export validation)
     * GET /api/v1/attendance/pending-count?sessionId=session-123
     */
    @GetMapping("/pending-count")
    public ResponseEntity<Object> getPendingRequestCount(@RequestParam String sessionId) {
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
     * This is a simplified implementation - you might want to enhance this
     */
    private String extractPresenterIdFromApiKey(String apiKey) {
        // This is a simplified implementation
        // In a real application, you would validate the API key and return the associated presenter ID
        // For now, we'll use a simple mapping based on the existing API keys
        switch (apiKey) {
            case "presenter-001-api-key-12345":
                return "presenter-001";
            case "presenter-002-api-key-67890":
                return "presenter-002";
            case "presenter-003-api-key-abcde":
                return "presenter-003";
            default:
                return null;
        }
    }
}
