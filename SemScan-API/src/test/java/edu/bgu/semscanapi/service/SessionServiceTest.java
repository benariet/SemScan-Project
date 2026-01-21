package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.dto.SessionDTO;
import edu.bgu.semscanapi.entity.Seminar;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.SeminarRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for SessionService
 * Tests all public methods with high coverage
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SeminarRepository seminarRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DatabaseLoggerService databaseLoggerService;

    @Mock
    private AppConfigService appConfigService;

    @InjectMocks
    private SessionService sessionService;

    private Session testSession;
    private Seminar testSeminar;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Setup test seminar
        testSeminar = new Seminar();
        testSeminar.setSeminarId(1L);
        testSeminar.setPresenterUsername("presenter1");
        testSeminar.setSeminarName("Test Seminar");
        testSeminar.setDescription("Test Description");

        // Setup test session
        testSession = new Session();
        testSession.setSessionId(1L);
        testSession.setSeminarId(1L);
        testSession.setStartTime(LocalDateTime.now());
        testSession.setStatus(Session.SessionStatus.OPEN);
        testSession.setLocation("Building 37 Room 201");
        testSession.setCreatedAt(LocalDateTime.now());

        // Setup test user
        testUser = new User();
        testUser.setBguUsername("presenter1");
        testUser.setFirstName("Test");
        testUser.setLastName("Presenter");
        testUser.setEmail("presenter1@example.com");

        // Default lenient mocks
        lenient().when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt()))
                .thenReturn(15);
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

    @Test
    void getSessionsBetweenDates_WithNoSessions_ReturnsEmptyList() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        when(sessionRepository.findSessionsBetweenDates(start, end))
                .thenReturn(Collections.emptyList());

        // When
        List<Session> result = sessionService.getSessionsBetweenDates(start, end);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getSessionsBetweenDates_WithDatabaseError_LogsError() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        when(sessionRepository.findSessionsBetweenDates(start, end))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            sessionService.getSessionsBetweenDates(start, end);
        });

        verify(databaseLoggerService).logError(eq("SESSION_RETRIEVAL_ERROR"), anyString(), any(), isNull(), anyString());
    }

    // ==================== getActiveSessionsBySeminar Tests ====================

    @Nested
    @DisplayName("getActiveSessionsBySeminar Tests")
    class GetActiveSessionsBySeminarTests {

        @Test
        @DisplayName("Get active sessions by seminar - sessions exist")
        void getActiveSessionsBySeminar_SessionsExist_ReturnsList() {
            // Given
            Session activeSession = new Session();
            activeSession.setSessionId(1L);
            activeSession.setSeminarId(1L);
            activeSession.setStatus(Session.SessionStatus.OPEN);

            when(sessionRepository.findActiveSessionsBySeminar(1L))
                    .thenReturn(Collections.singletonList(activeSession));

            // When
            List<Session> result = sessionService.getActiveSessionsBySeminar(1L);

            // Then
            assertEquals(1, result.size());
            assertEquals(Session.SessionStatus.OPEN, result.get(0).getStatus());
        }

        @Test
        @DisplayName("Get active sessions by seminar - no active sessions")
        void getActiveSessionsBySeminar_NoActiveSessions_ReturnsEmptyList() {
            // Given
            when(sessionRepository.findActiveSessionsBySeminar(1L))
                    .thenReturn(Collections.emptyList());

            // When
            List<Session> result = sessionService.getActiveSessionsBySeminar(1L);

            // Then
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Get active sessions by seminar logs error on exception")
        void getActiveSessionsBySeminar_LogsErrorOnException() {
            // Given
            when(sessionRepository.findActiveSessionsBySeminar(1L))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThrows(RuntimeException.class, () -> {
                sessionService.getActiveSessionsBySeminar(1L);
            });

            verify(databaseLoggerService).logError(eq("SESSION_RETRIEVAL_ERROR"), anyString(), any(), isNull(), anyString());
        }
    }

    // ==================== closeSession Tests ====================

    @Nested
    @DisplayName("closeSession Tests")
    class CloseSessionTests {

        @Test
        @DisplayName("Close session - SUCCESS")
        void closeSession_Success() {
            // Given
            testSession.setStatus(Session.SessionStatus.OPEN);
            testSession.setEndTime(null);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Session result = sessionService.closeSession(1L);

            // Then
            assertEquals(Session.SessionStatus.CLOSED, result.getStatus());
            assertNotNull(result.getEndTime());
        }

        @Test
        @DisplayName("Close session - session not found")
        void closeSession_SessionNotFound_ThrowsException() {
            // Given
            when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                sessionService.closeSession(999L);
            });
        }

        @Test
        @DisplayName("Close session sets end time to Israel timezone")
        void closeSession_SetsEndTimeToIsraelTimezone() {
            // Given
            testSession.setStatus(Session.SessionStatus.OPEN);
            testSession.setEndTime(null);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Session result = sessionService.closeSession(1L);

            // Then
            assertNotNull(result.getEndTime());
            // End time should be recent (within last minute)
            assertTrue(result.getEndTime().isAfter(LocalDateTime.now().minusMinutes(1)));
        }
    }

    // ==================== openSession Tests ====================

    @Nested
    @DisplayName("openSession Tests")
    class OpenSessionTests {

        @Test
        @DisplayName("Open session - SUCCESS")
        void openSession_Success() {
            // Given
            testSession.setStatus(Session.SessionStatus.CLOSED);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Session result = sessionService.openSession(1L);

            // Then
            assertEquals(Session.SessionStatus.OPEN, result.getStatus());
        }

        @Test
        @DisplayName("Open session - session not found")
        void openSession_SessionNotFound_ThrowsException() {
            // Given
            when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                sessionService.openSession(999L);
            });
        }

        @Test
        @DisplayName("Open already open session - succeeds")
        void openSession_AlreadyOpen_Succeeds() {
            // Given
            testSession.setStatus(Session.SessionStatus.OPEN);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Session result = sessionService.openSession(1L);

            // Then
            assertEquals(Session.SessionStatus.OPEN, result.getStatus());
        }
    }

    // ==================== Session Auto-Close (Expired Sessions) Tests ====================

    @Nested
    @DisplayName("Session Auto-Close Tests")
    class SessionAutoCloseTests {

        @Test
        @DisplayName("Get open sessions auto-closes expired sessions")
        void getOpenSessions_AutoClosesExpiredSessions() {
            // Given
            Session expiredSession = new Session();
            expiredSession.setSessionId(1L);
            expiredSession.setStatus(Session.SessionStatus.OPEN);
            expiredSession.setStartTime(LocalDateTime.now().minusMinutes(30)); // Started 30 minutes ago

            when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(expiredSession));
            when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt()))
                    .thenReturn(15); // 15 minute session duration
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            List<Session> result = sessionService.getOpenSessions();

            // Then
            assertTrue(result.isEmpty()); // Expired session should not be returned
            verify(sessionRepository).save(argThat(s -> s.getStatus() == Session.SessionStatus.CLOSED));
            verify(databaseLoggerService).logAction(eq("INFO"), eq("SESSION_AUTO_CLOSED_EXPIRED"), anyString(), isNull(), anyString());
        }

        @Test
        @DisplayName("Get open sessions keeps valid sessions")
        void getOpenSessions_KeepsValidSessions() {
            // Given
            Session validSession = new Session();
            validSession.setSessionId(1L);
            validSession.setStatus(Session.SessionStatus.OPEN);
            validSession.setStartTime(LocalDateTime.now().minusMinutes(5)); // Started 5 minutes ago

            when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(validSession));
            when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt()))
                    .thenReturn(15); // 15 minute session duration

            // When
            List<Session> result = sessionService.getOpenSessions();

            // Then
            assertEquals(1, result.size());
            // Valid session should not be saved/updated
            verify(sessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Get open sessions handles null start time")
        void getOpenSessions_HandlesNullStartTime() {
            // Given
            Session sessionWithNoStartTime = new Session();
            sessionWithNoStartTime.setSessionId(1L);
            sessionWithNoStartTime.setStatus(Session.SessionStatus.OPEN);
            sessionWithNoStartTime.setStartTime(null);

            when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(sessionWithNoStartTime));
            when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt()))
                    .thenReturn(15);

            // When
            List<Session> result = sessionService.getOpenSessions();

            // Then
            assertEquals(1, result.size()); // Session with no start time is included
        }

        @Test
        @DisplayName("Multiple sessions - only expired ones are auto-closed")
        void getOpenSessions_MultipleSessionsOnlyExpiredAreClosed() {
            // Given
            Session validSession = new Session();
            validSession.setSessionId(1L);
            validSession.setStatus(Session.SessionStatus.OPEN);
            validSession.setStartTime(LocalDateTime.now().minusMinutes(5));

            Session expiredSession = new Session();
            expiredSession.setSessionId(2L);
            expiredSession.setStatus(Session.SessionStatus.OPEN);
            expiredSession.setStartTime(LocalDateTime.now().minusMinutes(30));

            when(sessionRepository.findOpenSessions()).thenReturn(Arrays.asList(validSession, expiredSession));
            when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt()))
                    .thenReturn(15);
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            List<Session> result = sessionService.getOpenSessions();

            // Then
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getSessionId());

            // Verify only expired session was saved (auto-closed)
            ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
            verify(sessionRepository).save(sessionCaptor.capture());
            assertEquals(2L, sessionCaptor.getValue().getSessionId());
            assertEquals(Session.SessionStatus.CLOSED, sessionCaptor.getValue().getStatus());
        }
    }

    // ==================== getOpenSessionsEnriched Tests ====================

    @Nested
    @DisplayName("getOpenSessionsEnriched Tests")
    class GetOpenSessionsEnrichedTests {

        @Test
        @DisplayName("Get enriched sessions - includes presenter name and topic")
        void getOpenSessionsEnriched_IncludesPresenterNameAndTopic() {
            // Given
            Session openSession = new Session();
            openSession.setSessionId(1L);
            openSession.setSeminarId(1L);
            openSession.setStatus(Session.SessionStatus.OPEN);
            openSession.setStartTime(LocalDateTime.now().minusMinutes(5));

            when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(openSession));
            when(seminarRepository.findById(1L)).thenReturn(Optional.of(testSeminar));
            when(userRepository.findByBguUsername("presenter1")).thenReturn(Optional.of(testUser));
            when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt()))
                    .thenReturn(15);

            // When
            List<SessionDTO> result = sessionService.getOpenSessionsEnriched();

            // Then
            assertEquals(1, result.size());
            assertEquals("Test Presenter", result.get(0).getPresenterName());
            assertEquals("presenter1", result.get(0).getPresenterUsername());
            assertEquals("Test Description", result.get(0).getTopic());
        }

        @Test
        @DisplayName("Get enriched sessions - uses seminar name as topic fallback")
        void getOpenSessionsEnriched_UsesSeminarNameAsFallback() {
            // Given
            Session openSession = new Session();
            openSession.setSessionId(1L);
            openSession.setSeminarId(1L);
            openSession.setStatus(Session.SessionStatus.OPEN);
            openSession.setStartTime(LocalDateTime.now().minusMinutes(5));

            Seminar seminarWithoutDescription = new Seminar();
            seminarWithoutDescription.setSeminarId(1L);
            seminarWithoutDescription.setPresenterUsername("presenter1");
            seminarWithoutDescription.setSeminarName("Seminar Name");
            seminarWithoutDescription.setDescription(null);

            when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(openSession));
            when(seminarRepository.findById(1L)).thenReturn(Optional.of(seminarWithoutDescription));
            when(userRepository.findByBguUsername("presenter1")).thenReturn(Optional.of(testUser));
            when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt()))
                    .thenReturn(15);

            // When
            List<SessionDTO> result = sessionService.getOpenSessionsEnriched();

            // Then
            assertEquals(1, result.size());
            assertEquals("Seminar Name", result.get(0).getTopic());
        }

        @Test
        @DisplayName("Get enriched sessions - handles unknown presenter")
        void getOpenSessionsEnriched_HandlesUnknownPresenter() {
            // Given
            Session openSession = new Session();
            openSession.setSessionId(1L);
            openSession.setSeminarId(1L);
            openSession.setStatus(Session.SessionStatus.OPEN);
            openSession.setStartTime(LocalDateTime.now().minusMinutes(5));

            when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(openSession));
            when(seminarRepository.findById(1L)).thenReturn(Optional.of(testSeminar));
            when(userRepository.findByBguUsername("presenter1")).thenReturn(Optional.empty());
            when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt()))
                    .thenReturn(15);

            // When
            List<SessionDTO> result = sessionService.getOpenSessionsEnriched();

            // Then
            assertEquals(1, result.size());
            assertEquals("presenter1", result.get(0).getPresenterName()); // Falls back to username
        }

        @Test
        @DisplayName("Get enriched sessions - handles missing seminar")
        void getOpenSessionsEnriched_HandlesMissingSeminar() {
            // Given
            Session openSession = new Session();
            openSession.setSessionId(1L);
            openSession.setSeminarId(1L);
            openSession.setStatus(Session.SessionStatus.OPEN);
            openSession.setStartTime(LocalDateTime.now().minusMinutes(5));

            when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(openSession));
            when(seminarRepository.findById(1L)).thenReturn(Optional.empty());
            when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt()))
                    .thenReturn(15);

            // When
            List<SessionDTO> result = sessionService.getOpenSessionsEnriched();

            // Then
            assertEquals(1, result.size());
            assertEquals("Unknown Presenter", result.get(0).getPresenterName());
        }

        @Test
        @DisplayName("Get enriched sessions - includes start time epoch")
        void getOpenSessionsEnriched_IncludesStartTimeEpoch() {
            // Given
            LocalDateTime startTime = LocalDateTime.now().minusMinutes(5);
            Session openSession = new Session();
            openSession.setSessionId(1L);
            openSession.setSeminarId(1L);
            openSession.setStatus(Session.SessionStatus.OPEN);
            openSession.setStartTime(startTime);

            when(sessionRepository.findOpenSessions()).thenReturn(Collections.singletonList(openSession));
            when(seminarRepository.findById(1L)).thenReturn(Optional.of(testSeminar));
            when(userRepository.findByBguUsername("presenter1")).thenReturn(Optional.of(testUser));
            when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt()))
                    .thenReturn(15);

            // When
            List<SessionDTO> result = sessionService.getOpenSessionsEnriched();

            // Then
            assertEquals(1, result.size());
            assertNotNull(result.get(0).getStartTimeEpoch());
        }
    }

    // ==================== Session Status Transition Tests ====================

    @Nested
    @DisplayName("Session Status Transition Tests")
    class SessionStatusTransitionTests {

        @Test
        @DisplayName("Status transition: OPEN -> CLOSED sets end time")
        void statusTransition_OpenToClosed_SetsEndTime() {
            // Given
            testSession.setStatus(Session.SessionStatus.OPEN);
            testSession.setEndTime(null);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Session result = sessionService.updateSessionStatus(1L, Session.SessionStatus.CLOSED);

            // Then
            assertNotNull(result.getEndTime());
        }

        @Test
        @DisplayName("Status transition: CLOSED -> OPEN does not clear end time")
        void statusTransition_ClosedToOpen_DoesNotClearEndTime() {
            // Given
            LocalDateTime endTime = LocalDateTime.now().minusMinutes(5);
            testSession.setStatus(Session.SessionStatus.CLOSED);
            testSession.setEndTime(endTime);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Session result = sessionService.updateSessionStatus(1L, Session.SessionStatus.OPEN);

            // Then
            assertEquals(Session.SessionStatus.OPEN, result.getStatus());
            // End time is preserved (service doesn't clear it when reopening)
            assertEquals(endTime, result.getEndTime());
        }

        @Test
        @DisplayName("Status transition preserves existing end time when already set")
        void statusTransition_PreservesExistingEndTime() {
            // Given
            LocalDateTime existingEndTime = LocalDateTime.now().minusHours(1);
            testSession.setStatus(Session.SessionStatus.OPEN);
            testSession.setEndTime(existingEndTime);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            Session result = sessionService.updateSessionStatus(1L, Session.SessionStatus.CLOSED);

            // Then
            // End time should remain the existing value since it was already set
            assertEquals(existingEndTime, result.getEndTime());
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Create session logs error on database exception")
        void createSession_LogsErrorOnDatabaseException() {
            // Given
            when(seminarRepository.findById(1L)).thenReturn(Optional.of(testSeminar));
            when(sessionRepository.save(any(Session.class))).thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThrows(RuntimeException.class, () -> {
                sessionService.createSession(testSession);
            });

            verify(databaseLoggerService).logError(eq("SESSION_CREATION_ERROR"), anyString(), any(), isNull(), anyString());
        }

        @Test
        @DisplayName("Update session status logs error on database exception")
        void updateSessionStatus_LogsErrorOnDatabaseException() {
            // Given
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(sessionRepository.save(any(Session.class))).thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThrows(RuntimeException.class, () -> {
                sessionService.updateSessionStatus(1L, Session.SessionStatus.CLOSED);
            });

            verify(databaseLoggerService).logError(eq("SESSION_UPDATE_ERROR"), anyString(), any(), isNull(), anyString());
        }

        @Test
        @DisplayName("Delete session logs error on database exception")
        void deleteSession_LogsErrorOnDatabaseException() {
            // Given
            when(sessionRepository.existsById(1L)).thenReturn(true);
            doThrow(new RuntimeException("Database error")).when(sessionRepository).deleteById(1L);

            // When & Then
            assertThrows(RuntimeException.class, () -> {
                sessionService.deleteSession(1L);
            });

            verify(databaseLoggerService).logError(eq("SESSION_DELETE_ERROR"), anyString(), any(), isNull(), anyString());
        }

        @Test
        @DisplayName("Get sessions by seminar logs error on exception")
        void getSessionsBySeminar_LogsErrorOnException() {
            // Given
            when(sessionRepository.findBySeminarId(1L)).thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThrows(RuntimeException.class, () -> {
                sessionService.getSessionsBySeminar(1L);
            });

            verify(databaseLoggerService).logError(eq("SESSION_RETRIEVAL_ERROR"), anyString(), any(), isNull(), anyString());
        }

        @Test
        @DisplayName("Get open sessions logs error on exception")
        void getOpenSessions_LogsErrorOnException() {
            // Given
            when(sessionRepository.findOpenSessions()).thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThrows(RuntimeException.class, () -> {
                sessionService.getOpenSessions();
            });

            verify(databaseLoggerService).logError(eq("SESSION_RETRIEVAL_ERROR"), anyString(), any(), isNull(), anyString());
        }

        @Test
        @DisplayName("Get closed sessions logs error on exception")
        void getClosedSessions_LogsErrorOnException() {
            // Given
            when(sessionRepository.findClosedSessions()).thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThrows(RuntimeException.class, () -> {
                sessionService.getClosedSessions();
            });

            verify(databaseLoggerService).logError(eq("SESSION_RETRIEVAL_ERROR"), anyString(), any(), isNull(), anyString());
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Get session by ID with null ID")
        void getSessionById_WithNullId_ReturnsEmpty() {
            // Given
            when(sessionRepository.findById(null)).thenReturn(Optional.empty());

            // When
            Optional<Session> result = sessionService.getSessionById(null);

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Create session preserves provided status")
        void createSession_PreservesProvidedStatus() {
            // Given
            Session newSession = new Session();
            newSession.setSeminarId(1L);
            newSession.setStatus(Session.SessionStatus.CLOSED);

            when(seminarRepository.findById(1L)).thenReturn(Optional.of(testSeminar));
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
                Session s = invocation.getArgument(0);
                s.setSessionId(1L);
                return s;
            });

            // When
            Session result = sessionService.createSession(newSession);

            // Then
            assertEquals(Session.SessionStatus.CLOSED, result.getStatus());
        }

        @Test
        @DisplayName("Get open sessions filters out CLOSED sessions from query results")
        void getOpenSessions_FiltersOutClosedSessionsFromQueryResults() {
            // Given
            Session openSession = new Session();
            openSession.setSessionId(1L);
            openSession.setStatus(Session.SessionStatus.OPEN);
            openSession.setStartTime(LocalDateTime.now().minusMinutes(5));

            Session closedSession = new Session();
            closedSession.setSessionId(2L);
            closedSession.setStatus(Session.SessionStatus.CLOSED);

            // Query might return both due to timing issues
            when(sessionRepository.findOpenSessions()).thenReturn(Arrays.asList(openSession, closedSession));
            when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt()))
                    .thenReturn(15);

            // When
            List<Session> result = sessionService.getOpenSessions();

            // Then
            assertEquals(1, result.size());
            assertEquals(Session.SessionStatus.OPEN, result.get(0).getStatus());
        }

        @Test
        @DisplayName("Create session with null seminar ID throws exception")
        void createSession_WithNullSeminarId_ThrowsException() {
            // Given
            Session sessionWithNullSeminarId = new Session();
            sessionWithNullSeminarId.setSeminarId(null);

            when(seminarRepository.findById(null)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                sessionService.createSession(sessionWithNullSeminarId);
            });
        }
    }
}

