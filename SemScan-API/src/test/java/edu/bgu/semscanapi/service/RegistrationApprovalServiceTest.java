package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.entity.ApprovalStatus;
import edu.bgu.semscanapi.entity.EmailQueue;
import edu.bgu.semscanapi.entity.SeminarSlot;
import edu.bgu.semscanapi.entity.SeminarSlotRegistration;
import edu.bgu.semscanapi.entity.SeminarSlotRegistrationId;
import edu.bgu.semscanapi.entity.User;
import edu.bgu.semscanapi.entity.WaitingListEntry;
import edu.bgu.semscanapi.entity.WaitingListPromotion;
import edu.bgu.semscanapi.repository.SeminarSlotRegistrationRepository;
import edu.bgu.semscanapi.repository.SeminarSlotRepository;
import edu.bgu.semscanapi.repository.UserRepository;
import edu.bgu.semscanapi.repository.WaitingListPromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for RegistrationApprovalService
 * Tests approval/decline workflows, token handling, email sending, and auto-cancellation
 */
@ExtendWith(MockitoExtension.class)
class RegistrationApprovalServiceTest {

    @Mock
    private SeminarSlotRegistrationRepository registrationRepository;

    @Mock
    private SeminarSlotRepository slotRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailService mailService;

    @Mock
    private EmailQueueService emailQueueService;

    @Mock
    private GlobalConfig globalConfig;

    @Mock
    private DatabaseLoggerService databaseLoggerService;

    @Mock
    private PresenterHomeService presenterHomeService;

    @Mock
    private WaitingListPromotionRepository waitingListPromotionRepository;

    @Mock
    private WaitingListService waitingListService;

    @Mock
    private AppConfigService appConfigService;

    @Mock
    private FcmService fcmService;

    @InjectMocks
    private RegistrationApprovalService approvalService;

    private User testUser;
    private SeminarSlot testSlot;
    private SeminarSlotRegistration testRegistration;

    @BeforeEach
    void setUp() {
        // Setup test user
        testUser = new User();
        testUser.setBguUsername("testuser");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setDegree(User.Degree.MSc);
        testUser.setEmail("testuser@bgu.ac.il");

        // Setup test slot
        testSlot = new SeminarSlot();
        testSlot.setSlotId(1L);
        testSlot.setSlotDate(LocalDate.now().plusDays(7));
        testSlot.setStartTime(LocalTime.of(10, 0));
        testSlot.setEndTime(LocalTime.of(11, 0));
        testSlot.setBuilding("Building 37");
        testSlot.setRoom("Room 201");
        testSlot.setCapacity(3);
        testSlot.setStatus(SeminarSlot.SlotStatus.SEMI);

        // Setup test registration
        SeminarSlotRegistrationId registrationId = new SeminarSlotRegistrationId(1L, "testuser");
        testRegistration = new SeminarSlotRegistration();
        testRegistration.setId(registrationId);
        testRegistration.setDegree(User.Degree.MSc);
        testRegistration.setTopic("Test Research Topic");
        testRegistration.setSupervisorEmail("supervisor@bgu.ac.il");
        testRegistration.setSupervisorName("Dr. Supervisor");
        testRegistration.setApprovalStatus(ApprovalStatus.PENDING);
        testRegistration.setApprovalToken("valid-test-token-uuid");
        testRegistration.setApprovalTokenExpiresAt(LocalDateTime.now().plusDays(14));
        testRegistration.setRegisteredAt(LocalDateTime.now());

        // Default mock configurations
        lenient().when(appConfigService.getIntegerConfig(eq("approval_token_expiry_days"), anyInt())).thenReturn(14);
        lenient().when(globalConfig.getApiBaseUrl()).thenReturn("http://localhost:8080/api/v1");
    }

    // ==================== approveRegistrationByToken Tests ====================

    @Nested
    @DisplayName("Approve Registration By Token Tests")
    class ApproveRegistrationByTokenTests {

