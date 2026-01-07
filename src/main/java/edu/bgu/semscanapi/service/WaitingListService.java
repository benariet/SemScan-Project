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
import java.util.UUID;

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
    private final FcmService fcmService;

    @Value("${app.registration.msc.max-per-slot:3}")
    private int mscMaxPerSlot;

    @Value("${app.registration.phd.max-per-slot:1}")
    private int phdMaxPerSlot;

    @Value("${app.server.base-url:http://132.72.50.53:8080}")
    private String serverBaseUrl;

    // Removed hardcoded PROMOTION_OFFER_EXPIRY_HOURS - now using config "promotion_offer_expiry_hours"
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
            AppConfigService appConfigService,
            FcmService fcmService) {
        this.waitingListRepository = waitingListRepository;
        this.registrationRepository = registrationRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.databaseLoggerService = databaseLoggerService;
        this.mailService = mailService;
        this.waitingListPromotionRepository = waitingListPromotionRepository;
        this.appConfigService = appConfigService;
        this.fcmService = fcmService;
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
                        presenterUsername, slotId, topic != null ? topic : "null", 
                        supervisorName != null ? supervisorName : "null"),
                presenterUsername,
                String.format("slotId=%d,topic=%s,supervisorName=%s,supervisorEmail=%s", 
                        slotId, topic, supervisorName, supervisorEmail));

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
            // Business rule rejection - log as WARN, not ERROR
            databaseLoggerService.logAction("WARN", "WAITING_LIST_ALREADY_ON_LIST", errorMsg, normalizedUsername,
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
            // Business rule rejection - log as WARN, not ERROR
            databaseLoggerService.logAction("WARN", "WAITING_LIST_ALREADY_ON_ANOTHER", errorMsg, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s,existingSlotId=%s,reason=ALREADY_ON_ANOTHER_WAITING_LIST",
                            slotId, normalizedUsername, existingSlotId != null ? existingSlotId.toString() : "unknown"));
            throw new IllegalStateException("You can only be on 1 waiting list at once. Please wait for notification or cancel your current waiting list entry.");
        }

        // Prevent adding to waiting list if user already has an ACTIVE registration (PENDING or APPROVED) for this slot
        // DECLINED and EXPIRED registrations should NOT block joining the waiting list
        boolean hasActiveRegistration = registrationRepository.existsActiveRegistration(slotId, normalizedUsername);
        if (hasActiveRegistration) {
            String errorMsg = String.format("User has active registration (PENDING/APPROVED) for slotId=%d", slotId);
            logger.warn("{} - presenterUsername={}", errorMsg, normalizedUsername);
            // Business rule rejection - log as WARN, not ERROR
            databaseLoggerService.logAction("WARN", "WAITING_LIST_HAS_ACTIVE_REGISTRATION", errorMsg, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s", slotId, normalizedUsername));
            throw new IllegalStateException("User already has an active registration for this slot");
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

        // PhD/MSc exclusivity rules for waiting list:
        // 1. If PhD already registered, waiting list is open but "typed" by first joiner
        // 2. If MSc already registered, PhD can't join waiting list (they'd never get promoted anyway)
        // 3. First person to join waiting list sets the "type" (PhD-only or MSc-only)
        // 4. Only same-degree users can join after that
        // 5. When waiting list empties completely, type resets
        List<SeminarSlotRegistration> allRegistrations = registrationRepository.findByIdSlotId(slotId);
        List<SeminarSlotRegistration> activeRegistrations = allRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED ||
                              reg.getApprovalStatus() == ApprovalStatus.PENDING)
                .collect(java.util.stream.Collectors.toList());

        boolean existingPhd = activeRegistrations.stream()
                .anyMatch(reg -> User.Degree.PhD == reg.getDegree());
        boolean existingMsc = activeRegistrations.stream()
                .anyMatch(reg -> User.Degree.MSc == reg.getDegree());

        // Rule 1: First-sets-type queue - applies to ALL slots
        // First person to join sets the type, only same-degree can join after
        List<WaitingListEntry> currentWaitingList = waitingListRepository.findBySlotIdOrderByPositionAsc(slotId);

        if (!currentWaitingList.isEmpty()) {
            // Waiting list has entries - check the type (degree of position 1)
            User.Degree queueType = currentWaitingList.get(0).getDegree();

            if (user.getDegree() != queueType) {
                // User's degree doesn't match queue type
                String errorMsg = String.format("Waiting list is currently %s-only (first in queue is %s)",
                        queueType, queueType);
                logger.warn("{} - slotId={}, presenterUsername={}, userDegree={}, queueType={}",
                        errorMsg, slotId, normalizedUsername, user.getDegree(), queueType);
                databaseLoggerService.logAction("WARN", "WAITING_LIST_DEGREE_MISMATCH", errorMsg, normalizedUsername,
                        String.format("slotId=%d,presenterUsername=%s,userDegree=%s,queueType=%s",
                                slotId, normalizedUsername, user.getDegree(), queueType));
                throw new IllegalStateException(String.format(
                        "Cannot join waiting list - queue is currently %s-only", queueType));
            }
            // User's degree matches queue type - allow (fall through)
            logger.info("User {} ({}) joining waiting list for slot {} - queue type is {} (matches)",
                    normalizedUsername, user.getDegree(), slotId, queueType);
        } else {
            // Waiting list is empty - user is first, they set the type
            logger.info("User {} ({}) is first to join waiting list for slot {} - setting queue type to {}",
                    normalizedUsername, user.getDegree(), slotId, user.getDegree());
        }

        // Rule 2: If MSc already registered, warn PhD but allow joining (they need ALL MSc to cancel to be promoted)
        if (user.getDegree() == User.Degree.PhD && existingMsc) {
            long mscCount = activeRegistrations.stream().filter(r -> r.getDegree() == User.Degree.MSc).count();
            String warnMsg = String.format("PhD joining waiting list for slot with %d MSc - ALL MSc must cancel for promotion", mscCount);
            logger.warn("{} - slotId={}, presenterUsername={}", warnMsg, slotId, normalizedUsername);
            databaseLoggerService.logAction("WARN", "WAITING_LIST_PHD_JOINING_MSC_SLOT", warnMsg, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s,mscCount=%d", slotId, normalizedUsername, mscCount));
            // Allow joining but log warning - PhD understands the risk from UI message
        }

        // Get supervisor from User entity (preferred) or fall back to request parameters (backward compatibility)
        String finalSupervisorName = null;
        String finalSupervisorEmail = null;
        
        if (user.getSupervisor() != null) {
            // Use supervisor from User entity (new way - after account setup)
            finalSupervisorName = user.getSupervisor().getName();
            finalSupervisorEmail = user.getSupervisor().getEmail();
            logger.info("Using supervisor from User entity: name={}, email={}", finalSupervisorName, finalSupervisorEmail);
        } else if (supervisorName != null && !supervisorName.trim().isEmpty() && 
                   supervisorEmail != null && !supervisorEmail.trim().isEmpty()) {
            // Fall back to request parameters (backward compatibility for users who haven't completed account setup)
            finalSupervisorName = supervisorName.trim();
            finalSupervisorEmail = supervisorEmail.trim();
            logger.warn("User {} has no supervisor linked. Using supervisor from request (backward compatibility): name={}, email={}. " +
                    "User should complete account setup via /api/v1/auth/setup/{}", 
                    normalizedUsername, finalSupervisorName, finalSupervisorEmail, normalizedUsername);
        } else {
            // No supervisor available - this is required
            String errorMsg = String.format("Supervisor information is required. User %s has no supervisor linked and none provided in request. " +
                    "Please complete account setup via /api/v1/auth/setup/%s", normalizedUsername, normalizedUsername);
            logger.error("{} - slotId={}", errorMsg, slotId);
            databaseLoggerService.logError("WAITING_LIST_ADD_FAILED", errorMsg, null, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s,userId=%d,reason=NO_SUPERVISOR", 
                            slotId, normalizedUsername, user.getId()));
            throw new IllegalStateException("Supervisor information is required. Please complete account setup first.");
        }

        // Check waiting list capacity limit per slot (configurable, default=2)
        long currentCount = waitingListRepository.countBySlotId(slotId);
        int waitingListLimit = appConfigService.getIntegerConfig("waiting.list.limit.per.slot", 2);
        
        if (currentCount >= waitingListLimit) {
            String errorMsg = String.format("Waiting list for slot %d is full (limit: %d)", slotId, waitingListLimit);
            logger.warn("{} - presenterUsername={}, currentCount={}", errorMsg, normalizedUsername, currentCount);
            // Business rule rejection - log as WARN, not ERROR
            databaseLoggerService.logAction("WARN", "WAITING_LIST_FULL", errorMsg, normalizedUsername,
                    String.format("slotId=%d,presenterUsername=%s,currentCount=%d,limit=%d,reason=WAITING_LIST_FULL",
                            slotId, normalizedUsername, currentCount, waitingListLimit));
            throw new IllegalStateException("Waiting list is full. Please try again later.");
        }
        
        // Calculate position: new entries are added at the end (position = current count + 1)
        int position = (int) currentCount + 1;

        // Create new waiting list entry with calculated position and user details
        WaitingListEntry entry = new WaitingListEntry();
        entry.setSlotId(slotId);
        entry.setPresenterUsername(normalizedUsername);
        entry.setDegree(user.getDegree());
        entry.setTopic(topic);
        entry.setSupervisorName(finalSupervisorName);
        entry.setSupervisorEmail(finalSupervisorEmail);
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
        return removeFromWaitingList(slotId, presenterUsername, true);
    }

    /**
     * Remove user from waiting list with option to skip cancellation email (used during promotions)
     */
    public WaitingListEntry removeFromWaitingList(Long slotId, String presenterUsername, boolean sendCancellationEmail) {
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

        // Send cancellation email only if requested (not during promotions)
        if (sendCancellationEmail) {
            try {
                sendWaitingListCancellationEmail(entryToRemove);
            } catch (Exception e) {
                logger.error("Failed to send waiting list cancellation email for user {} and slot {}",
                        presenterUsername, slotId, e);
                // Don't fail removal if email fails
            }
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
     * Offer promotion to next user from waiting list when a slot becomes available.
     * Instead of auto-promoting, this sends an email asking if they still want to register.
     * User has 24 hours to confirm or decline.
     *
     * @return true if a promotion offer was sent, false if no one available
     */
    @Transactional
    public boolean offerPromotionToNextFromWaitingList(Long slotId) {
        // Find entries that don't already have a pending promotion offer
        List<WaitingListEntry> availableEntries = waitingListRepository
                .findAvailableForPromotion(slotId, LocalDateTime.now());

        if (availableEntries.isEmpty()) {
            logger.info("No one available for promotion on waiting list for slotId={}", slotId);
            return false;
        }

        WaitingListEntry nextEntry = availableEntries.get(0);

        // Load slot to check capacity
        SeminarSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + slotId));

        // Check capacity before offering promotion
        List<SeminarSlotRegistration> allRegistrations = registrationRepository.findByIdSlotId(slotId);
        long approvedCount = allRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED)
                .count();
        long pendingCount = allRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.PENDING)
                .count();
        int totalRegistrations = (int) (approvedCount + pendingCount);
        int capacity = slot.getCapacity() != null ? slot.getCapacity() : 0;

        if (totalRegistrations >= capacity && capacity > 0) {
            logger.info("Cannot offer promotion - slot {} is full (capacity: {}, approved: {}, pending: {})",
                    slotId, capacity, approvedCount, pendingCount);
            return false;
        }

        // PhD/MSc exclusivity check for promotion
        List<SeminarSlotRegistration> activeRegistrations = allRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED ||
                              reg.getApprovalStatus() == ApprovalStatus.PENDING)
                .collect(java.util.stream.Collectors.toList());

        boolean existingPhd = activeRegistrations.stream()
                .anyMatch(reg -> User.Degree.PhD == reg.getDegree());
        boolean existingMsc = activeRegistrations.stream()
                .anyMatch(reg -> User.Degree.MSc == reg.getDegree());

        // If PhD registered, slot is locked - no promotions
        if (existingPhd) {
            logger.info("Cannot offer promotion - slot {} has PhD presenter (exclusive)", slotId);
            databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTION_BLOCKED_PHD_EXCLUSIVE",
                    String.format("Cannot offer promotion for slot %d - PhD presenter has exclusive reservation", slotId),
                    nextEntry.getPresenterUsername());
            return false;
        }

        // If next person is PhD but MSc already registered, skip PhD and try next
        if (nextEntry.getDegree() == User.Degree.PhD && existingMsc) {
            logger.info("Skipping PhD user {} for promotion - slot {} has MSc presenters",
                    nextEntry.getPresenterUsername(), slotId);
            databaseLoggerService.logBusinessEvent("WAITING_LIST_PHD_SKIPPED_MSC_EXISTS",
                    String.format("PhD user %s skipped for promotion on slot %d - MSc presenters exist, removing from waiting list",
                            nextEntry.getPresenterUsername(), slotId),
                    nextEntry.getPresenterUsername());
            // Remove PhD from waiting list since they can never be promoted for this slot
            removeFromWaitingList(slotId, nextEntry.getPresenterUsername(), false);
            // Try next person
            return offerPromotionToNextFromWaitingList(slotId);
        }

        String username = nextEntry.getPresenterUsername();

        // Check if user already registered
        if (registrationRepository.existsByIdSlotIdAndIdPresenterUsername(slotId, username)) {
            logger.warn("User {} already registered for slot {} - removing from waiting list",
                    username, slotId);
            removeFromWaitingList(slotId, username, false);
            // Try next person
            return offerPromotionToNextFromWaitingList(slotId);
        }

        // Generate promotion token
        String token = UUID.randomUUID().toString();
        int promotionOfferExpiryHours = appConfigService.getIntegerConfig("promotion_offer_expiry_hours", 24);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(promotionOfferExpiryHours);

        // Update waiting list entry with promotion offer
        nextEntry.setPromotionToken(token);
        nextEntry.setPromotionTokenExpiresAt(expiresAt);
        nextEntry.setPromotionOfferedAt(LocalDateTime.now());
        waitingListRepository.save(nextEntry);

        logger.info("Offering promotion to user {} for slotId={}, token expires at {}",
                username, slotId, expiresAt);
        databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTION_OFFERED",
                String.format("Promotion offered to user %s for slotId=%d (expires at %s)",
                        username, slotId, expiresAt),
                username);

        // Send promotion offer email
        sendPromotionOfferEmail(nextEntry, slot, token);

        // Send push notification
        try {
            String slotDate = slot.getSlotDate().format(DATE_FORMAT);
            fcmService.sendPromotionNotification(username, slotId, slotDate);
        } catch (Exception e) {
            logger.error("Failed to send push notification for promotion offer to user {}", username, e);
            // Don't fail promotion if push notification fails
        }

        return true;
    }

    /**
     * Legacy method - now redirects to offerPromotionToNextFromWaitingList
     * Returns Optional.empty() since registration is no longer created immediately
     */
    @Transactional
    public Optional<SeminarSlotRegistration> promoteNextFromWaitingList(Long slotId) {
        boolean offered = offerPromotionToNextFromWaitingList(slotId);
        // Return empty since we don't create registration immediately anymore
        // The registration will be created when user confirms
        return Optional.empty();
    }

    /**
     * Confirm promotion - user clicked "Yes, I want to register"
     * Creates the PENDING registration and removes from waiting list
     */
    @Transactional
    public Optional<SeminarSlotRegistration> confirmPromotion(String token) {
        Optional<WaitingListEntry> entryOpt = waitingListRepository.findByPromotionToken(token);
        if (entryOpt.isEmpty()) {
            logger.warn("Promotion token not found: {}", token);
            return Optional.empty();
        }

        WaitingListEntry entry = entryOpt.get();

        // Check if token expired
        if (entry.getPromotionTokenExpiresAt() == null ||
            entry.getPromotionTokenExpiresAt().isBefore(LocalDateTime.now())) {
            logger.warn("Promotion token expired for user {} and slotId={}",
                    entry.getPresenterUsername(), entry.getSlotId());
            return Optional.empty();
        }

        Long slotId = entry.getSlotId();
        String username = entry.getPresenterUsername();

        // Get user details
        User user = userRepository.findByBguUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        // Load slot to check capacity again (may have changed)
        SeminarSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + slotId));

        // Check capacity before creating registration
        List<SeminarSlotRegistration> allRegistrations = registrationRepository.findByIdSlotId(slotId);
        long approvedCount = allRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED)
                .count();
        long pendingCount = allRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.PENDING)
                .count();
        int totalRegistrations = (int) (approvedCount + pendingCount);
        int capacity = slot.getCapacity() != null ? slot.getCapacity() : 0;

        // Check PhD capacity requirement
        int phdWeight = appConfigService.getIntegerConfig("phd.capacity.weight", 2);
        int userWeight = (user.getDegree() == User.Degree.PhD) ? phdWeight : 1;
        int effectiveUsage = 0;
        for (SeminarSlotRegistration reg : allRegistrations) {
            if (reg.getApprovalStatus() == ApprovalStatus.APPROVED ||
                reg.getApprovalStatus() == ApprovalStatus.PENDING) {
                effectiveUsage += (reg.getDegree() == User.Degree.PhD) ? phdWeight : 1;
            }
        }

        if (effectiveUsage + userWeight > capacity && capacity > 0) {
            logger.warn("Cannot confirm promotion - slot {} no longer has capacity for {} (capacity: {}, usage: {}, needed: {})",
                    slotId, username, capacity, effectiveUsage, userWeight);
            databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTION_FAILED",
                    String.format("User %s confirmed promotion but slot %d is now full", username, slotId),
                    username);
            return Optional.empty();
        }

        // PhD/MSc exclusivity check for promotion confirmation
        List<SeminarSlotRegistration> activeRegistrations = allRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED ||
                              reg.getApprovalStatus() == ApprovalStatus.PENDING)
                .collect(java.util.stream.Collectors.toList());

        boolean existingPhd = activeRegistrations.stream()
                .anyMatch(reg -> User.Degree.PhD == reg.getDegree());
        boolean existingMsc = activeRegistrations.stream()
                .anyMatch(reg -> User.Degree.MSc == reg.getDegree());

        // If PhD already registered, slot is locked
        if (existingPhd) {
            logger.warn("Cannot confirm promotion - slot {} has PhD presenter (exclusive)", slotId);
            databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTION_FAILED",
                    String.format("User %s confirmed promotion but slot %d is locked by PhD", username, slotId),
                    username);
            return Optional.empty();
        }

        // If user is PhD but MSc already registered, PhD can't be promoted
        if (user.getDegree() == User.Degree.PhD && existingMsc) {
            logger.warn("Cannot confirm promotion - PhD user {} blocked by existing MSc in slot {}",
                    username, slotId);
            databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTION_FAILED",
                    String.format("PhD user %s cannot be promoted - slot %d has MSc presenters", username, slotId),
                    username);
            return Optional.empty();
        }

        // Check if user already registered
        if (registrationRepository.existsByIdSlotIdAndIdPresenterUsername(slotId, username)) {
            logger.warn("User {} already registered for slot {} during promotion confirmation", username, slotId);
            removeFromWaitingList(slotId, username, false);
            return Optional.empty();
        }

        // Store entry data before removing
        int position = entry.getPosition();
        String topic = entry.getTopic();
        String supervisorName = entry.getSupervisorName();
        String supervisorEmail = entry.getSupervisorEmail();

        // Remove from waiting list
        waitingListRepository.deleteBySlotIdAndPresenterUsername(slotId, username);
        waitingListRepository.decrementPositionsAfter(slotId, position);

        // Create PENDING registration
        SeminarSlotRegistration newRegistration = new SeminarSlotRegistration();
        newRegistration.setId(new SeminarSlotRegistrationId(slotId, username));
        newRegistration.setDegree(user.getDegree());
        newRegistration.setTopic(topic);
        newRegistration.setSeminarAbstract(user.getSeminarAbstract());
        newRegistration.setSupervisorName(supervisorName);
        newRegistration.setSupervisorEmail(supervisorEmail);
        newRegistration.setRegisteredAt(LocalDateTime.now());
        newRegistration.setApprovalStatus(ApprovalStatus.PENDING);

        // Generate approval token for supervisor (expiry from config, default 14 days)
        int expiryDays = appConfigService.getIntegerConfig("approval_token_expiry_days", 14);
        String approvalToken = UUID.randomUUID().toString();
        newRegistration.setApprovalToken(approvalToken);
        newRegistration.setApprovalTokenExpiresAt(LocalDateTime.now().plusDays(expiryDays));

        registrationRepository.save(newRegistration);

        // Log promotion confirmation
        logger.info("User {} confirmed promotion for slotId={} - PENDING registration created", username, slotId);
        databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTION_CONFIRMED",
                String.format("User %s confirmed promotion for slotId=%d - PENDING registration created", username, slotId),
                username);

        // Create promotion record
        WaitingListPromotion promotion = new WaitingListPromotion();
        promotion.setSlotId(slotId);
        promotion.setPresenterUsername(username);
        promotion.setRegistrationSlotId(slotId);
        promotion.setRegistrationPresenterUsername(username);
        promotion.setPromotedAt(LocalDateTime.now());
        promotion.setExpiresAt(LocalDateTime.now().plusDays(expiryDays));
        promotion.setStatus(WaitingListPromotion.PromotionStatus.PENDING);
        waitingListPromotionRepository.save(promotion);

        // After promoting this user, check if there's room for more promotions
        // (e.g., MSc only takes 1/3 capacity, so more people can be promoted)
        // This is done asynchronously to avoid blocking the confirmation response
        try {
            boolean morePromotions = offerPromotionToNextFromWaitingList(slotId);
            if (morePromotions) {
                logger.info("Offered promotion to next person in waiting list for slotId={} after {} confirmed",
                        slotId, username);
            }
        } catch (Exception e) {
            // Don't fail the confirmation if offering next promotion fails
            logger.error("Failed to offer promotion to next person after {} confirmed for slotId={}: {}",
                    username, slotId, e.getMessage());
        }

        return Optional.of(newRegistration);
    }

    /**
     * Decline promotion - user clicked "No thanks"
     * Removes from waiting list and offers to next person
     */
    @Transactional
    public void declinePromotion(String token) {
        Optional<WaitingListEntry> entryOpt = waitingListRepository.findByPromotionToken(token);
        if (entryOpt.isEmpty()) {
            logger.warn("Promotion token not found for decline: {}", token);
            return;
        }

        WaitingListEntry entry = entryOpt.get();
        Long slotId = entry.getSlotId();
        String username = entry.getPresenterUsername();

        logger.info("User {} declined promotion for slotId={}", username, slotId);
        databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTION_DECLINED",
                String.format("User %s declined promotion for slotId=%d", username, slotId),
                username);

        // Remove from waiting list (don't send cancellation email - they chose to decline)
        removeFromWaitingList(slotId, username, false);

        // Offer to next person in line
        offerPromotionToNextFromWaitingList(slotId);
    }

    /**
     * Handle expired promotion offers - called by scheduler
     * Removes expired entries from waiting list and offers to next person
     */
    @Transactional
    public void processExpiredPromotionOffers() {
        List<WaitingListEntry> expired = waitingListRepository.findExpiredPromotionOffers(LocalDateTime.now());

        for (WaitingListEntry entry : expired) {
            Long slotId = entry.getSlotId();
            String username = entry.getPresenterUsername();

            logger.info("Promotion offer expired for user {} and slotId={}", username, slotId);
            databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTION_EXPIRED",
                    String.format("Promotion offer expired for user %s and slotId=%d - removing from waiting list",
                            username, slotId),
                    username);

            // Remove from waiting list
            removeFromWaitingList(slotId, username, false);

            // Offer to next person in line
            offerPromotionToNextFromWaitingList(slotId);
        }
    }

    /**
     * Send email to user offering promotion from waiting list
     */
    private void sendPromotionOfferEmail(WaitingListEntry entry, SeminarSlot slot, String token) {
        // Get user email
        User user = userRepository.findByBguUsername(entry.getPresenterUsername()).orElse(null);
        if (user == null || user.getEmail() == null) {
            logger.error("Cannot send promotion offer email - user not found or no email: {}",
                    entry.getPresenterUsername());
            return;
        }

        String studentEmail = user.getEmail();
        String studentName = user.getFirstName() != null && user.getLastName() != null
                ? user.getFirstName() + " " + user.getLastName()
                : entry.getPresenterUsername();

        String confirmUrl = serverBaseUrl + "/api/waiting-list/confirm?token=" + token;
        String declineUrl = serverBaseUrl + "/api/waiting-list/decline?token=" + token;

        String subject = "SemScan - A Presentation Slot is Now Available!";
        String htmlContent = generatePromotionOfferEmailHtml(entry, slot, studentName, confirmUrl, declineUrl);

        boolean sent = mailService.sendHtmlEmail(studentEmail, subject, htmlContent);
        if (sent) {
            logger.info("Promotion offer email sent to {} for slotId={}", studentEmail, entry.getSlotId());
            databaseLoggerService.logBusinessEvent("EMAIL_PROMOTION_OFFER_SENT",
                    String.format("Promotion offer email sent to %s for slotId=%d", studentEmail, entry.getSlotId()),
                    entry.getPresenterUsername());
        } else {
            logger.error("Failed to send promotion offer email to {}", studentEmail);
        }
    }

    /**
     * Generate HTML email for promotion offer
     */
    private String generatePromotionOfferEmailHtml(WaitingListEntry entry, SeminarSlot slot,
            String studentName, String confirmUrl, String declineUrl) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background-color: #4CAF50; color: white; padding: 20px; text-align: center;">
                        <h1 style="margin: 0;">Good News! A Slot is Available</h1>
                    </div>
                    <div style="padding: 20px; background-color: #f9f9f9;">
                        <p>Dear %s,</p>

                        <div style="background-color: #e8f5e9; padding: 15px; margin: 15px 0; border-left: 4px solid #4CAF50;">
                            <h3 style="margin-top: 0; color: #2e7d32;">🎉 A Presentation Slot Has Opened Up!</h3>
                            <p>You were on the waiting list for this slot, and now there's space available for you.</p>
                        </div>

                        <div style="background-color: #e3f2fd; padding: 15px; margin: 15px 0; border-left: 4px solid #2196F3;">
                            <h3>Slot Details:</h3>
                            <p><strong>Date:</strong> %s</p>
                            <p><strong>Time:</strong> %s - %s</p>
                            <p><strong>Location:</strong> %s, Room %s</p>
                            <p><strong>Your Topic:</strong> %s</p>
                        </div>

                        <div style="background-color: #fff3e0; padding: 15px; margin: 15px 0; border-left: 4px solid #ff9800;">
                            <p><strong>⏰ You have 24 hours to respond.</strong></p>
                            <p>If you don't respond in time, the slot will be offered to the next person on the waiting list.</p>
                        </div>

                        <p><strong>Do you still want to register for this slot?</strong></p>

                        <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin: 20px 0;">
                            <tr>
                                <td style="padding-right: 15px;">
                                    <a href="%s" style="display: inline-block; padding: 15px 30px; background-color: #4CAF50; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 16px;">Yes, Register Me!</a>
                                </td>
                                <td>
                                    <a href="%s" style="display: inline-block; padding: 15px 30px; background-color: #9e9e9e; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 16px;">No Thanks</a>
                                </td>
                            </tr>
                        </table>

                        <p style="color: #666; font-size: 12px;">If the buttons don't work, copy and paste these links:</p>
                        <p style="color: #666; font-size: 12px;">Yes: %s</p>
                        <p style="color: #666; font-size: 12px;">No: %s</p>
                    </div>
                    <div style="text-align: center; padding: 20px; color: #666; font-size: 12px;">
                        <p>This is an automated message from SemScan Attendance System.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
            studentName,
            slot.getSlotDate() != null ? DATE_FORMAT.format(slot.getSlotDate()) : "N/A",
            slot.getStartTime() != null ? TIME_FORMAT.format(slot.getStartTime()) : "N/A",
            slot.getEndTime() != null ? TIME_FORMAT.format(slot.getEndTime()) : "N/A",
            slot.getBuilding() != null ? slot.getBuilding() : "N/A",
            slot.getRoom() != null ? slot.getRoom() : "N/A",
            entry.getTopic() != null ? entry.getTopic() : "Not specified",
            confirmUrl,
            declineUrl,
            confirmUrl,
            declineUrl
        );
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
     * Get all waiting list entries for a user (across all slots)
     */
    public List<WaitingListEntry> getWaitingListEntriesForUser(String presenterUsername) {
        return waitingListRepository.findByPresenterUsername(presenterUsername);
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

