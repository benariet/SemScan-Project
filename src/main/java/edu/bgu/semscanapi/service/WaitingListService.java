package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.ApprovalStatus;
import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.entity.WaitingListEntry;
import edu.bgu.semscanapi.entity.WaitingListPromotion;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.repository.WaitingListRepository;
import edu.bgu.semscanapi.repository.WaitingListPromotionRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.MailService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bgu.semscanapi.entity.SeminarSlotRegistrationId;
import java.time.LocalDateTime;
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
    private final WaitingListPromotionRepository waitingListPromotionRepository;
    private final AppConfigService appConfigService;

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
            MailService mailService,
            WaitingListPromotionRepository waitingListPromotionRepository,
            AppConfigService appConfigService) {
        this.waitingListRepository = waitingListRepository;
        this.registrationRepository = registrationRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.databaseLoggerService = databaseLoggerService;
        this.mailService = mailService;
        this.waitingListPromotionRepository = waitingListPromotionRepository;
        this.appConfigService = appConfigService;
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

        // Validate slotId and presenterUsername are not null/empty before processing waiting list addition
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

        // Verify the requested slot exists in database before adding user to waiting list
        Optional<SeminarSlot> slotOpt = slotRepository.findById(slotId);
        if (slotOpt.isEmpty()) {
            String errorMsg = String.format("Slot not found: slotId=%d", slotId);
            logger.error("{} - presenterUsername={}", errorMsg, normalizedUsername);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s", slotId, normalizedUsername));
            throw new IllegalArgumentException("Slot not found: " + slotId);
        }

        // Prevent duplicate entries: check if user is already on waiting list for this slot
        Optional<WaitingListEntry> existing = waitingListRepository.findBySlotIdAndPresenterUsername(slotId, normalizedUsername);
        if (existing.isPresent()) {
            String errorMsg = String.format("User is already on the waiting list for slotId=%d", slotId);
            logger.warn("{} - presenterUsername={}, existingPosition={}", errorMsg, normalizedUsername, existing.get().getPosition());
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s,existingPosition=%d", slotId, normalizedUsername, existing.get().getPosition()));
            throw new IllegalStateException("User is already on the waiting list for this slot");
        }

        // BUSINESS RULE: Users can only be on ONE waiting list at a time (across all slots)
        // This prevents users from being on multiple waiting lists simultaneously
        // NOTE: This restriction does NOT apply to REGISTRATION - users can register for available slots
        // even if they're on a waiting list for another slot
        boolean onAnyWaitingList = waitingListRepository.existsByPresenterUsername(normalizedUsername);
        if (onAnyWaitingList) {
            // Find which slot they're on the waiting list for (for better error message)
            List<WaitingListEntry> existingEntries = waitingListRepository.findByPresenterUsername(normalizedUsername);
            Long existingSlotId = existingEntries.isEmpty() ? null : existingEntries.get(0).getSlotId();
            String errorMsg = String.format("You can only be on 1 waiting list at once. Please wait for notification or cancel your current waiting list entry (slotId=%d)", 
                    existingSlotId != null ? existingSlotId : "unknown");
            logger.warn("{} - presenterUsername={}, existingSlotId={}, requestedSlotId={}", 
                    errorMsg, normalizedUsername, existingSlotId, slotId);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s,existingSlotId=%s,reason=ALREADY_ON_ANOTHER_WAITING_LIST", 
                            slotId, normalizedUsername, existingSlotId != null ? existingSlotId.toString() : "unknown"));
            throw new IllegalStateException("You can only be on 1 waiting list at once. Please wait for notification or cancel your current waiting list entry.");
        }

        // Prevent adding to waiting list if user already has an approved/pending registration for this slot
        boolean alreadyRegistered = registrationRepository.existsByIdSlotIdAndIdPresenterUsername(slotId, normalizedUsername);
        if (alreadyRegistered) {
            String errorMsg = String.format("User is already registered for slotId=%d", slotId);
            logger.warn("{} - presenterUsername={}", errorMsg, normalizedUsername);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s", slotId, normalizedUsername));
            throw new IllegalStateException("User is already registered for this slot");
        }

        // Retrieve user record to determine degree (PhD/MSc) which affects waiting list position and promotion logic
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
                    String.format("slotId=%d,presenterUsername=%s,userId=%d", slotId, normalizedUsername, user.getId()));
            throw new IllegalStateException("User degree is not set");
        }

        // Calculate position: new entries are added at the end (position = current count + 1)
        long currentCount = waitingListRepository.countBySlotId(slotId);
        int position = (int) currentCount + 1;

        // Create new waiting list entry with calculated position and user details
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
        
        // Remove user from waiting list and decrement positions of all entries that were after this one
        waitingListRepository.deleteBySlotIdAndPresenterUsername(slotId, presenterUsername);
        
        // Decrement positions of entries after this one
        waitingListRepository.decrementPositionsAfter(slotId, position);
        
        logger.info("Removed user {} from waiting list for slotId={}", presenterUsername, slotId);
        databaseLoggerService.logBusinessEvent("WAITING_LIST_REMOVED",
                String.format("User %s removed from waiting list for slotId=%d", presenterUsername, slotId),
                presenterUsername);
        
        // Send cancellation email OUTSIDE transaction to prevent holding database locks during slow SMTP operations
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

        // Retrieve slot information (date, time, location) for email content
        SeminarSlot slot = slotRepository.findById(entry.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + entry.getSlotId()));

        // Retrieve user information to format full name in email
        User user = userRepository.findByBguUsername(entry.getPresenterUsername())
                .orElse(null);

        // Format full name: "FirstName LastName" if available, otherwise use username
        String studentName = user != null && user.getFirstName() != null && user.getLastName() != null
                ? user.getFirstName() + " " + user.getLastName()
                : entry.getPresenterUsername();

        // Generate HTML email content with slot details and cancellation reason
        String subject = "SemScan - Waiting List Cancellation: Presentation Slot";
        String htmlContent = generateWaitingListCancellationEmailHtml(entry, slot, studentName, supervisorName);

        // Send email via MailService (handles SMTP connection and error handling)
        boolean sent = mailService.sendHtmlEmail(supervisorEmail, subject, htmlContent);
        if (sent) {
            logger.info("Waiting list cancellation email sent to supervisor {} for user {} and slotId={}",
                    supervisorEmail, entry.getPresenterUsername(), entry.getSlotId());
            databaseLoggerService.logBusinessEvent("EMAIL_WAITING_LIST_CANCELLATION_EMAIL_SENT",
                    String.format("Waiting list cancellation email sent to supervisor %s for user %s and slotId=%d",
                            supervisorEmail, entry.getPresenterUsername(), entry.getSlotId()),
                    entry.getPresenterUsername());
        } else {
            logger.error("Failed to send waiting list cancellation email to supervisor {} for user {} and slotId={}",
                    supervisorEmail, entry.getPresenterUsername(), entry.getSlotId());
            databaseLoggerService.logError("EMAIL_WAITING_LIST_CANCELLATION_EMAIL_FAILED",
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

        // Count approved registrations to determine if slot has available capacity
        // TODO: Enhance to check degree-specific capacity (PhD takes whole slot, MSc shares)
        List<SeminarSlotRegistration> existingRegistrations = registrationRepository
                .findByIdSlotIdAndApprovalStatus(slotId, ApprovalStatus.APPROVED);
        long approvedCount = existingRegistrations.size();

        WaitingListEntry nextEntry = waitingList.get(0);
        
        // Load slot to check capacity before promoting
        SeminarSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + slotId));
        
        // Check capacity: count both APPROVED and PENDING registrations
        List<SeminarSlotRegistration> allRegistrations = registrationRepository.findByIdSlotId(slotId);
        long pendingCount = allRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.PENDING)
                .count();
        int totalRegistrations = (int) (approvedCount + pendingCount);
        int capacity = slot.getCapacity() != null ? slot.getCapacity() : 0;
        
        // Promote if slot has capacity: total registrations (approved + pending) must be less than capacity
        if (totalRegistrations >= capacity && capacity > 0) {
            logger.info("Cannot promote user {} from waiting list - slot {} is full (capacity: {}, approved: {}, pending: {})",
                    nextEntry.getPresenterUsername(), slotId, capacity, approvedCount, pendingCount);
            return Optional.empty();
        }
        
        String promotedUsername = nextEntry.getPresenterUsername();
        
        // Get user details
        User promotedUser = userRepository.findByBguUsername(promotedUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + promotedUsername));
        
        // Check if user already registered (shouldn't happen, but safety check)
        if (registrationRepository.existsByIdSlotIdAndIdPresenterUsername(slotId, promotedUsername)) {
            logger.warn("User {} already registered for slot {} - removing from waiting list without promotion",
                    promotedUsername, slotId);
            waitingListRepository.deleteBySlotIdAndPresenterUsername(slotId, promotedUsername);
            return Optional.empty();
        }
        
        // CRITICAL: Create registration BEFORE removing from waiting list to prevent data loss
        SeminarSlotRegistration newRegistration = new SeminarSlotRegistration();
        newRegistration.setId(new SeminarSlotRegistrationId(slotId, promotedUsername));
        newRegistration.setDegree(promotedUser.getDegree());
        newRegistration.setTopic(nextEntry.getTopic());
        newRegistration.setSupervisorName(nextEntry.getSupervisorName());
        newRegistration.setSupervisorEmail(nextEntry.getSupervisorEmail());
        newRegistration.setRegisteredAt(LocalDateTime.now());
        newRegistration.setApprovalStatus(ApprovalStatus.PENDING);
        
        registrationRepository.save(newRegistration);
        
        // Remove from waiting list after successful registration creation
        int position = nextEntry.getPosition();
        waitingListRepository.deleteBySlotIdAndPresenterUsername(slotId, promotedUsername);
        
        // Decrement positions of all waiting list entries that were after this one (maintains queue order)
        waitingListRepository.decrementPositionsAfter(slotId, position);
        
        // Create WaitingListPromotion record with expiration time
        LocalDateTime promotedAt = LocalDateTime.now();
        Integer approvalWindowHours = appConfigService != null 
                ? appConfigService.getIntegerConfig("waiting_list_approval_window_hours", 24) 
                : 24;
        LocalDateTime expiresAt = promotedAt.plusHours(approvalWindowHours);
        
        WaitingListPromotion promotion = new WaitingListPromotion();
        promotion.setSlotId(slotId);
        promotion.setPresenterUsername(promotedUsername);
        promotion.setRegistrationSlotId(slotId);
        promotion.setRegistrationPresenterUsername(promotedUsername);
        promotion.setPromotedAt(promotedAt);
        promotion.setExpiresAt(expiresAt);
        promotion.setStatus(WaitingListPromotion.PromotionStatus.PENDING);
        waitingListPromotionRepository.save(promotion);
        
        logger.info("Promoted user {} from waiting list for slotId={} - created PENDING registration with promotion record (expires at {})", 
                promotedUsername, slotId, expiresAt);
        databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTED",
                String.format("User %s promoted from waiting list for slotId=%d (PENDING registration created, expires at %s, approval window: %d hours)",
                        promotedUsername, slotId, expiresAt, approvalWindowHours),
                promotedUsername);

        // NOTE: Student confirmation email should be sent by the caller using RegistrationApprovalService.sendStudentConfirmationEmail()
        // to avoid circular dependencies and maintain proper transaction boundaries
        // After student confirms, supervisor approval email will be sent automatically
        return Optional.of(newRegistration);
    }

    /**
     * Check if user is on waiting list for a specific slot
     */
    public boolean isOnWaitingList(Long slotId, String presenterUsername) {
        return waitingListRepository.existsBySlotIdAndPresenterUsername(slotId, presenterUsername);
    }

    /**
     * Check if user is on ANY waiting list (across all slots)
     * Used to enforce "only one waiting list at a time" rule
     */
    public boolean isOnAnyWaitingList(String presenterUsername) {
        return waitingListRepository.existsByPresenterUsername(presenterUsername);
    }

    /**
     * Get the waiting list entry for a user (if they're on any waiting list)
     * Returns the first waiting list entry found (users can only be on one at a time)
     */
    public Optional<WaitingListEntry> getWaitingListEntryForUser(String presenterUsername) {
        List<WaitingListEntry> entries = waitingListRepository.findByPresenterUsername(presenterUsername);
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.get(0));
    }

    /**
     * Get the count of people on the waiting list for a slot
     * @param slotId The slot ID
     * @return The number of people on the waiting list
     */
    public long getWaitingListCount(Long slotId) {
        return waitingListRepository.countBySlotId(slotId);
    }
}

