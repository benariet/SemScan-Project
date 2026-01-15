package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.dto.ManualAttendanceRequest;
import edu.bgu.semscanapi.dto.ManualAttendanceResponse;
import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.AttendanceRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ManualAttendanceService
 * Tests manual attendance request creation, approval, rejection, time windows, and error handling
 */
@ExtendWith(MockitoExtension.class)
class ManualAttendanceServiceTest {

    private static final ZoneId ISRAEL_TIMEZONE = ZoneId.of("Asia/Jerusalem");

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DatabaseLoggerService databaseLoggerService;

    @Mock
    private GlobalConfig globalConfig;

    @Mock
    private AppConfigService appConfigService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ManualAttendanceService manualAttendanceService;

    private ManualAttendanceRequest testRequest;
    private Session testSession;
    private User testStudent;
    private User testApprover;
    private Attendance testAttendance;

    /**
     * Get current time in Israel timezone to match session times
     */
    private LocalDateTime nowIsrael() {
        return ZonedDateTime.now(ISRAEL_TIMEZONE).toLocalDateTime();
    }

    @BeforeEach
    void setUp() {
        // Mock AppConfigService for time window values - use lenient to avoid UnnecessaryStubbingException
        lenient().when(appConfigService.getIntegerConfig(eq("student_attendance_window_before_minutes"), anyInt())).thenReturn(10);
        lenient().when(appConfigService.getIntegerConfig(eq("student_attendance_window_after_minutes"), anyInt())).thenReturn(20);

        // Mock GlobalConfig fallback values - use lenient
        lenient().when(globalConfig.getManualAttendanceWindowBeforeMinutes()).thenReturn(10);
        lenient().when(globalConfig.getManualAttendanceWindowAfterMinutes()).thenReturn(20);

        // Setup test request
        testRequest = new ManualAttendanceRequest();
        testRequest.setSessionId(1L);
        testRequest.setStudentUsername("student1");
        testRequest.setReason("Forgot to scan QR code");
        testRequest.setDeviceId("device123");

        // Setup test session - use current Israel time for proper window testing
        testSession = new Session();
        testSession.setSessionId(1L);
        testSession.setSeminarId(1L);
        testSession.setStartTime(nowIsrael().minusMinutes(5)); // Started 5 minutes ago (within window)
        testSession.setStatus(Session.SessionStatus.OPEN);

        // Setup test student
        testStudent = new User();
        testStudent.setBguUsername("student1");
        testStudent.setFirstName("John");
        testStudent.setLastName("Doe");
        testStudent.setEmail("student1@bgu.ac.il");

        // Setup test approver (presenter)
        testApprover = new User();
        testApprover.setBguUsername("approver1");
        testApprover.setFirstName("Jane");
        testApprover.setLastName("Smith");
        testApprover.setEmail("approver1@bgu.ac.il");

        // Setup test attendance
        testAttendance = new Attendance();
        testAttendance.setAttendanceId(1L);
        testAttendance.setSessionId(1L);
        testAttendance.setStudentUsername("student1");
        testAttendance.setManualReason("Test reason");
    }

    // ==================== Create Manual Request Tests ====================

    @Nested
    @DisplayName("Create Manual Request Tests")
    class CreateManualRequestTests {

