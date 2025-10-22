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
-- Example: | user_id           | first_name | last_name | email | role       | student_id |
--          |-------------------|------------|-----------|-------|------------|------------|
--          | USR-1000-20240918 | John       | Smith     | john@ | PRESENTER  | NULL       |
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(20) PRIMARY KEY,
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
-- Example: | seminar_id         | seminar_name      | seminar_code | description      | presenter_id     |
--          |--------------------|-------------------|--------------|------------------|------------------|
--          | SEM-1000-20240918  | AI in Healthcare  | AI-HLTH-001  | AI applications  | USR-1000-20240918|
CREATE TABLE IF NOT EXISTS seminars (
    seminar_id VARCHAR(20) PRIMARY KEY,
    seminar_name VARCHAR(255) NOT NULL,
    seminar_code VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    presenter_id VARCHAR(20) NOT NULL,
    FOREIGN KEY (presenter_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uk_seminars_seminar_id UNIQUE (seminar_id)
);

-- =============================================
-- 3. SESSIONS TABLE
-- =============================================
-- Stores individual seminar sessions with timing
-- Example: | session_id         | seminar_id        | start_time           | end_time | status |
--          |--------------------|-------------------|----------------------|----------|--------|
--          | SES-1000-20240918  | SEM-1000-20240918 | 2024-09-18 10:00:00  | NULL     | OPEN   |
CREATE TABLE IF NOT EXISTS sessions (
    session_id VARCHAR(20) PRIMARY KEY,
    seminar_id VARCHAR(20) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NULL,
    status ENUM('OPEN', 'CLOSED') DEFAULT 'OPEN',
    FOREIGN KEY (seminar_id) REFERENCES seminars(seminar_id) ON DELETE CASCADE,
    CONSTRAINT uk_sessions_session_id UNIQUE (session_id)
);

-- =============================================
-- 4. ATTENDANCE TABLE
-- =============================================
-- Stores student attendance records with various methods
-- Example: | attendance_id      | session_id        | student_id         | attendance_time        | method  | request_status |
--          |--------------------|--------------------|--------------------|------------------------|---------|----------------|
--          | ATT-1000-20240918  | SES-1000-20240918  | USR-1000-20240918  | 2024-09-18 10:05:00    | QR_SCAN | CONFIRMED      |
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id VARCHAR(20) PRIMARY KEY,
    session_id VARCHAR(20) NOT NULL,
    student_id VARCHAR(20) NOT NULL,
    attendance_time TIMESTAMP NOT NULL,
    method ENUM('QR_SCAN', 'MANUAL', 'MANUAL_REQUEST', 'PROXY') DEFAULT 'QR_SCAN',
    request_status ENUM('CONFIRMED', 'PENDING_APPROVAL', 'REJECTED') DEFAULT 'CONFIRMED',
    manual_reason VARCHAR(255) NULL,
    requested_at TIMESTAMP NULL,
    approved_by VARCHAR(20) NULL,
    approved_at TIMESTAMP NULL,
    device_id VARCHAR(255) NULL,
    auto_flags JSON NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (approved_by) REFERENCES users(user_id) ON DELETE SET NULL,
    UNIQUE KEY unique_attendance (session_id, student_id),
    CONSTRAINT uk_attendance_attendance_id UNIQUE (attendance_id)
);

