-- Fix for slot_registration INSERT lock contention
-- This script adds an explicit index on slot_id to optimize foreign key checks
-- Even though slot_id is part of the primary key, an explicit index can help with concurrent inserts

-- Check if index exists first
SELECT COUNT(*) AS index_exists
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE()
AND TABLE_NAME = 'slot_registration'
AND INDEX_NAME = 'idx_slot_registration_slot_id';

-- If the above returns 0, run the following command:
-- CREATE INDEX idx_slot_registration_slot_id ON slot_registration(slot_id);

-- OR simply run this (it will error if index exists, which is safe to ignore):
CREATE INDEX idx_slot_registration_slot_id ON slot_registration(slot_id);

-- Verify indexes
SHOW INDEX FROM slot_registration WHERE Key_name = 'idx_slot_registration_slot_id';

-- Additional optimization: Ensure foreign key columns are indexed
-- slots.slot_id is already indexed (PRIMARY KEY)
-- users.bgu_username is already indexed (UNIQUE + explicit index)

-- Note: If using MySQL 8.0+, you can also check for lock contention with:
-- SELECT * FROM performance_schema.data_locks WHERE object_schema = DATABASE() AND object_name = 'slot_registration';

