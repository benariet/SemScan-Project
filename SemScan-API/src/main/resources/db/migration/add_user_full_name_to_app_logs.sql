-- Migration: Add user_full_name column to app_logs table
-- Date: 2026-01-04
-- Description: Store user's full name (first + last) for easier log readability

-- Add the column
ALTER TABLE app_logs
ADD COLUMN user_full_name VARCHAR(200) NULL AFTER bgu_username;

-- Backfill existing records with user names from users table
UPDATE app_logs al
INNER JOIN users u ON al.bgu_username = u.bgu_username
SET al.user_full_name = CONCAT(COALESCE(u.first_name, ''), ' ', COALESCE(u.last_name, ''))
WHERE al.bgu_username IS NOT NULL;

-- Trim any leading/trailing spaces from the backfilled data
UPDATE app_logs
SET user_full_name = TRIM(user_full_name)
WHERE user_full_name IS NOT NULL;

-- Set to NULL if it's just empty or whitespace
UPDATE app_logs
SET user_full_name = NULL
WHERE user_full_name = '' OR user_full_name = ' ';
