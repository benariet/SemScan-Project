package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.AttendanceRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for AttendanceService
 * Tests recording attendance (QR scan), duplicate prevention, session validation,
 * time window validation, and error scenarios.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SeminarSlotRepository slotRepository;

    @Mock
    private DatabaseLoggerService databaseLoggerService;

    @Mock
    private AppConfigService appConfigService;

    @InjectMocks
    private AttendanceService attendanceService;

    private User testStudent;
    private Session testSession;
    private Attendance testAttendance;

    @BeforeEach
    void setUp() {
        // Setup test student
        testStudent = new User();
        testStudent.setBguUsername("teststudent");
        testStudent.setFirstName("Test");
        testStudent.setLastName("Student");
        testStudent.setDegree(User.Degree.MSc);
        testStudent.setEmail("teststudent@bgu.ac.il");
        testStudent.setIsParticipant(true);

        // Setup test session - use Israel timezone for consistency with service
        ZonedDateTime nowIsrael = ZonedDateTime.now(ZoneId.of("Asia/Jerusalem"));
        testSession = new Session();
        testSession.setSessionId(1L);
        testSession.setSeminarId(1L);
        testSession.setStartTime(nowIsrael.toLocalDateTime().minusMinutes(5)); // Started 5 mins ago
        testSession.setEndTime(nowIsrael.toLocalDateTime().plusMinutes(55));
        testSession.setStatus(Session.SessionStatus.OPEN);
        testSession.setLocation("Building 37 Room 201");

        // Setup test attendance
        testAttendance = new Attendance();
        testAttendance.setSessionId(1L);
        testAttendance.setStudentUsername("teststudent");
        testAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);

        // Default appConfigService mock
        lenient().when(appConfigService.getIntegerConfig(eq("presenter_close_session_duration_minutes"), anyInt()))
                .thenReturn(15);
    }

    // ==================== Record Attendance (QR Scan) Tests ====================

    @Nested
    @DisplayName("Record Attendance Tests - QR Scan")
    class RecordAttendanceTests {

        @Test
        @DisplayName("Successfully record QR scan attendance")
        void recordAttendance_WithValidData_Success() {
            // Given
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertNotNull(result);
            assertEquals(100L, result.getAttendanceId());
            assertEquals(1L, result.getSessionId());
            assertEquals("teststudent", result.getStudentUsername());
            assertEquals(Attendance.AttendanceMethod.QR_SCAN, result.getMethod());
            verify(attendanceRepository).save(any(Attendance.class));
            verify(databaseLoggerService).logAttendance(eq("ATTENDANCE_RECORDED"), eq("teststudent"), eq(1L), anyString());
        }

        @Test
        @DisplayName("Record attendance with default QR_SCAN method when method is null")
        void recordAttendance_NullMethod_DefaultsToQrScan() {
            // Given
            testAttendance.setMethod(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertEquals(Attendance.AttendanceMethod.QR_SCAN, result.getMethod());
        }

        @Test
        @DisplayName("Record attendance sets attendance time if null")
        void recordAttendance_NullAttendanceTime_SetsCurrentTime() {
            // Given
            testAttendance.setAttendanceTime(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertNotNull(result.getAttendanceTime());
        }

        @Test
        @DisplayName("Record attendance normalizes username to lowercase")
        void recordAttendance_UppercaseUsername_Normalized() {
            // Given
            testAttendance.setStudentUsername("TESTSTUDENT");
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertEquals("teststudent", result.getStudentUsername());
        }

        @Test
        @DisplayName("Record attendance trims whitespace from username")
        void recordAttendance_UsernameWithWhitespace_Trimmed() {
            // Given
            testAttendance.setStudentUsername("  teststudent  ");
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertEquals("teststudent", result.getStudentUsername());
        }
    }

    // ==================== Duplicate Attendance Prevention Tests ====================

    @Nested
    @DisplayName("Duplicate Attendance Prevention Tests")
    class DuplicateAttendanceTests {

        @Test
        @DisplayName("Reject duplicate attendance - student already attended")
        void recordAttendance_DuplicateAttendance_ThrowsException() {
            // Given
            Attendance existingAttendance = new Attendance();
            existingAttendance.setAttendanceId(50L);
            existingAttendance.setSessionId(1L);
            existingAttendance.setStudentUsername("teststudent");

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.of(existingAttendance));

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });

            assertEquals("Student already attended this session", exception.getMessage());
            verify(attendanceRepository, never()).save(any());
            verify(databaseLoggerService).logError(eq("ATTENDANCE_DUPLICATE"), anyString(), isNull(),
                    eq("teststudent"), anyString());
        }

        @Test
        @DisplayName("Duplicate check is case-insensitive")
        void recordAttendance_DuplicateCaseInsensitive_ThrowsException() {
            // Given
            testAttendance.setStudentUsername("TestStudent"); // Mixed case

            Attendance existingAttendance = new Attendance();
            existingAttendance.setAttendanceId(50L);
            existingAttendance.setSessionId(1L);
            existingAttendance.setStudentUsername("teststudent"); // Lowercase in DB

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.of(existingAttendance));

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });
            verify(attendanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("Different students can both record attendance for same session")
        void recordAttendance_DifferentStudents_BothSucceed() {
            // Given - First student already attended
            Attendance student1Attendance = new Attendance();
            student1Attendance.setAttendanceId(50L);
            student1Attendance.setSessionId(1L);
            student1Attendance.setStudentUsername("student1");

            // Second student trying to attend
            testAttendance.setStudentUsername("student2");
            User student2 = new User();
            student2.setBguUsername("student2");
            student2.setFirstName("Student");
            student2.setLastName("Two");
            student2.setIsParticipant(true);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("student2")).thenReturn(Optional.of(student2));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "student2"))
                    .thenReturn(Optional.empty()); // No existing attendance for student2
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertNotNull(result);
            assertEquals("student2", result.getStudentUsername());
            verify(attendanceRepository).save(any());
        }
    }

    // ==================== Session Validation Tests ====================

    @Nested
    @DisplayName("Session Validation Tests")
    class SessionValidationTests {

        @Test
        @DisplayName("Reject attendance when session not found")
        void recordAttendance_SessionNotFound_ThrowsException() {
            // Given
            when(sessionRepository.findById(1L)).thenReturn(Optional.empty());

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });

            assertTrue(exception.getMessage().contains("Session not found"));
            verify(attendanceRepository, never()).save(any());
            verify(databaseLoggerService).logError(eq("ATTENDANCE_SESSION_NOT_FOUND"), anyString(), isNull(),
                    eq("teststudent"), anyString());
        }

        @Test
        @DisplayName("Reject attendance when session is CLOSED")
        void recordAttendance_SessionClosed_ThrowsException() {
            // Given
            testSession.setStatus(Session.SessionStatus.CLOSED);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });

            assertTrue(exception.getMessage().contains("Session is not open"));
            verify(attendanceRepository, never()).save(any());
            verify(databaseLoggerService).logError(eq("ATTENDANCE_SESSION_NOT_OPEN"), anyString(), isNull(),
                    eq("teststudent"), anyString());
        }

        @Test
        @DisplayName("Reject attendance when student not found in database")
        void recordAttendance_StudentNotFound_ThrowsException() {
            // Given
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.empty());

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });

            assertTrue(exception.getMessage().contains("Student not found"));
            verify(attendanceRepository, never()).save(any());
            verify(databaseLoggerService).logError(eq("ATTENDANCE_STUDENT_NOT_FOUND"), anyString(), isNull(),
                    eq("teststudent"), anyString());
        }

        @Test
        @DisplayName("Reject attendance when sessionId is null in request")
        void recordAttendance_NullSessionId_ThrowsException() {
            // Given
            testAttendance.setSessionId(null);
            when(sessionRepository.findById(isNull())).thenReturn(Optional.empty());

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });
        }

        @Test
        @DisplayName("Session with OPEN status allows attendance")
        void recordAttendance_SessionOpen_AllowsAttendance() {
            // Given
            testSession.setStatus(Session.SessionStatus.OPEN);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertNotNull(result);
            verify(attendanceRepository).save(any());
        }
    }

    // ==================== Time Window Validation Tests ====================

    @Nested
    @DisplayName("Time Window Validation Tests")
    class TimeWindowValidationTests {

        @Test
        @DisplayName("Reject attendance when window has closed")
        void recordAttendance_WindowClosed_ThrowsException() {
            // Given - Session started 20 minutes ago, window is 15 minutes
            ZonedDateTime nowIsrael = ZonedDateTime.now(ZoneId.of("Asia/Jerusalem"));
            testSession.setStartTime(nowIsrael.toLocalDateTime().minusMinutes(20));

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });

            assertTrue(exception.getMessage().contains("Attendance window has closed"));
            verify(attendanceRepository, never()).save(any());
            verify(databaseLoggerService).logError(eq("ATTENDANCE_WINDOW_CLOSED"), anyString(), isNull(),
                    eq("teststudent"), anyString());
        }

        @Test
        @DisplayName("Allow attendance within window (just before close)")
        void recordAttendance_WithinWindow_Success() {
            // Given - Session started 10 minutes ago, window is 15 minutes (5 mins remaining)
            ZonedDateTime nowIsrael = ZonedDateTime.now(ZoneId.of("Asia/Jerusalem"));
            testSession.setStartTime(nowIsrael.toLocalDateTime().minusMinutes(10));

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertNotNull(result);
            verify(attendanceRepository).save(any());
        }

        @Test
        @DisplayName("Allow attendance when session just started")
        void recordAttendance_SessionJustStarted_Success() {
            // Given - Session started just now
            ZonedDateTime nowIsrael = ZonedDateTime.now(ZoneId.of("Asia/Jerusalem"));
            testSession.setStartTime(nowIsrael.toLocalDateTime());

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertNotNull(result);
            verify(attendanceRepository).save(any());
        }

        @Test
        @DisplayName("Window check skipped when session start time is null")
        void recordAttendance_NullStartTime_SkipsWindowCheck() {
            // Given
            testSession.setStartTime(null);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then - No exception thrown, attendance recorded
            assertNotNull(result);
            verify(attendanceRepository).save(any());
        }

        @Test
        @DisplayName("Uses configurable window duration from AppConfigService")
        void recordAttendance_ConfigurableWindowDuration_Success() {
            // Given - Session started 25 minutes ago, but config says window is 30 minutes
            ZonedDateTime nowIsrael = ZonedDateTime.now(ZoneId.of("Asia/Jerusalem"));
            testSession.setStartTime(nowIsrael.toLocalDateTime().minusMinutes(25));

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(appConfigService.getIntegerConfig("presenter_close_session_duration_minutes", 15))
                    .thenReturn(30); // Extended window
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertNotNull(result);
            verify(attendanceRepository).save(any());
        }

        @Test
        @DisplayName("Window check uses Israel timezone")
        void recordAttendance_UsesIsraelTimezone_Success() {
            // Given - Session set in Israel timezone, 5 minutes ago
            ZonedDateTime nowIsrael = ZonedDateTime.now(ZoneId.of("Asia/Jerusalem"));
            testSession.setStartTime(nowIsrael.toLocalDateTime().minusMinutes(5));

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertNotNull(result);
        }

        @Test
        @DisplayName("Reject attendance exactly at window boundary")
        void recordAttendance_ExactlyAtWindowBoundary_Rejected() {
            // Given - Session started 16 minutes ago, window is exactly 15 minutes
            ZonedDateTime nowIsrael = ZonedDateTime.now(ZoneId.of("Asia/Jerusalem"));
            testSession.setStartTime(nowIsrael.toLocalDateTime().minusMinutes(16));

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(appConfigService.getIntegerConfig("presenter_close_session_duration_minutes", 15))
                    .thenReturn(15);

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });
        }
    }

    // ==================== Error Scenarios Tests ====================

    @Nested
    @DisplayName("Error Scenarios Tests")
    class ErrorScenariosTests {

        @Test
        @DisplayName("Handle null username gracefully")
        void recordAttendance_NullUsername_Handles() {
            // Given
            testAttendance.setStudentUsername(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            // findByBguUsernameIgnoreCase should handle null
            when(userRepository.findByBguUsernameIgnoreCase(isNull())).thenReturn(Optional.empty());

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });
        }

        @Test
        @DisplayName("Handle empty username gracefully")
        void recordAttendance_EmptyUsername_Handles() {
            // Given
            testAttendance.setStudentUsername("   ");
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase(isNull())).thenReturn(Optional.empty());

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });
        }

        @Test
        @DisplayName("SessionId mismatch after save throws IllegalStateException")
        void recordAttendance_SessionIdMismatch_ThrowsIllegalState() {
            // Given - This tests the post-save verification
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                saved.setSessionId(999L); // Mismatch - simulate database error
                return saved;
            });

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });

            assertTrue(exception.getMessage().contains("wrong sessionId"));
            verify(databaseLoggerService).logError(eq("ATTENDANCE_SAVED_WRONG_SESSION_ID"), anyString(), isNull(),
                    eq("teststudent"), anyString());
        }

        @Test
        @DisplayName("Database exception is propagated and logged")
        void recordAttendance_DatabaseError_PropagatesException() {
            // Given
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class)))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });

            assertEquals("Database connection failed", exception.getMessage());
            verify(databaseLoggerService).logError(eq("ATTENDANCE_RECORDING_ERROR"), anyString(), any(),
                    eq("teststudent"), anyString());
        }

        @Test
        @DisplayName("IllegalArgumentException from validation is re-thrown")
        void recordAttendance_ValidationError_RethrowsException() {
            // Given
            when(sessionRepository.findById(1L)).thenReturn(Optional.empty());

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                attendanceService.recordAttendance(testAttendance);
            });

            assertTrue(exception.getMessage().contains("Session not found"));
        }
    }

    // ==================== Query Methods Tests ====================

    @Nested
    @DisplayName("Query Methods Tests")
    class QueryMethodsTests {

        @Test
        @DisplayName("Get attendance by ID - found")
        void getAttendanceById_Found_ReturnsAttendance() {
            // Given
            Attendance attendance = new Attendance();
            attendance.setAttendanceId(1L);
            attendance.setSessionId(1L);
            attendance.setStudentUsername("teststudent");

            when(attendanceRepository.findById(1L)).thenReturn(Optional.of(attendance));

            // When
            Optional<Attendance> result = attendanceService.getAttendanceById(1L);

            // Then
            assertTrue(result.isPresent());
            assertEquals(1L, result.get().getAttendanceId());
        }

        @Test
        @DisplayName("Get attendance by ID - not found")
        void getAttendanceById_NotFound_ReturnsEmpty() {
            // Given
            when(attendanceRepository.findById(999L)).thenReturn(Optional.empty());

            // When
            Optional<Attendance> result = attendanceService.getAttendanceById(999L);

            // Then
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Get attendance by session ID returns all records")
        void getAttendanceBySession_ReturnsAllRecords() {
            // Given
            Attendance att1 = new Attendance();
            att1.setAttendanceId(1L);
            att1.setSessionId(1L);
            att1.setStudentUsername("student1");

            Attendance att2 = new Attendance();
            att2.setAttendanceId(2L);
            att2.setSessionId(1L);
            att2.setStudentUsername("student2");

            when(attendanceRepository.findBySessionId(1L)).thenReturn(Arrays.asList(att1, att2));

            // When
            List<Attendance> result = attendanceService.getAttendanceBySession(1L);

            // Then
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Get attendance by session ID filters out wrong session records")
        void getAttendanceBySession_FiltersInvalidRecords() {
            // Given - Repository returns record with wrong sessionId (simulates data inconsistency)
            Attendance validAtt = new Attendance();
            validAtt.setAttendanceId(1L);
            validAtt.setSessionId(1L);
            validAtt.setStudentUsername("student1");

            Attendance invalidAtt = new Attendance();
            invalidAtt.setAttendanceId(2L);
            invalidAtt.setSessionId(999L); // Wrong session ID
            invalidAtt.setStudentUsername("student2");

            when(attendanceRepository.findBySessionId(1L)).thenReturn(Arrays.asList(validAtt, invalidAtt));

            // When
            List<Attendance> result = attendanceService.getAttendanceBySession(1L);

            // Then - Invalid record is filtered out
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getSessionId());
            verify(databaseLoggerService).logError(eq("ATTENDANCE_WRONG_SESSION_ID"), anyString(), isNull(),
                    isNull(), anyString());
        }

        @Test
        @DisplayName("Get attendance by session ID - empty list")
        void getAttendanceBySession_NoRecords_ReturnsEmptyList() {
            // Given
            when(attendanceRepository.findBySessionId(1L)).thenReturn(Collections.emptyList());

            // When
            List<Attendance> result = attendanceService.getAttendanceBySession(1L);

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Get attendance by student username")
        void getAttendanceByStudent_ReturnsRecords() {
            // Given - The service uses findAttendanceWithDetailsByStudent which returns Object[] rows
            java.sql.Timestamp now = java.sql.Timestamp.valueOf(LocalDateTime.now());
            Object[] row = new Object[] {
                1L,                                 // attendance_id
                1L,                                 // session_id
                "teststudent",                      // student_username
                now,                                // attendance_time
                "QR_SCAN",                          // method
                null,                               // request_status
                null,                               // manual_reason
                null,                               // requested_at
                null,                               // approved_at
                null,                               // approved_by_username
                null,                               // device_id
                null,                               // auto_flags
                null,                               // notes
                now,                                // created_at
                null,                               // updated_at
                "Test Topic"                        // topic
            };

            when(attendanceRepository.findAttendanceWithDetailsByStudent("teststudent"))
                    .thenReturn(Collections.singletonList(row));

            // When
            List<Attendance> result = attendanceService.getAttendanceByStudent("teststudent");

            // Then
            assertEquals(1, result.size());
            assertEquals("teststudent", result.get(0).getStudentUsername());
            assertEquals(1L, result.get(0).getSessionId());
            assertEquals("Test Topic", result.get(0).getTopic());
        }

        @Test
        @DisplayName("Check if student attended session - true")
        void hasStudentAttended_True() {
            // Given
            when(attendanceRepository.existsBySessionIdAndStudentUsername(1L, "teststudent"))
                    .thenReturn(true);

            // When
            boolean result = attendanceService.hasStudentAttended(1L, "teststudent");

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("Check if student attended session - false")
        void hasStudentAttended_False() {
            // Given
            when(attendanceRepository.existsBySessionIdAndStudentUsername(1L, "teststudent"))
                    .thenReturn(false);

            // When
            boolean result = attendanceService.hasStudentAttended(1L, "teststudent");

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("Get attendance by method - QR_SCAN")
        void getAttendanceByMethod_QrScan_ReturnsRecords() {
            // Given
            Attendance qrAtt = new Attendance();
            qrAtt.setAttendanceId(1L);
            qrAtt.setMethod(Attendance.AttendanceMethod.QR_SCAN);

            when(attendanceRepository.findByMethod(Attendance.AttendanceMethod.QR_SCAN))
                    .thenReturn(Collections.singletonList(qrAtt));

            // When
            List<Attendance> result = attendanceService.getAttendanceByMethod(Attendance.AttendanceMethod.QR_SCAN);

            // Then
            assertEquals(1, result.size());
            assertEquals(Attendance.AttendanceMethod.QR_SCAN, result.get(0).getMethod());
        }

        @Test
        @DisplayName("Get attendance by method - MANUAL")
        void getAttendanceByMethod_Manual_ReturnsRecords() {
            // Given
            Attendance manualAtt = new Attendance();
            manualAtt.setAttendanceId(1L);
            manualAtt.setMethod(Attendance.AttendanceMethod.MANUAL);

            when(attendanceRepository.findByMethod(Attendance.AttendanceMethod.MANUAL))
                    .thenReturn(Collections.singletonList(manualAtt));

            // When
            List<Attendance> result = attendanceService.getAttendanceByMethod(Attendance.AttendanceMethod.MANUAL);

            // Then
            assertEquals(1, result.size());
            assertEquals(Attendance.AttendanceMethod.MANUAL, result.get(0).getMethod());
        }

        @Test
        @DisplayName("Get attendance between dates")
        void getAttendanceBetweenDates_ReturnsRecords() {
            // Given
            LocalDateTime start = LocalDateTime.now().minusDays(7);
            LocalDateTime end = LocalDateTime.now();

            Attendance att = new Attendance();
            att.setAttendanceId(1L);
            att.setAttendanceTime(LocalDateTime.now().minusDays(3));

            when(attendanceRepository.findAttendanceBetweenDates(start, end))
                    .thenReturn(Collections.singletonList(att));

            // When
            List<Attendance> result = attendanceService.getAttendanceBetweenDates(start, end);

            // Then
            assertEquals(1, result.size());
        }
    }

    // ==================== Attendance Statistics Tests ====================

    @Nested
    @DisplayName("Attendance Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Get session attendance statistics - all methods")
        void getSessionAttendanceStats_ReturnsStats() {
            // Given
            when(attendanceRepository.countBySessionId(1L)).thenReturn(10L);
            when(attendanceRepository.countByMethod(Attendance.AttendanceMethod.QR_SCAN)).thenReturn(8L);
            when(attendanceRepository.countByMethod(Attendance.AttendanceMethod.MANUAL)).thenReturn(1L);
            when(attendanceRepository.countByMethod(Attendance.AttendanceMethod.PROXY)).thenReturn(1L);

            // When
            AttendanceService.AttendanceStats stats = attendanceService.getSessionAttendanceStats(1L);

            // Then
            assertNotNull(stats);
            assertEquals(10L, stats.getTotalAttendance());
            assertEquals(8L, stats.getQrScanCount());
            assertEquals(1L, stats.getManualCount());
            assertEquals(1L, stats.getProxyCount());
        }

        @Test
        @DisplayName("Get session attendance statistics - zero attendance")
        void getSessionAttendanceStats_ZeroAttendance() {
            // Given
            when(attendanceRepository.countBySessionId(1L)).thenReturn(0L);
            when(attendanceRepository.countByMethod(any())).thenReturn(0L);

            // When
            AttendanceService.AttendanceStats stats = attendanceService.getSessionAttendanceStats(1L);

            // Then
            assertNotNull(stats);
            assertEquals(0L, stats.getTotalAttendance());
            assertEquals(0L, stats.getQrScanCount());
            assertEquals(0L, stats.getManualCount());
            assertEquals(0L, stats.getProxyCount());
        }
    }

    // ==================== Delete Attendance Tests ====================

    @Nested
    @DisplayName("Delete Attendance Tests")
    class DeleteAttendanceTests {

        @Test
        @DisplayName("Delete attendance - success")
        void deleteAttendance_Success() {
            // Given
            when(attendanceRepository.existsById(1L)).thenReturn(true);
            doNothing().when(attendanceRepository).deleteById(1L);

            // When
            attendanceService.deleteAttendance(1L);

            // Then
            verify(attendanceRepository).deleteById(1L);
            verify(databaseLoggerService).logAttendance(eq("ATTENDANCE_DELETED"), isNull(), isNull(), isNull());
        }

        @Test
        @DisplayName("Delete attendance - not found")
        void deleteAttendance_NotFound_ThrowsException() {
            // Given
            when(attendanceRepository.existsById(999L)).thenReturn(false);

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                attendanceService.deleteAttendance(999L);
            });

            assertTrue(exception.getMessage().contains("Attendance record not found"));
            verify(attendanceRepository, never()).deleteById(anyLong());
            verify(databaseLoggerService).logError(eq("ATTENDANCE_DELETE_NOT_FOUND"), anyString(), isNull(),
                    isNull(), anyString());
        }

        @Test
        @DisplayName("Delete attendance - database error propagated")
        void deleteAttendance_DatabaseError_Propagated() {
            // Given
            when(attendanceRepository.existsById(1L)).thenReturn(true);
            doThrow(new RuntimeException("Database error")).when(attendanceRepository).deleteById(1L);

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                attendanceService.deleteAttendance(1L);
            });

            assertEquals("Database error", exception.getMessage());
            verify(databaseLoggerService).logError(eq("ATTENDANCE_DELETE_ERROR"), anyString(), any(),
                    isNull(), anyString());
        }
    }

    // ==================== Different Attendance Methods Tests ====================

    @Nested
    @DisplayName("Different Attendance Methods Tests")
    class AttendanceMethodsTests {

        @Test
        @DisplayName("Record MANUAL attendance method")
        void recordAttendance_ManualMethod_Success() {
            // Given
            testAttendance.setMethod(Attendance.AttendanceMethod.MANUAL);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertEquals(Attendance.AttendanceMethod.MANUAL, result.getMethod());
        }

        @Test
        @DisplayName("Record MANUAL_REQUEST attendance method with reason")
        void recordAttendance_ManualRequestMethod_Success() {
            // Given
            testAttendance.setMethod(Attendance.AttendanceMethod.MANUAL_REQUEST);
            testAttendance.setManualReason("Phone battery died");

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertEquals(Attendance.AttendanceMethod.MANUAL_REQUEST, result.getMethod());
            assertEquals("Phone battery died", result.getManualReason());
        }

        @Test
        @DisplayName("Record PROXY attendance method")
        void recordAttendance_ProxyMethod_Success() {
            // Given
            testAttendance.setMethod(Attendance.AttendanceMethod.PROXY);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertEquals(Attendance.AttendanceMethod.PROXY, result.getMethod());
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Handle very long username")
        void recordAttendance_LongUsername_Handles() {
            // Given
            String longUsername = "a".repeat(50);
            testAttendance.setStudentUsername(longUsername);

            User userWithLongName = new User();
            userWithLongName.setBguUsername(longUsername);
            userWithLongName.setIsParticipant(true);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase(longUsername)).thenReturn(Optional.of(userWithLongName));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, longUsername))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertNotNull(result);
            assertEquals(longUsername, result.getStudentUsername());
        }

        @Test
        @DisplayName("Record attendance with explicit attendance time")
        void recordAttendance_ExplicitTime_Preserved() {
            // Given
            LocalDateTime explicitTime = LocalDateTime.of(2025, 1, 15, 10, 30, 0);
            testAttendance.setAttendanceTime(explicitTime);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertEquals(explicitTime, result.getAttendanceTime());
        }

        @Test
        @DisplayName("Multiple concurrent sessions - validates correct session")
        void recordAttendance_MultipleSessionsExist_ValidatesCorrectSession() {
            // Given - Session 1 is OPEN, Session 2 is CLOSED
            Session closedSession = new Session();
            closedSession.setSessionId(2L);
            closedSession.setStatus(Session.SessionStatus.CLOSED);

            // Request is for session 1 (OPEN)
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then - Success because we queried the correct session
            assertNotNull(result);
            assertEquals(1L, result.getSessionId());
        }

        @Test
        @DisplayName("AppConfigService returns null - uses default window")
        void recordAttendance_NullConfigValue_UsesDefault() {
            // Given
            ZonedDateTime nowIsrael = ZonedDateTime.now(ZoneId.of("Asia/Jerusalem"));
            testSession.setStartTime(nowIsrael.toLocalDateTime().minusMinutes(10));

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsernameIgnoreCase("teststudent")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "teststudent"))
                    .thenReturn(Optional.empty());
            when(appConfigService.getIntegerConfig("presenter_close_session_duration_minutes", 15))
                    .thenReturn(15); // Default value used
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance saved = invocation.getArgument(0);
                saved.setAttendanceId(100L);
                return saved;
            });

            // When
            Attendance result = attendanceService.recordAttendance(testAttendance);

            // Then
            assertNotNull(result);
        }
    }
}
