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
    description TEXT,
    tags VARCHAR(255) DEFAULT NULL,
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

CREATE TABLE fcm_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bgu_username VARCHAR(50) NOT NULL UNIQUE,
    fcm_token VARCHAR(255) NOT NULL,
    device_info VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_fcm_tokens_user
        FOREIGN KEY (bgu_username) REFERENCES users(bgu_username)
        ON DELETE CASCADE
);

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
    last_reminder_sent_at DATETIME,
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
    promotion_token VARCHAR(255) NULL,
    promotion_token_expires_at DATETIME NULL,
    promotion_offered_at DATETIME NULL,
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

INSERT INTO app_config (config_key, config_value, config_type, target_system, description, tags) VALUES
-- Registration & Capacity
('phd.capacity.weight', '3', 'INTEGER', 'BOTH', 'Capacity weight for PhD registrations. PhD students take more slot capacity than MSc. Example: If PhD weight is 3 and slot capacity is 3, one PhD fills entire slot. MSc weight is 1.', '#REGISTRATION #CAPACITY'),
('registration.max_approved', '1', 'INTEGER', 'API', 'Maximum APPROVED registrations any user can have at once. When one registration is approved, all other pending registrations are auto-cancelled. Typically 1 (one presentation per student).', '#REGISTRATION #LIMIT'),
('registration.max_pending.msc', '2', 'INTEGER', 'API', 'Maximum PENDING registrations an MSc student can have while waiting for supervisor approval. Allows registering for multiple slots as backups. When one is approved, others auto-cancel. Default: 2.', '#REGISTRATION #LIMIT'),
('registration.max_pending.phd', '1', 'INTEGER', 'API', 'Maximum PENDING registrations a PhD student can have while waiting for supervisor approval. Usually lower than MSc because PhD slots are scarcer (they take full capacity). Default: 1.', '#REGISTRATION #LIMIT'),

-- Waiting List
('waiting.list.limit.per.slot', '3', 'INTEGER', 'BOTH', 'Maximum users allowed on waiting list for a single slot. When limit reached, new users see "Waiting list is full". Prevents infinitely long queues. Default: 3.', '#WAITINGLIST'),
('waiting_list_approval_window_hours', '168', 'INTEGER', 'BOTH', 'Hours a user has to respond when promoted from waiting list. User receives push notification and email. If no response within this window, next person in queue is offered the slot. Default: 168 hours (7 days).', '#WAITINGLIST #EXPIRY'),
('promotion_offer_expiry_hours', '48', 'INTEGER', 'API', 'Hours a user has to accept waiting list promotion before it expires. When promoted, user has this many hours to confirm. After expiration, next person is offered the slot. Default: 48 hours.', '#WAITINGLIST #EXPIRY'),

-- Attendance Windows
('presenter_close_session_duration_minutes', '15', 'INTEGER', 'BOTH', 'How long (in minutes) an attendance session stays open after the presenter opens it. When this time expires, the session auto-closes and no more QR scans are accepted. Default: 15 minutes.', '#ATTENDANCE #TIMEOUT'),
('presenter_slot_open_window_before_minutes', '0', 'INTEGER', 'MOBILE', 'Minutes before the slot start time when presenter can begin opening attendance. Example: If set to 0, presenter can only open at slot start time or later. If set to 10, can open 10 minutes early.', '#ATTENDANCE'),
('presenter_slot_open_window_after_minutes', '15', 'INTEGER', 'MOBILE', 'Minutes after the slot end time when presenter can still open attendance. Example: If set to 15 and slot ends at 15:00, presenter can open until 15:15. After this, "Open Attendance" is disabled.', '#ATTENDANCE'),
('student_attendance_window_before_minutes', '0', 'INTEGER', 'MOBILE', 'Minutes before the session starts when students can begin scanning QR for attendance. Usually 0 (no early scanning allowed).', '#ATTENDANCE'),
('student_attendance_window_after_minutes', '10', 'INTEGER', 'MOBILE', 'Minutes after the session starts during which students can mark attendance via QR scan. Example: If set to 10 and session starts at 14:00, QR scanning works until 14:10.', '#ATTENDANCE'),
('manual_attendance_window_before_minutes', '10', 'INTEGER', 'MOBILE', 'Minutes before the session starts when students can begin requesting manual attendance. Example: If set to 10 and session starts at 14:00, manual requests accepted from 13:50.', '#ATTENDANCE'),
('manual_attendance_window_after_minutes', '15', 'INTEGER', 'MOBILE', 'Minutes after the session officially starts during which students can still request manual attendance. Example: If set to 15 and session starts at 14:00, manual requests accepted until 14:15.', '#ATTENDANCE'),

