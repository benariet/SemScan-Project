-- ============================================================================
-- CAPACITY MIGRATION SCRIPT: 3 sessions → 2 sessions per slot
-- ============================================================================
-- Run this script BEFORE restarting the API service
-- After running, RESTART the API service to clear config cache
-- ============================================================================

-- ============================================================================
-- STEP 1: DIAGNOSTIC - View current state (DO NOT MODIFY)
-- ============================================================================

-- 1.1 Show all slots with their current capacity and registration counts
SELECT
    s.slot_id,
    s.slot_date,
    s.start_time,
    s.capacity AS current_capacity,
    COUNT(r.presenter_username) AS total_registrations,
    SUM(CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END) AS effective_usage,
    SUM(CASE WHEN r.approval_status = 'APPROVED' THEN (CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END) ELSE 0 END) AS approved_usage,
    SUM(CASE WHEN r.approval_status = 'PENDING' THEN (CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END) ELSE 0 END) AS pending_usage,
    GROUP_CONCAT(CONCAT(r.presenter_username, '(', r.degree, ',', r.approval_status, ')') SEPARATOR ', ') AS registrations
FROM slots s
LEFT JOIN slot_registration r ON s.slot_id = r.slot_id
WHERE s.slot_date >= CURDATE()
GROUP BY s.slot_id, s.slot_date, s.start_time, s.capacity
ORDER BY s.slot_date, s.start_time;

-- 1.2 Show slots that will EXCEED new capacity of 2
SELECT
    s.slot_id,
    s.slot_date,
    s.start_time,
    s.capacity AS current_capacity,
    2 AS new_capacity,
    SUM(CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END) AS effective_usage,
    SUM(CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END) - 2 AS overflow_amount,
    GROUP_CONCAT(
        CONCAT(r.presenter_username, ' (', r.degree, ', ', r.approval_status, ', registered: ', r.registered_at, ')')
        ORDER BY r.registered_at ASC
        SEPARATOR '\n'
    ) AS registrations_by_time
FROM slots s
JOIN slot_registration r ON s.slot_id = r.slot_id
WHERE s.slot_date >= CURDATE()
  AND r.approval_status IN ('PENDING', 'APPROVED')
GROUP BY s.slot_id, s.slot_date, s.start_time, s.capacity
HAVING effective_usage > 2;

-- 1.3 Show current config values
SELECT config_key, config_value, description
FROM app_config
WHERE config_key IN ('phd.capacity.weight', 'waiting.list.limit.per.slot');

-- 1.4 Show waiting list entries that may need attention
SELECT
    w.slot_id,
    s.slot_date,
    w.presenter_username,
    w.degree,
    w.position,
    w.added_at
FROM waiting_list w
JOIN slots s ON w.slot_id = s.slot_id
WHERE s.slot_date >= CURDATE()
ORDER BY w.slot_id, w.position;


-- ============================================================================
-- STEP 2: BACKUP TABLES (RUN THIS FIRST!)
-- ============================================================================

-- Create backup of affected tables
CREATE TABLE IF NOT EXISTS slot_registration_backup_capacity_migration AS
SELECT *, NOW() AS backup_timestamp FROM slot_registration;

CREATE TABLE IF NOT EXISTS waiting_list_backup_capacity_migration AS
SELECT *, NOW() AS backup_timestamp FROM waiting_list;

CREATE TABLE IF NOT EXISTS slots_backup_capacity_migration AS
SELECT *, NOW() AS backup_timestamp FROM slots;


-- ============================================================================
-- STEP 3: HANDLE OVER-CAPACITY PENDING REGISTRATIONS
-- ============================================================================
-- Strategy: Cancel PENDING registrations (oldest first) until effective_usage <= 2
-- APPROVED registrations are NOT touched (manual intervention required if over-capacity)

