-- SemScan Attendance System - Lean MVP Database Schema
-- MySQL 8.4 Database Schema
-- Simplified for POC: Only core attendance tracking functionality

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
    role ENUM('STUDENT', 'TEACHER') NOT NULL DEFAULT 'STUDENT',
    student_id VARCHAR(50) UNIQUE NULL, -- Only for students
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- =============================================
-- 2. COURSES TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS courses (
    course_id VARCHAR(36) PRIMARY KEY,
    course_name VARCHAR(255) NOT NULL,
    course_code VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    lecturer_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (lecturer_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- =============================================
-- 3. ENROLLMENTS TABLE (Students in Courses)
-- =============================================
CREATE TABLE IF NOT EXISTS enrollments (
    enrollment_id VARCHAR(36) PRIMARY KEY,
    course_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status ENUM('ACTIVE', 'DROPPED', 'COMPLETED') DEFAULT 'ACTIVE',
    FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY unique_enrollment (course_id, student_id)
);

-- =============================================
-- 4. SESSIONS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    course_id VARCHAR(36) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NULL,
    status ENUM('OPEN', 'CLOSED') DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
    INDEX idx_course_time (course_id, start_time),
    INDEX idx_status (status)
);

-- =============================================
-- 5. ATTENDANCE TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    attendance_time TIMESTAMP NOT NULL,
    status ENUM('PRESENT', 'LATE', 'ABSENT') DEFAULT 'PRESENT',
    method ENUM('QR_SCAN', 'MANUAL') DEFAULT 'QR_SCAN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY unique_attendance (session_id, student_id),
    INDEX idx_session_time (session_id, attendance_time),
    INDEX idx_student_time (student_id, attendance_time)
);

