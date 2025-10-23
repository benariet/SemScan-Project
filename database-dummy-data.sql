-- SemScan Seminar Attendance System - Dummy Data
-- MySQL 8.4 Database Dummy Data
-- Sample data for testing and development
-- Run this AFTER running database-schema-ddl.sql

USE semscan_db;



-- =============================================
-- DUMMY DATA FOR ALL TABLES
-- =============================================

-- Insert sample presenters
INSERT INTO users (email, first_name, last_name, role) VALUES
('dr.john.smith@university.edu', 'Dr. John', 'Smith', 'PRESENTER'),
('prof.sarah.jones@university.edu', 'Prof. Sarah', 'Jones', 'PRESENTER'),
('dr.mike.wilson@university.edu', 'Dr. Mike', 'Wilson', 'PRESENTER'),
('dr.anna.brown@university.edu', 'Dr. Anna', 'Brown', 'PRESENTER'),
('prof.david.garcia@university.edu', 'Prof. David', 'Garcia', 'PRESENTER')
ON DUPLICATE KEY UPDATE email = email;

-- Insert sample students
INSERT INTO users (email, first_name, last_name, role, student_id) VALUES
('alice.johnson@student.edu', 'Alice', 'Johnson', 'STUDENT', 'STU001'),
('bob.brown@student.edu', 'Bob', 'Brown', 'STUDENT', 'STU002'),
('charlie.davis@student.edu', 'Charlie', 'Davis', 'STUDENT', 'STU003'),
('diana.wilson@student.edu', 'Diana', 'Wilson', 'STUDENT', 'STU004'),
('eve.garcia@student.edu', 'Eve', 'Garcia', 'STUDENT', 'STU005'),
('frank.miller@student.edu', 'Frank', 'Miller', 'STUDENT', 'STU006'),
('grace.lee@student.edu', 'Grace', 'Lee', 'STUDENT', 'STU007'),
('henry.taylor@student.edu', 'Henry', 'Taylor', 'STUDENT', 'STU008'),
('isabella.martinez@student.edu', 'Isabella', 'Martinez', 'STUDENT', 'STU009'),
('james.anderson@student.edu', 'James', 'Anderson', 'STUDENT', 'STU010')
ON DUPLICATE KEY UPDATE email = email;

-- Insert sample seminars (using numeric IDs for foreign keys)
INSERT INTO seminars (seminar_id, seminar_name, seminar_code, description, presenter_id) VALUES
('SEMINR-1-20250122', 'AI and Machine Learning in Healthcare', 'AI-HLTH-001', 'Exploring applications of AI in medical diagnosis and treatment', 1),
('SEMINR-2-20250122', 'Blockchain Technology and Cryptocurrency', 'BLK-CRYPT-001', 'Understanding blockchain fundamentals and crypto markets', 1),
('SEMINR-3-20250122', 'Cybersecurity Best Practices', 'CYBER-SEC-001', 'Modern cybersecurity threats and defense strategies', 2),
('SEMINR-4-20250122', 'Cloud Computing Architecture', 'CLOUD-ARCH-001', 'Designing scalable cloud infrastructure', 2),
('SEMINR-5-20250122', 'Data Science and Analytics', 'DATA-ANAL-001', 'Extracting insights from big data', 3),
('SEMINR-6-20250122', 'IoT and Smart Cities', 'IOT-SMART-001', 'Internet of Things applications in urban development', 3),
('SEMINR-7-20250122', 'Quantum Computing Fundamentals', 'QUANT-COMP-001', 'Introduction to quantum computing principles', 4),
('SEMINR-8-20250122', 'Robotics and Automation', 'ROBOT-AUTO-001', 'Industrial robotics and automation systems', 4),
('SEMINR-9-20250122', 'Digital Marketing Strategies', 'DIGI-MARK-001', 'Modern digital marketing techniques and tools', 5),
('SEMINR-10-20250122', 'Sustainable Technology', 'SUST-TECH-001', 'Green technology and sustainable development', 5)
ON DUPLICATE KEY UPDATE seminar_name = seminar_name;

