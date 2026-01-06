package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.WaitingListRequest;
import edu.bgu.semscanapi.dto.WaitingListResponse;
import edu.bgu.semscanapi.entity.WaitingListEntry;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.WaitingListService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for managing waiting list operations
 */
@RestController
@RequestMapping("/api/v1/slots/{slotId}/waiting-list")
@CrossOrigin(origins = "*")
public class WaitingListController {

    private static final Logger logger = LoggerUtil.getLogger(WaitingListController.class);

    private final WaitingListService waitingListService;
    private final DatabaseLoggerService databaseLoggerService;

    public WaitingListController(WaitingListService waitingListService, DatabaseLoggerService databaseLoggerService) {
        this.waitingListService = waitingListService;
        this.databaseLoggerService = databaseLoggerService;
    }

    /**
     * Get waiting list for a slot
     * GET /api/v1/slots/{slotId}/waiting-list
     */
    @GetMapping
    public ResponseEntity<WaitingListResponse> getWaitingList(@PathVariable Long slotId) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/slots/%d/waiting-list", slotId);
        LoggerUtil.logApiRequest(logger, "GET", endpoint, null);

        try {
            List<WaitingListEntry> entries = waitingListService.getWaitingList(slotId);
            WaitingListResponse response = new WaitingListResponse();
            response.setSlotId(slotId);
            response.setEntries(entries.stream()
                    .map(entry -> {
                        WaitingListResponse.WaitingListEntryDto dto = new WaitingListResponse.WaitingListEntryDto();
                        dto.setPresenterUsername(entry.getPresenterUsername());
                        dto.setDegree(entry.getDegree().toString());
                        dto.setTopic(entry.getTopic());
                        dto.setPosition(entry.getPosition());
                        dto.setAddedAt(entry.getAddedAt().toString());
                        return dto;
                    })
                    .collect(Collectors.toList()));
            
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.OK.value(), "Waiting list retrieved");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LoggerUtil.logError(logger, "Failed to get waiting list", e);
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            throw e;
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Add user to waiting list
     * POST /api/v1/slots/{slotId}/waiting-list
     */
    @PostMapping
    public ResponseEntity<WaitingListResponse> addToWaitingList(
            @PathVariable Long slotId,
            @Valid @RequestBody WaitingListRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        
        String endpoint = String.format("/api/v1/slots/%d/waiting-list", slotId);
        
        // Extract and validate username early for logging
        String presenterUsername = request != null ? request.getPresenterUsername() : null;
        if (presenterUsername != null && !presenterUsername.trim().isEmpty()) {
            LoggerUtil.setBguUsername(presenterUsername.trim());
        }
        
        // Log request details
        String requestDetails = String.format("slotId=%d, presenterUsername=%s, topic=%s, supervisorName=%s, supervisorEmail=%s",
                slotId, presenterUsername, request != null ? request.getTopic() : "null",
                request != null ? request.getSupervisorName() : "null",
                request != null ? request.getSupervisorEmail() : "null");
        LoggerUtil.logApiRequest(logger, "POST", endpoint, requestDetails);

        // Validate request body
        if (request == null) {
            String errorMsg = "Request body is null";
            logger.error("{} - endpoint={}", errorMsg, endpoint);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, null,
                    String.format("endpoint=%s,slotId=%d", endpoint, slotId));
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.BAD_REQUEST.value(), errorMsg);
            WaitingListResponse errorResponse = new WaitingListResponse();
            errorResponse.setOk(false);
            errorResponse.setMessage(errorMsg);
            errorResponse.setCode("INVALID_REQUEST");
            errorResponse.setSlotId(slotId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        // Validate required fields
        if (presenterUsername == null || presenterUsername.trim().isEmpty()) {
            String errorMsg = "Presenter username is required";
            logger.error("{} - endpoint={}, slotId={}", errorMsg, endpoint, slotId);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, null,
                    String.format("endpoint=%s,slotId=%d", endpoint, slotId));
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.BAD_REQUEST.value(), errorMsg);
            WaitingListResponse errorResponse = new WaitingListResponse();
            errorResponse.setOk(false);
            errorResponse.setMessage(errorMsg);
            errorResponse.setCode("MISSING_USERNAME");
            errorResponse.setSlotId(slotId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        try {
            WaitingListEntry entry = waitingListService.addToWaitingList(
                    slotId,
                    presenterUsername.trim(),
                    request.getTopic(),
                    request.getSupervisorName(),
                    request.getSupervisorEmail());

            WaitingListResponse response = new WaitingListResponse();
            response.setOk(true);
            response.setMessage("Added to waiting list");
            response.setCode("ADDED_TO_WAITING_LIST");
            response.setPosition(entry.getPosition());
            response.setSlotId(slotId);
            WaitingListResponse.WaitingListEntryDto dto = new WaitingListResponse.WaitingListEntryDto();
            dto.setPresenterUsername(entry.getPresenterUsername());
            dto.setDegree(entry.getDegree().toString());
            dto.setTopic(entry.getTopic());
            dto.setPosition(entry.getPosition());
            dto.setAddedAt(entry.getAddedAt().toString());
            response.setEntries(List.of(dto));

            // Log API response to database
            databaseLoggerService.logAction("INFO", "WAITING_LIST_ADD_API_RETURNED",
                    String.format("Waiting list add API response for user %s, slot %d: success=true, position=%d",
                            presenterUsername.trim(), slotId, entry.getPosition()),
                    presenterUsername.trim(),
                    String.format("slotId=%d,success=true,code=ADDED_TO_WAITING_LIST,position=%d,degree=%s,httpStatus=200",
                            slotId, entry.getPosition(), entry.getDegree()));

            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.OK.value(), "User added to waiting list");
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            // Business logic errors - already logged by service layer, just return response
            // (Service logs specific tag like WAITING_LIST_BLOCKED_PHD_EXCLUSIVE)
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            WaitingListResponse errorResponse = new WaitingListResponse();
            errorResponse.setOk(false);
            errorResponse.setMessage(e.getMessage());
            errorResponse.setCode("BUSINESS_ERROR");
            errorResponse.setSlotId(slotId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (IllegalArgumentException e) {
            // Validation errors - already logged by service layer, just return response
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            WaitingListResponse errorResponse = new WaitingListResponse();
            errorResponse.setOk(false);
            errorResponse.setMessage(e.getMessage());
            errorResponse.setCode("INVALID_ARGUMENT");
            errorResponse.setSlotId(slotId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            String errorMsg = String.format("Unexpected error: %s", e.getMessage());
            LoggerUtil.logError(logger, "Unexpected error adding to waiting list", e);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, e, presenterUsername,
                    String.format("endpoint=%s,slotId=%d,exceptionType=%s", endpoint, slotId, e.getClass().getSimpleName()));
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            throw e;
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Remove user from waiting list (mobile-compatible endpoint)
     * DELETE /api/v1/slots/{slotId}/waiting-list?username={username}
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> removeFromWaitingList(
            @PathVariable Long slotId,
            @RequestParam(value = "username", required = false) String username) {
        LoggerUtil.generateAndSetCorrelationId();
        
        // Validate username parameter
        if (username == null || username.trim().isEmpty()) {
            String endpoint = String.format("/api/v1/slots/%d/waiting-list", slotId);
            LoggerUtil.logApiRequest(logger, "DELETE", endpoint, null);
            LoggerUtil.logApiResponse(logger, "DELETE", endpoint, HttpStatus.BAD_REQUEST.value(), "Username parameter is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of(
                            "ok", false,
                            "code", "MISSING_USERNAME",
                            "message", "Username parameter is required"
                    ));
        }
        
        // Set username in MDC context for logging (from query parameter)
        LoggerUtil.setBguUsername(username.trim());
        
        String endpoint = String.format("/api/v1/slots/%d/waiting-list?username=%s", slotId, username);
        LoggerUtil.logApiRequest(logger, "DELETE", endpoint, null);

        // Log API request to database
        databaseLoggerService.logAction("INFO", "WAITING_LIST_CANCEL_REQUEST",
                String.format("Waiting list cancel API called for user %s, slot %d", username.trim(), slotId),
                username.trim(),
                String.format("slotId=%d", slotId));

        try {
            waitingListService.removeFromWaitingList(slotId, username.trim());

            // Log API response to database
            databaseLoggerService.logAction("INFO", "WAITING_LIST_CANCEL_RESPONSE",
                    String.format("Waiting list cancel API response for user %s, slot %d: success=true", username.trim(), slotId),
                    username.trim(),
                    String.format("slotId=%d,success=true,code=REMOVED_FROM_WAITING_LIST,httpStatus=200", slotId));

            LoggerUtil.logApiResponse(logger, "DELETE", endpoint, HttpStatus.OK.value(), "User removed from waiting list");
            return ResponseEntity.ok(java.util.Map.of(
                    "ok", true,
                    "message", "Removed from waiting list"
            ));
        } catch (IllegalArgumentException e) {
            LoggerUtil.logError(logger, "Failed to remove from waiting list", e);
            LoggerUtil.logApiResponse(logger, "DELETE", endpoint, HttpStatus.NOT_FOUND.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of(
                            "ok", false,
                            "code", "NOT_ON_WAITING_LIST",
                            "message", e.getMessage()
                    ));
        } catch (Exception e) {
            LoggerUtil.logError(logger, "Unexpected error removing from waiting list", e);
            LoggerUtil.logApiResponse(logger, "DELETE", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of(
                            "ok", false,
                            "code", "INTERNAL_ERROR",
                            "message", "An unexpected error occurred"
                    ));
        } finally {
            LoggerUtil.clearContext();
        }
    }
}

