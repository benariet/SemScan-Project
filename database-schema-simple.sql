-- SemScan Seminar Attendance System - MVP Database Schema
-- MySQL 8.4 Database Schema
-- Uses proper TIMESTAMP fields for better logging and debugging

-- Create database
CREATE DATABASE IF NOT EXISTS semscan_db;
USE semscan_db;

-- =============================================
-- 1. USERS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(36) PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    role ENUM('STUDENT', 'PRESENTER') NOT NULL DEFAULT 'STUDENT',
    student_id VARCHAR(50) UNIQUE NULL
);

-- =============================================
-- 2. SEMINARS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS seminars (
    seminar_id VARCHAR(36) PRIMARY KEY,
    seminar_name VARCHAR(255) NOT NULL,
    seminar_code VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    presenter_id VARCHAR(36) NOT NULL,
    FOREIGN KEY (presenter_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- =============================================
-- 3. SESSIONS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    seminar_id VARCHAR(36) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NULL,
    status ENUM('OPEN', 'CLOSED') DEFAULT 'OPEN',
    FOREIGN KEY (seminar_id) REFERENCES seminars(seminar_id) ON DELETE CASCADE
);

-- =============================================
-- 4. ATTENDANCE TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    attendance_time TIMESTAMP NOT NULL,
    method ENUM('QR_SCAN', 'MANUAL', 'PROXY') DEFAULT 'QR_SCAN',
    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY unique_attendance (session_id, student_id)
);

