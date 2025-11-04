USE semscan_db;

-- =============================
--  CLEAR EXISTING DATA (optional)
-- =============================
DELETE FROM app_logs;
DELETE FROM presenter_seminar_slot;
DELETE FROM presenter_seminar;
DELETE FROM attendance;
DELETE FROM sessions;
DELETE FROM seminars;
DELETE FROM users;

-- =============================
--  SEMINARS
-- =============================
INSERT INTO seminars (seminar_name, description, presenter_id)
VALUES ('AI and Machine Learning in Healthcare', 'Applications of AI in medicine.', (SELECT user_id FROM users WHERE email = 'dr.john.smith@university.edu'));

INSERT INTO seminars (seminar_name, description, presenter_id)
VALUES ('Blockchain Technology and Cryptocurrency', 'Fundamentals of blockchain and crypto markets.', (SELECT user_id FROM users WHERE email = 'prof.sarah.jones@university.edu'));

INSERT INTO seminars (seminar_name, description, presenter_id)
VALUES ('Cybersecurity Best Practices', 'Defending against modern cyber threats.', (SELECT user_id FROM users WHERE email = 'dr.mike.wilson@university.edu'));

-- =============================
--  SEMINAR PARTICIPANTS (Per-Seminar Roles)
-- =============================
-- AI Seminar: Dr. John Smith is PRESENTER, Alice is PRESENTER, others are STUDENTS
INSERT INTO seminar_participants (seminar_id, user_id, role)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), 
        (SELECT user_id FROM users WHERE email = 'dr.john.smith@university.edu'), 'PRESENTER');

INSERT INTO seminar_participants (seminar_id, user_id, role)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), 
        (SELECT user_id FROM users WHERE email = 'alice.johnson@student.edu'), 'PRESENTER');

INSERT INTO seminar_participants (seminar_id, user_id, role)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), 
        (SELECT user_id FROM users WHERE email = 'bob.brown@student.edu'), 'STUDENT');

INSERT INTO seminar_participants (seminar_id, user_id, role)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), 
        (SELECT user_id FROM users WHERE email = 'charlie.davis@student.edu'), 'STUDENT');

-- Blockchain Seminar: Prof. Sarah Jones is PRESENTER, others are STUDENTS
INSERT INTO seminar_participants (seminar_id, user_id, role)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'Blockchain Technology and Cryptocurrency'), 
        (SELECT user_id FROM users WHERE email = 'prof.sarah.jones@university.edu'), 'PRESENTER');

INSERT INTO seminar_participants (seminar_id, user_id, role)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'Blockchain Technology and Cryptocurrency'), 
        (SELECT user_id FROM users WHERE email = 'diana.wilson@student.edu'), 'STUDENT');

INSERT INTO seminar_participants (seminar_id, user_id, role)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'Blockchain Technology and Cryptocurrency'), 
        (SELECT user_id FROM users WHERE email = 'eve.garcia@student.edu'), 'STUDENT');

-- Cybersecurity Seminar: Dr. Mike Wilson is PRESENTER, Alice is STUDENT
INSERT INTO seminar_participants (seminar_id, user_id, role)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'Cybersecurity Best Practices'), 
        (SELECT user_id FROM users WHERE email = 'dr.mike.wilson@university.edu'), 'PRESENTER');

INSERT INTO seminar_participants (seminar_id, user_id, role)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'Cybersecurity Best Practices'), 
        (SELECT user_id FROM users WHERE email = 'alice.johnson@student.edu'), 'STUDENT');

INSERT INTO seminar_participants (seminar_id, user_id, role)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'Cybersecurity Best Practices'), 
        (SELECT user_id FROM users WHERE email = 'bob.brown@student.edu'), 'STUDENT');

-- =============================
--  SESSIONS
-- =============================
INSERT INTO sessions (seminar_id, start_time, end_time, status)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), '2025-02-10 10:00:00', '2025-02-10 11:30:00', 'CLOSED');

INSERT INTO sessions (seminar_id, start_time, end_time, status)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), '2025-02-17 10:00:00', '2025-02-17 11:30:00', 'CLOSED');

INSERT INTO sessions (seminar_id, start_time, end_time, status)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'Blockchain Technology and Cryptocurrency'), '2025-02-12 14:00:00', '2025-02-12 15:30:00', 'CLOSED');

INSERT INTO sessions (seminar_id, start_time, end_time, status)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'Cybersecurity Best Practices'), '2025-02-15 09:00:00', '2025-02-15 10:30:00', 'CLOSED');

