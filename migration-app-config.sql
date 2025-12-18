
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


SET @db_name = DATABASE();

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

INSERT INTO app_config (config_key, config_value, config_type, category, target_system, description) VALUES
('server_url', 'http://132.72.50.53:8080', 'STRING', 'NETWORK', 'BOTH', 'Base server URL for API calls - MUST be same for mobile and API'),
('export_email_recipients', 'benariet@bgu.ac.il,talbnwork@gmail.com', 'STRING', 'EMAIL', 'BOTH', 'Comma-separated email recipients for export emails - MUST be same for mobile and API'),
('support_email', 'benariet@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'Support email for bug reports and feedback - MUST be same for mobile and API'),
('email_domain', '@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'Email domain suffix for username-based emails - MUST be same for mobile and API'),
('test_email_recipient', 'talbnwork@gmail.com', 'STRING', 'EMAIL', 'BOTH', 'Test email recipient address - MUST be same for mobile and API'),

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

('waiting_list_approval_window_hours', '24', 'INTEGER', 'WAITING_LIST', 'BOTH', 'Hours user has to approve waiting list slot - MUST be same for mobile and API'),
('email_from_name', 'SemScan System', 'STRING', 'EMAIL', 'BOTH', 'Email sender display name - MUST be same for mobile and API'),
('email_reply_to', 'noreply@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'Email reply-to address - MUST be same for mobile and API'),
('email_bcc_list', 'admin@bgu.ac.il', 'STRING', 'EMAIL', 'BOTH', 'BCC recipients for emails (comma-separated) - MUST be same for mobile and API')

ON DUPLICATE KEY UPDATE 
    config_value = VALUES(config_value),
    config_type = VALUES(config_type),
    category = VALUES(category),
    target_system = VALUES(target_system),
    description = VALUES(description),
    updated_at = CURRENT_TIMESTAMP;
