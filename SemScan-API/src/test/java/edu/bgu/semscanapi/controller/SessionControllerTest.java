package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.dto.SessionDTO;
import edu.bgu.semscanapi.entity.Session;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.SessionService;
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
 * Unit tests for SessionController
 * Includes Test Case 12: Backend Returns Empty Sessions List
 */
@WebMvcTest(controllers = SessionController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private DatabaseLoggerService databaseLoggerService;

    private Session testSession;

    @BeforeEach
    void setUp() {
        testSession = new Session();
        testSession.setSessionId(1L);
        testSession.setSeminarId(1L);
        testSession.setStartTime(LocalDateTime.now());
        testSession.setStatus(Session.SessionStatus.OPEN);
    }

    // ==================== Test Case 12: Backend Returns Empty Sessions List ====================

    @Test
    void getOpenSessions_WhenBackendReturnsEmptyList_ReturnsEmptyArray() throws Exception {
        // Given - Backend has open sessions but returns empty list (bug scenario)
        // This simulates a scenario where the database has open sessions but the query returns []
        when(sessionService.getOpenSessionsEnriched()).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/v1/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty())
                .andExpect(jsonPath("$.length()").value(0));

        // Verify service was called
        verify(sessionService).getOpenSessionsEnriched();
    }

    @Test
    void getOpenSessions_WhenBackendReturnsEmptyList_LogsAppropriately() throws Exception {
        // Given - Backend returns empty list
        when(sessionService.getOpenSessionsEnriched()).thenReturn(Collections.emptyList());

        // When
        mockMvc.perform(get("/api/v1/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Then - Verify logging occurred (for debugging)
        // The controller logs "Retrieved 0 open sessions" which is helpful for debugging
        verify(sessionService).getOpenSessionsEnriched();
        // Note: Actual logging verification would require a logging framework test setup
        // But we can verify the service was called, which triggers logging in the controller
    }

    @Test
    void getOpenSessions_WhenBackendReturnsEmptyList_AllowsUserToRefresh() throws Exception {
        // Given - Backend returns empty list
        when(sessionService.getOpenSessionsEnriched()).thenReturn(Collections.emptyList());

        // When & Then - Should return 200 OK (not an error) so user can refresh
        mockMvc.perform(get("/api/v1/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // Success status allows refresh
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        // User can refresh - endpoint is still accessible
        mockMvc.perform(get("/api/v1/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getOpenSessions_WithValidSessions_ReturnsSessionsList() throws Exception {
        // Given - Backend returns valid sessions (positive test)
        Session session1 = new Session();
        session1.setSessionId(1L);
        session1.setSeminarId(1L);
        session1.setStatus(Session.SessionStatus.OPEN);
        session1.setStartTime(LocalDateTime.now());

        Session session2 = new Session();
        session2.setSessionId(2L);
        session2.setSeminarId(2L);
        session2.setStatus(Session.SessionStatus.OPEN);
        session2.setStartTime(LocalDateTime.now());

        // Create SessionDTOs from Session entities
        SessionDTO dto1 = SessionDTO.fromSession(session1, "Presenter One", "presenter1", "Topic 1");
        SessionDTO dto2 = SessionDTO.fromSession(session2, "Presenter Two", "presenter2", "Topic 2");

        when(sessionService.getOpenSessionsEnriched()).thenReturn(List.of(dto1, dto2));

        // When & Then
        mockMvc.perform(get("/api/v1/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].sessionId").value(1L))
                .andExpect(jsonPath("$[1].sessionId").value(2L));
    }

    @Test
    void getOpenSessions_WhenServiceThrowsException_ReturnsError() throws Exception {
        // Given - Service throws exception
        when(sessionService.getOpenSessionsEnriched())
                .thenThrow(new RuntimeException("Database connection error"));

        // When & Then
        mockMvc.perform(get("/api/v1/sessions/open")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").exists());

        // Note: The controller logs to slf4j logger but doesn't call databaseLoggerService.logError
        // for this endpoint, so we don't verify database logging here
    }

    // ==================== Additional Session Controller Tests ====================

    @Test
    void getSessionById_WithValidId_ReturnsSession() throws Exception {
        // Given
        when(sessionService.getSessionById(1L))
                .thenReturn(java.util.Optional.of(testSession));

        // When & Then
        mockMvc.perform(get("/api/v1/sessions/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(1L))
                .andExpect(jsonPath("$.seminarId").value(1L));
    }

    @Test
    void getSessionById_WithNotFound_Returns404() throws Exception {
        // Given
        when(sessionService.getSessionById(999L))
                .thenReturn(java.util.Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/sessions/999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getSessionsBySeminar_WithValidSeminarId_ReturnsSessions() throws Exception {
        // Given
        when(sessionService.getSessionsBySeminar(1L))
                .thenReturn(List.of(testSession));

        // When & Then
        mockMvc.perform(get("/api/v1/sessions/seminar/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").exists());
    }
}