-- 3.1 Create temp table to identify PENDING registrations to cancel
DROP TEMPORARY TABLE IF EXISTS pending_to_cancel;
CREATE TEMPORARY TABLE pending_to_cancel AS
WITH slot_usage AS (
    SELECT
        s.slot_id,
        s.capacity,
        SUM(CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END) AS effective_usage
    FROM slots s
    JOIN slot_registration r ON s.slot_id = r.slot_id
    WHERE s.slot_date >= CURDATE()
      AND r.approval_status IN ('PENDING', 'APPROVED')
    GROUP BY s.slot_id, s.capacity
    HAVING effective_usage > 2
),
pending_ranked AS (
    SELECT
        r.slot_id,
        r.presenter_username,
        r.degree,
        r.approval_status,
        r.registered_at,
        r.supervisor_email,
        r.topic,
        CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END AS weight,
        ROW_NUMBER() OVER (PARTITION BY r.slot_id ORDER BY r.registered_at DESC) AS cancel_priority
    FROM slot_registration r
    JOIN slot_usage su ON r.slot_id = su.slot_id
    WHERE r.approval_status = 'PENDING'
)
SELECT * FROM pending_ranked
WHERE cancel_priority = 1;  -- Cancel most recent PENDING first

-- 3.2 View what will be cancelled (REVIEW THIS!)
SELECT
    p.slot_id,
    s.slot_date,
    p.presenter_username,
    p.degree,
    p.weight,
    p.registered_at,
    p.supervisor_email,
    p.topic,
    'WILL BE CANCELLED' AS action
FROM pending_to_cancel p
JOIN slots s ON p.slot_id = s.slot_id;

-- 3.3 Log cancellations to app_logs before deleting
INSERT INTO app_logs (log_timestamp, level, tag, message, source, bgu_username, payload)
SELECT
    NOW(),
    'INFO',
    'CAPACITY_MIGRATION_CANCELLATION',
    CONCAT('Registration cancelled due to capacity reduction (3->2) for slot ', p.slot_id, ' on ', s.slot_date),
    'API',
    p.presenter_username,
    CONCAT('slot_id=', p.slot_id, ',degree=', p.degree, ',weight=', p.weight,
           ',registered_at=', p.registered_at, ',supervisor_email=', IFNULL(p.supervisor_email, 'N/A'),
           ',topic=', IFNULL(p.topic, 'N/A'))
FROM pending_to_cancel p
JOIN slots s ON p.slot_id = s.slot_id
WHERE EXISTS (SELECT 1 FROM users u WHERE u.bgu_username = p.presenter_username);

-- 3.4 DELETE the over-capacity PENDING registrations
-- UNCOMMENT THE LINE BELOW TO EXECUTE (after reviewing step 3.2)
-- DELETE r FROM slot_registration r
-- JOIN pending_to_cancel p ON r.slot_id = p.slot_id AND r.presenter_username = p.presenter_username;


-- ============================================================================
-- STEP 4: UPDATE SLOT CAPACITY
-- ============================================================================

-- 4.1 View slots that will be updated
SELECT slot_id, slot_date, start_time, capacity AS old_capacity, 2 AS new_capacity
FROM slots
WHERE capacity = 3 OR capacity > 2;

-- 4.2 Update all slots with capacity > 2 to capacity = 2
-- UNCOMMENT THE LINE BELOW TO EXECUTE
-- UPDATE slots SET capacity = 2, updated_at = NOW() WHERE capacity > 2;


-- ============================================================================
-- STEP 5: UPDATE APP_CONFIG
-- ============================================================================

-- 5.1 Update waiting list limit (should be <= slot capacity)
-- UNCOMMENT THE LINE BELOW TO EXECUTE
-- UPDATE app_config
-- SET config_value = '2', updated_at = NOW()
-- WHERE config_key = 'waiting.list.limit.per.slot' AND CAST(config_value AS UNSIGNED) > 2;

-- 5.2 Verify config after update
SELECT config_key, config_value, description
FROM app_config
WHERE config_key IN ('phd.capacity.weight', 'waiting.list.limit.per.slot');


-- ============================================================================
-- STEP 6: RECALCULATE SLOT STATUS
-- ============================================================================

