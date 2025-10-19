package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.PresenterApiKey;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.PresenterApiKeyRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Simple test for AuthenticationService
 * Tests basic authentication functionality
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private PresenterApiKeyRepository presenterApiKeyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthenticationService authenticationService;

    private User testUser;
    private PresenterApiKey testApiKey;

    @BeforeEach
    void setUp() {
        // Setup test user
        testUser = new User();
        testUser.setUserId("presenter-001");
        testUser.setEmail("presenter@example.com");
        testUser.setFirstName("John");
        testUser.setLastName("Presenter");
        testUser.setRole(User.UserRole.PRESENTER);

        // Setup test API key
        testApiKey = new PresenterApiKey();
        testApiKey.setApiKeyId("key-001");
        testApiKey.setApiKey("test-api-key-12345");
        testApiKey.setPresenterId("presenter-001");
        testApiKey.setIsActive(true);
        testApiKey.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void validateApiKey_WithValidKey_ShouldReturnUser() {
        // Given
        when(presenterApiKeyRepository.findActiveApiKey("test-api-key-12345"))
                .thenReturn(Optional.of(testApiKey));
        when(userRepository.findById("presenter-001"))
                .thenReturn(Optional.of(testUser));

        // When
        Optional<User> result = authenticationService.validateApiKey("test-api-key-12345");

        // Then
        assertTrue(result.isPresent());
        assertEquals("presenter-001", result.get().getUserId());
        assertEquals("presenter@example.com", result.get().getEmail());
        assertEquals(User.UserRole.PRESENTER, result.get().getRole());
    }

    @Test
    void validateApiKey_WithInvalidKey_ShouldReturnEmpty() {
        // Given
        when(presenterApiKeyRepository.findActiveApiKey("invalid-key"))
                .thenReturn(Optional.empty());

        // When
        Optional<User> result = authenticationService.validateApiKey("invalid-key");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void validateApiKey_WithInactiveKey_ShouldReturnEmpty() {
        // Given
        testApiKey.setIsActive(false);
        when(presenterApiKeyRepository.findActiveApiKey("inactive-key"))
                .thenReturn(Optional.empty());

        // When
        Optional<User> result = authenticationService.validateApiKey("inactive-key");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void validateApiKey_WithNullKey_ShouldReturnEmpty() {
        // When
        Optional<User> result = authenticationService.validateApiKey(null);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void validateApiKey_WithEmptyKey_ShouldReturnEmpty() {
        // When
        Optional<User> result = authenticationService.validateApiKey("");

        // Then
        assertFalse(result.isPresent());
    }
}
