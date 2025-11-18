package edu.bgu.semscanapi.service;

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
 * Comprehensive unit tests for AttendanceService
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
    private DatabaseLoggerService databaseLoggerService;

    @InjectMocks
    private AttendanceService attendanceService;

    private Attendance testAttendance;
    private Session testSession;
    private User testStudent;

    @BeforeEach
    void setUp() {
        // Setup test session
        testSession = new Session();
        testSession.setSessionId(1L);
        testSession.setSeminarId(1L);
        testSession.setStartTime(LocalDateTime.now().minusHours(1));
        testSession.setEndTime(LocalDateTime.now().plusHours(1));
        testSession.setStatus(Session.SessionStatus.OPEN);

        // Setup test student
        testStudent = new User();
        testStudent.setBguUsername("student1");
        testStudent.setFirstName("John");
        testStudent.setLastName("Doe");
        testStudent.setIsParticipant(true);

        // Setup test attendance
        testAttendance = new Attendance();
        testAttendance.setSessionId(1L);
        testAttendance.setStudentUsername("student1");
        testAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);
    }

    // ==================== recordAttendance Tests ====================

    @Test
    void recordAttendance_WithValidData_RecordsSuccessfully() {
        // Given
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(userRepository.findByBguUsernameIgnoreCase("student1")).thenReturn(Optional.of(testStudent));
        when(attendanceRepository.existsBySessionIdAndStudentUsernameIgnoreCase(1L, "student1")).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
            Attendance a = invocation.getArgument(0);
            a.setAttendanceId(1L);
            a.setAttendanceTime(LocalDateTime.now());
            return a;
        });

        // When
        Attendance result = attendanceService.recordAttendance(testAttendance);

        // Then
        assertNotNull(result);
        assertNotNull(result.getAttendanceId());
        assertEquals(1L, result.getSessionId());
        assertEquals("student1", result.getStudentUsername());
        verify(attendanceRepository).save(any(Attendance.class));
        verify(databaseLoggerService, never()).logError(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void recordAttendance_WithSessionNotFound_ThrowsException() {
        // Given
        when(sessionRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            attendanceService.recordAttendance(testAttendance);
        });
        verify(databaseLoggerService).logError(eq("ATTENDANCE_SESSION_NOT_FOUND"), anyString(), isNull(), 
            eq("student1"), anyString());
    }

    @Test
    void recordAttendance_WithClosedSession_ThrowsException() {
        // Given
        testSession.setStatus(Session.SessionStatus.CLOSED);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            attendanceService.recordAttendance(testAttendance);
        });
        verify(databaseLoggerService).logError(eq("ATTENDANCE_SESSION_NOT_OPEN"), anyString(), isNull(), 
            eq("student1"), anyString());
    }

    @Test
    void recordAttendance_WithStudentNotFound_ThrowsException() {
        // Given
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(userRepository.findByBguUsernameIgnoreCase("student1")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            attendanceService.recordAttendance(testAttendance);
        });
        verify(databaseLoggerService).logError(eq("ATTENDANCE_STUDENT_NOT_FOUND"), anyString(), isNull(), 
            eq("student1"), anyString());
    }

    @Test
    void recordAttendance_WithNonParticipant_ThrowsException() {
        // Given
        testStudent.setIsParticipant(false);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(userRepository.findByBguUsernameIgnoreCase("student1")).thenReturn(Optional.of(testStudent));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            attendanceService.recordAttendance(testAttendance);
        });
        verify(databaseLoggerService).logError(eq("ATTENDANCE_NOT_PARTICIPANT"), anyString(), isNull(), 
            eq("student1"), anyString());
    }

    @Test
    void recordAttendance_WithDuplicateAttendance_ThrowsException() {
        // Given
        Attendance existingAttendance = new Attendance();
        existingAttendance.setAttendanceId(99L);
        existingAttendance.setSessionId(1L);
        existingAttendance.setStudentUsername("student1");

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(userRepository.findByBguUsernameIgnoreCase("student1")).thenReturn(Optional.of(testStudent));
        when(attendanceRepository.existsBySessionIdAndStudentUsernameIgnoreCase(1L, "student1")).thenReturn(true);
        when(attendanceRepository.findBySessionIdAndStudentUsernameIgnoreCase(1L, "student1"))
                .thenReturn(Optional.of(existingAttendance));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            attendanceService.recordAttendance(testAttendance);
        });
        verify(databaseLoggerService).logError(eq("ATTENDANCE_DUPLICATE"), anyString(), isNull(), 
            eq("student1"), anyString());
    }

    @Test
    void recordAttendance_WithNullSessionId_ThrowsException() {
        // Given
        testAttendance.setSessionId(null);
        // The service validates null sessionId and throws IllegalArgumentException

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            attendanceService.recordAttendance(testAttendance);
        });
        verify(databaseLoggerService).logError(eq("ATTENDANCE_SESSION_NOT_FOUND"), anyString(), isNull(), 
            eq("student1"), anyString());
    }

    @Test
    void recordAttendance_WithNullAttendanceTime_SetsCurrentTime() {
        // Given
        testAttendance.setAttendanceTime(null);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(userRepository.findByBguUsernameIgnoreCase("student1")).thenReturn(Optional.of(testStudent));
        when(attendanceRepository.existsBySessionIdAndStudentUsernameIgnoreCase(1L, "student1")).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
            Attendance a = invocation.getArgument(0);
            a.setAttendanceId(1L);
            return a;
        });

        // When
        Attendance result = attendanceService.recordAttendance(testAttendance);

        // Then
        assertNotNull(result.getAttendanceTime());
        verify(attendanceRepository).save(any(Attendance.class));
    }

    @Test
    void recordAttendance_WithNullMethod_SetsDefaultMethod() {
        // Given
        testAttendance.setMethod(null);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(userRepository.findByBguUsernameIgnoreCase("student1")).thenReturn(Optional.of(testStudent));
        when(attendanceRepository.existsBySessionIdAndStudentUsernameIgnoreCase(1L, "student1")).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
            Attendance a = invocation.getArgument(0);
            a.setAttendanceId(1L);
            return a;
        });

        // When
        Attendance result = attendanceService.recordAttendance(testAttendance);

        // Then
        assertEquals(Attendance.AttendanceMethod.QR_SCAN, result.getMethod());
    }

    @Test
    void recordAttendance_WithWrongSessionIdAfterSave_ThrowsException() {
        // Given
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(userRepository.findByBguUsernameIgnoreCase("student1")).thenReturn(Optional.of(testStudent));
        when(attendanceRepository.existsBySessionIdAndStudentUsernameIgnoreCase(1L, "student1")).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(invocation -> {
            Attendance a = invocation.getArgument(0);
            a.setAttendanceId(1L);
            a.setSessionId(999L); // Wrong session ID
            return a;
        });

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            attendanceService.recordAttendance(testAttendance);
        });
        verify(databaseLoggerService).logError(eq("ATTENDANCE_SAVED_WRONG_SESSION_ID"), anyString(), isNull(), 
            eq("student1"), anyString());
    }

    // ==================== getAttendanceBySession Tests ====================

    @Test
    void getAttendanceBySession_WithValidSessionId_ReturnsAttendanceList() {
        // Given
        Attendance attendance1 = new Attendance();
        attendance1.setAttendanceId(1L);
        attendance1.setSessionId(1L);
        attendance1.setStudentUsername("student1");

        Attendance attendance2 = new Attendance();
        attendance2.setAttendanceId(2L);
        attendance2.setSessionId(1L);
        attendance2.setStudentUsername("student2");

        when(attendanceRepository.findBySessionId(1L))
                .thenReturn(List.of(attendance1, attendance2));

        // When
        List<Attendance> result = attendanceService.getAttendanceBySession(1L);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getSessionId());
        assertEquals(1L, result.get(1).getSessionId());
    }

    @Test
    void getAttendanceBySession_WithNoAttendance_ReturnsEmptyList() {
        // Given
        when(attendanceRepository.findBySessionId(1L)).thenReturn(Collections.emptyList());

        // When
        List<Attendance> result = attendanceService.getAttendanceBySession(1L);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getAttendanceBySession_WithWrongSessionIdInRecords_LogsErrorAndFilters() {
        // Given
        Attendance correctAttendance = new Attendance();
        correctAttendance.setAttendanceId(1L);
        correctAttendance.setSessionId(1L);
        correctAttendance.setStudentUsername("student1");

        Attendance wrongAttendance = new Attendance();
        wrongAttendance.setAttendanceId(2L);
        wrongAttendance.setSessionId(999L); // Wrong session ID
        wrongAttendance.setStudentUsername("student2");

        when(attendanceRepository.findBySessionId(1L))
                .thenReturn(List.of(correctAttendance, wrongAttendance));

        // When
        List<Attendance> result = attendanceService.getAttendanceBySession(1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size()); // Filtered out wrong one
        assertEquals(1L, result.get(0).getSessionId());
        verify(databaseLoggerService).logError(eq("ATTENDANCE_WRONG_SESSION_ID"), anyString(), isNull(), 
            isNull(), anyString());
    }

    // ==================== getAttendanceByStudent Tests ====================

    @Test
    void getAttendanceByStudent_WithValidUsername_ReturnsAttendanceList() {
        // Given
        Attendance attendance1 = new Attendance();
        attendance1.setAttendanceId(1L);
        attendance1.setSessionId(1L);
        attendance1.setStudentUsername("student1");

        when(attendanceRepository.findByStudentUsername("student1"))
                .thenReturn(List.of(attendance1));

        // When
        List<Attendance> result = attendanceService.getAttendanceByStudent("student1");

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("student1", result.get(0).getStudentUsername());
    }

    // ==================== deleteAttendance Tests ====================

    @Test
    void deleteAttendance_WithValidId_DeletesSuccessfully() {
        // Given
        testAttendance.setAttendanceId(1L);
        when(attendanceRepository.existsById(1L)).thenReturn(true);
        doNothing().when(attendanceRepository).deleteById(1L);

        // When
        attendanceService.deleteAttendance(1L);

        // Then
        verify(attendanceRepository).deleteById(1L);
        verify(databaseLoggerService).logAttendance(anyString(), isNull(), isNull(), isNull());
    }

    @Test
    void deleteAttendance_WithNotFound_ThrowsException() {
        // Given
        when(attendanceRepository.existsById(1L)).thenReturn(false);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            attendanceService.deleteAttendance(1L);
        });
        verify(attendanceRepository, never()).deleteById(any());
    }

    // ==================== getSessionAttendanceStats Tests ====================

    @Test
    void getSessionAttendanceStats_WithValidSessionId_ReturnsStats() {
        // Given
        when(attendanceRepository.countBySessionId(1L)).thenReturn(10L);
        when(attendanceRepository.countByMethod(Attendance.AttendanceMethod.QR_SCAN)).thenReturn(7L);
        when(attendanceRepository.countByMethod(Attendance.AttendanceMethod.MANUAL)).thenReturn(2L);
        when(attendanceRepository.countByMethod(Attendance.AttendanceMethod.PROXY)).thenReturn(1L);

        // When
        AttendanceService.AttendanceStats stats = attendanceService.getSessionAttendanceStats(1L);

        // Then
        assertNotNull(stats);
        assertEquals(10L, stats.getTotalAttendance());
        assertEquals(7L, stats.getQrScanCount());
        assertEquals(2L, stats.getManualCount());
        assertEquals(1L, stats.getProxyCount());
    }
}