-- =============================
--  ATTENDANCE
-- =============================
INSERT INTO attendance (session_id, student_id, attendance_time, method)
VALUES ((SELECT session_id FROM sessions WHERE seminar_id = (SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare') AND start_time = '2025-02-10 10:00:00'), (SELECT user_id FROM users WHERE student_id = 'STU001'), '2025-02-10 10:05:00', 'QR_SCAN');

INSERT INTO attendance (session_id, student_id, attendance_time, method)
VALUES ((SELECT session_id FROM sessions WHERE seminar_id = (SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare') AND start_time = '2025-02-10 10:00:00'), (SELECT user_id FROM users WHERE student_id = 'STU002'), '2025-02-10 10:07:00', 'QR_SCAN');

INSERT INTO attendance (session_id, student_id, attendance_time, method)
VALUES ((SELECT session_id FROM sessions WHERE seminar_id = (SELECT seminar_id FROM seminars WHERE seminar_name = 'Blockchain Technology and Cryptocurrency') AND start_time = '2025-02-12 14:00:00'), (SELECT user_id FROM users WHERE student_id = 'STU003'), '2025-02-12 14:10:00', 'MANUAL');

INSERT INTO attendance (session_id, student_id, attendance_time, method)
VALUES ((SELECT session_id FROM sessions WHERE seminar_id = (SELECT seminar_id FROM seminars WHERE seminar_name = 'Cybersecurity Best Practices') AND start_time = '2025-02-15 09:00:00'), (SELECT user_id FROM users WHERE student_id = 'STU004'), '2025-02-15 09:02:00', 'QR_SCAN');

-- =============================
--  PRESENTER SEMINAR AVAILABILITY
-- =============================
INSERT INTO presenter_seminar (presenter_id, seminar_id, instance_name, instance_description)
VALUES ((SELECT user_id FROM users WHERE email = 'dr.john.smith@university.edu'),
        (SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'),
        'AI Healthcare Availability',
        'Recurring weekly availability for AI seminar');

INSERT INTO presenter_seminar (presenter_id, seminar_id, instance_name, instance_description)
VALUES ((SELECT user_id FROM users WHERE email = 'prof.sarah.jones@university.edu'),
        (SELECT seminar_id FROM seminars WHERE seminar_name = 'Blockchain Technology and Cryptocurrency'),
        'Blockchain Workshop Availability',
        'Professor Jones weekly slots');

INSERT INTO presenter_seminar (presenter_id, seminar_id, instance_name, instance_description)
VALUES ((SELECT user_id FROM users WHERE email = 'dr.mike.wilson@university.edu'),
        (SELECT seminar_id FROM seminars WHERE seminar_name = 'Cybersecurity Best Practices'),
        'Cybersecurity Availability',
        'Availability for cybersecurity topics');

INSERT INTO presenter_seminar_slot (presenter_seminar_id, weekday, start_hour, end_hour)
VALUES ((SELECT presenter_seminar_id FROM presenter_seminar WHERE presenter_id = (SELECT user_id FROM users WHERE email = 'dr.john.smith@university.edu') AND instance_name = 'AI Healthcare Availability' LIMIT 1), 1, 10, 12);

INSERT INTO presenter_seminar_slot (presenter_seminar_id, weekday, start_hour, end_hour)
VALUES ((SELECT presenter_seminar_id FROM presenter_seminar WHERE presenter_id = (SELECT user_id FROM users WHERE email = 'prof.sarah.jones@university.edu') AND instance_name = 'Blockchain Workshop Availability' LIMIT 1), 3, 14, 16);

INSERT INTO presenter_seminar_slot (presenter_seminar_id, weekday, start_hour, end_hour)
VALUES ((SELECT presenter_seminar_id FROM presenter_seminar WHERE presenter_id = (SELECT user_id FROM users WHERE email = 'dr.mike.wilson@university.edu') AND instance_name = 'Cybersecurity Availability' LIMIT 1), 5, 9, 11);

-- =============================
--  APP LOGS
-- =============================
INSERT INTO app_logs (log_timestamp, level, tag, message, user_id)
VALUES ('2025-02-10 10:04:00', 'INFO', 'LOGIN', 'Alice signed in.', (SELECT user_id FROM users WHERE student_id = 'STU001'));

INSERT INTO app_logs (log_timestamp, level, tag, message, user_id)
VALUES ('2025-02-10 10:05:30', 'INFO', 'ATTENDANCE', 'Alice marked attendance via QR.', (SELECT user_id FROM users WHERE student_id = 'STU001'));

INSERT INTO app_logs (log_timestamp, level, tag, message, user_id)
VALUES ('2025-02-12 14:05:00', 'ERROR', 'NETWORK', 'Connection lost during session.', (SELECT user_id FROM users WHERE student_id = 'STU003'));

INSERT INTO app_logs (log_timestamp, level, tag, message, user_id)
VALUES ('2025-02-15 09:01:00', 'INFO', 'ATTENDANCE', 'Diana checked in via QR.', (SELECT user_id FROM users WHERE student_id = 'STU004'));

