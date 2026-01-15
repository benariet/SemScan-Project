package edu.bgu.semscanapi.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for MailService
 * Tests email sending functionality including HTML emails, attachments, and error handling
 */
@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @Mock
    private DatabaseLoggerService databaseLoggerService;

    @Mock
    private AppConfigService appConfigService;

    @InjectMocks
    private MailService mailService;

    @BeforeEach
    void setUp() {
        // Set default field values via reflection
        ReflectionTestUtils.setField(mailService, "fromEmail", "noreply@semscan.com");
        ReflectionTestUtils.setField(mailService, "monitoringEmail", "monitoring@bgu.ac.il");
    }

    // ==================== isEmailConfigured Tests ====================

    @Nested
    @DisplayName("isEmailConfigured Tests")
    class IsEmailConfiguredTests {

        @Test
        @DisplayName("Return true when mailSender is configured")
        void isEmailConfigured_MailSenderConfigured_ReturnsTrue() {
            assertTrue(mailService.isEmailConfigured());
        }

        @Test
        @DisplayName("Return false when mailSender is null")
        void isEmailConfigured_MailSenderNull_ReturnsFalse() {
            ReflectionTestUtils.setField(mailService, "mailSender", null);
            assertFalse(mailService.isEmailConfigured());
        }
    }

    // ==================== getFromEmail Tests ====================

    @Nested
    @DisplayName("getFromEmail Tests")
    class GetFromEmailTests {

        @Test
        @DisplayName("Return configured from email")
        void getFromEmail_ReturnsConfiguredEmail() {
            assertEquals("noreply@semscan.com", mailService.getFromEmail());
        }
    }

    // ==================== getMonitoringEmail Tests ====================

    @Nested
    @DisplayName("getMonitoringEmail Tests")
    class GetMonitoringEmailTests {

        @Test
        @DisplayName("Return configured monitoring email")
        void getMonitoringEmail_ReturnsConfiguredEmail() {
            assertEquals("monitoring@bgu.ac.il", mailService.getMonitoringEmail());
        }
    }

    // ==================== sendHtmlEmail (single recipient) Tests ====================

    @Nested
    @DisplayName("sendHtmlEmail (single recipient) Tests")
    class SendHtmlEmailSingleTests {

        @Test
        @DisplayName("Send email successfully to single recipient")
        void sendHtmlEmail_SingleRecipient_Success() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            boolean result = mailService.sendHtmlEmail("test@bgu.ac.il", "Test Subject", "<html>Test Content</html>");

            assertTrue(result);
            verify(mailSender).createMimeMessage();
            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("Return false when mailSender is null")
        void sendHtmlEmail_MailSenderNull_ReturnsFalse() {
            ReflectionTestUtils.setField(mailService, "mailSender", null);

            boolean result = mailService.sendHtmlEmail("test@bgu.ac.il", "Test Subject", "<html>Test</html>");

            assertFalse(result);
        }
    }

    // ==================== sendHtmlEmail (multiple recipients) Tests ====================

    @Nested
    @DisplayName("sendHtmlEmail (multiple recipients) Tests")
    class SendHtmlEmailMultipleTests {

        @Test
        @DisplayName("Send email successfully to multiple recipients")
        void sendHtmlEmail_MultipleRecipients_Success() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            List<String> recipients = Arrays.asList("user1@bgu.ac.il", "user2@bgu.ac.il");

            boolean result = mailService.sendHtmlEmail(recipients, "Test Subject", "<html>Test</html>", "Plain text");

            assertTrue(result);
            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("Return false when recipients list is null")
        void sendHtmlEmail_NullRecipients_ReturnsFalse() {
            // No stubbing needed - validation happens before createMimeMessage is called

            boolean result = mailService.sendHtmlEmail(null, "Test Subject", "<html>Test</html>", null);

            assertFalse(result);
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Return false when recipients list is empty")
        void sendHtmlEmail_EmptyRecipients_ReturnsFalse() {
            // No stubbing needed - validation happens before createMimeMessage is called

            boolean result = mailService.sendHtmlEmail(Collections.emptyList(), "Test Subject", "<html>Test</html>", null);

            assertFalse(result);
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Return false when mailSender is null")
        void sendHtmlEmail_MailSenderNull_ReturnsFalse() {
            ReflectionTestUtils.setField(mailService, "mailSender", null);
            List<String> recipients = Arrays.asList("user@bgu.ac.il");

            boolean result = mailService.sendHtmlEmail(recipients, "Test Subject", "<html>Test</html>", null);

            assertFalse(result);
        }

        @Test
        @DisplayName("Use from name from app config when configured")
        void sendHtmlEmail_UsesFromNameFromConfig() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(appConfigService.getStringConfig("email_from_name", null)).thenReturn("SemScan System");
            List<String> recipients = Arrays.asList("user@bgu.ac.il");

            boolean result = mailService.sendHtmlEmail(recipients, "Test Subject", "<html>Test</html>", null);

            assertTrue(result);
            verify(appConfigService).getStringConfig("email_from_name", null);
        }

        @Test
        @DisplayName("Use BCC list from app config when configured")
        void sendHtmlEmail_UsesBccListFromConfig() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            lenient().when(appConfigService.getStringConfig("email_from_name", null)).thenReturn(null);
            lenient().when(appConfigService.getStringConfig("email_reply_to", null)).thenReturn(null);
            when(appConfigService.getStringConfig("email_bcc_list", null)).thenReturn("bcc1@bgu.ac.il,bcc2@bgu.ac.il");
            List<String> recipients = Arrays.asList("user@bgu.ac.il");

            boolean result = mailService.sendHtmlEmail(recipients, "Test Subject", "<html>Test</html>", null);

            assertTrue(result);
            verify(appConfigService).getStringConfig("email_bcc_list", null);
        }

        @Test
        @DisplayName("Handle MessagingException")
        void sendHtmlEmail_MessagingException_ReturnsFalse() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));
            List<String> recipients = Arrays.asList("user@bgu.ac.il");

            boolean result = mailService.sendHtmlEmail(recipients, "Test Subject", "<html>Test</html>", null);

            assertFalse(result);
        }
    }

    // ==================== sendHtmlEmailWithAttachment (base64) Tests ====================

    @Nested
    @DisplayName("sendHtmlEmailWithAttachment (base64) Tests")
    class SendHtmlEmailWithBase64AttachmentTests {

        @Test
        @DisplayName("Send email with base64 attachment successfully")
        void sendHtmlEmailWithAttachment_Base64_Success() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            String base64Content = Base64.getEncoder().encodeToString("Test file content".getBytes());

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    "user@bgu.ac.il", "Test Subject", "<html>Test</html>",
                    "test.txt", base64Content, "text/plain");

            assertTrue(result);
            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("Return false when mailSender is null")
        void sendHtmlEmailWithAttachment_MailSenderNull_ReturnsFalse() {
            ReflectionTestUtils.setField(mailService, "mailSender", null);
            String base64Content = Base64.getEncoder().encodeToString("Test".getBytes());

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    "user@bgu.ac.il", "Test Subject", "<html>Test</html>",
                    "test.txt", base64Content, "text/plain");

            assertFalse(result);
        }

        @Test
        @DisplayName("Return false when recipient is null")
        void sendHtmlEmailWithAttachment_NullRecipient_ReturnsFalse() {
            String base64Content = Base64.getEncoder().encodeToString("Test".getBytes());

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    null, "Test Subject", "<html>Test</html>",
                    "test.txt", base64Content, "text/plain");

            assertFalse(result);
        }

        @Test
        @DisplayName("Return false when recipient is empty")
        void sendHtmlEmailWithAttachment_EmptyRecipient_ReturnsFalse() {
            String base64Content = Base64.getEncoder().encodeToString("Test".getBytes());

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    "  ", "Test Subject", "<html>Test</html>",
                    "test.txt", base64Content, "text/plain");

            assertFalse(result);
        }

        @Test
        @DisplayName("Return false for invalid base64 content")
        void sendHtmlEmailWithAttachment_InvalidBase64_ReturnsFalse() {
            // No stubbing needed - base64 decoding fails before createMimeMessage is called

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    "user@bgu.ac.il", "Test Subject", "<html>Test</html>",
                    "test.txt", "not-valid-base64!!!", "text/plain");

            assertFalse(result);
        }

        @Test
        @DisplayName("Use default content type when null")
        void sendHtmlEmailWithAttachment_NullContentType_UsesDefault() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            String base64Content = Base64.getEncoder().encodeToString("Test".getBytes());

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    "user@bgu.ac.il", "Test Subject", "<html>Test</html>",
                    "test.bin", base64Content, null);

            assertTrue(result);
        }
    }

    // ==================== sendHtmlEmailWithAttachment (byte array) Tests ====================

    @Nested
    @DisplayName("sendHtmlEmailWithAttachment (byte array) Tests")
    class SendHtmlEmailWithByteArrayAttachmentTests {

        @Test
        @DisplayName("Send email with byte array attachment successfully")
        void sendHtmlEmailWithAttachment_ByteArray_Success() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            byte[] attachmentData = "Test file content".getBytes();

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    "user@bgu.ac.il", "Test Subject", "<html>Test</html>",
                    "test.txt", attachmentData, "text/plain");

            assertTrue(result);
            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("Return false when mailSender is null")
        void sendHtmlEmailWithAttachment_ByteArray_MailSenderNull_ReturnsFalse() {
            ReflectionTestUtils.setField(mailService, "mailSender", null);
            byte[] attachmentData = "Test".getBytes();

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    "user@bgu.ac.il", "Test Subject", "<html>Test</html>",
                    "test.txt", attachmentData, "text/plain");

            assertFalse(result);
        }

        @Test
        @DisplayName("Return false when recipient is null")
        void sendHtmlEmailWithAttachment_ByteArray_NullRecipient_ReturnsFalse() {
            byte[] attachmentData = "Test".getBytes();

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    null, "Test Subject", "<html>Test</html>",
                    "test.txt", attachmentData, "text/plain");

            assertFalse(result);
        }

        @Test
        @DisplayName("Return false when recipient is empty")
        void sendHtmlEmailWithAttachment_ByteArray_EmptyRecipient_ReturnsFalse() {
            byte[] attachmentData = "Test".getBytes();

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    "", "Test Subject", "<html>Test</html>",
                    "test.txt", attachmentData, "text/plain");

            assertFalse(result);
        }

        @Test
        @DisplayName("Handle null attachment data gracefully")
        void sendHtmlEmailWithAttachment_ByteArray_NullAttachment_Success() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    "user@bgu.ac.il", "Test Subject", "<html>Test</html>",
                    "test.txt", (byte[]) null, "text/plain");

            assertTrue(result);
            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("Handle MessagingException")
        void sendHtmlEmailWithAttachment_ByteArray_MessagingException_ReturnsFalse() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));
            byte[] attachmentData = "Test".getBytes();

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    "user@bgu.ac.il", "Test Subject", "<html>Test</html>",
                    "test.txt", attachmentData, "text/plain");

            assertFalse(result);
        }

        @Test
        @DisplayName("Add monitoring BCC when configured")
        void sendHtmlEmailWithAttachment_ByteArray_AddsBcc() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            ReflectionTestUtils.setField(mailService, "monitoringEmail", "bcc@bgu.ac.il");
            byte[] attachmentData = "Test".getBytes();

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    "user@bgu.ac.il", "Test Subject", "<html>Test</html>",
                    "test.txt", attachmentData, "text/plain");

            assertTrue(result);
            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("Skip BCC when monitoring email is empty")
        void sendHtmlEmailWithAttachment_ByteArray_NoBcc() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            ReflectionTestUtils.setField(mailService, "monitoringEmail", "");
            byte[] attachmentData = "Test".getBytes();

            boolean result = mailService.sendHtmlEmailWithAttachment(
                    "user@bgu.ac.il", "Test Subject", "<html>Test</html>",
                    "test.txt", attachmentData, "text/plain");

            assertTrue(result);
            verify(mailSender).send(mimeMessage);
        }
    }
}
