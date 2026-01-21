-- =====================================================================
-- ALTER COMMANDS FOR SUPERVISOR APPROVAL SYSTEM
-- =====================================================================
-- Run these commands on your existing database to add the new features
-- =====================================================================

USE semscan_db;

-- =====================================================================
-- ALTER SLOT_REGISTRATION TABLE
-- =====================================================================
-- Add approval-related columns to slot_registration table

ALTER TABLE slot_registration
    ADD COLUMN approval_status ENUM('PENDING','APPROVED','DECLINED','EXPIRED') NOT NULL DEFAULT 'PENDING' AFTER registered_at,
    ADD COLUMN approval_token VARCHAR(255) UNIQUE AFTER approval_status,
    ADD COLUMN approval_token_expires_at DATETIME AFTER approval_token,
    ADD COLUMN supervisor_approved_at DATETIME AFTER approval_token_expires_at,
    ADD COLUMN supervisor_declined_at DATETIME AFTER supervisor_approved_at,
    ADD COLUMN supervisor_declined_reason TEXT AFTER supervisor_declined_at;

-- Add indexes for approval columns
CREATE INDEX idx_slot_registration_approval_status ON slot_registration(approval_status);
CREATE INDEX idx_slot_registration_approval_token ON slot_registration(approval_token);

-- =====================================================================
-- CREATE WAITING_LIST TABLE
-- =====================================================================
-- Create the waiting_list table for managing waiting lists

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

-- Add indexes for waiting_list table
CREATE INDEX idx_waiting_list_slot ON waiting_list(slot_id);
CREATE INDEX idx_waiting_list_presenter ON waiting_list(presenter_username);
CREATE INDEX idx_waiting_list_position ON waiting_list(slot_id, position);

-- =====================================================================
-- VERIFICATION
-- =====================================================================
-- Run these queries to verify the changes were applied correctly

-- Check slot_registration columns
DESCRIBE slot_registration;

-- Check waiting_list table exists
SHOW TABLES LIKE 'waiting_list';

-- Check indexes
SHOW INDEXES FROM slot_registration WHERE Key_name LIKE '%approval%';
SHOW INDEXES FROM waiting_list;

SELECT 'Database schema updated successfully!' AS status;


-- =====================================================================
-- ALTER COMMANDS FOR WAITING LIST PROMOTION CONFIRMATION (2025-12-28)
-- =====================================================================
-- Adds promotion offer confirmation flow - users must confirm they still
-- want the slot before being promoted from waiting list
-- =====================================================================

-- Add promotion offer columns to waiting_list table
ALTER TABLE waiting_list
    ADD COLUMN promotion_token VARCHAR(255) NULL,
    ADD COLUMN promotion_token_expires_at DATETIME NULL,
    ADD COLUMN promotion_offered_at DATETIME NULL;

-- Add index for promotion token lookups
CREATE INDEX idx_waiting_list_promotion_token ON waiting_list(promotion_token);
CREATE INDEX idx_waiting_list_promotion_expires ON waiting_list(promotion_token_expires_at);

-- Verification
SELECT 'Waiting list promotion confirmation columns added!' AS status;
DESCRIBE waiting_list;


-- =====================================================================
-- ALTER COMMANDS FOR SUPERVISOR APPROVAL REMINDERS (2025-12-28)
-- =====================================================================
-- Adds reminder tracking for supervisor approval emails
-- =====================================================================

-- Add last reminder tracking column
ALTER TABLE slot_registration ADD COLUMN last_reminder_sent_at DATETIME NULL AFTER supervisor_declined_reason;

-- Add config values for reminder system
INSERT INTO app_config (config_key, config_value, config_type, target_system, category, description) VALUES
('approval_reminder_interval_days', '2', 'INTEGER', 'API', 'email', 'Days between supervisor reminder emails'),
('approval_token_expiry_days', '14', 'INTEGER', 'API', 'email', 'Days until approval link expires')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- Verification
SELECT 'Supervisor approval reminder columns added!' AS status;
DESCRIBE slot_registration;


-- =====================================================================
-- ALTER COMMANDS FOR CONFIGURABLE EXPIRATION TIMES (2025-12-28)
-- =====================================================================
-- Makes promotion offer expiry and expiration warning timing configurable
-- =====================================================================

-- Add config values for promotion offer expiry and expiration warnings
-- Using 48 hours (2 days) to give students more time to respond
INSERT INTO app_config (config_key, config_value, config_type, target_system, category, description) VALUES
('promotion_offer_expiry_hours', '48', 'INTEGER', 'API', 'waiting_list', 'Hours until waiting list promotion offer expires'),
('expiration_warning_hours_before', '48', 'INTEGER', 'API', 'email', 'Hours before expiry to send warning email to student')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- Verification
SELECT 'Configurable expiration times added!' AS status;
SELECT * FROM app_config WHERE config_key IN ('promotion_offer_expiry_hours', 'expiration_warning_hours_before');

