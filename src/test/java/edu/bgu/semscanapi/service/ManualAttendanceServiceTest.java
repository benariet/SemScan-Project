package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.dto.ManualAttendanceRequest;
import edu.bgu.semscanapi.dto.ManualAttendanceResponse;
import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.AttendanceRepository;
import edu.bgu.semscanapi.repository.SessionRepository;
import edu.bgu.semscanapi.repository.UserRepository;
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
 * Comprehensive unit tests for ManualAttendanceService
 */
@ExtendWith(MockitoExtension.class)
class ManualAttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DatabaseLoggerService databaseLoggerService;

    @InjectMocks
    private ManualAttendanceService manualAttendanceService;

    private ManualAttendanceRequest testRequest;
    private Session testSession;
    private User testStudent;
    private Attendance testAttendance;

    @BeforeEach
    void setUp() {
        // Setup test request
        testRequest = new ManualAttendanceRequest();
        testRequest.setSessionId(1L);
        testRequest.setStudentUsername("student1");
        testRequest.setReason("Forgot to scan QR code");
        testRequest.setDeviceId("device123");

        // Setup test session
        testSession = new Session();
        testSession.setSessionId(1L);
        testSession.setSeminarId(1L);
        testSession.setStartTime(LocalDateTime.now().minusMinutes(5));
        testSession.setStatus(Session.SessionStatus.OPEN);

        // Setup test student
        testStudent = new User();
        testStudent.setBguUsername("student1");
        testStudent.setFirstName("John");
        testStudent.setLastName("Doe");

        // Setup test attendance
        testAttendance = new Attendance();
        testAttendance.setAttendanceId(1L);
        testAttendance.setSessionId(1L);
        testAttendance.setStudentUsername("student1");
    }

    // ==================== createManualRequest Tests ====================

    @Test
    void createManualRequest_WithValidData_CreatesSuccessfully() {
        // Given
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
        when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                .thenReturn(Optional.empty());
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
        assertEquals("PENDING_APPROVAL", response.getRequestStatus());
        verify(attendanceRepository).save(any(Attendance.class));
        verify(databaseLoggerService).logAttendance(anyString(), eq("student1"), eq(1L), anyString());
    }

    @Test
    void createManualRequest_WithSessionNotFound_ThrowsException() {
        // Given
        when(sessionRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            manualAttendanceService.createManualRequest(testRequest);
        });
        verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_SESSION_NOT_FOUND"), anyString(), 
            isNull(), eq("student1"), anyString());
    }

    @Test
    void createManualRequest_WithClosedSession_ThrowsException() {
        // Given
        testSession.setStatus(Session.SessionStatus.CLOSED);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            manualAttendanceService.createManualRequest(testRequest);
        });
        verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_SESSION_NOT_OPEN"), anyString(), 
            isNull(), eq("student1"), anyString());
    }

    @Test
    void createManualRequest_WithStudentNotFound_ThrowsException() {
        // Given
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(userRepository.findByBguUsername("student1")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            manualAttendanceService.createManualRequest(testRequest);
        });
        verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_STUDENT_NOT_FOUND"), anyString(), 
            isNull(), eq("student1"), anyString());
    }

    @Test
    void createManualRequest_WithAlreadyConfirmed_ThrowsException() {
        // Given
        testAttendance.setRequestStatus(Attendance.RequestStatus.CONFIRMED);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
        when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                .thenReturn(Optional.of(testAttendance));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            manualAttendanceService.createManualRequest(testRequest);
        });
        verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_ALREADY_CONFIRMED"), anyString(), 
            isNull(), eq("student1"), anyString());
    }

    @Test
    void createManualRequest_WithPendingRequest_UpdatesExisting() {
        // Given
        testAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(userRepository.findByBguUsername("student1")).thenReturn(Optional.of(testStudent));
        when(attendanceRepository.findBySessionIdAndStudentUsername(1L, "student1"))
                .thenReturn(Optional.of(testAttendance));
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
        verify(attendanceRepository).save(any(Attendance.class));
    }

    // ==================== getPendingRequests Tests ====================

    @Test
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
    }

    @Test
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

    // ==================== approveRequest Tests ====================

    @Test
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
        verify(attendanceRepository).save(any(Attendance.class));
        verify(databaseLoggerService).logAttendance(anyString(), eq("student1"), eq(1L), anyString());
    }

    @Test
    void approveRequest_WithNotFound_ThrowsException() {
        // Given
        when(attendanceRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            manualAttendanceService.approveRequest(1L, "approver1");
        });
        verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_APPROVE_NOT_FOUND"), anyString(), 
            isNull(), eq("approver1"), anyString());
    }

    @Test
    void approveRequest_WithNonPendingStatus_ThrowsException() {
        // Given
        testAttendance.setRequestStatus(Attendance.RequestStatus.CONFIRMED);
        when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            manualAttendanceService.approveRequest(1L, "approver1");
        });
        verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_APPROVE_INVALID_STATUS"), anyString(), 
            isNull(), eq("approver1"), anyString());
    }

    // ==================== rejectRequest Tests ====================

    @Test
    void rejectRequest_WithValidPendingRequest_RejectsSuccessfully() {
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
        ManualAttendanceResponse response = manualAttendanceService.rejectRequest(1L, "approver1");

        // Then
        assertNotNull(response);
        assertEquals("REJECTED", response.getRequestStatus());
        verify(attendanceRepository).save(any(Attendance.class));
        verify(databaseLoggerService).logAttendance(anyString(), eq("student1"), eq(1L), anyString());
    }

    @Test
    void rejectRequest_WithNotFound_ThrowsException() {
        // Given
        when(attendanceRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            manualAttendanceService.rejectRequest(1L, "approver1");
        });
        verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_REJECT_NOT_FOUND"), anyString(), 
            isNull(), eq("approver1"), anyString());
    }

    @Test
    void rejectRequest_WithNonPendingStatus_ThrowsException() {
        // Given
        testAttendance.setRequestStatus(Attendance.RequestStatus.REJECTED);
        when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            manualAttendanceService.rejectRequest(1L, "approver1");
        });
        verify(databaseLoggerService).logError(eq("MANUAL_ATTENDANCE_REJECT_INVALID_STATUS"), anyString(), 
            isNull(), eq("approver1"), anyString());
    }
}

