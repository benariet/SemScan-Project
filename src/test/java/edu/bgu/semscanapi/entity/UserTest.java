package edu.bgu.semscanapi.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test for User entity
 * Tests basic entity functionality
 */
class UserTest {

    @Test
    void testUserCreation() {
        // Given
        User user = new User();
        user.setUserId("user-001");
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setRole(User.UserRole.STUDENT);
        user.setStudentId("student-001");

        // When & Then
        assertNotNull(user);
        assertEquals("user-001", user.getUserId());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("John", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertEquals(User.UserRole.STUDENT, user.getRole());
        assertEquals("student-001", user.getStudentId());
    }

    @Test
    void testUserRoleEnum() {
        // Test that all enum values exist
        assertNotNull(User.UserRole.STUDENT);
        assertNotNull(User.UserRole.PRESENTER);
    }

    @Test
    void testUserEqualsAndHashCode() {
        // Given
        User user1 = new User();
        user1.setUserId("user-001");
        user1.setEmail("test@example.com");

        User user2 = new User();
        user2.setUserId("user-001");
        user2.setEmail("test@example.com");

        User user3 = new User();
        user3.setUserId("user-002");
        user3.setEmail("test@example.com");

        // When & Then
        assertEquals(user1, user2);
        assertNotEquals(user1, user3);
        assertEquals(user1.hashCode(), user2.hashCode());
        assertNotEquals(user1.hashCode(), user3.hashCode());
    }

    @Test
    void testUserToString() {
        // Given
        User user = new User();
        user.setUserId("user-001");
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");

        // When
        String userString = user.toString();

        // Then
        assertNotNull(userString);
        assertTrue(userString.contains("user-001"));
        assertTrue(userString.contains("test@example.com"));
        assertTrue(userString.contains("John"));
        assertTrue(userString.contains("Doe"));
    }
}
