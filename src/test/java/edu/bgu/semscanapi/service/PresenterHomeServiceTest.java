package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.dto.PresenterHomeResponse;
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
import edu.bgu.semscanapi.repository.SeminarRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.repository.WaitingListPromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * Comprehensive unit tests for PresenterHomeService
 * Tests all public methods with high coverage
 */
@ExtendWith(MockitoExtension.class)
class PresenterHomeServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SeminarSlotRepository seminarSlotRepository;

    @Mock
    private SeminarSlotRegistrationRepository registrationRepository;

    @Mock
    private SeminarRepository seminarRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private GlobalConfig globalConfig;

    @Mock
    private EmailService emailService;

    @Mock
    private DatabaseLoggerService databaseLoggerService;

    @Mock
    private RegistrationApprovalService approvalService;

    @Mock
    private WaitingListService waitingListService;

    @Mock
    private MailService mailService;

    @Mock
    private AppConfigService appConfigService;

    @Mock
    private WaitingListPromotionRepository waitingListPromotionRepository;

    @Mock
    private EmailQueueService emailQueueService;

    @Mock
    private FcmService fcmService;

    @InjectMocks
    private PresenterHomeService presenterHomeService;

    private User testPresenter;
    private SeminarSlot testSlot;
    private Seminar testSeminar;
    private Session testSession;
    private SeminarSlotRegistration testRegistration;

    @BeforeEach
    void setUp() {
        // Setup default mocks for services
        lenient().when(approvalService.sendApprovalEmail(any(SeminarSlotRegistration.class))).thenReturn(true);
        // Mock time window configs - 15 minutes before/after for most windows
        lenient().when(appConfigService.getIntegerConfig(anyString(), anyInt())).thenReturn(15);
        lenient().when(appConfigService.getIntegerConfig(eq("presenter_slot_open_window_before_minutes"), anyInt())).thenReturn(15);
        lenient().when(appConfigService.getIntegerConfig(eq("presenter_slot_open_window_after_minutes"), anyInt())).thenReturn(15);
        lenient().when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt())).thenReturn(15);
        lenient().when(waitingListService.isOnWaitingList(anyLong(), anyString())).thenReturn(false);
        // Mock email queue validation to always return valid
        lenient().when(emailQueueService.validateSupervisorEmail(anyString(), anyString()))
            .thenReturn(new EmailQueueService.EmailValidationResult(true, null, null));
        
        // Setup test presenter
        testPresenter = new User();
        testPresenter.setBguUsername("testuser");
        testPresenter.setFirstName("Test");
        testPresenter.setLastName("User");
        testPresenter.setDegree(User.Degree.MSc);
        testPresenter.setEmail("test@example.com");

        // Setup test slot
        testSlot = new SeminarSlot();
        testSlot.setSlotId(1L);
        testSlot.setSlotDate(LocalDate.now().plusDays(1));
        testSlot.setStartTime(LocalTime.of(10, 0));
        testSlot.setEndTime(LocalTime.of(11, 0));
        testSlot.setBuilding("Building 37");
        testSlot.setRoom("Room 201");
        testSlot.setCapacity(10);
        testSlot.setStatus(SeminarSlot.SlotStatus.FREE);

        // Setup test seminar
        testSeminar = new Seminar();
        testSeminar.setSeminarId(1L);
        testSeminar.setPresenterUsername("testuser");
        testSeminar.setSeminarName("Test Seminar");

        // Setup test session
        testSession = new Session();
        testSession.setSessionId(1L);
        testSession.setSeminarId(1L);
        testSession.setStartTime(LocalDateTime.of(testSlot.getSlotDate(), testSlot.getStartTime()));
        testSession.setEndTime(LocalDateTime.of(testSlot.getSlotDate(), testSlot.getEndTime()));
        testSession.setStatus(Session.SessionStatus.OPEN);
        testSession.setLocation("Building 37 Room 201");

        // Setup test registration
        SeminarSlotRegistrationId registrationId = new SeminarSlotRegistrationId(1L, "testuser");
        testRegistration = new SeminarSlotRegistration();
        testRegistration.setId(registrationId);
        testRegistration.setDegree(User.Degree.MSc);
        testRegistration.setTopic("Test Topic");
        testRegistration.setApprovalStatus(ApprovalStatus.APPROVED); // Most tests expect APPROVED status

        // Setup global config mocks (lenient since not all tests use them)
        lenient().when(globalConfig.getServerUrl()).thenReturn("http://localhost:8080");
        lenient().when(globalConfig.getApiBaseUrl()).thenReturn("http://localhost:8080/api/v1");
        // Mock registration limits - needed for registerForSlot tests
        lenient().when(globalConfig.getMaxApprovedRegistrations()).thenReturn(1);
        lenient().when(globalConfig.getMaxPendingRegistrationsPhd()).thenReturn(1);
        lenient().when(globalConfig.getMaxPendingRegistrationsMsc()).thenReturn(2);

        // Setup seminar repository mocks for legacy seminar handling
        lenient().when(seminarRepository.findByPresenterUsername(anyString())).thenReturn(Collections.singletonList(testSeminar));
        lenient().when(seminarRepository.save(any(Seminar.class))).thenAnswer(invocation -> {
            Seminar s = invocation.getArgument(0);
            if (s.getSeminarId() == null) s.setSeminarId(1L);
            return s;
        });

        // Setup registration repository mock for findById - needed for openAttendance tests
        // This returns an APPROVED registration by default
        lenient().when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                .thenReturn(Optional.of(testRegistration));

        // Setup session repository mocks
        lenient().when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            if (s.getSessionId() == null) s.setSessionId(1L);
            return s;
        });
        lenient().when(sessionRepository.findOpenSessions()).thenReturn(Collections.emptyList());
        lenient().when(sessionRepository.findById(anyLong())).thenReturn(Optional.of(testSession));
    }

    // ==================== getPresenterHome Tests ====================

    @Test
    void getPresenterHome_WithValidPresenter_ReturnsCompleteResponse() {
        // Given
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findBySlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(any(LocalDate.class)))
                .thenReturn(Collections.singletonList(testSlot));
        when(registrationRepository.findByIdSlotIdIn(anyList())).thenReturn(Collections.singletonList(testRegistration));
        when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.singletonList(testRegistration));
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));

        // When
        PresenterHomeResponse response = presenterHomeService.getPresenterHome("testuser");

        // Then
        assertNotNull(response);
        assertNotNull(response.getPresenter());
        assertNotNull(response.getMySlot());
        assertNotNull(response.getSlotCatalog());
        assertNotNull(response.getAttendance());
    }

    @Test
    void getPresenterHome_WithNoSlots_ReturnsEmptyCatalog() {
        // Given
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findBySlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(seminarSlotRepository.findAllByOrderBySlotDateAscStartTimeAsc())
                .thenReturn(Collections.emptyList());
        when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.emptyList());

        // When
        PresenterHomeResponse response = presenterHomeService.getPresenterHome("testuser");

        // Then
        assertNotNull(response);
        assertTrue(response.getSlotCatalog().isEmpty());
    }

    @Test
    void getPresenterHome_WithNullUsername_ThrowsException() {
        // Given - No mocking needed, exception is thrown before repository call

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            presenterHomeService.getPresenterHome(null);
        });
    }

    // ==================== registerForSlot Tests ====================

    @Test
    void registerForSlot_WithValidRequest_RegistersSuccessfully() {
        // Given
        PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
        request.setTopic("Test Topic");
        request.setSupervisorEmail("supervisor@example.com");
        request.setSupervisorName("Dr. Supervisor");

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        // Service now uses existsActiveRegistration to check for PENDING/APPROVED registrations
        when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(false);
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
        when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.emptyList());
        when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalService.sendApprovalEmail(any(SeminarSlotRegistration.class))).thenReturn(true);
        lenient().when(appConfigService.getIntegerConfig(anyString(), anyInt())).thenReturn(15);

        // When
        PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("testuser", 1L, request);

        // Then - with supervisor email, registration goes through approval flow
        assertTrue(response.isSuccess());
        assertEquals("PENDING_APPROVAL", response.getCode());
        verify(registrationRepository).saveAndFlush(any(SeminarSlotRegistration.class));
        verify(seminarSlotRepository, atLeastOnce()).save(any(SeminarSlot.class));
    }

    @Test
    void registerForSlot_WhenAlreadyRegistered_ReturnsAlreadyRegistered() {
        // Given
        PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        // Service now uses existsActiveRegistration which only returns true for PENDING/APPROVED status
        when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(true);

        // When
        PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("testuser", 1L, request);

        // Then
        // When already registered, the service returns success=true with code "ALREADY_IN_SLOT"
        assertTrue(response.isSuccess());
        assertEquals("ALREADY_IN_SLOT", response.getCode());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void registerForSlot_WithMissingUsername_ReturnsError() {
        // Given
        testPresenter.setBguUsername(null);
        PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));

        // When
        PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("testuser", 1L, request);

        // Then
        assertFalse(response.isSuccess());
        assertEquals("MISSING_USERNAME", response.getCode());
    }

    @Test
    void registerForSlot_WithSupervisorEmail_SendsApprovalEmail() {
        // Given
        PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
        request.setSupervisorEmail("supervisor@example.com");
        request.setSupervisorName("Supervisor Name");

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(false);
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
        when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.emptyList());
        when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalService.sendApprovalEmail(any(SeminarSlotRegistration.class))).thenReturn(true);

        // When
        PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("testuser", 1L, request);

        // Then - with supervisor email, goes through approval flow
        assertTrue(response.isSuccess());
        assertEquals("PENDING_APPROVAL", response.getCode());
        // Verify approval email is sent via approvalService
        verify(approvalService, atLeastOnce()).sendApprovalEmail(any(SeminarSlotRegistration.class));
    }

    // ==================== unregisterFromSlot Tests ====================

    @Test
    void unregisterFromSlot_WithValidRegistration_UnregistersSuccessfully() {
        // Given
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                .thenReturn(Optional.of(testRegistration));
        when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
        when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PresenterSlotRegistrationResponse response = presenterHomeService.unregisterFromSlot("testuser", 1L);

        // Then
        assertTrue(response.isSuccess());
        assertEquals("UNREGISTERED", response.getCode());
        verify(registrationRepository).delete(testRegistration);
    }

    @Test
    void unregisterFromSlot_WhenNotRegistered_ReturnsError() {
        // Given
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                .thenReturn(Optional.empty());

        // When
        PresenterSlotRegistrationResponse response = presenterHomeService.unregisterFromSlot("testuser", 1L);

        // Then
        assertFalse(response.isSuccess());
        assertEquals("NOT_REGISTERED", response.getCode());
        verify(registrationRepository, never()).delete(any());
    }

    // ==================== openAttendance Tests ====================

    @Test
    void openAttendance_WithValidSlot_CreatesNewSession() {
        // Given
        // Set slot to today with start time 5 minutes from now (within 10-minute window)
        LocalDateTime now = LocalDateTime.now();
        testSlot.setSlotDate(now.toLocalDate());
        testSlot.setStartTime(now.toLocalTime().plusMinutes(5)); // Start in 5 minutes
        testSlot.setEndTime(now.toLocalTime().plusHours(1)); // End 1 hour from now

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);
        when(sessionRepository.findOpenSessions()).thenReturn(Collections.emptyList());
        when(seminarRepository.findByPresenterUsername(anyString()))
                .thenReturn(Collections.emptyList());
        when(seminarRepository.save(any(Seminar.class))).thenAnswer(invocation -> {
            Seminar s = invocation.getArgument(0);
            s.setSeminarId(1L);
            return s;
        });
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            s.setSessionId(1L);
            s.setStatus(Session.SessionStatus.OPEN);
            return s;
        });
        // Mock findById to return the saved session (for verification in createLegacySession)
        when(sessionRepository.findById(1L)).thenAnswer(invocation -> {
            Session s = new Session();
            s.setSessionId(1L);
            s.setSeminarId(1L);
            s.setStatus(Session.SessionStatus.OPEN);
            s.setStartTime(LocalDateTime.of(testSlot.getSlotDate(), testSlot.getStartTime()));
            s.setLocation("Building " + testSlot.getBuilding() + " Room " + testSlot.getRoom());
            return Optional.of(s);
        });
        when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PresenterOpenAttendanceResponse response = presenterHomeService.openAttendance("testuser", 1L);

        // Then
        assertTrue(response.isSuccess());
        assertEquals("OPENED", response.getCode());
        assertNotNull(response.getSessionId());
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    void openAttendance_WhenAlreadyOpen_ReturnsAlreadyOpen() {
        // Given
        // Use Israel timezone to match service's time handling
        java.time.ZoneId israelTz = java.time.ZoneId.of("Asia/Jerusalem");
        LocalDateTime nowIsrael = java.time.ZonedDateTime.now(israelTz).toLocalDateTime();

        // Set slot and session to match by time and location
        testSlot.setSlotDate(nowIsrael.toLocalDate());
        testSlot.setStartTime(nowIsrael.toLocalTime().minusMinutes(5)); // Start was 5 minutes ago (within 15-min window)
        testSlot.setEndTime(nowIsrael.toLocalTime().plusHours(1));
        testSlot.setLegacySessionId(1L);

        testSession.setStartTime(LocalDateTime.of(testSlot.getSlotDate(), testSlot.getStartTime()));
        testSession.setLocation("Building 37 Room 201");
        
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);
        when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(testSession));
        when(seminarRepository.findById(1L)).thenReturn(Optional.of(testSeminar));
        // Make lenient since findByLegacySessionId may not be called depending on how session is matched
        lenient().when(seminarSlotRepository.findByLegacySessionId(1L)).thenReturn(Optional.of(testSlot));

        // When
        PresenterOpenAttendanceResponse response = presenterHomeService.openAttendance("testuser", 1L);

        // Then
        assertTrue(response.isSuccess());
        assertEquals("ALREADY_OPEN", response.getCode());
        assertEquals(1L, response.getSessionId());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void openAttendance_WhenTooEarly_ReturnsTooEarly() {
        // Given
        // Set slot start time to 30 minutes from now (too early - window opens 15 min before)
        LocalDateTime now = LocalDateTime.now();
        testSlot.setSlotDate(now.toLocalDate());
        testSlot.setStartTime(now.toLocalTime().plusMinutes(30)); // Start in 30 minutes (too early with 15 min window)
        testSlot.setEndTime(now.toLocalTime().plusHours(1).plusMinutes(30));

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);
        // findOpenSessions is not called when too early, so use lenient to avoid unnecessary stubbing warning
        lenient().when(sessionRepository.findOpenSessions()).thenReturn(Collections.emptyList());
        lenient().when(appConfigService.getIntegerConfig(anyString(), anyInt())).thenReturn(15);

        // When
        PresenterOpenAttendanceResponse response = presenterHomeService.openAttendance("testuser", 1L);

        // Then
        assertFalse(response.isSuccess());
        assertEquals("TOO_EARLY", response.getCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void openAttendance_WhenTooLate_ReturnsTooLate() {
        // Given
        // Set slot to today with end time more than 15 minutes in the past (window after is 15 min)
        LocalDateTime now = LocalDateTime.now();
        testSlot.setSlotDate(now.toLocalDate());
        testSlot.setStartTime(now.toLocalTime().minusHours(2)); // Start was 2 hours ago
        testSlot.setEndTime(now.toLocalTime().minusMinutes(20)); // End was 20 minutes ago (too late with 15 min window)

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);
        // findOpenSessions is not called when too late, so use lenient to avoid unnecessary stubbing warning
        lenient().when(sessionRepository.findOpenSessions()).thenReturn(Collections.emptyList());
        lenient().when(appConfigService.getIntegerConfig(anyString(), anyInt())).thenReturn(15);

        // When
        PresenterOpenAttendanceResponse response = presenterHomeService.openAttendance("testuser", 1L);

        // Then
        assertFalse(response.isSuccess());
        assertEquals("TOO_LATE", response.getCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void openAttendance_WhenNotRegistered_ReturnsNotRegistered() {
        // Given
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(false);

        // When
        PresenterOpenAttendanceResponse response = presenterHomeService.openAttendance("testuser", 1L);

        // Then
        assertFalse(response.isSuccess());
        assertEquals("NOT_REGISTERED", response.getCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void openAttendance_WhenOtherPresenterHasOpenSession_ReturnsInProgress() {
        // Given
        // Use Israel timezone to match service's time handling
        java.time.ZoneId israelTz = java.time.ZoneId.of("Asia/Jerusalem");
        LocalDateTime nowIsrael = java.time.ZonedDateTime.now(israelTz).toLocalDateTime();

        // Set slot to today with start time 5 minutes ago (well within the 15-minute session window)
        testSlot.setSlotDate(nowIsrael.toLocalDate());
        testSlot.setStartTime(nowIsrael.toLocalTime().minusMinutes(5)); // Start was 5 minutes ago
        testSlot.setEndTime(nowIsrael.toLocalTime().plusHours(1)); // End is 1 hour from now

        User otherPresenter = new User();
        otherPresenter.setBguUsername("otheruser");
        otherPresenter.setFirstName("Other");
        otherPresenter.setLastName("User");

        Seminar otherSeminar = new Seminar();
        otherSeminar.setSeminarId(2L);
        otherSeminar.setPresenterUsername("otheruser");

        Session otherSession = new Session();
        otherSession.setSessionId(2L);
        otherSession.setSeminarId(2L);
        // Session start time should match slot start time exactly
        otherSession.setStartTime(LocalDateTime.of(testSlot.getSlotDate(), testSlot.getStartTime()));
        otherSession.setStatus(Session.SessionStatus.OPEN);
        otherSession.setLocation("Building 37 Room 201");

        // Ensure slot has location set to match session location for the blocking check
        // buildLocation() creates "Building {building} Room {room}", so building="37", room="201"
        testSlot.setBuilding("37");
        testSlot.setRoom("201");

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);
        when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(otherSession));
        when(seminarRepository.findById(2L)).thenReturn(Optional.of(otherSeminar));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "otheruser")).thenReturn(true);
        when(userRepository.findByBguUsernameIgnoreCase("otheruser")).thenReturn(Optional.of(otherPresenter));

        // When
        PresenterOpenAttendanceResponse response = presenterHomeService.openAttendance("testuser", 1L);

        // Then
        assertFalse(response.isSuccess());
        assertEquals("IN_PROGRESS", response.getCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void openAttendance_WithClosedSessionReference_ClearsSlotFields() {
        // Given
        // Set slot to today with valid time window
        LocalDateTime now = LocalDateTime.now();
        testSlot.setSlotDate(now.toLocalDate());
        testSlot.setStartTime(now.toLocalTime().minusMinutes(15)); // Start was 15 minutes ago
        testSlot.setEndTime(now.toLocalTime().plusHours(1));
        
        Session closedSession = new Session();
        closedSession.setSessionId(99L);
        closedSession.setStatus(Session.SessionStatus.CLOSED);

        testSlot.setLegacySessionId(99L);

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);
        when(sessionRepository.findOpenSessions()).thenReturn(Collections.emptyList());
        when(sessionRepository.findById(99L)).thenReturn(Optional.of(closedSession));
        when(seminarRepository.findByPresenterUsername(anyString()))
                .thenReturn(Collections.emptyList());
        when(seminarRepository.save(any(Seminar.class))).thenAnswer(invocation -> {
            Seminar s = invocation.getArgument(0);
            s.setSeminarId(1L);
            return s;
        });
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            s.setSessionId(1L);
            s.setStatus(Session.SessionStatus.OPEN);
            return s;
        });
        // Mock findById to return the saved session (for verification in createLegacySession)
        when(sessionRepository.findById(1L)).thenAnswer(invocation -> {
            Session s = new Session();
            s.setSessionId(1L);
            s.setSeminarId(1L);
            s.setStatus(Session.SessionStatus.OPEN);
            s.setStartTime(LocalDateTime.of(testSlot.getSlotDate(), testSlot.getStartTime()));
            s.setLocation("Building " + testSlot.getBuilding() + " Room " + testSlot.getRoom());
            return Optional.of(s);
        });
        when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        presenterHomeService.openAttendance("testuser", 1L);

        // Then
        ArgumentCaptor<SeminarSlot> slotCaptor = ArgumentCaptor.forClass(SeminarSlot.class);
        verify(seminarSlotRepository, atLeastOnce()).save(slotCaptor.capture());
        // Verify that slot was saved (closed session reference should be cleared and new session ID set)
        assertTrue(slotCaptor.getAllValues().size() > 0);
    }

    // ==================== getSlotQRCode Tests ====================

    @Test
    void getSlotQRCode_WithOpenSession_ReturnsQRCode() {
        // Given
        // Set slot and session to match by time and location
        LocalDateTime now = LocalDateTime.now();
        testSlot.setSlotDate(now.toLocalDate());
        testSlot.setStartTime(now.toLocalTime().minusMinutes(15));
        testSlot.setLegacySessionId(1L);
        testSlot.setAttendanceOpenedAt(now.minusMinutes(5)); // Set attendance opened time
        testSlot.setAttendanceClosesAt(now.plusMinutes(10)); // Set attendance closes time
        
        testSession.setStartTime(LocalDateTime.of(testSlot.getSlotDate(), testSlot.getStartTime()));
        testSession.setLocation("Building 37 Room 201");
        testSeminar.setPresenterUsername("testuser"); // Ensure seminar belongs to test presenter

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);
        when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(testSession));
        when(seminarRepository.findById(1L)).thenReturn(Optional.of(testSeminar));
        // Mock globalConfig for QR code generation
        when(globalConfig.getSessionsEndpoint()).thenReturn("https://example.com/api/v1/sessions");
        when(globalConfig.getServerUrl()).thenReturn("https://example.com");
        when(globalConfig.getApiBaseUrl()).thenReturn("https://example.com/api/v1");
        when(globalConfig.getEnvironment()).thenReturn("test");

        // When
        Map<String, Object> qrCode = presenterHomeService.getSlotQRCode("testuser", 1L);

        // Then
        assertNotNull(qrCode);
        assertTrue(qrCode.containsKey("sessionId"));
        assertTrue(qrCode.containsKey("qrContent"));
    }

    @Test
    void getSlotQRCode_WithNoOpenSession_ThrowsException() {
        // Given
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);
        when(sessionRepository.findOpenSessions()).thenReturn(Collections.emptyList());

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            presenterHomeService.getSlotQRCode("testuser", 1L);
        });
    }

    @Test
    void getSlotQRCode_WithSessionFromDifferentPresenter_ThrowsException() {
        // Given
        // Set slot to today with valid time
        LocalDateTime now = LocalDateTime.now();
        testSlot.setSlotDate(now.toLocalDate());
        testSlot.setStartTime(now.toLocalTime().minusMinutes(15));
        // buildLocation() creates "Building {building} Room {room}", so building="37", room="201"
        testSlot.setBuilding("37");
        testSlot.setRoom("201");
        
        Seminar otherSeminar = new Seminar();
        otherSeminar.setSeminarId(2L);
        otherSeminar.setPresenterUsername("otheruser");

        Session otherSession = new Session();
        otherSession.setSessionId(2L);
        otherSession.setSeminarId(2L);
        otherSession.setStartTime(LocalDateTime.of(testSlot.getSlotDate(), testSlot.getStartTime()));
        otherSession.setStatus(Session.SessionStatus.OPEN);
        otherSession.setLocation("Building 37 Room 201");

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);
        when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(otherSession));
        when(seminarRepository.findById(2L)).thenReturn(Optional.of(otherSeminar));

        // When & Then
        // getSlotQRCode looks for THIS presenter's session, not other presenter's session
        // Since no session exists for this presenter, it will throw an exception
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            presenterHomeService.getSlotQRCode("testuser", 1L);
        });
        // The exception message should indicate no open session found for this presenter
        assertTrue(exception.getMessage().contains("No open session found") || 
                   exception.getMessage().contains("Please open attendance first"));
    }

    // ==================== getAllSlots Tests ====================

    @Test
    void getAllSlots_WithSlots_ReturnsSlotCatalog() {
        // Given
        when(seminarSlotRepository.findBySlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(any(LocalDate.class)))
                .thenReturn(Collections.singletonList(testSlot));
        when(registrationRepository.findByIdSlotIdIn(anyList())).thenReturn(Collections.emptyList());
        lenient().when(appConfigService.getIntegerConfig(anyString(), anyInt())).thenReturn(15);

        // When
        List<PresenterHomeResponse.SlotCard> slots = presenterHomeService.getAllSlots();

        // Then
        assertNotNull(slots);
        assertEquals(1, slots.size());
    }

    @Test
    void getAllSlots_WithNoSlots_ReturnsEmptyList() {
        // Given
        when(seminarSlotRepository.findBySlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(seminarSlotRepository.findAllByOrderBySlotDateAscStartTimeAsc())
                .thenReturn(Collections.emptyList());

        // When
        List<PresenterHomeResponse.SlotCard> slots = presenterHomeService.getAllSlots();

        // Then
        assertNotNull(slots);
        assertTrue(slots.isEmpty());
    }

    // ==================== sendSupervisorEmail Tests ====================

    @Test
    void sendSupervisorEmail_WithValidEmail_SendsSuccessfully() {
        // Given
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                .thenReturn(Optional.of(testRegistration));
        when(emailService.sendSupervisorNotificationEmail(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EmailService.EmailResult.success());
        lenient().when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PresenterSlotRegistrationResponse response = presenterHomeService.sendSupervisorEmail(
                "testuser", 1L, "supervisor@example.com", "Supervisor Name");

        // Then
        assertTrue(response.isSuccess());
        assertEquals("EMAIL_SENT", response.getCode());
        assertTrue(response.getHasSupervisorEmail());
        verify(emailService).sendSupervisorNotificationEmail(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendSupervisorEmail_WithEmptyEmail_ReturnsError() {
        // Given
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));

        // When
        PresenterSlotRegistrationResponse response = presenterHomeService.sendSupervisorEmail(
                "testuser", 1L, "", "Supervisor Name");

        // Then
        assertFalse(response.isSuccess());
        assertEquals("NO_SUPERVISOR_EMAIL", response.getCode());
        verify(emailService, never()).sendSupervisorNotificationEmail(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendSupervisorEmail_WhenEmailFails_ReturnsError() {
        // Given
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                .thenReturn(Optional.of(testRegistration));
        when(emailService.sendSupervisorNotificationEmail(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EmailService.EmailResult.failure("Failed to send email", "EMAIL_SEND_FAILED"));

        // When
        PresenterSlotRegistrationResponse response = presenterHomeService.sendSupervisorEmail(
                "testuser", 1L, "supervisor@example.com", "Supervisor Name");

        // Then
        assertFalse(response.isSuccess());
        assertEquals("EMAIL_SEND_FAILED", response.getCode());
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    void openAttendance_WithNullSlotStartTime_ReturnsNoSchedule() {
        // Given
        testSlot.setSlotDate(null);
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);

        // When
        PresenterOpenAttendanceResponse response = presenterHomeService.openAttendance("testuser", 1L);

        // Then
        assertFalse(response.isSuccess());
        assertEquals("NO_SCHEDULE", response.getCode());
    }

    @Test
    void openAttendance_WithSlotNotFound_ThrowsException() {
        // Given
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            presenterHomeService.openAttendance("testuser", 1L);
        });
    }

    @Test
    void registerForSlot_WithSlotNotFound_ThrowsException() {
        // Given
        PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(false);
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            presenterHomeService.registerForSlot("testuser", 1L, request);
        });
    }

    @Test
    void unregisterFromSlot_WithSlotNotFound_ThrowsException() {
        // Given
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            presenterHomeService.unregisterFromSlot("testuser", 1L);
        });
    }

    @Test
    void getSlotQRCode_WithSlotNotFound_ThrowsException() {
        // Given
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            presenterHomeService.getSlotQRCode("testuser", 1L);
        });
    }

    @Test
    void openAttendance_WithSessionCreationFailure_ThrowsException() {
        // Given
        // Set slot to today with valid time window
        LocalDateTime now = LocalDateTime.now();
        testSlot.setSlotDate(now.toLocalDate());
        testSlot.setStartTime(now.toLocalTime().minusMinutes(15)); // Start was 15 minutes ago
        testSlot.setEndTime(now.toLocalTime().plusHours(1));

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);
        when(sessionRepository.findOpenSessions()).thenReturn(Collections.emptyList());
        when(seminarRepository.findByPresenterUsername(anyString()))
                .thenReturn(Collections.emptyList());
        when(seminarRepository.save(any(Seminar.class))).thenAnswer(invocation -> {
            Seminar s = invocation.getArgument(0);
            s.setSeminarId(1L);
            return s;
        });
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            // Don't set sessionId to simulate save failure
            return s;
        });

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            presenterHomeService.openAttendance("testuser", 1L);
        });
    }

    // ==================== PhD/MSc Exclusivity Tests ====================

    @Nested
    @DisplayName("PhD/MSc Exclusivity Tests")
    class PhdMscExclusivityTests {

        private User phdPresenter;
        private User mscPresenter;
        private SeminarSlot emptySlot;

        @BeforeEach
        void setUpExclusivityTests() {
            // Setup PhD presenter
            phdPresenter = new User();
            phdPresenter.setBguUsername("phduser");
            phdPresenter.setFirstName("PhD");
            phdPresenter.setLastName("User");
            phdPresenter.setDegree(User.Degree.PhD);
            phdPresenter.setEmail("phd@example.com");

            // Setup MSc presenter
            mscPresenter = new User();
            mscPresenter.setBguUsername("mscuser");
            mscPresenter.setFirstName("MSc");
            mscPresenter.setLastName("User");
            mscPresenter.setDegree(User.Degree.MSc);
            mscPresenter.setEmail("msc@example.com");

            // Setup empty slot with capacity 3
            emptySlot = new SeminarSlot();
            emptySlot.setSlotId(100L);
            emptySlot.setSlotDate(LocalDate.now().plusDays(7));
            emptySlot.setStartTime(LocalTime.of(10, 0));
            emptySlot.setEndTime(LocalTime.of(11, 0));
            emptySlot.setBuilding("Building 37");
            emptySlot.setRoom("Room 201");
            emptySlot.setCapacity(3);
            emptySlot.setStatus(SeminarSlot.SlotStatus.FREE);

            // Default config mocks for capacity
            lenient().when(appConfigService.getIntegerConfig(eq("phd.capacity.weight"), anyInt())).thenReturn(3);
        }

        @Test
        @DisplayName("PhD registers to empty slot - SUCCESS")
        void phdRegistersToEmptySlot_Success() {
            // Given
            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("PhD Research Topic");
            request.setSupervisorEmail("supervisor@example.com");
            request.setSupervisorName("Dr. Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("phduser")).thenReturn(Optional.of(phdPresenter));
            when(registrationRepository.existsActiveRegistration(100L, "phduser")).thenReturn(false);
            when(seminarSlotRepository.findById(100L)).thenReturn(Optional.of(emptySlot));
            when(registrationRepository.findByIdSlotId(100L)).thenReturn(Collections.emptyList()); // Empty slot
            when(registrationRepository.findByIdPresenterUsername("phduser")).thenReturn(Collections.emptyList());
            when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(inv -> inv.getArgument(0));
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("phduser", 100L, request);

            // Then
            assertTrue(response.isSuccess());
            assertEquals("PENDING_APPROVAL", response.getCode());
            verify(registrationRepository).saveAndFlush(argThat(reg -> reg.getDegree() == User.Degree.PhD));
        }

        @Test
        @DisplayName("MSc registers to empty slot - SUCCESS")
        void mscRegistersToEmptySlot_Success() {
            // Given
            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("MSc Research Topic");
            request.setSupervisorEmail("supervisor@example.com");
            request.setSupervisorName("Dr. Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("mscuser")).thenReturn(Optional.of(mscPresenter));
            when(registrationRepository.existsActiveRegistration(100L, "mscuser")).thenReturn(false);
            when(seminarSlotRepository.findById(100L)).thenReturn(Optional.of(emptySlot));
            when(registrationRepository.findByIdSlotId(100L)).thenReturn(Collections.emptyList()); // Empty slot
            when(registrationRepository.findByIdPresenterUsername("mscuser")).thenReturn(Collections.emptyList());
            when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(inv -> inv.getArgument(0));
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("mscuser", 100L, request);

            // Then
            assertTrue(response.isSuccess());
            assertEquals("PENDING_APPROVAL", response.getCode());
        }

        @Test
        @DisplayName("MSc blocked by PhD - SLOT_LOCKED")
        void mscBlockedByPhd_SlotLocked() {
            // Given - Slot has a PhD registration
            SeminarSlotRegistration phdRegistration = new SeminarSlotRegistration();
            phdRegistration.setId(new SeminarSlotRegistrationId(100L, "existingphd"));
            phdRegistration.setDegree(User.Degree.PhD);
            phdRegistration.setApprovalStatus(ApprovalStatus.APPROVED);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("MSc Topic");
            request.setSupervisorEmail("supervisor@example.com");

            when(userRepository.findByBguUsernameIgnoreCase("mscuser")).thenReturn(Optional.of(mscPresenter));
            when(registrationRepository.existsActiveRegistration(100L, "mscuser")).thenReturn(false);
            // Service checks user registration limits first (returns empty list - no limits hit)
            when(registrationRepository.findByIdPresenterUsername("mscuser")).thenReturn(Collections.emptyList());
            // Then checks slot registrations for PhD/MSc exclusivity
            when(registrationRepository.findByIdSlotId(100L)).thenReturn(Collections.singletonList(phdRegistration));
            // Note: seminarSlotRepository.findById NOT stubbed - service returns at SLOT_LOCKED before calling it

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("mscuser", 100L, request);

            // Then
            assertFalse(response.isSuccess());
            assertEquals("SLOT_LOCKED", response.getCode());
            verify(registrationRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("PhD blocked by MSc - PHD_BLOCKED_BY_MSC")
        void phdBlockedByMsc_PhdBlockedByMsc() {
            // Given - Slot has an MSc registration
            SeminarSlotRegistration mscRegistration = new SeminarSlotRegistration();
            mscRegistration.setId(new SeminarSlotRegistrationId(100L, "existingmsc"));
            mscRegistration.setDegree(User.Degree.MSc);
            mscRegistration.setApprovalStatus(ApprovalStatus.APPROVED);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("PhD Topic");
            request.setSupervisorEmail("supervisor@example.com");

            when(userRepository.findByBguUsernameIgnoreCase("phduser")).thenReturn(Optional.of(phdPresenter));
            when(registrationRepository.existsActiveRegistration(100L, "phduser")).thenReturn(false);
            // Service checks user registration limits first (returns empty list - no limits hit)
            when(registrationRepository.findByIdPresenterUsername("phduser")).thenReturn(Collections.emptyList());
            // Then checks slot registrations - finds MSc, blocks PhD
            when(registrationRepository.findByIdSlotId(100L)).thenReturn(Collections.singletonList(mscRegistration));
            // Note: seminarSlotRepository.findById NOT stubbed - service returns at PHD_BLOCKED_BY_MSC before calling it

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("phduser", 100L, request);

            // Then
            assertFalse(response.isSuccess());
            assertEquals("PHD_BLOCKED_BY_MSC", response.getCode());
            verify(registrationRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Second MSc can register to slot with 1 MSc - SUCCESS")
        void secondMscRegistersToSlotWithOneMsc_Success() {
            // Given - Slot has 1 MSc registration
            SeminarSlotRegistration existingMsc = new SeminarSlotRegistration();
            existingMsc.setId(new SeminarSlotRegistrationId(100L, "msc1"));
            existingMsc.setDegree(User.Degree.MSc);
            existingMsc.setApprovalStatus(ApprovalStatus.APPROVED);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("MSc Topic 2");
            request.setSupervisorEmail("supervisor@example.com");
            request.setSupervisorName("Dr. Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("mscuser")).thenReturn(Optional.of(mscPresenter));
            when(registrationRepository.existsActiveRegistration(100L, "mscuser")).thenReturn(false);
            when(seminarSlotRepository.findById(100L)).thenReturn(Optional.of(emptySlot));
            when(registrationRepository.findByIdSlotId(100L)).thenReturn(Collections.singletonList(existingMsc));
            when(registrationRepository.findByIdPresenterUsername("mscuser")).thenReturn(Collections.emptyList());
            when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(inv -> inv.getArgument(0));
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("mscuser", 100L, request);

            // Then
            assertTrue(response.isSuccess());
            assertEquals("PENDING_APPROVAL", response.getCode());
        }

        @Test
        @DisplayName("Third MSc can register to slot with 2 MSc - SUCCESS")
        void thirdMscRegistersToSlotWithTwoMsc_Success() {
            // Given - Slot has 2 MSc registrations
            SeminarSlotRegistration msc1 = new SeminarSlotRegistration();
            msc1.setId(new SeminarSlotRegistrationId(100L, "msc1"));
            msc1.setDegree(User.Degree.MSc);
            msc1.setApprovalStatus(ApprovalStatus.APPROVED);

            SeminarSlotRegistration msc2 = new SeminarSlotRegistration();
            msc2.setId(new SeminarSlotRegistrationId(100L, "msc2"));
            msc2.setDegree(User.Degree.MSc);
            msc2.setApprovalStatus(ApprovalStatus.APPROVED);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("MSc Topic 3");
            request.setSupervisorEmail("supervisor@example.com");
            request.setSupervisorName("Dr. Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("mscuser")).thenReturn(Optional.of(mscPresenter));
            when(registrationRepository.existsActiveRegistration(100L, "mscuser")).thenReturn(false);
            when(seminarSlotRepository.findById(100L)).thenReturn(Optional.of(emptySlot));
            when(registrationRepository.findByIdSlotId(100L)).thenReturn(Arrays.asList(msc1, msc2));
            when(registrationRepository.findByIdPresenterUsername("mscuser")).thenReturn(Collections.emptyList());
            when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(inv -> inv.getArgument(0));
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("mscuser", 100L, request);

            // Then
            assertTrue(response.isSuccess());
            assertEquals("PENDING_APPROVAL", response.getCode());
        }

        @Test
        @DisplayName("Fourth MSc blocked when slot is full (3 MSc) - SLOT_FULL")
        void fourthMscBlockedWhenSlotFull_SlotFull() {
            // Given - Slot has 3 MSc registrations (full capacity)
            SeminarSlotRegistration msc1 = new SeminarSlotRegistration();
            msc1.setId(new SeminarSlotRegistrationId(100L, "msc1"));
            msc1.setDegree(User.Degree.MSc);
            msc1.setApprovalStatus(ApprovalStatus.APPROVED);

            SeminarSlotRegistration msc2 = new SeminarSlotRegistration();
            msc2.setId(new SeminarSlotRegistrationId(100L, "msc2"));
            msc2.setDegree(User.Degree.MSc);
            msc2.setApprovalStatus(ApprovalStatus.APPROVED);

            SeminarSlotRegistration msc3 = new SeminarSlotRegistration();
            msc3.setId(new SeminarSlotRegistrationId(100L, "msc3"));
            msc3.setDegree(User.Degree.MSc);
            msc3.setApprovalStatus(ApprovalStatus.APPROVED);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("MSc Topic 4");
            request.setSupervisorEmail("supervisor@example.com");

            when(userRepository.findByBguUsernameIgnoreCase("mscuser")).thenReturn(Optional.of(mscPresenter));
            when(registrationRepository.existsActiveRegistration(100L, "mscuser")).thenReturn(false);
            // Service checks user registration limits first
            when(registrationRepository.findByIdPresenterUsername("mscuser")).thenReturn(Collections.emptyList());
            // Then checks slot registrations - slot is full with 3 MSc
            when(registrationRepository.findByIdSlotId(100L)).thenReturn(Arrays.asList(msc1, msc2, msc3));
            // seminarSlotRepository.findById IS called because capacity check comes after exclusivity check
            when(seminarSlotRepository.findById(100L)).thenReturn(Optional.of(emptySlot));

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("mscuser", 100L, request);

            // Then
            assertFalse(response.isSuccess());
            assertEquals("SLOT_FULL", response.getCode());
            verify(registrationRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("PhD blocked by multiple MSc - PHD_BLOCKED_BY_MSC")
        void phdBlockedByMultipleMsc_PhdBlockedByMsc() {
            // Given - Slot has 2 MSc registrations
            SeminarSlotRegistration msc1 = new SeminarSlotRegistration();
            msc1.setId(new SeminarSlotRegistrationId(100L, "msc1"));
            msc1.setDegree(User.Degree.MSc);
            msc1.setApprovalStatus(ApprovalStatus.APPROVED);

            SeminarSlotRegistration msc2 = new SeminarSlotRegistration();
            msc2.setId(new SeminarSlotRegistrationId(100L, "msc2"));
            msc2.setDegree(User.Degree.MSc);
            msc2.setApprovalStatus(ApprovalStatus.PENDING);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("PhD Topic");
            request.setSupervisorEmail("supervisor@example.com");

            when(userRepository.findByBguUsernameIgnoreCase("phduser")).thenReturn(Optional.of(phdPresenter));
            when(registrationRepository.existsActiveRegistration(100L, "phduser")).thenReturn(false);
            // Service checks user registration limits first
            when(registrationRepository.findByIdPresenterUsername("phduser")).thenReturn(Collections.emptyList());
            // Then checks slot registrations - finds MSc, blocks PhD
            when(registrationRepository.findByIdSlotId(100L)).thenReturn(Arrays.asList(msc1, msc2));
            // Note: seminarSlotRepository.findById NOT stubbed - service returns at PHD_BLOCKED_BY_MSC before calling it

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("phduser", 100L, request);

            // Then
            assertFalse(response.isSuccess());
            assertEquals("PHD_BLOCKED_BY_MSC", response.getCode());
        }

        @Test
        @DisplayName("MSc blocked by pending PhD - SLOT_LOCKED")
        void mscBlockedByPendingPhd_SlotLocked() {
            // Given - Slot has a PENDING PhD registration
            SeminarSlotRegistration pendingPhd = new SeminarSlotRegistration();
            pendingPhd.setId(new SeminarSlotRegistrationId(100L, "pendingphd"));
            pendingPhd.setDegree(User.Degree.PhD);
            pendingPhd.setApprovalStatus(ApprovalStatus.PENDING);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("MSc Topic");
            request.setSupervisorEmail("supervisor@example.com");

            when(userRepository.findByBguUsernameIgnoreCase("mscuser")).thenReturn(Optional.of(mscPresenter));
            when(registrationRepository.existsActiveRegistration(100L, "mscuser")).thenReturn(false);
            // Service checks user registration limits first
            when(registrationRepository.findByIdPresenterUsername("mscuser")).thenReturn(Collections.emptyList());
            // Then checks slot registrations - finds pending PhD, blocks MSc
            when(registrationRepository.findByIdSlotId(100L)).thenReturn(Collections.singletonList(pendingPhd));
            // Note: seminarSlotRepository.findById NOT stubbed - service returns at SLOT_LOCKED before calling it

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("mscuser", 100L, request);

            // Then
            assertFalse(response.isSuccess());
            assertEquals("SLOT_LOCKED", response.getCode());
        }

        @Test
        @DisplayName("PhD can register if only DECLINED MSc exists - SUCCESS")
        void phdCanRegisterIfOnlyDeclinedMscExists_Success() {
            // Given - Slot has only a DECLINED MSc registration (doesn't count)
            SeminarSlotRegistration declinedMsc = new SeminarSlotRegistration();
            declinedMsc.setId(new SeminarSlotRegistrationId(100L, "declinedmsc"));
            declinedMsc.setDegree(User.Degree.MSc);
            declinedMsc.setApprovalStatus(ApprovalStatus.DECLINED);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("PhD Topic");
            request.setSupervisorEmail("supervisor@example.com");
            request.setSupervisorName("Dr. Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("phduser")).thenReturn(Optional.of(phdPresenter));
            when(registrationRepository.existsActiveRegistration(100L, "phduser")).thenReturn(false);
            when(seminarSlotRepository.findById(100L)).thenReturn(Optional.of(emptySlot));
            when(registrationRepository.findByIdSlotId(100L)).thenReturn(Collections.singletonList(declinedMsc));
            when(registrationRepository.findByIdPresenterUsername("phduser")).thenReturn(Collections.emptyList());
            when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(inv -> inv.getArgument(0));
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("phduser", 100L, request);

            // Then
            assertTrue(response.isSuccess());
            assertEquals("PENDING_APPROVAL", response.getCode());
        }

        @Test
        @DisplayName("MSc can register if only EXPIRED PhD exists - SUCCESS")
        void mscCanRegisterIfOnlyExpiredPhdExists_Success() {
            // Given - Slot has only an EXPIRED PhD registration (doesn't count)
            SeminarSlotRegistration expiredPhd = new SeminarSlotRegistration();
            expiredPhd.setId(new SeminarSlotRegistrationId(100L, "expiredphd"));
            expiredPhd.setDegree(User.Degree.PhD);
            expiredPhd.setApprovalStatus(ApprovalStatus.EXPIRED);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("MSc Topic");
            request.setSupervisorEmail("supervisor@example.com");
            request.setSupervisorName("Dr. Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("mscuser")).thenReturn(Optional.of(mscPresenter));
            when(registrationRepository.existsActiveRegistration(100L, "mscuser")).thenReturn(false);
            when(seminarSlotRepository.findById(100L)).thenReturn(Optional.of(emptySlot));
            when(registrationRepository.findByIdSlotId(100L)).thenReturn(Collections.singletonList(expiredPhd));
            when(registrationRepository.findByIdPresenterUsername("mscuser")).thenReturn(Collections.emptyList());
            when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(inv -> inv.getArgument(0));
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("mscuser", 100L, request);

            // Then
            assertTrue(response.isSuccess());
            assertEquals("PENDING_APPROVAL", response.getCode());
        }

        @Test
        @DisplayName("PhD takes full capacity (weight=3)")
        void phdTakesFullCapacity_CapacityCheck() {
            // Given - PhD weight is 3, slot capacity is 3
            // When PhD registers, effective usage becomes 3 (full)
            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("PhD Topic");
            request.setSupervisorEmail("supervisor@example.com");
            request.setSupervisorName("Dr. Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("phduser")).thenReturn(Optional.of(phdPresenter));
            when(registrationRepository.existsActiveRegistration(100L, "phduser")).thenReturn(false);
            when(seminarSlotRepository.findById(100L)).thenReturn(Optional.of(emptySlot));
            when(registrationRepository.findByIdSlotId(100L)).thenReturn(Collections.emptyList());
            when(registrationRepository.findByIdPresenterUsername("phduser")).thenReturn(Collections.emptyList());
            when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(inv -> inv.getArgument(0));
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> {
                SeminarSlot slot = inv.getArgument(0);
                // After PhD registers, status should become FULL
                assertEquals(SeminarSlot.SlotStatus.FULL, slot.getStatus());
                return slot;
            });

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("phduser", 100L, request);

            // Then
            assertTrue(response.isSuccess());
        }
    }

    // ==================== Slot Status Update Tests ====================

    @Nested
    @DisplayName("Slot Status Update Tests")
    class SlotStatusUpdateTests {

        @Test
        @DisplayName("Slot becomes SEMI after first MSc registers")
        void slotBecomesSemiAfterFirstMsc() {
            // Given
            testSlot.setStatus(SeminarSlot.SlotStatus.FREE);
            testSlot.setCapacity(3);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("MSc Topic");
            request.setSupervisorEmail("supervisor@example.com");
            request.setSupervisorName("Dr. Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
            when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(false);
            when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
            when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.emptyList());
            when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(inv -> inv.getArgument(0));
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));

            // Capture slot status change
            ArgumentCaptor<SeminarSlot> slotCaptor = ArgumentCaptor.forClass(SeminarSlot.class);

            // When
            presenterHomeService.registerForSlot("testuser", 1L, request);

            // Then
            verify(seminarSlotRepository, atLeastOnce()).save(slotCaptor.capture());
            SeminarSlot savedSlot = slotCaptor.getValue();
            assertEquals(SeminarSlot.SlotStatus.SEMI, savedSlot.getStatus());
        }
    }

    // ==================== Registration Limit Tests ====================

    @Nested
    @DisplayName("Registration Limit Tests")
    class RegistrationLimitTests {

        @Test
        @DisplayName("User blocked when max approved registrations reached")
        void userBlockedWhenMaxApprovedReached() {
            // Given - User already has an APPROVED registration
            SeminarSlotRegistration approvedReg = new SeminarSlotRegistration();
            approvedReg.setId(new SeminarSlotRegistrationId(99L, "testuser"));
            approvedReg.setDegree(User.Degree.MSc);
            approvedReg.setApprovalStatus(ApprovalStatus.APPROVED);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("Another Topic");
            request.setSupervisorEmail("supervisor@example.com");

            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
            when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(false);
            // User has an approved registration in another slot - this is checked BEFORE slot registrations
            when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.singletonList(approvedReg));
            // Max approved registrations = 1 - service returns at REGISTRATION_LIMIT_EXCEEDED
            when(globalConfig.getMaxApprovedRegistrations()).thenReturn(1);
            // Note: findByIdSlotId and seminarSlotRepository.findById NOT stubbed - service returns before calling them

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("testuser", 1L, request);

            // Then
            assertFalse(response.isSuccess());
            assertEquals("REGISTRATION_LIMIT_EXCEEDED", response.getCode());
            verify(registrationRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("MSc blocked when max pending registrations reached")
        void mscBlockedWhenMaxPendingReached() {
            // Given - MSc user already has 2 PENDING registrations
            SeminarSlotRegistration pending1 = new SeminarSlotRegistration();
            pending1.setId(new SeminarSlotRegistrationId(98L, "testuser"));
            pending1.setDegree(User.Degree.MSc);
            pending1.setApprovalStatus(ApprovalStatus.PENDING);

            SeminarSlotRegistration pending2 = new SeminarSlotRegistration();
            pending2.setId(new SeminarSlotRegistrationId(99L, "testuser"));
            pending2.setDegree(User.Degree.MSc);
            pending2.setApprovalStatus(ApprovalStatus.PENDING);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("Third Topic");
            request.setSupervisorEmail("supervisor@example.com");

            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
            when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(false);
            // User has 2 pending registrations - this is checked BEFORE slot registrations
            when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Arrays.asList(pending1, pending2));
            when(globalConfig.getMaxApprovedRegistrations()).thenReturn(1);
            when(globalConfig.getMaxPendingRegistrationsMsc()).thenReturn(2);
            // Note: findByIdSlotId and seminarSlotRepository.findById NOT stubbed - service returns at PENDING_LIMIT_EXCEEDED

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("testuser", 1L, request);

            // Then
            assertFalse(response.isSuccess());
            assertEquals("PENDING_LIMIT_EXCEEDED", response.getCode());
            verify(registrationRepository, never()).saveAndFlush(any());
        }
    }

    // ==================== Past Slot Registration Tests ====================

    @Nested
    @DisplayName("Past Slot Registration Tests")
    class PastSlotRegistrationTests {

        @Test
        @DisplayName("Registration blocked for past slot")
        void registerForSlot_PastSlot_Blocked() {
            // Given - Slot date was yesterday
            testSlot.setSlotDate(LocalDate.now().minusDays(1));

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("Topic");
            request.setSupervisorEmail("supervisor@example.com");

            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
            when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(false);
            when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.emptyList());
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
            when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("testuser", 1L, request);

            // Then
            assertFalse(response.isSuccess());
            assertEquals("SLOT_DATE_PASSED", response.getCode());
            verify(registrationRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Registration allowed for today's slot even if time has passed")
        void registerForSlot_TodaySlotPastTime_Allowed() {
            // Given - Slot is today but start time was 2 hours ago
            // The implementation only checks if date is before today, not time
            testSlot.setSlotDate(LocalDate.now());
            testSlot.setStartTime(LocalTime.now().minusHours(2));
            testSlot.setEndTime(LocalTime.now().minusHours(1));

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("Topic");
            request.setSupervisorEmail("supervisor@example.com");
            request.setSupervisorName("Dr. Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
            when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(false);
            when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.emptyList());
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
            when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(inv -> inv.getArgument(0));
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("testuser", 1L, request);

            // Then - Registration succeeds because only date (not time) is checked
            assertTrue(response.isSuccess());
            assertEquals("PENDING_APPROVAL", response.getCode());
        }
    }

    // ==================== Re-registration After DECLINED/EXPIRED Tests ====================

    @Nested
    @DisplayName("Re-registration Tests")
    class ReRegistrationTests {

        @Test
        @DisplayName("User can re-register after DECLINED registration")
        void reRegisterAfterDeclined_Success() {
            // Given - User has a DECLINED registration for this slot
            SeminarSlotRegistration declinedReg = new SeminarSlotRegistration();
            declinedReg.setId(new SeminarSlotRegistrationId(1L, "testuser"));
            declinedReg.setDegree(User.Degree.MSc);
            declinedReg.setApprovalStatus(ApprovalStatus.DECLINED);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("New Topic");
            request.setSupervisorEmail("supervisor@example.com");
            request.setSupervisorName("Dr. Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
            // existsActiveRegistration returns false for DECLINED - allows re-registration
            when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(false);
            when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(declinedReg));
            when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.singletonList(declinedReg));
            when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(inv -> inv.getArgument(0));
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("testuser", 1L, request);

            // Then
            assertTrue(response.isSuccess());
            assertEquals("PENDING_APPROVAL", response.getCode());
        }

        @Test
        @DisplayName("User can re-register after EXPIRED registration")
        void reRegisterAfterExpired_Success() {
            // Given - User has an EXPIRED registration for this slot
            SeminarSlotRegistration expiredReg = new SeminarSlotRegistration();
            expiredReg.setId(new SeminarSlotRegistrationId(1L, "testuser"));
            expiredReg.setDegree(User.Degree.MSc);
            expiredReg.setApprovalStatus(ApprovalStatus.EXPIRED);

            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("New Topic");
            request.setSupervisorEmail("supervisor@example.com");
            request.setSupervisorName("Dr. Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
            // existsActiveRegistration returns false for EXPIRED - allows re-registration
            when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(false);
            when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(expiredReg));
            when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.singletonList(expiredReg));
            when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(inv -> inv.getArgument(0));
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("testuser", 1L, request);

            // Then
            assertTrue(response.isSuccess());
            assertEquals("PENDING_APPROVAL", response.getCode());
        }
    }

    // ==================== Unregistration Edge Cases Tests ====================

    @Nested
    @DisplayName("Unregistration Edge Cases")
    class UnregistrationEdgeCaseTests {

        @Test
        @DisplayName("Unregister returns success for existing registration")
        void unregister_Success() {
            // Given
            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
            when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));
            // Mock waiting list service to return empty list (no promotion needed)
            when(waitingListService.getWaitingList(1L)).thenReturn(Collections.emptyList());

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.unregisterFromSlot("testuser", 1L);

            // Then
            assertTrue(response.isSuccess());
            assertEquals("UNREGISTERED", response.getCode());
            verify(registrationRepository).delete(testRegistration);
        }

        @Test
        @DisplayName("Unregister sends cancellation email for approved registration")
        void unregister_SendsCancellationEmail() {
            // Given - Registration is APPROVED
            testRegistration.setApprovalStatus(ApprovalStatus.APPROVED);
            testRegistration.setSupervisorEmail("supervisor@example.com");

            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
            when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));
            // Mock waiting list service to return empty list (no promotion needed)
            when(waitingListService.getWaitingList(1L)).thenReturn(Collections.emptyList());

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.unregisterFromSlot("testuser", 1L);

            // Then
            assertTrue(response.isSuccess());
            // Registration deleted for APPROVED registrations (cancellation email sent internally)
            verify(registrationRepository).delete(testRegistration);
        }

        @Test
        @DisplayName("Unregister updates slot status when last registration removed")
        void unregister_UpdatesSlotStatus() {
            // Given
            testSlot.setStatus(SeminarSlot.SlotStatus.SEMI);

            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
            when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList()); // No more registrations
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(inv -> inv.getArgument(0));
            // Mock waiting list service to return empty list (no promotion needed)
            when(waitingListService.getWaitingList(1L)).thenReturn(Collections.emptyList());

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.unregisterFromSlot("testuser", 1L);

            // Then
            assertTrue(response.isSuccess());
            // Slot should be saved (at least once - may be saved multiple times)
            verify(seminarSlotRepository, atLeastOnce()).save(any(SeminarSlot.class));
        }
    }

    // ==================== User Not Found Tests ====================

    @Nested
    @DisplayName("User Not Found Tests")
    class UserNotFoundTests {

        @Test
        @DisplayName("registerForSlot throws exception when user not found")
        void registerForSlot_UserNotFound() {
            // Given
            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            when(userRepository.findByBguUsernameIgnoreCase("unknownuser")).thenReturn(Optional.empty());

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                presenterHomeService.registerForSlot("unknownuser", 1L, request);
            });
        }

        @Test
        @DisplayName("unregisterFromSlot throws exception when user not found")
        void unregisterFromSlot_UserNotFound() {
            // Given
            when(userRepository.findByBguUsernameIgnoreCase("unknownuser")).thenReturn(Optional.empty());

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                presenterHomeService.unregisterFromSlot("unknownuser", 1L);
            });
        }

        @Test
        @DisplayName("openAttendance throws exception when user not found")
        void openAttendance_UserNotFound() {
            // Given
            when(userRepository.findByBguUsernameIgnoreCase("unknownuser")).thenReturn(Optional.empty());

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                presenterHomeService.openAttendance("unknownuser", 1L);
            });
        }
    }

    // ==================== Supervisor Email Validation Tests ====================

    @Nested
    @DisplayName("Supervisor Email Validation Tests")
    class SupervisorEmailValidationTests {

        @Test
        @DisplayName("Registration fails with invalid supervisor email")
        void registerForSlot_InvalidSupervisorEmail() {
            // Given
            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("Topic");
            request.setSupervisorEmail("invalid-email");
            request.setSupervisorName("Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
            when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(false);
            when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.emptyList());
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
            when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(emailQueueService.validateSupervisorEmail("invalid-email", "testuser"))
                    .thenReturn(new EmailQueueService.EmailValidationResult(false, "Invalid email format", "INVALID_EMAIL"));

            // When
            PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("testuser", 1L, request);

            // Then
            assertFalse(response.isSuccess());
            assertEquals("INVALID_EMAIL", response.getCode());
        }
    }

    // ==================== Concurrency Tests ====================

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent registration for same slot - only one succeeds")
        void concurrentRegistration_OnlyOneSucceeds() throws Exception {
            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("Concurrent Topic");
            request.setSupervisorEmail("supervisor@example.com");
            request.setSupervisorName("Dr. Supervisor");

            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
            when(registrationRepository.existsActiveRegistration(1L, "testuser")).thenReturn(false);
            when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
            when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.emptyList());
            when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(approvalService.sendApprovalEmail(any(SeminarSlotRegistration.class))).thenReturn(true);

            AtomicInteger saveCount = new AtomicInteger(0);
            when(registrationRepository.saveAndFlush(any(SeminarSlotRegistration.class))).thenAnswer(invocation -> {
                if (saveCount.incrementAndGet() > 1) {
                    throw new RuntimeException("Duplicate registration");
                }
                return invocation.getArgument(0);
            });

            CountDownLatch startLatch = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            Callable<PresenterSlotRegistrationResponse> task = () -> {
                startLatch.await(5, TimeUnit.SECONDS);
                return presenterHomeService.registerForSlot("testuser", 1L, request);
            };

            Future<PresenterSlotRegistrationResponse> result1 = executor.submit(task);
            Future<PresenterSlotRegistrationResponse> result2 = executor.submit(task);

            startLatch.countDown();

            int successCount = 0;
            int failureCount = 0;
            for (Future<PresenterSlotRegistrationResponse> result : Arrays.asList(result1, result2)) {
                try {
                    PresenterSlotRegistrationResponse response = result.get(5, TimeUnit.SECONDS);
                    if (response != null && response.isSuccess()) {
                        successCount++;
                    }
                } catch (Exception ex) {
                    failureCount++;
                }
            }

            executor.shutdownNow();

            assertEquals(1, successCount);
            assertEquals(1, failureCount);
        }
    }
}

