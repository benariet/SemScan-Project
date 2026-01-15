package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.config.GlobalConfig;
import edu.bgu.semscanapi.entity.EmailLog;
import edu.bgu.semscanapi.entity.EmailQueue;
import edu.bgu.semscanapi.repository.EmailLogRepository;
import edu.bgu.semscanapi.repository.EmailQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for EmailQueueService.
 * Tests email validation, queue operations, processing, and retry logic.
 */
@ExtendWith(MockitoExtension.class)
class EmailQueueServiceTest {

    @Mock
    private EmailQueueRepository emailQueueRepository;

    @Mock
    private EmailLogRepository emailLogRepository;

    @Mock
    private MailService mailService;

    @Mock
    private DatabaseLoggerService dbLogger;

    @Mock
    private GlobalConfig globalConfig;

    @InjectMocks
    private EmailQueueService emailQueueService;

    private EmailQueue testEmailQueue;

    @BeforeEach
    void setUp() {
        // Default config mocks
        lenient().when(globalConfig.getEmailQueueMaxRetries()).thenReturn(3);
        lenient().when(globalConfig.getEmailQueueInitialBackoffMinutes()).thenReturn(5);
        lenient().when(globalConfig.getEmailQueueBackoffMultiplier()).thenReturn(3);
        lenient().when(globalConfig.getEmailQueueBatchSize()).thenReturn(50);

        // Setup test email queue entry
        testEmailQueue = new EmailQueue();
        testEmailQueue.setId(1L);
        testEmailQueue.setToEmail("supervisor@bgu.ac.il");
        testEmailQueue.setSubject("Test Subject");
        testEmailQueue.setHtmlContent("<html><body>Test Content</body></html>");
        testEmailQueue.setEmailType(EmailQueue.EmailType.SUPERVISOR_APPROVAL);
        testEmailQueue.setRegistrationId(100L);
        testEmailQueue.setSlotId(10L);
        testEmailQueue.setUsername("testuser");
        testEmailQueue.setStatus(EmailQueue.Status.PENDING);
        testEmailQueue.setRetryCount(0);
        testEmailQueue.setMaxRetries(3);
        testEmailQueue.setScheduledAt(LocalDateTime.now());

        // Mock email log save
        lenient().when(emailLogRepository.save(any(EmailLog.class))).thenAnswer(inv -> {
            EmailLog log = inv.getArgument(0);
            log.setId(1L);
            return log;
        });
    }

    // ==================== Email Validation Tests ====================

    @Nested
    @DisplayName("Email Validation Tests")
    class EmailValidationTests {

        @Test
        @DisplayName("Valid email passes validation")
        void validEmail_ReturnsNull() {
            String result = emailQueueService.validateEmail("valid.email@bgu.ac.il");
            assertNull(result, "Valid email should return null (no error)");
        }

        @Test
        @DisplayName("Null email fails validation")
        void nullEmail_ReturnsError() {
            String result = emailQueueService.validateEmail(null);
            assertNotNull(result);
            assertTrue(result.contains("empty"));
        }

        @Test
        @DisplayName("Empty email fails validation")
        void emptyEmail_ReturnsError() {
            String result = emailQueueService.validateEmail("");
            assertNotNull(result);
            assertTrue(result.contains("empty"));
        }

        @Test
        @DisplayName("Whitespace-only email fails validation")
        void whitespaceEmail_ReturnsError() {
            String result = emailQueueService.validateEmail("   ");
            assertNotNull(result);
            assertTrue(result.contains("empty"));
        }

        @Test
        @DisplayName("Invalid email format fails validation")
        void invalidFormat_ReturnsError() {
            String result = emailQueueService.validateEmail("not-an-email");
            assertNotNull(result);
            assertTrue(result.contains("Invalid email format"));
        }

        @Test
        @DisplayName("Email without @ fails validation")
        void emailWithoutAt_ReturnsError() {
            String result = emailQueueService.validateEmail("userexample.com");
            assertNotNull(result);
            assertTrue(result.contains("Invalid email format"));
        }

        @Test
        @DisplayName("Email with consecutive dots fails validation")
        void emailWithConsecutiveDots_ReturnsError() {
            String result = emailQueueService.validateEmail("user..name@bgu.ac.il");
            assertNotNull(result);
            assertTrue(result.contains("consecutive dots"));
        }

