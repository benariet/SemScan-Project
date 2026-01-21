package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.entity.ApprovalStatus;
import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.SeminarSlotRegistrationId;
import edu.bgu.semscanapi.entity.Supervisor;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.entity.WaitingListEntry;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.repository.WaitingListRepository;
import edu.bgu.semscanapi.repository.WaitingListPromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for WaitingListService
 * Tests the First-Sets-Type queue system and PhD/MSc exclusivity rules
 */
@ExtendWith(MockitoExtension.class)
class WaitingListServiceTest {

    @Mock
    private WaitingListRepository waitingListRepository;

    @Mock
    private SeminarSlotRegistrationRepository registrationRepository;

    @Mock
    private SeminarSlotRepository slotRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DatabaseLoggerService databaseLoggerService;

    @Mock
    private MailService mailService;

    @Mock
    private WaitingListPromotionRepository waitingListPromotionRepository;

    @Mock
    private AppConfigService appConfigService;

    @Mock
    private FcmService fcmService;

    @InjectMocks
    private WaitingListService waitingListService;

    private User phdUser;
    private User mscUser;
    private User mscUser2;
    private SeminarSlot testSlot;
    private Supervisor supervisor;

    @BeforeEach
    void setUp() {
        // Setup supervisor
        supervisor = new Supervisor();
        supervisor.setId(1L);
        supervisor.setName("Dr. Supervisor");
        supervisor.setEmail("supervisor@example.com");

        // Setup PhD user
        phdUser = new User();
        phdUser.setId(1L);
        phdUser.setBguUsername("phduser");
        phdUser.setFirstName("PhD");
        phdUser.setLastName("User");
        phdUser.setDegree(User.Degree.PhD);
        phdUser.setEmail("phd@example.com");
        phdUser.setSupervisor(supervisor);

        // Setup MSc user
        mscUser = new User();
        mscUser.setId(2L);
        mscUser.setBguUsername("mscuser");
        mscUser.setFirstName("MSc");
        mscUser.setLastName("User");
        mscUser.setDegree(User.Degree.MSc);
        mscUser.setEmail("msc@example.com");
        mscUser.setSupervisor(supervisor);

        // Setup second MSc user
        mscUser2 = new User();
        mscUser2.setId(3L);
        mscUser2.setBguUsername("mscuser2");
        mscUser2.setFirstName("MSc2");
        mscUser2.setLastName("User");
        mscUser2.setDegree(User.Degree.MSc);
        mscUser2.setEmail("msc2@example.com");
        mscUser2.setSupervisor(supervisor);

        // Setup test slot with PhD registered
        testSlot = new SeminarSlot();
        testSlot.setSlotId(1L);
        testSlot.setSlotDate(LocalDate.now().plusDays(7));
        testSlot.setStartTime(LocalTime.of(10, 0));
        testSlot.setEndTime(LocalTime.of(11, 0));
        testSlot.setBuilding("Building 37");
        testSlot.setRoom("Room 201");
        testSlot.setCapacity(3);
        testSlot.setStatus(SeminarSlot.SlotStatus.FULL);

        // Default config mocks
        lenient().when(appConfigService.getIntegerConfig(eq("waiting.list.limit.per.slot"), anyInt())).thenReturn(3);
        lenient().when(appConfigService.getIntegerConfig(eq("phd.capacity.weight"), anyInt())).thenReturn(3);
    }

    // ==================== First-Sets-Type Queue Tests ====================

    @Nested
    @DisplayName("First-Sets-Type Queue Tests")
    class FirstSetsTypeQueueTests {

