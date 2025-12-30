package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.ErrorResponse;
import edu.bgu.semscanapi.dto.PresenterHomeResponse;
import edu.bgu.semscanapi.dto.PresenterOpenAttendanceResponse;
import edu.bgu.semscanapi.dto.PresenterSlotRegistrationRequest;
import edu.bgu.semscanapi.dto.PresenterSlotRegistrationResponse;
import edu.bgu.semscanapi.dto.SupervisorEmailRequest;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.PresenterHomeService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * Exposes the presenter home summary consumed by the mobile application.
 * Currently backed by stubbed data via {@link PresenterHomeService}.
 */
@RestController
@RequestMapping("/api/v1/presenters/{username}/home")
@CrossOrigin(origins = "*")
public class PresenterHomeController {

    private static final Logger logger = LoggerUtil.getLogger(PresenterHomeController.class);

    private final PresenterHomeService presenterHomeService;

    @Autowired(required = false)
    private DatabaseLoggerService databaseLoggerService;

    public PresenterHomeController(PresenterHomeService presenterHomeService) {
        this.presenterHomeService = presenterHomeService;
    }

    @GetMapping
    public ResponseEntity<?> getPresenterHome(@PathVariable("username") String presenterUsername) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/presenters/%s/home", presenterUsername);
        LoggerUtil.logApiRequest(logger, "GET", endpoint, null);

        try {
            PresenterHomeResponse response = presenterHomeService.getPresenterHome(presenterUsername);
            LoggerUtil.logApiResponse(logger, "GET", endpoint, 200, "Presenter home payload");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            LoggerUtil.logError(logger, "Presenter home request failed", ex);
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.NOT_FOUND.value(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Presenter not found", ex.getMessage(), HttpStatus.NOT_FOUND.value(), endpoint));
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Failed to build presenter home response", ex);
            LoggerUtil.logApiResponse(logger, "GET", endpoint, 500, "Internal Server Error");
            throw ex;
        } finally {
            LoggerUtil.clearContext();
        }
    }

