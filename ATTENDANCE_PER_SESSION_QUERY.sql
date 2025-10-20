-- ATTENDANCE PER SESSION QUERY
-- =============================

-- Query 1: Basic attendance per session
SELECT 
    sessions.session_id,
    sessions.seminar_id,
    sessions.start_time,
    sessions.end_time,
    sessions.status,
    COUNT(attendance.attendance_id) as total_attendance,
    COUNT(CASE WHEN attendance.request_status = 'CONFIRMED' THEN 1 END) as confirmed_count,
    COUNT(CASE WHEN attendance.request_status = 'PENDING_APPROVAL' THEN 1 END) as pending_count,
    COUNT(CASE WHEN attendance.request_status = 'REJECTED' THEN 1 END) as rejected_count,
    COUNT(CASE WHEN attendance.method = 'QR_SCAN' THEN 1 END) as qr_scan_count,
    COUNT(CASE WHEN attendance.method = 'MANUAL' THEN 1 END) as manual_count,
    COUNT(CASE WHEN attendance.method = 'MANUAL_REQUEST' THEN 1 END) as manual_request_count
FROM sessions
LEFT JOIN attendance ON sessions.session_id = attendance.session_id
GROUP BY sessions.session_id, sessions.seminar_id, sessions.start_time, sessions.end_time, sessions.status
ORDER BY sessions.start_time DESC;

-- Query 2: Detailed attendance per session with student info
SELECT 
    sessions.session_id,
    sessions.seminar_id,
    sessions.start_time,
    sessions.status,
    attendance.attendance_id,
    attendance.student_id,
    users.first_name,
    users.last_name,
    users.email,
    attendance.attendance_time,
    attendance.method,
    attendance.request_status,
    attendance.manual_reason,
    attendance.requested_at,
    attendance.approved_by,
    attendance.approved_at
FROM sessions
LEFT JOIN attendance ON sessions.session_id = attendance.session_id
LEFT JOIN users ON attendance.student_id = users.user_id
WHERE sessions.status = 'OPEN'  -- Only show open sessions
ORDER BY sessions.start_time DESC, attendance.attendance_time DESC;

-- Query 3: Attendance summary per session
SELECT 
    sessions.session_id,
    sessions.seminar_id,
    sessions.start_time,
    sessions.status,
    COUNT(attendance.attendance_id) as total_attendance,
    COUNT(CASE WHEN attendance.request_status = 'CONFIRMED' THEN 1 END) as confirmed_count,
    COUNT(CASE WHEN attendance.request_status = 'PENDING_APPROVAL' THEN 1 END) as pending_count,
    COUNT(CASE WHEN attendance.request_status = 'REJECTED' THEN 1 END) as rejected_count,
    COUNT(CASE WHEN attendance.method = 'QR_SCAN' THEN 1 END) as qr_scan_count,
    COUNT(CASE WHEN attendance.method = 'MANUAL' THEN 1 END) as manual_count,
    COUNT(CASE WHEN attendance.method = 'MANUAL_REQUEST' THEN 1 END) as manual_request_count
FROM sessions
LEFT JOIN attendance ON sessions.session_id = attendance.session_id
GROUP BY sessions.session_id, sessions.seminar_id, sessions.start_time, sessions.status
ORDER BY sessions.start_time DESC;

-- Query 4: Current open sessions with attendance count
SELECT 
    sessions.session_id,
    sessions.seminar_id,
    sessions.start_time,
    sessions.status,
    COUNT(attendance.attendance_id) as total_attendance,
    COUNT(CASE WHEN attendance.request_status = 'CONFIRMED' THEN 1 END) as confirmed_count,
    COUNT(CASE WHEN attendance.request_status = 'PENDING_APPROVAL' THEN 1 END) as pending_requests
FROM sessions
LEFT JOIN attendance ON sessions.session_id = attendance.session_id
WHERE sessions.status = 'OPEN'
GROUP BY sessions.session_id, sessions.seminar_id, sessions.start_time, sessions.status
ORDER BY sessions.start_time DESC;

-- Query 5: Manual attendance requests per session
SELECT 
    sessions.session_id,
    sessions.seminar_id,
    sessions.start_time,
    sessions.status,
    attendance.attendance_id,
    attendance.student_id,
    users.first_name,
    users.last_name,
    users.email,
    attendance.attendance_time,
    attendance.request_status,
    attendance.manual_reason,
    attendance.method,
    attendance.requested_at,
    attendance.approved_by,
    attendance.approved_at
FROM sessions
INNER JOIN attendance ON sessions.session_id = attendance.session_id
LEFT JOIN users ON attendance.student_id = users.user_id
WHERE attendance.method = 'MANUAL_REQUEST'
ORDER BY sessions.start_time DESC, attendance.attendance_time DESC;

