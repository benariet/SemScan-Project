-- =====================================================================
-- MIGRATION: Supervisor Linking to Users
-- =====================================================================
-- This migration adds supervisor support to the users table
-- Supervisors can have multiple presenters (students)
-- Run this script on existing databases to add supervisor functionality
-- =====================================================================

-- Create supervisors table
CREATE TABLE IF NOT EXISTS supervisors (
    supervisor_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP
);

-- Add supervisor_id column to users table (nullable for existing users)
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS supervisor_id BIGINT NULL;

-- Add foreign key constraint
-- Check if foreign key already exists before adding
SET @db_name = DATABASE();

SELECT COUNT(*) INTO @fk_exists FROM information_schema.table_constraints
WHERE table_schema = @db_name
AND table_name = 'users'
AND constraint_name = 'fk_users_supervisor'
AND constraint_type = 'FOREIGN KEY';

SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE users ADD CONSTRAINT fk_users_supervisor FOREIGN KEY (supervisor_id) REFERENCES supervisors(supervisor_id) ON DELETE SET NULL',
    'SELECT ''Foreign key fk_users_supervisor already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create index on supervisor_id for better query performance
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
WHERE table_schema = @db_name
AND table_name = 'users'
AND index_name = 'idx_users_supervisor_id';

SET @sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_users_supervisor_id ON users(supervisor_id)',
    'SELECT ''Index idx_users_supervisor_id already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Create index on supervisor email for faster lookups
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
WHERE table_schema = @db_name
AND table_name = 'supervisors'
AND index_name = 'idx_supervisors_email';

SET @sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_supervisors_email ON supervisors(email)',
    'SELECT ''Index idx_supervisors_email already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- MIGRATION COMPLETE
-- =====================================================================
-- After running this migration:
-- 1. Existing users will have supervisor_id = NULL (first-time users)
-- 2. New users created via login will have supervisor_id = NULL
-- 3. Users must complete account setup via /api/v1/auth/setup/{username}
--    to link their supervisor
-- 4. Once linked, supervisor info will be used automatically for
--    registrations and waiting list entries
-- =====================================================================
