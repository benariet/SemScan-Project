-- SemScan Seminar Attendance System - Dummy Data
-- MySQL 8.4 Database Dummy Data
-- Sample data for testing and development
-- Run this AFTER running database-schema-ddl.sql

USE semscan_db;



-- =============================================
-- DUMMY DATA FOR ALL TABLES
-- =============================================

-- Insert sample presenters
INSERT INTO users (user_id, email, first_name, last_name, role) VALUES
('USR-1000-20241021', 'dr.john.smith@university.edu', 'Dr. John', 'Smith', 'PRESENTER'),
('USR-1001-20241021', 'prof.sarah.jones@university.edu', 'Prof. Sarah', 'Jones', 'PRESENTER'),
('USR-1002-20241021', 'dr.mike.wilson@university.edu', 'Dr. Mike', 'Wilson', 'PRESENTER'),
('USR-1003-20241021', 'dr.anna.brown@university.edu', 'Dr. Anna', 'Brown', 'PRESENTER'),
('USR-1004-20241021', 'prof.david.garcia@university.edu', 'Prof. David', 'Garcia', 'PRESENTER')
ON DUPLICATE KEY UPDATE email = email;

-- Insert sample students
INSERT INTO users (user_id, email, first_name, last_name, role, student_id) VALUES
('USR-1005-20241021', 'alice.johnson@student.edu', 'Alice', 'Johnson', 'STUDENT', 'STU001'),
('USR-1006-20241021', 'bob.brown@student.edu', 'Bob', 'Brown', 'STUDENT', 'STU002'),
('USR-1007-20241021', 'charlie.davis@student.edu', 'Charlie', 'Davis', 'STUDENT', 'STU003'),
('USR-1008-20241021', 'diana.wilson@student.edu', 'Diana', 'Wilson', 'STUDENT', 'STU004'),
('USR-1009-20241021', 'eve.garcia@student.edu', 'Eve', 'Garcia', 'STUDENT', 'STU005'),
('USR-1010-20241021', 'frank.miller@student.edu', 'Frank', 'Miller', 'STUDENT', 'STU006'),
('USR-1011-20241021', 'grace.lee@student.edu', 'Grace', 'Lee', 'STUDENT', 'STU007'),
('USR-1012-20241021', 'henry.taylor@student.edu', 'Henry', 'Taylor', 'STUDENT', 'STU008'),
('USR-1013-20241021', 'isabella.martinez@student.edu', 'Isabella', 'Martinez', 'STUDENT', 'STU009'),
('USR-1014-20241021', 'james.anderson@student.edu', 'James', 'Anderson', 'STUDENT', 'STU010')
ON DUPLICATE KEY UPDATE email = email;

-- Insert sample seminars
INSERT INTO seminars (seminar_id, seminar_name, seminar_code, description, presenter_id) VALUES
('SEM-1000-20241021', 'AI and Machine Learning in Healthcare', 'AI-HLTH-001', 'Exploring applications of AI in medical diagnosis and treatment', 'USR-1000-20241021'),
('SEM-1001-20241021', 'Blockchain Technology and Cryptocurrency', 'BLK-CRYPT-001', 'Understanding blockchain fundamentals and crypto markets', 'USR-1000-20241021'),
('SEM-1002-20241021', 'Cybersecurity Best Practices', 'CYBER-SEC-001', 'Modern cybersecurity threats and defense strategies', 'USR-1001-20241021'),
('SEM-1003-20241021', 'Cloud Computing Architecture', 'CLOUD-ARCH-001', 'Designing scalable cloud infrastructure', 'USR-1001-20241021'),
('SEM-1004-20241021', 'Data Science and Analytics', 'DATA-ANAL-001', 'Extracting insights from big data', 'USR-1002-20241021'),
('SEM-1005-20241021', 'IoT and Smart Cities', 'IOT-SMART-001', 'Internet of Things applications in urban development', 'USR-1002-20241021'),
('SEM-1006-20241021', 'Quantum Computing Fundamentals', 'QUANT-COMP-001', 'Introduction to quantum computing principles', 'USR-1003-20241021'),
('SEM-1007-20241021', 'Robotics and Automation', 'ROBOT-AUTO-001', 'Industrial robotics and automation systems', 'USR-1003-20241021'),
('SEM-1008-20241021', 'Digital Marketing Strategies', 'DIGI-MARK-001', 'Modern digital marketing techniques and tools', 'USR-1004-20241021'),
('SEM-1009-20241021', 'Sustainable Technology', 'SUST-TECH-001', 'Green technology and sustainable development', 'USR-1004-20241021'),
-- Add PSM seminars for mobile app compatibility
('PSM-1000-20241021', 'AI and Machine Learning in Healthcare', 'AI-HLTH-PSM-001', 'Exploring applications of AI in medical diagnosis and treatment', 'USR-1000-20241021'),
('PSM-1001-20241021', 'Blockchain Technology and Cryptocurrency', 'BLK-CRYPT-PSM-001', 'Understanding blockchain fundamentals and crypto markets', 'USR-1000-20241021')
ON DUPLICATE KEY UPDATE seminar_name = seminar_name;

