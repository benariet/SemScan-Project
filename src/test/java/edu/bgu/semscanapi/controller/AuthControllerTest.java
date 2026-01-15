package edu.bgu.semscanapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bgu.semscanapi.dto.AccountSetupRequest;
import edu.bgu.semscanapi.dto.LoginRequest;
import edu.bgu.semscanapi.entity.Supervisor;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.SupervisorRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.service.BguAuthSoapService;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AuthController
 * Tests API endpoints for authentication and account setup
 */
@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BguAuthSoapService bguAuthSoapService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private SupervisorRepository supervisorRepository;

    @MockBean
    private DatabaseLoggerService databaseLoggerService;

    private User existingUser;
    private Supervisor existingSupervisor;

    @BeforeEach
    void setUp() {
        existingSupervisor = new Supervisor();
        existingSupervisor.setId(1L);
        existingSupervisor.setName("Dr. Test Supervisor");
        existingSupervisor.setEmail("supervisor@bgu.ac.il");

        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setBguUsername("testuser");
        existingUser.setEmail("testuser@bgu.ac.il");
        existingUser.setFirstName("Test");
        existingUser.setLastName("User");
        existingUser.setDegree(User.Degree.MSc);
        existingUser.setIsPresenter(true);
        existingUser.setIsParticipant(true);
        existingUser.setSupervisor(existingSupervisor);
    }

    // ==================== POST /api/v1/auth/login ====================

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Login success - existing user with presenter and participant roles")
        void login_ExistingUser_Success() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("testuser", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(existingUser));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.message").value("Login successful"))
                    .andExpect(jsonPath("$.bguUsername").value("testuser"))
                    .andExpect(jsonPath("$.email").value("testuser@bgu.ac.il"))
                    .andExpect(jsonPath("$.firstTime").value(false))
                    .andExpect(jsonPath("$.presenter").value(true))
                    .andExpect(jsonPath("$.participant").value(true));

            verify(bguAuthSoapService).validateUser("testuser", "password123");
            verify(userRepository).findByBguUsername("testuser");
        }

        @Test
        @DisplayName("Login success - first time user (not in database)")
        void login_FirstTimeUser_Success() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("newuser");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("newuser", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("newuser")).thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.message").value("Login successful"))
                    .andExpect(jsonPath("$.bguUsername").value("newuser"))
                    .andExpect(jsonPath("$.email").value("newuser@bgu.ac.il"))
                    .andExpect(jsonPath("$.firstTime").value(true))
                    .andExpect(jsonPath("$.presenter").value(false))
                    .andExpect(jsonPath("$.participant").value(false));
        }

        @Test
        @DisplayName("Login success - username with email domain is normalized")
        void login_UsernameWithDomain_IsNormalized() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("TestUser@bgu.ac.il");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("TestUser@bgu.ac.il", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(existingUser));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.bguUsername").value("testuser"))
                    .andExpect(jsonPath("$.email").value("testuser@bgu.ac.il"));

            verify(userRepository).findByBguUsername("testuser");
        }

        @Test
        @DisplayName("Login success - test user bypasses BGU SOAP validation")
        void login_TestUserBypass_Success() throws Exception {
            // Given - testmsc1 is a special test user that bypasses BGU SOAP
            LoginRequest request = new LoginRequest();
            request.setUsername("testmsc1");
            request.setPassword("Test123!");

            when(userRepository.findByBguUsername("testmsc1")).thenReturn(Optional.empty());

            // When & Then - Should succeed without calling BGU SOAP
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.bguUsername").value("testmsc1"));

            // BGU SOAP should NOT be called for test users
            verify(bguAuthSoapService, never()).validateUser(anyString(), anyString());
        }

        @Test
        @DisplayName("Login failure - invalid credentials")
        void login_InvalidCredentials_ReturnsUnauthorized() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("wrongpassword");

            when(bguAuthSoapService.validateUser("testuser", "wrongpassword")).thenReturn(false);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));

            verify(bguAuthSoapService).validateUser("testuser", "wrongpassword");
            verify(userRepository, never()).findByBguUsername(anyString());
        }

        @Test
        @DisplayName("Login failure - null username")
        void login_NullUsername_ReturnsBadRequest() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername(null);
            request.setPassword("password123");

            // When & Then - Spring validation should catch this
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Login failure - empty username")
        void login_EmptyUsername_ReturnsBadRequest() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("   ");
            request.setPassword("password123");

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Login failure - null password")
        void login_NullPassword_ReturnsBadRequest() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword(null);

            // When & Then - Spring validation should catch this
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Login failure - empty password")
        void login_EmptyPassword_ReturnsBadRequest() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("");

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Login failure - null request body")
        void login_NullBody_ReturnsBadRequest() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("null"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Login failure - BGU SOAP service throws exception")
        void login_SoapServiceException_ReturnsInternalServerError() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("testuser", "password123"))
                    .thenThrow(new RuntimeException("SOAP service unavailable"));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.message").value("Authentication service unavailable"));
        }

        @Test
        @DisplayName("Login success - user without supervisor")
        void login_UserWithoutSupervisor_Success() throws Exception {
            // Given
            User userWithoutSupervisor = new User();
            userWithoutSupervisor.setId(2L);
            userWithoutSupervisor.setBguUsername("nosupervisor");
            userWithoutSupervisor.setEmail("nosupervisor@bgu.ac.il");
            userWithoutSupervisor.setIsPresenter(false);
            userWithoutSupervisor.setIsParticipant(true);
            userWithoutSupervisor.setSupervisor(null);

            LoginRequest request = new LoginRequest();
            request.setUsername("nosupervisor");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("nosupervisor", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("nosupervisor")).thenReturn(Optional.of(userWithoutSupervisor));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.firstTime").value(false))
                    .andExpect(jsonPath("$.presenter").value(false))
                    .andExpect(jsonPath("$.participant").value(true));
        }

        @Test
        @DisplayName("Login - uppercase username is normalized to lowercase")
        void login_UppercaseUsername_IsNormalized() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("TESTUSER");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("TESTUSER", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(existingUser));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bguUsername").value("testuser"));
        }

        @Test
        @DisplayName("Login - PhD test users bypass authentication")
        void login_PhdTestUser_BypassesAuthentication() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testphd1");
            request.setPassword("Test123!");

            when(userRepository.findByBguUsername("testphd1")).thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));

            verify(bguAuthSoapService, never()).validateUser(anyString(), anyString());
        }

        @Test
        @DisplayName("Login - test user with wrong password fails")
        void login_TestUserWrongPassword_Fails() throws Exception {
            // Given - test users must use exact password "Test123!"
            LoginRequest request = new LoginRequest();
            request.setUsername("testmsc1");
            request.setPassword("WrongPassword");

            // The test bypass only works with correct password, so it falls through to BGU SOAP
            when(bguAuthSoapService.validateUser("testmsc1", "WrongPassword")).thenReturn(false);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.ok").value(false));
        }
    }

    // ==================== POST /api/v1/auth/setup ====================

    @Nested
    @DisplayName("POST /api/v1/auth/setup")
    class SetupWithoutUsernameTests {

        @Test
        @DisplayName("Setup without username path returns bad request")
        void setup_WithoutUsername_ReturnsBadRequest() throws Exception {
            // Given
            AccountSetupRequest request = new AccountSetupRequest();
            request.setSupervisorName("Dr. Test");
            request.setSupervisorEmail("supervisor@bgu.ac.il");

            // When & Then - Endpoint without username in path returns error
            mockMvc.perform(post("/api/v1/auth/setup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Username not provided"));
        }
    }

    // ==================== POST /api/v1/auth/setup/{username} ====================

    @Nested
    @DisplayName("POST /api/v1/auth/setup/{username}")
    class SetupAccountForUserTests {

        @Test
        @DisplayName("Account setup success - new supervisor created")
        void setupAccount_NewSupervisor_Success() throws Exception {
            // Given
            User userWithoutSupervisor = new User();
            userWithoutSupervisor.setId(2L);
            userWithoutSupervisor.setBguUsername("testuser");
            userWithoutSupervisor.setEmail("testuser@bgu.ac.il");
            userWithoutSupervisor.setSupervisor(null);

            AccountSetupRequest request = new AccountSetupRequest();
            request.setSupervisorName("Dr. New Supervisor");
            request.setSupervisorEmail("newsupervisor@bgu.ac.il");

            Supervisor newSupervisor = new Supervisor();
            newSupervisor.setId(2L);
            newSupervisor.setName("Dr. New Supervisor");
            newSupervisor.setEmail("newsupervisor@bgu.ac.il");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(userWithoutSupervisor));
            when(supervisorRepository.findByEmail("newsupervisor@bgu.ac.il")).thenReturn(Optional.empty());
            when(supervisorRepository.save(any(Supervisor.class))).thenReturn(newSupervisor);
            when(userRepository.save(any(User.class))).thenReturn(userWithoutSupervisor);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/setup/testuser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Account setup completed successfully"))
                    .andExpect(jsonPath("$.supervisorName").value("Dr. New Supervisor"))
                    .andExpect(jsonPath("$.supervisorEmail").value("newsupervisor@bgu.ac.il"));

            verify(supervisorRepository).save(any(Supervisor.class));
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Account setup success - existing supervisor used")
        void setupAccount_ExistingSupervisor_Success() throws Exception {
            // Given
            User userWithoutSupervisor = new User();
            userWithoutSupervisor.setId(2L);
            userWithoutSupervisor.setBguUsername("testuser");
            userWithoutSupervisor.setEmail("testuser@bgu.ac.il");
            userWithoutSupervisor.setSupervisor(null);

            AccountSetupRequest request = new AccountSetupRequest();
            request.setSupervisorName("Dr. Test Supervisor");
            request.setSupervisorEmail("supervisor@bgu.ac.il");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(userWithoutSupervisor));
            when(supervisorRepository.findByEmail("supervisor@bgu.ac.il")).thenReturn(Optional.of(existingSupervisor));
            when(userRepository.save(any(User.class))).thenReturn(userWithoutSupervisor);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/setup/testuser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.supervisorName").value("Dr. Test Supervisor"))
                    .andExpect(jsonPath("$.supervisorEmail").value("supervisor@bgu.ac.il"));

            // Should NOT create new supervisor
            verify(supervisorRepository, never()).save(any(Supervisor.class));
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Account setup - user already has supervisor (idempotent)")
        void setupAccount_AlreadyHasSupervisor_ReturnsSuccess() throws Exception {
            // Given - existing user already has supervisor
            AccountSetupRequest request = new AccountSetupRequest();
            request.setSupervisorName("Dr. Different");
            request.setSupervisorEmail("different@bgu.ac.il");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(existingUser));

            // When & Then - Should return success with existing supervisor info
            mockMvc.perform(post("/api/v1/auth/setup/testuser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Account already set up"))
                    .andExpect(jsonPath("$.supervisorName").value("Dr. Test Supervisor"))
                    .andExpect(jsonPath("$.supervisorEmail").value("supervisor@bgu.ac.il"));

            // Should NOT update anything
            verify(supervisorRepository, never()).save(any(Supervisor.class));
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Account setup failure - user not found")
        void setupAccount_UserNotFound_ReturnsNotFound() throws Exception {
            // Given
            AccountSetupRequest request = new AccountSetupRequest();
            request.setSupervisorName("Dr. Test");
            request.setSupervisorEmail("supervisor@bgu.ac.il");

            when(userRepository.findByBguUsername("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(post("/api/v1/auth/setup/nonexistent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("User not found"));
        }

        @Test
        @DisplayName("Account setup failure - empty username")
        void setupAccount_EmptyUsername_ReturnsBadRequest() throws Exception {
            // Given
            AccountSetupRequest request = new AccountSetupRequest();
            request.setSupervisorName("Dr. Test");
            request.setSupervisorEmail("supervisor@bgu.ac.il");

            // When & Then
            mockMvc.perform(post("/api/v1/auth/setup/   ")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid username"));
        }

        @Test
        @DisplayName("Account setup failure - missing supervisor name")
        void setupAccount_MissingSupervisorName_ReturnsBadRequest() throws Exception {
            // Given
            AccountSetupRequest request = new AccountSetupRequest();
            request.setSupervisorName(null);
            request.setSupervisorEmail("supervisor@bgu.ac.il");

            // When & Then - Spring validation should catch this
            mockMvc.perform(post("/api/v1/auth/setup/testuser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Account setup failure - missing supervisor email")
        void setupAccount_MissingSupervisorEmail_ReturnsBadRequest() throws Exception {
            // Given
            AccountSetupRequest request = new AccountSetupRequest();
            request.setSupervisorName("Dr. Test");
            request.setSupervisorEmail(null);

            // When & Then - Spring validation should catch this
            mockMvc.perform(post("/api/v1/auth/setup/testuser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Account setup failure - invalid supervisor email format")
        void setupAccount_InvalidEmailFormat_ReturnsBadRequest() throws Exception {
            // Given
            AccountSetupRequest request = new AccountSetupRequest();
            request.setSupervisorName("Dr. Test");
            request.setSupervisorEmail("not-an-email");

            // When & Then - @Email validation should catch this
            mockMvc.perform(post("/api/v1/auth/setup/testuser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Account setup - username with domain is normalized")
        void setupAccount_UsernameWithDomain_IsNormalized() throws Exception {
            // Given
            User userWithoutSupervisor = new User();
            userWithoutSupervisor.setId(2L);
            userWithoutSupervisor.setBguUsername("testuser");
            userWithoutSupervisor.setSupervisor(null);

            AccountSetupRequest request = new AccountSetupRequest();
            request.setSupervisorName("Dr. Test");
            request.setSupervisorEmail("supervisor@bgu.ac.il");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(userWithoutSupervisor));
            when(supervisorRepository.findByEmail("supervisor@bgu.ac.il")).thenReturn(Optional.of(existingSupervisor));
            when(userRepository.save(any(User.class))).thenReturn(userWithoutSupervisor);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/setup/TESTUSER@bgu.ac.il")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(userRepository).findByBguUsername("testuser");
        }

        @Test
        @DisplayName("Account setup failure - database error")
        void setupAccount_DatabaseError_ReturnsInternalServerError() throws Exception {
            // Given
            User userWithoutSupervisor = new User();
            userWithoutSupervisor.setId(2L);
            userWithoutSupervisor.setBguUsername("testuser");
            userWithoutSupervisor.setSupervisor(null);

            AccountSetupRequest request = new AccountSetupRequest();
            request.setSupervisorName("Dr. Test");
            request.setSupervisorEmail("supervisor@bgu.ac.il");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(userWithoutSupervisor));
            when(supervisorRepository.findByEmail("supervisor@bgu.ac.il")).thenReturn(Optional.of(existingSupervisor));
            when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Database error"));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/setup/testuser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Account setup failed"));
        }

        @Test
        @DisplayName("Account setup - supervisor email is normalized to lowercase")
        void setupAccount_SupervisorEmailUppercase_IsNormalized() throws Exception {
            // Given
            User userWithoutSupervisor = new User();
            userWithoutSupervisor.setId(2L);
            userWithoutSupervisor.setBguUsername("testuser");
            userWithoutSupervisor.setSupervisor(null);

            AccountSetupRequest request = new AccountSetupRequest();
            request.setSupervisorName("Dr. Test");
            request.setSupervisorEmail("SUPERVISOR@BGU.AC.IL");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(userWithoutSupervisor));
            when(supervisorRepository.findByEmail("supervisor@bgu.ac.il")).thenReturn(Optional.of(existingSupervisor));
            when(userRepository.save(any(User.class))).thenReturn(userWithoutSupervisor);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/setup/testuser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Should search with lowercase email
            verify(supervisorRepository).findByEmail("supervisor@bgu.ac.il");
        }
    }

    // ==================== Username Normalization Tests ====================

    @Nested
    @DisplayName("Username Normalization")
    class UsernameNormalizationTests {

        @Test
        @DisplayName("Username with leading/trailing spaces is trimmed")
        void login_UsernameWithSpaces_IsTrimmed() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("  testuser  ");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("  testuser  ", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(existingUser));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bguUsername").value("testuser"));
        }

        @Test
        @DisplayName("Mixed case username is lowercased")
        void login_MixedCaseUsername_IsLowercased() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("TestUser");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("TestUser", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(existingUser));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bguUsername").value("testuser"));
        }

        @Test
        @DisplayName("Username with full BGU email domain is stripped")
        void login_FullBguEmail_DomainStripped() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser@post.bgu.ac.il");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("testuser@post.bgu.ac.il", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(existingUser));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bguUsername").value("testuser"));
        }
    }

    // ==================== Database Logger Integration Tests ====================

    @Nested
    @DisplayName("Database Logger Integration")
    class DatabaseLoggerTests {

        @Test
        @DisplayName("Login attempt is logged to database")
        void login_LogsAttemptToDatabase() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("testuser", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(existingUser));

            // When
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Then
            verify(databaseLoggerService).logAction(
                    eq("INFO"),
                    eq("LOGIN_ATTEMPT"),
                    contains("testuser"),
                    eq("testuser"),
                    anyString()
            );
        }

        @Test
        @DisplayName("Login success is logged to database")
        void login_LogsSuccessToDatabase() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("testuser", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(existingUser));

            // When
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Then
            verify(databaseLoggerService).logBusinessEvent(
                    eq("LOGIN_SUCCESS"),
                    contains("testuser"),
                    eq("testuser")
            );
        }

        @Test
        @DisplayName("Login failure is logged to database")
        void login_LogsFailureToDatabase() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("wrongpassword");

            when(bguAuthSoapService.validateUser("testuser", "wrongpassword")).thenReturn(false);

            // When
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            // Then
            verify(databaseLoggerService).logAction(
                    eq("WARN"),
                    eq("LOGIN_FAILED"),
                    contains("Invalid credentials"),
                    eq("testuser"),
                    contains("INVALID_CREDENTIALS")
            );
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Login with department field (optional)")
        void login_WithDepartment_Success() throws Exception {
            // Given
            String requestJson = """
                {
                    "username": "testuser",
                    "password": "password123",
                    "department": 1
                }
                """;

            when(bguAuthSoapService.validateUser("testuser", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(existingUser));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
        }

        @Test
        @DisplayName("Login response includes correct email format")
        void login_FirstTimeUser_EmailFormat() throws Exception {
            // Given - user without @domain should get @bgu.ac.il appended
            LoginRequest request = new LoginRequest();
            request.setUsername("newuser");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("newuser", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("newuser")).thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("newuser@bgu.ac.il"));
        }

        @Test
        @DisplayName("Login with email domain preserves original email")
        void login_WithEmailDomain_PreservesEmail() throws Exception {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser@post.bgu.ac.il");
            request.setPassword("password123");

            when(bguAuthSoapService.validateUser("testuser@post.bgu.ac.il", "password123")).thenReturn(true);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("testuser@post.bgu.ac.il"));
        }
    }
}
