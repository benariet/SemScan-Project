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
import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
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

    public PresenterHomeService(UserRepository userRepository,
                                SeminarSlotRepository seminarSlotRepository,
                                SeminarSlotRegistrationRepository registrationRepository,
                                SeminarRepository seminarRepository,
                                SessionRepository sessionRepository,
                                GlobalConfig globalConfig) {
        this.userRepository = userRepository;
        this.seminarSlotRepository = seminarSlotRepository;
        this.registrationRepository = registrationRepository;
        this.seminarRepository = seminarRepository;
        this.sessionRepository = sessionRepository;
        this.globalConfig = globalConfig;
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

    @Transactional
    public PresenterSlotRegistrationResponse registerForSlot(String presenterUsernameParam,
                                                             Long slotId,
                                                             PresenterSlotRegistrationRequest request) {
        String normalizedUsername = normalizeUsername(presenterUsernameParam);
        logger.info("Registering presenter {} to slot {}", normalizedUsername, slotId);

        User presenter = findPresenterByUsername(normalizedUsername);

        String presenterUsername = normalizeUsername(presenter.getBguUsername());
        if (presenterUsername == null) {
            return new PresenterSlotRegistrationResponse(false, "Presenter is missing BGU username", "MISSING_USERNAME");
        }

        SeminarSlot slot = seminarSlotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + slotId));
        List<SeminarSlotRegistration> existingRegistrations = registrationRepository.findByIdSlotId(slotId);
        boolean existingPhd = existingRegistrations.stream()
                .anyMatch(reg -> User.Degree.PhD == reg.getDegree());

        if (registrationRepository.existsByIdSlotIdAndIdPresenterUsername(slotId, presenterUsername)) {
            logger.info("Presenter {} already registered to slot {}", presenterUsername, slotId);
            return new PresenterSlotRegistrationResponse(true, "Already registered for this slot", "ALREADY_IN_SLOT");
        }

        if (registrationRepository.existsByIdPresenterUsername(presenterUsername)) {
            return new PresenterSlotRegistrationResponse(false, "Presenter already registered in another slot", "ALREADY_REGISTERED");
        }

        if (existingPhd) {
            return new PresenterSlotRegistrationResponse(false, "Slot locked by PhD presenter", "SLOT_LOCKED");
        }

        User.Degree presenterDegree = presenter.getDegree() != null ? presenter.getDegree() : User.Degree.MSc;

        if (presenterDegree == User.Degree.PhD) {
            if (!existingRegistrations.isEmpty()) {
                return new PresenterSlotRegistrationResponse(false, "Slot already has an MSc presenter", "PHD_BLOCKED");
            }
        } else {
            int capacity = slot.getCapacity() != null ? slot.getCapacity() : 0;
            if (existingRegistrations.size() >= capacity) {
                updateSlotStatus(slot, SlotState.FULL);
                return new PresenterSlotRegistrationResponse(false, "Slot is already full", "SLOT_FULL");
            }
        }

        SeminarSlotRegistration registration = new SeminarSlotRegistration();
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

        logger.info("Presenter {} successfully registered to slot {}", presenterUsername, slotId);
        return new PresenterSlotRegistrationResponse(true, "Registered successfully", "REGISTERED");
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
        return new PresenterSlotRegistrationResponse(true, "Registration cancelled", "UNREGISTERED");
    }

    @Transactional
    public PresenterOpenAttendanceResponse openAttendance(String presenterUsernameParam, Long slotId) {
        String normalizedUsername = normalizeUsername(presenterUsernameParam);
        logger.info("Opening attendance for presenter {} on slot {}", normalizedUsername, slotId);

        User presenter = findPresenterByUsername(normalizedUsername);
        String presenterUsername = normalizeUsername(presenter.getBguUsername());
        if (presenterUsername == null) {
            return new PresenterOpenAttendanceResponse(false, "Presenter is missing BGU username", "MISSING_USERNAME", null, null, null, null, null);
        }

        SeminarSlot slot = seminarSlotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found: " + slotId));

        if (!registrationRepository.existsByIdSlotIdAndIdPresenterUsername(slotId, presenterUsername)) {
            return new PresenterOpenAttendanceResponse(false, "Presenter is not registered for this slot", "NOT_REGISTERED", null, null, null, null, null);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = toSlotStart(slot);
        if (start == null) {
            return new PresenterOpenAttendanceResponse(false, "Slot start time not configured", "NO_SCHEDULE", null, null, null, null, null);
        }

        LocalDateTime end = toSlotEnd(slot);
        LocalDateTime openWindow = start.minusMinutes(10);
        
        // Check if too early (before 10 minutes before start)
        // Allow opening during the slot time (from 10 minutes before start until slot ends, or indefinitely if no end time)
        if (now.isBefore(openWindow)) {
            String openWindowStr = DATE_TIME_FORMAT.format(openWindow);
            return new PresenterOpenAttendanceResponse(false,
                    String.format("Cannot start session. Attendance can only be opened 10 minutes before the slot start time (at %s)", openWindowStr),
                    "TOO_EARLY",
                    null,
                    openWindowStr,
                    null,
                    null,
                    null);
        }
        
        // Check if too late (after slot ends) - only if slot has an end time
        if (end != null && now.isAfter(end)) {
            return new PresenterOpenAttendanceResponse(false,
                    String.format("Too late: Slot has ended at %s", DATE_TIME_FORMAT.format(end)),
                    "TOO_LATE",
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        
        // Allow opening if: now >= openWindow (10 minutes before start) AND (end is null OR now <= end)
        // This covers both: 10 minutes before start AND during the slot time

        LocalDateTime openedAt = slot.getAttendanceOpenedAt();
        LocalDateTime closesAt = slot.getAttendanceClosesAt();
        String openedBy = normalizeUsername(slot.getAttendanceOpenedBy());

        Session activeLegacySession = null;
        if (slot.getLegacySessionId() != null) {
            activeLegacySession = sessionRepository.findById(slot.getLegacySessionId()).orElse(null);
            if (activeLegacySession == null) {
                slot.setLegacySessionId(null);
            } else if (closesAt != null && now.isAfter(closesAt)) {
                closeLegacySession(activeLegacySession);
                activeLegacySession = null;
                slot.setLegacySessionId(null);
                slot.setAttendanceOpenedAt(null);
                slot.setAttendanceClosesAt(null);
                slot.setAttendanceOpenedBy(null);
                openedAt = null;
                closesAt = null;
            }
        }

        if (openedAt != null && closesAt != null && now.isBefore(closesAt) && slot.getLegacySessionId() != null) {
            String qrUrl = buildQrUrl(slot.getLegacySessionId());
            String payload = buildQrPayload(slot.getLegacySessionId());
            if (Objects.equals(presenterUsername, openedBy)) {
                return new PresenterOpenAttendanceResponse(true,
                        "Attendance already open",
                        "ALREADY_OPEN",
                        qrUrl,
                        DATE_TIME_FORMAT.format(openedAt),
                        DATE_TIME_FORMAT.format(closesAt),
                        slot.getLegacySessionId(),
                        payload);
            }
            return new PresenterOpenAttendanceResponse(false,
                    "Attendance currently open",
                    "IN_PROGRESS",
                    qrUrl,
                    DATE_TIME_FORMAT.format(openedAt),
                    DATE_TIME_FORMAT.format(closesAt),
                    slot.getLegacySessionId(),
                    payload);
        }

        if (activeLegacySession == null) {
            Seminar legacySeminar = ensureLegacySeminar(slot, presenter);
            activeLegacySession = createLegacySession(slot, legacySeminar);
            slot.setLegacySeminarId(legacySeminar.getSeminarId());
            slot.setLegacySessionId(activeLegacySession.getSessionId());
        }

        slot.setAttendanceOpenedAt(now);
        slot.setAttendanceClosesAt(now.plusMinutes(15));
        slot.setAttendanceOpenedBy(presenterUsername);
        seminarSlotRepository.save(slot);

        String qrUrl = buildQrUrl(slot.getLegacySessionId());
        LocalDateTime newClosesAt = slot.getAttendanceClosesAt();
        return new PresenterOpenAttendanceResponse(true,
                "Attendance opened",
                "OPENED",
                qrUrl,
                DATE_TIME_FORMAT.format(now),
                newClosesAt != null ? DATE_TIME_FORMAT.format(newClosesAt) : null,
                slot.getLegacySessionId(),
                buildQrPayload(slot.getLegacySessionId()));
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
     * Get QR code data for a slot
     * Returns QR code information if attendance is open for the slot
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
            throw new IllegalArgumentException("Presenter is not registered for this slot");
        }

        // Check if attendance is open (has a session)
        if (slot.getLegacySessionId() == null) {
            throw new IllegalStateException("Attendance is not open for this slot");
        }

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

        // Get the session
        Session session = sessionRepository.findById(slot.getLegacySessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + slot.getLegacySessionId()));

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

    private Seminar ensureLegacySeminar(SeminarSlot slot, User presenter) {
        if (slot.getLegacySeminarId() != null) {
            Optional<Seminar> existing = seminarRepository.findById(slot.getLegacySeminarId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Seminar seminar = new Seminar();
        seminar.setPresenterUsername(presenter.getBguUsername());
        seminar.setSeminarName(buildSeminarName(slot));
        seminar.setDescription("Auto-generated seminar for slot " + buildSeminarName(slot));
        seminar.setMaxEnrollmentCapacity(slot.getCapacity() != null ? slot.getCapacity() : 0);
        Seminar saved = seminarRepository.save(seminar);
        slot.setLegacySeminarId(saved.getSeminarId());
        return saved;
    }

    private Session createLegacySession(SeminarSlot slot, Seminar seminar) {
        Session session = new Session();
        session.setSeminarId(seminar.getSeminarId());
        session.setStartTime(toSlotStart(slot));
        session.setEndTime(toSlotEnd(slot));
        session.setStatus(Session.SessionStatus.OPEN);
        session.setLocation(buildLocation(slot));
        return sessionRepository.save(session);
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

