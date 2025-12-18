-- =============================================
-- Diagnose Email Issue During Registration
-- =============================================
-- Run this after a registration attempt to see what happened

-- 1. Check if EMAIL_SENDING_FLOW_START was logged (this should ALWAYS appear)
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag = 'EMAIL_SENDING_FLOW_START'
ORDER BY log_timestamp DESC
LIMIT 10;

-- 2. Check if registration succeeded but email flow didn't start
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag = 'SLOT_REGISTRATION_SUCCESS'
ORDER BY log_timestamp DESC
LIMIT 10;

-- 3. Check for missing supervisor email (this would prevent email sending)
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag = 'EMAIL_REGISTRATION_NO_SUPERVISOR_EMAIL'
ORDER BY log_timestamp DESC
LIMIT 10;

-- 4. Check if supervisor email was found
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag IN ('EMAIL_SUPERVISOR_EMAIL_CHECK', 'EMAIL_SUPERVISOR_EMAIL_FROM_REGISTRATION', 'EMAIL_SUPERVISOR_EMAIL_VALIDATED')
ORDER BY log_timestamp DESC
LIMIT 20;

-- 5. Check if approval service was called
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE tag IN ('EMAIL_CALLING_APPROVAL_SERVICE', 'EMAIL_SEND_APPROVAL_EMAIL_CALLED')
ORDER BY log_timestamp DESC
LIMIT 10;

-- 6. Check for any email-related errors
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload,
    LEFT(stack_trace, 300) AS stack_preview
FROM app_logs
WHERE tag LIKE 'EMAIL_%'
AND level = 'ERROR'
ORDER BY log_timestamp DESC
LIMIT 20;

-- 7. Check recent registrations in database to see if supervisor email is stored
SELECT 
    slot_id,
    presenter_username,
    supervisor_email,
    approval_status,
    approval_token,
    registered_at
FROM slot_registration
ORDER BY registered_at DESC
LIMIT 10;

-- 8. Check if registration exists but email wasn't sent (has supervisor email but no token)
SELECT 
    slot_id,
    presenter_username,
    supervisor_email,
    approval_status,
    approval_token,
    registered_at,
    CASE 
        WHEN supervisor_email IS NOT NULL AND supervisor_email != '' AND approval_token IS NULL THEN 'MISSING_TOKEN'
        WHEN supervisor_email IS NULL OR supervisor_email = '' THEN 'MISSING_EMAIL'
        ELSE 'OK'
    END AS issue
FROM slot_registration
WHERE registered_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY registered_at DESC;
