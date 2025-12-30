DROP DATABASE IF EXISTS semscan_db;
CREATE DATABASE semscan_db;
USE semscan_db;

-- =====================================================================
--  BASE TABLES (no foreign key dependencies)
-- =====================================================================

CREATE TABLE supervisors (
    supervisor_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP
);

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

CREATE TABLE email_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    to_email VARCHAR(255) NOT NULL,
    cc_email VARCHAR(255) NULL,
    bcc_email VARCHAR(255) NULL,
    subject VARCHAR(500) NOT NULL,
    html_content TEXT NOT NULL,
    email_type VARCHAR(50) NOT NULL,
    registration_id BIGINT NULL,
    slot_id BIGINT NULL,
    username VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    last_error TEXT NULL,
    last_error_code VARCHAR(50) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    scheduled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMP NULL,
    sent_at TIMESTAMP NULL
);

CREATE TABLE email_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    to_email VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    email_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT NULL,
    error_code VARCHAR(50) NULL,
    registration_id BIGINT NULL,
    slot_id BIGINT NULL,
    username VARCHAR(100) NULL,
    queue_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE supervisor_reminder_tracking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_id BIGINT NOT NULL,
    presenter_username VARCHAR(100) NOT NULL,
    supervisor_email VARCHAR(255) NOT NULL,
    reminder_date DATE NOT NULL,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_reminder (slot_id, presenter_username, reminder_date)
);

-- =====================================================================
--  LEVEL 1: Tables depending on base tables
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
    seminar_abstract TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_supervisor
        FOREIGN KEY (supervisor_id) REFERENCES supervisors(supervisor_id)
        ON DELETE SET NULL
);

-- =====================================================================
--  LEVEL 2: Tables depending on users and/or slots
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

