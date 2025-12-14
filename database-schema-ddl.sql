DROP DATABASE IF EXISTS semscan_db;
CREATE DATABASE semscan_db;
USE semscan_db;

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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_degree ON users(degree);
CREATE INDEX idx_users_bgu_username ON users(bgu_username);
CREATE INDEX idx_users_is_presenter ON users(is_presenter);
CREATE INDEX idx_users_is_participant ON users(is_participant);

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