-- Email Settings
('approval_token_expiry_days', '14', 'INTEGER', 'API', 'Number of days until the supervisor approval link expires. After expiration, clicking the link shows "Token expired" and registration status changes to EXPIRED. Student must re-register. Default: 14 days.', '#APPROVAL #EXPIRY'),
('approval_reminder_interval_days', '2', 'INTEGER', 'API', 'Number of days between automatic reminder emails sent to supervisors who have not responded. Example: If set to 2, reminders sent every 2 days until supervisor responds or token expires.', '#EMAIL'),
('expiration_warning_hours_before', '48', 'INTEGER', 'API', 'Hours before supervisor approval token expires when warning email is sent to student. Example: If set to 48 and token expires in 14 days, warning sent on day 12.', '#EMAIL #EXPIRY'),
('email_from_name', 'SemScan System', 'STRING', 'BOTH', 'Display name shown as the sender in emails. Recipients see this name instead of the raw email address. Example: "SemScan System" appears as sender in inbox.', '#EMAIL'),
('email_reply_to', 'noreply@bgu.ac.il', 'STRING', 'BOTH', 'Reply-to address in system emails. When recipients click Reply, their email addresses this instead of the system sender.', '#EMAIL'),
('email_domain', '@bgu.ac.il', 'STRING', 'BOTH', 'Email domain suffix appended to usernames when constructing email addresses. Example: If set to "@bgu.ac.il" and username is "john", email becomes "john@bgu.ac.il".', '#EMAIL'),
('email_bcc_list', 'benariet@bgu.ac.il', 'STRING', 'BOTH', 'Comma-separated list of email addresses to BCC on all system emails. Useful for admin monitoring. Example: "admin@bgu.ac.il,backup@bgu.ac.il"', '#EMAIL'),
('export_email_recipients', 'benariet@bgu.ac.il,talbnwork@gmail.com', 'STRING', 'BOTH', 'Comma-separated list of email addresses that receive attendance export files when presenter exports data.', '#EMAIL'),
('support_email', 'benariet@bgu.ac.il', 'STRING', 'BOTH', 'Email address displayed to users for support requests, bug reports, and feedback. Shown in error messages and help screens.', '#EMAIL'),
('test_email_recipient', 'talbnwork@gmail.com', 'STRING', 'BOTH', 'Email address used for testing email functionality. Test emails sent here to verify SMTP works without affecting real users.', '#EMAIL #DEBUG'),
('email_queue_max_retries', '3', 'INTEGER', 'API', 'Maximum retry attempts for a failed email before marking as permanently FAILED. After this many attempts, email stops retrying. Default: 3 retries.', '#EMAIL'),
('email_queue_initial_backoff_minutes', '5', 'INTEGER', 'API', 'Initial wait time (in minutes) before first retry after email fails. Subsequent retries use exponential backoff (this value × multiplier). Default: 5 minutes.', '#EMAIL'),
('email_queue_backoff_multiplier', '3', 'INTEGER', 'API', 'Multiplier for exponential backoff when retrying failed emails. Example: If initial backoff is 5 min and multiplier is 3, retries at 5min, 15min, 45min.', '#EMAIL'),
('email_queue_batch_size', '50', 'INTEGER', 'API', 'Maximum number of pending emails processed in each batch. Limits database load and prevents timeout. Example: If set to 50, only 50 emails sent per cycle.', '#EMAIL'),
('email_queue_process_interval_seconds', '120', 'INTEGER', 'API', 'How often (in seconds) the email queue processor runs to check for pending emails. Lower = faster delivery but more DB queries. Default: 120 seconds (2 min).', '#EMAIL'),

-- Network & Timeouts
('server_url', 'http://132.72.50.53:8080', 'STRING', 'BOTH', 'Base URL for all API calls from mobile app. Must include protocol and port. Example: "http://132.72.50.53:8080". Change when migrating to new server or HTTPS.', '#NETWORK'),
('connection_timeout_seconds', '30', 'INTEGER', 'MOBILE', 'HTTP connection timeout in seconds for API calls. If server does not respond within this time, request fails with timeout error. Increase for slow networks.', '#NETWORK #TIMEOUT'),
('read_timeout_seconds', '30', 'INTEGER', 'MOBILE', 'HTTP read timeout in seconds for API calls. Maximum time to wait for response data after connection is established.', '#NETWORK #TIMEOUT'),
('write_timeout_seconds', '30', 'INTEGER', 'MOBILE', 'HTTP write timeout in seconds for API calls. Maximum time allowed to send request data to server.', '#NETWORK #TIMEOUT'),

-- Mobile App
('APP_VERSION', '1.0.0', 'STRING', 'MOBILE', 'Current version of the mobile app. Used for version checking and displaying in app settings. Update this when releasing a new APK.', '#VERSION'),
('config_cache_ttl_hours', '24', 'INTEGER', 'MOBILE', 'How long (in hours) mobile app caches configuration before fetching fresh values. Lower = more frequent updates but more network requests. Default: 24 hours.', '#CACHE'),
('max_export_file_size_mb', '50', 'INTEGER', 'MOBILE', 'Maximum allowed export file size in megabytes. Prevents extremely large exports that could cause memory issues or slow downloads.', '#EXPORT'),
('toast_duration_error', '10000', 'INTEGER', 'MOBILE', 'Duration in milliseconds to display error toast messages. Error toasts show longer to ensure users notice problems. Default: 10000ms (10 seconds).', '#UI'),
('toast_duration_success', '5000', 'INTEGER', 'MOBILE', 'Duration in milliseconds to display success toast messages. Can be shorter since they confirm expected behavior. Default: 5000ms (5 seconds).', '#UI'),
('toast_duration_info', '6000', 'INTEGER', 'MOBILE', 'Duration in milliseconds to display informational toast messages. Default: 6000ms (6 seconds).', '#UI');

-- =====================================================================
--  VERIFICATION
-- =====================================================================

SELECT 'Schema deployed successfully.' AS status;
