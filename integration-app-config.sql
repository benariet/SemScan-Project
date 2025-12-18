-- =====================================================================
-- Integration Script: Add app_config and waiting_list_promotions tables
-- =====================================================================
-- This script adds the new tables and default config values to an existing database
-- Safe to run multiple times (idempotent)
-- =====================================================================

-- =====================================================================
--  WAITING LIST PROMOTIONS TABLE
-- =====================================================================
CREATE TABLE IF NOT EXISTS waiting_list_promotions (
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

-- Create indexes
-- Note: MySQL doesn't support CREATE INDEX IF NOT EXISTS
-- If you get "Duplicate key name" errors, the indexes already exist - you can safely ignore those errors
-- Or use integration-app-config-safe.sql which checks for existing indexes first
CREATE INDEX idx_promotions_expires_at ON waiting_list_promotions(expires_at);
CREATE INDEX idx_promotions_status ON waiting_list_promotions(status);

-- =====================================================================
--  APP CONFIG TABLE
-- =====================================================================
CREATE TABLE IF NOT EXISTS app_config (
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

-- Create indexes
-- Note: MySQL doesn't support CREATE INDEX IF NOT EXISTS
-- If you get "Duplicate key name" errors, the indexes already exist - you can safely ignore those errors
-- Or use integration-app-config-safe.sql which checks for existing indexes first
CREATE INDEX idx_app_config_target_system ON app_config(target_system);
CREATE INDEX idx_app_config_category ON app_config(category);
CREATE INDEX idx_app_config_key ON app_config(config_key);

-- =====================================================================
--  INSERT DEFAULT CONFIGURATION VALUES
-- =====================================================================
-- Uses INSERT IGNORE to prevent errors if values already exist
-- To update existing values, use: UPDATE app_config SET config_value = '...' WHERE config_key = '...';
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
('presenter_close_session_duration_minutes', '15', 'INTEGER', 'ATTENDANCE', 'BOTH', 'Minutes from session open until automatic close/end - MUST be same for mobile and API'),
('student_attendance_window_before_minutes', '5', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes before session start for student attendance - mobile app only'),
('student_attendance_window_after_minutes', '10', 'INTEGER', 'ATTENDANCE', 'MOBILE', 'Minutes after session start for student attendance - mobile app only'),

-- BOTH: Must be identical for mobile and API
('waiting_list_approval_window_hours', '24', 'INTEGER', 'WAITING_LIST', 'BOTH', 'Hours user has to approve waiting list slot - MUST be same for mobile and API'),
('email_from_name', 'SemScan System', 'STRING', 'EMAIL', 'BOTH', 'Email sender display name - MUST be same for mobile and API'),
('email_reply_to', 'noreply@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'Email reply-to address - MUST be same for mobile and API'),
('email_bcc_list', 'admin@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'BCC recipients for emails (comma-separated) - MUST be same for mobile and API');

-- =====================================================================
--  VERIFICATION QUERIES (Optional - uncomment to verify)
-- =====================================================================
-- SELECT 'Integration completed successfully!' AS status;
-- SELECT COUNT(*) AS app_config_count FROM app_config;
-- SELECT COUNT(*) AS waiting_list_promotions_count FROM waiting_list_promotions;
-- SELECT config_key, config_value, target_system FROM app_config ORDER BY target_system, config_key;
