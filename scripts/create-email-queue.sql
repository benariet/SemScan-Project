CREATE TABLE IF NOT EXISTS email_queue (
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
    sent_at TIMESTAMP NULL,
    INDEX idx_status (status),
    INDEX idx_email_type (email_type),
    INDEX idx_scheduled_at (scheduled_at),
    INDEX idx_registration_id (registration_id),
    INDEX idx_to_email (to_email)
);

CREATE TABLE IF NOT EXISTS email_log (
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_to_email (to_email),
    INDEX idx_email_type (email_type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_registration_id (registration_id)
);

CREATE TABLE IF NOT EXISTS supervisor_reminder_tracking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    supervisor_email VARCHAR(255) NOT NULL,
    reminder_date DATE NOT NULL,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_reminder (registration_id, reminder_date),
    INDEX idx_registration_id (registration_id),
    INDEX idx_reminder_date (reminder_date)
);

SELECT 'email_queue' as table_name, COUNT(*) as row_count FROM email_queue
UNION ALL
SELECT 'email_log', COUNT(*) FROM email_log
UNION ALL
SELECT 'supervisor_reminder_tracking', COUNT(*) FROM supervisor_reminder_tracking;
