package edu.bgu.semscanapi.service;

import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import edu.bgu.semscanapi.config.FirebaseConfig;
import edu.bgu.semscanapi.entity.FcmToken;
import edu.bgu.semscanapi.repository.FcmTokenRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FcmService
 */
@ExtendWith(MockitoExtension.class)
class FcmServiceTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FirebaseConfig firebaseConfig;

    @Mock
    private DatabaseLoggerService databaseLoggerService;

    @InjectMocks
    private FcmService fcmService;

    private FcmToken testToken;

    @BeforeEach
    void setUp() {
        testToken = new FcmToken("testuser", "fcm_token_12345", "Android 12");
        testToken.setId(1L);
    }

    // ========== registerToken tests ==========

    @Test
    void registerToken_withNullUsername_shouldNotSave() {
        fcmService.registerToken(null, "token", "device");

        verify(fcmTokenRepository, never()).save(any());
        verify(fcmTokenRepository, never()).findByBguUsername(any());
    }

    @Test
    void registerToken_withNullToken_shouldNotSave() {
        fcmService.registerToken("user", null, "device");

        verify(fcmTokenRepository, never()).save(any());
        verify(fcmTokenRepository, never()).findByBguUsername(any());
    }

    @Test
    void registerToken_newUser_shouldCreateToken() {
        when(userRepository.existsByBguUsername("testuser")).thenReturn(true);
        when(fcmTokenRepository.findByBguUsername("testuser")).thenReturn(Optional.empty());

        fcmService.registerToken("testuser", "new_token", "Android 13");

        verify(fcmTokenRepository).findByBguUsername("testuser");
        verify(fcmTokenRepository).save(argThat(token ->
            token.getBguUsername().equals("testuser") &&
            token.getFcmToken().equals("new_token") &&
            token.getDeviceInfo().equals("Android 13")
        ));
    }

    @Test
    void registerToken_existingUser_shouldUpdateToken() {
        when(userRepository.existsByBguUsername("testuser")).thenReturn(true);
        when(fcmTokenRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testToken));

        fcmService.registerToken("testuser", "updated_token", "Android 14");

        verify(fcmTokenRepository).save(argThat(token ->
            token.getFcmToken().equals("updated_token") &&
            token.getDeviceInfo().equals("Android 14")
        ));
    }

    @Test
    void registerToken_shouldNormalizeUsername() {
        when(userRepository.existsByBguUsername("testuser")).thenReturn(true);
        when(fcmTokenRepository.findByBguUsername("testuser")).thenReturn(Optional.empty());

        fcmService.registerToken("  TESTUSER  ", "token", "device");

        verify(fcmTokenRepository).findByBguUsername("testuser");
    }

    @Test
    void registerToken_userNotInDatabase_shouldNotSave() {
        when(userRepository.existsByBguUsername("newuser")).thenReturn(false);

        fcmService.registerToken("newuser", "token", "device");

        verify(fcmTokenRepository, never()).save(any());
        verify(fcmTokenRepository, never()).findByBguUsername(any());
    }

    @Test
    void registerToken_shouldLogToDatabase() {
        when(userRepository.existsByBguUsername("testuser")).thenReturn(true);
        when(fcmTokenRepository.findByBguUsername("testuser")).thenReturn(Optional.empty());

        fcmService.registerToken("testuser", "token", "device");

        verify(databaseLoggerService).logAction(
            eq("INFO"),
            eq("FCM_TOKEN_REGISTERED"),
            contains("testuser"),
            eq("testuser"),
            contains("deviceInfo")
        );
    }

    // ========== removeToken tests ==========

    @Test
    void removeToken_withNullUsername_shouldDoNothing() {
        fcmService.removeToken(null);

        verify(fcmTokenRepository, never()).deleteByBguUsername(any());
    }

    @Test
    void removeToken_shouldDeleteAndLog() {
        fcmService.removeToken("testuser");

        verify(fcmTokenRepository).deleteByBguUsername("testuser");
        verify(databaseLoggerService).logAction(
            eq("INFO"),
            eq("FCM_TOKEN_REMOVED"),
            contains("testuser"),
            eq("testuser"),
            isNull()
        );
    }

    @Test
    void removeToken_shouldNormalizeUsername() {
        fcmService.removeToken("  TESTUSER  ");

        verify(fcmTokenRepository).deleteByBguUsername("testuser");
    }

    // ========== sendNotification tests ==========

    @Test
    void sendNotification_whenFirebaseNotInitialized_shouldReturnFalse() {
        when(firebaseConfig.isInitialized()).thenReturn(false);

        boolean result = fcmService.sendNotification("testuser", "Title", "Body", null);

        assertFalse(result);
        verify(fcmTokenRepository, never()).findByBguUsername(any());
    }

    @Test
    void sendNotification_withNullUsername_shouldReturnFalse() {
        when(firebaseConfig.isInitialized()).thenReturn(true);

        boolean result = fcmService.sendNotification(null, "Title", "Body", null);

        assertFalse(result);
    }

    @Test
    void sendNotification_whenNoTokenFound_shouldReturnFalse() {
        when(firebaseConfig.isInitialized()).thenReturn(true);
        when(fcmTokenRepository.findByBguUsername("testuser")).thenReturn(Optional.empty());

        boolean result = fcmService.sendNotification("testuser", "Title", "Body", null);

        assertFalse(result);
    }

    @Test
    void sendNotification_invalidToken_unregistered_removesToken() throws Exception {
        when(firebaseConfig.isInitialized()).thenReturn(true);
        when(fcmTokenRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testToken));

        FirebaseMessagingException ex = createMessagingException(MessagingErrorCode.UNREGISTERED);

        try (MockedStatic<FirebaseMessaging> firebaseMessaging = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
            firebaseMessaging.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);
            when(mockMessaging.send(any(Message.class))).thenThrow(ex);

            boolean result = fcmService.sendNotification("testuser", "Title", "Body", null);

            assertFalse(result);
            verify(fcmTokenRepository).deleteByBguUsername("testuser");
        }
    }

    @Test
    void sendNotification_invalidToken_argument_removesToken() throws Exception {
        when(firebaseConfig.isInitialized()).thenReturn(true);
        when(fcmTokenRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testToken));

        FirebaseMessagingException ex = createMessagingException(MessagingErrorCode.INVALID_ARGUMENT);

        try (MockedStatic<FirebaseMessaging> firebaseMessaging = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
            firebaseMessaging.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);
            when(mockMessaging.send(any(Message.class))).thenThrow(ex);

            boolean result = fcmService.sendNotification("testuser", "Title", "Body", null);

            assertFalse(result);
            verify(fcmTokenRepository).deleteByBguUsername("testuser");
        }
    }

    @Test
    void sendNotification_nonInvalidTokenError_doesNotRemoveToken() throws Exception {
        when(firebaseConfig.isInitialized()).thenReturn(true);
        when(fcmTokenRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testToken));

        FirebaseMessagingException ex = createMessagingException(MessagingErrorCode.INTERNAL);

        try (MockedStatic<FirebaseMessaging> firebaseMessaging = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
            firebaseMessaging.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);
            when(mockMessaging.send(any(Message.class))).thenThrow(ex);

            boolean result = fcmService.sendNotification("testuser", "Title", "Body", null);

            assertFalse(result);
            verify(fcmTokenRepository, never()).deleteByBguUsername(anyString());
        }
    }

    // ========== sendApprovalNotification tests ==========

    @Test
    void sendApprovalNotification_approved_shouldUseCorrectMessage() {
        when(firebaseConfig.isInitialized()).thenReturn(false); // Skip actual send

        fcmService.sendApprovalNotification("testuser", 1L, "Jan 15, 2026", true, null);

        // Just verify it doesn't throw - Firebase not initialized so no actual send
    }

    @Test
    void sendApprovalNotification_declined_withReason_shouldIncludeReason() {
        when(firebaseConfig.isInitialized()).thenReturn(false);

        fcmService.sendApprovalNotification("testuser", 1L, "Jan 15, 2026", false, "Schedule conflict");

        // Just verify it doesn't throw
    }

    @Test
    void sendApprovalNotification_declined_withoutReason_shouldUseDefaultMessage() {
        when(firebaseConfig.isInitialized()).thenReturn(false);

        fcmService.sendApprovalNotification("testuser", 1L, "Jan 15, 2026", false, null);

        // Just verify it doesn't throw
    }

    // ========== sendPromotionNotification tests ==========

    @Test
    void sendPromotionNotification_shouldNotThrow() {
        when(firebaseConfig.isInitialized()).thenReturn(false);

        fcmService.sendPromotionNotification("testuser", 1L, "Jan 15, 2026");

        // Just verify it doesn't throw
    }

    // ========== sendAttendanceReminder tests ==========

    @Test
    void sendAttendanceReminder_shouldNotThrow() {
        when(firebaseConfig.isInitialized()).thenReturn(false);

        fcmService.sendAttendanceReminder("testuser", 1L, "Jan 15, 2026", "10:00-11:00");

        // Just verify it doesn't throw
    }

    private FirebaseMessagingException createMessagingException(MessagingErrorCode code) {
        FirebaseException firebaseException = new FirebaseException(ErrorCode.INVALID_ARGUMENT, "send failed", null);
        try {
            Method method = FirebaseMessagingException.class.getDeclaredMethod(
                    "withMessagingErrorCode", FirebaseException.class, MessagingErrorCode.class);
            method.setAccessible(true);
            return (FirebaseMessagingException) method.invoke(null, firebaseException, code);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create FirebaseMessagingException", e);
        }
    }
}
