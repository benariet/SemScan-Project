-- Sample Data for SemScan Database
-- Run this after database-setup.sql

USE attendance;

-- Insert sample teacher
INSERT INTO users (user_id, email, first_name, last_name, role) 
VALUES ('teacher-001', 'teacher@university.edu', 'John', 'Smith', 'teacher')
ON DUPLICATE KEY UPDATE email = email;

-- Insert sample students
INSERT INTO users (user_id, email, first_name, last_name, role, student_id) VALUES
('student-001', 'alice@student.edu', 'Alice', 'Johnson', 'student', 'STU001'),
('student-002', 'bob@student.edu', 'Bob', 'Brown', 'student', 'STU002'),
('student-003', 'charlie@student.edu', 'Charlie', 'Davis', 'student', 'STU003'),
('student-004', 'diana@student.edu', 'Diana', 'Wilson', 'student', 'STU004'),
('student-005', 'eve@student.edu', 'Eve', 'Garcia', 'student', 'STU005')
ON DUPLICATE KEY UPDATE email = email;

-- Insert sample courses
INSERT INTO courses (course_id, course_name, course_code, description, lecturer_id) VALUES
('course-001', 'Introduction to Computer Science', 'CS101', 'Basic programming concepts and algorithms', 'teacher-001'),
('course-002', 'Data Structures and Algorithms', 'CS201', 'Advanced data structures and algorithm design', 'teacher-001'),
('course-003', 'Database Systems', 'CS301', 'Relational databases and SQL', 'teacher-001')
ON DUPLICATE KEY UPDATE course_name = course_name;

-- Insert sample sessions
INSERT INTO sessions (session_id, course_id, start_time, status) VALUES
('session-001', 'course-001', '2025-09-17 10:00:00', 'closed'),
('session-002', 'course-001', '2025-09-18 10:00:00', 'open'),
('session-003', 'course-002', '2025-09-17 14:00:00', 'closed'),
('session-004', 'course-003', '2025-09-18 16:00:00', 'open')
ON DUPLICATE KEY UPDATE start_time = start_time;

-- Insert sample attendance records
INSERT INTO attendance (attendance_id, session_id, student_id, attendance_time, status) VALUES
('attendance-001', 'session-001', 'student-001', '2025-09-17 10:05:00', 'present'),
('attendance-002', 'session-001', 'student-002', '2025-09-17 10:03:00', 'present'),
('attendance-003', 'session-001', 'student-003', '2025-09-17 10:15:00', 'late'),
('attendance-004', 'session-003', 'student-001', '2025-09-17 14:02:00', 'present'),
('attendance-005', 'session-003', 'student-002', '2025-09-17 14:01:00', 'present')
ON DUPLICATE KEY UPDATE attendance_time = attendance_time;

-- Insert sample absence requests
INSERT INTO absence_requests (request_id, student_id, course_id, session_id, reason, note, status) VALUES
('absence-001', 'student-004', 'course-001', 'session-001', 'Medical appointment', 'Had a doctor appointment', 'approved'),
('absence-002', 'student-005', 'course-001', 'session-001', 'Family emergency', 'Family member hospitalized', 'approved'),
('absence-003', 'student-003', 'course-002', 'session-003', 'Transportation issue', 'Car broke down', 'pending')
ON DUPLICATE KEY UPDATE reason = reason;

-- Insert sample API key for teacher
INSERT INTO teacher_api_keys (key_id, teacher_id, api_key, key_name) 
VALUES ('key-001', 'teacher-001', 'test-api-key-12345', 'Default API Key')
ON DUPLICATE KEY UPDATE api_key = api_key;

SELECT 'Sample Data Inserted Successfully!' as Status;