-- =============================================
-- 6. TEACHER API KEYS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS teacher_api_keys (
    key_id VARCHAR(36) PRIMARY KEY,
    teacher_id VARCHAR(36) NOT NULL,
    api_key VARCHAR(255) UNIQUE NOT NULL,
    key_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL,
    FOREIGN KEY (teacher_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_api_key (api_key),
    INDEX idx_teacher (teacher_id)
);

-- =============================================
-- 7. SYSTEM SETTINGS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS system_settings (
    setting_id VARCHAR(36) PRIMARY KEY,
    setting_key VARCHAR(100) UNIQUE NOT NULL,
    setting_value TEXT,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =============================================
-- INDEXES FOR PERFORMANCE
-- =============================================
CREATE INDEX idx_courses_lecturer ON courses(lecturer_id);
CREATE INDEX idx_sessions_course_status ON sessions(course_id, status);
CREATE INDEX idx_attendance_student_time ON attendance(student_id, timestamp_ms);

-- =============================================
-- SAMPLE DATA
-- =============================================

-- Insert sample teacher
INSERT INTO users (user_id, email, first_name, last_name, role) 
VALUES ('teacher-001', 'teacher@university.edu', 'John', 'Smith', 'TEACHER')
ON DUPLICATE KEY UPDATE email = email;

-- Insert sample students
INSERT INTO users (user_id, email, first_name, last_name, role, student_id) VALUES
('student-001', 'alice@student.edu', 'Alice', 'Johnson', 'STUDENT', 'STU001'),
('student-002', 'bob@student.edu', 'Bob', 'Brown', 'STUDENT', 'STU002'),
('student-003', 'charlie@student.edu', 'Charlie', 'Davis', 'STUDENT', 'STU003'),
('student-004', 'diana@student.edu', 'Diana', 'Wilson', 'STUDENT', 'STU004'),
('student-005', 'eve@student.edu', 'Eve', 'Garcia', 'STUDENT', 'STU005')
ON DUPLICATE KEY UPDATE email = email;

-- Insert sample course
INSERT INTO courses (course_id, course_name, course_code, description, lecturer_id) 
VALUES ('course-001', 'Introduction to Computer Science', 'CS101', 'Basic programming concepts and algorithms', 'teacher-001')
ON DUPLICATE KEY UPDATE course_name = course_name;

-- Insert sample enrollments
INSERT INTO enrollments (enrollment_id, course_id, student_id) VALUES
('enroll-001', 'course-001', 'student-001'),
('enroll-002', 'course-001', 'student-002'),
('enroll-003', 'course-001', 'student-003'),
('enroll-004', 'course-001', 'student-004'),
('enroll-005', 'course-001', 'student-005')
ON DUPLICATE KEY UPDATE enrollment_id = enrollment_id;

-- Insert sample sessions
INSERT INTO sessions (session_id, course_id, start_time, status) VALUES
('session-001', 'course-001', NOW(), 'OPEN'),
('session-002', 'course-001', NOW() - INTERVAL 1 DAY, 'CLOSED'),
('session-003', 'course-001', NOW() - INTERVAL 2 DAY, 'CLOSED')
ON DUPLICATE KEY UPDATE start_time = start_time;

-- Insert sample attendance records
INSERT INTO attendance (attendance_id, session_id, student_id, attendance_time, status, method) VALUES
-- Session 1 (OPEN) - Some students attended
('attendance-001', 'session-001', 'student-001', NOW(), 'PRESENT', 'QR_SCAN'),
('attendance-002', 'session-001', 'student-002', NOW(), 'PRESENT', 'QR_SCAN'),
('attendance-003', 'session-001', 'student-003', NOW(), 'PRESENT', 'QR_SCAN'),

-- Session 2 (CLOSED) - Some students attended
('attendance-004', 'session-002', 'student-001', NOW() - INTERVAL 1 DAY, 'PRESENT', 'QR_SCAN'),
('attendance-005', 'session-002', 'student-002', NOW() - INTERVAL 1 DAY, 'LATE', 'QR_SCAN'),
('attendance-006', 'session-002', 'student-004', NOW() - INTERVAL 1 DAY, 'PRESENT', 'QR_SCAN'),

-- Session 3 (CLOSED) - Some students attended
('attendance-007', 'session-003', 'student-001', NOW() - INTERVAL 2 DAY, 'PRESENT', 'QR_SCAN'),
('attendance-008', 'session-003', 'student-003', NOW() - INTERVAL 2 DAY, 'PRESENT', 'QR_SCAN'),
('attendance-009', 'session-003', 'student-005', NOW() - INTERVAL 2 DAY, 'LATE', 'QR_SCAN')
ON DUPLICATE KEY UPDATE attendance_time = attendance_time;

-- Insert sample API key for teacher
INSERT INTO teacher_api_keys (key_id, teacher_id, api_key, key_name) 
VALUES ('key-001', 'teacher-001', 'test-api-key-12345', 'Default API Key')
ON DUPLICATE KEY UPDATE api_key = api_key;

-- Insert system settings
INSERT INTO system_settings (setting_id, setting_key, setting_value, description) VALUES
('setting-001', 'qr_timeout_minutes', '15', 'QR code validity period in minutes'),
('setting-002', 'attendance_threshold', '75', 'Minimum attendance percentage required'),
('setting-003', 'session_timeout_minutes', '30', 'Session timeout in minutes')
ON DUPLICATE KEY UPDATE setting_value = setting_value;

-- =============================================
-- VIEWS FOR COMMON QUERIES
-- =============================================

-- View for attendance summary
CREATE OR REPLACE VIEW attendance_summary AS
SELECT 
    c.course_name,
    c.course_code,
    s.session_id,
    s.start_time,
    s.status as session_status,
    COUNT(DISTINCT e.student_id) as total_enrolled,
    COUNT(DISTINCT a.student_id) as attended_count,
    ROUND((COUNT(DISTINCT a.student_id) / COUNT(DISTINCT e.student_id)) * 100, 2) as attendance_percentage
FROM courses c
JOIN sessions s ON c.course_id = s.course_id
LEFT JOIN enrollments e ON c.course_id = e.course_id AND e.status = 'ACTIVE'
LEFT JOIN attendance a ON s.session_id = a.session_id
GROUP BY c.course_id, s.session_id;

-- View for student attendance history
CREATE OR REPLACE VIEW student_attendance_history AS
SELECT 
    u.user_id,
    u.first_name,
    u.last_name,
    u.student_id,
    c.course_name,
    c.course_code,
    s.start_time,
    s.session_id,
    a.status as attendance_status,
    a.attendance_time,
    a.method
FROM users u
JOIN enrollments e ON u.user_id = e.student_id
JOIN courses c ON e.course_id = c.course_id
LEFT JOIN sessions s ON c.course_id = s.course_id
LEFT JOIN attendance a ON s.session_id = a.session_id AND u.user_id = a.student_id
WHERE u.role = 'STUDENT' AND e.status = 'ACTIVE';

-- =============================================
-- COMPLETION MESSAGE
-- =============================================
SELECT 'SemScan Lean MVP Database Schema Created Successfully!' as Status;