        @Test
        @DisplayName("Email starting with dot fails validation")
        void emailStartingWithDot_ReturnsError() {
            String result = emailQueueService.validateEmail(".user@bgu.ac.il");
            assertNotNull(result);
            assertTrue(result.contains("starts or ends with dot"));
        }

        @Test
        @DisplayName("Email ending with dot in local part passes current validation")
        void emailEndingWithDotInLocalPart_PassesValidation() {
            // Note: The current validation only checks if the WHOLE email string starts/ends with dot,
            // not if the local part (before @) ends with a dot. This edge case is allowed by the regex.
            // If stricter RFC 5321 compliance is needed, the validation should be enhanced.
            String result = emailQueueService.validateEmail("user.@bgu.ac.il");
            // Current implementation allows this - the email passes validation
            assertNull(result, "Current validation does not catch dot at end of local part");
        }

        @Test
        @DisplayName("Test domain email fails validation")
        void testDomainEmail_ReturnsError() {
            String result = emailQueueService.validateEmail("user@example.com");
            assertNotNull(result);
            assertTrue(result.contains("test/example domain"));
        }

        @Test
        @DisplayName("Blocked generic BGU email fails validation")
        void blockedGenericEmail_ReturnsError() {
            String result = emailQueueService.validateEmail("test@bgu.ac.il");
            assertNotNull(result);
            assertTrue(result.contains("generic address"));
        }

        @Test
        @DisplayName("Blocked supervisor@bgu.ac.il fails validation")
        void blockedSupervisorEmail_ReturnsError() {
            String result = emailQueueService.validateEmail("supervisor@bgu.ac.il");
            assertNotNull(result);
            assertTrue(result.contains("generic address"));
        }

        @Test
        @DisplayName("Blocked prof@bgu.ac.il fails validation")
        void blockedProfEmail_ReturnsError() {
            String result = emailQueueService.validateEmail("prof@bgu.ac.il");
            assertNotNull(result);
            assertTrue(result.contains("generic address"));
        }

        @Test
        @DisplayName("Valid BGU email with numbers passes validation")
        void validBguEmailWithNumbers_ReturnsNull() {
            String result = emailQueueService.validateEmail("prof123@bgu.ac.il");
            assertNull(result, "Valid email with numbers should pass");
        }

        @Test
        @DisplayName("Valid external email passes validation")
        void validExternalEmail_ReturnsNull() {
            String result = emailQueueService.validateEmail("professor@gmail.com");
            assertNull(result, "Valid external email should pass");
        }

        @Test
        @DisplayName("Email validation is case-insensitive for blocked emails")
        void blockedEmailCaseInsensitive_ReturnsError() {
            String result = emailQueueService.validateEmail("SUPERVISOR@BGU.AC.IL");
            assertNotNull(result);
            assertTrue(result.contains("generic address"));
        }
    }

    // ==================== Supervisor Email Validation Tests ====================

    @Nested
    @DisplayName("Supervisor Email Validation Tests")
    class SupervisorEmailValidationTests {

        @Test
        @DisplayName("Valid supervisor email returns valid result")
        void validSupervisorEmail_ReturnsValid() {
            EmailQueueService.EmailValidationResult result =
                    emailQueueService.validateSupervisorEmail("dr.supervisor@bgu.ac.il", "testuser");

            assertTrue(result.isValid());
            assertNull(result.getErrorMessage());
            assertNull(result.getErrorCode());
        }

        @Test
        @DisplayName("Null supervisor email returns invalid with SUPERVISOR_EMAIL_MISSING code")
        void nullSupervisorEmail_ReturnsMissingCode() {
            EmailQueueService.EmailValidationResult result =
                    emailQueueService.validateSupervisorEmail(null, "testuser");

            assertFalse(result.isValid());
            assertNotNull(result.getErrorMessage());
            assertEquals("SUPERVISOR_EMAIL_MISSING", result.getErrorCode());
            assertTrue(result.getErrorMessage().contains("missing"));
        }

        @Test
        @DisplayName("Empty supervisor email returns invalid with SUPERVISOR_EMAIL_MISSING code")
        void emptySupervisorEmail_ReturnsMissingCode() {
            EmailQueueService.EmailValidationResult result =
                    emailQueueService.validateSupervisorEmail("", "testuser");

            assertFalse(result.isValid());
            assertEquals("SUPERVISOR_EMAIL_MISSING", result.getErrorCode());
        }

