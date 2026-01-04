package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.service.RegistrationApprovalService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for handling registration approval/decline via email links
 */
@RestController
@CrossOrigin(origins = "*")
public class RegistrationApprovalController {

    private static final Logger logger = LoggerUtil.getLogger(RegistrationApprovalController.class);

    private final RegistrationApprovalService approvalService;

    public RegistrationApprovalController(RegistrationApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * Approve registration via token (mobile-compatible endpoint)
     * GET /api/v1/approve/{approvalToken}
     */
    @GetMapping(value = "/api/v1/approve/{approvalToken}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> approveRegistrationByToken(@PathVariable String approvalToken) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/approve/%s", approvalToken.substring(0, Math.min(8, approvalToken.length())) + "...");
        LoggerUtil.logApiRequest(logger, "GET", endpoint, null);

        try {
            approvalService.approveRegistrationByToken(approvalToken);
            
            String html = generateSuccessPage("Registration Approved", 
                    "The registration has been successfully approved.");
            
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.OK.value(), "Registration approved");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid approval request", e);
            String html = generateErrorPage("Invalid Request", e.getMessage());
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalStateException e) {
            logger.error("Approval failed", e);
            String html = generateErrorPage("Approval Failed", e.getMessage());
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (Exception e) {
            logger.error("Unexpected error during approval", e);
            String html = generateErrorPage("Error", "An unexpected error occurred. Please try again later.");
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Decline registration via token (mobile-compatible endpoint)
     * GET /api/v1/decline/{approvalToken}?reason=optional_reason
     */
    @GetMapping(value = "/api/v1/decline/{approvalToken}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> declineRegistrationByToken(
            @PathVariable String approvalToken,
            @RequestParam(required = false) String reason) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/decline/%s", approvalToken.substring(0, Math.min(8, approvalToken.length())) + "...");
        LoggerUtil.logApiRequest(logger, "GET", endpoint, null);

        try {
            approvalService.declineRegistrationByToken(approvalToken, reason);
            
            String html = generateSuccessPage("Registration Declined", 
                    "The registration has been declined.");
            
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.OK.value(), "Registration declined");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid decline request", e);
            String html = generateErrorPage("Invalid Request", e.getMessage());
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalStateException e) {
            logger.error("Decline failed", e);
            String html = generateErrorPage("Decline Failed", e.getMessage());
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (Exception e) {
            logger.error("Unexpected error during decline", e);
            String html = generateErrorPage("Error", "An unexpected error occurred. Please try again later.");
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Approve registration via token (original endpoint - kept for backward compatibility)
     * GET /api/v1/slots/{slotId}/registrations/{presenterUsername}/approve?token={token}
     */
    @GetMapping(value = "/api/v1/slots/{slotId}/registrations/{presenterUsername}/approve", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> approveRegistration(
            @PathVariable Long slotId,
            @PathVariable String presenterUsername,
            @RequestParam String token) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/slots/%d/registrations/%s/approve", slotId, presenterUsername);
        LoggerUtil.logApiRequest(logger, "GET", endpoint, "token=" + token.substring(0, Math.min(8, token.length())) + "...");

        try {
            approvalService.approveRegistration(slotId, presenterUsername, token);
            
            String html = generateSuccessPage("Registration Approved", 
                    "The registration has been successfully approved.");
            
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.OK.value(), "Registration approved");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid approval request", e);
            String html = generateErrorPage("Invalid Request", e.getMessage());
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalStateException e) {
            logger.error("Approval failed", e);
            String html = generateErrorPage("Approval Failed", e.getMessage());
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (Exception e) {
            logger.error("Unexpected error during approval", e);
            String html = generateErrorPage("Error", "An unexpected error occurred. Please try again later.");
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Decline registration via token (original endpoint - kept for backward compatibility)
     * GET /api/v1/slots/{slotId}/registrations/{presenterUsername}/decline?token={token}&reason={reason}
     */
    @GetMapping(value = "/api/v1/slots/{slotId}/registrations/{presenterUsername}/decline", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> declineRegistration(
            @PathVariable Long slotId,
            @PathVariable String presenterUsername,
            @RequestParam String token,
            @RequestParam(required = false) String reason) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/slots/%d/registrations/%s/decline", slotId, presenterUsername);
        LoggerUtil.logApiRequest(logger, "GET", endpoint, "token=" + token.substring(0, Math.min(8, token.length())) + "...");

        try {
            approvalService.declineRegistration(slotId, presenterUsername, token, reason != null ? reason : "No reason provided");
            
            String html = generateSuccessPage("Registration Declined", 
                    "The registration has been declined.");
            
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.OK.value(), "Registration declined");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid decline request", e);
            String html = generateErrorPage("Invalid Request", e.getMessage());
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalStateException e) {
            logger.error("Decline failed", e);
            String html = generateErrorPage("Decline Failed", e.getMessage());
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (Exception e) {
            logger.error("Unexpected error during decline", e);
            String html = generateErrorPage("Error", "An unexpected error occurred. Please try again later.");
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Student confirms promotion from waiting list
     * GET /api/v1/student-confirm/{confirmationToken}
     */
    @GetMapping(value = "/api/v1/student-confirm/{confirmationToken}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirmStudentPromotion(@PathVariable String confirmationToken) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/student-confirm/%s", confirmationToken.substring(0, Math.min(8, confirmationToken.length())) + "...");
        LoggerUtil.logApiRequest(logger, "GET", endpoint, null);

        try {
            approvalService.confirmStudentPromotion(confirmationToken);
            
            String html = generateSuccessPage("Promotion Confirmed", 
                    "You have confirmed your promotion. Your supervisor will receive an approval request email shortly.");
            
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.OK.value(), "Student promotion confirmed");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid confirmation request", e);
            String html = generateErrorPage("Invalid Request", e.getMessage());
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalStateException e) {
            logger.error("Confirmation failed", e);
            String html = generateErrorPage("Confirmation Failed", e.getMessage());
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (Exception e) {
            logger.error("Unexpected error during student confirmation", e);
            String html = generateErrorPage("Error", "An unexpected error occurred. Please try again later.");
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Student declines promotion from waiting list
     * GET /api/v1/student-decline/{confirmationToken}
     */
    @GetMapping(value = "/api/v1/student-decline/{confirmationToken}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> declineStudentPromotion(@PathVariable String confirmationToken) {
        LoggerUtil.generateAndSetCorrelationId();
        String endpoint = String.format("/api/v1/student-decline/%s", confirmationToken.substring(0, Math.min(8, confirmationToken.length())) + "...");
        LoggerUtil.logApiRequest(logger, "GET", endpoint, null);

        try {
            approvalService.declineStudentPromotion(confirmationToken);
            
            String html = generateSuccessPage("Promotion Declined", 
                    "You have declined the promotion. Your registration has been cancelled. The slot will be offered to the next person on the waiting list.");
            
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.OK.value(), "Student promotion declined");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid decline request", e);
            String html = generateErrorPage("Invalid Request", e.getMessage());
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (IllegalStateException e) {
            logger.error("Decline failed", e);
            String html = generateErrorPage("Decline Failed", e.getMessage());
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (Exception e) {
            logger.error("Unexpected error during student decline", e);
            String html = generateErrorPage("Error", "An unexpected error occurred. Please try again later.");
            LoggerUtil.logApiResponse(logger, "GET", endpoint, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } finally {
            LoggerUtil.clearContext();
        }
    }

    private String generateSuccessPage(String title, String message) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <style>
                    body { font-family: Arial, sans-serif; display: flex; justify-content: center; align-items: center; 
                           min-height: 100vh; margin: 0; background-color: #f5f5f5; }
                    .container { background: white; padding: 40px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); 
                                text-align: center; max-width: 500px; }
                    .success-icon { color: #4CAF50; font-size: 64px; margin-bottom: 20px; }
                    h1 { color: #333; margin-bottom: 20px; }
                    p { color: #666; line-height: 1.6; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="success-icon">✓</div>
                    <h1>%s</h1>
                    <p>%s</p>
                </div>
            </body>
            </html>
            """, title, title, message);
    }

    private String generateErrorPage(String title, String message) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <style>
                    body { font-family: Arial, sans-serif; display: flex; justify-content: center; align-items: center; 
                           min-height: 100vh; margin: 0; background-color: #f5f5f5; }
                    .container { background: white; padding: 40px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); 
                                text-align: center; max-width: 500px; }
                    .error-icon { color: #f44336; font-size: 64px; margin-bottom: 20px; }
                    h1 { color: #333; margin-bottom: 20px; }
                    p { color: #666; line-height: 1.6; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="error-icon">✗</div>
                    <h1>%s</h1>
                    <p>%s</p>
                </div>
            </body>
            </html>
            """, title, title, message);
    }
}