        @Test
        @DisplayName("Approve valid token - SUCCESS")
        void approveValidToken_Success() {
            // Given
            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(registrationRepository.findByIdPresenterUsername("testuser"))
                    .thenReturn(Collections.singletonList(testRegistration));
            when(waitingListService.getWaitingListEntryForUser("testuser"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByBguUsernameIgnoreCase("testuser"))
                    .thenReturn(Optional.of(testUser));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            lenient().when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(1L, "testuser"))
                    .thenReturn(Optional.empty());

            // When
            approvalService.approveRegistrationByToken("valid-test-token-uuid");

            // Then
            ArgumentCaptor<SeminarSlotRegistration> captor = ArgumentCaptor.forClass(SeminarSlotRegistration.class);
            verify(registrationRepository, atLeastOnce()).save(captor.capture());
            SeminarSlotRegistration savedReg = captor.getValue();
            assertEquals(ApprovalStatus.APPROVED, savedReg.getApprovalStatus());
            assertNotNull(savedReg.getSupervisorApprovedAt());
        }

        @Test
        @DisplayName("Approve with null token - throws IllegalArgumentException")
        void approveNullToken_ThrowsException() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                approvalService.approveRegistrationByToken(null);
            });
        }

        @Test
        @DisplayName("Approve with empty token - throws IllegalArgumentException")
        void approveEmptyToken_ThrowsException() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                approvalService.approveRegistrationByToken("   ");
            });
        }

        @Test
        @DisplayName("Approve with invalid/not found token - throws IllegalArgumentException")
        void approveInvalidToken_ThrowsException() {
            // Given
            when(registrationRepository.findByApprovalToken("invalid-token"))
                    .thenReturn(Optional.empty());
            when(registrationRepository.findByApprovalStatus(ApprovalStatus.PENDING))
                    .thenReturn(Collections.emptyList());

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                approvalService.approveRegistrationByToken("invalid-token");
            });
            assertTrue(exception.getMessage().contains("Registration not found"));
        }

        @Test
        @DisplayName("Approve already approved registration - idempotent success")
        void approveAlreadyApproved_IdempotentSuccess() {
            // Given
            testRegistration.setApprovalStatus(ApprovalStatus.APPROVED);
            testRegistration.setSupervisorApprovedAt(LocalDateTime.now().minusHours(1));

            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));

            // When - should not throw
            assertDoesNotThrow(() -> {
                approvalService.approveRegistrationByToken("valid-test-token-uuid");
            });

            // Then - save should not be called for already approved
            verify(registrationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Approve DECLINED registration - throws IllegalStateException")
        void approveDeclinedRegistration_ThrowsException() {
            // Given
            testRegistration.setApprovalStatus(ApprovalStatus.DECLINED);

            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));

            // When & Then
            assertThrows(IllegalStateException.class, () -> {
                approvalService.approveRegistrationByToken("valid-test-token-uuid");
            });
        }
    }

    // ==================== Expired Token Tests ====================

    @Nested
    @DisplayName("Expired Token Tests")
    class ExpiredTokenTests {

        @Test
        @DisplayName("Approve with expired token - throws IllegalStateException")
        void approveExpiredToken_ThrowsException() {
            // Given
            testRegistration.setApprovalTokenExpiresAt(LocalDateTime.now().minusDays(1));

            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                approvalService.approveRegistrationByToken("valid-test-token-uuid");
            });
            assertTrue(exception.getMessage().contains("expired"));

            // Verify registration status is set to EXPIRED
            ArgumentCaptor<SeminarSlotRegistration> captor = ArgumentCaptor.forClass(SeminarSlotRegistration.class);
            verify(registrationRepository).save(captor.capture());
            assertEquals(ApprovalStatus.EXPIRED, captor.getValue().getApprovalStatus());
        }

        @Test
        @DisplayName("Decline with expired token - throws IllegalStateException")
        void declineExpiredToken_ThrowsException() {
            // Given
            testRegistration.setApprovalTokenExpiresAt(LocalDateTime.now().minusDays(1));

            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When & Then
            assertThrows(IllegalStateException.class, () -> {
                approvalService.declineRegistrationByToken("valid-test-token-uuid", "Some reason");
            });
        }
    }

    // ==================== Decline Registration Tests ====================

    @Nested
    @DisplayName("Decline Registration Tests")
    class DeclineRegistrationTests {

        @Test
        @DisplayName("Decline valid token with reason - SUCCESS")
        void declineValidTokenWithReason_Success() {
            // Given
            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            lenient().when(presenterHomeService.promoteFromWaitingListAfterCancellation(eq(1L), any(SeminarSlot.class)))
                    .thenReturn(Optional.empty());
            lenient().when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(1L, "testuser"))
                    .thenReturn(Optional.empty());

            // When
            approvalService.declineRegistrationByToken("valid-test-token-uuid", "Topic not suitable for seminar");

            // Then
            ArgumentCaptor<SeminarSlotRegistration> captor = ArgumentCaptor.forClass(SeminarSlotRegistration.class);
            verify(registrationRepository, atLeastOnce()).save(captor.capture());
            SeminarSlotRegistration savedReg = captor.getValue();
            assertEquals(ApprovalStatus.DECLINED, savedReg.getApprovalStatus());
            assertNotNull(savedReg.getSupervisorDeclinedAt());
            assertEquals("Topic not suitable for seminar", savedReg.getSupervisorDeclinedReason());
        }

        @Test
        @DisplayName("Decline valid token without reason - SUCCESS")
        void declineValidTokenWithoutReason_Success() {
            // Given
            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            lenient().when(presenterHomeService.promoteFromWaitingListAfterCancellation(eq(1L), any(SeminarSlot.class)))
                    .thenReturn(Optional.empty());
            lenient().when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(1L, "testuser"))
                    .thenReturn(Optional.empty());

            // When
            approvalService.declineRegistrationByToken("valid-test-token-uuid", null);

            // Then
            ArgumentCaptor<SeminarSlotRegistration> captor = ArgumentCaptor.forClass(SeminarSlotRegistration.class);
            verify(registrationRepository, atLeastOnce()).save(captor.capture());
            assertEquals(ApprovalStatus.DECLINED, captor.getValue().getApprovalStatus());
        }

        @Test
        @DisplayName("Decline already declined registration - idempotent success")
        void declineAlreadyDeclined_IdempotentSuccess() {
            // Given
            testRegistration.setApprovalStatus(ApprovalStatus.DECLINED);
            testRegistration.setSupervisorDeclinedAt(LocalDateTime.now().minusHours(1));

            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));

            // When - should not throw
            assertDoesNotThrow(() -> {
                approvalService.declineRegistrationByToken("valid-test-token-uuid", "reason");
            });

            // Then - save should not be called for already declined
            verify(registrationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Decline APPROVED registration - throws IllegalStateException")
        void declineApprovedRegistration_ThrowsException() {
            // Given
            testRegistration.setApprovalStatus(ApprovalStatus.APPROVED);

            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));

            // When & Then
            assertThrows(IllegalStateException.class, () -> {
                approvalService.declineRegistrationByToken("valid-test-token-uuid", "reason");
            });
        }

        @Test
        @DisplayName("Decline with null token - throws IllegalArgumentException")
        void declineNullToken_ThrowsException() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                approvalService.declineRegistrationByToken(null, "reason");
            });
        }
    }

    // ==================== Token Mismatch Tests ====================

    @Nested
    @DisplayName("Token Mismatch Tests")
    class TokenMismatchTests {

        @Test
        @DisplayName("Approve with mismatched token - throws IllegalArgumentException")
        void approveWithMismatchedToken_ThrowsException() {
            // Given
            testRegistration.setApprovalToken("different-token");

            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                approvalService.approveRegistration(1L, "testuser", "wrong-token");
            });
        }

        @Test
        @DisplayName("Decline with mismatched token - throws IllegalArgumentException")
        void declineWithMismatchedToken_ThrowsException() {
            // Given
            testRegistration.setApprovalToken("different-token");

            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                approvalService.declineRegistration(1L, "testuser", "wrong-token", "reason");
            });
        }
    }

    // ==================== sendApprovalEmail Tests ====================

    @Nested
    @DisplayName("Send Approval Email Tests")
    class SendApprovalEmailTests {

        @BeforeEach
        void setUpSelf() throws Exception {
            // Inject self reference for @Transactional calls using reflection
            Field selfField = RegistrationApprovalService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(approvalService, approvalService);
        }

        @Test
        @DisplayName("Send approval email with valid supervisor - SUCCESS")
        void sendApprovalEmailWithValidSupervisor_Success() {
            // Given
            when(slotRepository.findById(1L)).thenReturn(Optional.of(testSlot));
            when(userRepository.findByBguUsernameIgnoreCase("testuser"))
                    .thenReturn(Optional.of(testUser));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findByApprovalToken(anyString()))
                    .thenReturn(Optional.of(testRegistration));
            EmailQueue mockEmailQueue = new EmailQueue();
            mockEmailQueue.setId(1L);
            when(emailQueueService.queueEmail(any(EmailQueue.EmailType.class), anyString(), anyString(), anyString(),
                    any(), anyLong(), anyString())).thenReturn(mockEmailQueue);

            // When
            boolean result = approvalService.sendApprovalEmail(testRegistration);

            // Then
            assertTrue(result);
            verify(emailQueueService).queueEmail(
                    any(EmailQueue.EmailType.class),
                    eq("supervisor@bgu.ac.il"),
                    anyString(),
                    anyString(),
                    any(),
                    eq(1L),
                    eq("testuser")
            );
        }

        @Test
        @DisplayName("Send approval email with missing supervisor email - returns false")
        void sendApprovalEmailWithMissingSupervisor_ReturnsFalse() {
            // Given
            testRegistration.setSupervisorEmail(null);

            // When
            boolean result = approvalService.sendApprovalEmail(testRegistration);

            // Then
            assertFalse(result);
            verify(emailQueueService, never()).queueEmail(any(), anyString(), anyString(), anyString(),
                    any(), anyLong(), anyString());
        }

        @Test
        @DisplayName("Send approval email with empty supervisor email - returns false")
        void sendApprovalEmailWithEmptySupervisor_ReturnsFalse() {
            // Given
            testRegistration.setSupervisorEmail("   ");

            // When
            boolean result = approvalService.sendApprovalEmail(testRegistration);

            // Then
            assertFalse(result);
        }
    }

    // ==================== Auto-Cancel Other Registrations Tests ====================

    @Nested
    @DisplayName("Auto-Cancel Other Registrations Tests")
    class AutoCancelOtherRegistrationsTests {

        @Test
        @DisplayName("Approve cancels other pending registrations for same user")
        void approveCancelsOtherPendingRegistrations() {
            // Given - User has multiple pending registrations
            SeminarSlotRegistration otherPending1 = new SeminarSlotRegistration();
            otherPending1.setId(new SeminarSlotRegistrationId(2L, "testuser"));
            otherPending1.setApprovalStatus(ApprovalStatus.PENDING);
            otherPending1.setDegree(User.Degree.MSc);

            SeminarSlotRegistration otherPending2 = new SeminarSlotRegistration();
            otherPending2.setId(new SeminarSlotRegistrationId(3L, "testuser"));
            otherPending2.setApprovalStatus(ApprovalStatus.PENDING);
            otherPending2.setDegree(User.Degree.MSc);

            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(registrationRepository.findByIdPresenterUsername("testuser"))
                    .thenReturn(Arrays.asList(testRegistration, otherPending1, otherPending2));
            when(waitingListService.getWaitingListEntryForUser("testuser"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByBguUsernameIgnoreCase("testuser"))
                    .thenReturn(Optional.of(testUser));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            lenient().when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(anyLong(), anyString()))
                    .thenReturn(Optional.empty());

            // When
            approvalService.approveRegistrationByToken("valid-test-token-uuid");

            // Then - other pending registrations should be deleted
            verify(registrationRepository).delete(otherPending1);
            verify(registrationRepository).delete(otherPending2);
        }

        @Test
        @DisplayName("Approve removes user from waiting lists")
        void approveRemovesUserFromWaitingLists() {
            // Given - User is on a waiting list
            WaitingListEntry waitingEntry = new WaitingListEntry();
            waitingEntry.setSlotId(5L);
            waitingEntry.setPresenterUsername("testuser");

            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(registrationRepository.findByIdPresenterUsername("testuser"))
                    .thenReturn(Collections.singletonList(testRegistration));
            when(waitingListService.getWaitingListEntryForUser("testuser"))
                    .thenReturn(Optional.of(waitingEntry));
            when(userRepository.findByBguUsernameIgnoreCase("testuser"))
                    .thenReturn(Optional.of(testUser));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            lenient().when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(anyLong(), anyString()))
                    .thenReturn(Optional.empty());

            // When
            approvalService.approveRegistrationByToken("valid-test-token-uuid");

            // Then - user should be removed from waiting list (without cancellation email)
            verify(waitingListService).removeFromWaitingList(5L, "testuser", false);
        }
    }

    // ==================== Waiting List Promotion After Decline Tests ====================

    @Nested
    @DisplayName("Waiting List Promotion After Decline Tests")
    class WaitingListPromotionAfterDeclineTests {

        @Test
        @DisplayName("Decline triggers promotion from waiting list")
        void declineTriggersWaitingListPromotion() {
            // Given
            WaitingListEntry promotedEntry = new WaitingListEntry();
            promotedEntry.setSlotId(1L);
            promotedEntry.setPresenterUsername("nextuser");

            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            when(presenterHomeService.promoteFromWaitingListAfterCancellation(eq(1L), any(SeminarSlot.class)))
                    .thenReturn(Optional.of(promotedEntry));
            lenient().when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(anyLong(), anyString()))
                    .thenReturn(Optional.empty());

            // When
            approvalService.declineRegistrationByToken("valid-test-token-uuid", "reason");

            // Then
            verify(presenterHomeService).promoteFromWaitingListAfterCancellation(eq(1L), any(SeminarSlot.class));
        }

        @Test
        @DisplayName("Decline handles empty waiting list gracefully")
        void declineHandlesEmptyWaitingListGracefully() {
            // Given
            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            when(presenterHomeService.promoteFromWaitingListAfterCancellation(eq(1L), any(SeminarSlot.class)))
                    .thenReturn(Optional.empty());
            lenient().when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(anyLong(), anyString()))
                    .thenReturn(Optional.empty());

            // When - should not throw
            assertDoesNotThrow(() -> {
                approvalService.declineRegistrationByToken("valid-test-token-uuid", "reason");
            });
        }

        @Test
        @DisplayName("Decline handles promotion failure gracefully")
        void declineHandlesPromotionFailureGracefully() {
            // Given
            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            when(presenterHomeService.promoteFromWaitingListAfterCancellation(eq(1L), any(SeminarSlot.class)))
                    .thenThrow(new RuntimeException("Promotion failed"));
            lenient().when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(anyLong(), anyString()))
                    .thenReturn(Optional.empty());

            // When - should not throw (decline operation should still succeed)
            assertDoesNotThrow(() -> {
                approvalService.declineRegistrationByToken("valid-test-token-uuid", "reason");
            });

            // Then - registration should still be declined
            verify(registrationRepository, atLeastOnce()).save(any(SeminarSlotRegistration.class));
        }
    }

    // ==================== WaitingListPromotion Status Update Tests ====================

    @Nested
    @DisplayName("Waiting List Promotion Status Update Tests")
    class WaitingListPromotionStatusUpdateTests {

        @Test
        @DisplayName("Approve updates WaitingListPromotion status to APPROVED")
        void approveUpdatesPromotionStatusToApproved() {
            // Given
            WaitingListPromotion promotion = new WaitingListPromotion();
            promotion.setPromotionId(1L);
            promotion.setSlotId(1L);
            promotion.setPresenterUsername("testuser");
            promotion.setStatus(WaitingListPromotion.PromotionStatus.PENDING);

            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(registrationRepository.findByIdPresenterUsername("testuser"))
                    .thenReturn(Collections.singletonList(testRegistration));
            when(waitingListService.getWaitingListEntryForUser("testuser"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByBguUsernameIgnoreCase("testuser"))
                    .thenReturn(Optional.of(testUser));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(1L, "testuser"))
                    .thenReturn(Optional.of(promotion));

            // When
            approvalService.approveRegistrationByToken("valid-test-token-uuid");

            // Then
            ArgumentCaptor<WaitingListPromotion> captor = ArgumentCaptor.forClass(WaitingListPromotion.class);
            verify(waitingListPromotionRepository).save(captor.capture());
            assertEquals(WaitingListPromotion.PromotionStatus.APPROVED, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("Decline updates WaitingListPromotion status to DECLINED")
        void declineUpdatesPromotionStatusToDeclined() {
            // Given
            WaitingListPromotion promotion = new WaitingListPromotion();
            promotion.setPromotionId(1L);
            promotion.setSlotId(1L);
            promotion.setPresenterUsername("testuser");
            promotion.setStatus(WaitingListPromotion.PromotionStatus.PENDING);

            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            lenient().when(presenterHomeService.promoteFromWaitingListAfterCancellation(eq(1L), any(SeminarSlot.class)))
                    .thenReturn(Optional.empty());
            when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(1L, "testuser"))
                    .thenReturn(Optional.of(promotion));

            // When
            approvalService.declineRegistrationByToken("valid-test-token-uuid", "reason");

            // Then
            ArgumentCaptor<WaitingListPromotion> captor = ArgumentCaptor.forClass(WaitingListPromotion.class);
            verify(waitingListPromotionRepository).save(captor.capture());
            assertEquals(WaitingListPromotion.PromotionStatus.DECLINED, captor.getValue().getStatus());
        }
    }

    // ==================== FCM Notification Tests ====================

    @Nested
    @DisplayName("FCM Notification Tests")
    class FcmNotificationTests {

        @Test
        @DisplayName("Approve sends FCM notification")
        void approveSendsFcmNotification() {
            // Given
            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(registrationRepository.findByIdPresenterUsername("testuser"))
                    .thenReturn(Collections.singletonList(testRegistration));
            when(waitingListService.getWaitingListEntryForUser("testuser"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByBguUsernameIgnoreCase("testuser"))
                    .thenReturn(Optional.of(testUser));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            lenient().when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(anyLong(), anyString()))
                    .thenReturn(Optional.empty());

            // When
            approvalService.approveRegistrationByToken("valid-test-token-uuid");

            // Then
            verify(fcmService).sendApprovalNotification(
                    eq("testuser"),
                    eq(1L),
                    anyString(),
                    eq(true),
                    isNull()
            );
        }

        @Test
        @DisplayName("Decline sends FCM notification with reason")
        void declineSendsFcmNotificationWithReason() {
            // Given
            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            lenient().when(presenterHomeService.promoteFromWaitingListAfterCancellation(eq(1L), any(SeminarSlot.class)))
                    .thenReturn(Optional.empty());
            lenient().when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(anyLong(), anyString()))
                    .thenReturn(Optional.empty());

            // When
            approvalService.declineRegistrationByToken("valid-test-token-uuid", "Not suitable");

            // Then
            verify(fcmService).sendApprovalNotification(
                    eq("testuser"),
                    eq(1L),
                    anyString(),
                    eq(false),
                    eq("Not suitable")
            );
        }

        @Test
        @DisplayName("FCM notification failure does not block approval")
        void fcmFailureDoesNotBlockApproval() {
            // Given
            when(registrationRepository.findByApprovalToken("valid-test-token-uuid"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(registrationRepository.findByIdPresenterUsername("testuser"))
                    .thenReturn(Collections.singletonList(testRegistration));
            when(waitingListService.getWaitingListEntryForUser("testuser"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByBguUsernameIgnoreCase("testuser"))
                    .thenReturn(Optional.of(testUser));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            lenient().when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(anyLong(), anyString()))
                    .thenReturn(Optional.empty());
            doThrow(new RuntimeException("FCM error")).when(fcmService)
                    .sendApprovalNotification(anyString(), anyLong(), anyString(), anyBoolean(), any());

            // When - should not throw
            assertDoesNotThrow(() -> {
                approvalService.approveRegistrationByToken("valid-test-token-uuid");
            });

            // Then - registration should still be approved
            verify(registrationRepository, atLeastOnce()).save(any(SeminarSlotRegistration.class));
        }
    }

    // ==================== Student Confirmation Flow Tests ====================

    @Nested
    @DisplayName("Student Confirmation Flow Tests")
    class StudentConfirmationFlowTests {

        @Test
        @DisplayName("Send student confirmation email - SUCCESS")
        void sendStudentConfirmationEmail_Success() {
            // Given
            when(userRepository.findByBguUsernameIgnoreCase("testuser"))
                    .thenReturn(Optional.of(testUser));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            EmailQueue mockEmailQueue = new EmailQueue();
            mockEmailQueue.setId(1L);
            when(emailQueueService.queueEmail(any(EmailQueue.EmailType.class), anyString(), anyString(), anyString(),
                    any(), anyLong(), anyString())).thenReturn(mockEmailQueue);

            // When
            approvalService.sendStudentConfirmationEmail(testRegistration);

            // Then
            verify(emailQueueService).queueEmail(
                    any(EmailQueue.EmailType.class),
                    eq("testuser@bgu.ac.il"),
                    contains("Confirm Your Waiting List Promotion"),
                    anyString(),
                    any(),
                    eq(1L),
                    eq("testuser")
            );
        }

        @Test
        @DisplayName("Send student confirmation email - user not found")
        void sendStudentConfirmationEmail_UserNotFound() {
            // Given
            when(userRepository.findByBguUsernameIgnoreCase("testuser"))
                    .thenReturn(Optional.empty());

            // When
            approvalService.sendStudentConfirmationEmail(testRegistration);

            // Then - should not attempt to queue email
            verify(emailQueueService, never()).queueEmail(any(EmailQueue.EmailType.class), anyString(), anyString(), anyString(),
                    any(), anyLong(), anyString());
        }

        @Test
        @DisplayName("Confirm student promotion - SUCCESS")
        void confirmStudentPromotion_Success() throws Exception {
            // Given - Inject self reference for @Transactional calls using reflection
            Field selfField = RegistrationApprovalService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(approvalService, approvalService);

            testRegistration.setApprovalToken("confirmation-token");
            testRegistration.setApprovalTokenExpiresAt(LocalDateTime.now().plusDays(1));

            when(registrationRepository.findByApprovalToken("confirmation-token"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            when(userRepository.findByBguUsernameIgnoreCase("testuser"))
                    .thenReturn(Optional.of(testUser));
            EmailQueue mockEmailQueue = new EmailQueue();
            mockEmailQueue.setId(1L);
            when(emailQueueService.queueEmail(any(EmailQueue.EmailType.class), anyString(), anyString(), anyString(),
                    any(), anyLong(), anyString())).thenReturn(mockEmailQueue);

            // When
            approvalService.confirmStudentPromotion("confirmation-token");

            // Then - supervisor approval email should be sent
            verify(emailQueueService).queueEmail(
                    any(EmailQueue.EmailType.class),
                    eq("supervisor@bgu.ac.il"),
                    anyString(),
                    anyString(),
                    any(),
                    eq(1L),
                    eq("testuser")
            );
        }

        @Test
        @DisplayName("Confirm student promotion - expired token")
        void confirmStudentPromotion_ExpiredToken() {
            // Given
            testRegistration.setApprovalToken("confirmation-token");
            testRegistration.setApprovalTokenExpiresAt(LocalDateTime.now().minusDays(1));

            when(registrationRepository.findByApprovalToken("confirmation-token"))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When & Then
            assertThrows(IllegalStateException.class, () -> {
                approvalService.confirmStudentPromotion("confirmation-token");
            });

            // Verify status set to EXPIRED
            ArgumentCaptor<SeminarSlotRegistration> captor = ArgumentCaptor.forClass(SeminarSlotRegistration.class);
            verify(registrationRepository).save(captor.capture());
            assertEquals(ApprovalStatus.EXPIRED, captor.getValue().getApprovalStatus());
        }

        @Test
        @DisplayName("Decline student promotion - SUCCESS")
        void declineStudentPromotion_Success() {
            // Given
            testRegistration.setApprovalToken("confirmation-token");
            testRegistration.setApprovalTokenExpiresAt(LocalDateTime.now().plusDays(1));

            when(registrationRepository.findByApprovalToken("confirmation-token"))
                    .thenReturn(Optional.of(testRegistration));
            when(slotRepository.findById(1L))
                    .thenReturn(Optional.of(testSlot));
            lenient().when(presenterHomeService.promoteFromWaitingListAfterCancellation(eq(1L), any(SeminarSlot.class)))
                    .thenReturn(Optional.empty());
            lenient().when(waitingListPromotionRepository.findByRegistrationSlotIdAndRegistrationPresenterUsername(1L, "testuser"))
                    .thenReturn(Optional.empty());

            // When
            approvalService.declineStudentPromotion("confirmation-token");

            // Then - registration should be deleted
            verify(registrationRepository).delete(testRegistration);
        }
    }

    // ==================== Registration Not Found Tests ====================

    @Nested
    @DisplayName("Registration Not Found Tests")
    class RegistrationNotFoundTests {

        @Test
        @DisplayName("Approve registration not found - throws IllegalArgumentException")
        void approveRegistrationNotFound_ThrowsException() {
            // Given
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                approvalService.approveRegistration(1L, "testuser", "token");
            });
        }

        @Test
        @DisplayName("Decline registration not found - throws IllegalArgumentException")
        void declineRegistrationNotFound_ThrowsException() {
            // Given
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                approvalService.declineRegistration(1L, "testuser", "token", "reason");
            });
        }
    }

    // ==================== saveApprovalToken Tests ====================

    @Nested
    @DisplayName("Save Approval Token Tests")
    class SaveApprovalTokenTests {

        @Test
        @DisplayName("Save approval token - SUCCESS")
        void saveApprovalToken_Success() {
            // Given
            String token = "new-approval-token";
            LocalDateTime expiresAt = LocalDateTime.now().plusDays(14);

            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(testRegistration));
            when(registrationRepository.findByApprovalToken(token))
                    .thenReturn(Optional.of(testRegistration));

            // When
            approvalService.saveApprovalToken(testRegistration, token, expiresAt);

            // Then
            ArgumentCaptor<SeminarSlotRegistration> captor = ArgumentCaptor.forClass(SeminarSlotRegistration.class);
            verify(registrationRepository).save(captor.capture());
            assertEquals(token, captor.getValue().getApprovalToken());
            assertEquals(expiresAt, captor.getValue().getApprovalTokenExpiresAt());
            assertEquals(ApprovalStatus.PENDING, captor.getValue().getApprovalStatus());
        }

        @Test
        @DisplayName("Save approval token - token not saved correctly throws exception")
        void saveApprovalToken_TokenNotSaved_ThrowsException() {
            // Given
            String token = "new-approval-token";
            LocalDateTime expiresAt = LocalDateTime.now().plusDays(14);

            SeminarSlotRegistration savedRegistration = new SeminarSlotRegistration();
            savedRegistration.setId(testRegistration.getId());
            savedRegistration.setApprovalToken("different-token"); // Token mismatch

            when(registrationRepository.save(any(SeminarSlotRegistration.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(registrationRepository.findById(any(SeminarSlotRegistrationId.class)))
                    .thenReturn(Optional.of(savedRegistration));

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                approvalService.saveApprovalToken(testRegistration, token, expiresAt);
            });
        }
    }
}
