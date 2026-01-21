package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.service.RegistrationApprovalService;
import edu.bgu.semscanapi.service.WaitingListService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller for waiting list promotion confirmation/decline
 * These endpoints are called from email links
 */
@RestController
@RequestMapping("/api/waiting-list")
@CrossOrigin(origins = "*")
public class WaitingListPromotionController {

    private static final Logger logger = LoggerUtil.getLogger(WaitingListPromotionController.class);

    private final WaitingListService waitingListService;
    private final RegistrationApprovalService registrationApprovalService;

    public WaitingListPromotionController(WaitingListService waitingListService,
                                          RegistrationApprovalService registrationApprovalService) {
        this.waitingListService = waitingListService;
        this.registrationApprovalService = registrationApprovalService;
    }

    /**
     * Confirm promotion - user clicked "Yes, Register Me!"
     * GET /api/waiting-list/confirm?token=xxx
     */
    @GetMapping(value = "/confirm", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirmPromotion(@RequestParam("token") String token) {
        LoggerUtil.generateAndSetCorrelationId();
        logger.info("Processing promotion confirmation for token: {}", token);

        try {
            Optional<SeminarSlotRegistration> registration = waitingListService.confirmPromotion(token);

            if (registration.isEmpty()) {
                logger.warn("Promotion confirmation failed - token invalid or expired: {}", token);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(generateErrorHtml("Promotion Failed",
                                "This promotion link is invalid, expired, or the slot is no longer available.",
                                "Please check your email for a newer link, or contact support if you need assistance."));
            }

            SeminarSlotRegistration reg = registration.get();

            // Send supervisor approval email
            try {
                registrationApprovalService.sendApprovalEmail(reg);
                logger.info("Supervisor approval email sent for promoted user: {}", reg.getId().getPresenterUsername());
            } catch (Exception e) {
                logger.error("Failed to send supervisor approval email for promoted user: {}",
                        reg.getId().getPresenterUsername(), e);
                // Continue anyway - registration was created
            }

            logger.info("Promotion confirmed successfully for user: {} and slotId: {}",
                    reg.getId().getPresenterUsername(), reg.getId().getSlotId());

            return ResponseEntity.ok(generateSuccessHtml("Registration Confirmed!",
                    "You have been successfully registered for this presentation slot.",
                    "Your supervisor will receive an email to approve your registration. " +
                    "You will be notified once they respond."));

        } catch (Exception e) {
            logger.error("Error processing promotion confirmation for token: {}", token, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(generateErrorHtml("Error",
                            "An unexpected error occurred while processing your request.",
                            "Please try again later or contact support."));
        } finally {
            LoggerUtil.clearContext();
        }
    }

    /**
     * Decline promotion - user clicked "No Thanks"
     * GET /api/waiting-list/decline?token=xxx
     */
    @GetMapping(value = "/decline", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> declinePromotion(@RequestParam("token") String token) {
        LoggerUtil.generateAndSetCorrelationId();
        logger.info("Processing promotion decline for token: {}", token);

        try {
            waitingListService.declinePromotion(token);

            logger.info("Promotion declined for token: {}", token);

            return ResponseEntity.ok(generateSuccessHtml("Slot Declined",
                    "You have declined the presentation slot.",
                    "The slot will be offered to the next person on the waiting list. " +
                    "If you change your mind, you can join another waiting list from the app."));

        } catch (Exception e) {
            logger.error("Error processing promotion decline for token: {}", token, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(generateErrorHtml("Error",
                            "An unexpected error occurred while processing your request.",
                            "Please try again later or contact support."));
        } finally {
            LoggerUtil.clearContext();
        }
    }

    private String generateSuccessHtml(String title, String message, String details) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - SemScan</title>
            </head>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 50px auto; padding: 20px;">
                    <div style="background-color: #4CAF50; color: white; padding: 30px; text-align: center; border-radius: 8px 8px 0 0;">
                        <h1 style="margin: 0;">✓ %s</h1>
                    </div>
                    <div style="background-color: white; padding: 30px; border-radius: 0 0 8px 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
                        <p style="font-size: 18px; color: #333;">%s</p>
                        <p style="color: #666;">%s</p>
                        <div style="margin-top: 30px; text-align: center;">
                            <p style="color: #999; font-size: 14px;">You can close this window now.</p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """, title, title, message, details);
    }

    private String generateErrorHtml(String title, String message, String details) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - SemScan</title>
            </head>
            <body style="font-family: Arial, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5;">
                <div style="max-width: 600px; margin: 50px auto; padding: 20px;">
                    <div style="background-color: #f44336; color: white; padding: 30px; text-align: center; border-radius: 8px 8px 0 0;">
                        <h1 style="margin: 0;">✗ %s</h1>
                    </div>
                    <div style="background-color: white; padding: 30px; border-radius: 0 0 8px 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);">
                        <p style="font-size: 18px; color: #333;">%s</p>
                        <p style="color: #666;">%s</p>
                        <div style="margin-top: 30px; text-align: center;">
                            <p style="color: #999; font-size: 14px;">You can close this window now.</p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """, title, title, message, details);
    }
}
