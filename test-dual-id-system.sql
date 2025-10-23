-- Test Dual ID System
-- This script tests that both numeric and string IDs are created correctly

USE semscan_db;

-- First, let's see the current state
SELECT 'Current seminars in database:' as Status;
SELECT id, seminar_id, seminar_name FROM seminars ORDER BY id;

-- Insert a new seminar to test the dual ID system
INSERT INTO seminars (seminar_name, seminar_code, description, presenter_id) 
VALUES ('Test Seminar for Dual ID', 'TEST-DUAL-001', 'Testing dual ID system', 1);

-- Check the result - both IDs should be created
SELECT 'After insertion - checking both IDs:' as Status;
SELECT 
    id as 'Numeric_ID',
    seminar_id as 'String_ID', 
    seminar_name,
    CASE 
        WHEN seminar_id = CONCAT('SEMINR-', id, '-', DATE_FORMAT(CURDATE(), '%Y%m%d')) 
        THEN 'CORRELATED ✅' 
        ELSE 'NOT CORRELATED ❌' 
    END as 'Correlation_Check'
FROM seminars 
WHERE seminar_name = 'Test Seminar for Dual ID';

-- Show all seminars to see the pattern
SELECT 'All seminars showing ID correlation:' as Status;
SELECT 
    id as 'Numeric_ID',
    seminar_id as 'String_ID',
    seminar_name,
    CASE 
        WHEN seminar_id = CONCAT('SEMINR-', id, '-', DATE_FORMAT(CURDATE(), '%Y%m%d')) 
        THEN 'CORRELATED ✅' 
        ELSE 'NOT CORRELATED ❌' 
    END as 'Correlation_Check'
FROM seminars 
ORDER BY id;
