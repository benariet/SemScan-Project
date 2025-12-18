-- =============================================
-- Check MailService Logs
-- =============================================
-- This checks if MailService.sendHtmlEmail() is actually completing

-- Check for EMAIL_SENT (should appear when mailService.sendHtmlEmail succeeds)
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag = 'EMAIL_SENT'
ORDER BY log_timestamp DESC
LIMIT 10;

-- Check for EMAIL_SEND_FAILED (should appear when mailService.sendHtmlEmail fails)
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload,
    LEFT(stack_trace, 500) AS stack_preview
FROM app_logs
WHERE tag = 'EMAIL_SEND_FAILED'
ORDER BY log_timestamp DESC
LIMIT 10;

-- Check for EMAIL_AUTH_FAILED (SMTP authentication issues)
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload,
    LEFT(stack_trace, 500) AS stack_preview
FROM app_logs
WHERE tag IN ('EMAIL_AUTH_FAILED', 'EMAIL_AUTH_TIMEOUT')
ORDER BY log_timestamp DESC
LIMIT 10;

-- Check for EMAIL_MAILSENDER_NULL (JavaMailSender not configured)
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag = 'EMAIL_MAILSENDER_NULL'
ORDER BY log_timestamp DESC
LIMIT 5;

-- Check for EMAIL_NO_RECIPIENTS
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag = 'EMAIL_NO_RECIPIENTS'
ORDER BY log_timestamp DESC
LIMIT 5;

-- Full timeline for slot 278 (most recent registration)
SELECT 
    log_timestamp,
    level,
    tag,
    LEFT(message, 100) AS message_preview,
    LEFT(payload, 150) AS payload_preview
FROM app_logs
WHERE (
    payload LIKE '%slotId=278%' 
    OR payload LIKE '%slotId=276%'
    OR tag LIKE 'EMAIL_%'
)
AND log_timestamp >= '2025-12-17 11:50:00'
ORDER BY log_timestamp ASC
LIMIT 50;