-- 6.1 Update slot status based on new capacity
-- UNCOMMENT THE LINES BELOW TO EXECUTE
/*
UPDATE slots s
SET status = CASE
    WHEN (SELECT COALESCE(SUM(CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END), 0)
          FROM slot_registration r
          WHERE r.slot_id = s.slot_id AND r.approval_status = 'APPROVED') >= s.capacity
    THEN 'FULL'
    WHEN (SELECT COUNT(*) FROM slot_registration r
          WHERE r.slot_id = s.slot_id AND r.approval_status = 'APPROVED') > 0
    THEN 'SEMI'
    ELSE 'FREE'
END,
updated_at = NOW()
WHERE s.slot_date >= CURDATE();
*/


-- ============================================================================
-- STEP 7: VERIFICATION QUERIES (RUN AFTER MIGRATION)
-- ============================================================================

-- 7.1 Verify no slot exceeds new capacity
SELECT
    s.slot_id,
    s.slot_date,
    s.capacity,
    SUM(CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END) AS effective_usage,
    CASE
        WHEN SUM(CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END) > s.capacity THEN 'OVER CAPACITY!'
        ELSE 'OK'
    END AS status
FROM slots s
LEFT JOIN slot_registration r ON s.slot_id = r.slot_id AND r.approval_status IN ('PENDING', 'APPROVED')
WHERE s.slot_date >= CURDATE()
GROUP BY s.slot_id, s.slot_date, s.capacity
ORDER BY s.slot_date;

-- 7.2 Verify waiting list limits
SELECT
    w.slot_id,
    s.slot_date,
    COUNT(*) AS waiting_count,
    (SELECT config_value FROM app_config WHERE config_key = 'waiting.list.limit.per.slot') AS max_allowed
FROM waiting_list w
JOIN slots s ON w.slot_id = s.slot_id
WHERE s.slot_date >= CURDATE()
GROUP BY w.slot_id, s.slot_date;

-- 7.3 Final state summary
SELECT
    s.slot_id,
    s.slot_date,
    s.start_time,
    s.capacity,
    s.status,
    COALESCE(SUM(CASE WHEN r.approval_status = 'APPROVED' THEN (CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END) ELSE 0 END), 0) AS approved_usage,
    COALESCE(SUM(CASE WHEN r.approval_status = 'PENDING' THEN (CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END) ELSE 0 END), 0) AS pending_usage,
    (SELECT COUNT(*) FROM waiting_list w WHERE w.slot_id = s.slot_id) AS waiting_list_count
FROM slots s
LEFT JOIN slot_registration r ON s.slot_id = r.slot_id
WHERE s.slot_date >= CURDATE()
GROUP BY s.slot_id, s.slot_date, s.start_time, s.capacity, s.status
ORDER BY s.slot_date, s.start_time;


-- ============================================================================
-- STEP 8: CLEANUP (OPTIONAL - RUN AFTER VERIFYING MIGRATION SUCCESS)
-- ============================================================================

-- Drop backup tables after confirming migration success
-- UNCOMMENT THE LINES BELOW TO EXECUTE (ONLY AFTER VERIFICATION!)
-- DROP TABLE IF EXISTS slot_registration_backup_capacity_migration;
-- DROP TABLE IF EXISTS waiting_list_backup_capacity_migration;
-- DROP TABLE IF EXISTS slots_backup_capacity_migration;


-- ============================================================================
-- NOTES:
-- ============================================================================
-- 1. Run STEP 1 first to understand current state
-- 2. Run STEP 2 to create backups
-- 3. Review STEP 3.2 output carefully before uncommenting 3.4
-- 4. Uncomment and run STEP 3.4, 4.2, 5.1, 6.1 in order
-- 5. Run STEP 7 to verify migration success
-- 6. RESTART API SERVICE after migration to clear config cache
-- 7. Test mobile app to verify UI shows correct capacity
-- 8. Run STEP 8 cleanup only after confirming everything works
-- ============================================================================
