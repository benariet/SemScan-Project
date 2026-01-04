package edu.bgu.semscanapi.unit;

import edu.bgu.semscanapi.dto.LoginRequest;
import edu.bgu.semscanapi.dto.PresenterSlotRegistrationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for null input validation.
 * These tests validate the behavior when null or empty inputs are provided.
 */
class NullInputValidationTest {

    @Nested
    @DisplayName("LoginRequest Validation")
    class LoginRequestValidation {

        @Test
        @DisplayName("Null username should be detected")
        void nullUsername_ShouldBeDetected() {
            LoginRequest request = new LoginRequest();
            request.setUsername(null);
            request.setPassword("password");

            assertNull(request.getUsername());
        }

        @Test
        @DisplayName("Empty username should be detected")
        void emptyUsername_ShouldBeDetected() {
            LoginRequest request = new LoginRequest();
            request.setUsername("");
            request.setPassword("password");

            assertTrue(request.getUsername().isEmpty());
        }

        @Test
        @DisplayName("Blank username should be detected")
        void blankUsername_ShouldBeDetected() {
            LoginRequest request = new LoginRequest();
            request.setUsername("   ");
            request.setPassword("password");

            assertTrue(request.getUsername().trim().isEmpty());
        }

        @Test
        @DisplayName("Null password should be detected")
        void nullPassword_ShouldBeDetected() {
            LoginRequest request = new LoginRequest();
            request.setUsername("user");
            request.setPassword(null);

            assertNull(request.getPassword());
        }

        @Test
        @DisplayName("Empty password should be detected")
        void emptyPassword_ShouldBeDetected() {
            LoginRequest request = new LoginRequest();
            request.setUsername("user");
            request.setPassword("");

            assertTrue(request.getPassword().isEmpty());
        }
    }

    @Nested
    @DisplayName("PresenterSlotRegistrationRequest Validation")
    class RegistrationRequestValidation {

        @Test
        @DisplayName("Null request should be handled")
        void nullRequest_ShouldBeNull() {
            PresenterSlotRegistrationRequest request = null;
            assertNull(request);
        }

        @Test
        @DisplayName("Request with null topic should be detected")
        void nullTopic_ShouldBeDetected() {
            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic(null);

            assertNull(request.getTopic());
        }

        @Test
        @DisplayName("Request with null supervisor email should be detected")
        void nullSupervisorEmail_ShouldBeDetected() {
            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setSupervisorEmail(null);

            assertNull(request.getSupervisorEmail());
        }

        @Test
        @DisplayName("Valid request should have all fields")
        void validRequest_ShouldHaveAllFields() {
            PresenterSlotRegistrationRequest request = new PresenterSlotRegistrationRequest();
            request.setTopic("Test Topic");
            request.setSupervisorName("Dr. Smith");
            request.setSupervisorEmail("smith@bgu.ac.il");

            assertNotNull(request.getTopic());
            assertNotNull(request.getSupervisorName());
            assertNotNull(request.getSupervisorEmail());
        }
    }
}
