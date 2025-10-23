-- SemScan Seminar Attendance System - Database Schema DDL
-- MySQL 8.4 Database Schema
-- Data Definition Language (DDL) - Table Creation Only
-- Uses proper TIMESTAMP fields for better logging and debugging

-- Drop and recreate database for clean start
DROP DATABASE IF EXISTS semscan_db;
CREATE DATABASE semscan_db;
USE semscan_db;

-- =============================================
-- 1. USERS TABLE
-- =============================================
-- Stores user information for students and presenters
-- Example: | id | user_id                | first_name | last_name | email | role       | student_id |
--          |----|------------------------|------------|-----------|-------|------------|------------|
--          | 1  | USERID-1-20250122 | John       | Smith     | john@ | PRESENTER  | NULL       |
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(30) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    role ENUM('STUDENT', 'PRESENTER') NOT NULL DEFAULT 'STUDENT',
    student_id VARCHAR(50) UNIQUE NULL,
    CONSTRAINT uk_users_user_id UNIQUE (user_id)
);

-- =============================================
-- 2. SEMINARS TABLE
-- =============================================
-- Stores seminar information with unique codes
-- Example: | id | seminar_id              | seminar_name      | seminar_code | description      | presenter_id |
--          |----|-------------------------|-------------------|--------------|------------------|-------------|
--          | 1  | SEMINR-1-20250122   | AI in Healthcare  | AI-HLTH-001  | AI applications  | USERID-1-20250122 |
CREATE TABLE IF NOT EXISTS seminars (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seminar_id VARCHAR(30) UNIQUE NOT NULL,
    seminar_name VARCHAR(255) NOT NULL,
    seminar_code VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    presenter_id BIGINT NOT NULL,
    FOREIGN KEY (presenter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_seminars_seminar_id UNIQUE (seminar_id)
);

-- =============================================
-- 3. SESSIONS TABLE
-- =============================================
-- Stores individual seminar sessions with timing
-- Example: | id | session_id              | seminar_id | start_time           | end_time | status |
--          |----|------------------------|------------|----------------------|----------|--------|
--          | 1  | SESSIN-1-20250122   | SEMINR-1-20250122 | 2025-01-22 10:00:00  | NULL     | OPEN   |
CREATE TABLE IF NOT EXISTS sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(30) UNIQUE NOT NULL,
    seminar_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NULL,
    status ENUM('OPEN', 'CLOSED') DEFAULT 'OPEN',
    FOREIGN KEY (seminar_id) REFERENCES seminars(id) ON DELETE CASCADE,
    CONSTRAINT uk_sessions_session_id UNIQUE (session_id)
);

-- =============================================
-- 4. ATTENDANCE TABLE
-- =============================================
-- Stores student attendance records with various methods
-- Example: | id | attendance_id           | session_id | student_id | attendance_time        | method  | request_status |
--          |----|------------------------|------------|------------|------------------------|---------|----------------|
--          | 1  | ATTEND-1-20250122   | SESSIN-1-20250122 | USERID-6-20250122 | 2025-01-22 10:05:00    | QR_SCAN | CONFIRMED      |
CREATE TABLE IF NOT EXISTS attendance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attendance_id VARCHAR(30) UNIQUE NOT NULL,
    session_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    attendance_time TIMESTAMP NOT NULL,
    method ENUM('QR_SCAN', 'MANUAL', 'MANUAL_REQUEST', 'PROXY') DEFAULT 'QR_SCAN',
    request_status ENUM('CONFIRMED', 'PENDING_APPROVAL', 'REJECTED') DEFAULT 'CONFIRMED',
    manual_reason VARCHAR(255) NULL,
    requested_at TIMESTAMP NULL,
    approved_by BIGINT NULL,
    approved_at TIMESTAMP NULL,
    device_id VARCHAR(255) NULL,
    auto_flags JSON NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY unique_attendance (session_id, student_id),
    CONSTRAINT uk_attendance_attendance_id UNIQUE (attendance_id)
);


