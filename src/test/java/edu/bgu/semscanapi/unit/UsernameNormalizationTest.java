package edu.bgu.semscanapi.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for username normalization logic.
 * Tests the normalization rules: lowercase, trim, handle null/empty.
 *
 * These tests validate the same logic used across services:
 * - AuthController
 * - PresenterHomeService
 * - AttendanceService
 */
class UsernameNormalizationTest {

    /**
     * Normalize username using the same logic as the services.
     * This mirrors the private normalizeUsername methods.
     */
    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Extract base username (strip @domain).
     * This mirrors the logic in AuthController.login()
     */
    private String extractBaseUsername(String username) {
        if (username == null) {
            return null;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("@")) {
            return normalized.substring(0, normalized.indexOf('@'));
        }
        return normalized;
    }

    @Test
    @DisplayName("Null username returns null")
    void nullUsername_ReturnsNull() {
        assertNull(normalizeUsername(null));
    }

    @Test
    @DisplayName("Empty username returns null")
    void emptyUsername_ReturnsNull() {
        assertNull(normalizeUsername(""));
    }

    @Test
    @DisplayName("Blank username returns null")
    void blankUsername_ReturnsNull() {
        assertNull(normalizeUsername("   "));
    }

    @ParameterizedTest
    @DisplayName("Uppercase usernames are lowercased")
    @CsvSource({
        "USER, user",
        "User, user",
        "USER123, user123",
        "TestUser, testuser"
    })
    void uppercaseUsername_IsLowercased(String input, String expected) {
        assertEquals(expected, normalizeUsername(input));
    }

    @ParameterizedTest
    @DisplayName("Whitespace is trimmed")
    @CsvSource({
        "'  user  ', user",
        "'user ', user",
        "' user', user",
        "'  USER  ', user"
    })
    void whitespace_IsTrimmed(String input, String expected) {
        assertEquals(expected, normalizeUsername(input));
    }

    @Test
    @DisplayName("Already normalized username unchanged")
    void normalizedUsername_Unchanged() {
        assertEquals("testuser", normalizeUsername("testuser"));
    }

    // Domain stripping tests (for login flow)

    @ParameterizedTest
    @DisplayName("Email domain is stripped")
    @CsvSource({
        "user@bgu.ac.il, user",
        "User@BGU.AC.IL, user",
        "testuser@post.bgu.ac.il, testuser",
        "USER123@example.com, user123"
    })
    void emailDomain_IsStripped(String input, String expected) {
        assertEquals(expected, extractBaseUsername(input));
    }

    @Test
    @DisplayName("Username without domain unchanged")
    void usernameWithoutDomain_Unchanged() {
        assertEquals("testuser", extractBaseUsername("testuser"));
    }

    @Test
    @DisplayName("Null input to extractBaseUsername returns null")
    void nullInputToExtract_ReturnsNull() {
        assertNull(extractBaseUsername(null));
    }

    // Edge cases

    @Test
    @DisplayName("Username with multiple @ signs takes first part")
    void multipleAtSigns_TakesFirstPart() {
        assertEquals("user", extractBaseUsername("user@domain@extra"));
    }

    @Test
    @DisplayName("Username starting with @ returns empty before @")
    void startsWithAt_ReturnsEmpty() {
        assertEquals("", extractBaseUsername("@domain.com"));
    }
}
