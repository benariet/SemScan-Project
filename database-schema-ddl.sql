DROP DATABASE IF EXISTS semscan_db;
CREATE DATABASE semscan_db;
USE semscan_db;

-- =====================================================================
--  SUPERVISORS
-- =====================================================================
CREATE TABLE supervisors (
    supervisor_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_supervisors_email ON supervisors(email);

-- =====================================================================
--  USERS
-- =====================================================================
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    bgu_username VARCHAR(50) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    degree ENUM('MSc','PhD') NULL,
    is_presenter BOOLEAN DEFAULT FALSE,
    is_participant BOOLEAN DEFAULT FALSE,
    national_id_number VARCHAR(50),
    supervisor_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_supervisor
        FOREIGN KEY (supervisor_id) REFERENCES supervisors(supervisor_id)
        ON DELETE SET NULL
);

CREATE INDEX idx_users_degree ON users(degree);
CREATE INDEX idx_users_bgu_username ON users(bgu_username);
CREATE INDEX idx_users_is_presenter ON users(is_presenter);
CREATE INDEX idx_users_is_participant ON users(is_participant);
CREATE INDEX idx_users_supervisor_id ON users(supervisor_id);

-- =====================================================================
--  SEMINARS
-- =====================================================================
CREATE TABLE seminars (
    seminar_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seminar_name VARCHAR(255) NOT NULL,
    description TEXT,
    presenter_username VARCHAR(50) NOT NULL,
    max_enrollment_capacity INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_seminars_presenter
        FOREIGN KEY (presenter_username) REFERENCES users(bgu_username)
        ON DELETE CASCADE
);

CREATE INDEX idx_seminars_presenter_username ON seminars(presenter_username);
CREATE INDEX idx_seminars_name ON seminars(seminar_name);