-- =============================================
-- 6. PRESENTER SEMINAR TILES TABLE
-- =============================================
-- Stores presenter seminar tiles (subject-free)
-- Example: | id | presenter_seminar_id      | presenter_id | seminar_name   | created_at |
--          |----|---------------------------|-------------|----------------|------------|
--          | 1  | PRESEM-1-20250122     | USERID-1-20250122 | AI Healthcare  | 2025-01-22 |
CREATE TABLE IF NOT EXISTS presenter_seminar (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    presenter_seminar_id VARCHAR(30) UNIQUE NOT NULL,
    presenter_id BIGINT NOT NULL,
    seminar_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (presenter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_presenter_seminar_presenter_seminar_id UNIQUE (presenter_seminar_id)
);

-- Create index for presenter_seminar presenter_id
CREATE INDEX idx_ps_presenter ON presenter_seminar(presenter_id);

-- =============================================
-- 7. PRESENTER SEMINAR SLOTS TABLE
-- =============================================
-- Stores weekly time slots for presenter seminars
-- Example: | id | presenter_seminar_slot_id   | presenter_seminar_id | weekday | start_hour | end_hour |
--          |----|-----------------------------|---------------------|---------|------------|----------|
--          | 1  | PRESLT-1-20250122      | PRESEM-1-20250122     | 1       | 10         | 11       |
CREATE TABLE IF NOT EXISTS presenter_seminar_slot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    presenter_seminar_slot_id VARCHAR(30) UNIQUE NOT NULL,
    presenter_seminar_id BIGINT NOT NULL,
    weekday TINYINT NOT NULL,
    start_hour TINYINT NOT NULL,
    end_hour TINYINT NOT NULL,
    FOREIGN KEY (presenter_seminar_id) REFERENCES presenter_seminar(id) ON DELETE CASCADE,
    CONSTRAINT uk_presenter_seminar_slot_presenter_seminar_slot_id UNIQUE (presenter_seminar_slot_id)
);

-- Create index for presenter_seminar_slot presenter_seminar_id
CREATE INDEX idx_ps_slot_psid ON presenter_seminar_slot(presenter_seminar_id);

-- =============================================
-- 8. APP LOGS TABLE (Logging System)
-- =============================================
-- Stores application logs from mobile apps
-- Example: | id | timestamp      | level | tag   | message        | user_id | device_info |
--          |----|----------------|-------|-------|----------------|---------|-------------|
--          | 1  | 1695123456789  | INFO  | LOGIN | User logged in | USERID-6-20250122 | Android 13  |
CREATE TABLE IF NOT EXISTS app_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp BIGINT NOT NULL,
    level VARCHAR(10) NOT NULL,
    tag VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    user_id BIGINT,
    user_role VARCHAR(20),
    device_info TEXT,
    app_version VARCHAR(20),
    stack_trace TEXT,
    exception_type VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_timestamp (timestamp),
    INDEX idx_level (level),
    INDEX idx_tag (tag),
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
);

-- =============================================
-- 9. LOG ANALYTICS TABLE
-- =============================================
-- Stores aggregated log analytics for reporting
-- Example: | id | date | level | tag | count | unique_users | created_at |
--          |----|------|-------|-----|-------|--------------|------------|
--          | 1  | 2024-09-18 | INFO | LOGIN | 25 | 15 | 2024-09-18 |
CREATE TABLE IF NOT EXISTS log_analytics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    date DATE NOT NULL,
    level VARCHAR(10) NOT NULL,
    tag VARCHAR(50) NOT NULL,
    count INT NOT NULL,
    unique_users INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_date_level_tag (date, level, tag)
);

