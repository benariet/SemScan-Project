package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.ApprovalStatus;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for cleaning up expired registration approvals
 */
@Service
public class RegistrationCleanupService {

    private static final Logger logger = LoggerUtil.getLogger(RegistrationCleanupService.class);

    private final SeminarSlotRegistrationRepository registrationRepository;
    private final DatabaseLoggerService databaseLoggerService;

    public RegistrationCleanupService(SeminarSlotRegistrationRepository registrationRepository,
                                      DatabaseLoggerService databaseLoggerService) {
        this.registrationRepository = registrationRepository;
        this.databaseLoggerService = databaseLoggerService;
    }

    /**
     * Mark expired pending registrations as EXPIRED
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void expirePendingRegistrations() {
        logger.info("Starting scheduled task to expire pending registrations");
        
        LocalDateTime now = LocalDateTime.now();
        List<SeminarSlotRegistration> expiredRegistrations = registrationRepository
                .findExpiredPendingRegistrations(ApprovalStatus.PENDING, now);

        int expiredCount = 0;
        for (SeminarSlotRegistration registration : expiredRegistrations) {
            registration.setApprovalStatus(ApprovalStatus.EXPIRED);
            registrationRepository.save(registration);
            expiredCount++;
            logger.debug("Expired registration for slotId={}, presenter={}", 
                    registration.getSlotId(), registration.getPresenterUsername());
            databaseLoggerService.logBusinessEvent("REGISTRATION_EXPIRED_SCHEDULED",
                    String.format("Registration expired (scheduled cleanup) for slotId=%d, presenter=%s",
                            registration.getSlotId(), registration.getPresenterUsername()),
                    registration.getPresenterUsername());
        }

        logger.info("Expired {} pending registrations", expiredCount);
        if (expiredCount > 0) {
            databaseLoggerService.logAction("INFO", "REGISTRATION_CLEANUP",
                    String.format("Scheduled cleanup expired %d pending registrations", expiredCount),
                    null, String.format("expiredCount=%d", expiredCount));
        }
    }
}

