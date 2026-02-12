package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.SessionService;
import edu.bgu.semscanapi.config.GlobalConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for StudentController
 * Includes Test Case 12: Backend Returns Empty Sessions List (for manual attendance)
 */
@WebMvcTest(controllers = StudentController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private DatabaseLoggerService databaseLoggerService;

    @MockBean
    private GlobalConfig globalConfig;

    private Session testSession;

    @BeforeEach
    void setUp() {
        testSession = new Session();
        testSession.setSessionId(1L);
        testSession.setSeminarId(1L);
        testSession.setStartTime(LocalDateTime.now());
        testSession.setStatus(Session.SessionStatus.OPEN);
        testSession.setLocation("Building 37 Room 201");
    }

    // ==================== Test Case 12: Backend Returns Empty Sessions List (Manual Attendance) ====================

    @Test
    void getOpenSessionsForStudent_WhenBackendReturnsEmptyList_ReturnsEmptyArray() throws Exception {
        // Given - Backend has open sessions but returns empty list (bug scenario)
        // This is the scenario for Test Case 12: Participant opens "Manual Attendance Request"
        when(sessionService.getOpenSessions()).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/v1/student/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sessions").isArray())
                .andExpect(jsonPath("$.sessions").isEmpty())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.message").value("Open sessions available for manual attendance"));

        // Verify service was called
        verify(sessionService).getOpenSessions();
    }

    @Test
    void getOpenSessionsForStudent_WhenBackendReturnsEmptyList_ShowsEmptyState() throws Exception {
        // Given - Backend returns empty list
        when(sessionService.getOpenSessions()).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/v1/student/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isEmpty())
                .andExpect(jsonPath("$.totalCount").value(0));
        
        // Frontend should show: "No open sessions available" based on totalCount = 0
        // The response structure allows frontend to detect empty state
    }

    @Test
    void getOpenSessionsForStudent_WhenBackendReturnsEmptyList_AllowsUserToRefresh() throws Exception {
        // Given - Backend returns empty list
        when(sessionService.getOpenSessions()).thenReturn(Collections.emptyList());

        // When & Then - Should return 200 OK (not an error) so user can refresh
        mockMvc.perform(get("/api/v1/student/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // Success status allows refresh
                .andExpect(jsonPath("$.sessions").isArray())
                .andExpect(jsonPath("$.sessions").isEmpty());

        // User can refresh - endpoint is still accessible
        mockMvc.perform(get("/api/v1/student/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getOpenSessionsForStudent_WhenBackendReturnsEmptyList_LogsForDebugging() throws Exception {
        // Given - Backend returns empty list
        when(sessionService.getOpenSessions()).thenReturn(Collections.emptyList());

        // When
        mockMvc.perform(get("/api/v1/student/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Then - Verify service was called (which triggers logging in controller)
        // The controller logs "Retrieved 0 relevant open sessions for student" for debugging
        verify(sessionService).getOpenSessions();
        // Note: Actual logging verification would require a logging framework test setup
    }

    @Test
    void getOpenSessionsForStudent_WithValidSessions_ReturnsSessionsList() throws Exception {
        // Given - Backend returns valid sessions (positive test)
        Session session1 = new Session();
        session1.setSessionId(1L);
        session1.setSeminarId(1L);
        session1.setStatus(Session.SessionStatus.OPEN);
        session1.setStartTime(LocalDateTime.now());
        session1.setLocation("Building 37 Room 201");

        when(sessionService.getOpenSessions()).thenReturn(List.of(session1));

        // When & Then
        mockMvc.perform(get("/api/v1/student/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray())
                .andExpect(jsonPath("$.sessions[0]").exists())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.sessions[0].sessionId").value(1L));
    }

    @Test
    void getOpenSessionsForStudent_WhenServiceThrowsException_ReturnsError() throws Exception {
        // Given - Service throws exception
        when(sessionService.getOpenSessions())
                .thenThrow(new RuntimeException("Database connection error"));

        // When & Then
        mockMvc.perform(get("/api/v1/student/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getSessionsForStudent_WithValidUsername_ReturnsSessions() throws Exception {
        // Given
        when(sessionService.getOpenSessions()).thenReturn(List.of(testSession));

        // When & Then
        mockMvc.perform(get("/api/v1/student/sessions/student/student1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray())
                .andExpect(jsonPath("$.studentUsername").value("student1"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void getSessionsForStudent_WhenBackendReturnsEmptyList_ReturnsEmptyState() throws Exception {
        // Given - Backend returns empty list
        when(sessionService.getOpenSessions()).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/v1/student/sessions/student/student1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isEmpty())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.studentUsername").value("student1"));
    }
}

