-- =============================================
-- Check if Approval Token Exists in Database
-- =============================================
-- Run this to check if a specific token exists and why it might not be found

-- Replace 'YOUR_TOKEN_HERE' with the actual token you're trying to approve
SET @token = '4e58424b-8fa3-44f4-a5ae-d14ba32c7873';

-- 1. Check if token exists in database
SELECT 
    slot_id,
    presenter_username,
    supervisor_email,
    approval_token,
    approval_status,
    approval_token_expires_at,
    registered_at,
    CASE 
        WHEN approval_token IS NULL THEN 'TOKEN_IS_NULL'
        WHEN approval_token != @token THEN 'TOKEN_MISMATCH'
        WHEN approval_token_expires_at IS NOT NULL AND approval_token_expires_at < NOW() THEN 'TOKEN_EXPIRED'
        WHEN approval_status != 'PENDING' THEN CONCAT('STATUS_NOT_PENDING_', approval_status)
        ELSE 'TOKEN_FOUND_AND_VALID'
    END AS token_status
FROM slot_registration
WHERE approval_token = @token
   OR (slot_id = 279 AND presenter_username = 'talguest3');

-- 2. Check all registrations for slot 279 and presenter talguest3
SELECT 
    slot_id,
    presenter_username,
    supervisor_email,
    approval_token,
    approval_status,
    approval_token_expires_at,
    registered_at,
    supervisor_approved_at
FROM slot_registration
WHERE slot_id = 279 
  AND presenter_username = 'talguest3'
ORDER BY registered_at DESC;

-- 3. Check recent registrations to see token patterns
SELECT 
    slot_id,
    presenter_username,
    supervisor_email,
    LEFT(approval_token, 8) AS token_prefix,
    approval_status,
    approval_token_expires_at,
    registered_at
FROM slot_registration
WHERE registered_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
ORDER BY registered_at DESC
LIMIT 10;

-- 4. Check for registrations with null tokens (indicates email wasn't sent)
SELECT 
    slot_id,
    presenter_username,
    supervisor_email,
    approval_status,
    registered_at
FROM slot_registration
WHERE approval_token IS NULL
  AND approval_status = 'PENDING'
  AND registered_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY registered_at DESC;