-- Insert sample sessions
INSERT INTO sessions (session_id, seminar_id, start_time, status) VALUES
-- AI Healthcare sessions
('SES-1000-20241021', 'SEM-1000-20241021', '2024-09-18 10:00:00', 'CLOSED'),
('SES-1001-20241021', 'SEM-1000-20241021', '2024-09-20 10:00:00', 'OPEN'),
('SES-1002-20241021', 'SEM-1000-20241021', '2024-09-25 10:00:00', 'OPEN'),

-- Blockchain sessions
('SES-1003-20241021', 'SEM-1001-20241021', '2024-09-19 14:00:00', 'CLOSED'),
('SES-1004-20241021', 'SEM-1001-20241021', '2024-09-21 14:00:00', 'OPEN'),
('SES-1005-20241021', 'SEM-1001-20241021', '2024-09-26 14:00:00', 'OPEN'),

-- Cybersecurity sessions
('SES-1006-20241021', 'SEM-1002-20241021', '2024-09-18 16:00:00', 'CLOSED'),
('SES-1007-20241021', 'SEM-1002-20241021', '2024-09-22 16:00:00', 'OPEN'),
('SES-1008-20241021', 'SEM-1002-20241021', '2024-09-27 16:00:00', 'OPEN'),

-- Cloud Computing sessions
('SES-1009-20241021', 'SEM-1003-20241021', '2024-09-19 12:00:00', 'OPEN'),
('SES-1010-20241021', 'SEM-1003-20241021', '2024-09-23 12:00:00', 'OPEN'),

-- Data Science sessions
('SES-1011-20241021', 'SEM-1004-20241021', '2024-09-20 15:00:00', 'OPEN'),
('SES-1012-20241021', 'SEM-1004-20241021', '2024-09-24 15:00:00', 'OPEN'),

-- IoT sessions
('SES-1013-20241021', 'SEM-1005-20241021', '2024-09-21 11:00:00', 'OPEN'),
('SES-1014-20241021', 'SEM-1005-20241021', '2024-09-25 11:00:00', 'OPEN'),

-- Quantum Computing sessions
('SES-1015-20241021', 'SEM-1006-20241021', '2024-09-22 09:00:00', 'OPEN'),
('SES-1016-20241021', 'SEM-1006-20241021', '2024-09-26 09:00:00', 'OPEN'),

-- Robotics sessions
('SES-1017-20241021', 'SEM-1007-20241021', '2024-09-23 13:00:00', 'OPEN'),
('SES-1018-20241021', 'SEM-1007-20241021', '2024-09-27 13:00:00', 'OPEN'),

-- Digital Marketing sessions
('SES-1019-20241021', 'SEM-1008-20241021', '2024-09-24 10:00:00', 'OPEN'),
('SES-1020-20241021', 'SEM-1008-20241021', '2024-09-28 10:00:00', 'OPEN'),

