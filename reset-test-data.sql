-- =====================================================================
-- RESET TEST DATA - Clean Start for Today
-- =====================================================================
-- This script will:
-- 1. Delete all attendance records for today's sessions
-- 2. Delete all slot registrations for today's slots
-- 3. Delete all sessions created today
-- 4. Delete all slots for today (or created today)
-- 5. Optionally delete seminars created today (commented out by default)
-- 6. Insert a fresh slot for today
-- =====================================================================

START TRANSACTION;

-- Set the date threshold (today)
SET @TODAY = CURDATE();
SET @TODAY_START = CONCAT(@TODAY, ' 00:00:00');

-- =====================================================================
-- STEP 1: Delete attendance records for today's sessions
-- =====================================================================
DELETE FROM attendance
WHERE session_id IN (
    SELECT session_id 
    FROM sessions 
    WHERE DATE(created_at) = @TODAY
       OR DATE(start_time) = @TODAY
);

-- =====================================================================
-- STEP 2: Delete slot registrations for today's slots
-- =====================================================================
DELETE FROM slot_registration
WHERE slot_id IN (
    SELECT slot_id 
    FROM slots 
    WHERE slot_date = @TODAY
       OR DATE(created_at) = @TODAY
);

-- =====================================================================
-- STEP 3: Delete sessions created today
-- =====================================================================
DELETE FROM sessions
WHERE DATE(created_at) = @TODAY
   OR DATE(start_time) = @TODAY;

-- =====================================================================
-- STEP 4: Delete slots for today (or created today)
-- =====================================================================
DELETE FROM slots
WHERE slot_date = @TODAY
   OR DATE(created_at) = @TODAY;

-- =====================================================================
-- STEP 5: (OPTIONAL) Delete seminars created today
-- Uncomment the following if you also want to clean up test seminars
-- =====================================================================
-- DELETE FROM seminars
-- WHERE DATE(created_at) = @TODAY;

-- =====================================================================
-- STEP 6: Insert a fresh slot for today
-- =====================================================================
INSERT INTO slots (
    semester_label,
    slot_date,
    start_time,
    end_time,
    building,
    room,
    capacity,
    status,
    created_at,
    updated_at
) VALUES (
    'SEM A',
    @TODAY,              -- Today's date
    '00:01:00',
    '23:59:00',
    '37',
    '201',
    2,
    'FREE',
    NOW(),
    NOW()
);

-- Show the new slot ID
SET @NEW_SLOT_ID = LAST_INSERT_ID();
SELECT CONCAT('Fresh slot created with ID: ', @NEW_SLOT_ID) AS result;

COMMIT;

-- =====================================================================
-- VERIFICATION: Show what's left for today
-- =====================================================================
SELECT '=== SLOTS FOR TODAY ===' AS info;
SELECT slot_id, slot_date, start_time, end_time, building, room, capacity, status, created_at
FROM slots
WHERE slot_date = @TODAY;

SELECT '=== SESSIONS FOR TODAY ===' AS info;
SELECT session_id, seminar_id, start_time, end_time, status, created_at
FROM sessions
WHERE DATE(created_at) = @TODAY OR DATE(start_time) = @TODAY;

SELECT '=== SLOT REGISTRATIONS FOR TODAY ===' AS info;
SELECT sr.slot_id, sr.presenter_username, sr.topic, sr.registered_at
FROM slot_registration sr
INNER JOIN slots s ON sr.slot_id = s.slot_id
WHERE s.slot_date = @TODAY;

SELECT '=== ATTENDANCE FOR TODAY ===' AS info;
SELECT a.attendance_id, a.session_id, a.student_username, a.attendance_time, a.method
FROM attendance a
INNER JOIN sessions s ON a.session_id = s.session_id
WHERE DATE(s.created_at) = @TODAY OR DATE(s.start_time) = @TODAY;

SELECT 'Reset complete! Ready for fresh enrollment.' AS status;