-- Insert sample sessions
INSERT INTO sessions (session_id, seminar_id, start_time, status) VALUES
-- AI Healthcare sessions
('SESSIN-1-20250122', 1, '2025-01-22 10:00:00', 'CLOSED'),
('SESSIN-2-20250122', 1, '2025-01-24 10:00:00', 'CLOSED'),
('SESSIN-3-20250122', 1, '2025-01-29 10:00:00', 'CLOSED'),

-- Blockchain sessions
('SESSIN-4-20250122', 2, '2025-01-23 14:00:00', 'CLOSED'),
('SESSIN-5-20250122', 2, '2025-01-25 14:00:00', 'CLOSED'),
('SESSIN-6-20250122', 2, '2025-01-30 14:00:00', 'CLOSED'),

-- Cybersecurity sessions
('SESSIN-7-20250122', 3, '2025-01-22 16:00:00', 'CLOSED'),
('SESSIN-8-20250122', 3, '2025-01-26 16:00:00', 'CLOSED'),
('SESSIN-9-20250122', 3, '2025-01-31 16:00:00', 'CLOSED'),

-- Cloud Computing sessions
('SESSIN-10-20250122', 4, '2025-01-23 12:00:00', 'CLOSED'),
('SESSIN-11-20250122', 4, '2025-01-27 12:00:00', 'CLOSED'),

-- Data Science sessions
('SESSIN-12-20250122', 5, '2025-01-24 15:00:00', 'CLOSED'),
('SESSIN-13-20250122', 5, '2025-01-28 15:00:00', 'CLOSED'),

-- IoT sessions
('SESSIN-14-20250122', 6, '2025-01-25 11:00:00', 'CLOSED'),
('SESSIN-15-20250122', 6, '2025-01-29 11:00:00', 'CLOSED'),

-- Quantum Computing sessions
('SESSIN-16-20250122', 7, '2025-01-26 09:00:00', 'CLOSED'),
('SESSIN-17-20250122', 7, '2025-01-30 09:00:00', 'CLOSED'),

-- Robotics sessions
('SESSIN-18-20250122', 8, '2025-01-27 13:00:00', 'CLOSED'),
('SESSIN-19-20250122', 8, '2025-01-31 13:00:00', 'CLOSED'),

-- Digital Marketing sessions
('SESSIN-20-20250122', 9, '2025-01-28 10:00:00', 'CLOSED'),
('SESSIN-21-20250122', 9, '2025-02-01 10:00:00', 'CLOSED'),

-- Sustainable Technology sessions
('SESSIN-22-20250122', 10, '2025-01-29 14:00:00', 'CLOSED'),
('SESSIN-23-20250122', 10, '2025-02-02 14:00:00', 'CLOSED')
ON DUPLICATE KEY UPDATE start_time = start_time;

-- Insert sample attendance records
INSERT INTO attendance (attendance_id, session_id, student_id, attendance_time, method) VALUES
-- Session 1 (AI Healthcare - CLOSED) - Some students attended
('ATTEND-1-20250122', 1, 6, '2025-01-22 10:05:00', 'QR_SCAN'),
('ATTEND-2-20250122', 1, 7, '2025-01-22 10:15:00', 'QR_SCAN'),
('ATTEND-3-20250122', 1, 8, '2025-01-22 10:02:00', 'QR_SCAN'),
('ATTEND-4-20250122', 1, 9, '2025-01-22 10:08:00', 'QR_SCAN'),
('ATTEND-5-20250122', 1, 10, '2025-01-22 10:12:00', 'QR_SCAN'),

-- Session 4 (Blockchain - CLOSED) - Some students attended
('ATTEND-6-20250122', 4, 6, '2025-01-23 14:03:00', 'QR_SCAN'),
('ATTEND-7-20250122', 4, 7, '2025-01-23 14:20:00', 'QR_SCAN'),
('ATTEND-8-20250122', 4, 10, '2025-01-23 14:01:00', 'QR_SCAN'),
('ATTEND-9-20250122', 4, 11, '2025-01-23 14:15:00', 'QR_SCAN'),