-- Sustainable Technology sessions
('SES-1021-20241021', 'SEM-1009-20241021', '2024-09-25 14:00:00', 'OPEN'),
('SES-1022-20241021', 'SEM-1009-20241021', '2024-09-29 14:00:00', 'OPEN')
ON DUPLICATE KEY UPDATE start_time = start_time;

-- Insert sample attendance records
INSERT INTO attendance (attendance_id, session_id, student_id, attendance_time, method) VALUES
-- Session 1 (AI Healthcare - CLOSED) - Some students attended
('ATT-1000-20241021', 'SES-1000-20241021', 'USR-1005-20241021', '2024-09-18 10:05:00', 'QR_SCAN'),
('ATT-1001-20241021', 'SES-1000-20241021', 'USR-1006-20241021', '2024-09-18 10:15:00', 'QR_SCAN'),
('ATT-1002-20241021', 'SES-1000-20241021', 'USR-1007-20241021', '2024-09-18 10:02:00', 'QR_SCAN'),
('ATT-1003-20241021', 'SES-1000-20241021', 'USR-1008-20241021', '2024-09-18 10:08:00', 'QR_SCAN'),
('ATT-1004-20241021', 'SES-1000-20241021', 'USR-1009-20241021', '2024-09-18 10:12:00', 'QR_SCAN'),

-- Session 4 (Blockchain - CLOSED) - Some students attended
('ATT-1005-20241021', 'SES-1003-20241021', 'USR-1005-20241021', '2024-09-19 14:03:00', 'QR_SCAN'),
('ATT-1006-20241021', 'SES-1003-20241021', 'USR-1006-20241021', '2024-09-19 14:20:00', 'QR_SCAN'),
('ATT-1007-20241021', 'SES-1003-20241021', 'USR-1009-20241021', '2024-09-19 14:01:00', 'QR_SCAN'),
('ATT-1008-20241021', 'SES-1003-20241021', 'USR-1010-20241021', '2024-09-19 14:15:00', 'QR_SCAN'),

-- Session 7 (Cybersecurity - CLOSED) - Some students attended
('ATT-1009-20241021', 'SES-1006-20241021', 'USR-1007-20241021', '2024-09-18 16:05:00', 'QR_SCAN'),
('ATT-1010-20241021', 'SES-1006-20241021', 'USR-1010-20241021', '2024-09-18 16:02:00', 'QR_SCAN'),
('ATT-1011-20241021', 'SES-1006-20241021', 'USR-1011-20241021', '2024-09-18 16:10:00', 'QR_SCAN'),
('ATT-1012-20241021', 'SES-1006-20241021', 'USR-1012-20241021', '2024-09-18 16:07:00', 'QR_SCAN'),

-- Some manual attendance requests (PENDING_APPROVAL)
('ATT-1013-20241021', 'SES-1001-20241021', 'USR-1013-20241021', '2024-09-20 10:30:00', 'MANUAL_REQUEST'),
('ATT-1014-20241021', 'SES-1004-20241021', 'USR-1014-20241021', '2024-09-21 14:45:00', 'MANUAL_REQUEST')
ON DUPLICATE KEY UPDATE attendance_time = attendance_time;

-- Update some attendance records to show manual requests
UPDATE attendance SET 
    request_status = 'PENDING_APPROVAL',
    manual_reason = 'Technical issues with QR scanner',
    requested_at = '2024-09-20 10:30:00'
WHERE attendance_id = 'ATT-1013-20241021';

UPDATE attendance SET 
    request_status = 'PENDING_APPROVAL',
    manual_reason = 'Device battery died during scan',
    requested_at = '2024-09-21 14:45:00'
WHERE attendance_id = 'ATT-1014-20241021';

