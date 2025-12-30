-- Migration V3: Add Performance Indexes
-- This migration adds indexes for commonly queried columns to improve query performance

-- Index for slot_registration lookups by presenter
-- The composite PK (slot_id, presenter_username) already covers lookups by slot_id,
-- but queries filtering only by presenter_username need a separate index
CREATE INDEX IF NOT EXISTS idx_slot_registration_presenter
    ON slot_registration(presenter_username);

-- Index for slot_registration lookups by approval status
-- Frequently used to filter approved/pending registrations
CREATE INDEX IF NOT EXISTS idx_slot_registration_approval_status
    ON slot_registration(approval_status);

-- Composite index for slot + approval status queries (common query pattern)
CREATE INDEX IF NOT EXISTS idx_slot_registration_slot_approval
    ON slot_registration(slot_id, approval_status);

-- Index for waiting_list lookups by slot
CREATE INDEX IF NOT EXISTS idx_waiting_list_slot_id
    ON waiting_list(slot_id);

-- Index for waiting_list lookups by presenter
CREATE INDEX IF NOT EXISTS idx_waiting_list_presenter
    ON waiting_list(presenter_username);

-- Composite index for waiting list position queries
CREATE INDEX IF NOT EXISTS idx_waiting_list_slot_position
    ON waiting_list(slot_id, position);

-- Index for app_logs lookups by username (FK relationship)
CREATE INDEX IF NOT EXISTS idx_app_logs_bgu_username
    ON app_logs(bgu_username);

-- Index for app_logs lookups by tag (common filtering)
CREATE INDEX IF NOT EXISTS idx_app_logs_tag
    ON app_logs(tag);

-- Composite index for app_logs time-based queries with username
CREATE INDEX IF NOT EXISTS idx_app_logs_username_timestamp
    ON app_logs(bgu_username, log_timestamp);

-- Index for app_logs correlation ID lookups (tracing)
CREATE INDEX IF NOT EXISTS idx_app_logs_correlation_id
    ON app_logs(correlation_id);