-- =============================================
-- 5. TEACHER API KEYS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS presenter_api_keys (
    api_key_id VARCHAR(36) PRIMARY KEY,
    presenter_id VARCHAR(36) NOT NULL,
    api_key VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (presenter_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- =============================================
-- SAMPLE DATA
-- =============================================

-- Insert sample presenters
INSERT INTO users (user_id, email, first_name, last_name, role) VALUES
('presenter-001', 'dr.john.smith@university.edu', 'Dr. John', 'Smith', 'PRESENTER'),
('presenter-002', 'prof.sarah.jones@university.edu', 'Prof. Sarah', 'Jones', 'PRESENTER'),
('presenter-003', 'dr.mike.wilson@university.edu', 'Dr. Mike', 'Wilson', 'PRESENTER')
ON DUPLICATE KEY UPDATE email = email;

-- Insert sample students
INSERT INTO users (user_id, email, first_name, last_name, role, student_id) VALUES
('student-001', 'alice.johnson@student.edu', 'Alice', 'Johnson', 'STUDENT', 'STU001'),
('student-002', 'bob.brown@student.edu', 'Bob', 'Brown', 'STUDENT', 'STU002'),
('student-003', 'charlie.davis@student.edu', 'Charlie', 'Davis', 'STUDENT', 'STU003'),
('student-004', 'diana.wilson@student.edu', 'Diana', 'Wilson', 'STUDENT', 'STU004'),
('student-005', 'eve.garcia@student.edu', 'Eve', 'Garcia', 'STUDENT', 'STU005'),
('student-006', 'frank.miller@student.edu', 'Frank', 'Miller', 'STUDENT', 'STU006'),
('student-007', 'grace.lee@student.edu', 'Grace', 'Lee', 'STUDENT', 'STU007'),
('student-008', 'henry.taylor@student.edu', 'Henry', 'Taylor', 'STUDENT', 'STU008')
ON DUPLICATE KEY UPDATE email = email;

-- Insert sample seminars
INSERT INTO seminars (seminar_id, seminar_name, seminar_code, description, presenter_id) VALUES
('seminar-001', 'AI and Machine Learning in Healthcare', 'AI-HLTH-001', 'Exploring applications of AI in medical diagnosis and treatment', 'presenter-001'),
('seminar-002', 'Blockchain Technology and Cryptocurrency', 'BLK-CRYPT-001', 'Understanding blockchain fundamentals and crypto markets', 'presenter-001'),
('seminar-003', 'Cybersecurity Best Practices', 'CYBER-SEC-001', 'Modern cybersecurity threats and defense strategies', 'presenter-002'),
('seminar-004', 'Cloud Computing Architecture', 'CLOUD-ARCH-001', 'Designing scalable cloud infrastructure', 'presenter-002'),
('seminar-005', 'Data Science and Analytics', 'DATA-ANAL-001', 'Extracting insights from big data', 'presenter-003'),
('seminar-006', 'IoT and Smart Cities', 'IOT-SMART-001', 'Internet of Things applications in urban development', 'presenter-003')
ON DUPLICATE KEY UPDATE seminar_name = seminar_name;

-- Insert sample sessions
INSERT INTO sessions (session_id, seminar_id, start_time, status) VALUES
-- AI Healthcare sessions
('session-001', 'seminar-001', '2024-09-18 10:00:00', 'CLOSED'),
('session-002', 'seminar-001', '2024-09-20 10:00:00', 'OPEN'),
('session-003', 'seminar-001', '2024-09-25 10:00:00', 'OPEN'),

-- Blockchain sessions
('session-004', 'seminar-002', '2024-09-19 14:00:00', 'CLOSED'),
('session-005', 'seminar-002', '2024-09-21 14:00:00', 'OPEN'),

-- Cybersecurity sessions
('session-006', 'seminar-003', '2024-09-18 16:00:00', 'CLOSED'),
('session-007', 'seminar-003', '2024-09-22 16:00:00', 'OPEN'),

-- Cloud Computing sessions
('session-008', 'seminar-004', '2024-09-19 12:00:00', 'OPEN'),

-- Data Science sessions
('session-009', 'seminar-005', '2024-09-20 15:00:00', 'OPEN'),

-- IoT sessions
('session-010', 'seminar-006', '2024-09-21 11:00:00', 'OPEN')
ON DUPLICATE KEY UPDATE start_time = start_time;

-- Insert sample attendance records
INSERT INTO attendance (attendance_id, session_id, student_id, attendance_time, method) VALUES
-- Session 1 (AI Healthcare - CLOSED) - Some students attended
('attendance-001', 'session-001', 'student-001', '2024-09-18 10:05:00', 'QR_SCAN'),
('attendance-002', 'session-001', 'student-002', '2024-09-18 10:15:00', 'QR_SCAN'),
('attendance-003', 'session-001', 'student-003', '2024-09-18 10:02:00', 'QR_SCAN'),
('attendance-004', 'session-001', 'student-004', '2024-09-18 10:08:00', 'QR_SCAN'),

-- Session 4 (Blockchain - CLOSED) - Some students attended
('attendance-005', 'session-004', 'student-001', '2024-09-19 14:03:00', 'QR_SCAN'),
('attendance-006', 'session-004', 'student-002', '2024-09-19 14:20:00', 'QR_SCAN'),
('attendance-007', 'session-004', 'student-005', '2024-09-19 14:01:00', 'QR_SCAN'),

-- Session 6 (Cybersecurity - CLOSED) - Some students attended
('attendance-008', 'session-006', 'student-003', '2024-09-18 16:05:00', 'QR_SCAN'),
('attendance-009', 'session-006', 'student-006', '2024-09-18 16:02:00', 'QR_SCAN'),
('attendance-010', 'session-006', 'student-007', '2024-09-18 16:10:00', 'QR_SCAN')
ON DUPLICATE KEY UPDATE attendance_time = attendance_time;

-- Insert sample presenter API keys
INSERT INTO presenter_api_keys (api_key_id, presenter_id, api_key, is_active) VALUES
('api-key-001', 'presenter-001', 'presenter-001-api-key-12345', TRUE),
('api-key-002', 'presenter-002', 'presenter-002-api-key-67890', TRUE),
('api-key-003', 'presenter-003', 'presenter-003-api-key-abcde', TRUE)
ON DUPLICATE KEY UPDATE api_key = api_key;

SELECT 'SemScan Seminar Attendance MVP Database Schema Created Successfully!' as Status;

-- Note: This schema uses proper TIMESTAMP fields for better logging and debugging
-- Mobile apps send Unix milliseconds, server converts to TIMESTAMP
-- No absence_requests table - MVP focuses on seminar attendance only
-- Uses seminars and presenters instead of courses and teachers
