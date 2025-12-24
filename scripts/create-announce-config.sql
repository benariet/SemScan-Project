-- Create announce_config table for dynamic announcements
-- MySQL syntax

CREATE TABLE IF NOT EXISTS announce_config (
    id INT PRIMARY KEY,
    is_active BOOLEAN DEFAULT FALSE NOT NULL,
    version INT DEFAULT 1 NOT NULL,
    title VARCHAR(100) DEFAULT '',
    message TEXT,
    is_blocking BOOLEAN DEFAULT FALSE NOT NULL,
    start_at TIMESTAMP NULL,
    end_at TIMESTAMP NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Initialize with default row (id=1, singleton pattern)
INSERT IGNORE INTO announce_config (id, is_active, version, title, message, is_blocking)
VALUES (1, FALSE, 1, 'System Message', 'No active announcements.', FALSE);

-- Verify
SELECT * FROM announce_config;
