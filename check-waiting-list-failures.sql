-- Query to check if "failed to add to waiting list" errors are documented in app_logs table
-- This query searches for WAITING_LIST_ADD_FAILED tag entries

-- 1. Count all WAITING_LIST_ADD_FAILED entries
SELECT 
    COUNT(*) as total_failures,
    MIN(log_timestamp) as first_failure,
    MAX(log_timestamp) as last_failure
FROM app_logs
WHERE tag = 'WAITING_LIST_ADD_FAILED';

-- 2. Show recent WAITING_LIST_ADD_FAILED entries with details
SELECT 
    log_id,
    log_timestamp,
    level,
    tag,
    message,
    bgu_username,
    user_role,
    correlation_id,
    exception_type,
    payload,
    source,
    created_at
FROM app_logs
WHERE tag = 'WAITING_LIST_ADD_FAILED'
ORDER BY log_timestamp DESC
LIMIT 50;

-- 3. Group by error message to see different failure reasons
SELECT 
    message,
    COUNT(*) as occurrence_count,
    MIN(log_timestamp) as first_occurrence,
    MAX(log_timestamp) as last_occurrence
FROM app_logs
WHERE tag = 'WAITING_LIST_ADD_FAILED'
GROUP BY message
ORDER BY occurrence_count DESC;

-- 4. Check if there are any entries with "Failed to add to waiting list" in the message
SELECT 
    log_id,
    log_timestamp,
    level,
    tag,
    message,
    bgu_username,
    payload,
    exception_type
FROM app_logs
WHERE tag = 'WAITING_LIST_ADD_FAILED'
   OR message LIKE '%Failed to add to waiting list%'
   OR message LIKE '%failed to add to waiting list%'
ORDER BY log_timestamp DESC
LIMIT 50;

-- 5. Summary by date (last 30 days)
SELECT 
    DATE(log_timestamp) as failure_date,
    COUNT(*) as failures_count,
    COUNT(DISTINCT bgu_username) as unique_users_affected
FROM app_logs
WHERE tag = 'WAITING_LIST_ADD_FAILED'
  AND log_timestamp >= DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY DATE(log_timestamp)
ORDER BY failure_date DESC;

-- 6. Check for any async execution issues (entries that might have failed to save)
-- This checks if there are any recent waiting list operations without corresponding log entries
-- Note: This is a diagnostic query - you may need to adjust based on your actual data

-- 7. Verify the logging is working by checking the most recent entries
SELECT 
    log_timestamp,
    tag,
    level,
    message,
    bgu_username,
    source
FROM app_logs
WHERE tag IN ('WAITING_LIST_ADD_FAILED', 'WAITING_LIST_ADD_ATTEMPT', 'WAITING_LIST_ADDED')
ORDER BY log_timestamp DESC
LIMIT 100;