    @PostMapping("/slots/{slotId}/register")
    public ResponseEntity<PresenterSlotRegistrationResponse> registerForSlot(@PathVariable("username") String presenterUsername,
                                                                             @PathVariable Long slotId,
                                                                             @Valid @RequestBody PresenterSlotRegistrationRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/presenters/%s/home/slots/%d/register", presenterUsername, slotId);
        LoggerUtil.logApiRequest(logger, "POST", endpoint, String.format("{\"topic\":\"%s\"}", request.getTopic()));

        // Log API request to database with full payload
        if (databaseLoggerService != null) {
            databaseLoggerService.logAction("INFO", "API_REGISTER_REQUEST",
                    String.format("Registration API called for presenter %s, slot %d", presenterUsername, slotId),
                    presenterUsername,
                    String.format("slotId=%d,topic=%s,supervisorName=%s,supervisorEmail=%s,seminarAbstract=%s",
                            slotId,
                            request.getTopic() != null ? request.getTopic() : "null",
                            request.getSupervisorName() != null ? request.getSupervisorName() : "null",
                            request.getSupervisorEmail() != null ? request.getSupervisorEmail() : "null",
                            request.getSeminarAbstract() != null ? "provided" : "null"));
        }

        try {
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot(presenterUsername, slotId, request);
            HttpStatus status = deriveStatus(response);

            // Log API response to database
            if (databaseLoggerService != null) {
                databaseLoggerService.logAction("INFO", "API_REGISTER_RESPONSE",
                        String.format("Registration API response for presenter %s, slot %d: success=%s, code=%s",
                                presenterUsername, slotId, response.isSuccess(), response.getCode()),
                        presenterUsername,
                        String.format("slotId=%d,success=%s,code=%s,message=%s,httpStatus=%d",
                                slotId, response.isSuccess(), response.getCode(), response.getMessage(), status.value()));
            }

            LoggerUtil.logApiResponse(logger, "POST", endpoint, status.value(), response.getMessage());
            return ResponseEntity.status(status).body(response);
        } catch (IllegalArgumentException ex) {
            LoggerUtil.logError(logger, "Presenter slot registration failed", ex);
            // Log error to database with full details
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("API_REGISTER_ERROR",
                        String.format("Registration failed for presenter %s, slot %d: %s", presenterUsername, slotId, ex.getMessage()),
                        ex, presenterUsername,
                        String.format("slotId=%d,errorType=IllegalArgumentException,httpStatus=404", slotId));
            }
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.NOT_FOUND.value(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PresenterSlotRegistrationResponse(false, ex.getMessage(), "NOT_FOUND", false));
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error during slot registration", ex);
            // Log unexpected error to database with full stack trace
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("API_REGISTER_UNEXPECTED_ERROR",
                        String.format("Unexpected error during registration for presenter %s, slot %d: %s",
                                presenterUsername, slotId, ex.getMessage()),
                        ex, presenterUsername,
                        String.format("slotId=%d,errorType=%s,httpStatus=500", slotId, ex.getClass().getSimpleName()));
            }
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            throw ex;
        } finally {
            LoggerUtil.clearContext();
        }
    }

    @DeleteMapping("/slots/{slotId}/register")
    public ResponseEntity<PresenterSlotRegistrationResponse> unregisterFromSlot(@PathVariable("username") String presenterUsername,
                                                                                @PathVariable Long slotId) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/presenters/%s/home/slots/%d/register", presenterUsername, slotId);
        LoggerUtil.logApiRequest(logger, "DELETE", endpoint, null);

        // Log API request to database
        if (databaseLoggerService != null) {
            databaseLoggerService.logAction("INFO", "API_UNREGISTER_REQUEST",
                    String.format("Unregister API called for presenter %s, slot %d", presenterUsername, slotId),
                    presenterUsername,
                    String.format("slotId=%d", slotId));
        }

        try {
            PresenterSlotRegistrationResponse response = presenterHomeService.unregisterFromSlot(presenterUsername, slotId);
            HttpStatus status = deriveStatus(response);

            // Log API response to database
            if (databaseLoggerService != null) {
                databaseLoggerService.logAction("INFO", "API_UNREGISTER_RESPONSE",
                        String.format("Unregister API response for presenter %s, slot %d: success=%s, code=%s",
                                presenterUsername, slotId, response.isSuccess(), response.getCode()),
                        presenterUsername,
                        String.format("slotId=%d,success=%s,code=%s,message=%s,httpStatus=%d",
                                slotId, response.isSuccess(), response.getCode(), response.getMessage(), status.value()));
            }

            LoggerUtil.logApiResponse(logger, "DELETE", endpoint, status.value(), response.getMessage());
            return ResponseEntity.status(status).body(response);
        } catch (IllegalArgumentException ex) {
            LoggerUtil.logError(logger, "Presenter slot unregister failed", ex);
            // Log error to database with full details
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("API_UNREGISTER_ERROR",
                        String.format("Unregister failed for presenter %s, slot %d: %s", presenterUsername, slotId, ex.getMessage()),
                        ex, presenterUsername,
                        String.format("slotId=%d,errorType=IllegalArgumentException,httpStatus=404", slotId));
            }
            LoggerUtil.logApiResponse(logger, "DELETE", endpoint, HttpStatus.NOT_FOUND.value(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PresenterSlotRegistrationResponse(false, ex.getMessage(), "NOT_FOUND", false));
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error during slot unregister", ex);
            // Log unexpected error to database with full stack trace
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("API_UNREGISTER_UNEXPECTED_ERROR",
                        String.format("Unexpected error during unregister for presenter %s, slot %d: %s",
                                presenterUsername, slotId, ex.getMessage()),
                        ex, presenterUsername,
                        String.format("slotId=%d,errorType=%s,httpStatus=500", slotId, ex.getClass().getSimpleName()));
            }
            LoggerUtil.logApiResponse(logger, "DELETE", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            throw ex;
        } finally {
            LoggerUtil.clearContext();
        }
    }

    @PostMapping("/slots/{slotId}/attendance/open")
    public ResponseEntity<PresenterOpenAttendanceResponse> openAttendance(@PathVariable("username") String presenterUsername,
                                                                          @PathVariable Long slotId) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/presenters/%s/home/slots/%d/attendance/open", presenterUsername, slotId);
        LoggerUtil.logApiRequest(logger, "POST", endpoint, null);

        try {
            PresenterOpenAttendanceResponse response = presenterHomeService.openAttendance(presenterUsername, slotId);
            HttpStatus status = deriveAttendanceStatus(response);
            LoggerUtil.logApiResponse(logger, "POST", endpoint, status.value(), response.getMessage());
            return ResponseEntity.status(status).body(response);
        } catch (IllegalArgumentException ex) {
            LoggerUtil.logError(logger, "Open attendance failed", ex);
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.NOT_FOUND.value(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PresenterOpenAttendanceResponse(false, ex.getMessage(), "NOT_FOUND", null, null, null, null, null));
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error during open attendance", ex);
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            throw ex;
        } finally {
            LoggerUtil.clearContext();
        }
    }

    @PostMapping("/slots/{slotId}/supervisor/email")
    public ResponseEntity<PresenterSlotRegistrationResponse> sendSupervisorEmail(@PathVariable("username") String presenterUsername,
                                                                                 @PathVariable Long slotId,
                                                                                 @Valid @RequestBody SupervisorEmailRequest request) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/presenters/%s/home/slots/%d/supervisor/email", presenterUsername, slotId);
        LoggerUtil.logApiRequest(logger, "POST", endpoint, String.format("supervisorEmail=%s", request.getSupervisorEmail()));

        try {
            PresenterSlotRegistrationResponse response = presenterHomeService.sendSupervisorEmail(
                    presenterUsername, 
                    slotId, 
                    request.getSupervisorEmail(), 
                    request.getSupervisorName()
            );
            HttpStatus status = deriveStatus(response);
            LoggerUtil.logApiResponse(logger, "POST", endpoint, status.value(), response.getMessage());
            return ResponseEntity.status(status).body(response);
        } catch (IllegalArgumentException ex) {
            LoggerUtil.logError(logger, "Send supervisor email failed", ex);
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.NOT_FOUND.value(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new PresenterSlotRegistrationResponse(false, ex.getMessage(), "NOT_FOUND", false));
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error during send supervisor email", ex);
            LoggerUtil.logApiResponse(logger, "POST", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            throw ex;
        } finally {
            LoggerUtil.clearContext();
        }
    }

    @GetMapping("/slots/{slotId}/attendance/qr")
    public ResponseEntity<Object> getSlotQRCode(@PathVariable("username") String presenterUsername,
                                                 @PathVariable Long slotId) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/presenters/%s/home/slots/%d/attendance/qr", presenterUsername, slotId);
        LoggerUtil.logApiRequest(logger, "GET", endpoint, null);

        try {
            Map<String, Object> qrData = presenterHomeService.getSlotQRCode(presenterUsername, slotId);
            LoggerUtil.logApiResponse(logger, "GET", endpoint, 200, "QR code data");
            return ResponseEntity.ok(qrData);
        } catch (IllegalArgumentException ex) {
            LoggerUtil.logError(logger, "Get QR code failed", ex);
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.NOT_FOUND.value(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage(), "code", "NOT_FOUND"));
        } catch (IllegalStateException ex) {
            LoggerUtil.logError(logger, "Get QR code failed - attendance not open", ex);
            // Distinguish between "attendance window closed" vs "attendance never opened" for better error messages
            String errorMessage = ex.getMessage();
            String errorCode;
            HttpStatus status;
            
            if (errorMessage != null && errorMessage.contains("closed")) {
                // Attendance window has closed
                status = HttpStatus.GONE; // 410 Gone - resource was available but is no longer
                errorCode = "ATTENDANCE_CLOSED";
            } else {
                // Attendance was never opened
                status = HttpStatus.BAD_REQUEST; // 400 Bad Request
                errorCode = "ATTENDANCE_NOT_OPEN";
            }
            
            LoggerUtil.logApiResponse(logger, "GET", endpoint, status.value(), errorMessage);
            return ResponseEntity.status(status)
                    .body(Map.of("error", errorMessage, "code", errorCode));
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Unexpected error during get QR code", ex);
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal Server Error", "code", "INTERNAL_ERROR"));
        } finally {
            LoggerUtil.clearContext();
        }
    }

    private HttpStatus deriveStatus(PresenterSlotRegistrationResponse response) {
        if (response.isSuccess()) {
            return HttpStatus.OK;
        }

        String code = response.getCode();
        
        // Email-related status codes
        if ("EMAIL_SENT".equals(code)) {
            return HttpStatus.OK;
        }
        if ("EMAIL_NOT_CONFIGURED".equals(code)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if ("EMAIL_AUTH_FAILED".equals(code)) {
            return HttpStatus.UNAUTHORIZED; // 401 - Authentication failed
        }
        if ("EMAIL_SEND_FAILED".equals(code)) {
            return HttpStatus.INTERNAL_SERVER_ERROR; // 500 - Server error
        }
        if ("EMAIL_ERROR".equals(code)) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if ("NO_SUPERVISOR_EMAIL".equals(code)) {
            return HttpStatus.BAD_REQUEST;
        }
        
        // Map registration-related error codes to HTTP status codes for API response
        if ("MISSING_USERNAME".equals(code)) {
            return HttpStatus.BAD_REQUEST;
        }
        if ("NOT_REGISTERED".equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if ("SLOT_FULL".equals(code) || "ALREADY_REGISTERED".equals(code)) {
            return HttpStatus.CONFLICT;
        }
        
        return HttpStatus.BAD_REQUEST;
    }

    private HttpStatus deriveAttendanceStatus(PresenterOpenAttendanceResponse response) {
        if (response.isSuccess()) {
            return HttpStatus.OK;
        }

        if ("TOO_EARLY".equals(response.getCode())) {
            return HttpStatus.FORBIDDEN;
        }
        if ("IN_PROGRESS".equals(response.getCode())) {
            return HttpStatus.CONFLICT;
        }
        if ("MISSING_USERNAME".equals(response.getCode())) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.BAD_REQUEST;
    }
}