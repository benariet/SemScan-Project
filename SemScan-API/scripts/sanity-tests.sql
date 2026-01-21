-- =====================================================================
-- Sanity Tests: Basic Verification Queries
-- =====================================================================
-- Run these queries immediately after running integration-app-config.sql
-- to verify the database setup is correct
-- =====================================================================

-- =====================================================================
-- 1. VERIFY TABLES EXIST
-- =====================================================================
SELECT '=== TABLE EXISTENCE CHECK ===' AS test_name;

SELECT 
    TABLE_NAME,
    CASE 
        WHEN TABLE_NAME IN ('waiting_list_promotions', 'app_config') THEN '✓ NEW TABLE'
        ELSE '✓ EXISTING TABLE'
    END AS status
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
AND TABLE_NAME IN ('waiting_list_promotions', 'app_config', 'slots', 'slot_registration', 'users', 'waiting_list')
ORDER BY TABLE_NAME;

-- =====================================================================
-- 2. VERIFY APP_CONFIG TABLE STRUCTURE
-- =====================================================================
SELECT '=== APP_CONFIG TABLE STRUCTURE ===' AS test_name;

SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
AND TABLE_NAME = 'app_config'
ORDER BY ORDINAL_POSITION;

-- =====================================================================
-- 3. VERIFY APP_CONFIG DEFAULT VALUES
-- =====================================================================
SELECT '=== APP_CONFIG DEFAULT VALUES ===' AS test_name;

SELECT 
    config_key,
    config_value,
    config_type,
    target_system,
    category
FROM app_config
ORDER BY target_system, category, config_key;

-- Count by target system
SELECT 
    target_system,
    COUNT(*) AS count
FROM app_config
GROUP BY target_system;

-- =====================================================================
-- 4. VERIFY WAITING_LIST_PROMOTIONS TABLE STRUCTURE
-- =====================================================================
SELECT '=== WAITING_LIST_PROMOTIONS TABLE STRUCTURE ===' AS test_name;

SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
AND TABLE_NAME = 'waiting_list_promotions'
ORDER BY ORDINAL_POSITION;

-- =====================================================================
-- 5. VERIFY INDEXES EXIST
-- =====================================================================
SELECT '=== INDEX EXISTENCE CHECK ===' AS test_name;

SELECT 
    TABLE_NAME,
    INDEX_NAME,
    CASE 
        WHEN INDEX_NAME LIKE 'idx_%' THEN '✓ CUSTOM INDEX'
        WHEN INDEX_NAME = 'PRIMARY' THEN '✓ PRIMARY KEY'
        ELSE '✓ INDEX'
    END AS status
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
AND TABLE_NAME IN ('waiting_list_promotions', 'app_config')
AND INDEX_NAME IN (
    'idx_promotions_expires_at',
    'idx_promotions_status',
    'idx_app_config_target_system',
    'idx_app_config_category',
    'idx_app_config_key',
    'PRIMARY'
)
ORDER BY TABLE_NAME, INDEX_NAME;

-- =====================================================================
-- 6. VERIFY CRITICAL CONFIG VALUES (BOTH target_system)
-- =====================================================================
SELECT '=== CRITICAL CONFIG VALUES (BOTH) ===' AS test_name;

SELECT 
    config_key,
    config_value,
    CASE 
        WHEN config_value IS NULL OR config_value = '' THEN '✗ EMPTY'
        ELSE '✓ OK'
    END AS status
FROM app_config
WHERE target_system = 'BOTH'
AND config_key IN (
    'waiting_list_approval_window_hours',
    'email_from_name',
    'email_reply_to',
    'email_bcc_list',
    'server_url'
)
ORDER BY config_key;

-- =====================================================================
-- 7. VERIFY FOREIGN KEY CONSTRAINTS
-- =====================================================================
SELECT '=== FOREIGN KEY CONSTRAINTS ===' AS test_name;

SELECT 
    CONSTRAINT_NAME,
    TABLE_NAME,
    REFERENCED_TABLE_NAME,
    CASE 
        WHEN CONSTRAINT_NAME LIKE 'fk_%' THEN '✓ FOREIGN KEY'
        ELSE '✓ CONSTRAINT'
    END AS status
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
AND TABLE_NAME = 'waiting_list_promotions'
AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY CONSTRAINT_NAME;

-- =====================================================================
-- 8. QUICK DATA INTEGRITY CHECK
-- =====================================================================
SELECT '=== DATA INTEGRITY CHECK ===' AS test_name;

-- Check for any NULL values in required fields
SELECT 
    'app_config' AS table_name,
    COUNT(*) AS total_rows,
    SUM(CASE WHEN config_key IS NULL THEN 1 ELSE 0 END) AS null_keys,
    SUM(CASE WHEN config_value IS NULL THEN 1 ELSE 0 END) AS null_values,
    SUM(CASE WHEN target_system IS NULL THEN 1 ELSE 0 END) AS null_target_system
FROM app_config

UNION ALL

SELECT 
    'waiting_list_promotions' AS table_name,
    COUNT(*) AS total_rows,
    0 AS null_keys,
    0 AS null_values,
    0 AS null_target_system
FROM waiting_list_promotions;

-- =====================================================================
-- 9. SUMMARY
-- =====================================================================
SELECT '=== SUMMARY ===' AS test_name;

SELECT 
    'Tables' AS category,
    COUNT(*) AS count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
AND TABLE_NAME IN ('waiting_list_promotions', 'app_config')

UNION ALL

SELECT 
    'App Config Entries' AS category,
    COUNT(*) AS count
FROM app_config

UNION ALL

SELECT 
    'Waiting List Promotions' AS category,
    COUNT(*) AS count
FROM waiting_list_promotions;
