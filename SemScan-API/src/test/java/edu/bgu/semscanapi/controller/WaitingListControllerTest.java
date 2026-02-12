package edu.bgu.semscanapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bgu.semscanapi.dto.WaitingListRequest;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.entity.WaitingListEntry;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.WaitingListService;
import edu.bgu.semscanapi.config.GlobalConfig;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for WaitingListController
 * Tests API endpoints for waiting list operations
 */
@WebMvcTest(controllers = WaitingListController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class WaitingListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WaitingListService waitingListService;

    @MockBean
    private DatabaseLoggerService databaseLoggerService;

    @MockBean
    private GlobalConfig globalConfig;

    private WaitingListEntry testEntry;

    @BeforeEach
    void setUp() {
        testEntry = new WaitingListEntry();
        testEntry.setWaitingListId(1L);
        testEntry.setSlotId(1L);
        testEntry.setPresenterUsername("testuser");
        testEntry.setDegree(User.Degree.MSc);
        testEntry.setTopic("Test Topic");
        testEntry.setPosition(1);
        testEntry.setAddedAt(LocalDateTime.now());
    }

    // ==================== GET /api/v1/slots/{slotId}/waiting-list ====================

    @Nested
    @DisplayName("GET /api/v1/slots/{slotId}/waiting-list")
    class GetWaitingListTests {

        @Test
        @DisplayName("Returns waiting list for slot")
        void getWaitingList_ReturnsEntries() throws Exception {
            // Given
            WaitingListEntry entry2 = new WaitingListEntry();
            entry2.setSlotId(1L);
            entry2.setPresenterUsername("user2");
            entry2.setDegree(User.Degree.MSc);
            entry2.setTopic("Topic 2");
            entry2.setPosition(2);
            entry2.setAddedAt(LocalDateTime.now());

            when(waitingListService.getWaitingList(1L)).thenReturn(Arrays.asList(testEntry, entry2));

            // When & Then
            mockMvc.perform(get("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slotId").value(1))
                    .andExpect(jsonPath("$.entries").isArray())
                    .andExpect(jsonPath("$.entries.length()").value(2))
                    .andExpect(jsonPath("$.entries[0].presenterUsername").value("testuser"))
                    .andExpect(jsonPath("$.entries[0].position").value(1))
                    .andExpect(jsonPath("$.entries[1].presenterUsername").value("user2"))
                    .andExpect(jsonPath("$.entries[1].position").value(2));

            verify(waitingListService).getWaitingList(1L);
        }

        @Test
        @DisplayName("Returns empty list when no entries")
        void getWaitingList_ReturnsEmptyList() throws Exception {
            // Given
            when(waitingListService.getWaitingList(1L)).thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slotId").value(1))
                    .andExpect(jsonPath("$.entries").isArray())
                    .andExpect(jsonPath("$.entries").isEmpty());
        }
    }

    // ==================== POST /api/v1/slots/{slotId}/waiting-list ====================

    @Nested
    @DisplayName("POST /api/v1/slots/{slotId}/waiting-list")
    class AddToWaitingListTests {

        @Test
        @DisplayName("Adds user to waiting list - SUCCESS")
        void addToWaitingList_Success() throws Exception {
            // Given
            WaitingListRequest request = new WaitingListRequest();
            request.setPresenterUsername("testuser");
            request.setTopic("Test Topic");
            request.setSupervisorName("Dr. Supervisor");
            request.setSupervisorEmail("supervisor@example.com");

            when(waitingListService.addToWaitingList(eq(1L), eq("testuser"), anyString(), anyString(), anyString()))
                    .thenReturn(testEntry);

            // When & Then
            mockMvc.perform(post("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.code").value("ADDED_TO_WAITING_LIST"))
                    .andExpect(jsonPath("$.position").value(1))
                    .andExpect(jsonPath("$.slotId").value(1));

            verify(waitingListService).addToWaitingList(1L, "testuser", "Test Topic", "Dr. Supervisor", "supervisor@example.com");
        }

        @Test
        @DisplayName("Returns 400 when request body is null")
        void addToWaitingList_NullBody_Returns400() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("null"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when request body is malformed JSON")
        void addToWaitingList_MalformedJson_Returns400() throws Exception {
            String malformedJson = "{ \"presenterUsername\": \"testuser\",";

            mockMvc.perform(post("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(malformedJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when username is missing")
        void addToWaitingList_MissingUsername_Returns400() throws Exception {
            // Given - WaitingListRequest has @NotBlank on presenterUsername
            // Spring's bean validation triggers before controller's custom validation
            WaitingListRequest request = new WaitingListRequest();
            request.setPresenterUsername(null);
            request.setTopic("Test Topic");

            // When & Then - Just verify 400 status (Spring validation returns default error format)
            mockMvc.perform(post("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when username is empty")
        void addToWaitingList_EmptyUsername_Returns400() throws Exception {
            // Given - @NotBlank validates that string is not blank
            WaitingListRequest request = new WaitingListRequest();
            request.setPresenterUsername("   ");
            request.setTopic("Test Topic");

            // When & Then - Just verify 400 status (Spring validation returns default error format)
            mockMvc.perform(post("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when service throws IllegalStateException (business error)")
        void addToWaitingList_BusinessError_Returns400() throws Exception {
            // Given
            WaitingListRequest request = new WaitingListRequest();
            request.setPresenterUsername("testuser");
            request.setTopic("Test Topic");

            when(waitingListService.addToWaitingList(anyLong(), anyString(), anyString(), any(), any()))
                    .thenThrow(new IllegalStateException("Cannot join waiting list - queue is currently MSc-only"));

            // When & Then
            mockMvc.perform(post("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                    .andExpect(jsonPath("$.message").value("Cannot join waiting list - queue is currently MSc-only"));
        }

        @Test
        @DisplayName("Returns 400 when service throws IllegalArgumentException (validation error)")
        void addToWaitingList_ValidationError_Returns400() throws Exception {
            // Given
            WaitingListRequest request = new WaitingListRequest();
            request.setPresenterUsername("testuser");
            request.setTopic("Test Topic");

            when(waitingListService.addToWaitingList(anyLong(), anyString(), anyString(), any(), any()))
                    .thenThrow(new IllegalArgumentException("Slot not found: 999"));

            // When & Then
            mockMvc.perform(post("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        }

        @Test
        @DisplayName("Returns 400 when already on waiting list")
        void addToWaitingList_AlreadyOnList_Returns400() throws Exception {
            // Given
            WaitingListRequest request = new WaitingListRequest();
            request.setPresenterUsername("testuser");
            request.setTopic("Test Topic");

            when(waitingListService.addToWaitingList(anyLong(), anyString(), anyString(), any(), any()))
                    .thenThrow(new IllegalStateException("User is already on the waiting list for this slot"));

            // When & Then
            mockMvc.perform(post("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                    .andExpect(jsonPath("$.message").value("User is already on the waiting list for this slot"));
        }

        @Test
        @DisplayName("Returns 400 when waiting list is full")
        void addToWaitingList_ListFull_Returns400() throws Exception {
            // Given
            WaitingListRequest request = new WaitingListRequest();
            request.setPresenterUsername("testuser");
            request.setTopic("Test Topic");

            when(waitingListService.addToWaitingList(anyLong(), anyString(), anyString(), any(), any()))
                    .thenThrow(new IllegalStateException("Waiting list is full. Please try again later."));

            // When & Then
            mockMvc.perform(post("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.message").value("Waiting list is full. Please try again later."));
        }
    }

    // ==================== DELETE /api/v1/slots/{slotId}/waiting-list ====================

    @Nested
    @DisplayName("DELETE /api/v1/slots/{slotId}/waiting-list")
    class RemoveFromWaitingListTests {

        @Test
        @DisplayName("Removes user from waiting list - SUCCESS")
        void removeFromWaitingList_Success() throws Exception {
            // Given
            when(waitingListService.removeFromWaitingList(1L, "testuser")).thenReturn(testEntry);

            // When & Then
            mockMvc.perform(delete("/api/v1/slots/1/waiting-list")
                    .param("username", "testuser")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.message").value("Removed from waiting list"));

            verify(waitingListService).removeFromWaitingList(1L, "testuser");
        }

        @Test
        @DisplayName("Returns 400 when username parameter is missing")
        void removeFromWaitingList_MissingUsername_Returns400() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("MISSING_USERNAME"));
        }

        @Test
        @DisplayName("Returns 400 when username parameter is empty")
        void removeFromWaitingList_EmptyUsername_Returns400() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/v1/slots/1/waiting-list")
                    .param("username", "  ")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("MISSING_USERNAME"));
        }

        @Test
        @DisplayName("Returns 404 when user not on waiting list")
        void removeFromWaitingList_NotOnList_Returns404() throws Exception {
            // Given
            when(waitingListService.removeFromWaitingList(1L, "testuser"))
                    .thenThrow(new IllegalArgumentException("Not on waiting list for this slot"));

            // When & Then
            mockMvc.perform(delete("/api/v1/slots/1/waiting-list")
                    .param("username", "testuser")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("NOT_ON_WAITING_LIST"));
        }

        @Test
        @DisplayName("Returns 500 when unexpected error occurs")
        void removeFromWaitingList_UnexpectedError_Returns500() throws Exception {
            // Given
            when(waitingListService.removeFromWaitingList(1L, "testuser"))
                    .thenThrow(new RuntimeException("Database connection error"));

            // When & Then
            mockMvc.perform(delete("/api/v1/slots/1/waiting-list")
                    .param("username", "testuser")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        }
    }

    // ==================== PhD/MSc Queue Type Error Tests ====================

    @Nested
    @DisplayName("PhD/MSc Queue Type Error Tests")
    class PhdMscQueueTypeTests {

        @Test
        @DisplayName("PhD blocked when queue is MSc-only")
        void phdBlockedByMscQueue_Returns400() throws Exception {
            // Given
            WaitingListRequest request = new WaitingListRequest();
            request.setPresenterUsername("phduser");
            request.setTopic("PhD Topic");

            when(waitingListService.addToWaitingList(anyLong(), anyString(), anyString(), any(), any()))
                    .thenThrow(new IllegalStateException("Cannot join waiting list - queue is currently MSc-only"));

            // When & Then
            mockMvc.perform(post("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.message").value("Cannot join waiting list - queue is currently MSc-only"));
        }

        @Test
        @DisplayName("MSc blocked when queue is PhD-only")
        void mscBlockedByPhdQueue_Returns400() throws Exception {
            // Given
            WaitingListRequest request = new WaitingListRequest();
            request.setPresenterUsername("mscuser");
            request.setTopic("MSc Topic");

            when(waitingListService.addToWaitingList(anyLong(), anyString(), anyString(), any(), any()))
                    .thenThrow(new IllegalStateException("Cannot join waiting list - queue is currently PhD-only"));

            // When & Then
            mockMvc.perform(post("/api/v1/slots/1/waiting-list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.message").value("Cannot join waiting list - queue is currently PhD-only"));
        }
    }
}