-- =====================================================================
--  SEMINAR PARTICIPANTS (Per-Seminar Roles)
-- =====================================================================
CREATE TABLE seminar_participants (
    participant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seminar_id BIGINT NOT NULL,
    participant_username VARCHAR(50) NOT NULL,
    role ENUM('PARTICIPANT','PRESENTER') NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seminar_participants_seminar
        FOREIGN KEY (seminar_id) REFERENCES seminars(seminar_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_seminar_participants_user
        FOREIGN KEY (participant_username) REFERENCES users(bgu_username)
        ON DELETE CASCADE,
    UNIQUE KEY unique_seminar_user (seminar_id, participant_username)
);

CREATE INDEX idx_seminar_participants_seminar ON seminar_participants(seminar_id);
CREATE INDEX idx_seminar_participants_user ON seminar_participants(participant_username);
CREATE INDEX idx_seminar_participants_role ON seminar_participants(role);

-- =====================================================================
--  SESSIONS
-- =====================================================================
CREATE TABLE sessions (
    session_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seminar_id BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME,
    status ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
    location VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sessions_seminar
        FOREIGN KEY (seminar_id) REFERENCES seminars(seminar_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_sessions_seminar ON sessions(seminar_id);
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_sessions_time ON sessions(start_time);

-- =====================================================================
--  SLOTS
-- =====================================================================
CREATE TABLE slots (
    slot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    semester_label VARCHAR(50),
    slot_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    building VARCHAR(50),
    room VARCHAR(50),
    capacity INT NOT NULL,
    status ENUM('FREE','SEMI','FULL') NOT NULL DEFAULT 'FREE',
    attendance_opened_at DATETIME,
    attendance_closes_at DATETIME,
    attendance_opened_by VARCHAR(50),
    legacy_seminar_id BIGINT,
    legacy_session_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_slots_date ON slots(slot_date);
CREATE INDEX idx_slots_status ON slots(status);

-- =====================================================================
--  SLOT REGISTRATION
-- =====================================================================
CREATE TABLE slot_registration (
    slot_id BIGINT NOT NULL,
    presenter_username VARCHAR(50) NOT NULL,
    degree ENUM('MSc','PhD') NOT NULL,
    topic VARCHAR(255),
    supervisor_name VARCHAR(255),
    supervisor_email VARCHAR(255),
    registered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approval_status ENUM('PENDING','APPROVED','DECLINED','EXPIRED') NOT NULL DEFAULT 'PENDING',
    approval_token VARCHAR(255) UNIQUE,
    approval_token_expires_at DATETIME,
    supervisor_approved_at DATETIME,
    supervisor_declined_at DATETIME,
    supervisor_declined_reason TEXT,
    PRIMARY KEY (slot_id, presenter_username),
    CONSTRAINT fk_slot_registration_slot
        FOREIGN KEY (slot_id) REFERENCES slots(slot_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_slot_registration_presenter
        FOREIGN KEY (presenter_username) REFERENCES users(bgu_username)
        ON DELETE CASCADE
);

CREATE INDEX idx_slot_registration_presenter ON slot_registration(presenter_username);
CREATE INDEX idx_slot_registration_approval_status ON slot_registration(approval_status);
CREATE INDEX idx_slot_registration_approval_token ON slot_registration(approval_token);

-- =====================================================================
--  WAITING LIST
-- =====================================================================
CREATE TABLE waiting_list (
    waiting_list_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_id BIGINT NOT NULL,
    presenter_username VARCHAR(50) NOT NULL,
    degree ENUM('MSc','PhD') NOT NULL,
    topic VARCHAR(255),
    supervisor_name VARCHAR(255),
    supervisor_email VARCHAR(255),
    position INT NOT NULL,
    added_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_waiting_list_slot
        FOREIGN KEY (slot_id) REFERENCES slots(slot_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_waiting_list_presenter
        FOREIGN KEY (presenter_username) REFERENCES users(bgu_username)
        ON DELETE CASCADE,
    UNIQUE KEY unique_slot_presenter_waiting (slot_id, presenter_username)
);

CREATE INDEX idx_waiting_list_slot ON waiting_list(slot_id);
CREATE INDEX idx_waiting_list_presenter ON waiting_list(presenter_username);
CREATE INDEX idx_waiting_list_position ON waiting_list(slot_id, position);

-- =====================================================================
--  WAITING LIST PROMOTIONS
-- =====================================================================
CREATE TABLE waiting_list_promotions (
    promotion_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_id BIGINT NOT NULL,
    presenter_username VARCHAR(50) NOT NULL,
    registration_slot_id BIGINT NOT NULL,
    registration_presenter_username VARCHAR(50) NOT NULL,
    promoted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'DECLINED', 'EXPIRED') NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_promotions_slot FOREIGN KEY (slot_id) REFERENCES slots(slot_id) ON DELETE CASCADE,
    CONSTRAINT fk_promotions_user FOREIGN KEY (presenter_username) REFERENCES users(bgu_username) ON DELETE CASCADE,
    CONSTRAINT fk_promotions_registration FOREIGN KEY (registration_slot_id, registration_presenter_username) 
        REFERENCES slot_registration(slot_id, presenter_username) ON DELETE CASCADE
);

CREATE INDEX idx_promotions_expires_at ON waiting_list_promotions(expires_at);
CREATE INDEX idx_promotions_status ON waiting_list_promotions(status);

-- =====================================================================
--  APP CONFIG
-- =====================================================================
CREATE TABLE app_config (
    config_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    config_type ENUM('STRING', 'INTEGER', 'BOOLEAN', 'JSON') NOT NULL,
    target_system ENUM('MOBILE', 'API', 'BOTH') NOT NULL,
    category VARCHAR(50),
    description TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP
);

-- Create indexes (MySQL-compatible: check if exists before creating)
-- MySQL doesn't support CREATE INDEX IF NOT EXISTS, so we check first
SET @db_name = DATABASE();

-- Check and create idx_app_config_target_system
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics 
WHERE table_schema = @db_name 
AND table_name = 'app_config' 
AND index_name = 'idx_app_config_target_system';

SET @sql = IF(@idx_exists = 0, 
    'CREATE INDEX idx_app_config_target_system ON app_config(target_system)',
    'SELECT ''Index idx_app_config_target_system already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check and create idx_app_config_category
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics 
WHERE table_schema = @db_name 
AND table_name = 'app_config' 
AND index_name = 'idx_app_config_category';

SET @sql = IF(@idx_exists = 0, 
    'CREATE INDEX idx_app_config_category ON app_config(category)',
    'SELECT ''Index idx_app_config_category already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check and create idx_app_config_key
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics 
WHERE table_schema = @db_name 
AND table_name = 'app_config' 
AND index_name = 'idx_app_config_key';

SET @sql = IF(@idx_exists = 0, 
    'CREATE INDEX idx_app_config_key ON app_config(config_key)',
    'SELECT ''Index idx_app_config_key already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Insert default configuration values
-- Note: Uses INSERT IGNORE to allow re-running schema without errors
INSERT IGNORE INTO app_config (config_key, config_value, config_type, category, target_system, description) VALUES
-- BOTH: Must be identical for mobile and API (emails, domains, URLs)
('server_url', 'http://132.72.50.53:8080', 'STRING', 'NETWORK', 'BOTH', 'Base server URL for API calls - MUST be same for mobile and API'),
('export_email_recipients', 'benariet@bgu.ac.il,talbnwork@gmail.com', 'STRING', 'EMAIL', 'BOTH', 'Comma-separated email recipients for export emails - MUST be same for mobile and API'),
('support_email', 'benariet@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'Support email for bug reports and feedback - MUST be same for mobile and API'),
('email_domain', '@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'Email domain suffix for username-based emails - MUST be same for mobile and API'),
('test_email_recipient', 'talbnwork@gmail.com', 'STRING', 'EMAIL', 'BOTH', 'Test email recipient address - MUST be same for mobile and API'),

-- MOBILE: Only for mobile app
('connection_timeout_seconds', '10', 'INTEGER', 'NETWORK', 'MOBILE', 'HTTP connection timeout in seconds - mobile app only'),
('read_timeout_seconds', '10', 'INTEGER', 'NETWORK', 'MOBILE', 'HTTP read timeout in seconds - mobile app only'),
('write_timeout_seconds', '10', 'INTEGER', 'NETWORK', 'MOBILE', 'HTTP write timeout in seconds - mobile app only'),
('manual_attendance_window_before_minutes', '10', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes before session start for manual attendance - mobile app only'),
('manual_attendance_window_after_minutes', '15', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes after session start for manual attendance - mobile app only'),
('max_export_file_size_mb', '50', 'INTEGER', 'EXPORT', 'MOBILE', 'Maximum export file size in MB - mobile app only'),
('toast_duration_error', '10000', 'INTEGER', 'UI', 'MOBILE', 'Toast duration for errors in milliseconds - mobile app only'),
('toast_duration_success', '5000', 'INTEGER', 'UI', 'MOBILE', 'Toast duration for success messages in milliseconds - mobile app only'),
('toast_duration_info', '6000', 'INTEGER', 'UI', 'MOBILE', 'Toast duration for info messages in milliseconds - mobile app only'),
('config_cache_ttl_hours', '24', 'INTEGER', 'SYSTEM', 'MOBILE', 'Configuration cache TTL in hours - mobile app only'),
('presenter_slot_open_window_before_minutes', '30', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes before slot start when presenter can open - mobile app only'),
('presenter_slot_open_window_after_minutes', '15', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes after slot start when presenter can still open - mobile app only'),
('student_attendance_window_before_minutes', '5', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes before session start for student attendance - mobile app only'),
('student_attendance_window_after_minutes', '10', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes after session start for student attendance - mobile app only'),

-- BOTH: Must be identical for mobile and API
('waiting_list_approval_window_hours', '24', 'INTEGER', 'WAITING_LIST', 'BOTH', 'Hours user has to approve waiting list slot - MUST be same for mobile and API'),
('email_from_name', 'SemScan System', 'STRING', 'EMAIL', 'BOTH', 'Email sender display name - MUST be same for mobile and API'),
('email_reply_to', 'noreply@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'Email reply-to address - MUST be same for mobile and API'),
('email_bcc_list', 'admin@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'BCC recipients for emails (comma-separated) - MUST be same for mobile and API');

-- =====================================================================
--  ATTENDANCE
-- =====================================================================
CREATE TABLE attendance (
    attendance_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    student_username VARCHAR(50) NOT NULL,
    attendance_time DATETIME NOT NULL,
    method ENUM('QR_SCAN','MANUAL','MANUAL_REQUEST','PROXY') NOT NULL,
    request_status ENUM('PENDING_APPROVAL','CONFIRMED','REJECTED'),
    manual_reason VARCHAR(255),
    requested_at DATETIME,
    approved_by_username VARCHAR(50),
    approved_at DATETIME,
    device_id VARCHAR(100),
    auto_flags TEXT,
    notes TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_session
        FOREIGN KEY (session_id) REFERENCES sessions(session_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_attendance_student
        FOREIGN KEY (student_username) REFERENCES users(bgu_username)
        ON DELETE CASCADE,
    CONSTRAINT fk_attendance_approver
        FOREIGN KEY (approved_by_username) REFERENCES users(bgu_username)
        ON DELETE SET NULL,
    CONSTRAINT uq_attendance_session_student UNIQUE (session_id, student_username)
);

CREATE INDEX idx_attendance_session ON attendance(session_id);
CREATE INDEX idx_attendance_student ON attendance(student_username);
CREATE INDEX idx_attendance_status ON attendance(request_status);

-- =====================================================================
--  APP LOGS
-- =====================================================================
CREATE TABLE app_logs (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_timestamp DATETIME NOT NULL,
    level VARCHAR(20) NOT NULL,
    tag VARCHAR(100),
    message TEXT NOT NULL,
    source ENUM('API','MOBILE') NOT NULL DEFAULT 'API',
    correlation_id VARCHAR(50),
    bgu_username VARCHAR(50),
    user_role ENUM('PARTICIPANT','PRESENTER','BOTH','UNKNOWN') DEFAULT 'UNKNOWN',
    device_info VARCHAR(255),
    app_version VARCHAR(50),
    stack_trace TEXT,
    exception_type VARCHAR(100),
    payload TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_logs_user
        FOREIGN KEY (bgu_username) REFERENCES users(bgu_username)
        ON DELETE SET NULL
);

CREATE INDEX idx_logs_timestamp ON app_logs(log_timestamp);
CREATE INDEX idx_logs_level ON app_logs(level);
CREATE INDEX idx_logs_source ON app_logs(source);
CREATE INDEX idx_logs_correlation_id ON app_logs(correlation_id);
CREATE INDEX idx_logs_user ON app_logs(bgu_username);

SELECT 'Schema deployed with username-based relationships.' AS status;
