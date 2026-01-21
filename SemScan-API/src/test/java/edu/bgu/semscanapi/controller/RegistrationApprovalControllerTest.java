package edu.bgu.semscanapi.controller;

import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.RegistrationApprovalService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for RegistrationApprovalController
 * Tests API endpoints for supervisor approval/decline of registrations
 */
@WebMvcTest(controllers = RegistrationApprovalController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class RegistrationApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegistrationApprovalService approvalService;

    @MockBean
    private DatabaseLoggerService databaseLoggerService;

    @BeforeEach
    void setUp() {
        // Mock database logger to avoid NPE (DeviceInfoInterceptor uses static methods on DatabaseLoggerService)
        // Even though the controller doesn't directly use it, the interceptor chain does
    }

    private static final String VALID_TOKEN = "550e8400-e29b-41d4-a716-446655440000";
    private static final Long VALID_SLOT_ID = 1L;
    private static final String VALID_USERNAME = "testuser";

    // ==================== GET /api/v1/approve/{approvalToken} ====================

    @Nested
    @DisplayName("GET /api/v1/approve/{approvalToken}")
    class ApproveByTokenTests {

        @Test
        @DisplayName("Returns success HTML page when approval succeeds")
        void approveByToken_Success_ReturnsHtmlPage() throws Exception {
            // Given
            doNothing().when(approvalService).approveRegistrationByToken(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Registration Approved")))
                    .andExpect(content().string(containsString("successfully approved")));

            verify(approvalService).approveRegistrationByToken(VALID_TOKEN);
        }

        @Test
        @DisplayName("Returns 400 with error HTML when token is invalid (IllegalArgumentException)")
        void approveByToken_InvalidToken_Returns400() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Invalid approval token"))
                    .when(approvalService).approveRegistrationByToken(anyString());

            // When & Then
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", "invalid-token")
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Invalid Request")))
                    .andExpect(content().string(containsString("Invalid approval token")));
        }

        @Test
        @DisplayName("Returns 400 with error HTML when token not found")
        void approveByToken_TokenNotFound_Returns400() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Registration not found for this token"))
                    .when(approvalService).approveRegistrationByToken(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Invalid Request")));
        }

        @Test
        @DisplayName("Returns 400 with error HTML when token expired (IllegalStateException)")
        void approveByToken_TokenExpired_Returns400() throws Exception {
            // Given
            doThrow(new IllegalStateException("This approval link has expired. Please ask the student to register again."))
                    .when(approvalService).approveRegistrationByToken(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Approval Failed")))
                    .andExpect(content().string(containsString("expired")));
        }

        @Test
        @DisplayName("Returns 400 when registration is not pending (already approved/declined)")
        void approveByToken_NotPending_Returns400() throws Exception {
            // Given
            doThrow(new IllegalStateException("Registration is not pending approval"))
                    .when(approvalService).approveRegistrationByToken(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Approval Failed")));
        }

        @Test
        @DisplayName("Returns 500 with error HTML when unexpected error occurs")
        void approveByToken_UnexpectedError_Returns500() throws Exception {
            // Given
            doThrow(new RuntimeException("Database connection error"))
                    .when(approvalService).approveRegistrationByToken(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Error")))
                    .andExpect(content().string(containsString("unexpected error")));
        }

        @Test
        @DisplayName("Idempotent - returns success when already approved")
        void approveByToken_AlreadyApproved_ReturnsSuccess() throws Exception {
            // Given - service handles idempotency internally
            doNothing().when(approvalService).approveRegistrationByToken(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Registration Approved")));
        }
    }

    // ==================== GET /api/v1/decline/{approvalToken} ====================

    @Nested
    @DisplayName("GET /api/v1/decline/{approvalToken}")
    class DeclineByTokenTests {

        @Test
        @DisplayName("Returns success HTML page when decline succeeds (no reason)")
        void declineByToken_Success_NoReason_ReturnsHtmlPage() throws Exception {
            // Given
            doNothing().when(approvalService).declineRegistrationByToken(eq(VALID_TOKEN), isNull());

            // When & Then
            mockMvc.perform(get("/api/v1/decline/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Registration Declined")))
                    .andExpect(content().string(containsString("declined")));

            verify(approvalService).declineRegistrationByToken(VALID_TOKEN, null);
        }

        @Test
        @DisplayName("Returns success HTML page when decline succeeds (with reason)")
        void declineByToken_Success_WithReason_ReturnsHtmlPage() throws Exception {
            // Given
            String reason = "Student needs to complete coursework first";
            doNothing().when(approvalService).declineRegistrationByToken(eq(VALID_TOKEN), eq(reason));

            // When & Then
            mockMvc.perform(get("/api/v1/decline/{approvalToken}", VALID_TOKEN)
                    .param("reason", reason)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Registration Declined")));

            verify(approvalService).declineRegistrationByToken(VALID_TOKEN, reason);
        }

        @Test
        @DisplayName("Returns 400 with error HTML when token is invalid")
        void declineByToken_InvalidToken_Returns400() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Invalid approval token"))
                    .when(approvalService).declineRegistrationByToken(anyString(), any());

            // When & Then
            mockMvc.perform(get("/api/v1/decline/{approvalToken}", "invalid-token")
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Invalid Request")));
        }

        @Test
        @DisplayName("Returns 400 with error HTML when token not found")
        void declineByToken_TokenNotFound_Returns400() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Registration not found for this token"))
                    .when(approvalService).declineRegistrationByToken(eq(VALID_TOKEN), any());

            // When & Then
            mockMvc.perform(get("/api/v1/decline/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Invalid Request")));
        }

        @Test
        @DisplayName("Returns 400 with error HTML when token expired")
        void declineByToken_TokenExpired_Returns400() throws Exception {
            // Given
            doThrow(new IllegalStateException("This approval link has expired. Please ask the student to register again."))
                    .when(approvalService).declineRegistrationByToken(eq(VALID_TOKEN), any());

            // When & Then
            mockMvc.perform(get("/api/v1/decline/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Decline Failed")))
                    .andExpect(content().string(containsString("expired")));
        }

        @Test
        @DisplayName("Returns 400 when registration is not pending")
        void declineByToken_NotPending_Returns400() throws Exception {
            // Given
            doThrow(new IllegalStateException("Registration is not pending approval"))
                    .when(approvalService).declineRegistrationByToken(eq(VALID_TOKEN), any());

            // When & Then
            mockMvc.perform(get("/api/v1/decline/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Decline Failed")));
        }

        @Test
        @DisplayName("Returns 500 with error HTML when unexpected error occurs")
        void declineByToken_UnexpectedError_Returns500() throws Exception {
            // Given
            doThrow(new RuntimeException("Database connection error"))
                    .when(approvalService).declineRegistrationByToken(eq(VALID_TOKEN), any());

            // When & Then
            mockMvc.perform(get("/api/v1/decline/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Error")))
                    .andExpect(content().string(containsString("unexpected error")));
        }

        @Test
        @DisplayName("Idempotent - returns success when already declined")
        void declineByToken_AlreadyDeclined_ReturnsSuccess() throws Exception {
            // Given - service handles idempotency internally
            doNothing().when(approvalService).declineRegistrationByToken(eq(VALID_TOKEN), any());

            // When & Then
            mockMvc.perform(get("/api/v1/decline/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Registration Declined")));
        }
    }

    // ==================== GET /api/v1/slots/{slotId}/registrations/{presenterUsername}/approve ====================

    @Nested
    @DisplayName("GET /api/v1/slots/{slotId}/registrations/{presenterUsername}/approve (legacy)")
    class LegacyApproveTests {

        @Test
        @DisplayName("Returns success HTML page when approval succeeds")
        void legacyApprove_Success_ReturnsHtmlPage() throws Exception {
            // Given
            doNothing().when(approvalService).approveRegistration(eq(VALID_SLOT_ID), eq(VALID_USERNAME), eq(VALID_TOKEN));

            // When & Then
            mockMvc.perform(get("/api/v1/slots/{slotId}/registrations/{presenterUsername}/approve",
                    VALID_SLOT_ID, VALID_USERNAME)
                    .param("token", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Registration Approved")));

            verify(approvalService).approveRegistration(VALID_SLOT_ID, VALID_USERNAME, VALID_TOKEN);
        }

        @Test
        @DisplayName("Returns 400 when token is invalid")
        void legacyApprove_InvalidToken_Returns400() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Invalid approval token"))
                    .when(approvalService).approveRegistration(eq(VALID_SLOT_ID), eq(VALID_USERNAME), anyString());

            // When & Then
            mockMvc.perform(get("/api/v1/slots/{slotId}/registrations/{presenterUsername}/approve",
                    VALID_SLOT_ID, VALID_USERNAME)
                    .param("token", "invalid-token")
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Invalid Request")));
        }

        @Test
        @DisplayName("Returns 400 when registration not found")
        void legacyApprove_RegistrationNotFound_Returns400() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Registration not found"))
                    .when(approvalService).approveRegistration(eq(VALID_SLOT_ID), eq(VALID_USERNAME), eq(VALID_TOKEN));

            // When & Then
            mockMvc.perform(get("/api/v1/slots/{slotId}/registrations/{presenterUsername}/approve",
                    VALID_SLOT_ID, VALID_USERNAME)
                    .param("token", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Invalid Request")));
        }

        @Test
        @DisplayName("Returns 400 when token expired")
        void legacyApprove_TokenExpired_Returns400() throws Exception {
            // Given
            doThrow(new IllegalStateException("This approval link has expired"))
                    .when(approvalService).approveRegistration(eq(VALID_SLOT_ID), eq(VALID_USERNAME), eq(VALID_TOKEN));

            // When & Then
            mockMvc.perform(get("/api/v1/slots/{slotId}/registrations/{presenterUsername}/approve",
                    VALID_SLOT_ID, VALID_USERNAME)
                    .param("token", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Approval Failed")));
        }

        @Test
        @DisplayName("Returns 500 when unexpected error occurs")
        void legacyApprove_UnexpectedError_Returns500() throws Exception {
            // Given
            doThrow(new RuntimeException("Database error"))
                    .when(approvalService).approveRegistration(eq(VALID_SLOT_ID), eq(VALID_USERNAME), eq(VALID_TOKEN));

            // When & Then
            mockMvc.perform(get("/api/v1/slots/{slotId}/registrations/{presenterUsername}/approve",
                    VALID_SLOT_ID, VALID_USERNAME)
                    .param("token", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(containsString("Error")));
        }
    }

    // ==================== GET /api/v1/slots/{slotId}/registrations/{presenterUsername}/decline ====================

    @Nested
    @DisplayName("GET /api/v1/slots/{slotId}/registrations/{presenterUsername}/decline (legacy)")
    class LegacyDeclineTests {

        @Test
        @DisplayName("Returns success HTML page when decline succeeds (no reason)")
        void legacyDecline_Success_NoReason_ReturnsHtmlPage() throws Exception {
            // Given - When reason is null, controller passes "No reason provided"
            doNothing().when(approvalService).declineRegistration(
                    eq(VALID_SLOT_ID), eq(VALID_USERNAME), eq(VALID_TOKEN), eq("No reason provided"));

            // When & Then
            mockMvc.perform(get("/api/v1/slots/{slotId}/registrations/{presenterUsername}/decline",
                    VALID_SLOT_ID, VALID_USERNAME)
                    .param("token", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Registration Declined")));

            verify(approvalService).declineRegistration(VALID_SLOT_ID, VALID_USERNAME, VALID_TOKEN, "No reason provided");
        }

        @Test
        @DisplayName("Returns success HTML page when decline succeeds (with reason)")
        void legacyDecline_Success_WithReason_ReturnsHtmlPage() throws Exception {
            // Given
            String reason = "Student not ready for presentation";
            doNothing().when(approvalService).declineRegistration(
                    eq(VALID_SLOT_ID), eq(VALID_USERNAME), eq(VALID_TOKEN), eq(reason));

            // When & Then
            mockMvc.perform(get("/api/v1/slots/{slotId}/registrations/{presenterUsername}/decline",
                    VALID_SLOT_ID, VALID_USERNAME)
                    .param("token", VALID_TOKEN)
                    .param("reason", reason)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Registration Declined")));

            verify(approvalService).declineRegistration(VALID_SLOT_ID, VALID_USERNAME, VALID_TOKEN, reason);
        }

        @Test
        @DisplayName("Returns 400 when token is invalid")
        void legacyDecline_InvalidToken_Returns400() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Invalid approval token"))
                    .when(approvalService).declineRegistration(
                            eq(VALID_SLOT_ID), eq(VALID_USERNAME), anyString(), anyString());

            // When & Then
            mockMvc.perform(get("/api/v1/slots/{slotId}/registrations/{presenterUsername}/decline",
                    VALID_SLOT_ID, VALID_USERNAME)
                    .param("token", "invalid-token")
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Invalid Request")));
        }

        @Test
        @DisplayName("Returns 400 when registration not found")
        void legacyDecline_RegistrationNotFound_Returns400() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Registration not found"))
                    .when(approvalService).declineRegistration(
                            eq(VALID_SLOT_ID), eq(VALID_USERNAME), eq(VALID_TOKEN), anyString());

            // When & Then
            mockMvc.perform(get("/api/v1/slots/{slotId}/registrations/{presenterUsername}/decline",
                    VALID_SLOT_ID, VALID_USERNAME)
                    .param("token", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Invalid Request")));
        }

        @Test
        @DisplayName("Returns 400 when token expired")
        void legacyDecline_TokenExpired_Returns400() throws Exception {
            // Given
            doThrow(new IllegalStateException("This approval link has expired"))
                    .when(approvalService).declineRegistration(
                            eq(VALID_SLOT_ID), eq(VALID_USERNAME), eq(VALID_TOKEN), anyString());

            // When & Then
            mockMvc.perform(get("/api/v1/slots/{slotId}/registrations/{presenterUsername}/decline",
                    VALID_SLOT_ID, VALID_USERNAME)
                    .param("token", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Decline Failed")));
        }

        @Test
        @DisplayName("Returns 500 when unexpected error occurs")
        void legacyDecline_UnexpectedError_Returns500() throws Exception {
            // Given
            doThrow(new RuntimeException("Database error"))
                    .when(approvalService).declineRegistration(
                            eq(VALID_SLOT_ID), eq(VALID_USERNAME), eq(VALID_TOKEN), anyString());

            // When & Then
            mockMvc.perform(get("/api/v1/slots/{slotId}/registrations/{presenterUsername}/decline",
                    VALID_SLOT_ID, VALID_USERNAME)
                    .param("token", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(containsString("Error")));
        }
    }

    // ==================== GET /api/v1/student-confirm/{confirmationToken} ====================

    @Nested
    @DisplayName("GET /api/v1/student-confirm/{confirmationToken}")
    class StudentConfirmPromotionTests {

        @Test
        @DisplayName("Returns success HTML page when student confirms promotion")
        void confirmPromotion_Success_ReturnsHtmlPage() throws Exception {
            // Given
            doNothing().when(approvalService).confirmStudentPromotion(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/student-confirm/{confirmationToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Promotion Confirmed")))
                    .andExpect(content().string(containsString("supervisor")));

            verify(approvalService).confirmStudentPromotion(VALID_TOKEN);
        }

        @Test
        @DisplayName("Returns 400 when confirmation token is invalid")
        void confirmPromotion_InvalidToken_Returns400() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Invalid confirmation token"))
                    .when(approvalService).confirmStudentPromotion(anyString());

            // When & Then
            mockMvc.perform(get("/api/v1/student-confirm/{confirmationToken}", "invalid-token")
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Invalid Request")));
        }

        @Test
        @DisplayName("Returns 400 when confirmation token expired")
        void confirmPromotion_TokenExpired_Returns400() throws Exception {
            // Given
            doThrow(new IllegalStateException("Confirmation token has expired"))
                    .when(approvalService).confirmStudentPromotion(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/student-confirm/{confirmationToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Confirmation Failed")));
        }

        @Test
        @DisplayName("Returns 400 when registration is not pending")
        void confirmPromotion_NotPending_Returns400() throws Exception {
            // Given
            doThrow(new IllegalStateException("Registration is not pending student confirmation"))
                    .when(approvalService).confirmStudentPromotion(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/student-confirm/{confirmationToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Confirmation Failed")));
        }

        @Test
        @DisplayName("Returns 500 when unexpected error occurs")
        void confirmPromotion_UnexpectedError_Returns500() throws Exception {
            // Given
            doThrow(new RuntimeException("Database error"))
                    .when(approvalService).confirmStudentPromotion(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/student-confirm/{confirmationToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(containsString("Error")));
        }
    }

    // ==================== GET /api/v1/student-decline/{confirmationToken} ====================

    @Nested
    @DisplayName("GET /api/v1/student-decline/{confirmationToken}")
    class StudentDeclinePromotionTests {

        @Test
        @DisplayName("Returns success HTML page when student declines promotion")
        void declinePromotion_Success_ReturnsHtmlPage() throws Exception {
            // Given
            doNothing().when(approvalService).declineStudentPromotion(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/student-decline/{confirmationToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Promotion Declined")))
                    .andExpect(content().string(containsString("cancelled")));

            verify(approvalService).declineStudentPromotion(VALID_TOKEN);
        }

        @Test
        @DisplayName("Returns 400 when confirmation token is invalid")
        void declinePromotion_InvalidToken_Returns400() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Invalid confirmation token"))
                    .when(approvalService).declineStudentPromotion(anyString());

            // When & Then
            mockMvc.perform(get("/api/v1/student-decline/{confirmationToken}", "invalid-token")
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Invalid Request")));
        }

        @Test
        @DisplayName("Returns 400 when confirmation token expired")
        void declinePromotion_TokenExpired_Returns400() throws Exception {
            // Given
            doThrow(new IllegalStateException("Confirmation token has expired"))
                    .when(approvalService).declineStudentPromotion(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/student-decline/{confirmationToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Decline Failed")));
        }

        @Test
        @DisplayName("Returns 400 when registration is not pending")
        void declinePromotion_NotPending_Returns400() throws Exception {
            // Given
            doThrow(new IllegalStateException("Registration is not pending student confirmation"))
                    .when(approvalService).declineStudentPromotion(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/student-decline/{confirmationToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("Decline Failed")));
        }

        @Test
        @DisplayName("Returns 500 when unexpected error occurs")
        void declinePromotion_UnexpectedError_Returns500() throws Exception {
            // Given
            doThrow(new RuntimeException("Database error"))
                    .when(approvalService).declineStudentPromotion(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/student-decline/{confirmationToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(containsString("Error")));
        }
    }

    // ==================== Edge Cases and Token Format Tests ====================

    @Nested
    @DisplayName("Token Format and Edge Cases")
    class TokenFormatTests {

        @Test
        @DisplayName("Handles empty token in path - returns 404 or 400")
        void emptyToken_HandledGracefully() throws Exception {
            // Note: Spring will typically return 404 for empty path variable
            // This test documents the expected behavior
            mockMvc.perform(get("/api/v1/approve/")
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Handles very long token gracefully")
        void veryLongToken_HandledGracefully() throws Exception {
            // Given
            String veryLongToken = "a".repeat(1000);
            doThrow(new IllegalArgumentException("Invalid approval token"))
                    .when(approvalService).approveRegistrationByToken(veryLongToken);

            // When & Then
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", veryLongToken)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Handles special characters in token")
        void specialCharactersInToken_HandledGracefully() throws Exception {
            // Given
            String tokenWithSpecialChars = "token-with-special-chars-!@#$%";
            doThrow(new IllegalArgumentException("Invalid approval token"))
                    .when(approvalService).approveRegistrationByToken(anyString());

            // When & Then - URL encoding may change the token
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", tokenWithSpecialChars)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Handles UUID format token correctly")
        void validUuidToken_ProcessedCorrectly() throws Exception {
            // Given
            String uuidToken = "550e8400-e29b-41d4-a716-446655440000";
            doNothing().when(approvalService).approveRegistrationByToken(uuidToken);

            // When & Then
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", uuidToken)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk());

            verify(approvalService).approveRegistrationByToken(uuidToken);
        }
    }

    // ==================== HTML Response Content Tests ====================

    @Nested
    @DisplayName("HTML Response Content Validation")
    class HtmlContentTests {

        @Test
        @DisplayName("Success page contains proper HTML structure")
        void successPage_ContainsProperHtmlStructure() throws Exception {
            // Given
            doNothing().when(approvalService).approveRegistrationByToken(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<!DOCTYPE html>")))
                    .andExpect(content().string(containsString("<html>")))
                    .andExpect(content().string(containsString("</html>")))
                    .andExpect(content().string(containsString("<head>")))
                    .andExpect(content().string(containsString("<body>")));
        }

        @Test
        @DisplayName("Error page contains proper HTML structure")
        void errorPage_ContainsProperHtmlStructure() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Invalid token"))
                    .when(approvalService).approveRegistrationByToken(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("<!DOCTYPE html>")))
                    .andExpect(content().string(containsString("<html>")))
                    .andExpect(content().string(containsString("</html>")));
        }

        @Test
        @DisplayName("Success page contains success icon")
        void successPage_ContainsSuccessIcon() throws Exception {
            // Given
            doNothing().when(approvalService).approveRegistrationByToken(VALID_TOKEN);

            // When & Then - The controller uses a checkmark symbol
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("success-icon")));
        }

        @Test
        @DisplayName("Error page contains error icon")
        void errorPage_ContainsErrorIcon() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Invalid token"))
                    .when(approvalService).approveRegistrationByToken(VALID_TOKEN);

            // When & Then
            mockMvc.perform(get("/api/v1/approve/{approvalToken}", VALID_TOKEN)
                    .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("error-icon")));
        }
    }
}