-- Session 7 (Cybersecurity - CLOSED) - Some students attended
('ATTEND-10-20250122', 7, 8, '2025-01-22 16:05:00', 'QR_SCAN'),
('ATTEND-11-20250122', 7, 11, '2025-01-22 16:02:00', 'QR_SCAN'),
('ATTEND-12-20250122', 7, 12, '2025-01-22 16:10:00', 'QR_SCAN'),
('ATTEND-13-20250122', 7, 13, '2025-01-22 16:07:00', 'QR_SCAN'),

-- Some manual attendance requests (PENDING_APPROVAL)
('ATTEND-14-20250122', 2, 14, '2025-01-24 10:30:00', 'MANUAL_REQUEST'),
('ATTEND-15-20250122', 5, 15, '2025-01-25 14:45:00', 'MANUAL_REQUEST')
ON DUPLICATE KEY UPDATE attendance_time = attendance_time;

-- Update some attendance records to show manual requests
UPDATE attendance SET 
    request_status = 'PENDING_APPROVAL',
    manual_reason = 'Technical issues with QR scanner',
    requested_at = '2025-01-24 10:30:00'
WHERE session_id = 2 
AND student_id = 14;

UPDATE attendance SET 
    request_status = 'PENDING_APPROVAL',
    manual_reason = 'Device battery died during scan',
    requested_at = '2025-01-25 14:45:00'
WHERE session_id = 5 
AND student_id = 15;

-- API keys removed - no authentication required for POC

-- Insert sample presenter seminars (subject-free)
INSERT INTO presenter_seminar (presenter_seminar_id, presenter_id, seminar_name) VALUES
('PRESEM-1-20250122', 1, 'AI and Machine Learning in Healthcare'),
('PRESEM-2-20250122', 1, 'Blockchain Technology and Cryptocurrency'),
('PRESEM-3-20250122', 2, 'Cybersecurity Best Practices'),
('PRESEM-4-20250122', 2, 'Cloud Computing Architecture'),
('PRESEM-5-20250122', 3, 'Data Science and Analytics'),
('PRESEM-6-20250122', 3, 'IoT and Smart Cities'),
('PRESEM-7-20250122', 4, 'Quantum Computing Fundamentals'),
('PRESEM-8-20250122', 4, 'Robotics and Automation'),
('PRESEM-9-20250122', 5, 'Digital Marketing Strategies'),
('PRESEM-10-20250122', 5, 'Sustainable Technology')
ON DUPLICATE KEY UPDATE seminar_name = seminar_name;

-- Insert sample presenter seminar slots
INSERT INTO presenter_seminar_slot (presenter_seminar_slot_id, presenter_seminar_id, weekday, start_hour, end_hour) VALUES
-- AI Healthcare seminar slots (Monday 10-11, Wednesday 14-15)
('PRESLT-1-20250122', 1, 1, 10, 11),
('PRESLT-2-20250122', 1, 3, 14, 15),

-- Blockchain seminar slots (Tuesday 9-10, Thursday 15-16)
('PRESLT-3-20250122', 2, 2, 9, 10),
('PRESLT-4-20250122', 2, 4, 15, 16),

-- Cybersecurity seminar slots (Monday 16-17, Friday 10-11)
('PRESLT-5-20250122', 3, 1, 16, 17),
('PRESLT-6-20250122', 3, 5, 10, 11),

-- Cloud Computing seminar slots (Tuesday 12-13, Thursday 11-12)
('PRESLT-7-20250122', 4, 2, 12, 13),
('PRESLT-8-20250122', 4, 4, 11, 12),

-- Data Science seminar slots (Wednesday 15-16, Friday 14-15)
('PRESLT-9-20250122', 5, 3, 15, 16),
('PRESLT-10-20250122', 5, 5, 14, 15),

-- IoT seminar slots (Tuesday 11-12, Thursday 13-14)
('PRESLT-11-20250122', 6, 2, 11, 12),
('PRESLT-12-20250122', 6, 4, 13, 14),

-- Quantum Computing seminar slots (Monday 9-10, Wednesday 16-17)
('PRESLT-13-20250122', 7, 1, 9, 10),
('PRESLT-14-20250122', 7, 3, 16, 17),

