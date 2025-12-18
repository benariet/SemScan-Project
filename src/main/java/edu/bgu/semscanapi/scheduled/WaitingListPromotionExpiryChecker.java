package edu.bgu.semscanapi.scheduled;

import edu.bgu.semscanapi.entity.ApprovalStatus;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.WaitingListPromotion;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.WaitingListPromotionRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job to check and expire waiting list promotions
 * Runs every hour to auto-decline expired promotions
 */
@Component
public class WaitingListPromotionExpiryChecker {

    private static final Logger logger = LoggerUtil.getLogger(WaitingListPromotionExpiryChecker.class);

    @Autowired
    private WaitingListPromotionRepository promotionRepository;

    @Autowired
    private SeminarSlotRegistrationRepository registrationRepository;

    @Autowired(required = false)
    private DatabaseLoggerService databaseLoggerService;

    /**
     * Check for expired waiting list promotions and auto-decline them
     * Runs every hour (3600000 milliseconds)
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    public void checkExpiredPromotions() {
        logger.info("Checking for expired waiting list promotions...");
        
        if (databaseLoggerService != null) {
            databaseLoggerService.logAction("INFO", "WAITING_LIST_PROMOTION_EXPIRY_CHECK_STARTED",
                    "Scheduled job started: checking for expired waiting list promotions",
                    null, "job=WaitingListPromotionExpiryChecker");
        }
        
        LocalDateTime now = LocalDateTime.now();
        List<WaitingListPromotion> expiredPromotions = promotionRepository.findByExpiresAtBeforeAndStatus(
                now, WaitingListPromotion.PromotionStatus.PENDING);

        if (expiredPromotions.isEmpty()) {
            logger.debug("No expired waiting list promotions found");
            if (databaseLoggerService != null) {
                databaseLoggerService.logAction("INFO", "WAITING_LIST_PROMOTION_EXPIRY_CHECK_COMPLETED",
                        "Scheduled job completed: no expired waiting list promotions found",
                        null, "expiredCount=0");
            }
            return;
        }

        logger.info("Found {} expired waiting list promotion(s)", expiredPromotions.size());
        
        if (databaseLoggerService != null) {
            databaseLoggerService.logAction("INFO", "WAITING_LIST_PROMOTION_EXPIRY_CHECK_FOUND",
                    String.format("Found %d expired waiting list promotion(s) to process", expiredPromotions.size()),
                    null, String.format("expiredCount=%d", expiredPromotions.size()));
        }

        for (WaitingListPromotion promotion : expiredPromotions) {
            try {
                // Find the corresponding registration
                SeminarSlotRegistration registration = registrationRepository
                        .findById(new edu.bgu.semscanapi.entity.SeminarSlotRegistrationId(
                                promotion.getRegistrationSlotId(),
                                promotion.getRegistrationPresenterUsername()))
                        .orElse(null);

                if (registration != null && registration.getApprovalStatus() == ApprovalStatus.PENDING) {
                    // Auto-decline the registration
                    registration.setApprovalStatus(ApprovalStatus.EXPIRED);
                    registrationRepository.save(registration);

                    logger.info("Auto-declined expired registration for user {} in slot {}",
                            promotion.getRegistrationPresenterUsername(),
                            promotion.getRegistrationSlotId());
                }

                // Update promotion status to EXPIRED
                promotion.setStatus(WaitingListPromotion.PromotionStatus.EXPIRED);
                promotionRepository.save(promotion);

                // Log to app_logs
                if (databaseLoggerService != null) {
                    databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTION_EXPIRED",
                            String.format("Waiting list promotion expired for user %s, slotId=%d (expired at %s)",
                                    promotion.getRegistrationPresenterUsername(),
                                    promotion.getRegistrationSlotId(),
                                    promotion.getExpiresAt()),
                            promotion.getRegistrationPresenterUsername());
                }

                logger.info("Marked promotion as EXPIRED for user {} in slot {}",
                        promotion.getRegistrationPresenterUsername(),
                        promotion.getRegistrationSlotId());

            } catch (Exception e) {
                logger.error("Error processing expired promotion for user {} in slot {}: {}",
                        promotion.getRegistrationPresenterUsername(),
                        promotion.getRegistrationSlotId(),
                        e.getMessage(), e);
                
                // Log exception to database with ERROR level
                if (databaseLoggerService != null) {
                    databaseLoggerService.logError("WAITING_LIST_PROMOTION_EXPIRY_ERROR",
                            String.format("Error processing expired promotion for user %s in slot %d: %s",
                                    promotion.getRegistrationPresenterUsername(),
                                    promotion.getRegistrationSlotId(),
                                    e.getMessage()),
                            e,
                            promotion.getRegistrationPresenterUsername(),
                            String.format("slotId=%d,presenterUsername=%s,exceptionType=%s",
                                    promotion.getRegistrationSlotId(),
                                    promotion.getRegistrationPresenterUsername(),
                                    e.getClass().getSimpleName()));
                }
            }
        }

        logger.info("Completed checking expired waiting list promotions. Processed {} promotion(s)", expiredPromotions.size());
        
        if (databaseLoggerService != null) {
            databaseLoggerService.logAction("INFO", "WAITING_LIST_PROMOTION_EXPIRY_CHECK_COMPLETED",
                    String.format("Scheduled job completed: processed %d expired waiting list promotion(s)", expiredPromotions.size()),
                    null, String.format("processedCount=%d", expiredPromotions.size()));
        }
    }
}
