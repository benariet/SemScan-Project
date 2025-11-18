package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.Seminar;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.repository.SeminarRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for SessionService
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SeminarRepository seminarRepository;

    @Mock
    private DatabaseLoggerService databaseLoggerService;

    @InjectMocks
    private SessionService sessionService;

    private Session testSession;
    private Seminar testSeminar;

    @BeforeEach
    void setUp() {
        // Setup test seminar
        testSeminar = new Seminar();
        testSeminar.setSeminarId(1L);
        testSeminar.setPresenterUsername("presenter1");
        testSeminar.setSeminarName("Test Seminar");

        // Setup test session
        testSession = new Session();
        testSession.setSessionId(1L);
        testSession.setSeminarId(1L);
        testSession.setStartTime(LocalDateTime.now());
        testSession.setStatus(Session.SessionStatus.OPEN);
    }

    // ==================== createSession Tests ====================

    @Test
    void createSession_WithValidData_CreatesSuccessfully() {
        // Given
        when(seminarRepository.findById(1L)).thenReturn(Optional.of(testSeminar));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            s.setSessionId(1L);
            return s;
        });

        // When
        Session result = sessionService.createSession(testSession);

        // Then
        assertNotNull(result);
        assertNotNull(result.getSessionId());
        assertEquals(1L, result.getSeminarId());
        verify(sessionRepository).save(any(Session.class));
        verify(databaseLoggerService).logSessionEvent(eq("SESSION_CREATED"), eq(1L), eq(1L), eq("presenter1"));
    }

    @Test
    void createSession_WithSeminarNotFound_ThrowsException() {
        // Given
        when(seminarRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            sessionService.createSession(testSession);
        });
        verify(databaseLoggerService).logError(eq("SESSION_SEMINAR_NOT_FOUND"), anyString(), isNull(), 
            isNull(), anyString());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createSession_WithNullStatus_SetsDefaultStatus() {
        // Given
        testSession.setStatus(null);
        when(seminarRepository.findById(1L)).thenReturn(Optional.of(testSeminar));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            s.setSessionId(1L);
            return s;
        });

        // When
        Session result = sessionService.createSession(testSession);

        // Then
        assertEquals(Session.SessionStatus.OPEN, result.getStatus());
    }

    @Test
    void createSession_WithNullStartTime_SetsCurrentTime() {
        // Given
        testSession.setStartTime(null);
        when(seminarRepository.findById(1L)).thenReturn(Optional.of(testSeminar));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            s.setSessionId(1L);
            return s;
        });

        // When
        Session result = sessionService.createSession(testSession);

        // Then
        assertNotNull(result.getStartTime());
    }

    // ==================== getSessionById Tests ====================

    @Test
    void getSessionById_WithValidId_ReturnsSession() {
        // Given
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

        // When
        Optional<Session> result = sessionService.getSessionById(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getSessionId());
    }

    @Test
    void getSessionById_WithNotFound_ReturnsEmpty() {
        // Given
        when(sessionRepository.findById(1L)).thenReturn(Optional.empty());

        // When
        Optional<Session> result = sessionService.getSessionById(1L);

        // Then
        assertTrue(result.isEmpty());
    }

    // ==================== getSessionsBySeminar Tests ====================

    @Test
    void getSessionsBySeminar_WithValidSeminarId_ReturnsSessions() {
        // Given
        Session session1 = new Session();
        session1.setSessionId(1L);
        session1.setSeminarId(1L);

        Session session2 = new Session();
        session2.setSessionId(2L);
        session2.setSeminarId(1L);

        when(sessionRepository.findBySeminarId(1L))
                .thenReturn(List.of(session1, session2));

        // When
        List<Session> result = sessionService.getSessionsBySeminar(1L);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(s -> s.getSeminarId().equals(1L)));
    }

    @Test
    void getSessionsBySeminar_WithNoSessions_ReturnsEmptyList() {
        // Given
        when(sessionRepository.findBySeminarId(1L)).thenReturn(Collections.emptyList());

        // When
        List<Session> result = sessionService.getSessionsBySeminar(1L);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getOpenSessions Tests ====================

    @Test
    void getOpenSessions_WithOpenSessions_ReturnsOnlyOpenSessions() {
        // Given
        Session openSession1 = new Session();
        openSession1.setSessionId(1L);
        openSession1.setStatus(Session.SessionStatus.OPEN);

        Session openSession2 = new Session();
        openSession2.setSessionId(2L);
        openSession2.setStatus(Session.SessionStatus.OPEN);

        Session closedSession = new Session();
        closedSession.setSessionId(3L);
        closedSession.setStatus(Session.SessionStatus.CLOSED);

        when(sessionRepository.findOpenSessions())
                .thenReturn(List.of(openSession1, openSession2, closedSession));

        // When
        List<Session> result = sessionService.getOpenSessions();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size()); // Only open sessions
        assertTrue(result.stream().allMatch(s -> s.getStatus() == Session.SessionStatus.OPEN));
    }

    @Test
    void getOpenSessions_WithNoOpenSessions_ReturnsEmptyList() {
        // Given
        when(sessionRepository.findOpenSessions()).thenReturn(Collections.emptyList());

        // When
        List<Session> result = sessionService.getOpenSessions();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getClosedSessions Tests ====================

    @Test
    void getClosedSessions_WithClosedSessions_ReturnsClosedSessions() {
        // Given
        Session closedSession1 = new Session();
        closedSession1.setSessionId(1L);
        closedSession1.setStatus(Session.SessionStatus.CLOSED);

        when(sessionRepository.findClosedSessions())
                .thenReturn(List.of(closedSession1));

        // When
        List<Session> result = sessionService.getClosedSessions();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Session.SessionStatus.CLOSED, result.get(0).getStatus());
    }

    // ==================== updateSessionStatus Tests ====================

    @Test
    void updateSessionStatus_WithValidSession_UpdatesStatus() {
        // Given
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Session result = sessionService.updateSessionStatus(1L, Session.SessionStatus.CLOSED);

        // Then
        assertNotNull(result);
        assertEquals(Session.SessionStatus.CLOSED, result.getStatus());
        verify(sessionRepository).save(any(Session.class));
        verify(databaseLoggerService).logSessionEvent(eq("SESSION_STATUS_UPDATED"), eq(1L), eq(1L), isNull());
    }

    @Test
    void updateSessionStatus_WithSessionNotFound_ThrowsException() {
        // Given
        when(sessionRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            sessionService.updateSessionStatus(1L, Session.SessionStatus.CLOSED);
        });
        verify(databaseLoggerService).logError(eq("SESSION_NOT_FOUND"), anyString(), isNull(), 
            isNull(), anyString());
        verify(sessionRepository, never()).save(any());
    }

    // ==================== deleteSession Tests ====================

    @Test
    void deleteSession_WithValidId_DeletesSuccessfully() {
        // Given
        when(sessionRepository.existsById(1L)).thenReturn(true);
        doNothing().when(sessionRepository).deleteById(1L);

        // When
        sessionService.deleteSession(1L);

        // Then
        verify(sessionRepository).deleteById(1L);
        verify(databaseLoggerService).logSessionEvent(eq("SESSION_DELETED"), eq(1L), isNull(), isNull());
    }

    @Test
    void deleteSession_WithNotFound_ThrowsException() {
        // Given
        when(sessionRepository.existsById(1L)).thenReturn(false);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            sessionService.deleteSession(1L);
        });
        verify(sessionRepository, never()).deleteById(any());
        verify(databaseLoggerService).logError(eq("SESSION_DELETE_NOT_FOUND"), anyString(), isNull(), 
            isNull(), anyString());
    }

    // ==================== getSessionsBetweenDates Tests ====================

    @Test
    void getSessionsBetweenDates_WithValidDates_ReturnsSessions() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        Session session1 = new Session();
        session1.setSessionId(1L);
        session1.setStartTime(LocalDateTime.now());

        when(sessionRepository.findSessionsBetweenDates(start, end))
                .thenReturn(List.of(session1));

        // When
        List<Session> result = sessionService.getSessionsBetweenDates(start, end);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }
}

