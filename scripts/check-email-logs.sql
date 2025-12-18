-- =============================================
-- Check Email Logs in app_logs
-- =============================================
-- This script helps diagnose email sending issues
-- Run this to see if emails are being attempted and what's happening

-- Check for email-related logs in the last 24 hours
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload,
    stack_trace IS NOT NULL AS has_stack_trace
FROM app_logs
WHERE (
    tag LIKE 'EMAIL_%' 
    OR message LIKE '%email%' 
    OR message LIKE '%Email%'
    OR message LIKE '%EMAIL%'
    OR payload LIKE '%email%'
    OR payload LIKE '%Email%'
    OR payload LIKE '%EMAIL%'
)
AND log_timestamp >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY log_timestamp DESC
LIMIT 50;

-- Check for registration-related logs that should trigger emails
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload
FROM app_logs
WHERE (
    tag LIKE 'SLOT_REGISTRATION%'
    OR tag LIKE 'EMAIL_SENDING_FLOW%'
    OR tag LIKE 'EMAIL_CALLING_APPROVAL_SERVICE%'
    OR tag LIKE 'EMAIL_APPROVAL_SERVICE_RETURNED%'
)
AND log_timestamp >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY log_timestamp DESC
LIMIT 50;

-- Check for any errors related to email
SELECT 
    log_timestamp,
    level,
    tag,
    message,
    payload,
    LEFT(stack_trace, 500) AS stack_trace_preview
FROM app_logs
WHERE level = 'ERROR'
AND (
    tag LIKE 'EMAIL_%'
    OR message LIKE '%email%'
    OR message LIKE '%Email%'
    OR payload LIKE '%email%'
)
AND log_timestamp >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY log_timestamp DESC
LIMIT 20;

-- Summary: Count email-related logs by tag
SELECT 
    tag,
    level,
    COUNT(*) AS count,
    MAX(log_timestamp) AS last_occurrence
FROM app_logs
WHERE tag LIKE 'EMAIL_%'
AND log_timestamp >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY tag, level
ORDER BY last_occurrence DESC;
