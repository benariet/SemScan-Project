-- Test Trigger Creation and Functionality
-- This script verifies that the triggers are created and working

USE semscan_db;

-- Check if triggers exist
SELECT 'Checking if triggers exist:' as Status;
SELECT 
    TRIGGER_NAME,
    EVENT_MANIPULATION,
    EVENT_OBJECT_TABLE,
    ACTION_TIMING
FROM INFORMATION_SCHEMA.TRIGGERS 
WHERE TRIGGER_SCHEMA = 'semscan_db' 
AND EVENT_OBJECT_TABLE IN ('users', 'seminars', 'sessions', 'attendance', 'presenter_seminar', 'presenter_seminar_slot')
ORDER BY EVENT_OBJECT_TABLE, TRIGGER_NAME;

-- Test the trigger by inserting a user
SELECT 'Testing user trigger:' as Status;
INSERT INTO users (email, first_name, last_name, role) 
VALUES ('test.user@example.com', 'Test', 'User', 'STUDENT');

-- Check the result
SELECT 'User insertion result:' as Status;
SELECT 
    id as 'Numeric_ID',
    user_id as 'String_ID',
    first_name,
    CASE 
        WHEN user_id = CONCAT('USERID-', id, '-', DATE_FORMAT(CURDATE(), '%Y%m%d')) 
        THEN 'CORRELATED ✅' 
        ELSE 'NOT CORRELATED ❌' 
    END as 'Correlation_Check'
FROM users 
WHERE email = 'test.user@example.com';

-- Test seminar insertion
SELECT 'Testing seminar trigger:' as Status;
INSERT INTO seminars (seminar_name, seminar_code, description, presenter_id) 
VALUES ('Trigger Test Seminar', 'TRIGGER-001', 'Testing seminar trigger', 1);

-- Check the result
SELECT 'Seminar insertion result:' as Status;
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
WHERE seminar_name = 'Trigger Test Seminar';
