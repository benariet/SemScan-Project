-- Migration: Add national_id_number column to users table
-- Run this SQL on existing databases to add the new column

ALTER TABLE users 
ADD COLUMN national_id_number VARCHAR(50) NULL AFTER is_participant;

