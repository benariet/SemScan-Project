-- =============================================
-- Check what happens AFTER EMAIL_SEND_APPROVAL_EMAIL_CALLED
-- =============================================
-- This checks if mailService.sendHtmlEmail() is actually being called and what it returns

-- Check for EMAIL_MAILSERVICE_RETURNED (should appear after mailService.sendHtmlEmail returns)
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag = 'EMAIL_MAILSERVICE_RETURNED'
ORDER BY log_timestamp DESC
LIMIT 10;

-- Check for EMAIL_APPROVAL_SERVICE_RETURNED (should appear after sendApprovalEmail returns)
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag = 'EMAIL_APPROVAL_SERVICE_RETURNED'
ORDER BY log_timestamp DESC
LIMIT 10;

-- Check for EMAIL_CALLING_MAILSERVICE (should appear right before mailService.sendHtmlEmail is called)
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag = 'EMAIL_CALLING_MAILSERVICE'
ORDER BY log_timestamp DESC
LIMIT 10;

-- Check for EMAIL_CONTENT_GENERATED (should appear before calling mailService)
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag = 'EMAIL_CONTENT_GENERATED'
ORDER BY log_timestamp DESC
LIMIT 10;

-- Check for EMAIL_APPROVAL_TOKEN_GENERATED (should appear early in sendApprovalEmail)
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag = 'EMAIL_APPROVAL_TOKEN_GENERATED'
ORDER BY log_timestamp DESC
LIMIT 10;

-- Check for any exceptions during email sending
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload,
    LEFT(stack_trace, 500) AS stack_preview
FROM app_logs
WHERE tag IN ('EMAIL_REGISTRATION_APPROVAL_EMAIL_EXCEPTION', 'EMAIL_MAILSENDER_NULL', 'EMAIL_SEND_FAILED')
ORDER BY log_timestamp DESC
LIMIT 10;

-- Timeline: Check the sequence of events for the most recent registration
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    LEFT(payload, 100) AS payload_preview
FROM app_logs
WHERE payload LIKE '%slotId=278%'
   OR payload LIKE '%slotId=276%'
ORDER BY log_timestamp DESC
LIMIT 30;
