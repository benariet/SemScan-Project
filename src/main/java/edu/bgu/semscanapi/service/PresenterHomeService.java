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
import edu.bgu.semscanapi.entity.Seminar;
import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.SeminarSlotRegistrationId;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.SeminarRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PresenterHomeService {

    private static final Logger logger = LoggerUtil.getLogger(PresenterHomeService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final UserRepository userRepository;
    private final SeminarSlotRepository seminarSlotRepository;
    private final SeminarSlotRegistrationRepository registrationRepository;
    private final SeminarRepository seminarRepository;
    private final SessionRepository sessionRepository;
    private final GlobalConfig globalConfig;
    private final EmailService emailService;
    private final DatabaseLoggerService databaseLoggerService;

    public PresenterHomeService(UserRepository userRepository,
                                SeminarSlotRepository seminarSlotRepository,
                                SeminarSlotRegistrationRepository registrationRepository,
                                SeminarRepository seminarRepository,
                                SessionRepository sessionRepository,
                                GlobalConfig globalConfig,
                                EmailService emailService,
                                DatabaseLoggerService databaseLoggerService) {
        this.userRepository = userRepository;
        this.seminarSlotRepository = seminarSlotRepository;
        this.registrationRepository = registrationRepository;
        this.seminarRepository = seminarRepository;
        this.sessionRepository = sessionRepository;
        this.globalConfig = globalConfig;
        this.emailService = emailService;
        this.databaseLoggerService = databaseLoggerService;
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

    @Transactional(timeout = 10, isolation = Isolation.READ_COMMITTED) 
    // READ_COMMITTED reduces lock contention vs REPEATABLE_READ
    // Timeout prevents long-running transactions that hold locks
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

            // Check if presenter registered in another slot
            if (registrationRepository.existsByIdPresenterUsername(presenterUsername)) {
                String errorMsg = "Presenter already registered in another slot";
                databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, presenterUsername, 
                    String.format("slotId=%s,reason=ALREADY_REGISTERED", slotId));
                return new PresenterSlotRegistrationResponse(false, errorMsg, "ALREADY_REGISTERED");
            }
            
            // Load existing registrations for this slot (needed for capacity/PhD checks)
            // Do this BEFORE reading slot to minimize lock time
            List<SeminarSlotRegistration> existingRegistrations = registrationRepository.findByIdSlotId(slotId);
            boolean existingPhd = existingRegistrations.stream()
                    .anyMatch(reg -> User.Degree.PhD == reg.getDegree());

            if (existingPhd) {
                String errorMsg = "Slot already has a PhD presenter";
                databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, presenterUsername, 
                    String.format("slotId=%s,reason=PHD_BLOCKED", slotId));
                return new PresenterSlotRegistrationResponse(false, "Slot locked by PhD presenter", "SLOT_LOCKED");
            }

            presenterDegree = presenter.getDegree() != null ? presenter.getDegree() : User.Degree.MSc;

            if (presenterDegree == User.Degree.PhD) {
                if (!existingRegistrations.isEmpty()) {
                    String errorMsg = "Slot already has an MSc presenter";
                    databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, presenterUsername, 
                        String.format("slotId=%s,reason=PHD_BLOCKED_MSC_EXISTS", slotId));
                    return new PresenterSlotRegistrationResponse(false, errorMsg, "PHD_BLOCKED");
                }
            }
            
            // Read slot ONLY when we need it (for capacity check and update)
            // This minimizes the time we hold a lock on the slot row
            slot = seminarSlotRepository.findById(slotId)
                    .orElseThrow(() -> {
                        String errorMsg = "Slot not found: " + slotId;
                        databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, normalizedUsername, 
                            String.format("slotId=%s,reason=SLOT_NOT_FOUND", slotId));
                        return new IllegalArgumentException(errorMsg);
                    });
            
            if (presenterDegree != User.Degree.PhD) {
                int capacity = slot.getCapacity() != null ? slot.getCapacity() : 0;
                if (existingRegistrations.size() >= capacity) {
                    updateSlotStatus(slot, SlotState.FULL);
                    String errorMsg = "Slot is already full";
                    databaseLoggerService.logError("SLOT_REGISTRATION_FAILED", errorMsg, null, presenterUsername, 
                        String.format("slotId=%s,reason=SLOT_FULL,capacity=%d,existing=%d", slotId, capacity, existingRegistrations.size()));
                    return new PresenterSlotRegistrationResponse(false, errorMsg, "SLOT_FULL");
                }
            }

            registration = new SeminarSlotRegistration();
            registration.setId(new SeminarSlotRegistrationId(slotId, presenterUsername));
            registration.setDegree(presenter.getDegree());
            registration.setTopic(normalizeTopic(request.getTopic()));
            registration.setSupervisorName(normalizeSupervisorName(request.getSupervisorName()));
            registration.setSupervisorEmail(request.getSupervisorEmail());
            registration.setRegisteredAt(LocalDateTime.now());

            registrationRepository.save(registration);

            boolean slotNowHasPhd = presenterDegree == User.Degree.PhD;
            long newEnrolled = existingRegistrations.size() + 1;
            SlotState updatedState = slotNowHasPhd
                    ? SlotState.FULL
                    : determineState(slot.getCapacity(), (int) newEnrolled);

            if (slotNowHasPhd) {
                slot.setAttendanceOpenedAt(null);
                slot.setAttendanceClosesAt(null);
                slot.setAttendanceOpenedBy(null);
                closeLegacySessionIfExists(slot);
                slot.setLegacySessionId(null);
            }

            updateSlotStatus(slot, updatedState);
            seminarSlotRepository.save(slot);
            
            // Transaction commits here - all database operations complete
            // This releases locks immediately, preventing lock contention

            logger.info("Presenter {} successfully registered to slot {}", presenterUsername, slotId);
            
            // Log successful registration to database
            databaseLoggerService.logBusinessEvent("SLOT_REGISTRATION_SUCCESS", 
                String.format("Presenter %s registered to slot %s (degree: %s)", presenterUsername, slotId, presenterDegree), 
                presenterUsername);
            
        } catch (org.springframework.dao.DataAccessException e) {
            // Database errors (locks, timeouts, etc.)
            String errorMsg = String.format("Database error during slot registration: %s", e.getMessage());
            logger.error("Database error registering presenter {} to slot {}", presenterUsername, slotId, e);
            databaseLoggerService.logError("SLOT_REGISTRATION_DB_ERROR", errorMsg, e, 
                presenterUsername != null ? presenterUsername : normalizedUsername, 
                String.format("slotId=%s,exceptionType=%s", slotId, e.getClass().getName()));
            throw e; // Re-throw to trigger transaction rollback
        } catch (Exception e) {
            // Any other unexpected errors
            String errorMsg = String.format("Unexpected error during slot registration: %s", e.getMessage());
            logger.error("Unexpected error registering presenter {} to slot {}", presenterUsername, slotId, e);
            databaseLoggerService.logError("SLOT_REGISTRATION_ERROR", errorMsg, e, 
                presenterUsername != null ? presenterUsername : normalizedUsername, 
                String.format("slotId=%s,exceptionType=%s", slotId, e.getClass().getName()));
            throw e;
        }
        
        // Send supervisor notification email OUTSIDE the transaction
        // This prevents holding database locks during slow email operations
        boolean emailSent = false;
        String supervisorEmail = request.getSupervisorEmail();
        if (supervisorEmail != null && !supervisorEmail.trim().isEmpty()) {
            try {
                String presenterName = formatName(presenter);
                if (presenterName == null || presenterName.trim().isEmpty()) {
                    presenterName = presenterUsername;
                }
                String presenterDegreeStr = presenterDegree.name();
                String slotDateStr = slot.getSlotDate() != null ? DATE_FORMAT.format(slot.getSlotDate()) : "N/A";
                String slotStartTimeStr = slot.getStartTime() != null ? TIME_FORMAT.format(slot.getStartTime()) : "N/A";
                String slotEndTimeStr = slot.getEndTime() != null ? TIME_FORMAT.format(slot.getEndTime()) : "N/A";
                String topic = registration.getTopic();

                EmailService.EmailResult emailResult = emailService.sendSupervisorNotificationEmail(
                        supervisorEmail,
                        request.getSupervisorName(),
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

                emailSent = emailResult.isSuccess();
                if (emailSent) {
                    logger.info("Supervisor email sent successfully for presenter {} and slot {}", presenterUsername, slotId);
                } else {
                    logger.warn("Failed to send supervisor email for presenter {} and slot {}: {} (Code: {})", 
                        presenterUsername, slotId, emailResult.getErrorMessage(), emailResult.getErrorCode());
                }
            } catch (Exception e) {
                logger.error("Failed to send supervisor email for presenter {} and slot {}", presenterUsername, slotId, e);
                // Don't fail registration if email fails
            }
        }

        return new PresenterSlotRegistrationResponse(true, "Registered successfully", "REGISTERED", emailSent);
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

        registrationRepository.delete(existing.get());

        List<SeminarSlotRegistration> remainingRegistrations = registrationRepository.findByIdSlotId(slotId);
        boolean remainingPhd = remainingRegistrations.stream()
                .anyMatch(reg -> User.Degree.PhD == reg.getDegree());
        long remaining = remainingRegistrations.size();

        SlotState newState = remainingPhd
                ? SlotState.FULL
                : determineState(slot.getCapacity(), (int) remaining);
        updateSlotStatus(slot, newState);

        String openedBy = normalizeUsername(slot.getAttendanceOpenedBy());
        if (Objects.equals(presenterUsername, openedBy) || remaining == 0 || remainingPhd) {
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

        logger.info("Presenter {} removed from slot {}. Remaining presenters: {}", presenterUsername, slotId, remaining);
        return new PresenterSlotRegistrationResponse(true, "Registration cancelled", "UNREGISTERED", false);
    }

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

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = toSlotStart(slot);
        if (start == null) {
            String errorMsg = "Slot start time not configured";
            databaseLoggerService.logError("ATTENDANCE_OPEN_FAILED", errorMsg, null, presenterUsername, 
                String.format("slotId=%s,reason=NO_SCHEDULE", slotId));
            return new PresenterOpenAttendanceResponse(false, errorMsg, "NO_SCHEDULE", null, null, null, null, null);
        }

        LocalDateTime end = toSlotEnd(slot);
        LocalDateTime openWindow = start.minusMinutes(10);
        
        // Check if too early (before 10 minutes before start)
        // Allow opening during the slot time (from 10 minutes before start until slot ends, or indefinitely if no end time)
        if (now.isBefore(openWindow)) {
            String openWindowStr = DATE_TIME_FORMAT.format(openWindow);
            String errorMsg = String.format("Cannot start session. Attendance can only be opened 10 minutes before the slot start time (at %s)", openWindowStr);
            databaseLoggerService.logError("ATTENDANCE_OPEN_FAILED", errorMsg, null, presenterUsername, 
                String.format("slotId=%s,reason=TOO_EARLY,now=%s,openWindow=%s", slotId, DATE_TIME_FORMAT.format(now), openWindowStr));
            return new PresenterOpenAttendanceResponse(false, errorMsg, "TOO_EARLY", null, openWindowStr, null, null, null);
        }
        
        // Check if too late (after slot ends) - only if slot has an end time
        if (end != null && now.isAfter(end)) {
            String errorMsg = String.format("Too late: Slot has ended at %s", DATE_TIME_FORMAT.format(end));
            databaseLoggerService.logError("ATTENDANCE_OPEN_FAILED", errorMsg, null, presenterUsername, 
                String.format("slotId=%s,reason=TOO_LATE,now=%s,end=%s", slotId, DATE_TIME_FORMAT.format(now), DATE_TIME_FORMAT.format(end)));
            return new PresenterOpenAttendanceResponse(false, errorMsg, "TOO_LATE", null, null, null, null, null);
        }
        
        // Allow opening if: now >= openWindow (10 minutes before start) AND (end is null OR now <= end)
        // This covers both: 10 minutes before start AND during the slot time

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
        
        // Check if THIS presenter already has an OPEN session for THIS slot
        // If yes, return the existing session (don't create a duplicate)
        if (thisPresenterOpenSession != null) {
            // Get session details for response
            Optional<SeminarSlot> sessionSlot = seminarSlotRepository.findByLegacySessionId(thisPresenterOpenSession.getSessionId());
            String qrUrl = buildQrUrl(thisPresenterOpenSession.getSessionId());
            String payload = buildQrPayload(thisPresenterOpenSession.getSessionId());
            
            // Try to get timing info from slot, but if not available, calculate from session
            LocalDateTime sessionOpenedAt = null;
            LocalDateTime sessionClosesAt = null;
            if (sessionSlot.isPresent()) {
                sessionOpenedAt = sessionSlot.get().getAttendanceOpenedAt();
                sessionClosesAt = sessionSlot.get().getAttendanceClosesAt();
            }
            // If slot doesn't have timing info, use session start time + 15 minutes
            if (sessionClosesAt == null && thisPresenterOpenSession.getStartTime() != null) {
                sessionClosesAt = thisPresenterOpenSession.getStartTime().plusMinutes(15);
            }
            if (sessionOpenedAt == null && thisPresenterOpenSession.getStartTime() != null) {
                sessionOpenedAt = thisPresenterOpenSession.getStartTime();
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
        slot.setLegacySessionId(newSession.getSessionId());
        slot.setAttendanceOpenedAt(now);
        slot.setAttendanceClosesAt(now.plusMinutes(15));
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
        LocalDate today = LocalDate.now();
        logger.debug("Loading upcoming slots - today is: {}", today);
        List<SeminarSlot> upcoming = seminarSlotRepository.findBySlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(today);
        logger.debug("Found {} slots with date >= {}", upcoming.size(), today);
        if (upcoming.isEmpty()) {
            List<SeminarSlot> all = seminarSlotRepository.findAllByOrderBySlotDateAscStartTimeAsc();
            logger.debug("No upcoming slots found, returning all {} slots", all.size());
            return all;
        }
        logger.debug("Returning {} upcoming slots", upcoming.size());
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

        return registrationRepository.findByIdSlotIdIn(slotIds).stream()
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

        LocalDateTime now = LocalDateTime.now();
        
        return slots.stream()
                .filter(slot -> {
                    // Filter out slots where attendance was closed
                    // Check if session is CLOSED
                    if (slot.getLegacySessionId() != null) {
                        Optional<Session> session = sessionRepository.findById(slot.getLegacySessionId());
                        if (session.isPresent() && session.get().getStatus() == Session.SessionStatus.CLOSED) {
                            logger.debug("Filtering out slot {} - session {} is CLOSED", slot.getSlotId(), slot.getLegacySessionId());
                            return false; // Don't show closed slots
                        }
                    }
                    // Check if attendance window has passed
                    if (slot.getAttendanceClosesAt() != null && now.isAfter(slot.getAttendanceClosesAt())) {
                        logger.debug("Filtering out slot {} - attendance closed at {}", slot.getSlotId(), slot.getAttendanceClosesAt());
                        return false; // Don't show slots with closed attendance
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

        int enrolledCount = registrations.size();
        int capacity = slot.getCapacity() != null ? slot.getCapacity() : 0;
        boolean slotLockedByPhd = registrations.stream()
                .anyMatch(reg -> User.Degree.PhD == reg.getDegree());
        boolean slotHasMsc = registrations.stream()
                .anyMatch(reg -> reg.getDegree() == null || User.Degree.MSc == reg.getDegree());

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
        LocalDateTime now = LocalDateTime.now();
        if (slot.getAttendanceClosesAt() != null && now.isAfter(slot.getAttendanceClosesAt())) {
            attendanceClosed = true;
            logger.debug("Slot {} attendance window has closed at {}, marking as unavailable", slot.getSlotId(), slot.getAttendanceClosesAt());
        }

        int available = Math.max(capacity - enrolledCount, 0);
        SlotState state = slotLockedByPhd ? SlotState.FULL : determineState(capacity, enrolledCount);
        if (slotLockedByPhd) {
            available = 0;
        }
        
        // If attendance was closed, mark slot as unavailable
        if (attendanceClosed) {
            available = 0;
            state = SlotState.FULL; // Mark as FULL to prevent registration
        }

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
        
        // Set session status fields for client-side filtering
        card.setAttendanceOpenedAt(slot.getAttendanceOpenedAt() != null ? DATE_TIME_FORMAT.format(slot.getAttendanceOpenedAt()) : null);
        card.setAttendanceClosesAt(slot.getAttendanceClosesAt() != null ? DATE_TIME_FORMAT.format(slot.getAttendanceClosesAt()) : null);
        card.setHasClosedSession(attendanceClosed);

        boolean canRegister = presenterUsername != null
                && !presenterInThisSlot
                && !presenterRegisteredElsewhere
                && available > 0
                && !attendanceClosed; // Cannot register if attendance was closed

        card.setDisableReason(null);

        User.Degree effectiveDegree = presenterDegree != null ? presenterDegree : User.Degree.MSc;

        if (attendanceClosed) {
            canRegister = false;
            card.setDisableReason("Slot attendance has closed");
        } else if (slotLockedByPhd) {
            canRegister = false;
            if (!presenterInThisSlot) {
                card.setDisableReason("Slot locked by PhD presenter");
            }
        } else if (effectiveDegree == User.Degree.PhD && slotHasMsc && !presenterInThisSlot) {
            canRegister = false;
            card.setDisableReason("Slot already has an MSc presenter");
        } else if (state == SlotState.FULL) {
            canRegister = false;
            card.setDisableReason("Slot is full");
        } else if (presenterInThisSlot) {
            canRegister = false;
            card.setDisableReason("You are already registered in this slot");
        } else if (presenterRegisteredElsewhere) {
            canRegister = false;
            card.setDisableReason("You are registered in another slot");
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

        LocalDateTime now = LocalDateTime.now();
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

    private LocalDateTime toSlotStart(SeminarSlot slot) {
        if (slot.getSlotDate() == null || slot.getStartTime() == null) {
            return null;
        }
        return LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
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
                    boolean timeMatches = Math.abs(java.time.Duration.between(slotStart, session.getStartTime()).toMinutes()) <= 1;
                    
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
     * Find ANY OTHER presenter's OPEN session for a specific slot
     * Returns null if no other presenter has an open session
     * CRITICAL: Checks if session matches slot by time AND location, and belongs to a different presenter
     */
    private Session findOtherPresenterOpenSessionForSlot(String presenterUsername, Long slotId, SeminarSlot slot, List<Session> allOpenSessions) {
        LocalDateTime slotStart = toSlotStart(slot);
        LocalDateTime slotEnd = toSlotEnd(slot);
        String slotLocation = buildLocation(slot);
        
        logger.debug("Checking for other presenter's sessions for slot {} (time: {} to {}, location: {})", 
            slotId, slotStart, slotEnd, slotLocation);
        
        return allOpenSessions.stream()
            .filter(session -> {
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
                    boolean timeMatches = Math.abs(java.time.Duration.between(slotStart, session.getStartTime()).toMinutes()) <= 1;
                    
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
        LocalDateTime now = LocalDateTime.now();
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
        session.setStartTime(toSlotStart(slot));
        session.setEndTime(toSlotEnd(slot));
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
            sessionRepository.findById(slot.getLegacySessionId()).ifPresent(this::closeLegacySession);
        }
    }

    private void closeLegacySession(Session session) {
        if (session.getStatus() != Session.SessionStatus.CLOSED) {
            session.setStatus(Session.SessionStatus.CLOSED);
            session.setEndTime(LocalDateTime.now());
            sessionRepository.save(session);
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

