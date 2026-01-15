package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService
 * Tests user lookup, presenter/student retrieval, and existence checks
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthenticationService authenticationService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setBguUsername("testuser");
        testUser.setEmail("testuser@bgu.ac.il");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setIsPresenter(true);
        testUser.setIsParticipant(true);
    }

    // ==================== findUserByUsername Tests ====================

    @Nested
    @DisplayName("findUserByUsername Tests")
    class FindUserByUsernameTests {

        @Test
        @DisplayName("Find user by exact username match")
        void findUserByUsername_ExactMatch_ReturnsUser() {
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));

            Optional<User> result = authenticationService.findUserByUsername("testuser");

            assertTrue(result.isPresent());
            assertEquals("testuser", result.get().getBguUsername());
            verify(userRepository).findByBguUsername("testuser");
            verify(userRepository, never()).findByBguUsernameIgnoreCase(anyString());
        }

        @Test
        @DisplayName("Find user by case-insensitive match when exact match fails")
        void findUserByUsername_CaseInsensitiveMatch_ReturnsUser() {
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.empty());
            when(userRepository.findByBguUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testUser));

            Optional<User> result = authenticationService.findUserByUsername("TESTUSER");

            assertTrue(result.isPresent());
            assertEquals("testuser", result.get().getBguUsername());
            verify(userRepository).findByBguUsername("testuser");
            verify(userRepository).findByBguUsernameIgnoreCase("testuser");
        }

        @Test
        @DisplayName("Return empty when user not found")
        void findUserByUsername_NotFound_ReturnsEmpty() {
            when(userRepository.findByBguUsername("unknownuser")).thenReturn(Optional.empty());
            when(userRepository.findByBguUsernameIgnoreCase("unknownuser")).thenReturn(Optional.empty());

            Optional<User> result = authenticationService.findUserByUsername("unknownuser");

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Normalize username - strip domain and lowercase")
        void findUserByUsername_NormalizesUsername() {
            when(userRepository.findByBguUsername("testuser")).thenReturn(Optional.of(testUser));

            Optional<User> result = authenticationService.findUserByUsername("  TESTUSER  ");

            assertTrue(result.isPresent());
            verify(userRepository).findByBguUsername("testuser");
        }

        @Test
        @DisplayName("Handle null username")
        void findUserByUsername_NullUsername_ReturnsEmpty() {
            when(userRepository.findByBguUsername("")).thenReturn(Optional.empty());
            when(userRepository.findByBguUsernameIgnoreCase("")).thenReturn(Optional.empty());

            Optional<User> result = authenticationService.findUserByUsername(null);

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Handle repository exception")
        void findUserByUsername_Exception_ReturnsEmpty() {
            when(userRepository.findByBguUsername(anyString())).thenThrow(new RuntimeException("DB error"));

            Optional<User> result = authenticationService.findUserByUsername("testuser");

            assertFalse(result.isPresent());
        }
    }

    // ==================== findUserByEmail Tests ====================

    @Nested
    @DisplayName("findUserByEmail Tests")
    class FindUserByEmailTests {

        @Test
        @DisplayName("Find user by email")
        void findUserByEmail_Found_ReturnsUser() {
            when(userRepository.findByEmail("testuser@bgu.ac.il")).thenReturn(Optional.of(testUser));

            Optional<User> result = authenticationService.findUserByEmail("testuser@bgu.ac.il");

            assertTrue(result.isPresent());
            assertEquals("testuser@bgu.ac.il", result.get().getEmail());
        }

        @Test
        @DisplayName("Return empty when email not found")
        void findUserByEmail_NotFound_ReturnsEmpty() {
            when(userRepository.findByEmail("unknown@bgu.ac.il")).thenReturn(Optional.empty());

            Optional<User> result = authenticationService.findUserByEmail("unknown@bgu.ac.il");

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Handle repository exception")
        void findUserByEmail_Exception_ReturnsEmpty() {
            when(userRepository.findByEmail(anyString())).thenThrow(new RuntimeException("DB error"));

            Optional<User> result = authenticationService.findUserByEmail("testuser@bgu.ac.il");

            assertFalse(result.isPresent());
        }
    }

    // ==================== findPresenters Tests ====================

    @Nested
    @DisplayName("findPresenters Tests")
    class FindPresentersTests {

        @Test
        @DisplayName("Find all presenters")
        void findPresenters_ReturnsPresenters() {
            User presenter1 = new User();
            presenter1.setBguUsername("presenter1");
            presenter1.setIsPresenter(true);

            User presenter2 = new User();
            presenter2.setBguUsername("presenter2");
            presenter2.setIsPresenter(true);

            when(userRepository.findByIsPresenterTrue()).thenReturn(Arrays.asList(presenter1, presenter2));

            List<User> result = authenticationService.findPresenters();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Return empty list when no presenters")
        void findPresenters_NoPresenters_ReturnsEmptyList() {
            when(userRepository.findByIsPresenterTrue()).thenReturn(Collections.emptyList());

            List<User> result = authenticationService.findPresenters();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Handle repository exception")
        void findPresenters_Exception_ReturnsEmptyList() {
            when(userRepository.findByIsPresenterTrue()).thenThrow(new RuntimeException("DB error"));

            List<User> result = authenticationService.findPresenters();

            assertTrue(result.isEmpty());
        }
    }

    // ==================== findStudents Tests ====================

    @Nested
    @DisplayName("findStudents Tests")
    class FindStudentsTests {

        @Test
        @DisplayName("Find all students")
        void findStudents_ReturnsStudents() {
            User student1 = new User();
            student1.setBguUsername("student1");
            student1.setIsParticipant(true);

            User student2 = new User();
            student2.setBguUsername("student2");
            student2.setIsParticipant(true);

            when(userRepository.findByIsParticipantTrue()).thenReturn(Arrays.asList(student1, student2));

            List<User> result = authenticationService.findStudents();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Return empty list when no students")
        void findStudents_NoStudents_ReturnsEmptyList() {
            when(userRepository.findByIsParticipantTrue()).thenReturn(Collections.emptyList());

            List<User> result = authenticationService.findStudents();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Handle repository exception")
        void findStudents_Exception_ReturnsEmptyList() {
            when(userRepository.findByIsParticipantTrue()).thenThrow(new RuntimeException("DB error"));

            List<User> result = authenticationService.findStudents();

            assertTrue(result.isEmpty());
        }
    }

    // ==================== userExists Tests ====================

    @Nested
    @DisplayName("userExists Tests")
    class UserExistsTests {

        @Test
        @DisplayName("Return true when user exists")
        void userExists_UserExists_ReturnsTrue() {
            when(userRepository.existsByBguUsernameIgnoreCase("testuser")).thenReturn(true);

            boolean result = authenticationService.userExists("testuser");

            assertTrue(result);
        }

        @Test
        @DisplayName("Return false when user does not exist")
        void userExists_UserNotExists_ReturnsFalse() {
            when(userRepository.existsByBguUsernameIgnoreCase("unknownuser")).thenReturn(false);

            boolean result = authenticationService.userExists("unknownuser");

            assertFalse(result);
        }

        @Test
        @DisplayName("Normalize username for existence check")
        void userExists_NormalizesUsername() {
            when(userRepository.existsByBguUsernameIgnoreCase("testuser")).thenReturn(true);

            boolean result = authenticationService.userExists("  TESTUSER  ");

            assertTrue(result);
            verify(userRepository).existsByBguUsernameIgnoreCase("testuser");
        }

        @Test
        @DisplayName("Handle null username")
        void userExists_NullUsername_ReturnsFalse() {
            when(userRepository.existsByBguUsernameIgnoreCase("")).thenReturn(false);

            boolean result = authenticationService.userExists(null);

            assertFalse(result);
        }

        @Test
        @DisplayName("Handle repository exception")
        void userExists_Exception_ReturnsFalse() {
            when(userRepository.existsByBguUsernameIgnoreCase(anyString())).thenThrow(new RuntimeException("DB error"));

            boolean result = authenticationService.userExists("testuser");

            assertFalse(result);
        }
    }

    // ==================== emailExists Tests ====================

    @Nested
    @DisplayName("emailExists Tests")
    class EmailExistsTests {

        @Test
        @DisplayName("Return true when email exists")
        void emailExists_EmailExists_ReturnsTrue() {
            when(userRepository.existsByEmailIgnoreCase("testuser@bgu.ac.il")).thenReturn(true);

            boolean result = authenticationService.emailExists("testuser@bgu.ac.il");

            assertTrue(result);
        }

        @Test
        @DisplayName("Return false when email does not exist")
        void emailExists_EmailNotExists_ReturnsFalse() {
            when(userRepository.existsByEmailIgnoreCase("unknown@bgu.ac.il")).thenReturn(false);

            boolean result = authenticationService.emailExists("unknown@bgu.ac.il");

            assertFalse(result);
        }

        @Test
        @DisplayName("Handle repository exception")
        void emailExists_Exception_ReturnsFalse() {
            when(userRepository.existsByEmailIgnoreCase(anyString())).thenThrow(new RuntimeException("DB error"));

            boolean result = authenticationService.emailExists("testuser@bgu.ac.il");

            assertFalse(result);
        }
    }
}
