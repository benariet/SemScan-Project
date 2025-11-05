DROP DATABASE IF EXISTS semscan_db;
CREATE DATABASE semscan_db;
USE semscan_db;

-- =====================================================================
--  USERS
-- =====================================================================
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    bgu_username VARCHAR(50) UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    degree ENUM('MSc','PhD') NULL,
    is_presenter BOOLEAN DEFAULT FALSE,
    is_participant BOOLEAN DEFAULT FALSE,
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
    presenter_id BIGINT NOT NULL,
    max_enrollment_capacity INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_seminars_presenter
        FOREIGN KEY (presenter_id) REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_seminars_presenter ON seminars(presenter_id);
CREATE INDEX idx_seminars_name ON seminars(seminar_name);

-- =====================================================================
--  SEMINAR PARTICIPANTS (Per-Seminar Roles)
-- =====================================================================
CREATE TABLE seminar_participants (
    participant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seminar_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('PARTICIPANT','PRESENTER') NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seminar_participants_seminar
        FOREIGN KEY (seminar_id) REFERENCES seminars(seminar_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_seminar_participants_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,
    UNIQUE KEY unique_seminar_user (seminar_id, user_id)
);

CREATE INDEX idx_seminar_participants_seminar ON seminar_participants(seminar_id);
CREATE INDEX idx_seminar_participants_user ON seminar_participants(user_id);
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
--  ATTENDANCE
-- =====================================================================
CREATE TABLE attendance (
    attendance_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    attendance_time DATETIME NOT NULL,
    method ENUM('QR_SCAN','MANUAL','MANUAL_REQUEST','PROXY') NOT NULL,
    request_status ENUM('PENDING_APPROVAL','CONFIRMED','REJECTED'),
    manual_reason VARCHAR(255),
    requested_at DATETIME,
    approved_by BIGINT,
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
        FOREIGN KEY (student_id) REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_attendance_approver
        FOREIGN KEY (approved_by) REFERENCES users(id)
        ON DELETE SET NULL,
    CONSTRAINT uq_attendance_session_student UNIQUE (session_id, student_id)
);

CREATE INDEX idx_attendance_session ON attendance(session_id);
CREATE INDEX idx_attendance_student ON attendance(student_id);
CREATE INDEX idx_attendance_status ON attendance(request_status);

-- =====================================================================
--  PRESENTER SEMINAR MAPPINGS
-- =====================================================================
CREATE TABLE presenter_seminar (
    presenter_seminar_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    presenter_id BIGINT NOT NULL,
    seminar_id BIGINT NOT NULL,
    instance_name VARCHAR(255) NOT NULL,
    instance_description TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_presenter_seminar_presenter
        FOREIGN KEY (presenter_id) REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_presenter_seminar_seminar
        FOREIGN KEY (seminar_id) REFERENCES seminars(seminar_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_presenter_seminar_presenter ON presenter_seminar(presenter_id);
CREATE INDEX idx_presenter_seminar_instance ON presenter_seminar(instance_name);

CREATE TABLE presenter_seminar_slot (
    presenter_seminar_slot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    presenter_seminar_id BIGINT NOT NULL,
    weekday TINYINT NOT NULL,
    start_hour TINYINT NOT NULL,
    end_hour TINYINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_presenter_slot_parent
        FOREIGN KEY (presenter_seminar_id) REFERENCES presenter_seminar(presenter_seminar_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_presenter_slot_weekday CHECK (weekday BETWEEN 0 AND 6),
    CONSTRAINT chk_presenter_slot_hours CHECK (start_hour < end_hour)
);

CREATE INDEX idx_presenter_slot_weekday ON presenter_seminar_slot(weekday);

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
    user_id BIGINT,
    user_role ENUM('PARTICIPANT','PRESENTER','BOTH','UNKNOWN') DEFAULT 'UNKNOWN',
    device_info VARCHAR(255),
    app_version VARCHAR(50),
    stack_trace TEXT,
    exception_type VARCHAR(100),
    payload TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_logs_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_logs_timestamp ON app_logs(log_timestamp);
CREATE INDEX idx_logs_level ON app_logs(level);
CREATE INDEX idx_logs_source ON app_logs(source);
CREATE INDEX idx_logs_correlation_id ON app_logs(correlation_id);
CREATE INDEX idx_logs_user ON app_logs(user_id);

SELECT 'Schema deployed with descriptive numeric identifiers.' AS status;