        @Test
        @DisplayName("PhD joins empty waiting list - sets queue type to PhD-only")
        void phdJoinsEmptyWaitingList_SetsTypeToPhdOnly() {
            // Given - PhD-occupied slot with empty waiting list
            SeminarSlotRegistration phdReg = createRegistration(1L, "existingphd", User.Degree.PhD, ApprovalStatus.APPROVED);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "phduser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("phduser")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "phduser")).thenReturn(false);
            when(userRepository.findByBguUsername("phduser")).thenReturn(Optional.of(phdUser));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(phdReg));
            when(waitingListRepository.findBySlotIdOrderByPositionAsc(1L)).thenReturn(Collections.emptyList()); // Empty WL
            when(waitingListRepository.countBySlotId(1L)).thenReturn(0L);
            when(waitingListRepository.save(any(WaitingListEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            WaitingListEntry result = waitingListService.addToWaitingList(1L, "phduser", "PhD Topic", null, null);

            // Then
            assertNotNull(result);
            assertEquals(User.Degree.PhD, result.getDegree());
            assertEquals(1, result.getPosition());
            verify(waitingListRepository).save(argThat(entry ->
                entry.getDegree() == User.Degree.PhD && entry.getPosition() == 1));
        }

        @Test
        @DisplayName("MSc joins empty waiting list - sets queue type to MSc-only")
        void mscJoinsEmptyWaitingList_SetsTypeToMscOnly() {
            // Given - PhD-occupied slot with empty waiting list
            SeminarSlotRegistration phdReg = createRegistration(1L, "existingphd", User.Degree.PhD, ApprovalStatus.APPROVED);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("mscuser")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "mscuser")).thenReturn(false);
            when(userRepository.findByBguUsername("mscuser")).thenReturn(Optional.of(mscUser));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(phdReg));
            when(waitingListRepository.findBySlotIdOrderByPositionAsc(1L)).thenReturn(Collections.emptyList()); // Empty WL
            when(waitingListRepository.countBySlotId(1L)).thenReturn(0L);
            when(waitingListRepository.save(any(WaitingListEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            WaitingListEntry result = waitingListService.addToWaitingList(1L, "mscuser", "MSc Topic", null, null);

            // Then
            assertNotNull(result);
            assertEquals(User.Degree.MSc, result.getDegree());
            assertEquals(1, result.getPosition());
        }

        @Test
        @DisplayName("PhD blocked when MSc is first in queue - DEGREE_MISMATCH")
        void phdBlockedWhenMscIsFirst_DegreeMismatch() {
            // Given - Waiting list has MSc in position 1
            SeminarSlotRegistration phdReg = createRegistration(1L, "existingphd", User.Degree.PhD, ApprovalStatus.APPROVED);
            WaitingListEntry mscEntry = createWaitingListEntry(1L, "firstmsc", User.Degree.MSc, 1);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "phduser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("phduser")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "phduser")).thenReturn(false);
            when(userRepository.findByBguUsername("phduser")).thenReturn(Optional.of(phdUser));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(phdReg));
            when(waitingListRepository.findBySlotIdOrderByPositionAsc(1L)).thenReturn(Collections.singletonList(mscEntry));

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                waitingListService.addToWaitingList(1L, "phduser", "PhD Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("MSc-only"));
            verify(waitingListRepository, never()).save(any());
        }

        @Test
        @DisplayName("MSc blocked when PhD is first in queue - DEGREE_MISMATCH")
        void mscBlockedWhenPhdIsFirst_DegreeMismatch() {
            // Given - Waiting list has PhD in position 1
            SeminarSlotRegistration phdReg = createRegistration(1L, "existingphd", User.Degree.PhD, ApprovalStatus.APPROVED);
            WaitingListEntry phdEntry = createWaitingListEntry(1L, "firstphd", User.Degree.PhD, 1);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("mscuser")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "mscuser")).thenReturn(false);
            when(userRepository.findByBguUsername("mscuser")).thenReturn(Optional.of(mscUser));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(phdReg));
            when(waitingListRepository.findBySlotIdOrderByPositionAsc(1L)).thenReturn(Collections.singletonList(phdEntry));

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                waitingListService.addToWaitingList(1L, "mscuser", "MSc Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("PhD-only"));
            verify(waitingListRepository, never()).save(any());
        }

