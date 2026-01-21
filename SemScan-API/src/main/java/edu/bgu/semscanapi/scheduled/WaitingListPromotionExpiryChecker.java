package edu.bgu.semscanapi.scheduled;

import edu.bgu.semscanapi.entity.ApprovalStatus;
import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.WaitingListEntry;
import edu.bgu.semscanapi.entity.WaitingListPromotion;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.WaitingListPromotionRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.PresenterHomeService;
import edu.bgu.semscanapi.service.WaitingListService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Autowired
    private SeminarSlotRepository slotRepository;

    @Autowired
    private PresenterHomeService presenterHomeService;

    @Autowired
    private WaitingListService waitingListService;

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

                Long slotId = promotion.getRegistrationSlotId();
                boolean wasPending = false;
                
                if (registration != null && registration.getApprovalStatus() == ApprovalStatus.PENDING) {
                    // Auto-decline the registration
                    registration.setApprovalStatus(ApprovalStatus.EXPIRED);
                    registrationRepository.save(registration);
                    wasPending = true;

                    logger.info("Auto-declined expired registration for user {} in slot {}",
                            promotion.getRegistrationPresenterUsername(),
                            slotId);
                }

                // Update promotion status to EXPIRED
                promotion.setStatus(WaitingListPromotion.PromotionStatus.EXPIRED);
                promotionRepository.save(promotion);

                // Log to app_logs
                if (databaseLoggerService != null) {
                    databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTION_EXPIRED",
                            String.format("Waiting list promotion expired for user %s, slotId=%d (expired at %s)",
                                    promotion.getRegistrationPresenterUsername(),
                                    slotId,
                                    promotion.getExpiresAt()),
                            promotion.getRegistrationPresenterUsername());
                }

                logger.info("Marked promotion as EXPIRED for user {} in slot {}",
                        promotion.getRegistrationPresenterUsername(),
                        slotId);

                // CRITICAL: If a PENDING registration expired, it freed up capacity - promote next from waiting list
                // This ensures slots don't remain unfilled when promoted users don't respond
                if (wasPending) {
                    try {
                        Optional<SeminarSlot> slotOpt = slotRepository.findById(slotId);
                        if (slotOpt.isPresent()) {
                            SeminarSlot slot = slotOpt.get();
                            Optional<WaitingListEntry> promotedEntry = presenterHomeService.promoteFromWaitingListAfterCancellation(slotId, slot);
                            if (promotedEntry.isPresent()) {
                                logger.info("Successfully promoted next user {} from waiting list for slot {} after expiration",
                                        promotedEntry.get().getPresenterUsername(), slotId);
                                if (databaseLoggerService != null) {
                                    databaseLoggerService.logBusinessEvent("WAITING_LIST_AUTO_PROMOTED_AFTER_EXPIRY",
                                            String.format("Next person %s automatically promoted from waiting list for slotId=%d after previous promotion expired",
                                                    promotedEntry.get().getPresenterUsername(), slotId),
                                            promotedEntry.get().getPresenterUsername());
                                }
                            } else {
                                logger.debug("No one to promote from waiting list for slot {} after expiration (waiting list empty or slot full)", slotId);
                            }
                        } else {
                            logger.warn("Slot {} not found when attempting to promote after expiration", slotId);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to promote next person from waiting list for slot {} after expiration: {}",
                                slotId, e.getMessage(), e);
                        if (databaseLoggerService != null) {
                            databaseLoggerService.logError("WAITING_LIST_AUTO_PROMOTE_AFTER_EXPIRY_FAILED",
                                    String.format("Failed to automatically promote next person from waiting list for slotId=%d after promotion expired: %s",
                                            slotId, e.getMessage()),
                                    e, promotion.getRegistrationPresenterUsername(), String.format("slotId=%d", slotId));
                        }
                        // Don't fail the expiry operation if promotion fails
                    }
                }

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

    /**
     * Check for expired promotion OFFERS (where user didn't respond to "Do you want this slot?" email)
     * Runs every hour (3600000 milliseconds) with 30 minute offset from the other check
     *
     * This handles the new confirmation flow where:
     * 1. Spot opens up -> User gets email asking if they want it
     * 2. User has 24 hours to click "Yes" or "No"
     * 3. If they don't respond, we remove them from waiting list and offer to next person
     */
    @Scheduled(fixedRate = 3600000, initialDelay = 1800000) // 1 hour, start 30 min after app startup
    @Transactional
    public void checkExpiredPromotionOffers() {
        logger.info("Checking for expired promotion offers (unanswered 'Do you want this slot?' emails)...");

        if (databaseLoggerService != null) {
            databaseLoggerService.logAction("INFO", "WAITING_LIST_PROMOTION_OFFER_EXPIRY_CHECK_STARTED",
                    "Scheduled job started: checking for expired promotion offers",
                    null, "job=WaitingListPromotionOfferExpiryChecker");
        }

        try {
            waitingListService.processExpiredPromotionOffers();

            if (databaseLoggerService != null) {
                databaseLoggerService.logAction("INFO", "WAITING_LIST_PROMOTION_OFFER_EXPIRY_CHECK_COMPLETED",
                        "Scheduled job completed: processed expired promotion offers",
                        null, null);
            }
        } catch (Exception e) {
            logger.error("Error processing expired promotion offers: {}", e.getMessage(), e);
            if (databaseLoggerService != null) {
                databaseLoggerService.logError("WAITING_LIST_PROMOTION_OFFER_EXPIRY_ERROR",
                        String.format("Error processing expired promotion offers: %s", e.getMessage()),
                        e, null, null);
            }
        }

        logger.info("Completed checking expired promotion offers");
    }
}
