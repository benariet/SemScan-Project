-- SemScan Attendance System Database Schema
-- MySQL 8.4 Database Schema

USE attendance;

-- =============================================
-- 1. USERS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role ENUM('student', 'teacher', 'admin') NOT NULL DEFAULT 'student',
    student_id VARCHAR(50) UNIQUE NULL, -- Only for students
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- =============================================
-- 2. COURSES TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS courses (
    course_id VARCHAR(36) PRIMARY KEY,
    course_name VARCHAR(255) NOT NULL,
    course_code VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    lecturer_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (lecturer_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- =============================================
-- 3. ENROLLMENTS TABLE (Students in Courses)
-- =============================================
CREATE TABLE IF NOT EXISTS enrollments (
    enrollment_id VARCHAR(36) PRIMARY KEY,
    course_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status ENUM('active', 'dropped', 'completed') DEFAULT 'active',
    FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY unique_enrollment (course_id, student_id)
);

-- =============================================
-- 4. SESSIONS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    course_id VARCHAR(36) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NULL,
    status ENUM('open', 'closed') DEFAULT 'open',
    qr_code_data TEXT, -- Store QR code payload
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
    INDEX idx_course_time (course_id, start_time),
    INDEX idx_status (status)
);

-- =============================================
-- 5. ATTENDANCE TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS attendance (
    attendance_id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    attendance_time TIMESTAMP NOT NULL,
    status ENUM('present', 'late', 'absent') DEFAULT 'present',
    method ENUM('qr_scan', 'manual', 'proxy') DEFAULT 'qr_scan',
    latitude DECIMAL(10, 8) NULL, -- Optional location tracking
    longitude DECIMAL(11, 8) NULL,
    device_info TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY unique_attendance (session_id, student_id),
    INDEX idx_session_time (session_id, attendance_time),
    INDEX idx_student_time (student_id, attendance_time)
);

-- =============================================
-- 6. ABSENCE REQUESTS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS absence_requests (
    request_id VARCHAR(36) PRIMARY KEY,
    student_id VARCHAR(36) NOT NULL,
    course_id VARCHAR(36) NOT NULL,
    session_id VARCHAR(36) NULL, -- NULL for general absence
    reason VARCHAR(255) NOT NULL,
    note TEXT,
    status ENUM('pending', 'approved', 'rejected') DEFAULT 'pending',
    reviewed_by VARCHAR(36) NULL, -- Teacher who reviewed
    reviewed_at TIMESTAMP NULL,
    review_notes TEXT NULL,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE,
    FOREIGN KEY (reviewed_by) REFERENCES users(user_id) ON DELETE SET NULL,
    INDEX idx_student_status (student_id, status),
    INDEX idx_course_status (course_id, status),
    INDEX idx_status (status)
);

-- =============================================
-- 7. TEACHER API KEYS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS teacher_api_keys (
    key_id VARCHAR(36) PRIMARY KEY,
    teacher_id VARCHAR(36) NOT NULL,
    api_key VARCHAR(255) UNIQUE NOT NULL,
    key_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL,
    FOREIGN KEY (teacher_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_api_key (api_key),
    INDEX idx_teacher (teacher_id)
);

-- =============================================
-- 8. SYSTEM SETTINGS TABLE
-- =============================================
CREATE TABLE IF NOT EXISTS system_settings (
    setting_id VARCHAR(36) PRIMARY KEY,
    setting_key VARCHAR(100) UNIQUE NOT NULL,
    setting_value TEXT,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =============================================
-- 9. AUDIT LOG TABLE (Optional - for tracking changes)
-- =============================================
CREATE TABLE IF NOT EXISTS audit_log (
    log_id VARCHAR(36) PRIMARY KEY,
    table_name VARCHAR(100) NOT NULL,
    record_id VARCHAR(36) NOT NULL,
    action ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
    old_values JSON NULL,
    new_values JSON NULL,
    user_id VARCHAR(36) NULL,
    ip_address VARCHAR(45) NULL,
    user_agent TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_table_record (table_name, record_id),
    INDEX idx_user_time (user_id, created_at),
    INDEX idx_action_time (action, created_at)
);

-- =============================================
-- INDEXES FOR PERFORMANCE
-- =============================================

-- Additional indexes for better query performance
CREATE INDEX idx_courses_lecturer ON courses(lecturer_id);
CREATE INDEX idx_sessions_course_status ON sessions(course_id, status);
CREATE INDEX idx_attendance_student_time ON attendance(student_id, attendance_time);
CREATE INDEX idx_absence_requests_submitted ON absence_requests(submitted_at);

-- =============================================
-- SAMPLE DATA (Optional - for testing)
-- =============================================

-- Insert sample teacher
INSERT INTO users (user_id, email, first_name, last_name, role) 
VALUES ('teacher-001', 'teacher@university.edu', 'John', 'Smith', 'teacher')
ON DUPLICATE KEY UPDATE email = email;

-- Insert sample students
INSERT INTO users (user_id, email, first_name, last_name, role, student_id) VALUES
('student-001', 'alice@student.edu', 'Alice', 'Johnson', 'student', 'STU001'),
('student-002', 'bob@student.edu', 'Bob', 'Brown', 'student', 'STU002'),
('student-003', 'charlie@student.edu', 'Charlie', 'Davis', 'student', 'STU003')
ON DUPLICATE KEY UPDATE email = email;

-- Insert sample course
INSERT INTO courses (course_id, course_name, course_code, description, lecturer_id) 
VALUES ('course-001', 'Introduction to Computer Science', 'CS101', 'Basic programming concepts and algorithms', 'teacher-001')
ON DUPLICATE KEY UPDATE course_name = course_name;

-- Insert sample enrollments
INSERT INTO enrollments (enrollment_id, course_id, student_id) VALUES
('enroll-001', 'course-001', 'student-001'),
('enroll-002', 'course-001', 'student-002'),
('enroll-003', 'course-001', 'student-003')
ON DUPLICATE KEY UPDATE enrollment_id = enrollment_id;

-- Insert sample API key for teacher
INSERT INTO teacher_api_keys (key_id, teacher_id, api_key, key_name) 
VALUES ('key-001', 'teacher-001', 'test-api-key-12345', 'Default API Key')
ON DUPLICATE KEY UPDATE api_key = api_key;

-- Insert system settings
INSERT INTO system_settings (setting_id, setting_key, setting_value, description) VALUES
('setting-001', 'qr_timeout_minutes', '15', 'QR code validity period in minutes'),
('setting-002', 'max_absence_requests', '5', 'Maximum absence requests per student per course'),
('setting-003', 'attendance_threshold', '75', 'Minimum attendance percentage required')
ON DUPLICATE KEY UPDATE setting_value = setting_value;

-- =============================================
-- VIEWS FOR COMMON QUERIES
-- =============================================

-- View for attendance summary
CREATE OR REPLACE VIEW attendance_summary AS
SELECT 
    c.course_name,
    c.course_code,
    s.session_id,
    s.start_time,
    s.status as session_status,
    COUNT(DISTINCT e.student_id) as total_enrolled,
    COUNT(DISTINCT a.student_id) as attended_count,
    COUNT(DISTINCT ar.student_id) as absence_request_count
FROM courses c
JOIN sessions s ON c.course_id = s.course_id
LEFT JOIN enrollments e ON c.course_id = e.course_id AND e.status = 'active'
LEFT JOIN attendance a ON s.session_id = a.session_id
LEFT JOIN absence_requests ar ON s.session_id = ar.session_id AND ar.status = 'approved'
GROUP BY c.course_id, s.session_id;

-- View for student attendance history
CREATE OR REPLACE VIEW student_attendance_history AS
SELECT 
    u.user_id,
    u.first_name,
    u.last_name,
    u.student_id,
    c.course_name,
    c.course_code,
    s.start_time,
    s.session_id,
    a.status as attendance_status,
    a.attendance_time,
    ar.status as absence_request_status,
    ar.reason as absence_reason
FROM users u
JOIN enrollments e ON u.user_id = e.student_id
JOIN courses c ON e.course_id = c.course_id
LEFT JOIN sessions s ON c.course_id = s.course_id
LEFT JOIN attendance a ON s.session_id = a.session_id AND u.user_id = a.student_id
LEFT JOIN absence_requests ar ON s.session_id = ar.session_id AND u.user_id = ar.student_id
WHERE u.role = 'student' AND e.status = 'active';

-- =============================================
-- STORED PROCEDURES (Optional)
-- =============================================

DELIMITER //

-- Procedure to close a session and mark remaining students as absent
CREATE PROCEDURE CloseSession(IN session_uuid VARCHAR(36))
BEGIN
    DECLARE course_uuid VARCHAR(36);
    DECLARE session_start TIMESTAMP;
    
    -- Get session details
    SELECT course_id, start_time INTO course_uuid, session_start
    FROM sessions WHERE session_id = session_uuid;
    
    -- Update session status
    UPDATE sessions 
    SET status = 'closed', end_time = NOW() 
    WHERE session_id = session_uuid;
    
    -- Mark students who didn't attend as absent
    INSERT INTO absence_requests (request_id, student_id, course_id, session_id, reason, status)
    SELECT 
        UUID() as request_id,
        e.student_id,
        course_uuid,
        session_uuid,
        'Auto-marked absent - no attendance recorded',
        'approved'
    FROM enrollments e
    WHERE e.course_id = course_uuid 
    AND e.status = 'active'
    AND e.student_id NOT IN (
        SELECT student_id FROM attendance WHERE session_id = session_uuid
    )
    AND e.student_id NOT IN (
        SELECT student_id FROM absence_requests WHERE session_id = session_uuid
    );
    
END //

DELIMITER ;

-- =============================================
-- COMPLETION MESSAGE
-- =============================================
SELECT 'SemScan Database Schema Created Successfully!' as Status;
