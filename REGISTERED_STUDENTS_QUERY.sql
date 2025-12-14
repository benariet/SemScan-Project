-- =====================================================================
-- Query to show registered students with approval status
-- =====================================================================
-- Shows all students who registered for slots, including pending approvals
-- =====================================================================

SELECT 
    sr.slot_id,
    CONCAT(u.first_name, ' ', u.last_name) AS student_name,
    u.bgu_username,
    sr.supervisor_name,
    CASE 
        WHEN sr.approval_status = 'PENDING' THEN 'Waiting for supervisor approval'
        WHEN sr.approval_status = 'APPROVED' THEN 'Approved'
        WHEN sr.approval_status = 'DECLINED' THEN 'Declined'
        WHEN sr.approval_status = 'EXPIRED' THEN 'Expired'
        ELSE 'Unknown'
    END AS status_description
FROM 
    slot_registration sr
    INNER JOIN users u ON sr.presenter_username = u.bgu_username
    INNER JOIN slots s ON sr.slot_id = s.slot_id
ORDER BY 
    s.slot_date DESC,
    s.start_time ASC,
    sr.approval_status,
    student_name;

-- =====================================================================
-- Filter by approval status (optional)
-- =====================================================================

-- Show only PENDING registrations
-- SELECT 
--     sr.slot_id,
--     s.slot_date,
--     s.start_time,
--     CONCAT(u.first_name, ' ', u.last_name) AS student_name,
--     u.bgu_username,
--     u.degree,
--     sr.topic,
--     sr.supervisor_name,
--     sr.supervisor_email,
--     sr.registered_at
-- FROM 
--     slot_registration sr
--     INNER JOIN users u ON sr.presenter_username = u.bgu_username
--     INNER JOIN slots s ON sr.slot_id = s.slot_id
-- WHERE 
--     sr.approval_status = 'PENDING'
-- ORDER BY 
--     s.slot_date DESC,
--     s.start_time ASC,
--     student_name;

-- =====================================================================
-- Summary by approval status
-- =====================================================================

-- SELECT 
--     approval_status,
--     COUNT(*) AS count,
--     GROUP_CONCAT(CONCAT(u.first_name, ' ', u.last_name) ORDER BY u.last_name SEPARATOR ', ') AS students
-- FROM 
--     slot_registration sr
--     INNER JOIN users u ON sr.presenter_username = u.bgu_username
-- GROUP BY 
--     approval_status
-- ORDER BY 
--     approval_status;

