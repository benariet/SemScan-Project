USE semscan_db;

-- =============================
--  CLEAR EXISTING DATA (optional)
-- =============================
DELETE FROM app_logs;
DELETE FROM seminar_slot_registration;
DELETE FROM seminar_slot;
DELETE FROM attendance;
DELETE FROM sessions;
DELETE FROM seminars;
DELETE FROM users;

-- =============================
--  USERS
-- =============================
INSERT INTO users (email, bgu_username, first_name, last_name, degree, is_presenter, is_participant)
VALUES
('dr.john.smith@university.edu', 'john.smith', 'John', 'Smith', 'PhD', TRUE, FALSE),
('prof.sarah.jones@university.edu', 'sarah.jones', 'Sarah', 'Jones', 'PhD', TRUE, FALSE),
('dr.mike.wilson@university.edu', 'mike.wilson', 'Mike', 'Wilson', 'PhD', TRUE, FALSE),
('alice.johnson@student.edu', 'alice.johnson', 'Alice', 'Johnson', 'MSc', FALSE, TRUE),
('bob.brown@student.edu', 'bob.brown', 'Bob', 'Brown', 'MSc', FALSE, TRUE),
('charlie.davis@student.edu', 'charlie.davis', 'Charlie', 'Davis', 'MSc', FALSE, TRUE),
('diana.wilson@student.edu', 'diana.wilson', 'Diana', 'Wilson', 'MSc', FALSE, TRUE),
('eve.garcia@student.edu', 'eve.garcia', 'Eve', 'Garcia', 'MSc', FALSE, TRUE);

-- =============================
--  SEMINARS
-- =============================
INSERT INTO seminars (seminar_name, description, presenter_username, max_enrollment_capacity)
VALUES
('AI and Machine Learning in Healthcare', 'Applications of AI in medicine.', 'john.smith', 30),
('Blockchain Technology and Cryptocurrency', 'Fundamentals of blockchain and crypto markets.', 'sarah.jones', 40),
('Cybersecurity Best Practices', 'Defending against modern cyber threats.', 'mike.wilson', 35);

-- =============================
--  SEMINAR PARTICIPANTS (Per-Seminar Roles)
-- =============================
-- AI Seminar: presenters and participants
INSERT INTO seminar_participants (seminar_id, user_username, role)
VALUES
((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), 'john.smith', 'PRESENTER'),
((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), 'alice.johnson', 'PRESENTER'),
((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), 'bob.brown', 'PARTICIPANT'),
((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), 'charlie.davis', 'PARTICIPANT');

-- Blockchain Seminar participants
INSERT INTO seminar_participants (seminar_id, user_username, role)
VALUES
((SELECT seminar_id FROM seminars WHERE seminar_name = 'Blockchain Technology and Cryptocurrency'), 'sarah.jones', 'PRESENTER'),
((SELECT seminar_id FROM seminars WHERE seminar_name = 'Blockchain Technology and Cryptocurrency'), 'diana.wilson', 'PARTICIPANT'),
((SELECT seminar_id FROM seminars WHERE seminar_name = 'Blockchain Technology and Cryptocurrency'), 'eve.garcia', 'PARTICIPANT');

-- Cybersecurity Seminar participants
INSERT INTO seminar_participants (seminar_id, user_username, role)
VALUES
((SELECT seminar_id FROM seminars WHERE seminar_name = 'Cybersecurity Best Practices'), 'mike.wilson', 'PRESENTER'),
((SELECT seminar_id FROM seminars WHERE seminar_name = 'Cybersecurity Best Practices'), 'alice.johnson', 'PARTICIPANT'),
((SELECT seminar_id FROM seminars WHERE seminar_name = 'Cybersecurity Best Practices'), 'bob.brown', 'PARTICIPANT');

-- =============================
--  SESSIONS
-- =============================
INSERT INTO sessions (seminar_id, start_time, end_time, status, location)
VALUES
((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), '2025-02-10 10:00:00', '2025-02-10 11:30:00', 'OPEN', 'Building 37 Room 201'),
((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), '2025-02-17 10:00:00', '2025-02-17 11:30:00', 'CLOSED', 'Building 37 Room 201'),
((SELECT seminar_id FROM seminars WHERE seminar_name = 'Blockchain Technology and Cryptocurrency'), '2025-02-12 14:00:00', '2025-02-12 15:30:00', 'CLOSED', 'Auditorium B'),
((SELECT seminar_id FROM seminars WHERE seminar_name = 'Cybersecurity Best Practices'), '2025-02-15 09:00:00', '2025-02-15 10:30:00', 'CLOSED', 'Cyber Lab');

-- =============================
--  ATTENDANCE
-- =============================
INSERT INTO attendance (session_id, student_username, attendance_time, method, request_status)
VALUES
((SELECT session_id FROM sessions WHERE seminar_id = (SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare') AND start_time = '2025-02-10 10:00:00'), 'alice.johnson', '2025-02-10 10:05:00', 'QR_SCAN', 'CONFIRMED'),
((SELECT session_id FROM sessions WHERE seminar_id = (SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare') AND start_time = '2025-02-10 10:00:00'), 'bob.brown', '2025-02-10 10:07:00', 'QR_SCAN', 'CONFIRMED'),
((SELECT session_id FROM sessions WHERE seminar_id = (SELECT seminar_id FROM seminars WHERE seminar_name = 'Blockchain Technology and Cryptocurrency') AND start_time = '2025-02-12 14:00:00'), 'diana.wilson', '2025-02-12 14:10:00', 'MANUAL', 'CONFIRMED'),
((SELECT session_id FROM sessions WHERE seminar_id = (SELECT seminar_id FROM seminars WHERE seminar_name = 'Cybersecurity Best Practices') AND start_time = '2025-02-15 09:00:00'), 'eve.garcia', '2025-02-15 09:02:00', 'QR_SCAN', 'CONFIRMED');

-- =============================
--  SEMINAR SLOTS (FIXED SCHEDULE)
-- =============================
INSERT INTO seminar_slot (semester_label, slot_date, start_time, end_time, building, room, capacity, status, attendance_opened_by)
VALUES
('SEM A', DATE(NOW()), '10:00:00', '12:00:00', '37', '201', 2, 'FREE', NULL),
('SEM A', DATE(NOW() + INTERVAL 1 DAY), '14:00:00', '16:00:00', '37', '201', 2, 'FREE', NULL);

INSERT INTO seminar_slot_registration (slot_id, presenter_username, degree, topic, supervisor_name, supervisor_email)
VALUES
((SELECT slot_id FROM seminar_slot ORDER BY slot_id LIMIT 1), 'john.smith', 'PhD', 'AI Safety Overview', 'Prof. Ada Lovelace', 'ada.lovelace@university.edu');

-- =============================
--  APP LOGS
-- =============================
INSERT INTO app_logs (log_timestamp, level, tag, message, user_username, user_role, device_info, app_version)
VALUES
(NOW(), 'INFO', 'API_REQUEST', 'Mobile client fetched presenter home.', 'alice.johnson', 'PARTICIPANT', 'Pixel 7', '1.3.0'),
(NOW(), 'WARN', 'ATTENDANCE', 'Duplicate scan prevented.', 'alice.johnson', 'PARTICIPANT', 'Server', '1.3.0');