        @Test
        @DisplayName("Invalid format supervisor email returns SUPERVISOR_EMAIL_INVALID_FORMAT code")
        void invalidFormatSupervisorEmail_ReturnsInvalidFormatCode() {
            EmailQueueService.EmailValidationResult result =
                    emailQueueService.validateSupervisorEmail("not-an-email", "testuser");

            assertFalse(result.isValid());
            assertEquals("SUPERVISOR_EMAIL_INVALID_FORMAT", result.getErrorCode());
            assertTrue(result.getErrorMessage().contains("invalid"));
        }

        @Test
        @DisplayName("Blocked generic supervisor email returns SUPERVISOR_EMAIL_INVALID_FORMAT code")
        void blockedGenericSupervisorEmail_ReturnsInvalidFormatCode() {
            EmailQueueService.EmailValidationResult result =
                    emailQueueService.validateSupervisorEmail("supervisor@bgu.ac.il", "testuser");

            assertFalse(result.isValid());
            assertEquals("SUPERVISOR_EMAIL_INVALID_FORMAT", result.getErrorCode());
        }

        @Test
        @DisplayName("Supervisor validation logs to database")
        void supervisorValidation_LogsToDatabase() {
            emailQueueService.validateSupervisorEmail("dr.supervisor@bgu.ac.il", "testuser");

            verify(dbLogger, atLeastOnce()).logAction(eq("INFO"), anyString(), anyString(), eq("testuser"), any());
        }
    }

    // ==================== Queue Email Tests ====================

    @Nested
    @DisplayName("Queue Email Tests")
    class QueueEmailTests {

        @Test
        @DisplayName("Queue email creates EmailQueue with correct fields")
        void queueEmail_CreatesCorrectEntry() {
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> {
                EmailQueue eq = inv.getArgument(0);
                eq.setId(1L);
                return eq;
            });

            EmailQueue result = emailQueueService.queueEmail(
                    EmailQueue.EmailType.SUPERVISOR_APPROVAL,
                    "supervisor@bgu.ac.il",
                    "Test Subject",
                    "<html>Test</html>",
                    100L,
                    10L,
                    "testuser"
            );

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("supervisor@bgu.ac.il", result.getToEmail());
            assertEquals("Test Subject", result.getSubject());
            assertEquals(EmailQueue.EmailType.SUPERVISOR_APPROVAL, result.getEmailType());
            assertEquals(EmailQueue.Status.PENDING, result.getStatus());
            assertEquals(0, result.getRetryCount());
            assertEquals(100L, result.getRegistrationId());
            assertEquals(10L, result.getSlotId());
            assertEquals("testuser", result.getUsername());
        }

        @Test
        @DisplayName("Queue email with scheduled time creates entry with correct scheduledAt")
        void queueEmailWithScheduledTime_SetsScheduledAt() {
            LocalDateTime scheduledAt = LocalDateTime.now().plusHours(1);
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> {
                EmailQueue eq = inv.getArgument(0);
                eq.setId(1L);
                return eq;
            });

            emailQueueService.queueEmail(
                    EmailQueue.EmailType.SUPERVISOR_REMINDER,
                    "supervisor@bgu.ac.il",
                    "Reminder",
                    "<html>Reminder</html>",
                    100L,
                    10L,
                    "testuser",
                    scheduledAt
            );

