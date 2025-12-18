-- Check recent login errors from app_logs table
-- This will show both API-side errors and mobile app errors

-- 1. Recent login errors (last 24 hours)
SELECT 
    log_id,
    log_timestamp,
    level,
    tag,
    message,
    source,
    bgu_username,
    exception_type,
    LEFT(stack_trace, 200) as stack_trace_preview,
    payload,
    created_at
FROM app_logs
WHERE (
    tag LIKE '%LOGIN%' 
    OR tag LIKE '%AUTH%'
    OR message LIKE '%login%'
    OR message LIKE '%authentication%'
    OR message LIKE '%BGU%'
)
AND log_timestamp >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY log_timestamp DESC
LIMIT 50;

-- 2. All errors for specific user (benariet)
SELECT 
    log_id,
    log_timestamp,
    level,
    tag,
    message,
    source,
    bgu_username,
    exception_type,
    LEFT(stack_trace, 200) as stack_trace_preview,
    payload
FROM app_logs
WHERE bgu_username = 'benariet'
AND log_timestamp >= DATE_SUB(NOW(), INTERVAL 7 DAY)
ORDER BY log_timestamp DESC;

-- 3. Recent errors from mobile app (MOBILE source)
SELECT 
    log_id,
    log_timestamp,
    level,
    tag,
    message,
    source,
    bgu_username,
    device_info,
    app_version,
    exception_type,
    LEFT(stack_trace, 200) as stack_trace_preview
FROM app_logs
WHERE source = 'MOBILE'
AND level = 'ERROR'
AND log_timestamp >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY log_timestamp DESC
LIMIT 50;

-- 4. Count errors by tag (to see what types of errors are happening)
SELECT 
    tag,
    COUNT(*) as error_count,
    MAX(log_timestamp) as last_occurrence
FROM app_logs
WHERE level = 'ERROR'
AND log_timestamp >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY tag
ORDER BY error_count DESC;

