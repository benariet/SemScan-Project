package edu.bgu.semscanapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bgu.semscanapi.dto.PresenterHomeResponse;
import edu.bgu.semscanapi.dto.PresenterOpenAttendanceResponse;
import edu.bgu.semscanapi.dto.PresenterSlotRegistrationRequest;
import edu.bgu.semscanapi.dto.PresenterSlotRegistrationResponse;
import edu.bgu.semscanapi.dto.SupervisorEmailRequest;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.PresenterHomeService;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for PresenterHomeController
 * Tests API endpoints for presenter home operations including:
 * - Getting presenter home data
 * - Registering for slots
 * - Cancelling registrations
 * - Opening attendance sessions
 */
@WebMvcTest(controllers = PresenterHomeController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class PresenterHomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PresenterHomeService presenterHomeService;

    @MockBean
    private DatabaseLoggerService databaseLoggerService;

    private static final String TEST_USERNAME = "testuser";
    private static final Long TEST_SLOT_ID = 1L;

    private PresenterHomeResponse testHomeResponse;
    private PresenterSlotRegistrationRequest testRegistrationRequest;

    @BeforeEach
    void setUp() {
        // Setup test home response
        testHomeResponse = new PresenterHomeResponse();
        PresenterHomeResponse.PresenterSummary presenter = new PresenterHomeResponse.PresenterSummary();
        presenter.setId(1L);
        presenter.setName("Test User");
        presenter.setDegree("MSc");
        presenter.setBguUsername(TEST_USERNAME);
        presenter.setAlreadyRegistered(false);
        testHomeResponse.setPresenter(presenter);
        testHomeResponse.setSlotCatalog(new ArrayList<>());

        // Setup test registration request
        testRegistrationRequest = new PresenterSlotRegistrationRequest();
        testRegistrationRequest.setTopic("Test Topic");
        testRegistrationRequest.setSeminarAbstract("Test Abstract");
        testRegistrationRequest.setSupervisorName("Dr. Supervisor");
        testRegistrationRequest.setSupervisorEmail("supervisor@bgu.ac.il");
    }

    // ==================== GET /api/v1/presenters/{username}/home ====================

    @Nested
    @DisplayName("GET /api/v1/presenters/{username}/home")
    class GetPresenterHomeTests {

        @Test
        @DisplayName("Returns presenter home data successfully")
        void getPresenterHome_Success() throws Exception {
            // Given
            when(presenterHomeService.getPresenterHome(TEST_USERNAME)).thenReturn(testHomeResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/presenters/{username}/home", TEST_USERNAME)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.presenter.bguUsername").value(TEST_USERNAME))
                    .andExpect(jsonPath("$.presenter.name").value("Test User"))
                    .andExpect(jsonPath("$.presenter.degree").value("MSc"))
                    .andExpect(jsonPath("$.slotCatalog").isArray());

            verify(presenterHomeService).getPresenterHome(TEST_USERNAME);
        }

        @Test
        @DisplayName("Returns presenter home with slot catalog")
        void getPresenterHome_WithSlots() throws Exception {
            // Given
            PresenterHomeResponse.SlotCard slot = new PresenterHomeResponse.SlotCard();
            slot.setSlotId(1L);
            slot.setDate("15/01/2026");
            slot.setTimeRange("14:00 - 15:00");
            slot.setRoom("101");
            slot.setBuilding("Building A");
            slot.setState(PresenterHomeResponse.SlotState.FREE);
            slot.setCanRegister(true);
            testHomeResponse.getSlotCatalog().add(slot);

            when(presenterHomeService.getPresenterHome(TEST_USERNAME)).thenReturn(testHomeResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/presenters/{username}/home", TEST_USERNAME)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slotCatalog.length()").value(1))
                    .andExpect(jsonPath("$.slotCatalog[0].slotId").value(1))
                    .andExpect(jsonPath("$.slotCatalog[0].canRegister").value(true));
        }

        @Test
        @DisplayName("Returns 404 when presenter not found")
        void getPresenterHome_NotFound() throws Exception {
            // Given
            String unknownUser = "unknownuser";
            when(presenterHomeService.getPresenterHome(unknownUser))
                    .thenThrow(new IllegalArgumentException("User not found: " + unknownUser));

            // When & Then
            mockMvc.perform(get("/api/v1/presenters/{username}/home", unknownUser)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Presenter not found"));
        }

        @Test
        @DisplayName("Returns presenter with existing registration")
        void getPresenterHome_WithExistingRegistration() throws Exception {
            // Given
            testHomeResponse.getPresenter().setAlreadyRegistered(true);
            PresenterHomeResponse.MySlotSummary mySlot = new PresenterHomeResponse.MySlotSummary();
            mySlot.setSlotId(1L);
            mySlot.setDate("20/01/2026");
            mySlot.setTimeRange("10:00 - 11:00");
            testHomeResponse.setMySlot(mySlot);

            when(presenterHomeService.getPresenterHome(TEST_USERNAME)).thenReturn(testHomeResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/presenters/{username}/home", TEST_USERNAME)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.presenter.alreadyRegistered").value(true))
                    .andExpect(jsonPath("$.mySlot.slotId").value(1));
        }

        @Test
        @DisplayName("Returns presenter on waiting list")
        void getPresenterHome_OnWaitingList() throws Exception {
            // Given
            PresenterHomeResponse.WaitingListSlotSummary waitingSlot = new PresenterHomeResponse.WaitingListSlotSummary();
            waitingSlot.setSlotId(2L);
            waitingSlot.setPosition(1);
            waitingSlot.setTotalInQueue(3);
            testHomeResponse.setMyWaitingListSlot(waitingSlot);

            when(presenterHomeService.getPresenterHome(TEST_USERNAME)).thenReturn(testHomeResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/presenters/{username}/home", TEST_USERNAME)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myWaitingListSlot.slotId").value(2))
                    .andExpect(jsonPath("$.myWaitingListSlot.position").value(1))
                    .andExpect(jsonPath("$.myWaitingListSlot.totalInQueue").value(3));
        }
    }

    // ==================== POST /api/v1/presenters/{username}/home/slots/{slotId}/register ====================

    @Nested
    @DisplayName("POST /api/v1/presenters/{username}/home/slots/{slotId}/register")
    class RegisterForSlotTests {

        @Test
        @DisplayName("Registers for slot successfully - PENDING_APPROVAL")
        void registerForSlot_Success_PendingApproval() throws Exception {
            // Given
            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    true, "Registration successful, pending supervisor approval", "PENDING_APPROVAL", true);

            when(presenterHomeService.registerForSlot(eq(TEST_USERNAME), eq(TEST_SLOT_ID), any()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRegistrationRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.code").value("PENDING_APPROVAL"))
                    .andExpect(jsonPath("$.hasSupervisorEmail").value(true));

            verify(presenterHomeService).registerForSlot(eq(TEST_USERNAME), eq(TEST_SLOT_ID), any());
        }

        @Test
        @DisplayName("Returns 409 when slot is full")
        void registerForSlot_SlotFull() throws Exception {
            // Given
            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    false, "Slot is full", "SLOT_FULL", false);

            when(presenterHomeService.registerForSlot(eq(TEST_USERNAME), eq(TEST_SLOT_ID), any()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRegistrationRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("SLOT_FULL"));
        }

        @Test
        @DisplayName("Returns 409 when already registered")
        void registerForSlot_AlreadyRegistered() throws Exception {
            // Given
            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    false, "Already registered for this slot", "ALREADY_REGISTERED", false);

            when(presenterHomeService.registerForSlot(eq(TEST_USERNAME), eq(TEST_SLOT_ID), any()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRegistrationRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));
        }

        @Test
        @DisplayName("Returns 404 when slot not found")
        void registerForSlot_SlotNotFound() throws Exception {
            // Given
            Long nonExistentSlotId = 999L;
            when(presenterHomeService.registerForSlot(eq(TEST_USERNAME), eq(nonExistentSlotId), any()))
                    .thenThrow(new IllegalArgumentException("Slot not found: " + nonExistentSlotId));

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, nonExistentSlotId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRegistrationRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Returns 404 when user not found")
        void registerForSlot_UserNotFound() throws Exception {
            // Given
            String unknownUser = "unknownuser";
            when(presenterHomeService.registerForSlot(eq(unknownUser), eq(TEST_SLOT_ID), any()))
                    .thenThrow(new IllegalArgumentException("User not found: " + unknownUser));

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", unknownUser, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRegistrationRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Registration with minimal fields")
        void registerForSlot_MinimalFields() throws Exception {
            // Given
            PresenterSlotRegistrationRequest minimalRequest = new PresenterSlotRegistrationRequest();
            minimalRequest.setTopic("Minimal Topic");

            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    true, "Registration successful", "PENDING_APPROVAL", false);

            when(presenterHomeService.registerForSlot(eq(TEST_USERNAME), eq(TEST_SLOT_ID), any()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(minimalRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
        }

        @Test
        @DisplayName("Returns 400 for invalid supervisor email format")
        void registerForSlot_InvalidEmail() throws Exception {
            // Given
            PresenterSlotRegistrationRequest invalidRequest = new PresenterSlotRegistrationRequest();
            invalidRequest.setTopic("Test Topic");
            invalidRequest.setSupervisorEmail("invalid-email");

            // When & Then - validation should reject invalid email
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 for malformed JSON body")
        void registerForSlot_MalformedJson_Returns400() throws Exception {
            String malformedJson = "{ \"topic\": \"Test Topic\",";

            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(malformedJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 for missing username in path")
        void registerForSlot_MissingUsername() throws Exception {
            // Given
            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    false, "Missing username", "MISSING_USERNAME", false);

            when(presenterHomeService.registerForSlot(eq(""), eq(TEST_SLOT_ID), any()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", "", TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRegistrationRequest)))
                    .andExpect(status().isNotFound()); // Empty path variable results in 404
        }

        @Test
        @DisplayName("PhD blocked by MSc - returns error")
        void registerForSlot_PhdBlockedByMsc() throws Exception {
            // Given
            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    false, "Cannot register: slot has MSc presenters", "PHD_BLOCKED_BY_MSC", false);

            when(presenterHomeService.registerForSlot(eq(TEST_USERNAME), eq(TEST_SLOT_ID), any()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRegistrationRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("PHD_BLOCKED_BY_MSC"));
        }

        @Test
        @DisplayName("MSc blocked by PhD - slot locked")
        void registerForSlot_SlotLocked() throws Exception {
            // Given
            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    false, "Slot is reserved by PhD presenter", "SLOT_LOCKED", false);

            when(presenterHomeService.registerForSlot(eq(TEST_USERNAME), eq(TEST_SLOT_ID), any()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRegistrationRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("SLOT_LOCKED"));
        }
    }

    // ==================== DELETE /api/v1/presenters/{username}/home/slots/{slotId}/register ====================

    @Nested
    @DisplayName("DELETE /api/v1/presenters/{username}/home/slots/{slotId}/register")
    class UnregisterFromSlotTests {

        @Test
        @DisplayName("Cancels registration successfully")
        void unregisterFromSlot_Success() throws Exception {
            // Given
            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    true, "Registration cancelled successfully", "UNREGISTERED", null);

            when(presenterHomeService.unregisterFromSlot(TEST_USERNAME, TEST_SLOT_ID))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(delete("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.code").value("UNREGISTERED"));

            verify(presenterHomeService).unregisterFromSlot(TEST_USERNAME, TEST_SLOT_ID);
        }

        @Test
        @DisplayName("Returns 404 when not registered")
        void unregisterFromSlot_NotRegistered() throws Exception {
            // Given
            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    false, "Not registered for this slot", "NOT_REGISTERED", null);

            when(presenterHomeService.unregisterFromSlot(TEST_USERNAME, TEST_SLOT_ID))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(delete("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("NOT_REGISTERED"));
        }

        @Test
        @DisplayName("Returns 404 when slot not found")
        void unregisterFromSlot_SlotNotFound() throws Exception {
            // Given
            Long nonExistentSlotId = 999L;
            when(presenterHomeService.unregisterFromSlot(TEST_USERNAME, nonExistentSlotId))
                    .thenThrow(new IllegalArgumentException("Slot not found: " + nonExistentSlotId));

            // When & Then
            mockMvc.perform(delete("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, nonExistentSlotId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Returns 404 when user not found")
        void unregisterFromSlot_UserNotFound() throws Exception {
            // Given
            String unknownUser = "unknownuser";
            when(presenterHomeService.unregisterFromSlot(unknownUser, TEST_SLOT_ID))
                    .thenThrow(new IllegalArgumentException("User not found: " + unknownUser));

            // When & Then
            mockMvc.perform(delete("/api/v1/presenters/{username}/home/slots/{slotId}/register", unknownUser, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    // ==================== POST /api/v1/presenters/{username}/home/slots/{slotId}/attendance/open ====================

    @Nested
    @DisplayName("POST /api/v1/presenters/{username}/home/slots/{slotId}/attendance/open")
    class OpenAttendanceTests {

        @Test
        @DisplayName("Opens attendance successfully")
        void openAttendance_Success() throws Exception {
            // Given
            PresenterOpenAttendanceResponse response = new PresenterOpenAttendanceResponse(
                    true, "Attendance window opened", "OPENED",
                    "http://example.com/qr", "2026-01-15T14:00:00", "2026-01-15T14:15:00",
                    1L, "session-payload-123");

            when(presenterHomeService.openAttendance(TEST_USERNAME, TEST_SLOT_ID))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/attendance/open", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value("OPENED"))
                    .andExpect(jsonPath("$.sessionId").value(1))
                    .andExpect(jsonPath("$.qrPayload").value("session-payload-123"));

            verify(presenterHomeService).openAttendance(TEST_USERNAME, TEST_SLOT_ID);
        }

        @Test
        @DisplayName("Returns 403 when too early to open")
        void openAttendance_TooEarly() throws Exception {
            // Given
            PresenterOpenAttendanceResponse response = new PresenterOpenAttendanceResponse(
                    false, "Too early to open attendance window", "TOO_EARLY",
                    null, null, null, null, null);

            when(presenterHomeService.openAttendance(TEST_USERNAME, TEST_SLOT_ID))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/attendance/open", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("TOO_EARLY"));
        }

        @Test
        @DisplayName("Returns 409 when session already in progress")
        void openAttendance_InProgress() throws Exception {
            // Given
            PresenterOpenAttendanceResponse response = new PresenterOpenAttendanceResponse(
                    false, "Attendance session already in progress", "IN_PROGRESS",
                    null, null, null, null, null);

            when(presenterHomeService.openAttendance(TEST_USERNAME, TEST_SLOT_ID))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/attendance/open", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("IN_PROGRESS"));
        }

        @Test
        @DisplayName("Returns 404 when slot not found")
        void openAttendance_SlotNotFound() throws Exception {
            // Given
            Long nonExistentSlotId = 999L;
            when(presenterHomeService.openAttendance(TEST_USERNAME, nonExistentSlotId))
                    .thenThrow(new IllegalArgumentException("Slot not found: " + nonExistentSlotId));

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/attendance/open", TEST_USERNAME, nonExistentSlotId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Returns 404 when user not found")
        void openAttendance_UserNotFound() throws Exception {
            // Given
            String unknownUser = "unknownuser";
            when(presenterHomeService.openAttendance(unknownUser, TEST_SLOT_ID))
                    .thenThrow(new IllegalArgumentException("User not found: " + unknownUser));

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/attendance/open", unknownUser, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Returns 400 when missing username")
        void openAttendance_MissingUsername() throws Exception {
            // Given
            PresenterOpenAttendanceResponse response = new PresenterOpenAttendanceResponse(
                    false, "Missing username", "MISSING_USERNAME",
                    null, null, null, null, null);

            when(presenterHomeService.openAttendance(eq(""), eq(TEST_SLOT_ID)))
                    .thenReturn(response);

            // When & Then - Empty path variable typically results in 404
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/attendance/open", "", TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== POST /api/v1/presenters/{username}/home/slots/{slotId}/supervisor/email ====================

    @Nested
    @DisplayName("POST /api/v1/presenters/{username}/home/slots/{slotId}/supervisor/email")
    class SendSupervisorEmailTests {

        @Test
        @DisplayName("Sends supervisor email successfully")
        void sendSupervisorEmail_Success() throws Exception {
            // Given
            SupervisorEmailRequest request = new SupervisorEmailRequest();
            request.setSupervisorEmail("supervisor@bgu.ac.il");
            request.setSupervisorName("Dr. Supervisor");

            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    true, "Email sent successfully", "EMAIL_SENT", true);

            when(presenterHomeService.sendSupervisorEmail(eq(TEST_USERNAME), eq(TEST_SLOT_ID), anyString(), anyString()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/supervisor/email", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.code").value("EMAIL_SENT"));
        }

        @Test
        @DisplayName("Returns 503 when email service not configured")
        void sendSupervisorEmail_NotConfigured() throws Exception {
            // Given
            SupervisorEmailRequest request = new SupervisorEmailRequest();
            request.setSupervisorEmail("supervisor@bgu.ac.il");

            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    false, "Email service not configured", "EMAIL_NOT_CONFIGURED", false);

            when(presenterHomeService.sendSupervisorEmail(eq(TEST_USERNAME), eq(TEST_SLOT_ID), anyString(), any()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/supervisor/email", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("EMAIL_NOT_CONFIGURED"));
        }

        @Test
        @DisplayName("Returns 401 when email auth fails")
        void sendSupervisorEmail_AuthFailed() throws Exception {
            // Given
            SupervisorEmailRequest request = new SupervisorEmailRequest();
            request.setSupervisorEmail("supervisor@bgu.ac.il");

            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    false, "Email authentication failed", "EMAIL_AUTH_FAILED", false);

            when(presenterHomeService.sendSupervisorEmail(eq(TEST_USERNAME), eq(TEST_SLOT_ID), anyString(), any()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/supervisor/email", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("EMAIL_AUTH_FAILED"));
        }

        @Test
        @DisplayName("Returns 500 when email send fails")
        void sendSupervisorEmail_SendFailed() throws Exception {
            // Given
            SupervisorEmailRequest request = new SupervisorEmailRequest();
            request.setSupervisorEmail("supervisor@bgu.ac.il");

            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    false, "Failed to send email", "EMAIL_SEND_FAILED", false);

            when(presenterHomeService.sendSupervisorEmail(eq(TEST_USERNAME), eq(TEST_SLOT_ID), anyString(), any()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/supervisor/email", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("EMAIL_SEND_FAILED"));
        }

        @Test
        @DisplayName("Returns 400 when no supervisor email provided")
        void sendSupervisorEmail_NoEmail() throws Exception {
            // Given - SupervisorEmailRequest has @NotBlank validation on supervisorEmail
            // When email is null/blank, Spring validation triggers before controller method
            SupervisorEmailRequest request = new SupervisorEmailRequest();
            // No email set - validation will reject this

            // When & Then - Spring validation returns 400 Bad Request
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/supervisor/email", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
            // Note: Response body format depends on GlobalExceptionHandler's handling of validation errors
        }

        @Test
        @DisplayName("Returns 404 when registration not found")
        void sendSupervisorEmail_NotFound() throws Exception {
            // Given
            SupervisorEmailRequest request = new SupervisorEmailRequest();
            request.setSupervisorEmail("supervisor@bgu.ac.il");

            when(presenterHomeService.sendSupervisorEmail(eq(TEST_USERNAME), eq(TEST_SLOT_ID), anyString(), any()))
                    .thenThrow(new IllegalArgumentException("Registration not found"));

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/supervisor/email", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    // ==================== GET /api/v1/presenters/{username}/home/slots/{slotId}/attendance/qr ====================

    @Nested
    @DisplayName("GET /api/v1/presenters/{username}/home/slots/{slotId}/attendance/qr")
    class GetSlotQRCodeTests {

        @Test
        @DisplayName("Returns QR code data successfully")
        void getSlotQRCode_Success() throws Exception {
            // Given
            Map<String, Object> qrData = new HashMap<>();
            qrData.put("qrPayload", "session-token-123");
            qrData.put("sessionId", 1L);
            qrData.put("closesAt", "2026-01-15T14:15:00");

            when(presenterHomeService.getSlotQRCode(TEST_USERNAME, TEST_SLOT_ID))
                    .thenReturn(qrData);

            // When & Then
            mockMvc.perform(get("/api/v1/presenters/{username}/home/slots/{slotId}/attendance/qr", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.qrPayload").value("session-token-123"))
                    .andExpect(jsonPath("$.sessionId").value(1));

            verify(presenterHomeService).getSlotQRCode(TEST_USERNAME, TEST_SLOT_ID);
        }

        @Test
        @DisplayName("Returns 404 when slot not found")
        void getSlotQRCode_SlotNotFound() throws Exception {
            // Given
            Long nonExistentSlotId = 999L;
            when(presenterHomeService.getSlotQRCode(TEST_USERNAME, nonExistentSlotId))
                    .thenThrow(new IllegalArgumentException("Slot not found: " + nonExistentSlotId));

            // When & Then
            mockMvc.perform(get("/api/v1/presenters/{username}/home/slots/{slotId}/attendance/qr", TEST_USERNAME, nonExistentSlotId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("Returns 400 when attendance not open")
        void getSlotQRCode_AttendanceNotOpen() throws Exception {
            // Given
            when(presenterHomeService.getSlotQRCode(TEST_USERNAME, TEST_SLOT_ID))
                    .thenThrow(new IllegalStateException("Attendance window is not open"));

            // When & Then
            mockMvc.perform(get("/api/v1/presenters/{username}/home/slots/{slotId}/attendance/qr", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ATTENDANCE_NOT_OPEN"));
        }

        @Test
        @DisplayName("Returns 410 when attendance window closed")
        void getSlotQRCode_AttendanceClosed() throws Exception {
            // Given
            when(presenterHomeService.getSlotQRCode(TEST_USERNAME, TEST_SLOT_ID))
                    .thenThrow(new IllegalStateException("Attendance window has closed"));

            // When & Then
            mockMvc.perform(get("/api/v1/presenters/{username}/home/slots/{slotId}/attendance/qr", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value("ATTENDANCE_CLOSED"));
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Handles unexpected exception in getPresenterHome")
        void getPresenterHome_UnexpectedError() throws Exception {
            // Given
            when(presenterHomeService.getPresenterHome(TEST_USERNAME))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            mockMvc.perform(get("/api/v1/presenters/{username}/home", TEST_USERNAME)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Handles unexpected exception in registerForSlot")
        void registerForSlot_UnexpectedError() throws Exception {
            // Given
            when(presenterHomeService.registerForSlot(eq(TEST_USERNAME), eq(TEST_SLOT_ID), any()))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRegistrationRequest)))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Handles unexpected exception in unregisterFromSlot")
        void unregisterFromSlot_UnexpectedError() throws Exception {
            // Given
            when(presenterHomeService.unregisterFromSlot(TEST_USERNAME, TEST_SLOT_ID))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            mockMvc.perform(delete("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Handles unexpected exception in openAttendance")
        void openAttendance_UnexpectedError() throws Exception {
            // Given
            when(presenterHomeService.openAttendance(TEST_USERNAME, TEST_SLOT_ID))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/attendance/open", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Handles unexpected exception in getSlotQRCode")
        void getSlotQRCode_UnexpectedError() throws Exception {
            // Given
            when(presenterHomeService.getSlotQRCode(TEST_USERNAME, TEST_SLOT_ID))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            mockMvc.perform(get("/api/v1/presenters/{username}/home/slots/{slotId}/attendance/qr", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        }
    }

    // ==================== Registration Status Code Tests ====================

    @Nested
    @DisplayName("Registration Response Status Code Mapping")
    class RegistrationStatusCodeTests {

        @Test
        @DisplayName("Successful registration returns 200")
        void successfulRegistration_Returns200() throws Exception {
            // Given
            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    true, "Success", "REGISTERED", true);

            when(presenterHomeService.registerForSlot(eq(TEST_USERNAME), eq(TEST_SLOT_ID), any()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRegistrationRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("EMAIL_ERROR returns 500")
        void emailError_Returns500() throws Exception {
            // Given
            PresenterSlotRegistrationResponse response = new PresenterSlotRegistrationResponse(
                    false, "Email error", "EMAIL_ERROR", false);

            when(presenterHomeService.registerForSlot(eq(TEST_USERNAME), eq(TEST_SLOT_ID), any()))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/presenters/{username}/home/slots/{slotId}/register", TEST_USERNAME, TEST_SLOT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(testRegistrationRequest)))
                    .andExpect(status().isInternalServerError());
        }
    }
}
