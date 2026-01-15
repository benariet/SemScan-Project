package edu.bgu.semscanapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bgu.semscanapi.dto.FcmTokenRequest;
import edu.bgu.semscanapi.dto.UserExistsRequest;
import edu.bgu.semscanapi.dto.UserProfileUpdateRequest;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.service.DatabaseLoggerService;
import edu.bgu.semscanapi.service.FcmService;
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
 * Unit tests for UserController
 * Tests API endpoints for user profile operations
 */
@WebMvcTest(controllers = UserController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private FcmService fcmService;

    @MockBean
    private DatabaseLoggerService databaseLoggerService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setBguUsername("testuser");
        testUser.setEmail("testuser@bgu.ac.il");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setDegree(User.Degree.MSc);
        testUser.setIsPresenter(true);
        testUser.setIsParticipant(true);
        testUser.setNationalIdNumber("123456789");
    }

    // ==================== POST /api/v1/users (Upsert User) ====================

    @Nested
    @DisplayName("POST /api/v1/users - Upsert User")
    class UpsertUserTests {

        @Test
        @DisplayName("Creates new user when user does not exist")
        void upsertUser_CreatesNewUser_WhenUserDoesNotExist() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setBguUsername("newuser");
            request.setEmail("newuser@bgu.ac.il");

            when(userRepository.findByBguUsername("newuser")).thenReturn(Optional.empty());
            when(userRepository.findByBguUsernameIgnoreCase("newuser")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(2L);
                return saved;
            });

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.bguUsername").value("newuser"))
                    .andExpect(jsonPath("$.email").value("newuser@bgu.ac.il"))
                    .andExpect(jsonPath("$.firstName").value("Test"))
                    .andExpect(jsonPath("$.lastName").value("User"))
                    .andExpect(jsonPath("$.degree").value("MSc"));

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Updates existing user successfully")
        void upsertUser_UpdatesExistingUser_Success() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setFirstName("UpdatedFirst");
            request.setLastName("UpdatedLast");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.bguUsername").value("testuser"))
                    .andExpect(jsonPath("$.message").value("Profile updated"));

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Returns no changes when profile is unchanged")
        void upsertUser_ReturnsNoChanges_WhenProfileUnchanged() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            // Request matches existing user exactly

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Returns 400 when bguUsername is missing")
        void upsertUser_Returns400_WhenBguUsernameMissing() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setBguUsername(null);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when bguUsername is empty")
        void upsertUser_Returns400_WhenBguUsernameEmpty() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setBguUsername("   ");

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when email is invalid")
        void upsertUser_Returns400_WhenEmailInvalid() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setEmail("invalid-email");

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when firstName is missing")
        void upsertUser_Returns400_WhenFirstNameMissing() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setFirstName(null);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when lastName is missing")
        void upsertUser_Returns400_WhenLastNameMissing() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setLastName(null);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when degree is missing")
        void upsertUser_Returns400_WhenDegreeMissing() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setDegree(null);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Normalizes username to lowercase")
        void upsertUser_NormalizesUsernameToLowercase() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setBguUsername("TESTUSER");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.bguUsername").value("testuser"));

            verify(userRepository).findByBguUsername("testuser");
        }

        @Test
        @DisplayName("Updates user with PhD degree")
        void upsertUser_UpdatesWithPhdDegree() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setDegree(User.Degree.PhD);

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Updates participation preference to PRESENTER_ONLY")
        void upsertUser_UpdatesParticipationPreference_PresenterOnly() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setParticipationPreference(UserProfileUpdateRequest.ParticipationPreference.PRESENTER_ONLY);

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Updates participation preference to PARTICIPANT_ONLY")
        void upsertUser_UpdatesParticipationPreference_ParticipantOnly() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setParticipationPreference(UserProfileUpdateRequest.ParticipationPreference.PARTICIPANT_ONLY);

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Updates national ID number")
        void upsertUser_UpdatesNationalIdNumber() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setNationalIdNumber("987654321");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Updates seminar abstract")
        void upsertUser_UpdatesSeminarAbstract() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setSeminarAbstract("This is my seminar abstract about machine learning.");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Returns 500 on database error")
        void upsertUser_Returns500_OnDatabaseError() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            // Change something to trigger save()
            request.setFirstName("ChangedName");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Database error"));

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Failed to update profile")));
        }

        @Test
        @DisplayName("Returns error when email already exists for another user")
        void upsertUser_ReturnsError_WhenEmailExistsForAnotherUser() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setEmail("existing@bgu.ac.il");

            User anotherUser = new User();
            anotherUser.setId(2L);
            anotherUser.setBguUsername("anotheruser");
            anotherUser.setEmail("existing@bgu.ac.il");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.findByEmail("existing@bgu.ac.il")).thenReturn(Optional.of(anotherUser));

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== POST /api/v1/users/exists ====================

    @Nested
    @DisplayName("POST /api/v1/users/exists - Check User Exists")
    class UserExistsTests {

        @Test
        @DisplayName("Returns true when user exists by username")
        void userExists_ReturnsTrue_WhenUserExistsByUsername() throws Exception {
            // Given
            UserExistsRequest request = new UserExistsRequest();
            request.setUsername("testuser");

            when(userRepository.existsByBguUsernameIgnoreCase("testuser")).thenReturn(true);

            // When & Then
            mockMvc.perform(post("/api/v1/users/exists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true));
        }

        @Test
        @DisplayName("Returns true when user exists by email")
        void userExists_ReturnsTrue_WhenUserExistsByEmail() throws Exception {
            // Given
            UserExistsRequest request = new UserExistsRequest();
            request.setUsername("testuser@bgu.ac.il");

            when(userRepository.existsByBguUsernameIgnoreCase("testuser@bgu.ac.il")).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase("testuser@bgu.ac.il")).thenReturn(true);

            // When & Then
            mockMvc.perform(post("/api/v1/users/exists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true));
        }

        @Test
        @DisplayName("Returns true when user exists by assumed email")
        void userExists_ReturnsTrue_WhenUserExistsByAssumedEmail() throws Exception {
            // Given
            UserExistsRequest request = new UserExistsRequest();
            request.setUsername("testuser");

            when(userRepository.existsByBguUsernameIgnoreCase("testuser")).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase("testuser@bgu.ac.il")).thenReturn(true);

            // When & Then
            mockMvc.perform(post("/api/v1/users/exists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true));
        }

        @Test
        @DisplayName("Returns false when user does not exist")
        void userExists_ReturnsFalse_WhenUserDoesNotExist() throws Exception {
            // Given
            UserExistsRequest request = new UserExistsRequest();
            request.setUsername("nonexistent");

            when(userRepository.existsByBguUsernameIgnoreCase(anyString())).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
            when(userRepository.findByBguUsername(anyString())).thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(post("/api/v1/users/exists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(false));
        }

        @Test
        @DisplayName("Returns 400 when username is missing")
        void userExists_Returns400_WhenUsernameMissing() throws Exception {
            // Given
            UserExistsRequest request = new UserExistsRequest();
            request.setUsername(null);

            // When & Then
            mockMvc.perform(post("/api/v1/users/exists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when username is blank")
        void userExists_Returns400_WhenUsernameBlank() throws Exception {
            // Given
            UserExistsRequest request = new UserExistsRequest();
            request.setUsername("   ");

            // When & Then
            mockMvc.perform(post("/api/v1/users/exists")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== GET /api/v1/users/username/{username} ====================

    @Nested
    @DisplayName("GET /api/v1/users/username/{username} - Get User by Username")
    class GetUserByUsernameTests {

        @Test
        @DisplayName("Returns user profile successfully")
        void getUserByUsername_ReturnsProfile_Success() throws Exception {
            // Given
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));

            // When & Then
            mockMvc.perform(get("/api/v1/users/username/testuser")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.bguUsername").value("testuser"))
                    .andExpect(jsonPath("$.email").value("testuser@bgu.ac.il"))
                    .andExpect(jsonPath("$.firstName").value("Test"))
                    .andExpect(jsonPath("$.lastName").value("User"))
                    .andExpect(jsonPath("$.degree").value("MSc"))
                    .andExpect(jsonPath("$.nationalIdNumber").value("123456789"))
                    .andExpect(jsonPath("$.participationPreference").value("BOTH"));
        }

        @Test
        @DisplayName("Returns 404 when user not found")
        void getUserByUsername_Returns404_WhenUserNotFound() throws Exception {
            // Given
            when(userRepository.findByBguUsername(anyString())).thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(get("/api/v1/users/username/nonexistent")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Returns 400 when username is empty")
        void getUserByUsername_Returns400_WhenUsernameEmpty() throws Exception {
            // When & Then - Spring MVC will return 404 for empty path variable
            mockMvc.perform(get("/api/v1/users/username/   ")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Normalizes username to lowercase")
        void getUserByUsername_NormalizesUsername() throws Exception {
            // Given
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));

            // When & Then
            mockMvc.perform(get("/api/v1/users/username/TESTUSER")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bguUsername").value("testuser"));

            verify(userRepository).findByBguUsername("testuser");
        }

        @Test
        @DisplayName("Returns user with presenter only preference")
        void getUserByUsername_ReturnsPresenterOnlyPreference() throws Exception {
            // Given
            testUser.setIsPresenter(true);
            testUser.setIsParticipant(false);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));

            // When & Then
            mockMvc.perform(get("/api/v1/users/username/testuser")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participationPreference").value("PRESENTER_ONLY"));
        }

        @Test
        @DisplayName("Returns user with participant only preference")
        void getUserByUsername_ReturnsParticipantOnlyPreference() throws Exception {
            // Given
            testUser.setIsPresenter(false);
            testUser.setIsParticipant(true);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));

            // When & Then
            mockMvc.perform(get("/api/v1/users/username/testuser")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participationPreference").value("PARTICIPANT_ONLY"));
        }

        @Test
        @DisplayName("Returns null participation preference when both false")
        void getUserByUsername_ReturnsNullPreference_WhenBothFalse() throws Exception {
            // Given
            testUser.setIsPresenter(false);
            testUser.setIsParticipant(false);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));

            // When & Then
            mockMvc.perform(get("/api/v1/users/username/testuser")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participationPreference").doesNotExist());
        }

        @Test
        @DisplayName("Returns user with seminar abstract")
        void getUserByUsername_ReturnsWithSeminarAbstract() throws Exception {
            // Given
            testUser.setSeminarAbstract("My research focuses on AI.");
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));

            // When & Then
            mockMvc.perform(get("/api/v1/users/username/testuser")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seminarAbstract").value("My research focuses on AI."));
        }
    }

    // ==================== POST /api/v1/users/{username}/fcm-token ====================

    @Nested
    @DisplayName("POST /api/v1/users/{username}/fcm-token - Register FCM Token")
    class RegisterFcmTokenTests {

        @Test
        @DisplayName("Registers FCM token successfully")
        void registerFcmToken_Success() throws Exception {
            // Given
            FcmTokenRequest request = new FcmTokenRequest("fcm_token_123456", "Samsung Galaxy S21");
            doNothing().when(fcmService).registerToken(anyString(), anyString(), anyString());

            // When & Then
            mockMvc.perform(post("/api/v1/users/testuser/fcm-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("FCM token registered successfully"));

            verify(fcmService).registerToken("testuser", "fcm_token_123456", "Samsung Galaxy S21");
        }

        @Test
        @DisplayName("Returns 400 when FCM token is missing")
        void registerFcmToken_Returns400_WhenTokenMissing() throws Exception {
            // Given
            FcmTokenRequest request = new FcmTokenRequest();
            request.setFcmToken(null);
            request.setDeviceInfo("Samsung Galaxy S21");

            // When & Then
            mockMvc.perform(post("/api/v1/users/testuser/fcm-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when FCM token is empty")
        void registerFcmToken_Returns400_WhenTokenEmpty() throws Exception {
            // Given
            FcmTokenRequest request = new FcmTokenRequest("", "Samsung Galaxy S21");

            // When & Then
            mockMvc.perform(post("/api/v1/users/testuser/fcm-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when username is empty")
        void registerFcmToken_Returns400_WhenUsernameEmpty() throws Exception {
            // Given
            FcmTokenRequest request = new FcmTokenRequest("fcm_token_123456", "Samsung Galaxy S21");

            // When & Then
            mockMvc.perform(post("/api/v1/users/   /fcm-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Username is required"));
        }

        @Test
        @DisplayName("Registers FCM token without device info")
        void registerFcmToken_Success_WithoutDeviceInfo() throws Exception {
            // Given
            FcmTokenRequest request = new FcmTokenRequest();
            request.setFcmToken("fcm_token_123456");
            request.setDeviceInfo(null);
            doNothing().when(fcmService).registerToken(anyString(), anyString(), any());

            // When & Then
            mockMvc.perform(post("/api/v1/users/testuser/fcm-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(fcmService).registerToken("testuser", "fcm_token_123456", null);
        }

        @Test
        @DisplayName("Returns 500 on service error")
        void registerFcmToken_Returns500_OnServiceError() throws Exception {
            // Given
            FcmTokenRequest request = new FcmTokenRequest("fcm_token_123456", "Samsung Galaxy S21");
            doThrow(new RuntimeException("Firebase error")).when(fcmService).registerToken(anyString(), anyString(), anyString());

            // When & Then
            mockMvc.perform(post("/api/v1/users/testuser/fcm-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Failed to register FCM token")));
        }
    }

    // ==================== DELETE /api/v1/users/{username}/fcm-token ====================

    @Nested
    @DisplayName("DELETE /api/v1/users/{username}/fcm-token - Remove FCM Token")
    class RemoveFcmTokenTests {

        @Test
        @DisplayName("Removes FCM token successfully")
        void removeFcmToken_Success() throws Exception {
            // Given
            doNothing().when(fcmService).removeToken(anyString());

            // When & Then
            mockMvc.perform(delete("/api/v1/users/testuser/fcm-token")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("FCM token removed successfully"));

            verify(fcmService).removeToken("testuser");
        }

        @Test
        @DisplayName("Returns 400 when username is empty")
        void removeFcmToken_Returns400_WhenUsernameEmpty() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/v1/users/   /fcm-token")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Username is required"));
        }

        @Test
        @DisplayName("Returns 500 on service error")
        void removeFcmToken_Returns500_OnServiceError() throws Exception {
            // Given
            doThrow(new RuntimeException("Database error")).when(fcmService).removeToken(anyString());

            // When & Then
            mockMvc.perform(delete("/api/v1/users/testuser/fcm-token")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Failed to remove FCM token")));
        }
    }

    // ==================== Edge Cases and Validation Tests ====================

    @Nested
    @DisplayName("Edge Cases and Validation")
    class EdgeCasesTests {

        @Test
        @DisplayName("Handles whitespace in username")
        void upsertUser_HandlesWhitespaceInUsername() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setBguUsername("  testuser  ");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Handles mixed case username lookup")
        void getUserByUsername_HandlesMixedCaseUsername() throws Exception {
            // Given - try with uppercase, find with lowercase
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));

            // When & Then
            mockMvc.perform(get("/api/v1/users/username/TestUser")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bguUsername").value("testuser"));
        }

        @Test
        @DisplayName("Handles user with null degree")
        void getUserByUsername_HandlesNullDegree() throws Exception {
            // Given
            testUser.setDegree(null);
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));

            // When & Then
            mockMvc.perform(get("/api/v1/users/username/testuser")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.degree").doesNotExist());
        }

        @Test
        @DisplayName("Handles empty request body for upsert")
        void upsertUser_Returns400_WhenRequestBodyEmpty() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Handles null request body for upsert")
        void upsertUser_Returns400_WhenRequestBodyNull() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("null"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Updates email to normalized lowercase")
        void upsertUser_NormalizesEmailToLowercase() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setEmail("TestUser@BGU.AC.IL");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.findByEmail("testuser@bgu.ac.il")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Trims first name and last name")
        void upsertUser_TrimsNames() throws Exception {
            // Given
            UserProfileUpdateRequest request = createValidRequest();
            request.setFirstName("  John  ");
            request.setLastName("  Doe  ");

            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When & Then
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(userRepository).save(argThat(user ->
                "John".equals(user.getFirstName()) && "Doe".equals(user.getLastName())
            ));
        }
    }

    // ==================== Helper Methods ====================

    private UserProfileUpdateRequest createValidRequest() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setBguUsername("testuser");
        request.setEmail("testuser@bgu.ac.il");
        request.setFirstName("Test");
        request.setLastName("User");
        request.setDegree(User.Degree.MSc);
        request.setParticipationPreference(UserProfileUpdateRequest.ParticipationPreference.BOTH);
        request.setNationalIdNumber("123456789");
        return request;
    }
}
