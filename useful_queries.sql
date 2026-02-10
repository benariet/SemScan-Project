-- ==================== LOGS ====================

-- Delete old logs to free space
DELETE FROM semscan_db.app_logs ORDER BY log_timestamp ASC LIMIT 45000;

-- Count total logs
SELECT COUNT(*) FROM semscan_db.app_logs;

-- Show all tables in database
SHOW TABLES;

-- Get all distinct log tags
SELECT DISTINCT tag FROM semscan_db.app_logs;

-- All logs excluding scheduled job noise
SELECT *
FROM semscan_db.app_logs
WHERE tag NOT LIKE '%WAITING_LIST_PROMOTION_EXPIRY_CHECK%'
ORDER BY log_id DESC;

-- Clean logs excluding noise
SELECT *
FROM semscan_db.app_logs
WHERE message NOT LIKE '%/api/v1/logs%'
  AND tag NOT LIKE '%Log%'
  AND tag NOT LIKE '%WAITING_LIST_PROMOTION_EXPIRY_CHECK%'
ORDER BY log_id DESC;

-- Error logs only
SELECT log_timestamp, level, message, source, payload, stack_trace
FROM semscan_db.app_logs
WHERE message NOT LIKE '%/api/v1/logs%'
  AND tag NOT LIKE '%Log%'
  AND level = 'ERROR'
ORDER BY log_id DESC;

-- Logs excluding waiting list noise
SELECT log_timestamp, level, message, source, payload, device_info, stack_trace
FROM semscan_db.app_logs
WHERE message NOT LIKE '%/api/v1/logs%'
  AND tag NOT LIKE '%Log%'
  AND message NOT LIKE '%waiting list%'
  AND message NOT LIKE '%onWaiting%'
ORDER BY log_id DESC;

-- Search logs containing password (security check)
SELECT log_timestamp, message, source, payload, device_info, stack_trace
FROM semscan_db.app_logs
WHERE message LIKE '%password%'
ORDER BY log_id DESC;

-- APK download logs
SELECT log_id, log_timestamp, level, tag, message, device_info, stack_trace
FROM semscan_db.app_logs
WHERE message LIKE '%APK%'
ORDER BY log_id DESC
LIMIT 50;

-- Logs by time range (update dates as needed)
SELECT log_timestamp, level, tag, source, message, user_full_name, device_info, stack_trace
FROM semscan_db.app_logs
WHERE log_timestamp BETWEEN '2026-01-26 12:00:00' AND '2026-01-26 14:00:00'
ORDER BY log_id DESC;

-- Logs after specific time (update date as needed)
SELECT log_timestamp, level, tag, source, message, user_full_name, device_info, stack_trace
FROM semscan_db.app_logs
WHERE log_timestamp > CAST('2026-01-26 12:27:53' AS DATETIME)
ORDER BY log_id DESC;

-- All recent logs
SELECT log_timestamp, level, tag, source, message, user_full_name, device_info, stack_trace
FROM semscan_db.app_logs
ORDER BY log_id DESC;

-- Email sending flow logs
SELECT * FROM semscan_db.app_logs
WHERE tag = 'EMAIL_SENDING_FLOW_START'
ORDER BY log_timestamp DESC LIMIT 10;

-- APK download count
SELECT COUNT(*) FROM semscan_db.app_logs WHERE tag = 'APK_DOWNLOAD';

-- APK download details
SELECT log_timestamp, payload
FROM semscan_db.app_logs
WHERE tag = 'APK_DOWNLOAD'
ORDER BY log_id DESC;

-- ==================== CONFIG ====================

-- Config with expiry settings
SELECT * FROM semscan_db.app_config WHERE config_key LIKE '%exp%';

-- All config
SELECT * FROM semscan_db.app_config;

-- Config with timeout settings
SELECT * FROM semscan_db.app_config WHERE config_key LIKE '%timeout%';