-- Robotics seminar slots (Tuesday 13-14, Thursday 9-10)
('PRESLT-15-20250122', 8, 2, 13, 14),
('PRESLT-16-20250122', 8, 4, 9, 10),

-- Digital Marketing seminar slots (Monday 10-11, Friday 15-16)
('PRESLT-17-20250122', 9, 1, 10, 11),
('PRESLT-18-20250122', 9, 5, 15, 16),

-- Sustainable Technology seminar slots (Wednesday 14-15, Friday 9-10)
('PRESLT-19-20250122', 10, 3, 14, 15),
('PRESLT-20-20250122', 10, 5, 9, 10)
ON DUPLICATE KEY UPDATE start_hour = start_hour;

-- Insert sample app logs
INSERT INTO app_logs (timestamp, level, tag, message, user_id, user_role, device_info, app_version, exception_type) VALUES
(1737632345678, 'INFO', 'LOGIN', 'User logged in successfully', 6, 'STUDENT', 'Android 13, Samsung Galaxy S21', '1.2.3', NULL),
(1737632346000, 'INFO', 'QR_SCAN', 'QR code scanned successfully', 6, 'STUDENT', 'Android 13, Samsung Galaxy S21', '1.2.3', NULL),
(1737632346500, 'ERROR', 'NETWORK', 'Network connection failed', 7, 'STUDENT', 'iOS 16, iPhone 14', '1.2.3', 'NetworkException'),
(1737632347000, 'WARN', 'ATTENDANCE', 'Duplicate attendance attempt', 8, 'STUDENT', 'Android 12, Google Pixel 6', '1.2.3', NULL),
(1737632347500, 'INFO', 'SESSION', 'Session started', 1, 'PRESENTER', 'Web Browser, Chrome 117', '1.2.3', NULL),
(1737632348000, 'ERROR', 'API', 'Invalid API key provided', NULL, NULL, 'Mobile App, Version 1.2.3', '1.2.3', 'AuthenticationException'),
(1737632348500, 'INFO', 'ATTENDANCE', 'Manual attendance request submitted', 9, 'STUDENT', 'Android 13, OnePlus 9', '1.2.3', NULL),
(1737632349000, 'INFO', 'APPROVAL', 'Manual attendance approved', 1, 'PRESENTER', 'Web Browser, Chrome 117', '1.2.3', NULL)
ON DUPLICATE KEY UPDATE timestamp = timestamp;

-- Insert sample log analytics
INSERT INTO log_analytics (date, level, tag, count, unique_users) VALUES
('2025-01-22', 'INFO', 'LOGIN', 25, 15),
('2025-01-22', 'INFO', 'QR_SCAN', 45, 20),
('2025-01-22', 'ERROR', 'NETWORK', 3, 2),
('2025-01-22', 'WARN', 'ATTENDANCE', 2, 2),
('2025-01-23', 'INFO', 'LOGIN', 30, 18),
('2025-01-23', 'INFO', 'QR_SCAN', 52, 22),
('2025-01-23', 'ERROR', 'API', 1, 1),
('2025-01-23', 'INFO', 'ATTENDANCE', 38, 19),
('2025-01-24', 'INFO', 'LOGIN', 28, 16),
('2025-01-24', 'INFO', 'QR_SCAN', 48, 21),
('2025-01-24', 'INFO', 'SESSION', 5, 3),
('2025-01-24', 'INFO', 'APPROVAL', 2, 1)
ON DUPLICATE KEY UPDATE count = count + VALUES(count), unique_users = unique_users + VALUES(unique_users);

SELECT 'SemScan Dummy Data Inserted Successfully!' as Status;

-- Note: This dummy data includes:
-- - 5 presenters and 10 students
-- - 10 seminars with 23 sessions
-- - 15 attendance records (some with manual requests)
-- - 10 presenter seminars with 20 time slots
-- - Sample app logs and analytics data
-- - Realistic data relationships and timestamps
-- - All dates updated to 2025-01-22 format
-- - All IDs use 5-digit format starting from 10000
