-- =====================================================================
-- Check MySQL Timeout Settings
-- =====================================================================
-- Run these queries to check if MySQL server timeouts are too low
-- Low timeouts can cause transaction timeouts even with minimal load
-- =====================================================================

-- Check current timeout settings
SELECT 
    @@wait_timeout AS wait_timeout_seconds,
    @@interactive_timeout AS interactive_timeout_seconds,
    @@innodb_lock_wait_timeout AS innodb_lock_wait_timeout_seconds,
    @@net_read_timeout AS net_read_timeout_seconds,
    @@net_write_timeout AS net_write_timeout_seconds;

-- Check current connection settings
SHOW VARIABLES LIKE '%timeout%';

-- Check for long-running queries that might be blocking
SELECT 
    id,
    user,
    host,
    db,
    command,
    time AS seconds_running,
    state,
    LEFT(info, 100) AS query_preview
FROM information_schema.processlist
WHERE command != 'Sleep'
AND time > 5
ORDER BY time DESC;

-- Check for locked tables
SHOW OPEN TABLES WHERE In_use > 0;

-- Check for InnoDB lock waits (MySQL 8.0+ compatible)
-- Method 1: Check transactions waiting for locks
SELECT 
    trx_id AS waiting_trx_id,
    trx_mysql_thread_id AS waiting_thread,
    trx_query AS waiting_query,
    trx_state,
    trx_requested_lock_id,
    trx_wait_started,
    TIMESTAMPDIFF(SECOND, trx_wait_started, NOW()) AS wait_duration_seconds
FROM information_schema.innodb_trx
WHERE trx_state = 'LOCK WAIT'
ORDER BY trx_wait_started;

-- Method 2: Check all active transactions (to identify potential blockers)
SELECT 
    trx_id,
    trx_mysql_thread_id AS thread_id,
    trx_state,
    trx_started,
    trx_query,
    trx_tables_locked,
    trx_rows_locked
FROM information_schema.innodb_trx
ORDER BY trx_started;

-- Method 3: Check for lock waits using performance_schema (MySQL 8.0+)
-- Only works if performance_schema is enabled (may return empty if not enabled)
SELECT 
    rw.requesting_thread_id,
    rw.requesting_event_id,
    rw.blocking_thread_id,
    rw.blocking_event_id
FROM performance_schema.data_lock_waits rw
LIMIT 10;

-- =====================================================================
-- Recommended MySQL Timeout Settings
-- =====================================================================
-- If timeouts are too low, increase them:
-- 
-- SET GLOBAL wait_timeout = 28800;          -- 8 hours (default is usually 28800)
-- SET GLOBAL interactive_timeout = 28800;   -- 8 hours (default is usually 28800)
-- SET GLOBAL innodb_lock_wait_timeout = 120; -- 120 seconds (default is 50)
-- SET GLOBAL net_read_timeout = 60;         -- 60 seconds (default is usually 30)
-- SET GLOBAL net_write_timeout = 60;       -- 60 seconds (default is usually 60)
--
-- Note: These changes are session-level. To make permanent, add to my.cnf:
-- [mysqld]
-- wait_timeout = 28800
-- interactive_timeout = 28800
-- innodb_lock_wait_timeout = 120
-- net_read_timeout = 60
-- net_write_timeout = 60
-- =====================================================================