-- Announcement config
SELECT * FROM semscan_db.announce_config;

-- Update announcement message
UPDATE semscan_db.announce_config
SET is_active = TRUE,
    version = version + 1,
    title = 'Welcome!',
    message = 'This is a test announcement. The system is working correctly.',
    is_blocking = FALSE
WHERE id = 1;

-- Insert or update app version config
INSERT INTO semscan_db.app_config (config_key, config_value, config_type, category, target_system, description)
VALUES ('APP_VERSION', '1.0.0', 'STRING', 'APP', 'MOBILE', 'Current mobile app version number')
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    updated_at = CURRENT_TIMESTAMP;

-- ==================== USERS ====================

-- All users
SELECT * FROM semscan_db.users;

-- Test users only
SELECT * FROM semscan_db.users WHERE email LIKE '%test%';

-- All supervisors
SELECT * FROM semscan_db.supervisors;

-- FCM tokens for push notifications
SELECT id, bgu_username, device_info, last_notification_title, last_notification_body, created_at
FROM semscan_db.fcm_tokens;

-- ==================== DELETE USER (update USERNAME) ====================
DELETE FROM semscan_db.waiting_list_promotions WHERE presenter_username = 'USERNAME';
DELETE FROM semscan_db.waiting_list WHERE presenter_username = 'USERNAME';
DELETE FROM semscan_db.slot_registration WHERE presenter_username = 'USERNAME';
DELETE FROM semscan_db.seminar_participants WHERE participant_username = 'USERNAME';
DELETE FROM semscan_db.attendance WHERE student_username = 'USERNAME';
DELETE FROM semscan_db.users WHERE bgu_username = 'USERNAME';

-- ==================== SLOTS ====================

-- Current server time
SELECT NOW();

-- All slots ordered by date
SELECT * FROM semscan_db.slots ORDER BY slot_date;

-- Available slots from today
SELECT slot_id, slot_date FROM semscan_db.slots
WHERE slot_date >= CURDATE() AND status = 'FREE' LIMIT 10;




-- Insert slot for TODAY (morning)
INSERT INTO semscan_db.slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', CURDATE(), '09:00:00', '12:00:00', '37', '201', 3, 'FREE');

-- Insert slot for TODAY (afternoon)
INSERT INTO semscan_db.slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', CURDATE(), '14:00:00', '17:00:00', '37', '201', 3, 'FREE');

-- Insert slot for TODAY (evening)
INSERT INTO semscan_db.slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', CURDATE(), '18:00:00', '21:00:00', '37', '201', 3, 'FREE');

-- Insert slot for TODAY with custom time (update times as needed)
INSERT INTO semscan_db.slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', CURDATE(), '15:00:00', '18:00:00', '37', '201', 3, 'FREE');

-- Insert slot for TOMORROW
INSERT INTO semscan_db.slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '09:00:00', '12:00:00', '37', '201', 3, 'FREE');

-- Insert multiple slots for next week
INSERT INTO semscan_db.slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES
    ('SEM A', DATE_ADD(CURDATE(), INTERVAL 7 DAY), '09:00:00', '12:00:00', '37', '201', 3, 'FREE'),
    ('SEM A', DATE_ADD(CURDATE(), INTERVAL 8 DAY), '09:00:00', '12:00:00', '37', '201', 3, 'FREE'),
    ('SEM A', DATE_ADD(CURDATE(), INTERVAL 9 DAY), '09:00:00', '12:00:00', '37', '201', 3, 'FREE');

-- ==================== SESSIONS & SEMINARS ====================

-- All seminars
SELECT * FROM semscan_db.seminars ORDER BY seminar_id DESC;

-- All sessions
SELECT * FROM semscan_db.sessions;

-- ==================== REGISTRATIONS ====================

-- All registrations
SELECT * FROM semscan_db.slot_registration;