            ArgumentCaptor<EmailQueue> captor = ArgumentCaptor.forClass(EmailQueue.class);
            verify(emailQueueRepository).save(captor.capture());
            assertEquals(scheduledAt, captor.getValue().getScheduledAt());
        }

        @Test
        @DisplayName("Queue email sets max retries from config")
        void queueEmail_SetsMaxRetriesFromConfig() {
            when(globalConfig.getEmailQueueMaxRetries()).thenReturn(5);
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> {
                EmailQueue eq = inv.getArgument(0);
                eq.setId(1L);
                return eq;
            });

            emailQueueService.queueEmail(
                    EmailQueue.EmailType.SUPERVISOR_APPROVAL,
                    "supervisor@bgu.ac.il",
                    "Test",
                    "<html>Test</html>",
                    100L,
                    10L,
                    "testuser"
            );

            ArgumentCaptor<EmailQueue> captor = ArgumentCaptor.forClass(EmailQueue.class);
            verify(emailQueueRepository).save(captor.capture());
            assertEquals(5, captor.getValue().getMaxRetries());
        }

        @Test
        @DisplayName("Queue email logs to email_log table")
        void queueEmail_LogsToEmailLog() {
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> {
                EmailQueue eq = inv.getArgument(0);
                eq.setId(1L);
                return eq;
            });

            emailQueueService.queueEmail(
                    EmailQueue.EmailType.SUPERVISOR_APPROVAL,
                    "supervisor@bgu.ac.il",
                    "Test",
                    "<html>Test</html>",
                    100L,
                    10L,
                    "testuser"
            );

            ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
            verify(emailLogRepository).save(logCaptor.capture());
            EmailLog savedLog = logCaptor.getValue();
            assertEquals("supervisor@bgu.ac.il", savedLog.getToEmail());
            assertEquals(EmailLog.Status.QUEUED, savedLog.getStatus());
        }

        @Test
        @DisplayName("Queue email logs action to app_logs")
        void queueEmail_LogsActionToAppLogs() {
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> {
                EmailQueue eq = inv.getArgument(0);
                eq.setId(1L);
                return eq;
            });

            emailQueueService.queueEmail(
                    EmailQueue.EmailType.SUPERVISOR_APPROVAL,
                    "supervisor@bgu.ac.il",
                    "Test",
                    "<html>Test</html>",
                    100L,
                    10L,
                    "testuser"
            );

            verify(dbLogger).logAction(eq("INFO"), eq("EMAIL_QUEUED"), anyString(), eq("testuser"), anyString());
        }
    }

    // ==================== Process Queue Tests ====================

    @Nested
    @DisplayName("Process Queue Tests")
    class ProcessQueueTests {

        @Test
        @DisplayName("Process queue with no pending emails returns 0")
        void processQueue_NoPendingEmails_ReturnsZero() {
            when(emailQueueRepository.resetStuckProcessingEmails(any(LocalDateTime.class))).thenReturn(0);
            when(emailQueueRepository.findPendingEmailsReadyToSendWithLimit(any(), any(PageRequest.class)))
                    .thenReturn(Collections.emptyList());

            int result = emailQueueService.processQueue();

            assertEquals(0, result);
        }

        @Test
        @DisplayName("Process queue resets stuck processing emails")
        void processQueue_ResetsStuckEmails() {
            when(emailQueueRepository.resetStuckProcessingEmails(any(LocalDateTime.class))).thenReturn(2);
            when(emailQueueRepository.findPendingEmailsReadyToSendWithLimit(any(), any(PageRequest.class)))
                    .thenReturn(Collections.emptyList());

            emailQueueService.processQueue();

            verify(emailQueueRepository).resetStuckProcessingEmails(any(LocalDateTime.class));
            verify(dbLogger).logAction(eq("WARN"), eq("EMAIL_STUCK_RESET"), contains("2"), isNull(), isNull());
        }

        @Test
        @DisplayName("Process queue resets stuck emails and still processes pending emails")
        void processQueue_ResetsStuckEmailsAndProcessesPending() {
            testEmailQueue.setStatus(EmailQueue.Status.PENDING);
            when(emailQueueRepository.resetStuckProcessingEmails(any(LocalDateTime.class))).thenReturn(1);
            when(emailQueueRepository.findPendingEmailsReadyToSendWithLimit(any(), any(PageRequest.class)))
                    .thenReturn(Collections.singletonList(testEmailQueue));
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(true);

            int result = emailQueueService.processQueue();

            assertEquals(1, result);
            verify(emailQueueRepository).resetStuckProcessingEmails(any(LocalDateTime.class));
            verify(mailService).sendHtmlEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Process queue sends pending emails")
        void processQueue_SendsPendingEmails() {
            testEmailQueue.setStatus(EmailQueue.Status.PENDING);
            when(emailQueueRepository.resetStuckProcessingEmails(any(LocalDateTime.class))).thenReturn(0);
            when(emailQueueRepository.findPendingEmailsReadyToSendWithLimit(any(), any(PageRequest.class)))
                    .thenReturn(Collections.singletonList(testEmailQueue));
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(true);

            int result = emailQueueService.processQueue();

            assertEquals(1, result);
            verify(mailService).sendHtmlEmail(
                    eq("supervisor@bgu.ac.il"),
                    eq("Test Subject"),
                    eq("<html><body>Test Content</body></html>")
            );
        }

        @Test
        @DisplayName("Process queue uses batch size from config")
        void processQueue_UsesBatchSizeFromConfig() {
            when(globalConfig.getEmailQueueBatchSize()).thenReturn(25);
            when(emailQueueRepository.resetStuckProcessingEmails(any(LocalDateTime.class))).thenReturn(0);
            when(emailQueueRepository.findPendingEmailsReadyToSendWithLimit(any(), any(PageRequest.class)))
                    .thenReturn(Collections.emptyList());

            emailQueueService.processQueue();

            ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
            verify(emailQueueRepository).findPendingEmailsReadyToSendWithLimit(any(), pageCaptor.capture());
            assertEquals(25, pageCaptor.getValue().getPageSize());
        }

        @Test
        @DisplayName("Process queue handles multiple emails")
        void processQueue_HandlesMultipleEmails() {
            EmailQueue email1 = createTestEmailQueue(1L, "email1@bgu.ac.il");
            EmailQueue email2 = createTestEmailQueue(2L, "email2@bgu.ac.il");
            EmailQueue email3 = createTestEmailQueue(3L, "email3@bgu.ac.il");

            when(emailQueueRepository.resetStuckProcessingEmails(any(LocalDateTime.class))).thenReturn(0);
            when(emailQueueRepository.findPendingEmailsReadyToSendWithLimit(any(), any(PageRequest.class)))
                    .thenReturn(Arrays.asList(email1, email2, email3));
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(true);

            int result = emailQueueService.processQueue();

            assertEquals(3, result);
            verify(mailService, times(3)).sendHtmlEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Process queue logs batch completion")
        void processQueue_LogsBatchCompletion() {
            when(emailQueueRepository.resetStuckProcessingEmails(any(LocalDateTime.class))).thenReturn(0);
            when(emailQueueRepository.findPendingEmailsReadyToSendWithLimit(any(), any(PageRequest.class)))
                    .thenReturn(Collections.singletonList(testEmailQueue));
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(true);

            emailQueueService.processQueue();

            verify(dbLogger).logAction(eq("INFO"), eq("EMAIL_BATCH_PROCESSING_COMPLETE"), anyString(), isNull(), isNull());
        }
    }

    // ==================== Process Single Email Tests ====================

    @Nested
    @DisplayName("Process Single Email Tests")
    class ProcessSingleEmailTests {

        @Test
        @DisplayName("Process email success updates status to SENT")
        void processEmail_Success_UpdatesStatusToSent() {
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(true);

            boolean result = emailQueueService.processEmail(testEmailQueue);

            assertTrue(result);
            assertEquals(EmailQueue.Status.SENT, testEmailQueue.getStatus());
            assertNotNull(testEmailQueue.getSentAt());
            assertNull(testEmailQueue.getLastError());
        }

        @Test
        @DisplayName("Process email success increments retry count")
        void processEmail_Success_IncrementsRetryCount() {
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(true);

            emailQueueService.processEmail(testEmailQueue);

            assertEquals(1, testEmailQueue.getRetryCount());
        }

        @Test
        @DisplayName("Process email success logs to email_log")
        void processEmail_Success_LogsSuccess() {
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(true);

            emailQueueService.processEmail(testEmailQueue);

            ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
            verify(emailLogRepository).save(logCaptor.capture());
            assertEquals(EmailLog.Status.SENT, logCaptor.getValue().getStatus());
        }

        @Test
        @DisplayName("Process email sets status to PROCESSING before send")
        void processEmail_SetsProcessingStatus() {
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(true);

            emailQueueService.processEmail(testEmailQueue);

            // Verify save was called at least twice - once for PROCESSING, once for SENT
            verify(emailQueueRepository, atLeast(2)).save(testEmailQueue);
        }

        @Test
        @DisplayName("Process email sets lastAttemptAt")
        void processEmail_SetsLastAttemptAt() {
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(true);

            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            emailQueueService.processEmail(testEmailQueue);
            LocalDateTime after = LocalDateTime.now().plusSeconds(1);

            assertNotNull(testEmailQueue.getLastAttemptAt());
            assertTrue(testEmailQueue.getLastAttemptAt().isAfter(before));
            assertTrue(testEmailQueue.getLastAttemptAt().isBefore(after));
        }
    }

    // ==================== Retry Logic Tests ====================

    @Nested
    @DisplayName("Retry Logic Tests")
    class RetryLogicTests {

        @Test
        @DisplayName("Failed email stays PENDING if retries remaining")
        void failedEmail_RetriesRemaining_StaysPending() {
            testEmailQueue.setRetryCount(0);
            testEmailQueue.setMaxRetries(3);
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(false);

            boolean result = emailQueueService.processEmail(testEmailQueue);

            assertFalse(result);
            assertEquals(EmailQueue.Status.PENDING, testEmailQueue.getStatus());
        }

        @Test
        @DisplayName("Failed email applies exponential backoff - first retry")
        void failedEmail_FirstRetry_AppliesBackoff() {
            testEmailQueue.setRetryCount(0);
            testEmailQueue.setMaxRetries(3);
            when(globalConfig.getEmailQueueInitialBackoffMinutes()).thenReturn(5);
            when(globalConfig.getEmailQueueBackoffMultiplier()).thenReturn(3);
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(false);

            LocalDateTime before = LocalDateTime.now();
            emailQueueService.processEmail(testEmailQueue);

            // First failure: 5 * 3^0 = 5 minutes backoff
            assertNotNull(testEmailQueue.getScheduledAt());
            assertTrue(testEmailQueue.getScheduledAt().isAfter(before.plusMinutes(4)));
            assertTrue(testEmailQueue.getScheduledAt().isBefore(before.plusMinutes(6)));
        }

        @Test
        @DisplayName("Failed email applies exponential backoff - second retry")
        void failedEmail_SecondRetry_AppliesExponentialBackoff() {
            testEmailQueue.setRetryCount(1); // Already failed once
            testEmailQueue.setMaxRetries(3);
            when(globalConfig.getEmailQueueInitialBackoffMinutes()).thenReturn(5);
            when(globalConfig.getEmailQueueBackoffMultiplier()).thenReturn(3);
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(false);

            LocalDateTime before = LocalDateTime.now();
            emailQueueService.processEmail(testEmailQueue);

            // Second failure (retry count becomes 2): 5 * 3^1 = 15 minutes backoff
            assertNotNull(testEmailQueue.getScheduledAt());
            assertTrue(testEmailQueue.getScheduledAt().isAfter(before.plusMinutes(14)));
            assertTrue(testEmailQueue.getScheduledAt().isBefore(before.plusMinutes(16)));
        }

        @Test
        @DisplayName("Failed email applies exponential backoff - third retry")
        void failedEmail_ThirdRetry_AppliesExponentialBackoff() {
            testEmailQueue.setRetryCount(2); // Already failed twice
            testEmailQueue.setMaxRetries(4); // Allow one more retry
            when(globalConfig.getEmailQueueInitialBackoffMinutes()).thenReturn(5);
            when(globalConfig.getEmailQueueBackoffMultiplier()).thenReturn(3);
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(false);

            LocalDateTime before = LocalDateTime.now();
            emailQueueService.processEmail(testEmailQueue);

            // Third failure (retry count becomes 3): 5 * 3^2 = 45 minutes backoff
            assertNotNull(testEmailQueue.getScheduledAt());
            assertTrue(testEmailQueue.getScheduledAt().isAfter(before.plusMinutes(44)));
            assertTrue(testEmailQueue.getScheduledAt().isBefore(before.plusMinutes(46)));
        }

        @Test
        @DisplayName("Failed email becomes FAILED after max retries")
        void failedEmail_MaxRetriesReached_BecomesFailed() {
            testEmailQueue.setRetryCount(2); // Already at 2
            testEmailQueue.setMaxRetries(3); // Max is 3, next attempt will hit max
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(false);

            boolean result = emailQueueService.processEmail(testEmailQueue);

            assertFalse(result);
            assertEquals(EmailQueue.Status.FAILED, testEmailQueue.getStatus());
        }

        @Test
        @DisplayName("Failed email stores error message")
        void failedEmail_StoresErrorMessage() {
            testEmailQueue.setRetryCount(0);
            testEmailQueue.setMaxRetries(3);
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(false);

            emailQueueService.processEmail(testEmailQueue);

            assertNotNull(testEmailQueue.getLastError());
            assertEquals("EMAIL_SEND_RETURNED_FALSE", testEmailQueue.getLastErrorCode());
        }

        @Test
        @DisplayName("Exception during send stores exception message")
        void sendException_StoresExceptionMessage() {
            testEmailQueue.setRetryCount(0);
            testEmailQueue.setMaxRetries(3);
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("SMTP connection failed"));

            emailQueueService.processEmail(testEmailQueue);

            assertEquals("SMTP connection failed", testEmailQueue.getLastError());
            assertEquals("EMAIL_EXCEPTION", testEmailQueue.getLastErrorCode());
        }

        @Test
        @DisplayName("Permanently failed email logs error to database")
        void permanentlyFailedEmail_LogsError() {
            testEmailQueue.setRetryCount(2);
            testEmailQueue.setMaxRetries(3);
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(false);

            emailQueueService.processEmail(testEmailQueue);

            verify(dbLogger).logError(eq("EMAIL_PERMANENTLY_FAILED"), anyString(), isNull(), eq("testuser"), anyString());
        }

        @Test
        @DisplayName("Retry failure logs warning to database")
        void retryFailure_LogsWarning() {
            testEmailQueue.setRetryCount(0);
            testEmailQueue.setMaxRetries(3);
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(false);

            emailQueueService.processEmail(testEmailQueue);

            verify(dbLogger).logAction(eq("WARN"), eq("EMAIL_SEND_FAILED_WILL_RETRY"), anyString(), eq("testuser"), anyString());
        }
    }

    // ==================== Cancel Emails Tests ====================

    @Nested
    @DisplayName("Cancel Emails Tests")
    class CancelEmailsTests {

        @Test
        @DisplayName("Cancel emails for registration calls repository")
        void cancelEmailsForRegistration_CallsRepository() {
            when(emailQueueRepository.cancelPendingEmailsForRegistration(100L)).thenReturn(2);

            int result = emailQueueService.cancelEmailsForRegistration(100L);

            assertEquals(2, result);
            verify(emailQueueRepository).cancelPendingEmailsForRegistration(100L);
        }

        @Test
        @DisplayName("Cancel emails logs when emails cancelled")
        void cancelEmails_LogsWhenCancelled() {
            when(emailQueueRepository.cancelPendingEmailsForRegistration(100L)).thenReturn(3);

            emailQueueService.cancelEmailsForRegistration(100L);

            verify(dbLogger).logAction(eq("INFO"), eq("EMAIL_CANCELLED_FOR_REGISTRATION"),
                    contains("3"), isNull(), contains("100"));
        }

        @Test
        @DisplayName("Cancel emails does not log when no emails cancelled")
        void cancelEmails_NoLogWhenNoCancelled() {
            when(emailQueueRepository.cancelPendingEmailsForRegistration(100L)).thenReturn(0);

            emailQueueService.cancelEmailsForRegistration(100L);

            verify(dbLogger, never()).logAction(eq("INFO"), eq("EMAIL_CANCELLED_FOR_REGISTRATION"),
                    anyString(), any(), any());
        }
    }

    // ==================== Utility Method Tests ====================

    @Nested
    @DisplayName("Utility Method Tests")
    class UtilityMethodTests {

        @Test
        @DisplayName("formatDateTime handles null")
        void formatDateTime_HandlesNull() {
            String result = EmailQueueService.formatDateTime(null);
            assertEquals("N/A", result);
        }

        @Test
        @DisplayName("formatDateTime formats correctly")
        void formatDateTime_FormatsCorrectly() {
            LocalDateTime dateTime = LocalDateTime.of(2025, 6, 15, 14, 30);
            String result = EmailQueueService.formatDateTime(dateTime);
            assertTrue(result.contains("June"));
            assertTrue(result.contains("15"));
            assertTrue(result.contains("2025"));
            assertTrue(result.contains("2:30"));
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Process queue handles exception in single email gracefully")
        void processQueue_HandlesExceptionInSingleEmail() {
            EmailQueue email1 = createTestEmailQueue(1L, "email1@bgu.ac.il");
            EmailQueue email2 = createTestEmailQueue(2L, "email2@bgu.ac.il");

            when(emailQueueRepository.resetStuckProcessingEmails(any(LocalDateTime.class))).thenReturn(0);
            when(emailQueueRepository.findPendingEmailsReadyToSendWithLimit(any(), any(PageRequest.class)))
                    .thenReturn(Arrays.asList(email1, email2));
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));

            // First email throws exception, second succeeds
            when(mailService.sendHtmlEmail(eq("email1@bgu.ac.il"), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Network error"));
            when(mailService.sendHtmlEmail(eq("email2@bgu.ac.il"), anyString(), anyString()))
                    .thenReturn(true);

            int result = emailQueueService.processQueue();

            // Should continue processing and count the successful one
            assertEquals(1, result);
        }

        @Test
        @DisplayName("Email log save failure does not break email processing")
        void emailLogSaveFailure_DoesNotBreakProcessing() {
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mailService.sendHtmlEmail(anyString(), anyString(), anyString())).thenReturn(true);
            when(emailLogRepository.save(any(EmailLog.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // Should not throw exception
            assertDoesNotThrow(() -> emailQueueService.processEmail(testEmailQueue));
        }

        @Test
        @DisplayName("Queue email with null optional fields succeeds")
        void queueEmail_NullOptionalFields_Succeeds() {
            when(emailQueueRepository.save(any(EmailQueue.class))).thenAnswer(inv -> {
                EmailQueue eq = inv.getArgument(0);
                eq.setId(1L);
                return eq;
            });

            EmailQueue result = emailQueueService.queueEmail(
                    EmailQueue.EmailType.SUPERVISOR_APPROVAL,
                    "supervisor@bgu.ac.il",
                    "Test",
                    "<html>Test</html>",
                    null, // registrationId
                    null, // slotId
                    null  // username
            );

            assertNotNull(result);
            assertNull(result.getRegistrationId());
            assertNull(result.getSlotId());
            assertNull(result.getUsername());
        }
    }

    // ==================== All Blocked Emails Tests ====================

    @Nested
    @DisplayName("All Blocked Generic Emails Tests")
    class AllBlockedEmailsTests {

        @Test
        @DisplayName("All blocked generic BGU emails are rejected")
        void allBlockedEmails_AreRejected() {
            List<String> blockedEmails = Arrays.asList(
                    "test@bgu.ac.il",
                    "supervisor@bgu.ac.il",
                    "prof@bgu.ac.il",
                    "professor@bgu.ac.il",
                    "advisor@bgu.ac.il",
                    "admin@bgu.ac.il",
                    "support@bgu.ac.il",
                    "info@bgu.ac.il",
                    "help@bgu.ac.il",
                    "user@bgu.ac.il",
                    "student@bgu.ac.il",
                    "phd@bgu.ac.il",
                    "msc@bgu.ac.il",
                    "new@bgu.ac.il",
                    "empty@bgu.ac.il",
                    "minimal@bgu.ac.il",
                    "sup1@bgu.ac.il",
                    "sup2@bgu.ac.il",
                    "sup3@bgu.ac.il",
                    "profa@bgu.ac.il",
                    "profb@bgu.ac.il"
            );

            for (String email : blockedEmails) {
                String result = emailQueueService.validateEmail(email);
                assertNotNull(result, "Email should be blocked: " + email);
                assertTrue(result.contains("generic address"),
                        "Email " + email + " should mention 'generic address' but got: " + result);
            }
        }
    }

    // ==================== Helper Methods ====================

    private EmailQueue createTestEmailQueue(Long id, String toEmail) {
        EmailQueue email = new EmailQueue();
        email.setId(id);
        email.setToEmail(toEmail);
        email.setSubject("Test Subject " + id);
        email.setHtmlContent("<html>Test " + id + "</html>");
        email.setEmailType(EmailQueue.EmailType.SUPERVISOR_APPROVAL);
        email.setStatus(EmailQueue.Status.PENDING);
        email.setRetryCount(0);
        email.setMaxRetries(3);
        email.setScheduledAt(LocalDateTime.now().minusMinutes(1));
        email.setUsername("testuser");
        return email;
    }
}