-- =============================================
-- 5. PRESENTER API KEYS TABLE
-- =============================================
-- Stores API keys for presenter authentication
-- Example: | api_key_id         | presenter_id       | api_key | created_at | is_active |
--          |--------------------|--------------------|---------|------------|-----------|
--          | API-1000-20240918  | USR-1000-20240918  | abc123  | 2024-09-18 | TRUE      |
CREATE TABLE IF NOT EXISTS presenter_api_keys (
    api_key_id VARCHAR(20) PRIMARY KEY,
    presenter_id VARCHAR(20) NOT NULL,
    api_key VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (presenter_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uk_presenter_api_keys_api_key_id UNIQUE (api_key_id)
);

-- =============================================
-- 6. PRESENTER SEMINAR TILES TABLE
-- =============================================
-- Stores presenter seminar tiles (subject-free)
-- Example: | presenter_seminar_id | presenter_id       | seminar_name   | created_at |
--          |---------------------|--------------------|----------------|------------|
--          | PSM-1000-20240918   | USR-1000-20240918  | AI Healthcare  | 2024-09-18 |
CREATE TABLE IF NOT EXISTS presenter_seminar (
    presenter_seminar_id VARCHAR(20) PRIMARY KEY,
    presenter_id VARCHAR(20) NOT NULL,
    seminar_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (presenter_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uk_presenter_seminar_presenter_seminar_id UNIQUE (presenter_seminar_id)
);

-- Create index for presenter_seminar presenter_id
CREATE INDEX idx_ps_presenter ON presenter_seminar(presenter_id);

-- =============================================
-- 7. PRESENTER SEMINAR SLOTS TABLE
-- =============================================
-- Stores weekly time slots for presenter seminars
-- Example: | presenter_seminar_slot_id | presenter_seminar_id | weekday | start_hour | end_hour |
--          |-------------------------|---------------------|---------|------------|----------|
--          | PSS-1000-20240918      | PSM-1000-20240918   | 1       | 10         | 11       |
CREATE TABLE IF NOT EXISTS presenter_seminar_slot (
    presenter_seminar_slot_id VARCHAR(20) PRIMARY KEY,
    presenter_seminar_id VARCHAR(20) NOT NULL,
    weekday TINYINT NOT NULL,
    start_hour TINYINT NOT NULL,
    end_hour TINYINT NOT NULL,
    FOREIGN KEY (presenter_seminar_id) REFERENCES presenter_seminar(presenter_seminar_id) ON DELETE CASCADE,
    CONSTRAINT uk_presenter_seminar_slot_presenter_seminar_slot_id UNIQUE (presenter_seminar_slot_id)
);

-- Create index for presenter_seminar_slot presenter_seminar_id
CREATE INDEX idx_ps_slot_psid ON presenter_seminar_slot(presenter_seminar_id);

-- =============================================
-- 8. APP LOGS TABLE (Logging System)
-- =============================================
-- Stores application logs from mobile apps
-- Example: | id | timestamp      | level | tag   | message        | user_id         | device_info |
--          |----|----------------|-------|-------|----------------|-----------------|-------------|
--          | 1  | 1695123456789  | INFO  | LOGIN | User logged in | USR-1000-20240918| Android 13  |
CREATE TABLE IF NOT EXISTS app_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp BIGINT NOT NULL,
    level VARCHAR(10) NOT NULL,
    tag VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    user_id VARCHAR(20),
    user_role VARCHAR(20),
    device_info TEXT,
    app_version VARCHAR(20),
    stack_trace TEXT,
    exception_type VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
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
-- AUTOMATIC ID GENERATION FUNCTIONS
-- =============================================

-- Function to generate next ID for a given table prefix with duplicate prevention
DELIMITER $$
CREATE FUNCTION generate_next_id(table_prefix VARCHAR(10)) 
RETURNS VARCHAR(20)
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE next_num INT DEFAULT 1000;
    DECLARE today_date VARCHAR(8);
    DECLARE result_id VARCHAR(20);
    DECLARE max_attempts INT DEFAULT 100;
    DECLARE attempt_count INT DEFAULT 0;
    DECLARE id_exists BOOLEAN DEFAULT TRUE;
    
    SET today_date = DATE_FORMAT(CURDATE(), '%Y%m%d');
    
    -- Get the next number based on table prefix
    CASE table_prefix
        WHEN 'USR' THEN
            SELECT COALESCE(MAX(CAST(SUBSTRING(user_id, 5, 4) AS UNSIGNED)), 999) + 1 INTO next_num
            FROM users WHERE user_id LIKE CONCAT('USR-', today_date, '%');
        WHEN 'SEM' THEN
            SELECT COALESCE(MAX(CAST(SUBSTRING(seminar_id, 5, 4) AS UNSIGNED)), 999) + 1 INTO next_num
            FROM seminars WHERE seminar_id LIKE CONCAT('SEM-', today_date, '%');
        WHEN 'SES' THEN
            SELECT COALESCE(MAX(CAST(SUBSTRING(session_id, 5, 4) AS UNSIGNED)), 999) + 1 INTO next_num
            FROM sessions WHERE session_id LIKE CONCAT('SES-', today_date, '%');
        WHEN 'ATT' THEN
            SELECT COALESCE(MAX(CAST(SUBSTRING(attendance_id, 5, 4) AS UNSIGNED)), 999) + 1 INTO next_num
            FROM attendance WHERE attendance_id LIKE CONCAT('ATT-', today_date, '%');
        WHEN 'API' THEN
            SELECT COALESCE(MAX(CAST(SUBSTRING(api_key_id, 5, 4) AS UNSIGNED)), 999) + 1 INTO next_num
            FROM presenter_api_keys WHERE api_key_id LIKE CONCAT('API-', today_date, '%');
        WHEN 'PSM' THEN
            SELECT COALESCE(MAX(CAST(SUBSTRING(presenter_seminar_id, 5, 4) AS UNSIGNED)), 999) + 1 INTO next_num
            FROM presenter_seminar WHERE presenter_seminar_id LIKE CONCAT('PSM-', today_date, '%');
        WHEN 'PSS' THEN
            SELECT COALESCE(MAX(CAST(SUBSTRING(presenter_seminar_slot_id, 5, 4) AS UNSIGNED)), 999) + 1 INTO next_num
            FROM presenter_seminar_slot WHERE presenter_seminar_slot_id LIKE CONCAT('PSS-', today_date, '%');
    END CASE;
    
    -- Generate unique ID with collision detection
    WHILE id_exists = TRUE AND attempt_count < max_attempts DO
        SET result_id = CONCAT(table_prefix, '-', next_num, '-', today_date);
        SET attempt_count = attempt_count + 1;
        
        -- Check if ID already exists
        CASE table_prefix
            WHEN 'USR' THEN
                SELECT COUNT(*) = 0 INTO id_exists FROM users WHERE user_id = result_id;
            WHEN 'SEM' THEN
                SELECT COUNT(*) = 0 INTO id_exists FROM seminars WHERE seminar_id = result_id;
            WHEN 'SES' THEN
                SELECT COUNT(*) = 0 INTO id_exists FROM sessions WHERE session_id = result_id;
            WHEN 'ATT' THEN
                SELECT COUNT(*) = 0 INTO id_exists FROM attendance WHERE attendance_id = result_id;
            WHEN 'API' THEN
                SELECT COUNT(*) = 0 INTO id_exists FROM presenter_api_keys WHERE api_key_id = result_id;
            WHEN 'PSM' THEN
                SELECT COUNT(*) = 0 INTO id_exists FROM presenter_seminar WHERE presenter_seminar_id = result_id;
            WHEN 'PSS' THEN
                SELECT COUNT(*) = 0 INTO id_exists FROM presenter_seminar_slot WHERE presenter_seminar_slot_id = result_id;
        END CASE;
        
        IF id_exists = FALSE THEN
            SET next_num = next_num + 1;
        END IF;
    END WHILE;
    
    -- If we couldn't find a unique ID, add timestamp to ensure uniqueness
    IF attempt_count >= max_attempts THEN
        SET result_id = CONCAT(table_prefix, '-', next_num, '-', today_date, '-', UNIX_TIMESTAMP());
    END IF;
    
    RETURN result_id;
END$$
DELIMITER ;

-- =============================================
-- TRIGGERS FOR AUTOMATIC ID GENERATION
-- =============================================

-- Trigger for users table
DELIMITER $$
CREATE TRIGGER tr_users_before_insert
BEFORE INSERT ON users
FOR EACH ROW
BEGIN
    IF NEW.user_id IS NULL OR NEW.user_id = '' THEN
        SET NEW.user_id = generate_next_id('USR');
    END IF;
END$$
DELIMITER ;

-- Trigger for seminars table
DELIMITER $$
CREATE TRIGGER tr_seminars_before_insert
BEFORE INSERT ON seminars
FOR EACH ROW
BEGIN
    IF NEW.seminar_id IS NULL OR NEW.seminar_id = '' THEN
        SET NEW.seminar_id = generate_next_id('SEM');
    END IF;
END$$
DELIMITER ;

-- Trigger for sessions table
DELIMITER $$
CREATE TRIGGER tr_sessions_before_insert
BEFORE INSERT ON sessions
FOR EACH ROW
BEGIN
    IF NEW.session_id IS NULL OR NEW.session_id = '' THEN
        SET NEW.session_id = generate_next_id('SES');
    END IF;
END$$
DELIMITER ;

-- Trigger for attendance table
DELIMITER $$
CREATE TRIGGER tr_attendance_before_insert
BEFORE INSERT ON attendance
FOR EACH ROW
BEGIN
    IF NEW.attendance_id IS NULL OR NEW.attendance_id = '' THEN
        SET NEW.attendance_id = generate_next_id('ATT');
    END IF;
END$$
DELIMITER ;

-- Trigger for presenter_api_keys table
DELIMITER $$
CREATE TRIGGER tr_presenter_api_keys_before_insert
BEFORE INSERT ON presenter_api_keys
FOR EACH ROW
BEGIN
    IF NEW.api_key_id IS NULL OR NEW.api_key_id = '' THEN
        SET NEW.api_key_id = generate_next_id('API');
    END IF;
END$$
DELIMITER ;

-- Trigger for presenter_seminar table
DELIMITER $$
CREATE TRIGGER tr_presenter_seminar_before_insert
BEFORE INSERT ON presenter_seminar
FOR EACH ROW
BEGIN
    IF NEW.presenter_seminar_id IS NULL OR NEW.presenter_seminar_id = '' THEN
        SET NEW.presenter_seminar_id = generate_next_id('PSM');
    END IF;
END$$
DELIMITER ;

-- Trigger for presenter_seminar_slot table
DELIMITER $$
CREATE TRIGGER tr_presenter_seminar_slot_before_insert
BEFORE INSERT ON presenter_seminar_slot
FOR EACH ROW
BEGIN
    IF NEW.presenter_seminar_slot_id IS NULL OR NEW.presenter_seminar_slot_id = '' THEN
        SET NEW.presenter_seminar_slot_id = generate_next_id('PSS');
    END IF;
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