-- Registrations with presenter names and readable status
SELECT
    sr.slot_id,
    s.slot_date,
    CONCAT(u.first_name, ' ', u.last_name) AS presenter_name,
    u.bgu_username,
    sr.degree,
    sr.supervisor_name,
    CASE
        WHEN sr.approval_status = 'PENDING' THEN 'Waiting for supervisor approval'
        WHEN sr.approval_status = 'APPROVED' THEN 'Approved'
        WHEN sr.approval_status = 'DECLINED' THEN 'Declined'
        WHEN sr.approval_status = 'EXPIRED' THEN 'Expired'
        ELSE 'Unknown'
    END AS status_description,
    sr.registered_at
FROM semscan_db.slot_registration sr
INNER JOIN semscan_db.users u ON sr.presenter_username = u.bgu_username
INNER JOIN semscan_db.slots s ON sr.slot_id = s.slot_id
ORDER BY s.slot_date DESC, s.start_time ASC, sr.approval_status;

-- Clear registration details for a user (update username)
UPDATE semscan_db.slot_registration
SET topic = NULL,
    seminar_abstract = NULL,
    supervisor_name = NULL,
    supervisor_email = NULL
WHERE presenter_username = 'USERNAME';

-- ==================== WAITING LIST ====================

-- All waiting list entries
SELECT * FROM semscan_db.waiting_list;

-- Waiting list with positions
SELECT slot_id, presenter_username, position, added_at
FROM semscan_db.waiting_list
ORDER BY slot_id, position;

-- All waiting list promotions
SELECT * FROM semscan_db.waiting_list_promotions;

-- ==================== ATTENDANCE ====================

-- All attendance records
SELECT * FROM semscan_db.attendance ORDER BY attendance_id DESC;

-- Attendance summary per student
SELECT
    CONCAT(u.first_name, ' ', u.last_name) AS student_name,
    u.bgu_username,
    COUNT(a.attendance_id) AS total_attendances,
    MIN(a.attendance_time) AS first_attendance,
    MAX(a.attendance_time) AS last_attendance
FROM semscan_db.attendance a
LEFT JOIN semscan_db.users u ON a.student_username = u.bgu_username
GROUP BY a.student_username, u.first_name, u.last_name
ORDER BY total_attendances DESC;

-- #################### RESET SESSIONS ####################

-- Close all open sessions
UPDATE semscan_db.sessions SET status = 'CLOSED', end_time = NOW() WHERE status = 'OPEN';

-- Clear attendance window from all slots
UPDATE semscan_db.slots SET attendance_opened_at = NULL, attendance_closes_at = NULL, attendance_opened_by = NULL WHERE attendance_opened_at IS NOT NULL;

-- #################### FULL DB RESET (DANGEROUS!) ####################

-- Delete all transactional data
DELETE FROM semscan_db.attendance;
DELETE FROM semscan_db.seminar_participants;
DELETE FROM semscan_db.waiting_list_promotions;
DELETE FROM semscan_db.waiting_list;
DELETE FROM semscan_db.slot_registration;

-- Reset all slots to FREE status
UPDATE semscan_db.slots SET status = 'FREE', attendance_opened_at = NULL, attendance_closes_at = NULL, attendance_opened_by = NULL, legacy_seminar_id = NULL, legacy_session_id = NULL;

COMMIT;

-- ==================== TROUBLESHOOTING ====================

-- Check InnoDB lock timeout settings
SELECT @@innodb_lock_wait_timeout, @@wait_timeout;

-- Increase lock timeout temporarily
SET GLOBAL innodb_lock_wait_timeout = 120;

-- Check for blocking transactions
SELECT * FROM information_schema.innodb_lock_waits;

-- Show all running processes
SHOW PROCESSLIST;

-- Find which tables have a specific column
SELECT COLUMN_NAME, TABLE_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE COLUMN_NAME = 'user_full_name';

-- Test flows table
SELECT * FROM semscan_db.test_flows;