-- Query 6: Attendance statistics per session
SELECT 
    sessions.session_id,
    sessions.seminar_id,
    sessions.start_time,
    sessions.status,
    COUNT(attendance.attendance_id) as total_attendance,
    ROUND(COUNT(CASE WHEN attendance.request_status = 'CONFIRMED' THEN 1 END) * 100.0 / COUNT(attendance.attendance_id), 2) as confirmed_percentage,
    COUNT(CASE WHEN attendance.method = 'QR_SCAN' THEN 1 END) as qr_scan_count,
    COUNT(CASE WHEN attendance.method = 'MANUAL' THEN 1 END) as manual_count,
    COUNT(CASE WHEN attendance.method = 'MANUAL_REQUEST' THEN 1 END) as manual_request_count,
    COUNT(CASE WHEN attendance.request_status = 'PENDING_APPROVAL' THEN 1 END) as pending_requests
FROM sessions
LEFT JOIN attendance ON sessions.session_id = attendance.session_id
GROUP BY sessions.session_id, sessions.seminar_id, sessions.start_time, sessions.status
HAVING COUNT(attendance.attendance_id) > 0  -- Only show sessions with attendance
ORDER BY sessions.start_time DESC;

-- Query 7: Recent sessions with attendance (last 7 days)
SELECT 
    sessions.session_id,
    sessions.seminar_id,
    sessions.start_time,
    sessions.status,
    COUNT(attendance.attendance_id) as total_attendance,
    COUNT(CASE WHEN attendance.request_status = 'CONFIRMED' THEN 1 END) as confirmed_count,
    COUNT(CASE WHEN attendance.request_status = 'PENDING_APPROVAL' THEN 1 END) as pending_requests
FROM sessions
LEFT JOIN attendance ON sessions.session_id = attendance.session_id
WHERE sessions.start_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY sessions.session_id, sessions.seminar_id, sessions.start_time, sessions.status
ORDER BY sessions.start_time DESC;

-- Query 8: Export-ready attendance per session
SELECT 
    sessions.session_id as 'Session ID',
    sessions.seminar_id as 'Seminar ID',
    sessions.start_time as 'Session Start Time',
    sessions.status as 'Session Status',
    attendance.attendance_id as 'Attendance ID',
    attendance.student_id as 'Student ID',
    users.first_name as 'First Name',
    users.last_name as 'Last Name',
    users.email as 'Email',
    attendance.attendance_time as 'Attendance Time',
    attendance.method as 'Attendance Method',
    attendance.request_status as 'Request Status',
    attendance.manual_reason as 'Manual Reason',
    attendance.requested_at as 'Requested At',
    attendance.approved_by as 'Approved By',
    attendance.approved_at as 'Approved At'
FROM sessions
LEFT JOIN attendance ON sessions.session_id = attendance.session_id
LEFT JOIN users ON attendance.student_id = users.user_id
ORDER BY sessions.start_time DESC, attendance.attendance_time DESC;

-- Query 9: Students who attended a specific session (with student names)
-- Replace 'session-001' with the actual session_id you want to check
SELECT 
    sessions.session_id,
    sessions.seminar_id,
    sessions.start_time,
    sessions.status as session_status,
    attendance.attendance_id,
    attendance.student_id,
    users.first_name,
    users.last_name,
    users.email,
    attendance.attendance_time,
    attendance.method,
    attendance.request_status,
    attendance.manual_reason,
    attendance.requested_at,
    attendance.approved_by,
    attendance.approved_at
FROM sessions
LEFT JOIN attendance ON sessions.session_id = attendance.session_id
LEFT JOIN users ON attendance.student_id = users.user_id
WHERE sessions.session_id = 'session-001'  -- Replace with your session ID
ORDER BY attendance.attendance_time ASC;

-- Query 10: Students who attended a specific session (with user details)
-- This query joins with the users table to get student names
SELECT 
    sessions.session_id,
    sessions.seminar_id,
    sessions.start_time,
    sessions.status as session_status,
    attendance.attendance_id,
    attendance.student_id,
    users.first_name,
    users.last_name,
    users.email,
    attendance.attendance_time,
    attendance.method,
    attendance.request_status,
    attendance.manual_reason,
    attendance.requested_at,
    attendance.approved_by,
    attendance.approved_at
FROM sessions
LEFT JOIN attendance ON sessions.session_id = attendance.session_id
LEFT JOIN users ON attendance.student_id = users.user_id
WHERE sessions.session_id = 'session-001'  -- Replace with your session ID
ORDER BY attendance.attendance_time ASC;
