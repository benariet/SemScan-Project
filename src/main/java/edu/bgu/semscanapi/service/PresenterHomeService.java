package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.dto.PresenterHomeResponse;
import edu.bgu.semscanapi.dto.PresenterHomeResponse.AttendancePanel;
import edu.bgu.semscanapi.dto.PresenterHomeResponse.MySlotSummary;
import edu.bgu.semscanapi.dto.PresenterHomeResponse.PresenterSummary;
import edu.bgu.semscanapi.dto.PresenterHomeResponse.RegisteredPresenter;
import edu.bgu.semscanapi.dto.PresenterHomeResponse.SlotCard;
import edu.bgu.semscanapi.dto.PresenterHomeResponse.SlotState;
import edu.bgu.semscanapi.dto.PresenterOpenAttendanceResponse;
import edu.bgu.semscanapi.dto.PresenterSlotRegistrationRequest;
import edu.bgu.semscanapi.dto.PresenterSlotRegistrationResponse;
import edu.bgu.semscanapi.entity.ApprovalStatus;
import edu.bgu.semscanapi.entity.Seminar;
import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.SeminarSlotRegistrationId;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.entity.WaitingListEntry;
import edu.bgu.semscanapi.entity.WaitingListPromotion;
import edu.bgu.semscanapi.repository.SeminarRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.repository.WaitingListPromotionRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PresenterHomeService {

    private static final Logger logger = LoggerUtil.getLogger(PresenterHomeService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final ZoneId ISRAEL_TIMEZONE = ZoneId.of("Asia/Jerusalem");

    private final UserRepository userRepository;
    private final SeminarSlotRepository seminarSlotRepository;
    private final SeminarSlotRegistrationRepository registrationRepository;
    private final SeminarRepository seminarRepository;
    private final SessionRepository sessionRepository;
    private final GlobalConfig globalConfig;
    private final EmailService emailService;
    private final DatabaseLoggerService databaseLoggerService;
    private final RegistrationApprovalService approvalService;
    private final WaitingListService waitingListService;
    private final MailService mailService;
    private final AppConfigService appConfigService;
    private final WaitingListPromotionRepository waitingListPromotionRepository;
    private final EmailQueueService emailQueueService;

    public PresenterHomeService(UserRepository userRepository,
                                SeminarSlotRepository seminarSlotRepository,
                                SeminarSlotRegistrationRepository registrationRepository,
                                SeminarRepository seminarRepository,
                                SessionRepository sessionRepository,
                                GlobalConfig globalConfig,
                                EmailService emailService,
                                DatabaseLoggerService databaseLoggerService,
                                RegistrationApprovalService approvalService,
                                WaitingListService waitingListService,
                                MailService mailService,
                                AppConfigService appConfigService,
                                WaitingListPromotionRepository waitingListPromotionRepository,
                                EmailQueueService emailQueueService) {
        this.userRepository = userRepository;
        this.seminarSlotRepository = seminarSlotRepository;
        this.registrationRepository = registrationRepository;
        this.seminarRepository = seminarRepository;
        this.sessionRepository = sessionRepository;
        this.globalConfig = globalConfig;
        this.emailService = emailService;
        this.databaseLoggerService = databaseLoggerService;
        this.approvalService = approvalService;
        this.waitingListService = waitingListService;
        this.mailService = mailService;
        this.appConfigService = appConfigService;
        this.waitingListPromotionRepository = waitingListPromotionRepository;
        this.emailQueueService = emailQueueService;
    }

    /**
     * Calculate effective capacity usage for a slot.
     * PhD students count as 2 toward capacity, MSc students count as 1.
     * This is configurable via app_config (phd.capacity.weight, default=2).
     */
    private int calculateEffectiveCapacityUsage(List<SeminarSlotRegistration> registrations) {
        int phdWeight = appConfigService.getIntegerConfig("phd.capacity.weight", 2);
        int effectiveUsage = 0;
        for (SeminarSlotRegistration reg : registrations) {
            if (reg.getDegree() == User.Degree.PhD) {
                effectiveUsage += phdWeight;
            } else {
                effectiveUsage += 1;
            }
        }
        return effectiveUsage;
    }

    /**
     * Determine slot state based on effective capacity usage.
     * Delegates to determineState since the logic is the same.
     */
    private SlotState determineStateByEffectiveUsage(Integer capacity, int effectiveUsage) {
        return determineState(capacity, effectiveUsage);
    }

    @Transactional(readOnly = true)
    public PresenterHomeResponse getPresenterHome(String presenterUsernameParam) {
        String normalizedUsername = normalizeUsername(presenterUsernameParam);
        logger.debug("Assembling presenter home response for presenterUsername={}", normalizedUsername);

        User presenter = findPresenterByUsername(normalizedUsername);

        String presenterUsername = normalizeUsername(presenter.getBguUsername());
        boolean hasValidUsername = presenterUsername != null;

        List<SeminarSlot> slots = loadUpcomingSlots();
        Map<Long, SeminarSlot> slotsById = slots.stream()
                .collect(Collectors.toMap(SeminarSlot::getSlotId, slot -> slot));

        List<Long> slotIds = new ArrayList<>(slotsById.keySet());
        Map<Long, List<SeminarSlotRegistration>> registrationsBySlot = loadRegistrationsBySlot(slotIds);

        List<SeminarSlotRegistration> presenterRegistrations = hasValidUsername
                ? registrationRepository.findByIdPresenterUsername(presenterUsername)
                : List.of();

        Map<String, User> registeredUsers = loadRegisteredUsers(registrationsBySlot);

        SeminarSlot mySlotEntity = hasValidUsername
                ? findNextSlotForPresenter(presenterRegistrations, slotsById)
                : null;
        List<SeminarSlotRegistration> mySlotRegistrations = mySlotEntity != null
                ? registrationsBySlot.getOrDefault(mySlotEntity.getSlotId(), List.of())
                : List.of();

        MySlotSummary mySlotSummary = buildMySlotSummary(mySlotEntity, mySlotRegistrations, registeredUsers);

        PresenterHomeResponse response = new PresenterHomeResponse();
        response.setPresenter(buildPresenterSummary(presenter, presenterUsername, !presenterRegistrations.isEmpty()));
        response.setMySlot(mySlotSummary);
        response.setSlotCatalog(buildSlotCatalog(slots,
                registrationsBySlot,
                registeredUsers,
                presenterUsername,
                presenter.getDegree(),
                presenterRegistrations));
        response.setAttendance(buildAttendancePanel(mySlotEntity, presenterUsername));

        logger.debug("Presenter home response ready for presenterUsername={}", normalizedUsername);
        return response;
    }

    @Transactional(timeout = 60, isolation = Isolation.READ_COMMITTED) 
    // READ_COMMITTED reduces lock contention vs REPEATABLE_READ
    // Timeout set to 60 seconds to handle multiple database queries and potential locks
    // This method performs: user lookup, registration checks, slot loading, capacity checks, and saves
    public PresenterSlotRegistrationResponse registerForSlot(String presenterUsernameParam,
                                                             Long slotId,
                                                             PresenterSlotRegistrationRequest request) {
        String normalizedUsername = normalizeUsername(presenterUsernameParam);
        logger.info("Registering presenter {} to slot {}", normalizedUsername, slotId);
        
        String presenterUsername = null;
        User presenter = null;
        User.Degree presenterDegree = null;
        SeminarSlot slot = null;
        SeminarSlotRegistration registration = null;
        
        try {
            presenter = findPresenterByUsername(normalizedUsername);

            presenterUsername = normalizeUsername(presenter.getBguUsername());
            if (presenterUsername == null) {
                String errorMsg = "Presenter is missing BGU username";
                databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, normalizedUsername, 
                    String.format("slotId=%s,reason=MISSING_USERNAME", slotId));
                return new PresenterSlotRegistrationResponse(false, errorMsg, "MISSING_USERNAME");
            }

            // CRITICAL: Check if presenter already registered FIRST (before any slot reads)
            // This avoids unnecessary slot locks if already registered
            if (registrationRepository.existsByIdSlotIdAndIdPresenterUsername(slotId, presenterUsername)) {
                logger.info("Presenter {} already registered to slot {}", presenterUsername, slotId);
                databaseLoggerService.logBusinessEvent("SLOT_REGISTRATION_ALREADY", 
                    String.format("Presenter %s already registered to slot %s", presenterUsername, slotId), presenterUsername);
                return new PresenterSlotRegistrationResponse(true, "Already registered for this slot", "ALREADY_IN_SLOT");
            }

            // Determine presenter's degree (PhD or MSc) to apply appropriate registration limits
            presenterDegree = presenter.getDegree() != null ? presenter.getDegree() : User.Degree.MSc;
            
            // Retrieve all registrations for this user across all slots to check registration limits
            List<SeminarSlotRegistration> userRegistrations = registrationRepository.findByIdPresenterUsername(presenterUsername);
            
            // Count approved and pending registrations: PhD max 1 approved + 1 pending, MSc max 1 approved + 2 pending
            long approvedCount = userRegistrations.stream()
                    .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED)
                    .count();
            LocalDateTime now = nowIsrael();
            long pendingCount = userRegistrations.stream()
                    .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.PENDING)
                    .filter(reg -> reg.getApprovalTokenExpiresAt() == null || now.isBefore(reg.getApprovalTokenExpiresAt()))
                    .count();
            
            // Enforce registration limits
            if (presenterDegree == User.Degree.PhD) {
                // PhD: Max 1 approved, Max 1 pending
                if (approvedCount >= 1) {
                    String errorMsg = "PhD students can have at most 1 approved registration";
                    databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, presenterUsername, 
                        String.format("slotId=%s,reason=REGISTRATION_LIMIT_EXCEEDED,approvedCount=%d", slotId, approvedCount));
                    return new PresenterSlotRegistrationResponse(false, errorMsg, "REGISTRATION_LIMIT_EXCEEDED");
                }
                if (pendingCount >= 1) {
                    String errorMsg = "PhD students can have at most 1 pending approval";
                    databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, presenterUsername, 
                        String.format("slotId=%s,reason=PENDING_LIMIT_EXCEEDED,pendingCount=%d", slotId, pendingCount));
                    return new PresenterSlotRegistrationResponse(false, errorMsg, "PENDING_LIMIT_EXCEEDED");
                }
            } else {
                // MSc: Max 1 approved, Max 2 pending
                if (approvedCount >= 1) {
                    String errorMsg = "MSc students can have at most 1 approved registration";
                    databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, presenterUsername, 
                        String.format("slotId=%s,reason=REGISTRATION_LIMIT_EXCEEDED,approvedCount=%d", slotId, approvedCount));
                    return new PresenterSlotRegistrationResponse(false, errorMsg, "REGISTRATION_LIMIT_EXCEEDED");
                }
                if (pendingCount >= 2) {
                    String errorMsg = "MSc students can have at most 2 pending approvals";
                    databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, presenterUsername, 
                        String.format("slotId=%s,reason=PENDING_LIMIT_EXCEEDED,pendingCount=%d", slotId, pendingCount));
                    return new PresenterSlotRegistrationResponse(false, errorMsg, "PENDING_LIMIT_EXCEEDED");
                }
            }
            
            // NOTE: Registration limits are already enforced above (PhD: max 1 approved + 1 pending, MSc: max 1 approved + 2 pending)
            // CRITICAL BUSINESS RULE: Being on a waiting list for another slot does NOT prevent registration for other slots
            // Users can register for available/empty slots even if they're on a waiting list for a different slot
            // Only actual registrations (approved/pending) count towards the limits, NOT waiting list entries
            // The "only one waiting list" restriction applies only when JOINING a waiting list, not when registering
            
            // Load ALL registrations for this slot (APPROVED + PENDING) to check capacity
            // CRITICAL: Both approved AND pending registrations count towards capacity
            // This prevents over-registration when multiple users register simultaneously
            List<SeminarSlotRegistration> allSlotRegistrations = registrationRepository.findByIdSlotId(slotId);
            List<SeminarSlotRegistration> approvedRegistrations = allSlotRegistrations.stream()
                    .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED)
                    .collect(Collectors.toList());
            List<SeminarSlotRegistration> pendingRegistrations = allSlotRegistrations.stream()
                    .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.PENDING)
                    .filter(reg -> reg.getApprovalTokenExpiresAt() == null || now.isBefore(reg.getApprovalTokenExpiresAt()))
                    .collect(Collectors.toList());
            
            boolean existingPhd = approvedRegistrations.stream()
                    .anyMatch(reg -> User.Degree.PhD == reg.getDegree());
            
            // PhD exclusivity rule: if slot already has a PhD presenter, no other presenters can register
            if (existingPhd) {
                String errorMsg = "Slot already has a PhD presenter";
                databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, presenterUsername, 
                    String.format("slotId=%s,reason=PHD_BLOCKED", slotId));
                return new PresenterSlotRegistrationResponse(false, "Slot locked by PhD presenter", "SLOT_LOCKED");
            }
            
            // Read slot entity only when needed (for capacity check and status update) to minimize row lock duration
            slot = seminarSlotRepository.findById(slotId)
                    .orElseThrow(() -> {
                        String errorMsg = "Slot not found: " + slotId;
                        databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, normalizedUsername, 
                            String.format("slotId=%s,reason=SLOT_NOT_FOUND", slotId));
                        return new IllegalArgumentException(errorMsg);
                    });
            
            // Check slot capacity using effective capacity (PhD counts as 2, MSc counts as 1)
            // CRITICAL FIX: Count BOTH approved AND pending registrations against capacity
            int capacity = slot.getCapacity() != null ? slot.getCapacity() : 0;
            
            // Combine approved and pending for effective usage calculation
            List<SeminarSlotRegistration> allActiveRegistrations = new ArrayList<>();
            allActiveRegistrations.addAll(approvedRegistrations);
            allActiveRegistrations.addAll(pendingRegistrations);
            int effectiveUsage = calculateEffectiveCapacityUsage(allActiveRegistrations);
            
            // Calculate what the new total would be if this registration is added
            int phdWeight = appConfigService.getIntegerConfig("phd.capacity.weight", 2);
            int newRegistrationWeight = (presenterDegree == User.Degree.PhD) ? phdWeight : 1;
            int projectedUsage = effectiveUsage + newRegistrationWeight;
            
            if (projectedUsage > capacity) {
                updateSlotStatus(slot, SlotState.FULL);
                String errorMsg = String.format("Slot is already full (capacity: %d, effective usage: %d, your weight: %d)", 
                        capacity, effectiveUsage, newRegistrationWeight);
                logger.warn("Slot {} is full - capacity: {}, effective usage: {}, projected: {}", 
                        slotId, capacity, effectiveUsage, projectedUsage);
                databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, presenterUsername, 
                    String.format("slotId=%s,reason=SLOT_FULL,capacity=%d,effectiveUsage=%d,projectedUsage=%d,degree=%s", 
                            slotId, capacity, effectiveUsage, projectedUsage, presenterDegree));
                return new PresenterSlotRegistrationResponse(false, "Slot is already full", "SLOT_FULL");
            }

            // Get supervisor from User entity (preferred) or fall back to request parameters (backward compatibility)
            String finalSupervisorName = null;
            String finalSupervisorEmail = null;
            
            if (presenter.getSupervisor() != null) {
                // Use supervisor from User entity (new way - after account setup)
                finalSupervisorName = presenter.getSupervisor().getName();
                finalSupervisorEmail = presenter.getSupervisor().getEmail();
                logger.info("Using supervisor from User entity: name={}, email={}", finalSupervisorName, finalSupervisorEmail);
            } else if (request.getSupervisorName() != null && !request.getSupervisorName().trim().isEmpty() && 
                       request.getSupervisorEmail() != null && !request.getSupervisorEmail().trim().isEmpty()) {
                // Fall back to request parameters (backward compatibility for users who haven't completed account setup)
                finalSupervisorName = normalizeSupervisorName(request.getSupervisorName());
                finalSupervisorEmail = request.getSupervisorEmail().trim();
                logger.warn("User {} has no supervisor linked. Using supervisor from request (backward compatibility): name={}, email={}. " +
                        "User should complete account setup via /api/v1/auth/setup/{}", 
                        presenterUsername, finalSupervisorName, finalSupervisorEmail, presenterUsername);
            } else {
                // No supervisor available - this is required
                String errorMsg = String.format("Supervisor information is required. User %s has no supervisor linked and none provided in request. " +
                        "Please complete account setup via /api/v1/auth/setup/%s", presenterUsername, presenterUsername);
                logger.error("{} - slotId={}", errorMsg, slotId);
                databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, presenterUsername, 
                    String.format("slotId=%s,reason=NO_SUPERVISOR", slotId));
                return new PresenterSlotRegistrationResponse(false, errorMsg, "NO_SUPERVISOR");
            }

            registration = new SeminarSlotRegistration();
            registration.setId(new SeminarSlotRegistrationId(slotId, presenterUsername));
            registration.setDegree(presenter.getDegree());
            registration.setTopic(normalizeTopic(request.getTopic()));
            // Use abstract from request if provided, otherwise fall back to user profile
            String finalAbstract = (request.getSeminarAbstract() != null && !request.getSeminarAbstract().trim().isEmpty())
                    ? request.getSeminarAbstract()
                    : presenter.getSeminarAbstract();
            registration.setSeminarAbstract(finalAbstract);
            registration.setSupervisorName(finalSupervisorName);
            registration.setSupervisorEmail(finalSupervisorEmail);
            registration.setRegisteredAt(LocalDateTime.now());
            registration.setApprovalStatus(ApprovalStatus.PENDING); // Default to PENDING

            registrationRepository.save(registration);

            // OPTION 1: "Safety Net" Logic - If user was on waiting list for THIS slot, remove them
            // (They got a spot, so they don't need to be on the waiting list anymore)
            // BUT: Keep them on waiting lists for OTHER slots (they can have a backup option)
            if (waitingListService.isOnWaitingList(slotId, presenterUsername)) {
                try {
                    waitingListService.removeFromWaitingList(slotId, presenterUsername);
                    logger.info("Removed user {} from waiting list for slot {} after successful registration", 
                            presenterUsername, slotId);
                    databaseLoggerService.logBusinessEvent("WAITING_LIST_AUTO_REMOVED_ON_REGISTRATION",
                            String.format("User %s automatically removed from waiting list for slot %d after registering", 
                                    presenterUsername, slotId),
                            presenterUsername);
                } catch (Exception e) {
                    // Don't fail registration if waiting list removal fails
                    logger.warn("Failed to remove user {} from waiting list for slot {} after registration: {}", 
                            presenterUsername, slotId, e.getMessage());
                }
            }

            // PhD registration: slot becomes FULL immediately (PhD takes entire slot, no other presenters allowed)
            // Non-PhD registration: update slot state based on effective capacity usage (PhD=2, MSc=1)
            boolean slotNowHasPhd = presenterDegree == User.Degree.PhD;
            int newEffectiveUsage = effectiveUsage + newRegistrationWeight; // Include this new registration
            SlotState updatedState = slotNowHasPhd
                    ? SlotState.FULL
                    : determineStateByEffectiveUsage(slot.getCapacity(), newEffectiveUsage);

            if (slotNowHasPhd) {
                // PhD registration: close attendance window (PhD takes whole slot, no attendance needed)
                slot.setAttendanceOpenedAt(null);
                slot.setAttendanceClosesAt(null);
                slot.setAttendanceOpenedBy(null);
                closeLegacySessionIfExists(slot);
                slot.setLegacySessionId(null);
            }

            updateSlotStatus(slot, updatedState);
            seminarSlotRepository.save(slot);
            
            // Transaction commits here: all database operations complete, locks released immediately to prevent contention

            logger.info("Presenter {} successfully registered to slot {}", presenterUsername, slotId);
            
            // Log successful registration to app_logs table for audit trail and analytics
            databaseLoggerService.logBusinessEvent("SLOT_REGISTRATION_SUCCESS", 
                String.format("Presenter %s registered to slot %s (degree: %s)", presenterUsername, slotId, presenterDegree), 
                presenterUsername);
            
        } catch (org.springframework.dao.DataAccessException e) {
            // Handle database-level errors: deadlocks, timeouts, constraint violations, connection failures
            String errorMsg = String.format("Database error during slot registration: %s", e.getMessage());
            logger.error("Database error registering presenter {} to slot {}", presenterUsername, slotId, e);
            databaseLoggerService.logError("SLOT_REGISTRATION_DB_ERROR", errorMsg, e, 
                presenterUsername != null ? presenterUsername : normalizedUsername, 
                String.format("slotId=%s,exceptionType=%s", slotId, e.getClass().getName()));
            throw e; // Re-throw to trigger transaction rollback
        } catch (Exception e) {
            // Handle unexpected application errors: null pointers, illegal arguments, business logic exceptions
            String errorMsg = String.format("Unexpected error during slot registration: %s", e.getMessage());
            logger.error("Unexpected error registering presenter {} to slot {}", presenterUsername, slotId, e);
            databaseLoggerService.logError("SLOT_REGISTRATION_ERROR", errorMsg, e, 
                presenterUsername != null ? presenterUsername : normalizedUsername, 
                String.format("slotId=%s,exceptionType=%s", slotId, e.getClass().getName()));
            throw e;
        }
        
        // Send supervisor approval email OUTSIDE transaction to prevent holding database locks during SMTP operations
        logger.info("📧 EMAIL SENDING FLOW START - presenter: {}, slotId: {}", presenterUsername, slotId);
        databaseLoggerService.logAction("INFO", "EMAIL_SENDING_FLOW_START",
                String.format("Starting email sending flow for presenter %s and slot %s", presenterUsername, slotId),
                presenterUsername, String.format("slotId=%d", slotId));
        
        // CRITICAL: Wait a moment for transaction to commit before refreshing registration
        // This ensures the registration is fully persisted in the database
        try {
            Thread.sleep(100); // Small delay to ensure transaction commit
            logger.info("📧 Waited 100ms for transaction commit before refreshing registration");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("📧 Thread interrupted while waiting for transaction commit");
        }
        
        // CRITICAL: Refresh registration from database to ensure all fields are persisted before sending email
        if (registration != null && registration.getId() != null) {
            logger.info("📧 Refreshing registration from database - registrationId: {}", registration.getId());
            databaseLoggerService.logAction("INFO", "EMAIL_REGISTRATION_REFRESH_ATTEMPT",
                    String.format("Refreshing registration from database for presenter %s and slot %s", presenterUsername, slotId),
                    presenterUsername, String.format("slotId=%d,registrationId=%s", slotId, registration.getId()));
            
            // Try multiple times in case transaction hasn't committed yet
            Optional<SeminarSlotRegistration> refreshedRegistration = Optional.empty();
            for (int attempt = 1; attempt <= 3; attempt++) {
                refreshedRegistration = registrationRepository.findById(registration.getId());
                if (refreshedRegistration.isPresent()) {
                    logger.info("📧 Registration found on attempt {}", attempt);
                    break;
                } else {
                    logger.warn("📧 Registration not found on attempt {}, waiting 200ms before retry...", attempt);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            if (refreshedRegistration.isPresent()) {
                registration = refreshedRegistration.get();
                String dbEmail = registration.getSupervisorEmail() != null ? registration.getSupervisorEmail() : "null";
                logger.info("📧 Registration refreshed - supervisorEmail in DB: {}", dbEmail);
                databaseLoggerService.logAction("INFO", "EMAIL_REGISTRATION_REFRESHED",
                        String.format("Registration refreshed from database - supervisorEmail: %s", dbEmail),
                        presenterUsername, String.format("slotId=%d,supervisorEmail=%s", slotId, dbEmail));
            } else {
                logger.error("📧 CRITICAL: Registration not found in database after 3 attempts! registrationId: {}", registration.getId());
                databaseLoggerService.logError("EMAIL_REGISTRATION_NOT_FOUND_AFTER_SAVE",
                        String.format("Registration not found in database after save for presenter %s and slot %s (tried 3 times)",
                                presenterUsername, slotId),
                        null, presenterUsername, String.format("slotId=%d,registrationId=%s,attempts=3", slotId, registration.getId()));
                // Continue anyway - use the registration object we have
                logger.warn("📧 Continuing with original registration object (may not have supervisorEmail persisted)");
            }
        } else {
            logger.error("📧 CRITICAL: Registration is null or has no ID! registration: {}, id: {}", 
                    registration != null ? "not null" : "null",
                    registration != null && registration.getId() != null ? registration.getId() : "null");
            databaseLoggerService.logError("EMAIL_REGISTRATION_NULL_OR_NO_ID",
                    String.format("Registration is null or has no ID for presenter %s and slot %s",
                            presenterUsername, slotId),
                    null, presenterUsername, String.format("slotId=%d", slotId));
        }
        
        // Get supervisor email: prefer User entity, then registration, then request (backward compatibility)
        String supervisorEmail = null;
        String supervisorName = null;
        
        if (presenter.getSupervisor() != null) {
            // Use supervisor from User entity (new way - after account setup)
            supervisorEmail = presenter.getSupervisor().getEmail();
            supervisorName = presenter.getSupervisor().getName();
            logger.info("📧 Using supervisor from User entity: name={}, email={}", supervisorName, supervisorEmail);
            databaseLoggerService.logAction("INFO", "EMAIL_SUPERVISOR_EMAIL_FROM_USER",
                    String.format("Using supervisor from User entity: %s (%s)", supervisorName, supervisorEmail),
                    presenterUsername, String.format("slotId=%d,supervisorEmail=%s", slotId, supervisorEmail));
        } else if (registration != null && registration.getSupervisorEmail() != null && !registration.getSupervisorEmail().trim().isEmpty()) {
            // Fall back to registration (backward compatibility)
            supervisorEmail = registration.getSupervisorEmail();
            supervisorName = registration.getSupervisorName();
            logger.info("📧 Using supervisor from registration object: name={}, email={}", supervisorName, supervisorEmail);
            databaseLoggerService.logAction("INFO", "EMAIL_SUPERVISOR_EMAIL_FROM_REGISTRATION",
                    String.format("Using supervisor from registration: %s (%s)", supervisorName, supervisorEmail),
                    presenterUsername, String.format("slotId=%d,registrationEmail=%s", slotId, supervisorEmail));
        } else if (request.getSupervisorEmail() != null && !request.getSupervisorEmail().trim().isEmpty()) {
            // Fall back to request (backward compatibility)
            supervisorEmail = request.getSupervisorEmail();
            supervisorName = request.getSupervisorName();
            logger.warn("📧 Using supervisor from request (backward compatibility): name={}, email={}. " +
                    "User should complete account setup via /api/v1/auth/setup/{}", 
                    supervisorName, supervisorEmail, presenterUsername);
            databaseLoggerService.logAction("INFO", "EMAIL_SUPERVISOR_EMAIL_FROM_REQUEST",
                    String.format("Using supervisor from request (backward compatibility): %s (%s)", supervisorName, supervisorEmail),
                    presenterUsername, String.format("slotId=%d,requestEmail=%s", slotId, supervisorEmail));
        }
        
        // CRITICAL: Validate supervisor email BEFORE proceeding with registration
        // If supervisor email is invalid or missing, REJECT the registration with a clear error
        EmailQueueService.EmailValidationResult emailValidation = emailQueueService.validateSupervisorEmail(
            supervisorEmail, presenterUsername);

        if (!emailValidation.isValid()) {
            // Registration REJECTED due to invalid/missing supervisor email
            String errorMsg = emailValidation.getErrorMessage();
            String errorCode = emailValidation.getErrorCode();

            logger.error("📧 ❌ REGISTRATION REJECTED - Supervisor email validation failed: {}", errorMsg);
            databaseLoggerService.logError("EMAIL_SUPERVISOR_VALIDATION_REJECTED",
                String.format("Registration rejected for %s on slot %d - %s",
                    presenterUsername, slotId, errorMsg),
                null, presenterUsername, String.format("slotId=%d,errorCode=%s", slotId, errorCode));

            // Return error to user with clear instructions
            return new PresenterSlotRegistrationResponse(false, errorMsg, errorCode);
        }

        // Supervisor email validated successfully
        supervisorEmail = supervisorEmail.trim();
        logger.info("📧 Supervisor email validated successfully: {} - proceeding with registration", supervisorEmail);
        
        if (supervisorEmail != null && !supervisorEmail.trim().isEmpty()) {
            try {
                // Ensure registration has supervisorEmail set before sending
                if (registration != null && (registration.getSupervisorEmail() == null || registration.getSupervisorEmail().trim().isEmpty())) {
                    logger.info("📧 Setting supervisorEmail on registration object before sending");
                    registration.setSupervisorEmail(supervisorEmail);
                    registrationRepository.save(registration);
                    logger.info("📧 Updated registration with supervisor email: {}", supervisorEmail);
                    databaseLoggerService.logAction("INFO", "EMAIL_REGISTRATION_EMAIL_UPDATED",
                            String.format("Updated registration with supervisor email %s for presenter %s and slot %s",
                                    supervisorEmail, presenterUsername, slotId),
                            presenterUsername, String.format("slotId=%d,supervisorEmail=%s", slotId, supervisorEmail));
                }
                
                if (registration == null) {
                    logger.error("📧 ❌ CRITICAL: Registration is null when trying to send email!");
                    databaseLoggerService.logError("EMAIL_REGISTRATION_NULL_EMAIL_SEND",
                            String.format("Registration is null when trying to send email for presenter %s and slot %s",
                                    presenterUsername, slotId),
                            null, presenterUsername, String.format("slotId=%d", slotId));
                } else {
                    logger.info("📧 Calling approvalService.sendApprovalEmail() for presenter {} and slot {}", presenterUsername, slotId);
                    databaseLoggerService.logAction("INFO", "EMAIL_CALLING_APPROVAL_SERVICE",
                            String.format("Calling approvalService.sendApprovalEmail() for presenter %s and slot %s",
                                    presenterUsername, slotId),
                            presenterUsername, String.format("slotId=%d,supervisorEmail=%s", slotId, supervisorEmail));
                    
                    boolean emailSent = approvalService.sendApprovalEmail(registration);
                    logger.info("📧 [PresenterHomeService] sendApprovalEmail() returned: {} for presenter {} and slot {} to supervisor {}", 
                            emailSent, presenterUsername, slotId, supervisorEmail);
                    databaseLoggerService.logAction("INFO", "EMAIL_APPROVAL_SERVICE_RETURNED",
                            String.format("sendApprovalEmail() returned: %s for presenter %s and slot %s", emailSent, presenterUsername, slotId),
                            presenterUsername, String.format("slotId=%d,supervisorEmail=%s,sent=%s", slotId, supervisorEmail, emailSent));
                    
                    if (emailSent) {
                        logger.info("📧 ✅ Approval email sent successfully for presenter {} and slot {} to supervisor {}", 
                                presenterUsername, slotId, supervisorEmail);
                        databaseLoggerService.logBusinessEvent("EMAIL_REGISTRATION_APPROVAL_EMAIL_SENT",
                                String.format("Approval email sent successfully for presenter %s and slot %s to supervisor %s",
                                        presenterUsername, slotId, supervisorEmail),
                                presenterUsername);
                    } else {
                        logger.error("📧 ❌ Approval email FAILED to send for presenter {} and slot {} to supervisor {} - check logs for details", 
                                presenterUsername, slotId, supervisorEmail);
                        databaseLoggerService.logError("EMAIL_REGISTRATION_APPROVAL_EMAIL_FAILED",
                                String.format("Approval email FAILED to send for presenter %s and slot %s to supervisor %s",
                                        presenterUsername, slotId, supervisorEmail),
                                null, presenterUsername,
                                String.format("slotId=%d,supervisorEmail=%s", slotId, supervisorEmail));
                    }
                }
            } catch (Exception e) {
                logger.error("📧 ❌ Failed to send approval email for presenter {} and slot {} to supervisor {} - Exception: {}",
                        presenterUsername, slotId, supervisorEmail, e.getMessage(), e);
                databaseLoggerService.logError("EMAIL_REGISTRATION_APPROVAL_EMAIL_EXCEPTION",
                        String.format("Exception sending approval email for presenter %s and slot %s: %s",
                                presenterUsername, slotId, e.getMessage()),
                        e, presenterUsername,
                        String.format("slotId=%d,supervisorEmail=%s,exceptionType=%s,exceptionMessage=%s,stackTrace=%s",
                                slotId, supervisorEmail, e.getClass().getSimpleName(), e.getMessage(),
                                e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : "no_stack_trace"));
                // Don't fail registration if email fails
            }
        } else {
            String warningMsg = String.format("No supervisor email provided for presenter %s and slot %s - email not sent. Request email: %s, Registration email: %s",
                    presenterUsername, slotId, 
                    request.getSupervisorEmail() != null ? request.getSupervisorEmail() : "null",
                    registration != null && registration.getSupervisorEmail() != null ? registration.getSupervisorEmail() : "null");
            logger.warn("📧 ⚠️ {}", warningMsg);
            databaseLoggerService.logError("EMAIL_REGISTRATION_NO_SUPERVISOR_EMAIL", warningMsg, null,
                    presenterUsername, String.format("slotId=%d,requestEmail=%s,registrationEmail=%s",
                            slotId, 
                            request.getSupervisorEmail() != null ? request.getSupervisorEmail() : "null",
                            registration != null && registration.getSupervisorEmail() != null ? registration.getSupervisorEmail() : "null"));
        }
        
        logger.info("📧 EMAIL SENDING FLOW END - presenter: {}, slotId: {}", presenterUsername, slotId);

        // Map approval status for mobile API compatibility: PENDING -> PENDING_APPROVAL (mobile expects this format)
        String approvalStatusStr = "PENDING_APPROVAL";
        
        // Generate unique registration ID for mobile: hash of slotId+username (workaround for composite key limitation)
        // Mobile app expects single Long ID, but database uses composite key (slotId, username)
        Long registrationId = (long) (slotId.toString() + presenterUsername).hashCode();
        if (registrationId < 0) {
            registrationId = Math.abs(registrationId);
        }
        
        // Get approval token from registration (may be null if email wasn't sent yet)
        String approvalToken = null;
        if (registration != null) {
            approvalToken = registration.getApprovalToken();
        }
        
        return new PresenterSlotRegistrationResponse(
                true, 
                "Registration submitted. Waiting for supervisor approval.", 
                "PENDING_APPROVAL",
                registrationId,
                approvalToken,
                approvalStatusStr
        );
    }

    @Transactional(readOnly = true)
    public List<PresenterHomeResponse.SlotCard> getAllSlots() {
        logger.debug("Fetching slot catalog without presenter context");
        List<SeminarSlot> slots = loadUpcomingSlots();
        Map<Long, SeminarSlot> slotsById = slots.stream()
                .collect(Collectors.toMap(SeminarSlot::getSlotId, slot -> slot));

        List<Long> slotIds = new ArrayList<>(slotsById.keySet());
        Map<Long, List<SeminarSlotRegistration>> registrationsBySlot = loadRegistrationsBySlot(slotIds);
        Map<String, User> registeredUsers = loadRegisteredUsers(registrationsBySlot);

        return buildSlotCatalog(slots,
                registrationsBySlot,
                registeredUsers,
                null,
                null,
                Collections.emptyList());
    }

    @Transactional
    public PresenterSlotRegistrationResponse unregisterFromSlot(String presenterUsernameParam, Long slotId) {
        String normalizedUsername = normalizeUsername(presenterUsernameParam);
        logger.info("Unregistering presenter {} from slot {}", normalizedUsername, slotId);

        User presenter = findPresenterByUsername(normalizedUsername);
        String presenterUsername = normalizeUsername(presenter.getBguUsername());
        if (presenterUsername == null) {
            return new PresenterSlotRegistrationResponse(false, "Presenter is missing BGU username", "MISSING_USERNAME");
        }

        SeminarSlot slot = seminarSlotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + slotId));

        SeminarSlotRegistrationId id = new SeminarSlotRegistrationId(slotId, presenterUsername);
        Optional<SeminarSlotRegistration> existing = registrationRepository.findById(id);
        if (existing.isEmpty()) {
            return new PresenterSlotRegistrationResponse(false, "Presenter is not registered for this slot", "NOT_REGISTERED");
        }

        SeminarSlotRegistration registration = existing.get();
        
        // Send cancellation email to supervisor if registration was APPROVED
        // Do this BEFORE deleting the registration so we can access supervisor info
        if (registration.getApprovalStatus() == ApprovalStatus.APPROVED) {
            String supervisorEmail = registration.getSupervisorEmail();
            if (supervisorEmail != null && !supervisorEmail.trim().isEmpty()) {
                try {
                    sendRegistrationCancellationEmail(registration, slot, presenter);
                    logger.info("Registration cancellation email sent to supervisor {} for user {} and slotId={}",
                            supervisorEmail, presenterUsername, slotId);
                } catch (Exception e) {
                    logger.error("Failed to send registration cancellation email to supervisor {} for user {} and slotId={}",
                            supervisorEmail, presenterUsername, slotId, e);
                    // Don't fail cancellation if email fails
                }
            } else {
                logger.debug("No supervisor email for approved registration - skipping cancellation email. slotId={}, presenter={}",
                        slotId, presenterUsername);
            }
        }

        // Capture approval status before deletion for logging purposes
        String approvalStatusStr = registration.getApprovalStatus() != null ? registration.getApprovalStatus().toString() : "UNKNOWN";
        
        registrationRepository.delete(registration);

        // Count remaining registrations for this slot to determine if slot should be marked as AVAILABLE again
        List<SeminarSlotRegistration> remainingRegistrations = registrationRepository.findByIdSlotId(slotId);
        long remaining = remainingRegistrations.size();
        
        // Count only APPROVED registrations for capacity check (PENDING may be declined, DECLINED/EXPIRED don't take capacity)
        long approvedRemaining = remainingRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED)
                .count();
        
        // Log cancellation to app_log
        databaseLoggerService.logBusinessEvent("SLOT_REGISTRATION_CANCELLED",
                String.format("User %s cancelled registration for slotId=%d (approval status: %s, remaining: %d, approved: %d)",
                        presenterUsername, slotId, approvalStatusStr, remaining, approvedRemaining),
                presenterUsername);
        
        // Calculate effective capacity usage (PhD=2, MSc=1) for state determination
        List<SeminarSlotRegistration> activeRemainingRegs = remainingRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED || 
                               reg.getApprovalStatus() == ApprovalStatus.PENDING)
                .collect(java.util.stream.Collectors.toList());
        int effectiveUsageRemaining = calculateEffectiveCapacityUsage(activeRemainingRegs);

        // Determine new state based on effective capacity usage
        SlotState newState = determineState(slot.getCapacity(), effectiveUsageRemaining);
        updateSlotStatus(slot, newState);

        String openedBy = normalizeUsername(slot.getAttendanceOpenedBy());
        if (Objects.equals(presenterUsername, openedBy) || remaining == 0) {
            slot.setAttendanceOpenedAt(null);
            slot.setAttendanceClosesAt(null);
            slot.setAttendanceOpenedBy(null);
            closeLegacySessionIfExists(slot);
            slot.setLegacySessionId(null);
            if (remaining == 0) {
                slot.setLegacySeminarId(null);
            }
        }
        seminarSlotRepository.save(slot);

        // CRITICAL: If an APPROVED registration was cancelled and slot now has capacity, promote next from waiting list
        // Only count APPROVED registrations for capacity check - PENDING may be declined, DECLINED/EXPIRED don't take capacity
        if (registration.getApprovalStatus() == ApprovalStatus.APPROVED && approvedRemaining < slot.getCapacity()) {
            try {
                Optional<WaitingListEntry> promotedEntry = promoteFromWaitingListAfterCancellation(slotId, slot);
                if (promotedEntry.isPresent()) {
                    logger.info("Successfully promoted user {} from waiting list for slot {} after cancellation", 
                            promotedEntry.get().getPresenterUsername(), slotId);
                    
                    // CRITICAL FIX: Recalculate and update slot state AFTER promotion
                    // New PENDING registrations were created, so state needs to be updated
                    List<SeminarSlotRegistration> updatedRegistrations = registrationRepository.findByIdSlotId(slotId);
                    int effectiveUsage = calculateEffectiveCapacityUsage(updatedRegistrations.stream()
                            .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED || 
                                           reg.getApprovalStatus() == ApprovalStatus.PENDING)
                            .collect(java.util.stream.Collectors.toList()));
                    SlotState stateAfterPromotion = determineState(slot.getCapacity(), effectiveUsage);
                    updateSlotStatus(slot, stateAfterPromotion);
                    seminarSlotRepository.save(slot);
                    logger.info("Updated slot {} state to {} after promotion (effectiveUsage={})", 
                            slotId, stateAfterPromotion, effectiveUsage);
                }
            } catch (Exception e) {
                logger.error("Failed to promote from waiting list after cancellation for slot {}: {}", slotId, e.getMessage(), e);
                // Don't fail cancellation if promotion fails
            }
        }

        logger.info("Presenter {} removed from slot {}. Remaining presenters: {}", presenterUsername, slotId, remaining);
        return new PresenterSlotRegistrationResponse(true, "Registration cancelled", "UNREGISTERED", false);
    }

    /**
     * Send registration cancellation email to supervisor when an approved registration is canceled
     */
    private void sendRegistrationCancellationEmail(SeminarSlotRegistration registration, SeminarSlot slot, User presenter) {
        String supervisorEmail = registration.getSupervisorEmail();
        String supervisorName = registration.getSupervisorName();
        
        if (supervisorEmail == null || supervisorEmail.trim().isEmpty()) {
            logger.debug("No supervisor email for registration cancellation - skipping email. slotId={}, presenter={}",
                    registration.getId().getSlotId(), registration.getId().getPresenterUsername());
            return;
        }

        // Format student name
        String studentName = presenter != null && presenter.getFirstName() != null && presenter.getLastName() != null
                ? presenter.getFirstName() + " " + presenter.getLastName()
                : registration.getId().getPresenterUsername();

        // Generate email content
        String subject = "SemScan - Registration Cancellation: Presentation Slot";
        String htmlContent = generateRegistrationCancellationEmailHtml(registration, slot, studentName, supervisorName);

        // Send email
        boolean sent = mailService.sendHtmlEmail(supervisorEmail, subject, htmlContent);
        if (sent) {
            logger.info("Registration cancellation email sent to supervisor {} for user {} and slotId={}",
                    supervisorEmail, registration.getId().getPresenterUsername(), registration.getId().getSlotId());
            databaseLoggerService.logBusinessEvent("EMAIL_REGISTRATION_CANCELLATION_EMAIL_SENT",
                    String.format("Registration cancellation email sent to supervisor %s for user %s and slotId=%d",
                            supervisorEmail, registration.getId().getPresenterUsername(), registration.getId().getSlotId()),
                    registration.getId().getPresenterUsername());
        } else {
            logger.error("Failed to send registration cancellation email to supervisor {} for user {} and slotId={}",
                    supervisorEmail, registration.getId().getPresenterUsername(), registration.getId().getSlotId());
            databaseLoggerService.logError("EMAIL_REGISTRATION_CANCELLATION_EMAIL_FAILED",
                    String.format("Failed to send registration cancellation email to supervisor %s for user %s and slotId=%d",
                            supervisorEmail, registration.getId().getPresenterUsername(), registration.getId().getSlotId()),
                    null, registration.getId().getPresenterUsername(),
                    String.format("slotId=%d,supervisorEmail=%s", registration.getId().getSlotId(), supervisorEmail));
        }
    }

    /**
     * Generate HTML email content for registration cancellation notification
     */
    private String generateRegistrationCancellationEmailHtml(SeminarSlotRegistration registration, SeminarSlot slot, 
                                                             String studentName, String supervisorName) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #dc3545; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background-color: #f9f9f9; }
                    .info-box { background-color: #f8d7da; padding: 15px; margin: 15px 0; border-left: 4px solid #dc3545; }
                    .details-box { background-color: #e3f2fd; padding: 15px; margin: 15px 0; border-left: 4px solid #2196F3; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>SemScan - Registration Cancellation</h1>
                    </div>
                    <div class="content">
                        <p>Dear %s,</p>
                        
                        <div class="info-box">
                            <h3 style="margin-top: 0; color: #721c24;">⚠️ Registration Cancelled</h3>
                            <p>Your student has cancelled their approved registration for this presentation slot.</p>
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
                        
                        <p><strong>Note:</strong> The slot is now available for other students.</p>
                        
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
            registration.getId().getPresenterUsername(),
            registration.getDegree() != null ? registration.getDegree().toString() : "N/A",
            registration.getTopic() != null ? registration.getTopic() : "Not specified",
            slot.getSlotDate() != null ? DATE_FORMAT.format(slot.getSlotDate()) : "N/A",
            slot.getStartTime() != null ? TIME_FORMAT.format(slot.getStartTime()) : "N/A",
            slot.getEndTime() != null ? TIME_FORMAT.format(slot.getEndTime()) : "N/A",
            slot.getBuilding() != null ? slot.getBuilding() : "N/A",
            slot.getRoom() != null ? slot.getRoom() : "N/A"
        );
    }

    /**
     * Promote users from waiting list after a registration is cancelled
     * Promotes as many people as available capacity allows (PhD counts as 2)
     * Creates PENDING registrations and sends student confirmation emails
     * Can be called from other services (e.g., when student declines promotion)
     */
    @Transactional
    public Optional<WaitingListEntry> promoteFromWaitingListAfterCancellation(Long slotId, SeminarSlot slot) {
        logger.info("Attempting to promote from waiting list for slot {} after cancellation", slotId);

        // Get waiting list
        List<WaitingListEntry> waitingList = waitingListService.getWaitingList(slotId);
        if (waitingList.isEmpty()) {
            logger.debug("No one on waiting list for slot {}", slotId);
            return Optional.empty();
        }

        // Calculate current effective usage (PhD=2, MSc=1)
        List<SeminarSlotRegistration> allRegistrations = registrationRepository.findByIdSlotId(slotId);
        List<SeminarSlotRegistration> activeRegistrations = allRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED ||
                               reg.getApprovalStatus() == ApprovalStatus.PENDING)
                .collect(Collectors.toList());
        int currentEffectiveUsage = calculateEffectiveCapacityUsage(activeRegistrations);
        int capacity = slot.getCapacity() != null ? slot.getCapacity() : 0;
        int availableCapacity = capacity - currentEffectiveUsage;

        if (availableCapacity <= 0) {
            logger.info("Cannot promote from waiting list - slot {} is full (capacity: {}, effectiveUsage: {})",
                    slotId, capacity, currentEffectiveUsage);
            return Optional.empty();
        }

        logger.info("Slot {} has {} available capacity, attempting to promote from waiting list", slotId, availableCapacity);

        int phdWeight = appConfigService.getIntegerConfig("phd.capacity.weight", 2);
        Integer approvalWindowHours = appConfigService != null
                ? appConfigService.getIntegerConfig("waiting_list_approval_window_hours", 24)
                : 24;

        WaitingListEntry firstPromoted = null;
        int usedCapacity = 0;

        // Promote as many people as capacity allows
        for (WaitingListEntry entry : waitingList) {
            String promotedUsername = entry.getPresenterUsername();

            // Get user details to determine their degree/weight
            Optional<User> userOpt = userRepository.findByBguUsername(promotedUsername);
            if (userOpt.isEmpty()) {
                logger.warn("User {} not found, skipping", promotedUsername);
                continue;
            }
            User promotedUser = userOpt.get();

            // Calculate weight for this user
            int userWeight = (promotedUser.getDegree() == User.Degree.PhD) ? phdWeight : 1;

            // Check if we have enough capacity for this user
            if (usedCapacity + userWeight > availableCapacity) {
                logger.info("Not enough capacity for user {} (needs {}, available {}), stopping promotion",
                        promotedUsername, userWeight, availableCapacity - usedCapacity);
                break;
            }

            // Check if user already registered (safety check)
            if (registrationRepository.existsByIdSlotIdAndIdPresenterUsername(slotId, promotedUsername)) {
                logger.warn("User {} already registered for slot {} - removing from waiting list without promotion",
                        promotedUsername, slotId);
                waitingListService.removeFromWaitingList(slotId, promotedUsername);
                continue;
            }

            // Create PENDING registration
            SeminarSlotRegistration newRegistration = new SeminarSlotRegistration();
            newRegistration.setId(new SeminarSlotRegistrationId(slotId, promotedUsername));
            newRegistration.setDegree(promotedUser.getDegree());
            newRegistration.setTopic(entry.getTopic());
            // Get abstract from user profile (not waiting list entry)
            newRegistration.setSeminarAbstract(promotedUser.getSeminarAbstract());
            newRegistration.setSupervisorName(entry.getSupervisorName());
            newRegistration.setSupervisorEmail(entry.getSupervisorEmail());
            newRegistration.setRegisteredAt(LocalDateTime.now());
            newRegistration.setApprovalStatus(ApprovalStatus.PENDING);
            registrationRepository.save(newRegistration);

            // Remove from waiting list (skip cancellation email since this is a promotion)
            waitingListService.removeFromWaitingList(slotId, promotedUsername, false);

            // Create WaitingListPromotion record
            LocalDateTime promotedAt = LocalDateTime.now();
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

            usedCapacity += userWeight;

            logger.info("Promoted user {} from waiting list for slot {} (weight: {}, total used: {}/{})",
                    promotedUsername, slotId, userWeight, usedCapacity, availableCapacity);
            databaseLoggerService.logBusinessEvent("WAITING_LIST_PROMOTED_AFTER_CANCELLATION",
                    String.format("User %s promoted from waiting list for slot %d (degree: %s, weight: %d, expires at %s)",
                            promotedUsername, slotId, promotedUser.getDegree(), userWeight, expiresAt),
                    promotedUsername);

            // Send student confirmation email
            try {
                approvalService.sendStudentConfirmationEmail(newRegistration);
                logger.info("Student confirmation email sent to promoted user {} in slot {}", promotedUsername, slotId);
            } catch (Exception e) {
                logger.error("Failed to send student confirmation email for promoted user {} in slot {}: {}",
                        promotedUsername, slotId, e.getMessage(), e);
            }

            if (firstPromoted == null) {
                firstPromoted = entry;
            }
        }

        if (firstPromoted != null) {
            logger.info("Promoted {} capacity worth of users from waiting list for slot {}", usedCapacity, slotId);
        }

        return Optional.ofNullable(firstPromoted);
    }

    /**
     * Send supervisor notification email

    /**
     * Send supervisor notification email for an existing registration
     *
     * @param presenterUsernameParam The presenter's username
     * @param slotId The slot ID
     * @param supervisorEmail The supervisor's email address (from request)
     * @param supervisorName The supervisor's name (from request, optional)
     * @return Response indicating success or failure
     */
    @Transactional
    public PresenterSlotRegistrationResponse sendSupervisorEmail(String presenterUsernameParam, Long slotId, 
                                                                 String supervisorEmail, String supervisorName) {
        String normalizedUsername = normalizeUsername(presenterUsernameParam);
        logger.info("Sending supervisor email for presenter {} and slot {} to supervisor {}", normalizedUsername, slotId, supervisorEmail);

        User presenter = findPresenterByUsername(normalizedUsername);
        String presenterUsername = normalizeUsername(presenter.getBguUsername());
        if (presenterUsername == null) {
            return new PresenterSlotRegistrationResponse(false, "Presenter is missing BGU username", "MISSING_USERNAME", false);
        }

        if (supervisorEmail == null || supervisorEmail.trim().isEmpty()) {
            return new PresenterSlotRegistrationResponse(false, "Supervisor email is required", "NO_SUPERVISOR_EMAIL", false);
        }

        SeminarSlot slot = seminarSlotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + slotId));

        SeminarSlotRegistrationId id = new SeminarSlotRegistrationId(slotId, presenterUsername);
        SeminarSlotRegistration registration = registrationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found for presenter " + presenterUsername + " and slot " + slotId));

        try {
            String presenterName = formatName(presenter);
            if (presenterName == null || presenterName.trim().isEmpty()) {
                presenterName = presenterUsername;
            }
            User.Degree presenterDegree = presenter.getDegree() != null ? presenter.getDegree() : User.Degree.MSc;
            String presenterDegreeStr = presenterDegree.name();
            String slotDateStr = slot.getSlotDate() != null ? DATE_FORMAT.format(slot.getSlotDate()) : "N/A";
            String slotStartTimeStr = slot.getStartTime() != null ? TIME_FORMAT.format(slot.getStartTime()) : "N/A";
            String slotEndTimeStr = slot.getEndTime() != null ? TIME_FORMAT.format(slot.getEndTime()) : "N/A";
            String topic = registration.getTopic();

            EmailService.EmailResult emailResult = emailService.sendSupervisorNotificationEmail(
                    supervisorEmail,
                    supervisorName,
                    presenterName,
                    presenterUsername,
                    presenterDegreeStr,
                    slotDateStr,
                    slotStartTimeStr,
                    slotEndTimeStr,
                    slot.getBuilding(),
                    slot.getRoom(),
                    topic
            );

            if (emailResult.isSuccess()) {
                // Optionally save supervisor details to registration for future reference
                registration.setSupervisorEmail(supervisorEmail);
                registration.setSupervisorName(normalizeSupervisorName(supervisorName));
                registrationRepository.save(registration);
                
                logger.info("Supervisor email sent successfully for presenter {} and slot {}", presenterUsername, slotId);
                return new PresenterSlotRegistrationResponse(true, "Supervisor email sent successfully", "EMAIL_SENT", true);
            } else {
                // Return specific error message from EmailResult
                String errorCode = emailResult.getErrorCode();
                String errorMessage = emailResult.getErrorMessage();
                logger.warn("Failed to send supervisor email for presenter {} and slot {}: {} (Code: {})", 
                    presenterUsername, slotId, errorMessage, errorCode);
                
                // Map error codes to response codes
                String responseCode = "EMAIL_ERROR";
                if ("EMAIL_AUTH_FAILED".equals(errorCode)) {
                    responseCode = "EMAIL_AUTH_FAILED";
                } else if ("EMAIL_NOT_CONFIGURED".equals(errorCode)) {
                    responseCode = "EMAIL_NOT_CONFIGURED";
                } else if ("EMAIL_SEND_FAILED".equals(errorCode)) {
                    responseCode = "EMAIL_SEND_FAILED";
                }
                
                return new PresenterSlotRegistrationResponse(false, errorMessage, responseCode, true);
            }
        } catch (Exception e) {
            String errorMsg = "Unexpected error sending supervisor email: " + e.getMessage();
            logger.error("Unexpected error sending supervisor email for presenter {} and slot {}", presenterUsername, slotId, e);
            return new PresenterSlotRegistrationResponse(false, errorMsg, "EMAIL_ERROR", true);
        }
    }

    @Transactional
    public PresenterOpenAttendanceResponse openAttendance(String presenterUsernameParam, Long slotId) {
        String normalizedUsername = normalizeUsername(presenterUsernameParam);
        logger.info("Opening attendance for presenter {} on slot {}", normalizedUsername, slotId);

        User presenter = findPresenterByUsername(normalizedUsername);
        String presenterUsername = normalizeUsername(presenter.getBguUsername());
        if (presenterUsername == null) {
            String errorMsg = "Presenter is missing BGU username";
            databaseLoggerService.logError("ATTENDANCE_OPEN_FAILED", errorMsg, null, normalizedUsername, 
                String.format("slotId=%s,reason=MISSING_USERNAME", slotId));
            return new PresenterOpenAttendanceResponse(false, errorMsg, "MISSING_USERNAME", null, null, null, null, null);
        }

        SeminarSlot slot = seminarSlotRepository.findById(slotId)
                .orElseThrow(() -> {
                    String errorMsg = "Slot not found: " + slotId;
                    databaseLoggerService.logError("ATTENDANCE_OPEN_FAILED", errorMsg, null, presenterUsername, 
                        String.format("slotId=%s,reason=SLOT_NOT_FOUND", slotId));
                    return new IllegalArgumentException(errorMsg);
                });

        if (!registrationRepository.existsByIdSlotIdAndIdPresenterUsername(slotId, presenterUsername)) {
            String errorMsg = "Presenter is not registered for this slot";
            databaseLoggerService.logError("ATTENDANCE_OPEN_FAILED", errorMsg, null, presenterUsername, 
                String.format("slotId=%s,reason=NOT_REGISTERED", slotId));
            return new PresenterOpenAttendanceResponse(false, errorMsg, "NOT_REGISTERED", null, null, null, null, null);
        }

        // CRITICAL: Get current time in Israel timezone to match slot times
        // Slot times are stored as DATE + TIME (no timezone) and interpreted as Israel time
        LocalDateTime now = nowIsrael();
        LocalDateTime start = toSlotStart(slot);
        if (start == null) {
            String errorMsg = "Slot start time not configured";
            databaseLoggerService.logError("ATTENDANCE_OPEN_FAILED", errorMsg, null, presenterUsername, 
                String.format("slotId=%s,reason=NO_SCHEDULE", slotId));
            return new PresenterOpenAttendanceResponse(false, errorMsg, "NO_SCHEDULE", null, null, null, null, null);
        }

        LocalDateTime end = toSlotEnd(slot);
        
        // Get configurable window values from AppConfigService (read from MOBILE or BOTH, default: 30 minutes before)
        Integer windowBeforeMinutes = appConfigService != null 
                ? appConfigService.getIntegerConfig("presenter_slot_open_window_before_minutes", 30)
                : 30;
        Integer windowAfterMinutes = appConfigService != null 
                ? appConfigService.getIntegerConfig("presenter_slot_open_window_after_minutes", 15)
                : 15;
        
        LocalDateTime openWindow = start.minusMinutes(windowBeforeMinutes);
        LocalDateTime closeWindow = end != null ? end.plusMinutes(windowAfterMinutes) : null;
        
        // Log time comparison for debugging - CRITICAL for diagnosing timezone issues
        long minutesUntilOpen = Duration.between(now, openWindow).toMinutes();
        String systemTimezone = java.util.TimeZone.getDefault().getID();
        LocalDateTime nowUtc = LocalDateTime.now(); // For logging comparison
        logger.info("⏰⏰⏰ CRITICAL TIME CHECK - Slot: {}, System Timezone: {}, Now (UTC): {}, Now (Israel): {}, Slot Start: {}, Open Window: {} ({} min before), Minutes until open: {}, Can open: {}", 
            slotId, systemTimezone, nowUtc, now, start, openWindow, windowBeforeMinutes, minutesUntilOpen, !now.isBefore(openWindow));
        
        // Check if too early (before configured window before start)
        // Allow opening during the slot time (from windowBeforeMinutes before start until slot ends + windowAfterMinutes, or indefinitely if no end time)
        if (now.isBefore(openWindow)) {
            String openWindowStr = DATE_TIME_FORMAT.format(openWindow);
            // Format date and time in user-friendly format
            java.time.format.DateTimeFormatter dateFormat = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            java.time.format.DateTimeFormatter timeFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
            String dateStr = dateFormat.format(openWindow);
            String timeStr = timeFormat.format(openWindow);
            long minutesDiff = Duration.between(now, openWindow).toMinutes();
            String errorMsg = String.format("Cannot open session yet. You will be able to open it on %s at %s",
                    dateStr, timeStr);
            logger.error("❌ TOO_EARLY - Slot: {}, Now: {}, OpenWindow: {}, Start: {}, Minutes difference: {} (negative = too early)", 
                slotId, now, openWindow, start, minutesDiff);
            databaseLoggerService.logError("ATTENDANCE_OPEN_FAILED", errorMsg, null, presenterUsername, 
                String.format("slotId=%s,reason=TOO_EARLY,now=%s,openWindow=%s,start=%s,minutesDiff=%d", 
                    slotId, DATE_TIME_FORMAT.format(now), openWindowStr, DATE_TIME_FORMAT.format(start), minutesDiff));
            return new PresenterOpenAttendanceResponse(false, errorMsg, "TOO_EARLY", null, openWindowStr, null, null, null);
        }
        
        logger.info("✅ Time check passed - Slot: {} can be opened (Now: {} >= OpenWindow: {})", slotId, now, openWindow);
        
        // Check if too late (after slot ends + windowAfterMinutes) - only if slot has an end time
        if (closeWindow != null && now.isAfter(closeWindow)) {
            String errorMsg = String.format("Too late: Attendance window closed. Slot ended at %s, attendance can be opened up to %d minutes after (closed at %s)", 
                    DATE_TIME_FORMAT.format(end), windowAfterMinutes, DATE_TIME_FORMAT.format(closeWindow));
            databaseLoggerService.logError("ATTENDANCE_OPEN_FAILED", errorMsg, null, presenterUsername, 
                String.format("slotId=%s,reason=TOO_LATE,now=%s,end=%s,closeWindow=%s,windowAfterMinutes=%d", 
                    slotId, DATE_TIME_FORMAT.format(now), DATE_TIME_FORMAT.format(end), DATE_TIME_FORMAT.format(closeWindow), windowAfterMinutes));
            return new PresenterOpenAttendanceResponse(false, errorMsg, "TOO_LATE", null, null, null, null, null);
        }
        
        // Allow opening if: now >= openWindow (windowBeforeMinutes before start) AND (closeWindow is null OR now <= closeWindow)
        // This covers both: windowBeforeMinutes before start AND during the slot time + windowAfterMinutes

        // CRITICAL: Get fresh list of open sessions to ensure we see all current sessions
        // This is important to catch sessions that were just opened by other presenters
        List<Session> allOpenSessions = sessionRepository.findOpenSessions();
        logger.info("Found {} open sessions total when presenter {} tries to open slot {}", 
            allOpenSessions.size(), presenterUsername, slotId);
        if (logger.isDebugEnabled()) {
            allOpenSessions.forEach(s -> logger.debug("Open session: ID={}, SeminarID={}, StartTime={}, Location={}", 
                s.getSessionId(), s.getSeminarId(), s.getStartTime(), s.getLocation()));
        }
        
        // Find THIS presenter's open session for THIS slot (if any)
        Session thisPresenterOpenSession = findPresenterOpenSessionForSlot(presenterUsername, slotId, slot);

        // CRITICAL: Check if presenter already has a CLOSED session for this slot - block re-opening
        if (thisPresenterOpenSession == null) {
            // No open session - check if there's a closed one
            Session closedSession = findPresenterClosedSessionForSlot(presenterUsername, slotId, slot);
            if (closedSession != null) {
                String closedAt = closedSession.getEndTime() != null
                        ? DATE_TIME_FORMAT.format(closedSession.getEndTime())
                        : "earlier";
                String errorMsg = String.format("Attendance session has already ended at %s. You cannot re-open attendance for this slot.", closedAt);
                logger.warn("Presenter {} tried to re-open attendance for slot {} but session {} is already closed",
                    presenterUsername, slotId, closedSession.getSessionId());
                databaseLoggerService.logError("ATTENDANCE_REOPEN_BLOCKED", errorMsg, null, presenterUsername,
                    String.format("slotId=%s,closedSessionId=%s", slotId, closedSession.getSessionId()));
                return new PresenterOpenAttendanceResponse(false, errorMsg, "SESSION_CLOSED", null, null, null, null, null);
            }
        }

        // Check if THIS presenter already has an OPEN session for THIS slot
        // If yes, check if the window has expired - if so, block; otherwise return existing
        if (thisPresenterOpenSession != null) {
            // Calculate session close time from session start time
            Integer sessionCloseDurationMinutes = appConfigService != null
                    ? appConfigService.getIntegerConfig("presenter_close_session_duration_minutes", 15)
                    : 15;
            LocalDateTime sessionClosesAt = thisPresenterOpenSession.getStartTime() != null
                    ? thisPresenterOpenSession.getStartTime().plusMinutes(sessionCloseDurationMinutes)
                    : null;
            LocalDateTime sessionOpenedAt = thisPresenterOpenSession.getStartTime();

            // CRITICAL: Check if the session's time window has expired
            // If expired, close the old session and BLOCK re-opening
            if (sessionClosesAt != null && now.isAfter(sessionClosesAt)) {
                logger.info("Session {} time window has expired (closed at {}), closing session",
                    thisPresenterOpenSession.getSessionId(), sessionClosesAt);

                // Close the old session
                thisPresenterOpenSession.setStatus(Session.SessionStatus.CLOSED);
                thisPresenterOpenSession.setEndTime(sessionClosesAt);
                sessionRepository.save(thisPresenterOpenSession);
                databaseLoggerService.logSessionEvent("SESSION_AUTO_CLOSED_EXPIRED",
                    thisPresenterOpenSession.getSessionId(), thisPresenterOpenSession.getSeminarId(), presenterUsername);

                // Clear slot's reference to the closed session
                slot.setLegacySessionId(null);
                slot.setAttendanceOpenedAt(null);
                slot.setAttendanceClosesAt(null);
                slot.setAttendanceOpenedBy(null);
                seminarSlotRepository.save(slot);

                // BLOCK re-opening - attendance window has closed
                String errorMsg = String.format("Attendance session has already ended at %s. You cannot re-open attendance for this slot.",
                    DATE_TIME_FORMAT.format(sessionClosesAt));
                databaseLoggerService.logError("ATTENDANCE_REOPEN_BLOCKED", errorMsg, null, presenterUsername,
                    String.format("slotId=%s,sessionId=%s,closedAt=%s", slotId, thisPresenterOpenSession.getSessionId(), sessionClosesAt));
                return new PresenterOpenAttendanceResponse(false, errorMsg, "SESSION_CLOSED", null, null, null, null, null);
            } else {
                // Session is still valid, return it
                String qrUrl = buildQrUrl(thisPresenterOpenSession.getSessionId());
                String payload = buildQrPayload(thisPresenterOpenSession.getSessionId());

                // Link slot to session if not already linked
                if (slot.getLegacySessionId() == null || !slot.getLegacySessionId().equals(thisPresenterOpenSession.getSessionId())) {
                    logger.info("Linking slot {} to existing session {} (was: {})", slotId,
                        thisPresenterOpenSession.getSessionId(), slot.getLegacySessionId());
                    slot.setLegacySessionId(thisPresenterOpenSession.getSessionId());
                    slot.setAttendanceOpenedAt(sessionOpenedAt);
                    slot.setAttendanceClosesAt(sessionClosesAt);
                    slot.setAttendanceOpenedBy(presenterUsername);
                    seminarSlotRepository.save(slot);
                    databaseLoggerService.logBusinessEvent("SLOT_SESSION_LINKED_EXISTING",
                        String.format("Slot %s linked to existing session %s", slotId, thisPresenterOpenSession.getSessionId()),
                        presenterUsername);
                }

                logger.info("Presenter {} already has an OPEN session {} for slot {}, returning existing session",
                    presenterUsername, thisPresenterOpenSession.getSessionId(), slotId);
                databaseLoggerService.logBusinessEvent("ATTENDANCE_ALREADY_OPEN",
                    String.format("Presenter %s already has open session %s for slot %s", presenterUsername,
                        thisPresenterOpenSession.getSessionId(), slotId), presenterUsername);
                return new PresenterOpenAttendanceResponse(true,
                        "Attendance already open",
                        "ALREADY_OPEN",
                        qrUrl,
                        sessionOpenedAt != null ? DATE_TIME_FORMAT.format(sessionOpenedAt) : null,
                        sessionClosesAt != null ? DATE_TIME_FORMAT.format(sessionClosesAt) : null,
                        thisPresenterOpenSession.getSessionId(),
                        payload);
            }
        }
        
        // CRITICAL FIX: Check if ANY OTHER presenter has an OPEN session for this slot
        // If yes, return IN_PROGRESS error to block this presenter from opening a new session
        logger.info("Checking for other presenter's open sessions for slot {} (presenter: {})", slotId, presenterUsername);
        Session otherPresenterOpenSession = findOtherPresenterOpenSessionForSlot(presenterUsername, slotId, slot, allOpenSessions);
        if (otherPresenterOpenSession != null) {
            logger.warn("BLOCKING: Presenter {} cannot open session for slot {} - found other presenter's open session {}", 
                presenterUsername, slotId, otherPresenterOpenSession.getSessionId());
            // Get the other presenter's name for the error message
            String otherPresenterName = "another presenter";
            Optional<Seminar> otherSeminar = seminarRepository.findById(otherPresenterOpenSession.getSeminarId());
            if (otherSeminar.isPresent() && otherSeminar.get().getPresenterUsername() != null) {
                Optional<User> otherPresenter = userRepository.findByBguUsernameIgnoreCase(otherSeminar.get().getPresenterUsername());
                if (otherPresenter.isPresent()) {
                    otherPresenterName = otherPresenter.get().getFirstName() + " " + otherPresenter.get().getLastName();
                } else {
                    otherPresenterName = otherSeminar.get().getPresenterUsername();
                }
            }
            
            String errorMsg = String.format("Another presenter (%s) has an active session for this time slot. Please wait for that session to end before opening a new one.", otherPresenterName);
            logger.warn("Presenter {} cannot open session for slot {} - another presenter has an open session {}", 
                presenterUsername, slotId, otherPresenterOpenSession.getSessionId());
            databaseLoggerService.logError("ATTENDANCE_OPEN_BLOCKED", errorMsg, null, presenterUsername,
                String.format("slotId=%s,otherSessionId=%s,otherPresenter=%s", slotId, 
                    otherPresenterOpenSession.getSessionId(), otherSeminar.map(Seminar::getPresenterUsername).orElse("unknown")));
            return new PresenterOpenAttendanceResponse(false, errorMsg, "IN_PROGRESS", null, null, null, null, null);
        }
        
        // Clean up any closed session references in the slot
        if (slot.getLegacySessionId() != null) {
            Session slotReferencedSession = sessionRepository.findById(slot.getLegacySessionId()).orElse(null);
            if (slotReferencedSession != null && slotReferencedSession.getStatus() == Session.SessionStatus.CLOSED) {
                logger.info("Slot references CLOSED session {}, clearing slot attendance fields", slotReferencedSession.getSessionId());
                slot.setLegacySessionId(null);
                slot.setAttendanceOpenedAt(null);
                slot.setAttendanceClosesAt(null);
                slot.setAttendanceOpenedBy(null);
                seminarSlotRepository.save(slot);
            }
        }

        logger.info("No blocking sessions found. Proceeding to create new session for presenter {} on slot {}", presenterUsername, slotId);
        
        // Create a new session for this presenter
        Seminar legacySeminar = ensureLegacySeminar(slot, presenter);
        Session newSession = createLegacySession(slot, legacySeminar, presenterUsername);
        
        // CRITICAL: Verify session was created and saved
        if (newSession.getSessionId() == null) {
            String errorMsg = "Failed to create session - sessionId is null for presenter " + presenterUsername + " on slot " + slotId;
            logger.error(errorMsg);
            databaseLoggerService.logError("SESSION_CREATION_FAILED", errorMsg, null, presenterUsername, 
                String.format("slotId=%s,presenterUsername=%s", slotId, presenterUsername));
            throw new IllegalStateException("Failed to create session - session was not saved to database");
        }
        
        logger.info("Created new session {} with status {} for presenter {} on slot {}", 
            newSession.getSessionId(), newSession.getStatus(), presenterUsername, slotId);
        
        // Log session creation to database
        databaseLoggerService.logSessionEvent("SESSION_CREATED", newSession.getSessionId(), 
            legacySeminar.getSeminarId(), presenterUsername);
        
        slot.setLegacySeminarId(legacySeminar.getSeminarId());
        
        // CRITICAL: Update slot's session tracking fields
        // Since we already checked that no other presenter has an open session (and returned IN_PROGRESS if they did),
        // we can safely update the slot to track this new session
        // Use the 'now' variable already defined at the start of the method
        slot.setLegacySessionId(newSession.getSessionId());
        slot.setAttendanceOpenedAt(now);
        
        // Get configurable session close duration from app_config (default: 15 minutes)
        Integer sessionCloseDurationMinutes = appConfigService != null 
                ? appConfigService.getIntegerConfig("presenter_close_session_duration_minutes", 15)
                : 15;
        slot.setAttendanceClosesAt(now.plusMinutes(sessionCloseDurationMinutes));
        logger.info("📧 Set attendance closes at: {} (session close duration: {} minutes from now)", 
                slot.getAttendanceClosesAt(), sessionCloseDurationMinutes);
        slot.setAttendanceOpenedBy(presenterUsername);
        seminarSlotRepository.save(slot);
        logger.info("Updated slot {} to track session {}", slotId, newSession.getSessionId());
        databaseLoggerService.logBusinessEvent("SLOT_SESSION_LINKED", 
            String.format("Slot %s now tracks session %s", slotId, newSession.getSessionId()), presenterUsername);

        // Use the new session's ID for QR code
        String qrUrl = buildQrUrl(newSession.getSessionId());
        LocalDateTime newClosesAt = slot.getAttendanceClosesAt();
        return new PresenterOpenAttendanceResponse(true,
                "Attendance opened",
                "OPENED",
                qrUrl,
                DATE_TIME_FORMAT.format(now),
                newClosesAt != null ? DATE_TIME_FORMAT.format(newClosesAt) : null,
                newSession.getSessionId(),
                buildQrPayload(newSession.getSessionId()));
    }

    private List<SeminarSlot> loadUpcomingSlots() {
        // CRITICAL: Use Israel timezone consistently with buildSlotCatalog()
        LocalDate today = ZonedDateTime.now(ISRAEL_TIMEZONE).toLocalDate();
        LocalDate yesterday = today.minusDays(1); // Include yesterday in case of timezone edge cases
        
        logger.info("Loading upcoming slots - today is: {} (Israel timezone), checking from yesterday: {}", today, yesterday);
        
        // Query for slots from yesterday onwards (to catch today's slots even with timezone issues)
        List<SeminarSlot> upcoming = seminarSlotRepository.findBySlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(yesterday);
        logger.info("Found {} slots with date >= {} (yesterday)", upcoming.size(), yesterday);
        
        // Log slot dates for debugging
        if (logger.isDebugEnabled() && !upcoming.isEmpty()) {
            upcoming.forEach(slot -> logger.debug("Slot {} - date: {}, start: {}, end: {}", 
                slot.getSlotId(), slot.getSlotDate(), slot.getStartTime(), slot.getEndTime()));
        }
        
        if (upcoming.isEmpty()) {
            // If no slots found, try loading all slots to see what's in the database
            List<SeminarSlot> all = seminarSlotRepository.findAllByOrderBySlotDateAscStartTimeAsc();
            logger.warn("No upcoming slots found for date >= {}, but found {} total slots in database", yesterday, all.size());
            if (!all.isEmpty() && logger.isDebugEnabled()) {
                all.forEach(slot -> logger.debug("All slots - Slot {} - date: {}, start: {}, end: {}", 
                    slot.getSlotId(), slot.getSlotDate(), slot.getStartTime(), slot.getEndTime()));
            }
            // Return all slots - the filter in buildSlotCatalog will handle showing today's slots
            return all;
        }
        logger.info("Returning {} upcoming slots (including yesterday and today)", upcoming.size());
        return upcoming;
    }

    private User findPresenterByUsername(String normalizedUsername) {
        if (normalizedUsername == null) {
            throw new IllegalArgumentException("Presenter username must not be blank");
        }

        return userRepository.findByBguUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new IllegalArgumentException("Presenter not found: " + normalizedUsername));
    }

    private Map<Long, List<SeminarSlotRegistration>> loadRegistrationsBySlot(List<Long> slotIds) {
        if (slotIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Only load APPROVED registrations for display and capacity calculations
        return registrationRepository.findByIdSlotIdIn(slotIds).stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED)
                .collect(Collectors.groupingBy(SeminarSlotRegistration::getSlotId));
    }

    private Map<String, User> loadRegisteredUsers(Map<Long, List<SeminarSlotRegistration>> registrationsBySlot) {
        Set<String> usernames = registrationsBySlot.values().stream()
                .flatMap(List::stream)
                .map(SeminarSlotRegistration::getPresenterUsername)
                .filter(Objects::nonNull)
                .map(this::normalizeUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        if (usernames.isEmpty()) {
            return Collections.emptyMap();
        }

        return userRepository.findByBguUsernameIn(new ArrayList<>(usernames)).stream()
                .collect(Collectors.toMap(user -> normalizeUsername(user.getBguUsername()), user -> user));
    }

    private PresenterSummary buildPresenterSummary(User presenter, String presenterUsername, boolean alreadyRegistered) {
        PresenterSummary summary = new PresenterSummary();
        summary.setId(presenter.getId());
        summary.setName(formatName(presenter));
        summary.setDegree(presenter.getDegree() != null ? presenter.getDegree().name() : null);
        summary.setAlreadyRegistered(alreadyRegistered);
        summary.setCurrentCycleId(null);
        summary.setBguUsername(presenterUsername);
        return summary;
    }

    private MySlotSummary buildMySlotSummary(SeminarSlot slot,
                                             List<SeminarSlotRegistration> registrations,
                                             Map<String, User> registeredUsers) {
        if (slot == null) {
            return null;
        }

        MySlotSummary summary = new MySlotSummary();
        summary.setSlotId(slot.getSlotId());
        summary.setSemesterLabel(slot.getSemesterLabel());
        summary.setDate(formatDate(slot.getSlotDate()));
        summary.setDayOfWeek(formatDayOfWeek(slot.getSlotDate()));
        summary.setTimeRange(formatTimeRange(slot.getStartTime(), slot.getEndTime()));
        summary.setRoom(slot.getRoom());
        summary.setBuilding(slot.getBuilding());

        List<RegisteredPresenter> coPresenters = registrations.stream()
                .map(reg -> toRegisteredPresenter(reg, registeredUsers))
                .collect(Collectors.toList());
        summary.setCoPresenters(coPresenters);

        return summary;
    }

    private SeminarSlot findNextSlotForPresenter(List<SeminarSlotRegistration> registrations,
                                                 Map<Long, SeminarSlot> slotsById) {
        return registrations.stream()
                .filter(reg -> reg != null && reg.getSlotId() != null)
                .map(reg -> {
                    SeminarSlot slot = slotsById.get(reg.getSlotId());
                    return slot != null ? Map.entry(reg, slot) : null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((Map.Entry<SeminarSlotRegistration, SeminarSlot> e) -> e.getValue().getSlotDate())
                        .thenComparing(e -> e.getValue().getStartTime()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private List<SlotCard> buildSlotCatalog(List<SeminarSlot> slots,
                                            Map<Long, List<SeminarSlotRegistration>> registrationsBySlot,
                                            Map<String, User> registeredUsers,
                                            String presenterUsername,
                                            User.Degree presenterDegree,
                                            List<SeminarSlotRegistration> presenterRegistrations) {
        boolean alreadyRegisteredElsewhere = presenterRegistrations.stream()
                .map(SeminarSlotRegistration::getSlotId)
                .findFirst()
                .isPresent();

        Set<Long> presenterSlotIds = presenterRegistrations.stream()
                .map(SeminarSlotRegistration::getSlotId)
                .collect(Collectors.toSet());

        LocalDateTime now = nowIsrael();
        LocalDate today = ZonedDateTime.now(ISRAEL_TIMEZONE).toLocalDate();
        
        logger.info("Building slot catalog - today is: {}, total slots to filter: {}", today, slots.size());
        
        return slots.stream()
                .filter(slot -> {
                    // Always show today's slots, regardless of status
                    LocalDate slotDate = slot.getSlotDate();
                    boolean isToday = slotDate != null && slotDate.equals(today);
                    
                    if (isToday) {
                        logger.info("Slot {} is for today ({}), always showing it (slotDate={}, today={})", 
                            slot.getSlotId(), slotDate, slotDate, today);
                        return true; // Always show today's slots
                    }
                    
                    logger.debug("Slot {} is NOT for today (slotDate={}, today={})", slot.getSlotId(), slotDate, today);
                    
                    // For future slots, filter out closed ones
                    // Check if session is CLOSED
                    if (slot.getLegacySessionId() != null) {
                        Optional<Session> session = sessionRepository.findById(slot.getLegacySessionId());
                        if (session.isPresent() && session.get().getStatus() == Session.SessionStatus.CLOSED) {
                            logger.debug("Filtering out future slot {} - session {} is CLOSED", slot.getSlotId(), slot.getLegacySessionId());
                            return false; // Don't show closed future slots
                        }
                    }
                    // Check if attendance window has passed
                    if (slot.getAttendanceClosesAt() != null && now.isAfter(slot.getAttendanceClosesAt())) {
                        logger.debug("Filtering out future slot {} - attendance closed at {}", slot.getSlotId(), slot.getAttendanceClosesAt());
                        return false; // Don't show future slots with closed attendance
                    }
                    return true; // Show all other slots
                })
                .map(slot -> buildSlotCard(slot,
                        registrationsBySlot.getOrDefault(slot.getSlotId(), List.of()),
                        registeredUsers,
                        presenterUsername,
                        presenterDegree,
                        alreadyRegisteredElsewhere,
                        presenterSlotIds.contains(slot.getSlotId())))
                .collect(Collectors.toList());
    }

    private SlotCard buildSlotCard(SeminarSlot slot,
                                   List<SeminarSlotRegistration> registrations,
                                   Map<String, User> registeredUsers,
                                   String presenterUsername,
                                   User.Degree presenterDegree,
                                   boolean presenterRegisteredElsewhere,
                                   boolean presenterInThisSlot) {

        // Load ALL registrations for this slot (not just approved) to calculate counts
        List<SeminarSlotRegistration> allRegistrations = registrationRepository.findByIdSlotId(slot.getSlotId());
        
        // Calculate approved and pending counts
        long approvedCount = allRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.APPROVED)
                .count();
        
        LocalDateTime now = nowIsrael();
        long pendingCount = allRegistrations.stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.PENDING)
                .filter(reg -> reg.getApprovalTokenExpiresAt() == null || now.isBefore(reg.getApprovalTokenExpiresAt()))
                .count();
        
        // Get current user's approval status for this slot
        String userApprovalStatus = null;
        boolean onWaitingList = false;
        if (presenterUsername != null) {
            Optional<SeminarSlotRegistration> userRegistration = allRegistrations.stream()
                    .filter(reg -> presenterUsername.equalsIgnoreCase(reg.getPresenterUsername()))
                    .findFirst();
            if (userRegistration.isPresent()) {
                ApprovalStatus status = userRegistration.get().getApprovalStatus();
                // Map status values for mobile compatibility
                if (status == ApprovalStatus.PENDING) {
                    userApprovalStatus = "PENDING_APPROVAL";
                } else {
                    userApprovalStatus = status.name();
                }
            }
            // Check if user is on waiting list
            onWaitingList = waitingListService.isOnWaitingList(slot.getSlotId(), presenterUsername);
        }

        int enrolledCount = registrations.size(); // This is already filtered to APPROVED only
        int capacity = slot.getCapacity() != null ? slot.getCapacity() : 0;
        boolean slotLockedByPhd = registrations.stream()
                .anyMatch(reg -> User.Degree.PhD == reg.getDegree());

        // CRITICAL FIX: Calculate effective capacity usage (PhD=2, MSc=1) to determine if slot is actually full
        List<SeminarSlotRegistration> allActiveRegs = new ArrayList<>();
        allActiveRegs.addAll(registrations); // approved
        // Add pending registrations for effective usage calculation
        List<SeminarSlotRegistration> pendingRegs = registrationRepository.findByIdSlotId(slot.getSlotId())
                .stream()
                .filter(reg -> reg.getApprovalStatus() == ApprovalStatus.PENDING)
                .filter(reg -> reg.getApprovalTokenExpiresAt() == null || now.isBefore(reg.getApprovalTokenExpiresAt()))
                .collect(Collectors.toList());
        allActiveRegs.addAll(pendingRegs);
        int effectiveCapacityUsage = calculateEffectiveCapacityUsage(allActiveRegs);
        boolean slotOverCapacity = effectiveCapacityUsage >= capacity && capacity > 0;

        // Check if attendance was closed - if session exists and is CLOSED, slot is no longer available
        boolean attendanceClosed = false;
        if (slot.getLegacySessionId() != null) {
            Optional<Session> session = sessionRepository.findById(slot.getLegacySessionId());
            if (session.isPresent() && session.get().getStatus() == Session.SessionStatus.CLOSED) {
                attendanceClosed = true;
                logger.debug("Slot {} has closed session {}, marking as unavailable", slot.getSlotId(), slot.getLegacySessionId());
            }
        }
        // Also check if attendance window has passed (even if session wasn't explicitly closed)
        if (slot.getAttendanceClosesAt() != null && now.isAfter(slot.getAttendanceClosesAt())) {
            attendanceClosed = true;
            logger.debug("Slot {} attendance window has closed at {}, marking as unavailable", slot.getSlotId(), slot.getAttendanceClosesAt());
        }

        // Calculate available count: capacity minus effective usage (PhD=2, MSc=1)
        // If effective usage meets or exceeds capacity, available should be 0
        int available = Math.max(capacity - effectiveCapacityUsage, 0);
        
        // Determine slot state: if effective usage >= capacity, slot is FULL
        SlotState state;
        if (slotLockedByPhd) {
            state = SlotState.FULL;
            available = 0;
        } else if (slotOverCapacity) {
            // Slot is over capacity
            state = SlotState.FULL;
            available = 0;
            logger.warn("Slot {} is over capacity: capacity={}, effectiveUsage={}, approved={}, pending={}",
                    slot.getSlotId(), capacity, effectiveCapacityUsage, approvedCount, pendingCount);
        } else {
            state = determineState(capacity, effectiveCapacityUsage);
        }
        
        // NOTE: Do NOT change available count or state when attendance is closed
        // The slot should still show correct availability (e.g., 1/2 available = SEMI state)
        // Registration will be prevented by the canRegister check below (line 918)

        SlotCard card = new SlotCard();
        card.setSlotId(slot.getSlotId());
        card.setSemesterLabel(slot.getSemesterLabel());
        card.setDate(formatDate(slot.getSlotDate()));
        card.setDayOfWeek(formatDayOfWeek(slot.getSlotDate()));
        card.setTimeRange(formatTimeRange(slot.getStartTime(), slot.getEndTime()));
        card.setRoom(slot.getRoom());
        card.setBuilding(slot.getBuilding());
        card.setState(state);
        card.setCapacity(capacity);
        card.setEnrolledCount(enrolledCount);
        card.setAvailableCount(available);
        
        // Get waiting list count for this slot (MUST be included for all slots)
        long waitingListCount = waitingListService.getWaitingListCount(slot.getSlotId());
        
        // Get the first person on the waiting list (if any) for waitingListUserName
        String waitingListUserName = null;
        if (waitingListCount > 0) {
            List<WaitingListEntry> waitingList = waitingListService.getWaitingList(slot.getSlotId());
            if (!waitingList.isEmpty()) {
                waitingListUserName = waitingList.get(0).getPresenterUsername();
            }
        }
        
        // Log waiting list status to app_log for all slots
        databaseLoggerService.logAction("INFO", "WAITING_LIST_STATUS",
                String.format("Slot %d waiting list status: count=%d, userOnList=%s, firstUser=%s",
                        slot.getSlotId(), waitingListCount, onWaitingList, waitingListUserName != null ? waitingListUserName : "none"),
                presenterUsername != null ? presenterUsername : "system",
                String.format("slotId=%d,waitingListCount=%d,onWaitingList=%s,waitingListUserName=%s",
                        slot.getSlotId(), waitingListCount, onWaitingList, waitingListUserName != null ? waitingListUserName : "null"));
        
        // Warning: Log data inconsistency when user is marked as on waiting list but count is 0
        // This indicates a potential missing field or data synchronization issue
        if (onWaitingList && waitingListCount == 0) {
            String warningMsg = String.format("Data inconsistency detected: onWaitingList=true but waitingListCount=0 for slotId=%d, presenterUsername=%s",
                    slot.getSlotId(), presenterUsername != null ? presenterUsername : "unknown");
            logger.warn("⚠️ {}", warningMsg);
            databaseLoggerService.logError("WAITING_LIST_DATA_INCONSISTENCY", warningMsg, null,
                    presenterUsername != null ? presenterUsername : "system",
                    String.format("slotId=%d,presenterUsername=%s,onWaitingList=true,waitingListCount=0",
                            slot.getSlotId(), presenterUsername != null ? presenterUsername : "unknown"));
        }
        
        // Set new fields for mobile compatibility
        // IMPORTANT: Send EFFECTIVE capacity usage, not just registration counts
        // PhD = 2 slots, MSc = 1 slot
        int approvedEffectiveUsage = calculateEffectiveCapacityUsage(registrations);
        int pendingEffectiveUsage = calculateEffectiveCapacityUsage(pendingRegs);
        card.setApprovedCount(approvedEffectiveUsage);
        card.setPendingCount(pendingEffectiveUsage);
        card.setApprovalStatus(userApprovalStatus);
        card.setOnWaitingList(onWaitingList);
        card.setWaitingListCount((int) waitingListCount);
        card.setWaitingListUserName(waitingListUserName);
        
        // Load users for pending registrations (they may not be in registeredUsers which only has approved)
        Set<String> pendingUsernames = pendingRegs.stream()
                .map(SeminarSlotRegistration::getPresenterUsername)
                .filter(Objects::nonNull)
                .map(this::normalizeUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, User> allUsers = new HashMap<>(registeredUsers);
        if (!pendingUsernames.isEmpty()) {
            List<User> pendingUsers = userRepository.findByBguUsernameIn(new ArrayList<>(pendingUsernames));
            for (User user : pendingUsers) {
                String normalizedName = normalizeUsername(user.getBguUsername());
                if (normalizedName != null && !allUsers.containsKey(normalizedName)) {
                    allUsers.put(normalizedName, user);
                }
            }
        }

        // Set pending presenters list (with names)
        List<RegisteredPresenter> pendingPresenters = pendingRegs.stream()
                .map(reg -> toRegisteredPresenter(reg, allUsers))
                .collect(Collectors.toList());
        card.setPendingPresenters(pendingPresenters);
        
        // Set waiting list entries (with names) - also load users for WL entries
        List<WaitingListEntry> waitingListEntries = waitingListService.getWaitingList(slot.getSlotId());
        // Load users for waiting list entries
        Set<String> wlUsernames = waitingListEntries.stream()
                .map(WaitingListEntry::getPresenterUsername)
                .filter(Objects::nonNull)
                .map(this::normalizeUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!wlUsernames.isEmpty()) {
            List<User> wlUsers = userRepository.findByBguUsernameIn(new ArrayList<>(wlUsernames));
            for (User user : wlUsers) {
                String normalizedName = normalizeUsername(user.getBguUsername());
                if (normalizedName != null && !allUsers.containsKey(normalizedName)) {
                    allUsers.put(normalizedName, user);
                }
            }
        }
        List<RegisteredPresenter> waitingListPresenters = waitingListEntries.stream()
                .map(entry -> {
                    RegisteredPresenter presenter = new RegisteredPresenter();
                    String username = entry.getPresenterUsername();
                    User user = username != null ? allUsers.get(normalizeUsername(username)) : null;
                    presenter.setName(user != null ? formatName(user) : username);
                    if (entry.getDegree() != null) {
                        presenter.setDegree(entry.getDegree().name());
                    }
                    presenter.setTopic(entry.getTopic());
                    return presenter;
                })
                .collect(Collectors.toList());
        card.setWaitingListEntries(waitingListPresenters);
        
        // Set session status fields for client-side filtering
        card.setAttendanceOpenedAt(slot.getAttendanceOpenedAt() != null ? DATE_TIME_FORMAT.format(slot.getAttendanceOpenedAt()) : null);
        card.setAttendanceClosesAt(slot.getAttendanceClosesAt() != null ? DATE_TIME_FORMAT.format(slot.getAttendanceClosesAt()) : null);
        card.setHasClosedSession(attendanceClosed);

        // NOTE: Registration limits are enforced at the API level, not UI level
        // Being registered in another slot or on a waiting list does NOT block registration
        // Users can register for multiple slots as long as they're within limits (PhD: 1 approved + 1 pending, MSc: 1 approved + 2 pending)
        boolean canRegister = presenterUsername != null
                && !presenterInThisSlot
                && available > 0
                && !attendanceClosed; // Cannot register if attendance was closed

        card.setDisableReason(null);

        if (attendanceClosed) {
            canRegister = false;
            card.setDisableReason("Slot attendance has closed");
        } else if (slotLockedByPhd) {
            canRegister = false;
            if (!presenterInThisSlot) {
                card.setDisableReason("Slot locked by PhD presenter");
            }
        } else if (state == SlotState.FULL) {
            canRegister = false;
            card.setDisableReason("Slot is full");
        } else if (presenterInThisSlot) {
            canRegister = false;
            card.setDisableReason("You are already registered in this slot");
        } else if (presenterUsername == null) {
            canRegister = false;
        }

        card.setCanRegister(canRegister);

        List<RegisteredPresenter> registeredPresenters = registrations.stream()
                .map(reg -> toRegisteredPresenter(reg, registeredUsers))
                .collect(Collectors.toList());
        card.setRegistered(registeredPresenters);

        return card;
    }

    private RegisteredPresenter toRegisteredPresenter(SeminarSlotRegistration registration,
                                                      Map<String, User> registeredUsers) {
        RegisteredPresenter presenter = new RegisteredPresenter();
        String username = normalizeUsername(registration.getPresenterUsername());
        User user = username != null ? registeredUsers.get(username) : null;

        presenter.setName(user != null ? formatName(user) : registration.getPresenterUsername());
        if (registration.getDegree() != null) {
            presenter.setDegree(registration.getDegree().name());
        } else if (user != null && user.getDegree() != null) {
            presenter.setDegree(user.getDegree().name());
        }
        presenter.setTopic(registration.getTopic());
        return presenter;
    }

    private AttendancePanel buildAttendancePanel(SeminarSlot slot, String presenterUsername) {
        AttendancePanel panel = new AttendancePanel();
        if (slot == null) {
            panel.setCanOpen(false);
            panel.setStatus("No upcoming slot");
            return panel;
        }

        LocalDateTime now = nowIsrael();
        LocalDateTime start = toSlotStart(slot);
        if (start == null) {
            panel.setCanOpen(false);
            panel.setStatus("Slot start time not configured");
            return panel;
        }

        LocalDateTime openWindow = start.minusMinutes(10);
        LocalDateTime openedAt = slot.getAttendanceOpenedAt();
        LocalDateTime closesAt = slot.getAttendanceClosesAt();
        String openedBy = normalizeUsername(slot.getAttendanceOpenedBy());

        if (openedAt != null && closesAt != null && now.isBefore(closesAt) && slot.getLegacySessionId() != null) {
            boolean openedByPresenter = Objects.equals(presenterUsername, openedBy);
            panel.setCanOpen(false);
            panel.setAlreadyOpen(true);
            panel.setStatus(openedByPresenter ? "Attendance already open" : "Attendance currently open");
            panel.setOpenQrUrl(buildQrUrl(slot.getLegacySessionId()));
            panel.setWarning("Registration closes at " + DATE_TIME_FORMAT.format(closesAt));
            panel.setOpenedAt(DATE_TIME_FORMAT.format(openedAt));
            panel.setClosesAt(DATE_TIME_FORMAT.format(closesAt));
            panel.setSessionId(slot.getLegacySessionId());
            panel.setQrPayload(buildQrPayload(slot.getLegacySessionId()));
            return panel;
        }

        if (now.isBefore(openWindow)) {
            panel.setCanOpen(false);
            panel.setAlreadyOpen(false);
            panel.setStatus("You can open attendance 10 minutes before start");
            panel.setWarning("Opens at " + DATE_TIME_FORMAT.format(openWindow));
            panel.setOpenQrUrl(null);
            panel.setOpenedAt(null);
            panel.setClosesAt(null);
            panel.setSessionId(null);
            panel.setQrPayload(null);
            return panel;
        }

        panel.setCanOpen(true);
        panel.setAlreadyOpen(false);
        panel.setStatus("Ready to open attendance");
        panel.setWarning("Attendance closes 15 minutes after you open the QR");
        panel.setOpenQrUrl(null);
        panel.setOpenedAt(openedAt != null ? DATE_TIME_FORMAT.format(openedAt) : null);
        panel.setClosesAt(closesAt != null ? DATE_TIME_FORMAT.format(closesAt) : null);
        panel.setSessionId(slot.getLegacySessionId());
        panel.setQrPayload(slot.getLegacySessionId() != null ? buildQrPayload(slot.getLegacySessionId()) : null);
        return panel;
    }

    private void updateSlotStatus(SeminarSlot slot, SlotState desiredState) {
        SeminarSlot.SlotStatus target = SeminarSlot.SlotStatus.valueOf(desiredState.name());
        if (slot.getStatus() != target) {
            slot.setStatus(target);
            seminarSlotRepository.save(slot);
        }
    }

    private SlotState determineState(Integer capacity, int enrolled) {
        int cap = capacity != null ? capacity : 0;
        if (cap <= 0) {
            return enrolled > 0 ? SlotState.FULL : SlotState.FREE;
        }
        if (enrolled >= cap) {
            return SlotState.FULL;
        }
        if (enrolled == 0) {
            return SlotState.FREE;
        }
        return SlotState.SEMI;
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Get current time in Israel timezone to match slot times
     * Slot times are stored as DATE + TIME (no timezone) and interpreted as Israel time
     */
    private LocalDateTime nowIsrael() {
        ZonedDateTime nowZoned = ZonedDateTime.now(ISRAEL_TIMEZONE);
        LocalDateTime nowLocal = nowZoned.toLocalDateTime();
        logger.debug("nowIsrael() - ZonedDateTime: {}, LocalDateTime: {}, Timezone: {}", 
            nowZoned, nowLocal, ISRAEL_TIMEZONE);
        return nowLocal;
    }

    private LocalDateTime toSlotStart(SeminarSlot slot) {
        if (slot.getSlotDate() == null || slot.getStartTime() == null) {
            return null;
        }
        LocalDateTime slotStart = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
        logger.info("🔍 toSlotStart - Slot: {}, Date: {}, Time: {}, Combined LocalDateTime: {} (interpreted as Israel time)", 
            slot.getSlotId(), slot.getSlotDate(), slot.getStartTime(), slotStart);
        return slotStart;
    }

    private String buildQrUrl(Long sessionId) {
        return "/api/v1/qr/session/" + sessionId;
    }

    private String formatDate(LocalDate date) {
        return date != null ? DATE_FORMAT.format(date) : null;
    }

    private String formatDayOfWeek(LocalDate date) {
        return date != null ? date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) : null;
    }

    private String formatTimeRange(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return null;
        }
        return TIME_FORMAT.format(start) + "-" + TIME_FORMAT.format(end);
    }

    private String formatName(User user) {
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        return (first + " " + last).trim();
    }

    private String normalizeTopic(String topic) {
        if (topic == null) {
            return null;
        }
        String trimmed = topic.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSupervisorName(String supervisorName) {
        if (supervisorName == null) {
            return null;
        }
        String trimmed = supervisorName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildQrPayload(Long sessionId) {
        return sessionId != null ? String.format("{\"sessionId\":%d}", sessionId) : null;
    }

    /**
     * Find THIS presenter's OPEN session for a specific slot
     * Uses seminar's presenterUsername to reliably identify the presenter's session
     * CRITICAL: Checks if session matches slot by time AND location, and belongs to THIS presenter
     */
    private Session findPresenterOpenSessionForSlot(String presenterUsername, Long slotId, SeminarSlot slot) {
        List<Session> allOpenSessions = sessionRepository.findOpenSessions();
        LocalDateTime slotStart = toSlotStart(slot);
        String slotLocation = buildLocation(slot);
        
        return allOpenSessions.stream()
            .filter(session -> {
                // Check if this session belongs to THIS slot
                boolean belongsToSlot = false;
                
                // Method 1: Check if slot references this session
                if (slot.getLegacySessionId() != null && Objects.equals(slot.getLegacySessionId(), session.getSessionId())) {
                    belongsToSlot = true;
                }
                
                // Method 2: Check if session time AND location match slot time (for cases where slot doesn't reference the session)
                if (!belongsToSlot && slotStart != null && session.getStartTime() != null) {
                    // Check if session start time matches slot start time (within 1 minute tolerance)
                    boolean timeMatches = Math.abs(Duration.between(slotStart, session.getStartTime()).toMinutes()) <= 1;
                    
                    // Check if session location matches slot location
                    boolean locationMatches = false;
                    if (slotLocation != null && session.getLocation() != null) {
                        locationMatches = slotLocation.equals(session.getLocation());
                    } else if (slotLocation == null && session.getLocation() == null) {
                        locationMatches = true; // Both null = match
                    }
                    
                    if (timeMatches && locationMatches) {
                        belongsToSlot = true;
                    }
                }
                
                if (!belongsToSlot) {
                    return false;
                }
                
                // Verify the session's seminar belongs to THIS presenter
                Optional<Seminar> seminar = seminarRepository.findById(session.getSeminarId());
                if (seminar.isPresent()) {
                    String seminarPresenter = normalizeUsername(seminar.get().getPresenterUsername());
                    return Objects.equals(seminarPresenter, presenterUsername);
                }
                
                return false;
            })
            .findFirst()
            .orElse(null);
    }

    /**
     * Find THIS presenter's CLOSED session for a specific slot
     * Used to block re-opening attendance after session has ended
     * Only checks sessions that were explicitly linked to this slot via legacy_session_id
     */
    private Session findPresenterClosedSessionForSlot(String presenterUsername, Long slotId, SeminarSlot slot) {
        // Simple approach: check if slot references a closed session for THIS presenter
        // This is more accurate than time/location matching which can have false positives
        Long slotSessionId = slot.getLegacySessionId();
        if (slotSessionId == null) {
            // No session linked to this slot - no closed session to block
            return null;
        }

        Optional<Session> sessionOpt = sessionRepository.findById(slotSessionId);
        if (sessionOpt.isEmpty()) {
            return null;
        }

        Session session = sessionOpt.get();

        // Only block if session is CLOSED
        if (session.getStatus() != Session.SessionStatus.CLOSED) {
            return null;
        }

        // Verify the session belongs to THIS presenter
        Optional<Seminar> seminar = seminarRepository.findById(session.getSeminarId());
        if (seminar.isPresent()) {
            String seminarPresenter = normalizeUsername(seminar.get().getPresenterUsername());
            if (Objects.equals(seminarPresenter, presenterUsername)) {
                logger.info("Found closed session {} for presenter {} on slot {}",
                    session.getSessionId(), presenterUsername, slotId);
                return session;
            }
        }

        return null;
    }

    /**
     * Find ANY OTHER presenter's OPEN session for a specific slot
     * Returns null if no other presenter has an open session
     * CRITICAL: Checks if session matches slot by time AND location, and belongs to a different presenter
     */
    private Session findOtherPresenterOpenSessionForSlot(String presenterUsername, Long slotId, SeminarSlot slot, List<Session> allOpenSessions) {
        LocalDateTime slotStart = toSlotStart(slot);
        LocalDateTime slotEnd = toSlotEnd(slot);
        String slotLocation = buildLocation(slot);
        LocalDateTime now = nowIsrael();

        // Get configured session duration
        Integer sessionCloseDurationMinutes = appConfigService != null
                ? appConfigService.getIntegerConfig("presenter_close_session_duration_minutes", 15)
                : 15;

        logger.debug("Checking for other presenter's sessions for slot {} (time: {} to {}, location: {})",
            slotId, slotStart, slotEnd, slotLocation);

        return allOpenSessions.stream()
            .filter(session -> {
                // CRITICAL: Skip sessions whose time window has expired
                // Even if status is OPEN, if the window has passed, don't consider it blocking
                if (session.getStartTime() != null) {
                    LocalDateTime sessionClosesAt = session.getStartTime().plusMinutes(sessionCloseDurationMinutes);
                    if (now.isAfter(sessionClosesAt)) {
                        logger.debug("Session {} time window expired (closed at {}), not considering it blocking",
                            session.getSessionId(), sessionClosesAt);
                        return false;
                    }
                }

                // Check if this session belongs to THIS slot by matching time AND location
                boolean belongsToSlot = false;

                // Method 1: Check if slot directly references this session
                if (slot.getLegacySessionId() != null && Objects.equals(slot.getLegacySessionId(), session.getSessionId())) {
                    belongsToSlot = true;
                    logger.debug("Session {} belongs to slot {} (slot.legacySessionId matches)", session.getSessionId(), slotId);
                }

                // Method 2: Check if session time AND location match slot
                if (!belongsToSlot && slotStart != null && session.getStartTime() != null) {
                    // Check if session start time matches slot start time (within 1 minute tolerance)
                    boolean timeMatches = Math.abs(Duration.between(slotStart, session.getStartTime()).toMinutes()) <= 1;

                    // Check if session location matches slot location
                    boolean locationMatches = false;
                    if (slotLocation != null && session.getLocation() != null) {
                        locationMatches = slotLocation.equals(session.getLocation());
                    } else if (slotLocation == null && session.getLocation() == null) {
                        locationMatches = true; // Both null = match
                    }

                    if (timeMatches && locationMatches) {
                        belongsToSlot = true;
                        logger.debug("Session {} belongs to slot {} (time and location match)", session.getSessionId(), slotId);
                    }
                }

                if (!belongsToSlot) {
                    return false;
                }

                // Check if this session belongs to a DIFFERENT presenter
                Optional<Seminar> seminar = seminarRepository.findById(session.getSeminarId());
                if (seminar.isPresent()) {
                    String seminarPresenter = normalizeUsername(seminar.get().getPresenterUsername());
                    boolean isDifferentPresenter = !Objects.equals(seminarPresenter, presenterUsername);

                    if (isDifferentPresenter) {
                        // CRITICAL: Also verify that the other presenter is registered for THIS slot
                        // This ensures we only block if the other presenter's session is for the same slot
                        boolean otherPresenterRegistered = registrationRepository.existsByIdSlotIdAndIdPresenterUsername(slotId, seminarPresenter);
                        if (otherPresenterRegistered) {
                            logger.warn("Found other presenter's session {} for slot {} (presenter: {} vs current: {}) - BLOCKING",
                                session.getSessionId(), slotId, seminarPresenter, presenterUsername);
                            return true;
                        } else {
                            logger.debug("Session {} belongs to different presenter {} but they're not registered for slot {} - not blocking",
                                session.getSessionId(), seminarPresenter, slotId);
                        }
                    }
                }

                return false;
            })
            .findFirst()
            .orElse(null);
    }

    /**
     * Get QR code data for a slot
     * Returns QR code information if attendance is open for the slot
     * CRITICAL FIX: Finds THIS presenter's OPEN session, not just any session in the slot
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSlotQRCode(String presenterUsernameParam, Long slotId) {
        String normalizedUsername = normalizeUsername(presenterUsernameParam);
        logger.info("Getting QR code for presenter {} and slot {}", normalizedUsername, slotId);

        // Verify presenter exists
        findPresenterByUsername(normalizedUsername);

        // Get slot
        SeminarSlot slot = seminarSlotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + slotId));

        // Check if presenter is registered for this slot
        if (!registrationRepository.existsByIdSlotIdAndIdPresenterUsername(slotId, normalizedUsername)) {
            String errorMsg = "Presenter is not registered for this slot";
            databaseLoggerService.logError("QR_CODE_NOT_REGISTERED", errorMsg, null, normalizedUsername,
                String.format("slotId=%s", slotId));
            throw new IllegalArgumentException(errorMsg);
        }

        // CRITICAL FIX: Find THIS presenter's OPEN session for THIS slot
        // Do NOT use slot.getLegacySessionId() as it might point to another presenter's session
        // Use the same logic as openAttendance to find the correct session
        Session session = findPresenterOpenSessionForSlot(normalizedUsername, slotId, slot);
        
        if (session == null) {
            String errorMsg = "No open session found for this presenter and slot. Please open attendance first.";
            logger.warn("Presenter {} requested QR code for slot {} but has no open session", normalizedUsername, slotId);
            databaseLoggerService.logError("QR_CODE_NO_SESSION", errorMsg, null, normalizedUsername,
                String.format("slotId=%s", slotId));
            throw new IllegalStateException(errorMsg);
        }
        
        logger.info("Found open session {} for presenter {} on slot {}", session.getSessionId(), normalizedUsername, slotId);

        // Check if attendance window is still open
        LocalDateTime now = nowIsrael();
        LocalDateTime openedAt = slot.getAttendanceOpenedAt();
        LocalDateTime closesAt = slot.getAttendanceClosesAt();
        
        if (openedAt == null) {
            throw new IllegalStateException("Attendance was never opened for this slot");
        }
        
        if (closesAt != null && now.isAfter(closesAt)) {
            String closesAtStr = DATE_TIME_FORMAT.format(closesAt);
            throw new IllegalStateException(String.format("Attendance window has closed at %s", closesAtStr));
        }

        // Generate QR code data similar to QRCodeController
        Map<String, Object> qrData = new HashMap<>();
        
        String sessionIdString = String.valueOf(session.getSessionId());
        String fullUrl = globalConfig.getSessionsEndpoint() + "/" + sessionIdString;
        String relativePath = "/api/v1/sessions/" + sessionIdString;
        String sessionIdOnly = sessionIdString;
        
        qrData.put("sessionId", session.getSessionId());
        qrData.put("seminarId", session.getSeminarId());
        qrData.put("status", session.getStatus().toString());
        qrData.put("startTime", session.getStartTime());
        
        // QR code content options
        Map<String, String> qrContent = new HashMap<>();
        qrContent.put("fullUrl", fullUrl);
        qrContent.put("relativePath", relativePath);
        qrContent.put("sessionIdOnly", sessionIdOnly);
        qrContent.put("recommended", fullUrl);
        
        qrData.put("qrContent", qrContent);
        
        // Server information
        Map<String, String> serverInfo = new HashMap<>();
        serverInfo.put("serverUrl", globalConfig.getServerUrl());
        serverInfo.put("apiBaseUrl", globalConfig.getApiBaseUrl());
        serverInfo.put("environment", globalConfig.getEnvironment());
        
        qrData.put("serverInfo", serverInfo);
        
        // QR code metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generatedAt", LocalDateTime.now());
        metadata.put("version", "1.0");
        metadata.put("format", "URL");
        metadata.put("description", "Session QR code for attendance scanning");
        
        qrData.put("metadata", metadata);
        
        logger.info("QR code data generated successfully for slot {} and session {}", slotId, session.getSessionId());
        return qrData;
    }

    /**
     * Ensure a seminar exists for THIS presenter for THIS slot
     * CRITICAL: Each presenter must have their own seminar, even if they share the same slot
     * This ensures that when multiple presenters open sessions in the same slot,
     * each session's seminar has the correct presenter_username
     */
    private Seminar ensureLegacySeminar(SeminarSlot slot, User presenter) {
        String presenterUsername = normalizeUsername(presenter.getBguUsername());
        if (presenterUsername == null) {
            throw new IllegalArgumentException("Presenter username is required");
        }
        
        // CRITICAL FIX: Find or create a seminar for THIS specific presenter
        // Do NOT reuse the slot's legacySeminarId, as it might belong to another presenter
        // Check if this presenter already has a seminar for this slot
        String seminarName = buildSeminarName(slot);
        
        // Try to find an existing seminar for this presenter with this name
        // This allows the same presenter to reuse their seminar if they reopen a session
        List<Seminar> existingSeminars = seminarRepository.findByPresenterUsername(presenterUsername);
        Optional<Seminar> existingSeminar = existingSeminars.stream()
            .filter(sem -> sem.getSeminarName() != null && sem.getSeminarName().equals(seminarName))
            .findFirst();
        
        if (existingSeminar.isPresent()) {
            Seminar found = existingSeminar.get();
            logger.info("Found existing seminar {} for presenter {} on slot {} (seminarName: {})", 
                found.getSeminarId(), presenterUsername, slot.getSlotId(), seminarName);
            return found;
        }
        
        // Create a new seminar for THIS presenter
        Seminar seminar = new Seminar();
        seminar.setPresenterUsername(presenterUsername);
        seminar.setSeminarName(seminarName);
        seminar.setDescription("Auto-generated seminar for slot " + seminarName + " (Presenter: " + presenterUsername + ")");
        seminar.setMaxEnrollmentCapacity(slot.getCapacity() != null ? slot.getCapacity() : 0);
        Seminar saved = seminarRepository.save(seminar);
        
        logger.info("Created new seminar {} for presenter {} on slot {} (seminarName: {})", 
            saved.getSeminarId(), presenterUsername, slot.getSlotId(), seminarName);
        
        // CRITICAL: Do NOT update slot.setLegacySeminarId here
        // The slot's legacySeminarId should only be updated if this is the first session for the slot
        // Multiple presenters can have different seminars for the same slot
        
        return saved;
    }

    private Session createLegacySession(SeminarSlot slot, Seminar seminar, String presenterUsername) {
        Session session = new Session();
        session.setSeminarId(seminar.getSeminarId());
        // CRITICAL: Use current time as session start, not slot's scheduled time
        // This ensures attendance window is calculated from when presenter actually opens attendance
        session.setStartTime(nowIsrael());
        session.setEndTime(null); // Will be set when session closes
        session.setStatus(Session.SessionStatus.OPEN); // CRITICAL: Must be OPEN status
        session.setLocation(buildLocation(slot));
        
        logger.info("Creating new session for slot {} with OPEN status (seminarId: {})", 
            slot.getSlotId(), seminar.getSeminarId());
        
        // CRITICAL: Save session to database - this MUST create a new row in sessions table
        Session savedSession = sessionRepository.save(session);
        
        // CRITICAL: Verify session was saved with OPEN status and has an ID
        if (savedSession.getSessionId() == null) {
            String errorMsg = "CRITICAL ERROR: Session was saved but sessionId is null! Session not persisted to database!";
            logger.error(errorMsg);
            databaseLoggerService.logError("SESSION_SAVE_FAILED", errorMsg, null, presenterUsername, 
                String.format("slotId=%s,seminarId=%s", slot.getSlotId(), seminar.getSeminarId()));
            throw new IllegalStateException("Session was not saved to database - sessionId is null");
        }
        
        if (savedSession.getStatus() != Session.SessionStatus.OPEN) {
            String errorMsg = String.format("CRITICAL ERROR: Session %s was saved with status %s instead of OPEN!", 
                savedSession.getSessionId(), savedSession.getStatus());
            logger.error(errorMsg);
            databaseLoggerService.logError("SESSION_WRONG_STATUS", errorMsg, null, presenterUsername, 
                String.format("sessionId=%s,status=%s", savedSession.getSessionId(), savedSession.getStatus()));
            throw new IllegalStateException("Session was not saved with OPEN status: " + savedSession.getStatus());
        }
        
        logger.info("Successfully created and saved session {} with OPEN status for slot {} (seminarId: {})", 
            savedSession.getSessionId(), slot.getSlotId(), savedSession.getSeminarId());
        
        // Log to database
        databaseLoggerService.logAction("INFO", "SESSION_CREATED", 
            String.format("Session %s created with OPEN status for slot %s", savedSession.getSessionId(), slot.getSlotId()),
            presenterUsername, String.format("sessionId=%s,slotId=%s,seminarId=%s,status=OPEN", 
                savedSession.getSessionId(), slot.getSlotId(), savedSession.getSeminarId()));
        
        // Verify session can be retrieved from database immediately
        Optional<Session> verifySession = sessionRepository.findById(savedSession.getSessionId());
        if (verifySession.isEmpty()) {
            String errorMsg = String.format("CRITICAL ERROR: Session %s was saved but cannot be retrieved from database!", 
                savedSession.getSessionId());
            logger.error(errorMsg);
            databaseLoggerService.logError("SESSION_RETRIEVAL_FAILED", errorMsg, null, presenterUsername, 
                String.format("sessionId=%s", savedSession.getSessionId()));
            throw new IllegalStateException("Session was saved but cannot be retrieved - transaction may have rolled back");
        }
        
        Session verifiedSession = verifySession.get();
        if (verifiedSession.getStatus() != Session.SessionStatus.OPEN) {
            String errorMsg = String.format("CRITICAL ERROR: Retrieved session %s has status %s instead of OPEN!", 
                verifiedSession.getSessionId(), verifiedSession.getStatus());
            logger.error(errorMsg);
            databaseLoggerService.logError("SESSION_STATUS_MISMATCH", errorMsg, null, presenterUsername, 
                String.format("sessionId=%s,retrievedStatus=%s", verifiedSession.getSessionId(), verifiedSession.getStatus()));
            throw new IllegalStateException("Retrieved session has wrong status: " + verifiedSession.getStatus());
        }
        
        logger.info("Verified session {} exists in database with OPEN status - will appear in GET /api/v1/sessions/open", 
            verifiedSession.getSessionId());
        
        return savedSession;
    }

    private void closeLegacySessionIfExists(SeminarSlot slot) {
        if (slot.getLegacySessionId() != null) {
            sessionRepository.findById(slot.getLegacySessionId()).ifPresent(session -> {
                // CRITICAL: Don't close active sessions that are still within their attendance window
                // This prevents premature closing when PhD students register or other events occur
                if (session.getStatus() == Session.SessionStatus.OPEN) {
                    LocalDateTime now = nowIsrael();
                    LocalDateTime closesAt = slot.getAttendanceClosesAt();
                    // If attendance window is still open, preserve the active session
                    if (closesAt != null && now.isBefore(closesAt)) {
                        logger.warn("Preventing automatic close of active session {} - attendance window still open until {} (current time: {})", 
                            session.getSessionId(), closesAt, now);
                        databaseLoggerService.logSessionEvent("SESSION_CLOSE_PREVENTED", session.getSessionId(), 
                            session.getSeminarId(), null);
                        return;
                    }
                    // If attendance window has passed, it's safe to close
                    if (closesAt != null && now.isAfter(closesAt)) {
                        logger.info("Closing session {} - attendance window has passed (closed at: {}, current: {})", 
                            session.getSessionId(), closesAt, now);
                    }
                }
                closeLegacySession(session);
            });
        }
    }

    private void closeLegacySession(Session session) {
        if (session.getStatus() != Session.SessionStatus.CLOSED) {
            logger.info("Closing legacy session {} (status: {})", session.getSessionId(), session.getStatus());
            session.setStatus(Session.SessionStatus.CLOSED);
            // CRITICAL: Use Israel timezone to match session times
            session.setEndTime(nowIsrael());
            sessionRepository.save(session);
            databaseLoggerService.logSessionEvent("SESSION_AUTO_CLOSED", session.getSessionId(), 
                session.getSeminarId(), null);
        }
    }

    private String buildSeminarName(SeminarSlot slot) {
        String date = formatDate(slot.getSlotDate());
        String time = formatTimeRange(slot.getStartTime(), slot.getEndTime());
        return String.format("%s %s", date != null ? date : "-", time != null ? time : "").trim();
    }

    private LocalDateTime toSlotEnd(SeminarSlot slot) {
        if (slot.getSlotDate() == null || slot.getEndTime() == null) {
            return null;
        }
        return LocalDateTime.of(slot.getSlotDate(), slot.getEndTime());
    }

    private String buildLocation(SeminarSlot slot) {
        if (slot.getBuilding() == null && slot.getRoom() == null) {
            return null;
        }
        if (slot.getBuilding() != null && slot.getRoom() != null) {
            return "Building " + slot.getBuilding() + " Room " + slot.getRoom();
        }
        return slot.getBuilding() != null ? slot.getBuilding() : slot.getRoom();
    }
}

