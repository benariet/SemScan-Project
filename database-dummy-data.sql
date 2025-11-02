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
--  USERS
-- =============================
INSERT INTO users (email, bgu_username, first_name, last_name, role, is_presenter, is_participant)
VALUES ('dr.john.smith@university.edu', 'jsmith', 'Dr. John', 'Smith', 'PRESENTER', TRUE, TRUE);

INSERT INTO users (email, bgu_username, first_name, last_name, role, is_presenter, is_participant)
VALUES ('prof.sarah.jones@university.edu', 'sjones', 'Prof. Sarah', 'Jones', 'PRESENTER', TRUE, TRUE);

INSERT INTO users (email, bgu_username, first_name, last_name, role, is_presenter, is_participant)
VALUES ('dr.mike.wilson@university.edu', 'mwilson', 'Dr. Mike', 'Wilson', 'PRESENTER', TRUE, TRUE);

INSERT INTO users (email, bgu_username, first_name, last_name, role, is_presenter, is_participant)
VALUES ('dr.anna.brown@university.edu', 'abrown', 'Dr. Anna', 'Brown', 'PRESENTER', TRUE, TRUE);

INSERT INTO users (email, bgu_username, first_name, last_name, role, is_presenter, is_participant)
VALUES ('prof.david.garcia@university.edu', 'dgarcia', 'Prof. David', 'Garcia', 'PRESENTER', TRUE, TRUE);

INSERT INTO users (email, bgu_username, first_name, last_name, role, student_id, is_presenter, is_participant)
VALUES ('alice.johnson@student.edu', 'alice', 'Alice', 'Johnson', 'STUDENT', 'STU001', TRUE, TRUE);

INSERT INTO users (email, bgu_username, first_name, last_name, role, student_id, is_presenter, is_participant)
VALUES ('bob.brown@student.edu', 'bob', 'Bob', 'Brown', 'STUDENT', 'STU002', FALSE, TRUE);

INSERT INTO users (email, bgu_username, first_name, last_name, role, student_id, is_presenter, is_participant)
VALUES ('charlie.davis@student.edu', 'charlie', 'Charlie', 'Davis', 'STUDENT', 'STU003', FALSE, TRUE);

INSERT INTO users (email, bgu_username, first_name, last_name, role, student_id, is_presenter, is_participant)
VALUES ('diana.wilson@student.edu', 'diana', 'Diana', 'Wilson', 'STUDENT', 'STU004', FALSE, TRUE);

INSERT INTO users (email, bgu_username, first_name, last_name, role, student_id, is_presenter, is_participant)
VALUES ('eve.garcia@student.edu', 'eve', 'Eve', 'Garcia', 'STUDENT', 'STU005', FALSE, TRUE);

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
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), '2025-02-10 10:00:00', '2025-02-10 11:30:00', 'OPEN');

INSERT INTO sessions (seminar_id, start_time, end_time, status)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'AI and Machine Learning in Healthcare'), '2025-02-17 10:00:00', '2025-02-17 11:30:00', 'OPEN');

INSERT INTO sessions (seminar_id, start_time, end_time, status)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'Blockchain Technology and Cryptocurrency'), '2025-02-12 14:00:00', '2025-02-12 15:30:00', 'OPEN');

INSERT INTO sessions (seminar_id, start_time, end_time, status)
VALUES ((SELECT seminar_id FROM seminars WHERE seminar_name = 'Cybersecurity Best Practices'), '2025-02-15 09:00:00', '2025-02-15 10:30:00', 'OPEN');

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

-- =============================
--  VERIFICATION QUERIES
-- =============================
SELECT 'Presenters' AS label, COUNT(*) AS total FROM users WHERE role = 'PRESENTER';
SELECT 'Students' AS label, COUNT(*) AS total FROM users WHERE role = 'STUDENT';
SELECT 'Seminars' AS label, COUNT(*) AS total FROM seminars;
SELECT 'Sessions' AS label, COUNT(*) AS total FROM sessions;
SELECT 'Attendance rows' AS label, COUNT(*) AS total FROM attendance;
SELECT 'Presenter seminar slots' AS label, COUNT(*) AS total FROM presenter_seminar_slot;
SELECT 'App logs' AS label, COUNT(*) AS total FROM app_logs;

SELECT 'Seminars with presenters' AS label, s.seminar_name, u.first_name AS presenter
FROM seminars s JOIN users u ON s.presenter_id = u.user_id;

SELECT 'Attendance details' AS label,
       se.start_time,
       su.first_name AS student
FROM attendance a
JOIN sessions se ON a.session_id = se.session_id
JOIN users su ON a.student_id = su.user_id;

SELECT 'Logs summary' AS label, level, COUNT(*) AS total
FROM app_logs
GROUP BY level;

SELECT 'Dummy data loaded successfully.' AS status;
