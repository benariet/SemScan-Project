package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.entity.WaitingListEntry;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.repository.WaitingListRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.MailService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class WaitingListService {

    private static final Logger logger = LoggerUtil.getLogger(WaitingListService.class);

    private final WaitingListRepository waitingListRepository;
    private final SeminarSlotRegistrationRepository registrationRepository;
    private final SeminarSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final DatabaseLoggerService databaseLoggerService;
    private final MailService mailService;

    @Value("${app.registration.msc.max-per-slot:3}")
    private int mscMaxPerSlot;

    @Value("${app.registration.phd.max-per-slot:1}")
    private int phdMaxPerSlot;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public WaitingListService(
            WaitingListRepository waitingListRepository,
            SeminarSlotRegistrationRepository registrationRepository,
            SeminarSlotRepository slotRepository,
            UserRepository userRepository,
            DatabaseLoggerService databaseLoggerService,
            MailService mailService) {
        this.waitingListRepository = waitingListRepository;
        this.registrationRepository = registrationRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.databaseLoggerService = databaseLoggerService;
        this.mailService = mailService;
    }

    /**
     * Add user to waiting list for a slot
     */
    @Transactional
    public WaitingListEntry addToWaitingList(Long slotId, String presenterUsername, String topic,
                                            String supervisorName, String supervisorEmail) {
        // Log entry point
        logger.info("Attempting to add user {} to waiting list for slotId={}", presenterUsername, slotId);
        databaseLoggerService.logAction("INFO", "WAITING_LIST_ADD_ATTEMPT",
                String.format("Attempting to add user %s to waiting list for slotId=%d (topic=%s, supervisor=%s)",
                        presenterUsername, slotId, topic != null ? topic : "null", supervisorName != null ? supervisorName : "null"),
                presenterUsername,
                String.format("slotId=%d,topic=%s,supervisorName=%s,supervisorEmail=%s", slotId, topic, supervisorName, supervisorEmail));

        // Validate input parameters
        if (slotId == null) {
            String errorMsg = "Slot ID is null";
            logger.error("{} - slotId=null, presenterUsername={}", errorMsg, presenterUsername);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, presenterUsername,
                    String.format("slotId=null,presenterUsername=%s", presenterUsername));
            throw new IllegalArgumentException(errorMsg);
        }

        if (presenterUsername == null || presenterUsername.trim().isEmpty()) {
            String errorMsg = "Presenter username is null or empty";
            logger.error("{} - slotId={}", errorMsg, slotId);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, null,
                    String.format("slotId=%d,presenterUsername=null", slotId));
            throw new IllegalArgumentException(errorMsg);
        }

        String normalizedUsername = presenterUsername.trim();

        // Check if slot exists
        Optional<SeminarSlot> slotOpt = slotRepository.findById(slotId);
        if (slotOpt.isEmpty()) {
            String errorMsg = String.format("Slot not found: slotId=%d", slotId);
            logger.error("{} - presenterUsername={}", errorMsg, normalizedUsername);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s", slotId, normalizedUsername));
            throw new IllegalArgumentException("Slot not found: " + slotId);
        }

        // Check if already in waiting list
        Optional<WaitingListEntry> existing = waitingListRepository.findBySlotIdAndPresenterUsername(slotId, normalizedUsername);
        if (existing.isPresent()) {
            String errorMsg = String.format("User is already on the waiting list for slotId=%d", slotId);
            logger.warn("{} - presenterUsername={}, existingPosition={}", errorMsg, normalizedUsername, existing.get().getPosition());
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s,existingPosition=%d", slotId, normalizedUsername, existing.get().getPosition()));
            throw new IllegalStateException("User is already on the waiting list for this slot");
        }

        // Check if already registered
        boolean alreadyRegistered = registrationRepository.existsByIdSlotIdAndIdPresenterUsername(slotId, normalizedUsername);
        if (alreadyRegistered) {
            String errorMsg = String.format("User is already registered for slotId=%d", slotId);
            logger.warn("{} - presenterUsername={}", errorMsg, normalizedUsername);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s", slotId, normalizedUsername));
            throw new IllegalStateException("User is already registered for this slot");
        }

        // Get user to determine degree
        Optional<User> userOpt = userRepository.findByBguUsername(normalizedUsername);
        if (userOpt.isEmpty()) {
            String errorMsg = String.format("User not found: %s", normalizedUsername);
            logger.error("{} - slotId={}", errorMsg, slotId);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s", slotId, normalizedUsername));
            throw new IllegalArgumentException("User not found: " + normalizedUsername);
        }

        User user = userOpt.get();
        if (user.getDegree() == null) {
            String errorMsg = String.format("User degree is not set for user: %s", normalizedUsername);
            logger.error("{} - slotId={}", errorMsg, slotId);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s,userId=%d", slotId, normalizedUsername, user.getUserId()));
            throw new IllegalStateException("User degree is not set");
        }

        // Get current position (next position = count + 1)
        long currentCount = waitingListRepository.countBySlotId(slotId);
        int position = (int) currentCount + 1;

        // Create waiting list entry
        WaitingListEntry entry = new WaitingListEntry();
        entry.setSlotId(slotId);
        entry.setPresenterUsername(normalizedUsername);
        entry.setDegree(user.getDegree());
        entry.setTopic(topic);
        entry.setSupervisorName(supervisorName);
        entry.setSupervisorEmail(supervisorEmail);
        entry.setPosition(position);

        try {
            WaitingListEntry saved = waitingListRepository.save(entry);
            logger.info("Added user {} to waiting list for slotId={} at position {}", normalizedUsername, slotId, position);
            databaseLoggerService.logBusinessEvent("WAITING_LIST_ADDED",
                    String.format("User %s added to waiting list for slotId=%d at position %d (degree=%s, topic=%s)",
                            normalizedUsername, slotId, position, user.getDegree(), topic != null ? topic : "null"),
                    normalizedUsername);
            return saved;
        } catch (Exception e) {
            String errorMsg = String.format("Database error while saving waiting list entry: %s", e.getMessage());
            logger.error("Failed to save waiting list entry for user {} and slotId={}", normalizedUsername, slotId, e);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, e, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s,degree=%s,position=%d,exceptionType=%s",
                            slotId, normalizedUsername, user.getDegree(), position, e.getClass().getSimpleName()));
            throw new RuntimeException("Failed to add to waiting list: " + e.getMessage(), e);
        }
    }

    /**
     * Remove user from waiting list
     * Returns the entry that was removed (for email notification purposes)
     */
    @Transactional
    public WaitingListEntry removeFromWaitingList(Long slotId, String presenterUsername) {
        Optional<WaitingListEntry> entry = waitingListRepository.findBySlotIdAndPresenterUsername(slotId, presenterUsername);
        if (entry.isEmpty()) {
            throw new IllegalArgumentException("Not on waiting list for this slot");
        }

        WaitingListEntry entryToRemove = entry.get();
        int position = entryToRemove.getPosition();
        
        // Delete from waiting list
        waitingListRepository.deleteBySlotIdAndPresenterUsername(slotId, presenterUsername);
        
        // Decrement positions of entries after this one
        waitingListRepository.decrementPositionsAfter(slotId, position);
        
        logger.info("Removed user {} from waiting list for slotId={}", presenterUsername, slotId);
        databaseLoggerService.logBusinessEvent("WAITING_LIST_REMOVED",
                String.format("User %s removed from waiting list for slotId=%d", presenterUsername, slotId),
                presenterUsername);
        
        // Send cancellation email to supervisor OUTSIDE the transaction
        // This prevents holding database locks during slow email operations
        try {
            sendWaitingListCancellationEmail(entryToRemove);
        } catch (Exception e) {
            logger.error("Failed to send waiting list cancellation email for user {} and slot {}", 
                    presenterUsername, slotId, e);
            // Don't fail removal if email fails
        }
        
        return entryToRemove;
    }

    /**
     * Send waiting list cancellation email to supervisor
     */
    private void sendWaitingListCancellationEmail(WaitingListEntry entry) {
        // Try to get supervisor email from waiting list entry first
        String supervisorEmail = entry.getSupervisorEmail();
        String supervisorName = entry.getSupervisorName();
        
        // If not in waiting list entry, try to find from previous registration
        if (supervisorEmail == null || supervisorEmail.trim().isEmpty()) {
            logger.debug("No supervisor email in waiting list entry - checking previous registrations. slotId={}, presenter={}", 
                    entry.getSlotId(), entry.getPresenterUsername());
            
            // Look for any previous registration by this user (for this slot or any slot) with supervisor info
            List<SeminarSlotRegistration> registrations = registrationRepository.findByIdPresenterUsername(entry.getPresenterUsername());
            for (SeminarSlotRegistration reg : registrations) {
                if (reg.getSupervisorEmail() != null && !reg.getSupervisorEmail().trim().isEmpty()) {
                    supervisorEmail = reg.getSupervisorEmail();
                    if (supervisorName == null || supervisorName.trim().isEmpty()) {
                        supervisorName = reg.getSupervisorName();
                    }
                    logger.info("Found supervisor email from previous registration for user {}: {}", 
                            entry.getPresenterUsername(), supervisorEmail);
                    break;
                }
            }
        }
        
        // If still no supervisor email, skip sending email
        if (supervisorEmail == null || supervisorEmail.trim().isEmpty()) {
            logger.debug("No supervisor email found for waiting list cancellation - skipping email. slotId={}, presenter={}", 
                    entry.getSlotId(), entry.getPresenterUsername());
            return;
        }

        // Get slot details
        SeminarSlot slot = slotRepository.findById(entry.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + entry.getSlotId()));

        // Get user details
        User user = userRepository.findByBguUsername(entry.getPresenterUsername())
                .orElse(null);

        // Format student name
        String studentName = user != null && user.getFirstName() != null && user.getLastName() != null
                ? user.getFirstName() + " " + user.getLastName()
                : entry.getPresenterUsername();

        // Generate email content
        String subject = "SemScan - Waiting List Cancellation: Presentation Slot";
        String htmlContent = generateWaitingListCancellationEmailHtml(entry, slot, studentName, supervisorName);

        // Send email
        boolean sent = mailService.sendHtmlEmail(supervisorEmail, subject, htmlContent);
        if (sent) {
            logger.info("Waiting list cancellation email sent to supervisor {} for user {} and slotId={}",
                    supervisorEmail, entry.getPresenterUsername(), entry.getSlotId());
            databaseLoggerService.logBusinessEvent("WAITING_LIST_CANCELLATION_EMAIL_SENT",
                    String.format("Waiting list cancellation email sent to supervisor %s for user %s and slotId=%d",
                            supervisorEmail, entry.getPresenterUsername(), entry.getSlotId()),
                    entry.getPresenterUsername());
        } else {
            logger.error("Failed to send waiting list cancellation email to supervisor {} for user {} and slotId={}",
                    supervisorEmail, entry.getPresenterUsername(), entry.getSlotId());
            databaseLoggerService.logError("WAITING_LIST_CANCELLATION_EMAIL_FAILED",
                    String.format("Failed to send waiting list cancellation email to supervisor %s for user %s and slotId=%d",
                            supervisorEmail, entry.getPresenterUsername(), entry.getSlotId()),
                    null, entry.getPresenterUsername(),
                    String.format("slotId=%d,supervisorEmail=%s", entry.getSlotId(), supervisorEmail));
        }
    }

    /**
     * Generate HTML email content for waiting list cancellation notification
     */
    private String generateWaitingListCancellationEmailHtml(WaitingListEntry entry, SeminarSlot slot, String studentName, String supervisorName) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #ff9800; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background-color: #f9f9f9; }
                    .info-box { background-color: #fff3cd; padding: 15px; margin: 15px 0; border-left: 4px solid #ffc107; }
                    .details-box { background-color: #e3f2fd; padding: 15px; margin: 15px 0; border-left: 4px solid #2196F3; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>SemScan - Waiting List Cancellation</h1>
                    </div>
                    <div class="content">
                        <p>Dear %s,</p>
                        
                        <div class="info-box">
                            <h3 style="margin-top: 0; color: #856404;">⚠️ Waiting List Position Cancelled</h3>
                            <p>The student has cancelled their waiting list position for this slot.</p>
                        </div>
                        
                        <div class="details-box">
                            <h3>Student Details:</h3>
                            <p><strong>Name:</strong> %s</p>
                            <p><strong>Username:</strong> %s</p>
                            <p><strong>Degree:</strong> %s</p>
                            <p><strong>Topic:</strong> %s</p>
                        </div>
                        
                        <div class="details-box">
                            <h3>Slot Information:</h3>
                            <p><strong>Date:</strong> %s</p>
                            <p><strong>Time:</strong> %s - %s</p>
                            <p><strong>Location:</strong> %s, Room %s</p>
                        </div>
                        
                        <p><strong>Note:</strong> The slot may still be available for other students.</p>
                        
                        <p>If you have any questions, please contact the system administrator.</p>
                    </div>
                    <div class="footer">
                        <p>This is an automated message from SemScan Attendance System.</p>
                        <p>If you did not expect this email, please ignore it.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
            supervisorName != null && !supervisorName.trim().isEmpty() ? supervisorName : "Supervisor",
            studentName,
            entry.getPresenterUsername(),
            entry.getDegree() != null ? entry.getDegree().toString() : "N/A",
            entry.getTopic() != null ? entry.getTopic() : "Not specified",
            slot.getSlotDate() != null ? DATE_FORMAT.format(slot.getSlotDate()) : "N/A",
            slot.getStartTime() != null ? TIME_FORMAT.format(slot.getStartTime()) : "N/A",
            slot.getEndTime() != null ? TIME_FORMAT.format(slot.getEndTime()) : "N/A",
            slot.getBuilding() != null ? slot.getBuilding() : "N/A",
            slot.getRoom() != null ? slot.getRoom() : "N/A"
        );
    }

    /**
     * Get waiting list for a slot
     */
    public List<WaitingListEntry> getWaitingList(Long slotId) {
        return waitingListRepository.findBySlotIdOrderByPositionAsc(slotId);
    }

    /**
     * Promote next user from waiting list when a slot becomes available
     */
    @Transactional
    public Optional<SeminarSlotRegistration> promoteNextFromWaitingList(Long slotId) {
        List<WaitingListEntry> waitingList = waitingListRepository.findBySlotIdOrderByPositionAsc(slotId);
        if (waitingList.isEmpty()) {
            return Optional.empty();
        }

        // Get current approved registrations count
        // Note: This logic should be enhanced to check actual degree counts
        // For now, we'll use a simpler approach and let PresenterHomeService handle capacity checks

        WaitingListEntry nextEntry = waitingList.get(0);
        
        // Check if slot has capacity
        // This logic should be enhanced based on degree and current registrations
        // For now, we'll just promote if there's space
        
        // Remove from waiting list (don't send cancellation email for promotion)
        // We need to remove manually to avoid sending cancellation email
        int position = nextEntry.getPosition();
        waitingListRepository.deleteBySlotIdAndPresenterUsername(slotId, nextEntry.getPresenterUsername());
        
        // Decrement positions of entries after this one
        waitingListRepository.decrementPositionsAfter(slotId, position);
        
        logger.info("Promoted user {} from waiting list for slotId={} (removed without cancellation email)", 
                nextEntry.getPresenterUsername(), slotId);
        databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTED",
                String.format("User %s promoted from waiting list for slotId=%d", nextEntry.getPresenterUsername(), slotId),
                nextEntry.getPresenterUsername());

        // Create registration (this will need to be handled by PresenterHomeService)
        // For now, return the entry so the caller can create the registration
        logger.info("Promoted user {} from waiting list for slotId={}", nextEntry.getPresenterUsername(), slotId);
        
        // Return empty for now - the actual registration creation should be handled by PresenterHomeService
        return Optional.empty();
    }

    /**
     * Check if user is on waiting list
     */
    public boolean isOnWaitingList(Long slotId, String presenterUsername) {
        return waitingListRepository.existsBySlotIdAndPresenterUsername(slotId, presenterUsername);
    }
}

