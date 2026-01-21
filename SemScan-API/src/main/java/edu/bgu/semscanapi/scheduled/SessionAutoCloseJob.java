package edu.bgu.semscanapi.scheduled;

import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Scheduled job to automatically close expired attendance sessions.
 * This ensures that when a presenter opens a session and doesn't manually close it,
 * other presenters won't be blocked from opening their own sessions.
 */
@Component
public class SessionAutoCloseJob {

    private static final Logger logger = LoggerFactory.getLogger(SessionAutoCloseJob.class);
    private static final ZoneId ISRAEL_ZONE = ZoneId.of("Asia/Jerusalem");

    private final SeminarSlotRepository slotRepository;
    private final SessionRepository sessionRepository;
    private final DatabaseLoggerService databaseLoggerService;

    public SessionAutoCloseJob(SeminarSlotRepository slotRepository,
                               SessionRepository sessionRepository,
                               DatabaseLoggerService databaseLoggerService) {
        this.slotRepository = slotRepository;
        this.sessionRepository = sessionRepository;
        this.databaseLoggerService = databaseLoggerService;
    }

    /**
     * Runs every 2 minutes to close expired sessions and clear slot attendance fields.
     */
    @Scheduled(fixedRate = 120000) // 2 minutes
    @Transactional
    public void closeExpiredSessions() {
        LocalDateTime now = LocalDateTime.now(ISRAEL_ZONE);

        // Find slots where attendance_closes_at has passed but fields are still set
        List<SeminarSlot> expiredSlots = slotRepository.findSlotsWithExpiredAttendance(now);

        if (expiredSlots.isEmpty()) {
            return; // Nothing to do
        }

        logger.info("Found {} slots with expired attendance sessions to clean up", expiredSlots.size());

        for (SeminarSlot slot : expiredSlots) {
            try {
                // Close any OPEN sessions linked to this slot
                if (slot.getLegacySessionId() != null) {
                    sessionRepository.findById(slot.getLegacySessionId()).ifPresent(session -> {
                        if (session.getStatus() == Session.SessionStatus.OPEN) {
                            session.setStatus(Session.SessionStatus.CLOSED);
                            session.setEndTime(slot.getAttendanceClosesAt());
                            sessionRepository.save(session);
                            logger.info("Auto-closed session {} for slot {}", session.getSessionId(), slot.getSlotId());
                            databaseLoggerService.logSessionEvent("SESSION_AUTO_CLOSED",
                                session.getSessionId(), session.getSeminarId(), slot.getAttendanceOpenedBy());
                        }
                    });
                }

                // Clear the slot's attendance fields so other presenters can open their sessions
                String previousOpenedBy = slot.getAttendanceOpenedBy();
                LocalDateTime previousClosesAt = slot.getAttendanceClosesAt();

                slot.setAttendanceOpenedAt(null);
                slot.setAttendanceClosesAt(null);
                slot.setAttendanceOpenedBy(null);
                slot.setLegacySessionId(null);
                slotRepository.save(slot);

                logger.info("Cleared attendance fields for slot {} (was opened by {} until {})",
                    slot.getSlotId(), previousOpenedBy, previousClosesAt);
                databaseLoggerService.logAction("INFO", "SLOT_ATTENDANCE_AUTO_CLEARED",
                    String.format("Slot %d attendance fields cleared after session expired (was opened by %s)",
                        slot.getSlotId(), previousOpenedBy),
                    previousOpenedBy,
                    String.format("slotId=%d,closesAt=%s", slot.getSlotId(), previousClosesAt));

            } catch (Exception e) {
                logger.error("Error closing expired session for slot {}: {}", slot.getSlotId(), e.getMessage(), e);
            }
        }
    }
}