        @Test
        @DisplayName("Same degree (MSc) can join existing MSc queue - SUCCESS")
        void sameDegreeCanJoinExistingQueue_Success() {
            // Given - Waiting list has MSc in position 1
            SeminarSlotRegistration phdReg = createRegistration(1L, "existingphd", User.Degree.PhD, ApprovalStatus.APPROVED);
            WaitingListEntry mscEntry = createWaitingListEntry(1L, "firstmsc", User.Degree.MSc, 1);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("mscuser")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "mscuser")).thenReturn(false);
            when(userRepository.findByBguUsername("mscuser")).thenReturn(Optional.of(mscUser));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(phdReg));
            when(waitingListRepository.findBySlotIdOrderByPositionAsc(1L)).thenReturn(Collections.singletonList(mscEntry));
            when(waitingListRepository.countBySlotId(1L)).thenReturn(1L);
            when(waitingListRepository.save(any(WaitingListEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            WaitingListEntry result = waitingListService.addToWaitingList(1L, "mscuser", "MSc Topic 2", null, null);

            // Then
            assertNotNull(result);
            assertEquals(User.Degree.MSc, result.getDegree());
            assertEquals(2, result.getPosition()); // Position 2 (after first MSc)
        }
    }

    // ==================== Business Rule Rejection Tests ====================

    @Nested
    @DisplayName("Business Rule Rejection Tests")
    class BusinessRuleRejectionTests {

        @Test
        @DisplayName("User already on this waiting list - ALREADY_ON_LIST")
        void userAlreadyOnWaitingList_AlreadyOnList() {
            // Given
            WaitingListEntry existingEntry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 1);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.of(existingEntry));

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                waitingListService.addToWaitingList(1L, "mscuser", "Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("already on the waiting list"));
        }

        @Test
        @DisplayName("User already on another waiting list - ALREADY_ON_ANOTHER")
        void userAlreadyOnAnotherWaitingList_AlreadyOnAnother() {
            // Given
            WaitingListEntry existingEntry = createWaitingListEntry(99L, "mscuser", User.Degree.MSc, 1);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("mscuser")).thenReturn(true);
            when(waitingListRepository.findByPresenterUsername("mscuser")).thenReturn(Collections.singletonList(existingEntry));

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                waitingListService.addToWaitingList(1L, "mscuser", "Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("only be on 1 waiting list"));
        }

        @Test
        @DisplayName("User has active registration - HAS_ACTIVE_REGISTRATION")
        void userHasActiveRegistration_HasActiveRegistration() {
            // Given
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("mscuser")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "mscuser")).thenReturn(true);

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                waitingListService.addToWaitingList(1L, "mscuser", "Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("already has an active registration"));
        }

        @Test
        @DisplayName("Waiting list full - WAITING_LIST_FULL")
        void waitingListFull_WaitingListFull() {
            // Given - Waiting list already has 3 entries (limit)
            SeminarSlotRegistration phdReg = createRegistration(1L, "existingphd", User.Degree.PhD, ApprovalStatus.APPROVED);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("mscuser")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "mscuser")).thenReturn(false);
            when(userRepository.findByBguUsername("mscuser")).thenReturn(Optional.of(mscUser));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(phdReg));
            when(waitingListRepository.findBySlotIdOrderByPositionAsc(1L)).thenReturn(Collections.emptyList());
            when(waitingListRepository.countBySlotId(1L)).thenReturn(3L); // At limit

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                waitingListService.addToWaitingList(1L, "mscuser", "Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("full"));
        }

        @Test
        @DisplayName("User not found - throws IllegalArgumentException")
        void userNotFound_ThrowsException() {
            // Given
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "unknownuser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("unknownuser")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "unknownuser")).thenReturn(false);
            when(userRepository.findByBguUsername("unknownuser")).thenReturn(Optional.empty());

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                waitingListService.addToWaitingList(1L, "unknownuser", "Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("User not found"));
        }

        @Test
        @DisplayName("Slot not found - throws IllegalArgumentException")
        void slotNotFound_ThrowsException() {
            // Given
            when(slotRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                waitingListService.addToWaitingList(999L, "mscuser", "Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("Slot not found"));
        }

        @Test
        @DisplayName("User degree not set - throws IllegalStateException")
        void userDegreeNotSet_ThrowsException() {
            // Given
            User noDegreeUser = new User();
            noDegreeUser.setBguUsername("nodegree");
            noDegreeUser.setDegree(null);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "nodegree")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("nodegree")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "nodegree")).thenReturn(false);
            when(userRepository.findByBguUsername("nodegree")).thenReturn(Optional.of(noDegreeUser));

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                waitingListService.addToWaitingList(1L, "nodegree", "Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("degree is not set"));
        }
    }

    // ==================== Remove from Waiting List Tests ====================

    @Nested
    @DisplayName("Remove from Waiting List Tests")
    class RemoveFromWaitingListTests {

        @Test
        @DisplayName("Remove user from waiting list - SUCCESS")
        void removeFromWaitingList_Success() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 2);

            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.of(entry));

            // When
            WaitingListEntry result = waitingListService.removeFromWaitingList(1L, "mscuser");

            // Then
            assertNotNull(result);
            assertEquals("mscuser", result.getPresenterUsername());
            verify(waitingListRepository).deleteBySlotIdAndPresenterUsername(1L, "mscuser");
            verify(waitingListRepository).decrementPositionsAfter(1L, 2);
        }

        @Test
        @DisplayName("Remove user not on waiting list - throws IllegalArgumentException")
        void removeUserNotOnWaitingList_ThrowsException() {
            // Given
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "notinlist")).thenReturn(Optional.empty());

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                waitingListService.removeFromWaitingList(1L, "notinlist")
            );
            assertTrue(exception.getMessage().contains("Not on waiting list"));
        }
    }

    // ==================== Helper Tests ====================

    @Nested
    @DisplayName("Helper Method Tests")
    class HelperMethodTests {

        @Test
        @DisplayName("isOnWaitingList returns true when user is on list")
        void isOnWaitingList_ReturnsTrue_WhenOnList() {
            // Given
            when(waitingListRepository.existsBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(true);

            // When
            boolean result = waitingListService.isOnWaitingList(1L, "mscuser");

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("isOnWaitingList returns false when user is not on list")
        void isOnWaitingList_ReturnsFalse_WhenNotOnList() {
            // Given
            when(waitingListRepository.existsBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(false);

            // When
            boolean result = waitingListService.isOnWaitingList(1L, "mscuser");

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("isOnAnyWaitingList returns true when user is on any list")
        void isOnAnyWaitingList_ReturnsTrue_WhenOnAnyList() {
            // Given
            when(waitingListRepository.existsByPresenterUsername("mscuser")).thenReturn(true);

            // When
            boolean result = waitingListService.isOnAnyWaitingList("mscuser");

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("getWaitingList returns ordered list")
        void getWaitingList_ReturnsOrderedList() {
            // Given
            WaitingListEntry entry1 = createWaitingListEntry(1L, "user1", User.Degree.MSc, 1);
            WaitingListEntry entry2 = createWaitingListEntry(1L, "user2", User.Degree.MSc, 2);

            when(waitingListRepository.findBySlotIdOrderByPositionAsc(1L)).thenReturn(Arrays.asList(entry1, entry2));

            // When
            List<WaitingListEntry> result = waitingListService.getWaitingList(1L);

            // Then
            assertEquals(2, result.size());
            assertEquals("user1", result.get(0).getPresenterUsername());
            assertEquals("user2", result.get(1).getPresenterUsername());
        }

        @Test
        @DisplayName("getWaitingListCount returns correct count")
        void getWaitingListCount_ReturnsCorrectCount() {
            // Given
            when(waitingListRepository.countBySlotId(1L)).thenReturn(5L);

            // When
            long result = waitingListService.getWaitingListCount(1L);

            // Then
            assertEquals(5L, result);
        }
    }

    // ==================== Input Validation Tests ====================

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Null slotId - throws IllegalArgumentException")
        void nullSlotId_ThrowsException() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                waitingListService.addToWaitingList(null, "mscuser", "Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("Slot ID is null"));
        }

        @Test
        @DisplayName("Null username - throws IllegalArgumentException")
        void nullUsername_ThrowsException() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                waitingListService.addToWaitingList(1L, null, "Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("null or empty"));
        }

        @Test
        @DisplayName("Empty username - throws IllegalArgumentException")
        void emptyUsername_ThrowsException() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                waitingListService.addToWaitingList(1L, "  ", "Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("null or empty"));
        }
    }

    // ==================== Supervisor Fallback Tests ====================

    @Nested
    @DisplayName("Supervisor Fallback Tests")
    class SupervisorFallbackTests {

        @Test
        @DisplayName("Uses supervisor from User entity when available")
        void usesSupervisorFromUserEntity() {
            // Given - User has supervisor linked
            SeminarSlotRegistration phdReg = createRegistration(1L, "existingphd", User.Degree.PhD, ApprovalStatus.APPROVED);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("mscuser")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "mscuser")).thenReturn(false);
            when(userRepository.findByBguUsername("mscuser")).thenReturn(Optional.of(mscUser)); // Has supervisor
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(phdReg));
            when(waitingListRepository.findBySlotIdOrderByPositionAsc(1L)).thenReturn(Collections.emptyList());
            when(waitingListRepository.countBySlotId(1L)).thenReturn(0L);
            when(waitingListRepository.save(any(WaitingListEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            // When - Don't provide supervisor in request
            WaitingListEntry result = waitingListService.addToWaitingList(1L, "mscuser", "Topic", null, null);

            // Then - Should use supervisor from User entity
            assertNotNull(result);
            assertEquals("Dr. Supervisor", result.getSupervisorName());
            assertEquals("supervisor@example.com", result.getSupervisorEmail());
        }

        @Test
        @DisplayName("Falls back to request supervisor when user has none")
        void fallsBackToRequestSupervisor() {
            // Given - User has no supervisor linked
            User noSupervisorUser = new User();
            noSupervisorUser.setBguUsername("nosupuser");
            noSupervisorUser.setDegree(User.Degree.MSc);
            noSupervisorUser.setSupervisor(null);

            SeminarSlotRegistration phdReg = createRegistration(1L, "existingphd", User.Degree.PhD, ApprovalStatus.APPROVED);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "nosupuser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("nosupuser")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "nosupuser")).thenReturn(false);
            when(userRepository.findByBguUsername("nosupuser")).thenReturn(Optional.of(noSupervisorUser));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(phdReg));
            when(waitingListRepository.findBySlotIdOrderByPositionAsc(1L)).thenReturn(Collections.emptyList());
            when(waitingListRepository.countBySlotId(1L)).thenReturn(0L);
            when(waitingListRepository.save(any(WaitingListEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            // When - Provide supervisor in request
            WaitingListEntry result = waitingListService.addToWaitingList(1L, "nosupuser", "Topic",
                    "Request Supervisor", "request.sup@example.com");

            // Then - Should use supervisor from request
            assertNotNull(result);
            assertEquals("Request Supervisor", result.getSupervisorName());
            assertEquals("request.sup@example.com", result.getSupervisorEmail());
        }

        @Test
        @DisplayName("Throws exception when no supervisor available")
        void throwsWhenNoSupervisorAvailable() {
            // Given - User has no supervisor and none provided in request
            User noSupervisorUser = new User();
            noSupervisorUser.setBguUsername("nosupuser");
            noSupervisorUser.setDegree(User.Degree.MSc);
            noSupervisorUser.setSupervisor(null);

            SeminarSlotRegistration phdReg = createRegistration(1L, "existingphd", User.Degree.PhD, ApprovalStatus.APPROVED);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "nosupuser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("nosupuser")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "nosupuser")).thenReturn(false);
            when(userRepository.findByBguUsername("nosupuser")).thenReturn(Optional.of(noSupervisorUser));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(phdReg));
            when(waitingListRepository.findBySlotIdOrderByPositionAsc(1L)).thenReturn(Collections.emptyList());

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                waitingListService.addToWaitingList(1L, "nosupuser", "Topic", null, null)
            );
            assertTrue(exception.getMessage().contains("Supervisor information is required"));
        }
    }

    // ==================== Offer Promotion Tests ====================

    @Nested
    @DisplayName("Offer Promotion Tests")
    class OfferPromotionTests {

        @Test
        @DisplayName("Offer promotion to first available user - SUCCESS")
        void offerPromotion_Success() {
            // Given - Waiting list has one entry
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 1);
            testSlot.setStatus(SeminarSlot.SlotStatus.SEMI);

            when(waitingListRepository.findAvailableForPromotion(eq(1L), any())).thenReturn(Collections.singletonList(entry));
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
            when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "mscuser")).thenReturn(false);
            when(userRepository.findByBguUsername("mscuser")).thenReturn(Optional.of(mscUser));
            when(waitingListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(true);

            // When
            boolean result = waitingListService.offerPromotionToNextFromWaitingList(1L);

            // Then
            assertTrue(result);
            verify(waitingListRepository).save(argThat(e ->
                    e.getPromotionToken() != null && e.getPromotionOfferedAt() != null));
        }

        @Test
        @DisplayName("No one available for promotion - returns false")
        void offerPromotion_NoOneAvailable() {
            // Given
            when(waitingListRepository.findAvailableForPromotion(eq(1L), any())).thenReturn(Collections.emptyList());

            // When
            boolean result = waitingListService.offerPromotionToNextFromWaitingList(1L);

            // Then
            assertFalse(result);
            verify(waitingListRepository, never()).save(any());
        }

        @Test
        @DisplayName("Slot is full - returns false")
        void offerPromotion_SlotFull() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 1);
            SeminarSlotRegistration reg1 = createRegistration(1L, "user1", User.Degree.MSc, ApprovalStatus.APPROVED);
            SeminarSlotRegistration reg2 = createRegistration(1L, "user2", User.Degree.MSc, ApprovalStatus.APPROVED);
            SeminarSlotRegistration reg3 = createRegistration(1L, "user3", User.Degree.MSc, ApprovalStatus.APPROVED);

            when(waitingListRepository.findAvailableForPromotion(eq(1L), any())).thenReturn(Collections.singletonList(entry));
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Arrays.asList(reg1, reg2, reg3));

            // When
            boolean result = waitingListService.offerPromotionToNextFromWaitingList(1L);

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("Slot has PhD - blocks all promotions")
        void offerPromotion_SlotHasPhd_Blocked() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 1);
            SeminarSlotRegistration phdReg = createRegistration(1L, "phduser", User.Degree.PhD, ApprovalStatus.APPROVED);

            when(waitingListRepository.findAvailableForPromotion(eq(1L), any())).thenReturn(Collections.singletonList(entry));
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(phdReg));

            // When
            boolean result = waitingListService.offerPromotionToNextFromWaitingList(1L);

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("PhD in waiting list skipped when MSc exists - tries next")
        void offerPromotion_PhdSkippedWhenMscExists() {
            // Given - PhD first in line, but MSc registered
            WaitingListEntry phdEntry = createWaitingListEntry(1L, "phduser", User.Degree.PhD, 1);
            SeminarSlotRegistration mscReg = createRegistration(1L, "existingmsc", User.Degree.MSc, ApprovalStatus.APPROVED);

            when(waitingListRepository.findAvailableForPromotion(eq(1L), any()))
                    .thenReturn(Collections.singletonList(phdEntry))
                    .thenReturn(Collections.emptyList()); // After PhD removed
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(mscReg));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "phduser")).thenReturn(Optional.of(phdEntry));

            // When
            boolean result = waitingListService.offerPromotionToNextFromWaitingList(1L);

            // Then - PhD removed, no one else to promote
            assertFalse(result);
            verify(waitingListRepository).deleteBySlotIdAndPresenterUsername(1L, "phduser");
        }

        @Test
        @DisplayName("User already registered - removed from waiting list")
        void offerPromotion_UserAlreadyRegistered() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 1);

            when(waitingListRepository.findAvailableForPromotion(eq(1L), any()))
                    .thenReturn(Collections.singletonList(entry))
                    .thenReturn(Collections.emptyList());
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
            when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "mscuser")).thenReturn(true);
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.of(entry));

            // When
            boolean result = waitingListService.offerPromotionToNextFromWaitingList(1L);

            // Then
            assertFalse(result);
            verify(waitingListRepository).deleteBySlotIdAndPresenterUsername(1L, "mscuser");
        }
    }

    // ==================== Confirm Promotion Tests ====================

    @Nested
    @DisplayName("Confirm Promotion Tests")
    class ConfirmPromotionTests {

        @Test
        @DisplayName("Confirm promotion successfully creates registration")
        void confirmPromotion_Success() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 1);
            entry.setPromotionToken("valid-token");
            entry.setPromotionTokenExpiresAt(java.time.LocalDateTime.now().plusHours(24));
            entry.setSupervisorName("Dr. Supervisor");
            entry.setSupervisorEmail("supervisor@example.com");

            when(waitingListRepository.findByPromotionToken("valid-token")).thenReturn(Optional.of(entry));
            when(userRepository.findByBguUsername("mscuser")).thenReturn(Optional.of(mscUser));
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.emptyList());
            when(registrationRepository.existsByIdSlotIdAndIdPresenterUsername(1L, "mscuser")).thenReturn(false);
            when(registrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(waitingListPromotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(waitingListRepository.findAvailableForPromotion(eq(1L), any())).thenReturn(Collections.emptyList());
            when(appConfigService.getIntegerConfig(eq("approval_token_expiry_days"), anyInt())).thenReturn(14);

            // When
            Optional<SeminarSlotRegistration> result = waitingListService.confirmPromotion("valid-token");

            // Then
            assertTrue(result.isPresent());
            assertEquals("mscuser", result.get().getId().getPresenterUsername());
            assertEquals(ApprovalStatus.PENDING, result.get().getApprovalStatus());
            verify(waitingListRepository).deleteBySlotIdAndPresenterUsername(1L, "mscuser");
        }

        @Test
        @DisplayName("Token not found - returns empty")
        void confirmPromotion_TokenNotFound() {
            // Given
            when(waitingListRepository.findByPromotionToken("invalid-token")).thenReturn(Optional.empty());

            // When
            Optional<SeminarSlotRegistration> result = waitingListService.confirmPromotion("invalid-token");

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Token expired - returns empty")
        void confirmPromotion_TokenExpired() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 1);
            entry.setPromotionToken("expired-token");
            entry.setPromotionTokenExpiresAt(java.time.LocalDateTime.now().minusHours(1)); // Expired

            when(waitingListRepository.findByPromotionToken("expired-token")).thenReturn(Optional.of(entry));

            // When
            Optional<SeminarSlotRegistration> result = waitingListService.confirmPromotion("expired-token");

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Slot now has PhD - returns empty")
        void confirmPromotion_SlotNowHasPhd() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 1);
            entry.setPromotionToken("valid-token");
            entry.setPromotionTokenExpiresAt(java.time.LocalDateTime.now().plusHours(24));
            SeminarSlotRegistration phdReg = createRegistration(1L, "phduser", User.Degree.PhD, ApprovalStatus.APPROVED);

            when(waitingListRepository.findByPromotionToken("valid-token")).thenReturn(Optional.of(entry));
            when(userRepository.findByBguUsername("mscuser")).thenReturn(Optional.of(mscUser));
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(phdReg));

            // When
            Optional<SeminarSlotRegistration> result = waitingListService.confirmPromotion("valid-token");

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("PhD user blocked by MSc - returns empty")
        void confirmPromotion_PhdBlockedByMsc() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "phduser", User.Degree.PhD, 1);
            entry.setPromotionToken("valid-token");
            entry.setPromotionTokenExpiresAt(java.time.LocalDateTime.now().plusHours(24));
            SeminarSlotRegistration mscReg = createRegistration(1L, "mscexisting", User.Degree.MSc, ApprovalStatus.APPROVED);

            when(waitingListRepository.findByPromotionToken("valid-token")).thenReturn(Optional.of(entry));
            when(userRepository.findByBguUsername("phduser")).thenReturn(Optional.of(phdUser));
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(mscReg));

            // When
            Optional<SeminarSlotRegistration> result = waitingListService.confirmPromotion("valid-token");

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Slot now full - returns empty")
        void confirmPromotion_SlotNowFull() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 1);
            entry.setPromotionToken("valid-token");
            entry.setPromotionTokenExpiresAt(java.time.LocalDateTime.now().plusHours(24));
            SeminarSlotRegistration reg1 = createRegistration(1L, "user1", User.Degree.MSc, ApprovalStatus.APPROVED);
            SeminarSlotRegistration reg2 = createRegistration(1L, "user2", User.Degree.MSc, ApprovalStatus.APPROVED);
            SeminarSlotRegistration reg3 = createRegistration(1L, "user3", User.Degree.MSc, ApprovalStatus.APPROVED);

            when(waitingListRepository.findByPromotionToken("valid-token")).thenReturn(Optional.of(entry));
            when(userRepository.findByBguUsername("mscuser")).thenReturn(Optional.of(mscUser));
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Arrays.asList(reg1, reg2, reg3));

            // When
            Optional<SeminarSlotRegistration> result = waitingListService.confirmPromotion("valid-token");

            // Then
            assertFalse(result.isPresent());
        }
    }

    // ==================== Decline Promotion Tests ====================

    @Nested
    @DisplayName("Decline Promotion Tests")
    class DeclinePromotionTests {

        @Test
        @DisplayName("Decline promotion removes user and offers to next")
        void declinePromotion_Success() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 1);
            entry.setPromotionToken("decline-token");

            when(waitingListRepository.findByPromotionToken("decline-token")).thenReturn(Optional.of(entry));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.of(entry));
            when(waitingListRepository.findAvailableForPromotion(eq(1L), any())).thenReturn(Collections.emptyList());

            // When
            waitingListService.declinePromotion("decline-token");

            // Then
            verify(waitingListRepository).deleteBySlotIdAndPresenterUsername(1L, "mscuser");
            verify(waitingListRepository).decrementPositionsAfter(1L, 1);
        }

        @Test
        @DisplayName("Decline with invalid token does nothing")
        void declinePromotion_InvalidToken() {
            // Given
            when(waitingListRepository.findByPromotionToken("invalid-token")).thenReturn(Optional.empty());

            // When
            waitingListService.declinePromotion("invalid-token");

            // Then
            verify(waitingListRepository, never()).deleteBySlotIdAndPresenterUsername(anyLong(), anyString());
        }
    }

    // ==================== Process Expired Promotions Tests ====================

    @Nested
    @DisplayName("Process Expired Promotions Tests")
    class ProcessExpiredPromotionsTests {

        @Test
        @DisplayName("Process expired promotions removes entries")
        void processExpiredPromotions_RemovesExpiredEntries() {
            // Given
            WaitingListEntry expired1 = createWaitingListEntry(1L, "user1", User.Degree.MSc, 1);
            expired1.setPromotionTokenExpiresAt(java.time.LocalDateTime.now().minusHours(1));
            WaitingListEntry expired2 = createWaitingListEntry(2L, "user2", User.Degree.MSc, 1);
            expired2.setPromotionTokenExpiresAt(java.time.LocalDateTime.now().minusHours(2));

            when(waitingListRepository.findExpiredPromotionOffers(any())).thenReturn(Arrays.asList(expired1, expired2));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "user1")).thenReturn(Optional.of(expired1));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(2L, "user2")).thenReturn(Optional.of(expired2));
            when(waitingListRepository.findAvailableForPromotion(anyLong(), any())).thenReturn(Collections.emptyList());

            // When
            waitingListService.processExpiredPromotionOffers();

            // Then
            verify(waitingListRepository).deleteBySlotIdAndPresenterUsername(1L, "user1");
            verify(waitingListRepository).deleteBySlotIdAndPresenterUsername(2L, "user2");
        }

        @Test
        @DisplayName("No expired promotions does nothing")
        void processExpiredPromotions_NoExpired() {
            // Given
            when(waitingListRepository.findExpiredPromotionOffers(any())).thenReturn(Collections.emptyList());

            // When
            waitingListService.processExpiredPromotionOffers();

            // Then
            verify(waitingListRepository, never()).deleteBySlotIdAndPresenterUsername(anyLong(), anyString());
        }
    }

    // ==================== Get Waiting List Entry Tests ====================

    @Nested
    @DisplayName("Get Waiting List Entry Tests")
    class GetWaitingListEntryTests {

        @Test
        @DisplayName("getWaitingListEntryForUser returns entry when exists")
        void getWaitingListEntryForUser_ReturnsEntry() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 1);
            when(waitingListRepository.findByPresenterUsername("mscuser")).thenReturn(Collections.singletonList(entry));

            // When
            Optional<WaitingListEntry> result = waitingListService.getWaitingListEntryForUser("mscuser");

            // Then
            assertTrue(result.isPresent());
            assertEquals("mscuser", result.get().getPresenterUsername());
        }

        @Test
        @DisplayName("getWaitingListEntryForUser returns empty when not exists")
        void getWaitingListEntryForUser_ReturnsEmpty() {
            // Given
            when(waitingListRepository.findByPresenterUsername("unknownuser")).thenReturn(Collections.emptyList());

            // When
            Optional<WaitingListEntry> result = waitingListService.getWaitingListEntryForUser("unknownuser");

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("getWaitingListEntriesForUser returns all entries")
        void getWaitingListEntriesForUser_ReturnsAllEntries() {
            // Given
            WaitingListEntry entry1 = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 1);
            when(waitingListRepository.findByPresenterUsername("mscuser")).thenReturn(Collections.singletonList(entry1));

            // When
            List<WaitingListEntry> result = waitingListService.getWaitingListEntriesForUser("mscuser");

            // Then
            assertEquals(1, result.size());
            assertEquals("mscuser", result.get(0).getPresenterUsername());
        }
    }

    // ==================== PhD Joining MSc Slot Warning Tests ====================

    @Nested
    @DisplayName("PhD Joining MSc Slot Tests")
    class PhdJoiningMscSlotTests {

        @Test
        @DisplayName("PhD can join waiting list for slot with MSc (with warning)")
        void phdJoinsWaitingListWithMscRegistered_Success() {
            // Given - Slot has MSc registered, PhD wants to join empty waiting list
            SeminarSlotRegistration mscReg = createRegistration(1L, "existingmsc", User.Degree.MSc, ApprovalStatus.APPROVED);

            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "phduser")).thenReturn(Optional.empty());
            when(waitingListRepository.existsByPresenterUsername("phduser")).thenReturn(false);
            when(registrationRepository.existsActiveRegistration(1L, "phduser")).thenReturn(false);
            when(userRepository.findByBguUsername("phduser")).thenReturn(Optional.of(phdUser));
            when(registrationRepository.findByIdSlotId(1L)).thenReturn(Collections.singletonList(mscReg));
            when(waitingListRepository.findBySlotIdOrderByPositionAsc(1L)).thenReturn(Collections.emptyList());
            when(waitingListRepository.countBySlotId(1L)).thenReturn(0L);
            when(waitingListRepository.save(any(WaitingListEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            WaitingListEntry result = waitingListService.addToWaitingList(1L, "phduser", "PhD Topic", null, null);

            // Then - Should succeed with warning logged
            assertNotNull(result);
            assertEquals(User.Degree.PhD, result.getDegree());
            verify(databaseLoggerService).logAction(eq("WARN"), eq("WAITING_LIST_PHD_JOINING_MSC_SLOT"),
                    anyString(), eq("phduser"), anyString());
        }
    }

    // ==================== Legacy promoteNextFromWaitingList Tests ====================

    @Nested
    @DisplayName("Legacy Promote Method Tests")
    class LegacyPromoteMethodTests {

        @Test
        @DisplayName("promoteNextFromWaitingList returns empty (delegates to offer)")
        void promoteNextFromWaitingList_ReturnsEmpty() {
            // Given
            when(waitingListRepository.findAvailableForPromotion(eq(1L), any())).thenReturn(Collections.emptyList());

            // When
            Optional<SeminarSlotRegistration> result = waitingListService.promoteNextFromWaitingList(1L);

            // Then
            assertFalse(result.isPresent());
        }
    }

    // ==================== Remove with Cancellation Email Tests ====================

    @Nested
    @DisplayName("Remove with Cancellation Email Tests")
    class RemoveWithCancellationEmailTests {

        @Test
        @DisplayName("Remove sends cancellation email when supervisor email exists")
        void removeWithCancellationEmail_SendsEmail() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 2);
            entry.setSupervisorEmail("supervisor@example.com");
            entry.setSupervisorName("Dr. Supervisor");

            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.of(entry));
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(userRepository.findByBguUsername("mscuser")).thenReturn(Optional.of(mscUser));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(true);

            // When
            WaitingListEntry result = waitingListService.removeFromWaitingList(1L, "mscuser");

            // Then
            assertNotNull(result);
            verify(mailService).sendHtmlEmail(eq("supervisor@example.com"), anyString(), anyString());
        }

        @Test
        @DisplayName("Remove skips email when sendCancellationEmail is false")
        void removeWithoutCancellationEmail_SkipsEmail() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 2);
            entry.setSupervisorEmail("supervisor@example.com");

            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.of(entry));

            // When
            WaitingListEntry result = waitingListService.removeFromWaitingList(1L, "mscuser", false);

            // Then
            assertNotNull(result);
            verify(mailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Remove continues if email send fails")
        void removeWithCancellationEmail_ContinuesOnEmailFailure() {
            // Given
            WaitingListEntry entry = createWaitingListEntry(1L, "mscuser", User.Degree.MSc, 2);
            entry.setSupervisorEmail("supervisor@example.com");

            when(waitingListRepository.findBySlotIdAndPresenterUsername(1L, "mscuser")).thenReturn(Optional.of(entry));
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(userRepository.findByBguUsername("mscuser")).thenReturn(Optional.of(mscUser));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(false);

            // When
            WaitingListEntry result = waitingListService.removeFromWaitingList(1L, "mscuser");

            // Then - Should still succeed
            assertNotNull(result);
            verify(waitingListRepository).deleteBySlotIdAndPresenterUsername(1L, "mscuser");
        }
    }

    // ==================== Helper Methods ====================

    private SeminarSlotRegistration createRegistration(Long slotId, String username, User.Degree degree, ApprovalStatus status) {
        SeminarSlotRegistration reg = new SeminarSlotRegistration();
        reg.setId(new SeminarSlotRegistrationId(slotId, username));
        reg.setDegree(degree);
        reg.setApprovalStatus(status);
        return reg;
    }

    private WaitingListEntry createWaitingListEntry(Long slotId, String username, User.Degree degree, int position) {
        WaitingListEntry entry = new WaitingListEntry();
        entry.setSlotId(slotId);
        entry.setPresenterUsername(username);
        entry.setDegree(degree);
        entry.setPosition(position);
        return entry;
    }
}