-- Insert sample presenter API keys
INSERT INTO presenter_api_keys (api_key_id, presenter_id, api_key, is_active) VALUES
('API-1000-20241021', 'USR-1000-20241021', 'presenter-001-api-key-12345', TRUE),
('API-1001-20241021', 'USR-1001-20241021', 'presenter-002-api-key-67890', TRUE),
('API-1002-20241021', 'USR-1002-20241021', 'presenter-003-api-key-abcde', TRUE),
('API-1003-20241021', 'USR-1003-20241021', 'presenter-004-api-key-fghij', TRUE),
('API-1004-20241021', 'USR-1004-20241021', 'presenter-005-api-key-klmno', TRUE)
ON DUPLICATE KEY UPDATE api_key = api_key;

-- Insert sample presenter seminars (subject-free)
INSERT INTO presenter_seminar (presenter_seminar_id, presenter_id, seminar_name) VALUES
('PSM-1000-20241021', 'USR-1000-20241021', 'AI and Machine Learning in Healthcare'),
('PSM-1001-20241021', 'USR-1000-20241021', 'Blockchain Technology and Cryptocurrency'),
('PSM-1002-20241021', 'USR-1001-20241021', 'Cybersecurity Best Practices'),
('PSM-1003-20241021', 'USR-1001-20241021', 'Cloud Computing Architecture'),
('PSM-1004-20241021', 'USR-1002-20241021', 'Data Science and Analytics'),
('PSM-1005-20241021', 'USR-1002-20241021', 'IoT and Smart Cities'),
('PSM-1006-20241021', 'USR-1003-20241021', 'Quantum Computing Fundamentals'),
('PSM-1007-20241021', 'USR-1003-20241021', 'Robotics and Automation'),
('PSM-1008-20241021', 'USR-1004-20241021', 'Digital Marketing Strategies'),
('PSM-1009-20241021', 'USR-1004-20241021', 'Sustainable Technology')
ON DUPLICATE KEY UPDATE seminar_name = seminar_name;

-- Insert sample presenter seminar slots
INSERT INTO presenter_seminar_slot (presenter_seminar_slot_id, presenter_seminar_id, weekday, start_hour, end_hour) VALUES
-- AI Healthcare seminar slots (Monday 10-11, Wednesday 14-15)
('PSS-1000-20241021', 'PSM-1000-20241021', 1, 10, 11),
('PSS-1001-20241021', 'PSM-1000-20241021', 3, 14, 15),

-- Blockchain seminar slots (Tuesday 9-10, Thursday 15-16)
('PSS-1002-20241021', 'PSM-1001-20241021', 2, 9, 10),
('PSS-1003-20241021', 'PSM-1001-20241021', 4, 15, 16),

-- Cybersecurity seminar slots (Monday 16-17, Friday 10-11)
('PSS-1004-20241021', 'PSM-1002-20241021', 1, 16, 17),
('PSS-1005-20241021', 'PSM-1002-20241021', 5, 10, 11),

-- Cloud Computing seminar slots (Tuesday 12-13, Thursday 11-12)
('PSS-1006-20241021', 'PSM-1003-20241021', 2, 12, 13),
('PSS-1007-20241021', 'PSM-1003-20241021', 4, 11, 12),

-- Data Science seminar slots (Wednesday 15-16, Friday 14-15)
('PSS-1008-20241021', 'PSM-1004-20241021', 3, 15, 16),
('PSS-1009-20241021', 'PSM-1004-20241021', 5, 14, 15),

-- IoT seminar slots (Tuesday 11-12, Thursday 13-14)
('PSS-1010-20241021', 'PSM-1005-20241021', 2, 11, 12),
('PSS-1011-20241021', 'PSM-1005-20241021', 4, 13, 14),

-- Quantum Computing seminar slots (Monday 9-10, Wednesday 16-17)
('PSS-1012-20241021', 'PSM-1006-20241021', 1, 9, 10),
('PSS-1013-20241021', 'PSM-1006-20241021', 3, 16, 17),

-- Robotics seminar slots (Tuesday 13-14, Thursday 9-10)
('PSS-1014-20241021', 'PSM-1007-20241021', 2, 13, 14),
('PSS-1015-20241021', 'PSM-1007-20241021', 4, 9, 10),

