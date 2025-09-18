-- SemScan Attendance System - Simplified Database Schema
-- MySQL 8.4 Database Schema

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
    role ENUM('STUDENT', 'TEACHER', 'ADMIN') NOT NULL DEFAULT 'STUDENT',
    student_id VARCHAR(50) UNIQUE NULL
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
    FOREIGN KEY (lecturer_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- =============================================
-- 3. SESSIONS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    course_id VARCHAR(36) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NULL,
    status ENUM('OPEN', 'CLOSED') DEFAULT 'OPEN',
    qr_code_data TEXT,
    FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE
);

-- =============================================
-- 4. ATTENDANCE TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    attendance_time TIMESTAMP NOT NULL,
    status ENUM('PRESENT', 'LATE', 'ABSENT') DEFAULT 'PRESENT',
    method ENUM('QR_SCAN', 'MANUAL', 'PROXY') DEFAULT 'QR_SCAN',
    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY unique_attendance (session_id, student_id)
);

-- =============================================
-- 5. ABSENCE REQUESTS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS absence_requests (
    request_id VARCHAR(36) PRIMARY KEY,
    student_id VARCHAR(36) NOT NULL,
    course_id VARCHAR(36) NOT NULL,
    session_id VARCHAR(36) NULL,
    reason VARCHAR(255) NOT NULL,
    note TEXT,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE
);

-- =============================================
-- SAMPLE DATA
-- =============================================

-- Insert sample teachers
INSERT INTO users (user_id, email, first_name, last_name, role) VALUES
('teacher-001', 'john.smith@university.edu', 'John', 'Smith', 'TEACHER'),
('teacher-002', 'sarah.jones@university.edu', 'Sarah', 'Jones', 'TEACHER'),
('teacher-003', 'mike.wilson@university.edu', 'Mike', 'Wilson', 'TEACHER'),
('admin-001', 'admin@university.edu', 'Admin', 'User', 'ADMIN')
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

-- Insert sample courses
INSERT INTO courses (course_id, course_name, course_code, description, lecturer_id) VALUES
('course-001', 'Introduction to Computer Science', 'CS101', 'Basic programming concepts and algorithms', 'teacher-001'),
('course-002', 'Data Structures and Algorithms', 'CS201', 'Advanced data structures and algorithm design', 'teacher-001'),
('course-003', 'Database Systems', 'CS301', 'Database design and management', 'teacher-002'),
('course-004', 'Web Development', 'CS401', 'Full-stack web development', 'teacher-002'),
('course-005', 'Software Engineering', 'CS501', 'Software development methodologies', 'teacher-003'),
('course-006', 'Machine Learning', 'CS601', 'Introduction to machine learning', 'teacher-003')
ON DUPLICATE KEY UPDATE course_name = course_name;

-- Insert sample sessions
INSERT INTO sessions (session_id, course_id, start_time, status, qr_code_data) VALUES
-- CS101 sessions
('session-001', 'course-001', '2024-09-18 10:00:00', 'CLOSED', 'QR_CS101_001'),
('session-002', 'course-001', '2024-09-20 10:00:00', 'OPEN', 'QR_CS101_002'),
('session-003', 'course-001', '2024-09-25 10:00:00', 'OPEN', 'QR_CS101_003'),

-- CS201 sessions
('session-004', 'course-002', '2024-09-19 14:00:00', 'CLOSED', 'QR_CS201_001'),
('session-005', 'course-002', '2024-09-21 14:00:00', 'OPEN', 'QR_CS201_002'),

-- CS301 sessions
('session-006', 'course-003', '2024-09-18 16:00:00', 'CLOSED', 'QR_CS301_001'),
('session-007', 'course-003', '2024-09-22 16:00:00', 'OPEN', 'QR_CS301_002'),

-- CS401 sessions
('session-008', 'course-004', '2024-09-19 12:00:00', 'OPEN', 'QR_CS401_001'),

-- CS501 sessions
('session-009', 'course-005', '2024-09-20 15:00:00', 'OPEN', 'QR_CS501_001'),

-- CS601 sessions
('session-010', 'course-006', '2024-09-21 11:00:00', 'OPEN', 'QR_CS601_001')
ON DUPLICATE KEY UPDATE start_time = start_time;

-- Insert sample attendance records
INSERT INTO attendance (attendance_id, session_id, student_id, attendance_time, status, method) VALUES
-- Session 1 (CS101 - CLOSED) - Some students attended
('attendance-001', 'session-001', 'student-001', '2024-09-18 10:05:00', 'PRESENT', 'QR_SCAN'),
('attendance-002', 'session-001', 'student-002', '2024-09-18 10:15:00', 'LATE', 'QR_SCAN'),
('attendance-003', 'session-001', 'student-003', '2024-09-18 10:02:00', 'PRESENT', 'QR_SCAN'),
('attendance-004', 'session-001', 'student-004', '2024-09-18 10:08:00', 'PRESENT', 'QR_SCAN'),

-- Session 4 (CS201 - CLOSED) - Some students attended
('attendance-005', 'session-004', 'student-001', '2024-09-19 14:03:00', 'PRESENT', 'QR_SCAN'),
('attendance-006', 'session-004', 'student-002', '2024-09-19 14:20:00', 'LATE', 'QR_SCAN'),
('attendance-007', 'session-004', 'student-005', '2024-09-19 14:01:00', 'PRESENT', 'QR_SCAN'),

-- Session 6 (CS301 - CLOSED) - Some students attended
('attendance-008', 'session-006', 'student-003', '2024-09-18 16:05:00', 'PRESENT', 'QR_SCAN'),
('attendance-009', 'session-006', 'student-006', '2024-09-18 16:02:00', 'PRESENT', 'QR_SCAN'),
('attendance-010', 'session-006', 'student-007', '2024-09-18 16:10:00', 'LATE', 'QR_SCAN')
ON DUPLICATE KEY UPDATE attendance_time = attendance_time;

-- Insert sample absence requests
INSERT INTO absence_requests (request_id, student_id, course_id, session_id, reason, note, status, submitted_at) VALUES
('request-001', 'student-005', 'course-001', 'session-001', 'Medical appointment', 'Had a doctor appointment', 'APPROVED', '2024-09-17 15:30:00'),
('request-002', 'student-008', 'course-002', 'session-004', 'Family emergency', 'Family member in hospital', 'APPROVED', '2024-09-18 09:15:00'),
('request-003', 'student-002', 'course-003', 'session-006', 'Transportation issue', 'Bus was late', 'PENDING', '2024-09-18 16:45:00'),
('request-004', 'student-004', 'course-001', NULL, 'General absence', 'Will be absent for next week', 'PENDING', '2024-09-19 10:20:00'),
('request-005', 'student-006', 'course-002', 'session-004', 'Technical difficulties', 'Could not access online materials', 'REJECTED', '2024-09-18 14:30:00')
ON DUPLICATE KEY UPDATE reason = reason;

SELECT 'SemScan Database Schema Created Successfully!' as Status;
