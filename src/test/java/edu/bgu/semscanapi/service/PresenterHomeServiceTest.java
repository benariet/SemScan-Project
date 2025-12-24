package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.dto.PresenterHomeResponse;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

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
        lenient().when(appConfigService.getIntegerConfig(anyString(), anyInt())).thenReturn(15);
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

        // Setup global config mocks (lenient since not all tests use them)
        lenient().when(globalConfig.getServerUrl()).thenReturn("http://localhost:8080");
        lenient().when(globalConfig.getApiBaseUrl()).thenReturn("http://localhost:8080/api/v1");

        // Setup seminar repository mocks for legacy seminar handling
        lenient().when(seminarRepository.findByPresenterUsername(anyString())).thenReturn(Collections.singletonList(testSeminar));
        lenient().when(seminarRepository.save(any(Seminar.class))).thenAnswer(invocation -> {
            Seminar s = invocation.getArgument(0);
            if (s.getSeminarId() == null) s.setSeminarId(1L);
            return s;
        });

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
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(false);
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
        when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.emptyList());
        when(registrationRepository.save(any(SeminarSlotRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(seminarSlotRepository.save(any(SeminarSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalService.sendApprovalEmail(any(SeminarSlotRegistration.class))).thenReturn(true);
        lenient().when(appConfigService.getIntegerConfig(anyString(), anyInt())).thenReturn(15);

        // When
        PresenterSlotRegistrationResponse response = presenterHomeService.registerForSlot("testuser", 1L, request);

        // Then - with supervisor email, registration goes through approval flow
        assertTrue(response.isSuccess());
        assertEquals("PENDING_APPROVAL", response.getCode());
        verify(registrationRepository).save(any(SeminarSlotRegistration.class));
        verify(seminarSlotRepository, atLeastOnce()).save(any(SeminarSlot.class));
    }

    @Test
    void registerForSlot_WhenAlreadyRegistered_ReturnsAlreadyRegistered() {
        // Given
        PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();

        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);

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
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(false);
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
        when(registrationRepository.findByIdPresenterUsername("testuser")).thenReturn(Collections.emptyList());
        when(registrationRepository.save(any(SeminarSlotRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        // Set slot and session to match by time and location
        LocalDateTime now = LocalDateTime.now();
        testSlot.setSlotDate(now.toLocalDate());
        testSlot.setStartTime(now.toLocalTime().minusMinutes(15)); // Start was 15 minutes ago
        testSlot.setEndTime(now.toLocalTime().plusHours(1));
        testSlot.setLegacySessionId(1L);
        
        testSession.setStartTime(LocalDateTime.of(testSlot.getSlotDate(), testSlot.getStartTime()));
        testSession.setLocation("Building 37 Room 201");
        
        when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testPresenter));
        when(seminarSlotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(true);
        when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(testSession));
        when(seminarRepository.findById(1L)).thenReturn(Optional.of(testSeminar));
        when(seminarSlotRepository.findByLegacySessionId(1L)).thenReturn(Optional.of(testSlot));

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
        // Set slot to today with start time 15 minutes ago (so we're within the 10-minute window)
        // The window opens 10 minutes before start, so if start was 15 min ago, we're 5 min past start = valid
        LocalDateTime now = LocalDateTime.now();
        testSlot.setSlotDate(now.toLocalDate());
        testSlot.setStartTime(now.toLocalTime().minusMinutes(15)); // Start was 15 minutes ago
        testSlot.setEndTime(now.toLocalTime().plusHours(1)); // End is 1 hour from now
        
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
        when(registrationRepository.save(any(SeminarSlotRegistration.class)))
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
        when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "testuser")).thenReturn(false);
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
}

