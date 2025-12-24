-- Add unique constraints to prevent race condition duplicates
-- Run this on existing database

-- Check and add attendance constraint (prevents duplicate attendance)
-- This may already exist if using the new schema
ALTER TABLE attendance
ADD CONSTRAINT uq_attendance_session_student UNIQUE (session_id, student_username);

-- Note: slot_registration already has PRIMARY KEY (slot_id, presenter_username)
-- which acts as a unique constraint

-- Note: waiting_list already has unique_slot_presenter_waiting constraint
-- If not, run: ALTER TABLE waiting_list ADD UNIQUE KEY unique_slot_presenter_waiting (slot_id, presenter_username);