-- =============================================
-- DUAL ID SYSTEM
-- =============================================
-- This database uses a dual ID system for optimal performance and usability:
-- 1. AUTO_INCREMENT numeric IDs (id) - Used for internal database operations, foreign keys, and performance
-- 2. String IDs (user_id, seminar_id, etc.) - Used for external API communication and mobile app integration
-- String IDs are generated using the numeric ID for perfect correlation
-- Format: PREFIX-NUMERIC_ID-DATE (e.g., USERID-1-20250122)
-- This approach provides both database efficiency and human-readable external identifiers

-- =============================================
-- TRIGGERS FOR AUTOMATIC ID GENERATION
-- =============================================

-- Trigger for users table
DELIMITER $$
CREATE TRIGGER tr_users_before_insert
BEFORE INSERT ON users
FOR EACH ROW
BEGIN
    -- Generate string ID using numeric ID for perfect correlation
    SET NEW.user_id = CONCAT('USERID-', NEW.id, '-', DATE_FORMAT(CURDATE(), '%Y%m%d'));
END$$
DELIMITER ;

-- Trigger for seminars table
DELIMITER $$
CREATE TRIGGER tr_seminars_before_insert
BEFORE INSERT ON seminars
FOR EACH ROW
BEGIN
    -- Generate string ID using numeric ID for perfect correlation
    SET NEW.seminar_id = CONCAT('SEMINR-', NEW.id, '-', DATE_FORMAT(CURDATE(), '%Y%m%d'));
END$$
DELIMITER ;

-- Trigger for sessions table
DELIMITER $$
CREATE TRIGGER tr_sessions_before_insert
BEFORE INSERT ON sessions
FOR EACH ROW
BEGIN
    -- Generate string ID using numeric ID for perfect correlation
    SET NEW.session_id = CONCAT('SESSIN-', NEW.id, '-', DATE_FORMAT(CURDATE(), '%Y%m%d'));
END$$
DELIMITER ;

-- Trigger for attendance table
DELIMITER $$
CREATE TRIGGER tr_attendance_before_insert
BEFORE INSERT ON attendance
FOR EACH ROW
BEGIN
    -- Generate string ID using numeric ID for perfect correlation
    SET NEW.attendance_id = CONCAT('ATTEND-', NEW.id, '-', DATE_FORMAT(CURDATE(), '%Y%m%d'));
END$$
DELIMITER ;


-- Trigger for presenter_seminar table
DELIMITER $$
CREATE TRIGGER tr_presenter_seminar_before_insert
BEFORE INSERT ON presenter_seminar
FOR EACH ROW
BEGIN
    -- Generate string ID using numeric ID for perfect correlation
    SET NEW.presenter_seminar_id = CONCAT('PRESEM-', NEW.id, '-', DATE_FORMAT(CURDATE(), '%Y%m%d'));
END$$
DELIMITER ;

-- Trigger for presenter_seminar_slot table
DELIMITER $$
CREATE TRIGGER tr_presenter_seminar_slot_before_insert
BEFORE INSERT ON presenter_seminar_slot
FOR EACH ROW
BEGIN
    -- Generate string ID using numeric ID for perfect correlation
    SET NEW.presenter_seminar_slot_id = CONCAT('PRESLT-', NEW.id, '-', DATE_FORMAT(CURDATE(), '%Y%m%d'));
END$$
DELIMITER ;

SELECT 'SemScan Database Schema DDL Created Successfully!' as Status;

-- Note: This schema uses proper TIMESTAMP fields for better logging and debugging
-- Mobile apps send Unix milliseconds, server converts to TIMESTAMP
-- Includes manual attendance request system with approval workflow
-- Uses seminars and presenters instead of courses and teachers
-- Manual attendance requests are stored in the same attendance table with request_status field
-- Includes comprehensive logging system for mobile app analytics
-- Seminar slots use integer hours (0-23) for start_hour and (1-24) for end_hour
-- Weekdays are stored as integers (0=Sunday, 1=Monday, ..., 6=Saturday)
