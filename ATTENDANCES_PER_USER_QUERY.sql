-- =============================================
-- ATTENDANCES PER USER QUERY
-- =============================================
-- This query shows the total number of attendances for each user/student
-- Results are ordered by attendance count (highest first)

SELECT 
    a.student_username,
    CONCAT(u.first_name, ' ', u.last_name) AS student_name,
    u.email AS student_email,
    u.degree AS student_degree,
    COUNT(a.attendance_id) AS total_attendances,
    MIN(a.attendance_time) AS first_attendance,
    MAX(a.attendance_time) AS last_attendance
FROM 
    attendance a
LEFT JOIN 
    users u ON a.student_username = u.bgu_username
GROUP BY 
    a.student_username, u.first_name, u.last_name, u.email, u.degree
ORDER BY 
    total_attendances DESC, a.student_username ASC;

-- =============================================
-- SIMPLIFIED VERSION (just username and count)
-- =============================================
-- SELECT 
--     student_username,
--     COUNT(*) AS total_attendances
-- FROM 
--     attendance
-- GROUP BY 
--     student_username
-- ORDER BY 
--     total_attendances DESC;

-- =============================================
-- ATTENDANCES PER USER WITH SESSION DETAILS
-- =============================================
-- This version includes session information
-- SELECT 
--     a.student_username,
--     CONCAT(u.first_name, ' ', u.last_name) AS student_name,
--     COUNT(DISTINCT a.session_id) AS sessions_attended,
--     COUNT(a.attendance_id) AS total_attendances,
--     GROUP_CONCAT(DISTINCT s.start_time ORDER BY s.start_time DESC SEPARATOR ', ') AS session_dates
-- FROM 
--     attendance a
-- LEFT JOIN 
--     users u ON a.student_username = u.bgu_username
-- LEFT JOIN 
--     sessions s ON a.session_id = s.session_id
-- GROUP BY 
--     a.student_username, u.first_name, u.last_name
-- ORDER BY 
--     total_attendances DESC;

