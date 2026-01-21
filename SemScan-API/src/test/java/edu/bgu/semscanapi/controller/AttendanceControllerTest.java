package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.service.AttendanceService;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive unit tests for AttendanceController
 * Covers all endpoints with success and error scenarios
 */
@WebMvcTest(controllers = AttendanceController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class AttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttendanceService attendanceService;

    @MockBean
    private DatabaseLoggerService databaseLoggerService;

    @Autowired
    private ObjectMapper objectMapper;

    private Attendance validAttendance;
    private Attendance savedAttendance;

    @BeforeEach
    void setUp() {
        validAttendance = new Attendance();
        validAttendance.setSessionId(1L);
        validAttendance.setStudentUsername("student1");
        validAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);
        validAttendance.setAttendanceTime(LocalDateTime.now());

        savedAttendance = new Attendance();
        savedAttendance.setAttendanceId(1L);
        savedAttendance.setSessionId(1L);
        savedAttendance.setStudentUsername("student1");
        savedAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);
        savedAttendance.setAttendanceTime(LocalDateTime.now());

        // Mock database logger to avoid NPE in all tests
        doNothing().when(databaseLoggerService).logAction(anyString(), anyString(), anyString(), any(), any());
        doNothing().when(databaseLoggerService).logError(anyString(), anyString(), any(), any(), any());
        doNothing().when(databaseLoggerService).logAttendance(anyString(), any(), any(), any());
        doNothing().when(databaseLoggerService).logBusinessEvent(anyString(), anyString(), any());
    }

    // ==================== POST /api/v1/attendance - Record Attendance ====================

    @Nested
    @DisplayName("POST /api/v1/attendance - Record Attendance via QR Scan")
    class RecordAttendanceTests {

        @Test
        @DisplayName("Successfully records attendance with valid data")
        void recordAttendance_WithValidData_ReturnsCreated() throws Exception {
            // Given
            when(attendanceService.recordAttendance(any(Attendance.class))).thenReturn(savedAttendance);

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAttendance)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.attendanceId").value(1))
                    .andExpect(jsonPath("$.sessionId").value(1))
                    .andExpect(jsonPath("$.studentUsername").value("student1"))
                    .andExpect(jsonPath("$.method").value("QR_SCAN"));

            verify(attendanceService).recordAttendance(any(Attendance.class));
        }

        @Test
        @DisplayName("Returns 400 when sessionId is null")
        void recordAttendance_WithNullSessionId_ReturnsBadRequest() throws Exception {
            // Given
            Attendance invalidAttendance = new Attendance();
            invalidAttendance.setSessionId(null);
            invalidAttendance.setStudentUsername("student1");
            invalidAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);

            when(attendanceService.recordAttendance(any(Attendance.class)))
                    .thenThrow(new IllegalArgumentException("Attendance request missing sessionId"));

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidAttendance)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Returns 400 when session is not found")
        void recordAttendance_WithNonExistentSession_ReturnsBadRequest() throws Exception {
            // Given
            validAttendance.setSessionId(99999L);

            when(attendanceService.recordAttendance(any(Attendance.class)))
                    .thenThrow(new IllegalArgumentException("Session not found: 99999"));

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAttendance)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Session not found: 99999"))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Returns 400 when session is not open (CLOSED)")
        void recordAttendance_WithClosedSession_ReturnsBadRequest() throws Exception {
            // Given
            when(attendanceService.recordAttendance(any(Attendance.class)))
                    .thenThrow(new IllegalArgumentException("Session is not open: 1 (status: CLOSED)"));

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAttendance)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Session is not open: 1 (status: CLOSED)"))
                    .andExpect(jsonPath("$.error").value("Bad Request"));
        }

        @Test
        @DisplayName("Returns 400 when attendance window has closed")
        void recordAttendance_WithClosedAttendanceWindow_ReturnsBadRequest() throws Exception {
            // Given
            when(attendanceService.recordAttendance(any(Attendance.class)))
                    .thenThrow(new IllegalArgumentException("Attendance window has closed. You can no longer scan for this session."));

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAttendance)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Attendance window has closed. You can no longer scan for this session."));
        }

        @Test
        @DisplayName("Returns 400 when student already attended (duplicate)")
        void recordAttendance_WithDuplicateAttendance_ReturnsBadRequest() throws Exception {
            // Given
            when(attendanceService.recordAttendance(any(Attendance.class)))
                    .thenThrow(new IllegalArgumentException("Student already attended this session"));

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAttendance)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Student already attended this session"))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Returns 400 when student not found in database")
        void recordAttendance_WithNonExistentStudent_ReturnsBadRequest() throws Exception {
            // Given
            when(attendanceService.recordAttendance(any(Attendance.class)))
                    .thenThrow(new IllegalArgumentException("Student not found: unknownuser"));

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAttendance)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Student not found: unknownuser"));
        }

        @Test
        @DisplayName("Returns 400 with negative sessionId")
        void recordAttendance_WithNegativeSessionId_ReturnsBadRequest() throws Exception {
            // Given
            Attendance invalidAttendance = new Attendance();
            invalidAttendance.setSessionId(-1L);
            invalidAttendance.setStudentUsername("student1");
            invalidAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);

            when(attendanceService.recordAttendance(any(Attendance.class)))
                    .thenThrow(new IllegalArgumentException("Session not found: -1"));

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidAttendance)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("Returns 400 with invalid JSON format for sessionId")
        void recordAttendance_WithInvalidSessionIdFormat_ReturnsBadRequest() throws Exception {
            // Given - sessionId as string instead of number
            String invalidJson = """
                {
                    "sessionId": "not-a-number",
                    "studentUsername": "student1",
                    "method": "QR_SCAN"
                }
                """;

            // When & Then - JSON deserialization fails
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 500 when unexpected error occurs")
        void recordAttendance_WithUnexpectedError_ReturnsInternalServerError() throws Exception {
            // Given
            when(attendanceService.recordAttendance(any(Attendance.class)))
                    .thenThrow(new RuntimeException("Database connection error"));

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAttendance)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.status").value(500));
        }

        @Test
        @DisplayName("Successfully records MANUAL attendance method")
        void recordAttendance_WithManualMethod_ReturnsCreated() throws Exception {
            // Given
            Attendance manualAttendance = new Attendance();
            manualAttendance.setSessionId(1L);
            manualAttendance.setStudentUsername("student1");
            manualAttendance.setMethod(Attendance.AttendanceMethod.MANUAL);
            manualAttendance.setManualReason("Phone battery died");

            Attendance savedManualAttendance = new Attendance();
            savedManualAttendance.setAttendanceId(2L);
            savedManualAttendance.setSessionId(1L);
            savedManualAttendance.setStudentUsername("student1");
            savedManualAttendance.setMethod(Attendance.AttendanceMethod.MANUAL);
            savedManualAttendance.setManualReason("Phone battery died");
            savedManualAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);

            when(attendanceService.recordAttendance(any(Attendance.class))).thenReturn(savedManualAttendance);

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(manualAttendance)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.method").value("MANUAL"))
                    .andExpect(jsonPath("$.requestStatus").value("PENDING_APPROVAL"));
        }
    }

    // ==================== GET /api/v1/attendance/session/{sessionId} ====================

    @Nested
    @DisplayName("GET /api/v1/attendance/session/{sessionId} - Get Session Attendance")
    class GetAttendanceBySessionTests {

        @Test
        @DisplayName("Returns attendance list for valid session")
        void getAttendanceBySession_ReturnsAttendanceList() throws Exception {
            // Given
            Attendance attendance1 = createAttendance(1L, 1L, "student1", Attendance.AttendanceMethod.QR_SCAN);
            Attendance attendance2 = createAttendance(2L, 1L, "student2", Attendance.AttendanceMethod.QR_SCAN);
            List<Attendance> attendanceList = Arrays.asList(attendance1, attendance2);

            when(attendanceService.getAttendanceBySession(1L)).thenReturn(attendanceList);

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/session/1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].studentUsername").value("student1"))
                    .andExpect(jsonPath("$[1].studentUsername").value("student2"));

            verify(attendanceService).getAttendanceBySession(1L);
        }

        @Test
        @DisplayName("Returns empty list when no attendance records")
        void getAttendanceBySession_ReturnsEmptyList() throws Exception {
            // Given
            when(attendanceService.getAttendanceBySession(1L)).thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/session/1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("Returns 500 when service throws exception")
        void getAttendanceBySession_WithError_ReturnsInternalServerError() throws Exception {
            // Given
            when(attendanceService.getAttendanceBySession(1L))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/session/1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.status").value(500));
        }
    }

    // ==================== GET /api/v1/attendance/{attendanceId} ====================

    @Nested
    @DisplayName("GET /api/v1/attendance/{attendanceId} - Get Attendance by ID")
    class GetAttendanceByIdTests {

        @Test
        @DisplayName("Returns attendance when found")
        void getAttendanceById_WhenFound_ReturnsAttendance() throws Exception {
            // Given
            when(attendanceService.getAttendanceById(1L)).thenReturn(Optional.of(savedAttendance));

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.attendanceId").value(1))
                    .andExpect(jsonPath("$.sessionId").value(1))
                    .andExpect(jsonPath("$.studentUsername").value("student1"));

            verify(attendanceService).getAttendanceById(1L);
        }

        @Test
        @DisplayName("Returns 404 when attendance not found")
        void getAttendanceById_WhenNotFound_ReturnsNotFound() throws Exception {
            // Given
            when(attendanceService.getAttendanceById(999L)).thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/999")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Attendance not found with ID: 999"));
        }

        @Test
        @DisplayName("Returns 500 when unexpected error occurs")
        void getAttendanceById_WithError_ReturnsInternalServerError() throws Exception {
            // Given
            when(attendanceService.getAttendanceById(1L))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Internal Server Error"));
        }
    }

    // ==================== GET /api/v1/attendance/student/{studentUsername} ====================

    @Nested
    @DisplayName("GET /api/v1/attendance/student/{studentUsername} - Get Attendance by Student")
    class GetAttendanceByStudentTests {

        @Test
        @DisplayName("Returns attendance list for student")
        void getAttendanceByStudent_ReturnsAttendanceList() throws Exception {
            // Given
            Attendance attendance1 = createAttendance(1L, 1L, "student1", Attendance.AttendanceMethod.QR_SCAN);
            Attendance attendance2 = createAttendance(2L, 2L, "student1", Attendance.AttendanceMethod.QR_SCAN);
            List<Attendance> attendanceList = Arrays.asList(attendance1, attendance2);

            when(attendanceService.getAttendanceByStudent("student1")).thenReturn(attendanceList);

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/student/student1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2));

            verify(attendanceService).getAttendanceByStudent("student1");
        }

        @Test
        @DisplayName("Returns empty list when student has no attendance")
        void getAttendanceByStudent_ReturnsEmptyList() throws Exception {
            // Given
            when(attendanceService.getAttendanceByStudent("newstudent")).thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/student/newstudent")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    // ==================== GET /api/v1/attendance/check ====================

    @Nested
    @DisplayName("GET /api/v1/attendance/check - Check Student Attendance")
    class CheckStudentAttendanceTests {

        @Test
        @DisplayName("Returns true when student has attended")
        void checkAttendance_WhenAttended_ReturnsTrue() throws Exception {
            // Given
            when(attendanceService.hasStudentAttended(1L, "student1")).thenReturn(true);

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/check")
                    .param("sessionId", "1")
                    .param("studentUsername", "student1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));

            verify(attendanceService).hasStudentAttended(1L, "student1");
        }

        @Test
        @DisplayName("Returns false when student has not attended")
        void checkAttendance_WhenNotAttended_ReturnsFalse() throws Exception {
            // Given
            when(attendanceService.hasStudentAttended(1L, "student1")).thenReturn(false);

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/check")
                    .param("sessionId", "1")
                    .param("studentUsername", "student1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }

        @Test
        @DisplayName("Returns 500 when error occurs")
        void checkAttendance_WithError_ReturnsInternalServerError() throws Exception {
            // Given
            when(attendanceService.hasStudentAttended(anyLong(), anyString()))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/check")
                    .param("sessionId", "1")
                    .param("studentUsername", "student1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());
        }
    }

    // ==================== GET /api/v1/attendance/method/{method} ====================

    @Nested
    @DisplayName("GET /api/v1/attendance/method/{method} - Get Attendance by Method")
    class GetAttendanceByMethodTests {

        @Test
        @DisplayName("Returns attendance list for QR_SCAN method")
        void getAttendanceByMethod_QrScan_ReturnsAttendanceList() throws Exception {
            // Given
            List<Attendance> attendanceList = Arrays.asList(
                createAttendance(1L, 1L, "student1", Attendance.AttendanceMethod.QR_SCAN),
                createAttendance(2L, 2L, "student2", Attendance.AttendanceMethod.QR_SCAN)
            );

            when(attendanceService.getAttendanceByMethod(Attendance.AttendanceMethod.QR_SCAN))
                    .thenReturn(attendanceList);

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/method/QR_SCAN")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].method").value("QR_SCAN"));
        }

        @Test
        @DisplayName("Returns attendance list for MANUAL method")
        void getAttendanceByMethod_Manual_ReturnsAttendanceList() throws Exception {
            // Given
            List<Attendance> attendanceList = Arrays.asList(
                createAttendance(3L, 1L, "student3", Attendance.AttendanceMethod.MANUAL)
            );

            when(attendanceService.getAttendanceByMethod(Attendance.AttendanceMethod.MANUAL))
                    .thenReturn(attendanceList);

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/method/MANUAL")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].method").value("MANUAL"));
        }
    }

    // ==================== GET /api/v1/attendance/session/{sessionId}/stats ====================

    @Nested
    @DisplayName("GET /api/v1/attendance/session/{sessionId}/stats - Get Session Stats")
    class GetSessionStatsTests {

        @Test
        @DisplayName("Returns attendance statistics for session")
        void getSessionStats_ReturnsStats() throws Exception {
            // Given
            AttendanceService.AttendanceStats stats = new AttendanceService.AttendanceStats(10, 7, 2, 1);
            when(attendanceService.getSessionAttendanceStats(1L)).thenReturn(stats);

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/session/1/stats")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalAttendance").value(10))
                    .andExpect(jsonPath("$.qrScanCount").value(7))
                    .andExpect(jsonPath("$.manualCount").value(2))
                    .andExpect(jsonPath("$.proxyCount").value(1));

            verify(attendanceService).getSessionAttendanceStats(1L);
        }

        @Test
        @DisplayName("Returns 500 when error occurs")
        void getSessionStats_WithError_ReturnsInternalServerError() throws Exception {
            // Given
            when(attendanceService.getSessionAttendanceStats(1L))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/session/1/stats")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Internal Server Error"));
        }
    }

    // ==================== DELETE /api/v1/attendance/{attendanceId} ====================

    @Nested
    @DisplayName("DELETE /api/v1/attendance/{attendanceId} - Delete Attendance")
    class DeleteAttendanceTests {

        @Test
        @DisplayName("Successfully deletes attendance record")
        void deleteAttendance_Success_ReturnsNoContent() throws Exception {
            // Given
            doNothing().when(attendanceService).deleteAttendance(1L);

            // When & Then
            mockMvc.perform(delete("/api/v1/attendance/1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(attendanceService).deleteAttendance(1L);
        }

        @Test
        @DisplayName("Returns 404 when attendance not found for deletion")
        void deleteAttendance_NotFound_ReturnsNotFound() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Attendance record not found: 999"))
                    .when(attendanceService).deleteAttendance(999L);

            // When & Then
            mockMvc.perform(delete("/api/v1/attendance/999")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Attendance record not found: 999"));
        }

        @Test
        @DisplayName("Returns 500 when unexpected error occurs")
        void deleteAttendance_WithError_ReturnsInternalServerError() throws Exception {
            // Given
            doThrow(new RuntimeException("Database error"))
                    .when(attendanceService).deleteAttendance(1L);

            // When & Then
            mockMvc.perform(delete("/api/v1/attendance/1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Internal Server Error"));
        }
    }

    // ==================== GET /api/v1/attendance?sessionId= ====================

    @Nested
    @DisplayName("GET /api/v1/attendance?sessionId= - Get Attendance by Session Query")
    class GetAttendanceBySessionQueryTests {

        @Test
        @DisplayName("Returns attendance list for session query parameter")
        void getAttendanceBySessionQuery_ReturnsAttendanceList() throws Exception {
            // Given
            List<Attendance> attendanceList = Arrays.asList(
                createAttendance(1L, 1L, "student1", Attendance.AttendanceMethod.QR_SCAN),
                createAttendance(2L, 1L, "student2", Attendance.AttendanceMethod.QR_SCAN)
            );

            when(attendanceService.getAttendanceBySession(1L)).thenReturn(attendanceList);

            // When & Then
            mockMvc.perform(get("/api/v1/attendance")
                    .param("sessionId", "1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    // ==================== GET /api/v1/attendance/pending-requests ====================

    @Nested
    @DisplayName("GET /api/v1/attendance/pending-requests - Get Pending Manual Requests")
    class GetPendingRequestsTests {

        @Test
        @DisplayName("Returns pending requests for session")
        void getPendingRequests_ReturnsPendingList() throws Exception {
            // Given
            Attendance pendingAttendance = createAttendance(1L, 1L, "student1", Attendance.AttendanceMethod.MANUAL_REQUEST);
            pendingAttendance.setRequestStatus(Attendance.RequestStatus.PENDING_APPROVAL);

            Attendance confirmedAttendance = createAttendance(2L, 1L, "student2", Attendance.AttendanceMethod.QR_SCAN);
            confirmedAttendance.setRequestStatus(Attendance.RequestStatus.CONFIRMED);

            when(attendanceService.getAttendanceBySession(1L))
                    .thenReturn(Arrays.asList(pendingAttendance, confirmedAttendance));

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/pending-requests")
                    .param("sessionId", "1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].requestStatus").value("PENDING_APPROVAL"));
        }

        @Test
        @DisplayName("Returns empty list when no pending requests")
        void getPendingRequests_ReturnsEmptyList() throws Exception {
            // Given
            Attendance confirmedAttendance = createAttendance(1L, 1L, "student1", Attendance.AttendanceMethod.QR_SCAN);
            confirmedAttendance.setRequestStatus(Attendance.RequestStatus.CONFIRMED);

            when(attendanceService.getAttendanceBySession(1L))
                    .thenReturn(Arrays.asList(confirmedAttendance));

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/pending-requests")
                    .param("sessionId", "1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    // ==================== GET /api/v1/attendance/date-range ====================

    @Nested
    @DisplayName("GET /api/v1/attendance/date-range - Get Attendance in Date Range")
    class GetAttendanceDateRangeTests {

        @Test
        @DisplayName("Returns attendance list for date range")
        void getAttendanceBetweenDates_ReturnsAttendanceList() throws Exception {
            // Given
            List<Attendance> attendanceList = Arrays.asList(
                createAttendance(1L, 1L, "student1", Attendance.AttendanceMethod.QR_SCAN),
                createAttendance(2L, 2L, "student2", Attendance.AttendanceMethod.QR_SCAN)
            );

            when(attendanceService.getAttendanceBetweenDates(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(attendanceList);

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/date-range")
                    .param("startDate", "2024-01-01T00:00:00")
                    .param("endDate", "2024-01-31T23:59:59")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("Returns 500 when error occurs")
        void getAttendanceBetweenDates_WithError_ReturnsInternalServerError() throws Exception {
            // Given
            when(attendanceService.getAttendanceBetweenDates(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/date-range")
                    .param("startDate", "2024-01-01T00:00:00")
                    .param("endDate", "2024-01-31T23:59:59")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());
        }
    }

    // ==================== GET /api/v1/attendance/export/csv ====================

    @Nested
    @DisplayName("GET /api/v1/attendance/export/csv - Export CSV")
    class ExportCsvTests {

        @Test
        @DisplayName("Successfully exports CSV for session")
        void exportCsv_Success_ReturnsCsvFile() throws Exception {
            // Given
            List<Attendance> attendanceList = Arrays.asList(
                createAttendance(1L, 1L, "student1", Attendance.AttendanceMethod.QR_SCAN),
                createAttendance(2L, 1L, "student2", Attendance.AttendanceMethod.QR_SCAN)
            );

            when(attendanceService.getAttendanceBySession(1L)).thenReturn(attendanceList);

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/export/csv")
                    .param("sessionId", "1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", "form-data; name=\"attachment\"; filename=\"attendance_1.csv\""));
        }

        @Test
        @DisplayName("Returns 500 when export fails")
        void exportCsv_WithError_ReturnsInternalServerError() throws Exception {
            // Given
            when(attendanceService.getAttendanceBySession(1L))
                    .thenThrow(new RuntimeException("Export error"));

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/export/csv")
                    .param("sessionId", "1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());
        }
    }

    // ==================== GET /api/v1/attendance/export/xlsx ====================

    @Nested
    @DisplayName("GET /api/v1/attendance/export/xlsx - Export XLSX")
    class ExportXlsxTests {

        @Test
        @DisplayName("Successfully exports XLSX for session")
        void exportXlsx_Success_ReturnsXlsxFile() throws Exception {
            // Given
            List<Attendance> attendanceList = Arrays.asList(
                createAttendance(1L, 1L, "student1", Attendance.AttendanceMethod.QR_SCAN)
            );

            when(attendanceService.getAttendanceBySession(1L)).thenReturn(attendanceList);

            // When & Then
            mockMvc.perform(get("/api/v1/attendance/export/xlsx")
                    .param("sessionId", "1")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", "form-data; name=\"attachment\"; filename=\"attendance_1.xlsx\""));
        }
    }

    // ==================== Invalid QR Code Format Tests (Test Case 13) ====================

    @Nested
    @DisplayName("Invalid QR Code Format Tests")
    class InvalidQrCodeFormatTests {

        @Test
        @DisplayName("Handles malformed JSON gracefully")
        void recordAttendance_WithMalformedJson_ReturnsBadRequest() throws Exception {
            // Given - Invalid JSON structure
            String malformedJson = """
                {
                    "sessionId": "not-a-number",
                    "studentUsername": "student1",
                    "method": "QR_SCAN"
                }
                """;

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(malformedJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Handles wrong field names in QR payload")
        void recordAttendance_WithWrongFieldNames_ReturnsBadRequest() throws Exception {
            // Given - Wrong field names (session instead of sessionId)
            String wrongFieldsJson = """
                {
                    "session": 1,
                    "student": "student1",
                    "type": "scan"
                }
                """;

            when(attendanceService.recordAttendance(any(Attendance.class)))
                    .thenThrow(new IllegalArgumentException("Attendance request missing sessionId"));

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(wrongFieldsJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Handles zero sessionId")
        void recordAttendance_WithZeroSessionId_ReturnsBadRequest() throws Exception {
            // Given
            Attendance invalidAttendance = new Attendance();
            invalidAttendance.setSessionId(0L);
            invalidAttendance.setStudentUsername("student1");
            invalidAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);

            when(attendanceService.recordAttendance(any(Attendance.class)))
                    .thenThrow(new IllegalArgumentException("Session not found: 0"));

            // When & Then
            mockMvc.perform(post("/api/v1/attendance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidAttendance)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    // ==================== Helper Methods ====================

    private Attendance createAttendance(Long id, Long sessionId, String studentUsername, Attendance.AttendanceMethod method) {
        Attendance attendance = new Attendance();
        attendance.setAttendanceId(id);
        attendance.setSessionId(sessionId);
        attendance.setStudentUsername(studentUsername);
        attendance.setMethod(method);
        attendance.setAttendanceTime(LocalDateTime.now());
        return attendance;
    }
}
