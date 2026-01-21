package edu.bgu.semscanapi.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FcmToken entity
 */
class FcmTokenTest {

    @Test
    void constructor_shouldSetFields() {
        FcmToken token = new FcmToken("testuser", "fcm_token_123", "Android 12");

        assertEquals("testuser", token.getBguUsername());
        assertEquals("fcm_token_123", token.getFcmToken());
        assertEquals("Android 12", token.getDeviceInfo());
    }

    @Test
    void defaultConstructor_shouldWork() {
        FcmToken token = new FcmToken();

        assertNull(token.getBguUsername());
        assertNull(token.getFcmToken());
    }

    @Test
    void setters_shouldUpdateFields() {
        FcmToken token = new FcmToken();

        token.setBguUsername("user1");
        token.setFcmToken("token1");
        token.setDeviceInfo("device1");
        token.setLastNotificationTitle("Test Title");
        token.setLastNotificationBody("Test Body");
        token.setLastNotificationSentAt(LocalDateTime.now());

        assertEquals("user1", token.getBguUsername());
        assertEquals("token1", token.getFcmToken());
        assertEquals("device1", token.getDeviceInfo());
        assertEquals("Test Title", token.getLastNotificationTitle());
        assertEquals("Test Body", token.getLastNotificationBody());
        assertNotNull(token.getLastNotificationSentAt());
    }

    @Test
    void equals_sameId_shouldBeEqual() {
        FcmToken token1 = new FcmToken("user1", "token1", "device1");
        token1.setId(1L);

        FcmToken token2 = new FcmToken("user2", "token2", "device2");
        token2.setId(1L);

        assertEquals(token1, token2);
    }

    @Test
    void equals_differentId_shouldNotBeEqual() {
        FcmToken token1 = new FcmToken("user1", "token1", "device1");
        token1.setId(1L);

        FcmToken token2 = new FcmToken("user1", "token1", "device1");
        token2.setId(2L);

        assertNotEquals(token1, token2);
    }

    @Test
    void equals_null_shouldNotBeEqual() {
        FcmToken token = new FcmToken("user1", "token1", "device1");
        token.setId(1L);

        assertNotEquals(token, null);
    }

    @Test
    void equals_differentClass_shouldNotBeEqual() {
        FcmToken token = new FcmToken("user1", "token1", "device1");
        token.setId(1L);

        assertNotEquals(token, "not a token");
    }

    @Test
    void hashCode_sameId_shouldBeEqual() {
        FcmToken token1 = new FcmToken("user1", "token1", "device1");
        token1.setId(1L);

        FcmToken token2 = new FcmToken("user2", "token2", "device2");
        token2.setId(1L);

        assertEquals(token1.hashCode(), token2.hashCode());
    }

    @Test
    void toString_shouldContainUsername() {
        FcmToken token = new FcmToken("testuser", "token", "device");
        token.setId(1L);

        String str = token.toString();

        assertTrue(str.contains("testuser"));
        assertTrue(str.contains("1"));
    }

    @Test
    void onCreate_shouldSetCreatedAt() {
        FcmToken token = new FcmToken("user", "token", "device");

        token.onCreate();

        assertNotNull(token.getCreatedAt());
    }

    @Test
    void onUpdate_shouldSetUpdatedAt() {
        FcmToken token = new FcmToken("user", "token", "device");

        token.onUpdate();

        assertNotNull(token.getUpdatedAt());
    }
}
