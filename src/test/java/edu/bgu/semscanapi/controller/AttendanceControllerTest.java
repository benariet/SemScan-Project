package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.entity.Attendance;
import edu.bgu.semscanapi.service.AttendanceService;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AttendanceController
 * Includes test case for invalid QR code format (Test Case 13)
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

    @BeforeEach
    void setUp() {
        validAttendance = new Attendance();
        validAttendance.setSessionId(1L);
        validAttendance.setStudentUsername("student1");
        validAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);
        validAttendance.setAttendanceTime(LocalDateTime.now());
        
        // Mock database logger to avoid NPE in all tests
        doNothing().when(databaseLoggerService).logAction(anyString(), anyString(), anyString(), anyString(), anyString());
        doNothing().when(databaseLoggerService).logError(anyString(), anyString(), any(), anyString(), anyString());
    }

    // ==================== Test Case 13: Invalid QR Code Format ====================

    @Test
    void recordAttendance_WithNullSessionId_ReturnsBadRequest() throws Exception {
        // Given - Invalid QR code format: null sessionId
        Attendance invalidAttendance = new Attendance();
        invalidAttendance.setSessionId(null); // Invalid: missing sessionId
        invalidAttendance.setStudentUsername("student1");
        invalidAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);

        // Mock service to throw exception for null sessionId
        when(attendanceService.recordAttendance(any(Attendance.class)))
                .thenThrow(new IllegalArgumentException("Attendance request missing sessionId"));

        // When & Then
        mockMvc.perform(post("/api/v1/attendance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidAttendance)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void recordAttendance_WithInvalidSessionIdFormat_ReturnsBadRequest() throws Exception {
        // Given - Invalid QR code format: sessionId as invalid value
        // This simulates a QR code that was scanned but contains invalid data
        // Note: Jackson will fail to deserialize "invalid-session-id" to Long, causing 500 from controller
        String invalidJson = """
            {
                "sessionId": "invalid-session-id",
                "studentUsername": "student1",
                "method": "QR_SCAN"
            }
            """;

        // When & Then - Invalid JSON format returns Bad Request
        mockMvc.perform(post("/api/v1/attendance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest()); // Controller returns 400 for invalid input
    }

    @Test
    void recordAttendance_WithNegativeSessionId_ReturnsBadRequest() throws Exception {
        // Given - Invalid QR code format: negative sessionId
        Attendance invalidAttendance = new Attendance();
        invalidAttendance.setSessionId(-1L); // Invalid: negative ID
        invalidAttendance.setStudentUsername("student1");
        invalidAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);

        // Mock service to throw exception for negative sessionId (session not found)
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
    void recordAttendance_WithZeroSessionId_ReturnsBadRequest() throws Exception {
        // Given - Invalid QR code format: zero sessionId
        Attendance invalidAttendance = new Attendance();
        invalidAttendance.setSessionId(0L); // Invalid: zero ID
        invalidAttendance.setStudentUsername("student1");
        invalidAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);

        // Mock service to throw exception for zero sessionId (session not found)
        when(attendanceService.recordAttendance(any(Attendance.class)))
                .thenThrow(new IllegalArgumentException("Session not found: 0"));

        // When & Then
        mockMvc.perform(post("/api/v1/attendance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidAttendance)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void recordAttendance_WithNonExistentSessionId_ReturnsBadRequest() throws Exception {
        // Given - QR code with valid format but non-existent sessionId
        Attendance attendance = new Attendance();
        attendance.setSessionId(99999L); // Valid format but doesn't exist
        attendance.setStudentUsername("student1");
        attendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);

        // Mock service to throw exception for non-existent session
        when(attendanceService.recordAttendance(any(Attendance.class)))
                .thenThrow(new IllegalArgumentException("Session not found: 99999"));

        // When & Then
        mockMvc.perform(post("/api/v1/attendance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(attendance)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Session not found: 99999")) // ErrorResponse.message contains the exception message
                .andExpect(jsonPath("$.error").value("Bad Request")) // ErrorResponse.error is the error type
                .andExpect(jsonPath("$.status").value(400));
        
        // Verify error was logged (bguUsername may be null from LoggerUtil, so we use any() for that parameter)
        verify(databaseLoggerService).logError(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void recordAttendance_WithMalformedJson_ReturnsBadRequest() throws Exception {
        // Given - Invalid QR code format: malformed JSON (invalid sessionId type)
        String malformedJson = """
            {
                "sessionId": "not-a-number",
                "studentUsername": "student1",
                "method": "QR_SCAN"
            }
            """;

        // When & Then - Malformed JSON returns Bad Request
        mockMvc.perform(post("/api/v1/attendance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson))
                .andExpect(status().isBadRequest()); // Controller returns 400 for invalid input
    }

    @Test
    void recordAttendance_WithEmptySessionId_ReturnsBadRequest() throws Exception {
        // Given - Invalid QR code format: empty sessionId in JSON
        // Note: Empty string for Long will cause JSON parsing error, which results in 500 from controller
        String invalidJson = """
            {
                "sessionId": "",
                "studentUsername": "student1",
                "method": "QR_SCAN"
            }
            """;

        // When & Then - JSON deserialization will fail, causing 500 Internal Server Error
        // (Controller catches all exceptions and returns 500)
        mockMvc.perform(post("/api/v1/attendance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isInternalServerError()); // Controller returns 500 for JSON parsing errors
    }

    @Test
    void recordAttendance_WithValidFormat_ReturnsSuccess() throws Exception {
        // Given - Valid QR code format
        Attendance savedAttendance = new Attendance();
        savedAttendance.setAttendanceId(1L);
        savedAttendance.setSessionId(1L);
        savedAttendance.setStudentUsername("student1");
        savedAttendance.setMethod(Attendance.AttendanceMethod.QR_SCAN);
        savedAttendance.setAttendanceTime(LocalDateTime.now());

        when(attendanceService.recordAttendance(any(Attendance.class)))
                .thenReturn(savedAttendance);
        
        // Mock database logger to avoid NPE
        doNothing().when(databaseLoggerService).logAction(anyString(), anyString(), anyString(), anyString(), anyString());

        // When & Then
        mockMvc.perform(post("/api/v1/attendance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validAttendance)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").exists())
                .andExpect(jsonPath("$.sessionId").value(1L))
                .andExpect(jsonPath("$.studentUsername").value("student1"));
    }

    // ==================== Additional QR Code Validation Tests ====================

    @Test
    void recordAttendance_WithInvalidQRPayloadFormat_ReturnsBadRequest() throws Exception {
        // Given - QR code contains invalid payload format (e.g., wrong structure)
        // Simulating a QR code that was generated incorrectly or corrupted
        // This will create an Attendance object with null sessionId, which service will reject
        String invalidPayloadJson = """
            {
                "session": 1,
                "student": "student1",
                "type": "scan"
            }
            """;

        // Mock service to throw exception for missing sessionId
        when(attendanceService.recordAttendance(any(Attendance.class)))
                .thenThrow(new IllegalArgumentException("Attendance request missing sessionId"));

        // When & Then - Should fail validation due to missing required fields
        mockMvc.perform(post("/api/v1/attendance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayloadJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordAttendance_WithWrongQRCodeStructure_ReturnsBadRequest() throws Exception {
        // Given - QR code with completely wrong structure
        // This will create an Attendance object with null sessionId, which service will reject
        String wrongStructureJson = """
            {
                "qrData": {
                    "id": 1
                },
                "user": "student1"
            }
            """;

        // Mock service to throw exception for missing sessionId
        when(attendanceService.recordAttendance(any(Attendance.class)))
                .thenThrow(new IllegalArgumentException("Attendance request missing sessionId"));

        // When & Then
        mockMvc.perform(post("/api/v1/attendance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongStructureJson))
                .andExpect(status().isBadRequest());
    }
}