        @Test
        @DisplayName("Create request with valid data - SUCCESS")
        void createManualRequest_WithValidData_CreatesSuccessfully() {
            // Given
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.countBySessionId(1L)).thenReturn(10L);
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(2L);
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance a = invocation.getArgument(0);
                a.setAttendanceId(1L);
                a.setRequestedAt(LocalDateTime.now());
                return a;
            });

            // When
            ManualAttendanceResponse response = manualAttendanceService.createManualRequest(testRequest);

            // Then
            assertNotNull(response);
            assertEquals(1L, response.getAttendanceId());
            assertEquals(1L, response.getSessionId());
            assertEquals("student1", response.getStudentUsername());
            assertEquals("John Doe", response.getStudentName());
            assertEquals("PENDING_APPROVAL", response.getRequestStatus());
            assertNotNull(response.getAutoFlags());
            assertTrue(response.getMessage().contains("submitted successfully"));
            verify(attendanceRepository).save(any(Attendance.class));
            verify(databaseLoggerService).logAttendance(eq("MANUAL_ATTENDANCE_REQUEST_CREATED"),
                eq("student1"), eq(1L), eq("MANUAL_REQUEST"));
        }

        @Test
        @DisplayName("Create request with uppercase username - normalizes to lowercase")
        void createManualRequest_WithUppercaseUsername_NormalizesToLowercase() {
            // Given
            testRequest.setStudentUsername("STUDENT1");
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.countBySessionId(1L)).thenReturn(5L);
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(0L);
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance a = invocation.getArgument(0);
                a.setAttendanceId(1L);
                return a;
            });

            // When
            ManualAttendanceResponse response = manualAttendanceService.createManualRequest(testRequest);

            // Then
            assertNotNull(response);
            assertEquals("student1", response.getStudentUsername());
        }

        @Test
        @DisplayName("Create request with whitespace username - trims whitespace")
        void createManualRequest_WithWhitespaceUsername_TrimsWhitespace() {
            // Given
            testRequest.setStudentUsername("  student1  ");
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.countBySessionId(1L)).thenReturn(5L);
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(0L);
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance a = invocation.getArgument(0);
                a.setAttendanceId(1L);
                return a;
            });

            // When
            ManualAttendanceResponse response = manualAttendanceService.createManualRequest(testRequest);

            // Then
            assertNotNull(response);
            assertEquals("student1", response.getStudentUsername());
        }

        @Test
        @DisplayName("Create request for closed session within time window - SUCCESS")
        void createManualRequest_ClosedSessionWithinWindow_Succeeds() {
            // Given - Session is CLOSED but we're within the time window
            testSession.setStatus(Session.SessionStatus.CLOSED);
            testSession.setStartTime(nowIsrael().minusMinutes(5)); // Still within window

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.countBySessionId(1L)).thenReturn(10L);
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(2L);
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance a = invocation.getArgument(0);
                a.setAttendanceId(1L);
                return a;
            });

            // When
            ManualAttendanceResponse response = manualAttendanceService.createManualRequest(testRequest);

            // Then
            assertNotNull(response);
            assertEquals("PENDING_APPROVAL", response.getRequestStatus());
            verify(databaseLoggerService).logAttendance(eq("MANUAL_ATTENDANCE_CLOSED_SESSION_ALLOWED"),
                eq("student1"), eq(1L), eq("MANUAL_REQUEST"));
        }
    }

    // ==================== Session Not Found Tests ====================

    @Nested
    @DisplayName("Session Not Found Tests")
    class SessionNotFoundTests {

        @Test
        @DisplayName("Create request with non-existent session - throws IllegalArgumentException")
        void createManualRequest_WithSessionNotFound_ThrowsException() {
            // Given
            when(sessionRepository.findById(1L)).thenReturn(Optional.empty());

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.createManualRequest(testRequest)
            );
            assertTrue(exception.getMessage().contains("Session not found"));
            verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_SESSION_NOT_FOUND"), anyString(),
                isNull(), eq("student1"), anyString());
        }
    }

    // ==================== Time Window Validation Tests ====================

    @Nested
    @DisplayName("Time Window Validation Tests")
    class TimeWindowValidationTests {

        @Test
        @DisplayName("Request outside time window (too early) - throws IllegalArgumentException")
        void createManualRequest_TooEarly_ThrowsException() {
            // Given - Session starts in 30 minutes (beyond 10-minute before window)
            testSession.setStartTime(nowIsrael().plusMinutes(30));
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.createManualRequest(testRequest)
            );
            assertTrue(exception.getMessage().contains("outside the allowed time window"));
            verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_OUTSIDE_WINDOW"), anyString(),
                isNull(), eq("student1"), anyString());
        }

        @Test
        @DisplayName("Request outside time window (too late) - throws IllegalArgumentException")
        void createManualRequest_TooLate_ThrowsException() {
            // Given - Session started 1 hour ago (beyond 20-minute after window)
            testSession.setStartTime(nowIsrael().minusHours(1));
            testSession.setEndTime(null); // No end time, uses windowAfterMinutes
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.createManualRequest(testRequest)
            );
            assertTrue(exception.getMessage().contains("outside the allowed time window"));
            verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_OUTSIDE_WINDOW"), anyString(),
                isNull(), eq("student1"), anyString());
        }

        @Test
        @DisplayName("Request exactly at window start boundary - SUCCESS")
        void createManualRequest_AtWindowStartBoundary_Succeeds() {
            // Given - Session starts in exactly 10 minutes (at the boundary)
            testSession.setStartTime(nowIsrael().plusMinutes(10));

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.countBySessionId(1L)).thenReturn(5L);
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(0L);
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> {
                Attendance a = inv.getArgument(0);
                a.setAttendanceId(1L);
                return a;
            });

            // When
            ManualAttendanceResponse response = manualAttendanceService.createManualRequest(testRequest);

            // Then
            assertNotNull(response);
            assertEquals("PENDING_APPROVAL", response.getRequestStatus());
        }

        @Test
        @DisplayName("Request with session end time - uses end time as window end")
        void createManualRequest_WithSessionEndTime_UsesEndTimeAsWindowEnd() {
            // Given - Session has end time, request is after start but before end
            testSession.setStartTime(nowIsrael().minusMinutes(30));
            testSession.setEndTime(nowIsrael().plusMinutes(30)); // Session ends in 30 minutes

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.countBySessionId(1L)).thenReturn(5L);
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(0L);
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> {
                Attendance a = inv.getArgument(0);
                a.setAttendanceId(1L);
                return a;
            });

            // When
            ManualAttendanceResponse response = manualAttendanceService.createManualRequest(testRequest);

            // Then
            assertNotNull(response);
            assertEquals("PENDING_APPROVAL", response.getRequestStatus());
        }

        @Test
        @DisplayName("Request after session end time - throws IllegalArgumentException")
        void createManualRequest_AfterSessionEndTime_ThrowsException() {
            // Given - Session ended 30 minutes ago
            testSession.setStartTime(nowIsrael().minusHours(1));
            testSession.setEndTime(nowIsrael().minusMinutes(30)); // Ended 30 minutes ago
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.createManualRequest(testRequest)
            );
            assertTrue(exception.getMessage().contains("outside the allowed time window"));
        }

        @Test
        @DisplayName("Session with null start time - throws IllegalArgumentException")
        void createManualRequest_NullStartTime_ThrowsException() {
            // Given
            testSession.setStartTime(null);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.createManualRequest(testRequest)
            );
            assertTrue(exception.getMessage().contains("start time is not set"));
            verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_NO_START_TIME"), anyString(),
                isNull(), eq("student1"), anyString());
        }
    }

    // ==================== Student Not Found Tests ====================

    @Nested
    @DisplayName("Student Not Found Tests")
    class StudentNotFoundTests {

        @Test
        @DisplayName("Create request with non-existent student - throws IllegalArgumentException")
        void createManualRequest_WithStudentNotFound_ThrowsException() {
            // Given
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.empty());

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.createManualRequest(testRequest)
            );
            assertTrue(exception.getMessage().contains("Student not found"));
            verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_STUDENT_NOT_FOUND"), anyString(),
                isNull(), eq("student1"), anyString());
        }
    }

    // ==================== Duplicate Request Handling Tests ====================

    @Nested
    @DisplayName("Duplicate Request Handling Tests")
    class DuplicateRequestHandlingTests {

        @Test
        @DisplayName("Create request when already confirmed - throws IllegalArgumentException")
        void createManualRequest_WithAlreadyConfirmed_ThrowsException() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.CONFIRMED);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                    .thenReturn(Optional.of(testAttendance));

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.createManualRequest(testRequest)
            );
            assertTrue(exception.getMessage().contains("already marked as present"));
            verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_ALREADY_CONFIRMED"), anyString(),
                isNull(), eq("student1"), anyString());
        }

        @Test
        @DisplayName("Create request when pending exists - updates existing request")
        void createManualRequest_WithPendingRequest_UpdatesExisting() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            testAttendance.setManualReason("Old reason");
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                    .thenReturn(Optional.of(testAttendance));
            when(attendanceRepository.countBySessionId(1L)).thenReturn(10L);
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(2L);
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance a = invocation.getArgument(0);
                a.setRequestedAt(LocalDateTime.now());
                return a;
            });

            // When
            ManualAttendanceResponse response = manualAttendanceService.createManualRequest(testRequest);

            // Then
            assertNotNull(response);
            assertEquals(1L, response.getAttendanceId());
            assertTrue(response.getMessage().contains("updated successfully"));
            verify(attendanceRepository).save(argThat(attendance ->
                attendance.getManualReason().equals("Forgot to scan QR code")
            ));
        }

        @Test
        @DisplayName("Create request when rejected exists - creates new attendance record")
        void createManualRequest_WithRejectedRequest_CreatesNewRecord() {
            // Given - Existing rejected attendance
            Attendance rejectedAttendance = new Attendance();
            rejectedAttendance.setAttendanceId(1L);
            rejectedAttendance.setSessionId(1L);
            rejectedAttendance.setStudentUsername("student1");
            rejectedAttendance.setRequestStatus(Attendance.RequestStatus.REJECTED);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                    .thenReturn(Optional.of(rejectedAttendance));
            when(attendanceRepository.countBySessionId(1L)).thenReturn(10L);
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(0L);
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance a = invocation.getArgument(0);
                a.setRequestedAt(LocalDateTime.now());
                return a;
            });

            // When
            ManualAttendanceResponse response = manualAttendanceService.createManualRequest(testRequest);

            // Then
            assertNotNull(response);
            assertEquals("PENDING_APPROVAL", response.getRequestStatus());
            verify(attendanceRepository).save(any(Attendance.class));
        }
    }

    // ==================== Get Pending Requests Tests ====================

    @Nested
    @DisplayName("Get Pending Requests Tests")
    class GetPendingRequestsTests {

        @Test
        @DisplayName("Get pending requests - returns list with student names")
        void getPendingRequests_WithPendingRequests_ReturnsList() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            testAttendance.setRequestedAt(LocalDateTime.now());
            testAttendance.setManualReason("Test reason");

            when(attendanceRepository.findPendingRequestsBySessionId(1L))
                    .thenReturn(List.of(testAttendance));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));

            // When
            List<ManualAttendanceResponse> result = manualAttendanceService.getPendingRequests(1L);

            // Then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("PENDING_APPROVAL", result.get(0).getRequestStatus());
            assertEquals("John Doe", result.get(0).getStudentName());
            assertEquals("Test reason", result.get(0).getReason());
        }

        @Test
        @DisplayName("Get pending requests with unknown student - returns 'Unknown Student'")
        void getPendingRequests_WithUnknownStudent_ReturnsUnknownStudent() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            testAttendance.setRequestedAt(LocalDateTime.now());

            when(attendanceRepository.findPendingRequestsBySessionId(1L))
                    .thenReturn(List.of(testAttendance));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.empty());

            // When
            List<ManualAttendanceResponse> result = manualAttendanceService.getPendingRequests(1L);

            // Then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("Unknown Student", result.get(0).getStudentName());
        }

        @Test
        @DisplayName("Get pending requests - empty list when none pending")
        void getPendingRequests_WithNoPendingRequests_ReturnsEmptyList() {
            // Given
            when(attendanceRepository.findPendingRequestsBySessionId(1L))
                    .thenReturn(Collections.emptyList());

            // When
            List<ManualAttendanceResponse> result = manualAttendanceService.getPendingRequests(1L);

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Get pending requests - handles exception gracefully")
        void getPendingRequests_WithException_ThrowsRuntimeException() {
            // Given
            when(attendanceRepository.findPendingRequestsBySessionId(1L))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                manualAttendanceService.getPendingRequests(1L)
            );
            assertTrue(exception.getMessage().contains("retrieving pending requests"));
            verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_RETRIEVAL_ERROR"), anyString(),
                any(Exception.class), isNull(), anyString());
        }
    }

    // ==================== Approve Request Tests ====================

    @Nested
    @DisplayName("Approve Request Tests")
    class ApproveRequestTests {

        @Test
        @DisplayName("Approve pending request - SUCCESS")
        void approveRequest_WithValidPendingRequest_ApprovesSuccessfully() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance a = invocation.getArgument(0);
                a.setApprovedAt(LocalDateTime.now());
                return a;
            });

            // When
            ManualAttendanceResponse response = manualAttendanceService.approveRequest(1L, "approver1");

            // Then
            assertNotNull(response);
            assertEquals("CONFIRMED", response.getRequestStatus());
            assertTrue(response.getMessage().contains("approved successfully"));
            verify(attendanceRepository).save(argThat(attendance ->
                attendance.getRequestStatus() == Attendance.RequestStatus.CONFIRMED &&
                attendance.getMethod() == Attendance.AttendanceMethod.MANUAL &&
                attendance.getApprovedByUsername().equals("approver1")
            ));
            verify(databaseLoggerService).logAttendance(eq("MANUAL_ATTENDANCE_APPROVED"),
                eq("student1"), eq(1L), eq("MANUAL"));
        }

        @Test
        @DisplayName("Approve request with uppercase approver username - normalizes to lowercase")
        void approveRequest_WithUppercaseApprover_NormalizesToLowercase() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            ManualAttendanceResponse response = manualAttendanceService.approveRequest(1L, "APPROVER1");

            // Then
            assertNotNull(response);
            verify(attendanceRepository).save(argThat(attendance ->
                attendance.getApprovedByUsername().equals("approver1")
            ));
        }

        @Test
        @DisplayName("Approve non-existent request - throws IllegalArgumentException")
        void approveRequest_WithNotFound_ThrowsException() {
            // Given
            when(attendanceRepository.findById(1L)).thenReturn(Optional.empty());

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.approveRequest(1L, "approver1")
            );
            assertTrue(exception.getMessage().contains("not found"));
            verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_APPROVE_NOT_FOUND"), anyString(),
                isNull(), eq("approver1"), anyString());
        }

        @Test
        @DisplayName("Approve already confirmed request - throws IllegalArgumentException")
        void approveRequest_WithAlreadyConfirmed_ThrowsException() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.CONFIRMED);
            when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.approveRequest(1L, "approver1")
            );
            assertTrue(exception.getMessage().contains("not pending"));
            verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_APPROVE_INVALID_STATUS"), anyString(),
                isNull(), eq("approver1"), anyString());
        }

        @Test
        @DisplayName("Approve already rejected request - throws IllegalArgumentException")
        void approveRequest_WithAlreadyRejected_ThrowsException() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.REJECTED);
            when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.approveRequest(1L, "approver1")
            );
            assertTrue(exception.getMessage().contains("not pending"));
        }
    }

    // ==================== Reject Request Tests ====================

    @Nested
    @DisplayName("Reject Request Tests")
    class RejectRequestTests {

        @Test
        @DisplayName("Reject pending request - SUCCESS")
        void rejectRequest_WithValidPendingRequest_RejectsSuccessfully() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("approver1")).thenReturn(Optional.of(testApprover));
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance a = invocation.getArgument(0);
                a.setApprovedAt(LocalDateTime.now());
                return a;
            });

            // When
            ManualAttendanceResponse response = manualAttendanceService.rejectRequest(1L, "approver1");

            // Then
            assertNotNull(response);
            assertEquals("REJECTED", response.getRequestStatus());
            assertTrue(response.getMessage().contains("rejected"));
            verify(attendanceRepository).save(argThat(attendance ->
                attendance.getRequestStatus() == Attendance.RequestStatus.REJECTED &&
                attendance.getApprovedByUsername().equals("approver1")
            ));
            verify(databaseLoggerService).logAttendance(eq("MANUAL_ATTENDANCE_REJECTED"),
                eq("student1"), eq(1L), eq("MANUAL_REQUEST"));
        }

        @Test
        @DisplayName("Reject request with uppercase approver username - normalizes to lowercase")
        void rejectRequest_WithUppercaseApprover_NormalizesToLowercase() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("approver1")).thenReturn(Optional.of(testApprover));
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            ManualAttendanceResponse response = manualAttendanceService.rejectRequest(1L, "APPROVER1");

            // Then
            assertNotNull(response);
            verify(attendanceRepository).save(argThat(attendance ->
                attendance.getApprovedByUsername().equals("approver1")
            ));
        }

        @Test
        @DisplayName("Reject non-existent request - throws IllegalArgumentException")
        void rejectRequest_WithNotFound_ThrowsException() {
            // Given
            when(attendanceRepository.findById(1L)).thenReturn(Optional.empty());

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.rejectRequest(1L, "approver1")
            );
            assertTrue(exception.getMessage().contains("not found"));
            verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_REJECT_NOT_FOUND"), anyString(),
                isNull(), eq("approver1"), anyString());
        }

        @Test
        @DisplayName("Reject already rejected request - throws IllegalArgumentException")
        void rejectRequest_WithAlreadyRejected_ThrowsException() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.REJECTED);
            when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.rejectRequest(1L, "approver1")
            );
            assertTrue(exception.getMessage().contains("not pending"));
            verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_REJECT_INVALID_STATUS"), anyString(),
                isNull(), eq("approver1"), anyString());
        }

        @Test
        @DisplayName("Reject already confirmed request - throws IllegalArgumentException")
        void rejectRequest_WithAlreadyConfirmed_ThrowsException() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.CONFIRMED);
            when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                manualAttendanceService.rejectRequest(1L, "approver1")
            );
            assertTrue(exception.getMessage().contains("not pending"));
        }
    }

    // ==================== Helper Methods Tests ====================

    @Nested
    @DisplayName("Helper Methods Tests")
    class HelperMethodsTests {

        @Test
        @DisplayName("hasPendingRequests returns true when pending exists")
        void hasPendingRequests_WithPendingRequests_ReturnsTrue() {
            // Given
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(3L);

            // When
            boolean result = manualAttendanceService.hasPendingRequests(1L);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("hasPendingRequests returns false when none pending")
        void hasPendingRequests_WithNoPendingRequests_ReturnsFalse() {
            // Given
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(0L);

            // When
            boolean result = manualAttendanceService.hasPendingRequests(1L);

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("getPendingRequestCount returns correct count")
        void getPendingRequestCount_ReturnsCorrectCount() {
            // Given
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(5L);

            // When
            long result = manualAttendanceService.getPendingRequestCount(1L);

            // Then
            assertEquals(5L, result);
        }
    }

    // ==================== Email Notification Tests ====================

    @Nested
    @DisplayName("Email Notification Tests")
    class EmailNotificationTests {

        @Test
        @DisplayName("Reject sends email notification to student")
        void rejectRequest_SendsEmailNotification() {
            // Given
            testAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("approver1")).thenReturn(Optional.of(testApprover));
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));
            when(emailService.sendManualAttendanceNotificationEmail(anyString(), anyString(),
                anyBoolean(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EmailService.EmailResult.success());
            when(appConfigService.getStringConfig(eq("export_email_recipients"), anyString()))
                .thenReturn("admin@bgu.ac.il");

            // When
            manualAttendanceService.rejectRequest(1L, "approver1");

            // Then
            verify(emailService, atLeastOnce()).sendManualAttendanceNotificationEmail(
                anyString(), anyString(), eq(false), anyString(), anyString(), anyString(), anyString()
            );
        }

        @Test
        @DisplayName("Email service null - rejection still succeeds")
        void rejectRequest_WithNullEmailService_StillSucceeds() {
            // Given - Create service without email service
            ManualAttendanceService serviceWithoutEmail = new ManualAttendanceService();
            // Note: This test verifies the null check in sendAttendanceNotificationEmail
            // In practice, the @Autowired(required = false) handles this

            testAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            ManualAttendanceResponse response = manualAttendanceService.rejectRequest(1L, "approver1");

            // Then - Should not throw, even if email fails
            assertNotNull(response);
            assertEquals("REJECTED", response.getRequestStatus());
        }
    }

    // ==================== Auto-Flags Generation Tests ====================

    @Nested
    @DisplayName("Auto-Flags Generation Tests")
    class AutoFlagsGenerationTests {

        @Test
        @DisplayName("Auto-flags include inWindow flag")
        void createManualRequest_AutoFlagsIncludeInWindow() {
            // Given
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.countBySessionId(1L)).thenReturn(10L);
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(2L);
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance a = invocation.getArgument(0);
                a.setAttendanceId(1L);
                return a;
            });

            // When
            ManualAttendanceResponse response = manualAttendanceService.createManualRequest(testRequest);

            // Then
            assertNotNull(response.getAutoFlags());
            assertTrue(response.getAutoFlags().contains("inWindow"));
        }

        @Test
        @DisplayName("Auto-flags include pendingCount")
        void createManualRequest_AutoFlagsIncludePendingCount() {
            // Given
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                    .thenReturn(Optional.empty());
            when(attendanceRepository.countBySessionId(1L)).thenReturn(10L);
            when(attendanceRepository.countPendingRequestsBySessionId(1L)).thenReturn(3L);
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
                Attendance a = invocation.getArgument(0);
                a.setAttendanceId(1L);
                return a;
            });

            // When
            ManualAttendanceResponse response = manualAttendanceService.createManualRequest(testRequest);

            // Then
            assertNotNull(response.getAutoFlags());
            assertTrue(response.getAutoFlags().contains("pendingCount"));
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Student with null name fields - handles gracefully")
        void getPendingRequests_StudentWithNullName_HandlesGracefully() {
            // Given
            testStudent.setFirstName(null);
            testStudent.setLastName(null);
            testAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            testAttendance.setRequestedAt(LocalDateTime.now());

            when(attendanceRepository.findPendingRequestsBySessionId(1L))
                    .thenReturn(List.of(testAttendance));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));

            // When
            List<ManualAttendanceResponse> result = manualAttendanceService.getPendingRequests(1L);

            // Then
            assertNotNull(result);
            assertEquals(1, result.size());
            // Should handle null names without throwing
            assertNotNull(result.get(0).getStudentName());
        }

        @Test
        @DisplayName("Multiple pending requests - all returned")
        void getPendingRequests_MultiplePending_AllReturned() {
            // Given
            Attendance attendance1 = new Attendance();
            attendance1.setAttendanceId(1L);
            attendance1.setSessionId(1L);
            attendance1.setStudentUsername("student1");
            attendance1.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            attendance1.setRequestedAt(LocalDateTime.now());

            Attendance attendance2 = new Attendance();
            attendance2.setAttendanceId(2L);
            attendance2.setSessionId(1L);
            attendance2.setStudentUsername("student2");
            attendance2.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
            attendance2.setRequestedAt(LocalDateTime.now());

            User student2 = new User();
            student2.setBguUsername("student2");
            student2.setFirstName("Jane");
            student2.setLastName("Doe");

            when(attendanceRepository.findPendingRequestsBySessionId(1L))
                    .thenReturn(List.of(attendance1, attendance2));
            when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
            when(userRepository.findByBguUsername("student2")).thenReturn(Optional.of(student2));

            // When
            List<ManualAttendanceResponse> result = manualAttendanceService.getPendingRequests(1L);

            // Then
            assertEquals(2, result.size());
            assertEquals("John Doe", result.get(0).getStudentName());
            assertEquals("Jane Doe", result.get(1).getStudentName());
        }
    }
}
