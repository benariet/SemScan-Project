-- Migration V2: Add Manual Attendance Request Fields
-- This migration adds support for manual attendance requests

-- Add new columns to attendance table for manual requests
ALTER TABLE attendance 
ADD COLUMN request_status ENUM('confirmed', 'pending_approval', 'rejected') DEFAULT 'confirmed',
ADD COLUMN manual_reason VARCHAR(255) NULL,
ADD COLUMN requested_at TIMESTAMP NULL,
ADD COLUMN approved_by VARCHAR(36) NULL,
ADD COLUMN approved_at TIMESTAMP NULL,
ADD COLUMN device_id VARCHAR(255) NULL,
ADD COLUMN auto_flags JSON NULL;

-- Add foreign key constraint for approved_by
ALTER TABLE attendance 
ADD FOREIGN KEY (approved_by) REFERENCES users(user_id) ON DELETE SET NULL;

-- Update the method enum to include manual_request
ALTER TABLE attendance 
MODIFY COLUMN method ENUM('qr_scan', 'manual', 'manual_request', 'proxy') DEFAULT 'qr_scan';

-- Add index for better query performance on pending requests
CREATE INDEX idx_attendance_request_status ON attendance(request_status);
CREATE INDEX idx_attendance_session_status ON attendance(session_id, request_status);