CREATE TABLE slot_registration (
    slot_id BIGINT NOT NULL,
    presenter_username VARCHAR(50) NOT NULL,
    degree ENUM('MSc','PhD') NOT NULL,
    topic VARCHAR(255),
    seminar_abstract TEXT,
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

-- =====================================================================
--  LEVEL 3: Tables depending on seminars
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

-- =====================================================================
--  LEVEL 4: Tables depending on sessions
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

-- =====================================================================
--  INDEXES
-- =====================================================================

CREATE INDEX idx_supervisors_email ON supervisors(email);

CREATE INDEX idx_slots_date ON slots(slot_date);
CREATE INDEX idx_slots_status ON slots(status);

CREATE INDEX idx_app_config_target_system ON app_config(target_system);
CREATE INDEX idx_app_config_category ON app_config(category);
CREATE INDEX idx_app_config_key ON app_config(config_key);

CREATE INDEX idx_email_queue_status ON email_queue(status);
CREATE INDEX idx_email_queue_email_type ON email_queue(email_type);
CREATE INDEX idx_email_queue_scheduled_at ON email_queue(scheduled_at);
CREATE INDEX idx_email_queue_registration_id ON email_queue(registration_id);
CREATE INDEX idx_email_queue_to_email ON email_queue(to_email);

CREATE INDEX idx_email_log_to_email ON email_log(to_email);
CREATE INDEX idx_email_log_email_type ON email_log(email_type);
CREATE INDEX idx_email_log_status ON email_log(status);
CREATE INDEX idx_email_log_created_at ON email_log(created_at);
CREATE INDEX idx_email_log_registration_id ON email_log(registration_id);

CREATE INDEX idx_reminder_tracking_slot_presenter ON supervisor_reminder_tracking(slot_id, presenter_username);
CREATE INDEX idx_reminder_tracking_reminder_date ON supervisor_reminder_tracking(reminder_date);

CREATE INDEX idx_users_degree ON users(degree);
CREATE INDEX idx_users_bgu_username ON users(bgu_username);
CREATE INDEX idx_users_is_presenter ON users(is_presenter);
CREATE INDEX idx_users_is_participant ON users(is_participant);
CREATE INDEX idx_users_supervisor_id ON users(supervisor_id);

CREATE INDEX idx_seminars_presenter_username ON seminars(presenter_username);
CREATE INDEX idx_seminars_name ON seminars(seminar_name);

CREATE INDEX idx_slot_registration_presenter ON slot_registration(presenter_username);
CREATE INDEX idx_slot_registration_approval_status ON slot_registration(approval_status);
CREATE INDEX idx_slot_registration_approval_token ON slot_registration(approval_token);

CREATE INDEX idx_waiting_list_slot ON waiting_list(slot_id);
CREATE INDEX idx_waiting_list_presenter ON waiting_list(presenter_username);
CREATE INDEX idx_waiting_list_position ON waiting_list(slot_id, position);

CREATE INDEX idx_logs_timestamp ON app_logs(log_timestamp);
CREATE INDEX idx_logs_level ON app_logs(level);
CREATE INDEX idx_logs_source ON app_logs(source);
CREATE INDEX idx_logs_correlation_id ON app_logs(correlation_id);
CREATE INDEX idx_logs_user ON app_logs(bgu_username);

CREATE INDEX idx_sessions_seminar ON sessions(seminar_id);
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_sessions_time ON sessions(start_time);

CREATE INDEX idx_seminar_participants_seminar ON seminar_participants(seminar_id);
CREATE INDEX idx_seminar_participants_user ON seminar_participants(participant_username);
CREATE INDEX idx_seminar_participants_role ON seminar_participants(role);

CREATE INDEX idx_promotions_expires_at ON waiting_list_promotions(expires_at);
CREATE INDEX idx_promotions_status ON waiting_list_promotions(status);

CREATE INDEX idx_attendance_session ON attendance(session_id);
CREATE INDEX idx_attendance_student ON attendance(student_username);
CREATE INDEX idx_attendance_status ON attendance(request_status);

-- =====================================================================
--  INITIAL DATA
-- =====================================================================

INSERT INTO app_config (config_key, config_value, config_type, category, target_system, description) VALUES
('server_url', 'http://132.72.50.53:8080', 'STRING', 'NETWORK', 'BOTH', 'Base server URL for API calls'),
('export_email_recipients', 'benariet@bgu.ac.il,talbnwork@gmail.com', 'STRING', 'EMAIL', 'BOTH', 'Comma-separated email recipients for export emails'),
('support_email', 'benariet@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'Support email for bug reports and feedback'),
('email_domain', '@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'Email domain suffix for username-based emails'),
('test_email_recipient', 'talbnwork@gmail.com', 'STRING', 'EMAIL', 'BOTH', 'Test email recipient address'),
('connection_timeout_seconds', '30', 'INTEGER', 'NETWORK', 'MOBILE', 'HTTP connection timeout in seconds'),
('read_timeout_seconds', '30', 'INTEGER', 'NETWORK', 'MOBILE', 'HTTP read timeout in seconds'),
('write_timeout_seconds', '30', 'INTEGER', 'NETWORK', 'MOBILE', 'HTTP write timeout in seconds'),
('manual_attendance_window_before_minutes', '10', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes before session start for manual attendance'),
('manual_attendance_window_after_minutes', '15', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes after session start for manual attendance'),
('max_export_file_size_mb', '50', 'INTEGER', 'EXPORT', 'MOBILE', 'Maximum export file size in MB'),
('toast_duration_error', '10000', 'INTEGER', 'UI', 'MOBILE', 'Toast duration for errors in milliseconds'),
('toast_duration_success', '5000', 'INTEGER', 'UI', 'MOBILE', 'Toast duration for success messages in milliseconds'),
('toast_duration_info', '6000', 'INTEGER', 'UI', 'MOBILE', 'Toast duration for info messages in milliseconds'),
('config_cache_ttl_hours', '24', 'INTEGER', 'SYSTEM', 'MOBILE', 'Configuration cache TTL in hours'),
('presenter_slot_open_window_before_minutes', '30', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes before slot start when presenter can open'),
('presenter_slot_open_window_after_minutes', '15', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes after slot start when presenter can still open'),
('student_attendance_window_before_minutes', '5', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes before session start for student attendance'),
('student_attendance_window_after_minutes', '10', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes after session start for student attendance'),
('waiting_list_approval_window_hours', '168', 'INTEGER', 'WAITING_LIST', 'BOTH', 'Hours user has to approve waiting list slot'),
('email_from_name', 'SemScan System', 'STRING', 'EMAIL', 'BOTH', 'Email sender display name'),
('email_reply_to', 'noreply@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'Email reply-to address'),
('email_bcc_list', 'admin@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'BCC recipients for emails (comma-separated)'),
('waiting.list.limit.per.slot', '3', 'INTEGER', 'REGISTRATION', 'BOTH', 'Maximum number of users on waiting list per slot'),
('phd.capacity.weight', '2', 'INTEGER', 'REGISTRATION', 'BOTH', 'How many capacity slots a PhD registration counts as'),
('APP_VERSION', '1.0.0', 'STRING', 'APP', 'MOBILE', 'Current mobile app version number'),
('email_queue_max_retries', '3', 'INTEGER', 'EMAIL', 'API', 'Maximum retry attempts for failed emails'),
('email_queue_initial_backoff_minutes', '5', 'INTEGER', 'EMAIL', 'API', 'Initial backoff time in minutes before first retry'),
('email_queue_backoff_multiplier', '3', 'INTEGER', 'EMAIL', 'API', 'Multiplier for exponential backoff'),
('email_queue_batch_size', '50', 'INTEGER', 'EMAIL', 'API', 'Maximum emails to process per batch');

-- =====================================================================
--  VERIFICATION
-- =====================================================================

SELECT 'Schema deployed successfully.' AS status;
