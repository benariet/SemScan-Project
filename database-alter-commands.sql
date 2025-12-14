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