-- Digital Marketing seminar slots (Monday 10-11, Friday 15-16)
('PSS-1016-20241021', 'PSM-1008-20241021', 1, 10, 11),
('PSS-1017-20241021', 'PSM-1008-20241021', 5, 15, 16),

-- Sustainable Technology seminar slots (Wednesday 14-15, Friday 9-10)
('PSS-1018-20241021', 'PSM-1009-20241021', 3, 14, 15),
('PSS-1019-20241021', 'PSM-1009-20241021', 5, 9, 10)
ON DUPLICATE KEY UPDATE start_hour = start_hour;

-- Insert sample app logs
INSERT INTO app_logs (timestamp, level, tag, message, user_id, user_role, device_info, app_version, exception_type) VALUES
(1695123456789, 'INFO', 'LOGIN', 'User logged in successfully', 'USR-1005-20241021', 'STUDENT', 'Android 13, Samsung Galaxy S21', '1.2.3', NULL),
(1695123460000, 'INFO', 'QR_SCAN', 'QR code scanned successfully', 'USR-1005-20241021', 'STUDENT', 'Android 13, Samsung Galaxy S21', '1.2.3', NULL),
(1695123465000, 'ERROR', 'NETWORK', 'Network connection failed', 'USR-1006-20241021', 'STUDENT', 'iOS 16, iPhone 14', '1.2.3', 'NetworkException'),
(1695123470000, 'WARN', 'ATTENDANCE', 'Duplicate attendance attempt', 'USR-1007-20241021', 'STUDENT', 'Android 12, Google Pixel 6', '1.2.3', NULL),
(1695123475000, 'INFO', 'SESSION', 'Session started', 'USR-1000-20241021', 'PRESENTER', 'Web Browser, Chrome 117', '1.2.3', NULL),
(1695123480000, 'ERROR', 'API', 'Invalid API key provided', NULL, NULL, 'Mobile App, Version 1.2.3', '1.2.3', 'AuthenticationException'),
(1695123485000, 'INFO', 'ATTENDANCE', 'Manual attendance request submitted', 'USR-1008-20241021', 'STUDENT', 'Android 13, OnePlus 9', '1.2.3', NULL),
(1695123490000, 'INFO', 'APPROVAL', 'Manual attendance approved', 'USR-1000-20241021', 'PRESENTER', 'Web Browser, Chrome 117', '1.2.3', NULL)
ON DUPLICATE KEY UPDATE timestamp = timestamp;

-- Insert sample log analytics
INSERT INTO log_analytics (date, level, tag, count, unique_users) VALUES
('2024-09-18', 'INFO', 'LOGIN', 25, 15),
('2024-09-18', 'INFO', 'QR_SCAN', 45, 20),
('2024-09-18', 'ERROR', 'NETWORK', 3, 2),
('2024-09-18', 'WARN', 'ATTENDANCE', 2, 2),
('2024-09-19', 'INFO', 'LOGIN', 30, 18),
('2024-09-19', 'INFO', 'QR_SCAN', 52, 22),
('2024-09-19', 'ERROR', 'API', 1, 1),
('2024-09-19', 'INFO', 'ATTENDANCE', 38, 19),
('2024-09-20', 'INFO', 'LOGIN', 28, 16),
('2024-09-20', 'INFO', 'QR_SCAN', 48, 21),
('2024-09-20', 'INFO', 'SESSION', 5, 3),
('2024-09-20', 'INFO', 'APPROVAL', 2, 1)
ON DUPLICATE KEY UPDATE count = count + VALUES(count), unique_users = unique_users + VALUES(unique_users);

SELECT 'SemScan Dummy Data Inserted Successfully!' as Status;

-- Note: This dummy data includes:
-- - 5 presenters and 10 students
-- - 10 seminars with 23 sessions
-- - 15 attendance records (some with manual requests)
-- - 5 API keys for presenters
-- - 10 presenter seminars with 20 time slots
-- - Sample app logs and analytics data
-- - Realistic data relationships and timestamps
