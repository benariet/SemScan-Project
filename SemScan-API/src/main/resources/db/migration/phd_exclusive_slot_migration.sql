-- PhD Exclusive Slot Migration
-- This migration updates the capacity system for PhD/MSc exclusivity:
-- - PhD takes entire slot (no MSc allowed when PhD registered)
-- - No PhD = 3 MSc spots available
-- - PhD can't register if any MSc exists
-- - MSc can't register if PhD exists

-- IMPORTANT: Run these queries on production database BEFORE deploying new code

-- ============================================================================
-- PRE-DEPLOYMENT CHECKS
-- ============================================================================

-- 1. Check current PhD weight (should be 2, will change to 3)
SELECT config_key, config_value, target_system
FROM app_config
WHERE config_key = 'phd.capacity.weight';

-- 2. Check current slot capacities
SELECT slot_id, slot_date, capacity, status
FROM slots
WHERE slot_date >= CURDATE()
ORDER BY slot_date;

-- 3. Check for slots with mixed PhD/MSc registrations (shouldn't exist, but verify)
SELECT s.slot_id, s.slot_date,
       GROUP_CONCAT(CONCAT(r.presenter_username, ':', r.degree)) as registrations
FROM slots s
JOIN slot_registration r ON s.slot_id = r.slot_id
WHERE s.slot_date >= CURDATE()
  AND r.approval_status IN ('PENDING', 'APPROVED')
GROUP BY s.slot_id
HAVING COUNT(DISTINCT r.degree) > 1;

-- ============================================================================
-- MIGRATION STEPS
-- ============================================================================

-- Step 1: Update PhD capacity weight from 2 to 3
-- This means PhD now takes 3 capacity (entire slot)
UPDATE app_config
SET config_value = '3',
    updated_at = NOW()
WHERE config_key = 'phd.capacity.weight';

-- Step 2: Update all future slot capacities from 2 to 3
-- This allows 3 MSc students OR 1 PhD per slot
UPDATE slots
SET capacity = 3,
    updated_at = NOW()
WHERE slot_date >= CURDATE()
  AND capacity = 2;

-- Step 3: Verify the changes
SELECT config_key, config_value FROM app_config WHERE config_key = 'phd.capacity.weight';
SELECT slot_id, slot_date, capacity FROM slots WHERE slot_date >= CURDATE() ORDER BY slot_date LIMIT 10;

-- ============================================================================
-- POST-MIGRATION: Restart API Service
-- ============================================================================
-- After running this migration, restart the API service to clear config cache:
-- sudo systemctl restart semscan-api

-- ============================================================================
-- ROLLBACK (if needed)
-- ============================================================================
-- To rollback, run:
-- UPDATE app_config SET config_value = '2', updated_at = NOW() WHERE config_key = 'phd.capacity.weight';
-- UPDATE slots SET capacity = 2, updated_at = NOW() WHERE slot_date >= CURDATE() AND capacity = 3;
