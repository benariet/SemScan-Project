-- =============================================
-- EXACT SQL QUERIES FROM API ENDPOINTS
-- =============================================
-- BASIC TABLE QUERIES
SELECT *
FROM   users;

SELECT *
FROM   seminars;

SELECT *
FROM   sessions;

SELECT *
FROM   attendance;

SELECT *
FROM   presenter_seminar;

SELECT *
FROM   presenter_seminar_slot;

SELECT *
FROM   app_logs;

SELECT *
FROM   log_analytics;

-- USER REPOSITORY QUERIES (AuthenticationService)
SELECT *
FROM   users
WHERE  user_username = 'USERID-10001-20250122';

SELECT *
FROM   users
WHERE  email = 'dr.john.smith@university.edu';

SELECT *
FROM   users
WHERE  student_username = 'STU001';

SELECT *
FROM   users
WHERE  role = 'PRESENTER';



-- SEMINAR REPOSITORY QUERIES (SeminarService)
SELECT *
FROM   seminars
WHERE  seminar_id = 'SEMINR-10001-20250122';

SELECT *
FROM   seminars
WHERE  seminar_code = 'AI-HLTH-001';

SELECT *
FROM   seminars
WHERE  presenter_username = 'USERID-10001-20250122';


SELECT s.*,
       u.first_name,
       u.last_name,
       u.email
FROM   seminars s
       JOIN users u
         ON s.presenter_username = u.user_username
WHERE  u.role = 'PRESENTER';

-- SESSION REPOSITORY QUERIES (SessionService)
SELECT *
FROM   sessions
WHERE  session_id = 'SESSIN-10001-20250122';

SELECT *
FROM   sessions
WHERE  seminar_id = 'SEMINR-10001-20250122';

SELECT *
FROM   sessions
WHERE  status = 'OPEN';

SELECT *
FROM   sessions
WHERE  status = 'CLOSED';

SELECT *
FROM   sessions
WHERE  seminar_id = 'SEMINR-10001-20250122'
       AND status = 'OPEN';

SELECT Count(*)
FROM   sessions
WHERE  seminar_id = 'SEMINR-10001-20250122';

SELECT Count(*)
FROM   sessions
WHERE  status = 'OPEN';

-- ATTENDANCE REPOSITORY QUERIES (AttendanceService)
SELECT *
FROM   attendance
WHERE  attendance_id = 'ATTEND-10001-20250122';

SELECT *
FROM   attendance
WHERE  session_id = 'SESSIN-10001-20250122';

SELECT *
FROM   attendance
WHERE  student_username = 'USERID-10005-20250122';

SELECT *
FROM   attendance
WHERE  session_id = 'SESSIN-10001-20250122'
       AND student_username = 'USERID-10005-20250122';

SELECT Count(*) > 0
FROM   attendance
WHERE  session_id = 'SESSIN-10001-20250122'
       AND student_username = 'USERID-10005-20250122';

SELECT *
FROM   attendance
WHERE  method = 'QR_SCAN';

SELECT *
FROM   attendance
WHERE  session_id = 'SESSIN-10001-20250122'
       AND method = 'QR_SCAN';

SELECT Count(*)
FROM   attendance
WHERE  session_id = 'SESSIN-10001-20250122';

SELECT Count(*)
FROM   attendance
WHERE  student_username = 'USERID-10005-20250122';

SELECT Count(*)
FROM   attendance
WHERE  method = 'QR_SCAN';

-- APP LOG REPOSITORY QUERIES (AppLogService)
SELECT *
FROM   app_logs
WHERE  level = 'ERROR';

SELECT *
FROM   app_logs
WHERE  tag = 'LOGIN';

SELECT *
FROM   app_logs
WHERE  user_username = 'USERID-10005-20250122';

SELECT *
FROM   app_logs
WHERE  user_role = 'STUDENT';

SELECT *
FROM   app_logs
WHERE  level = 'ERROR'
ORDER  BY timestamp DESC;

SELECT *
FROM   app_logs
WHERE  level = 'ERROR'
       AND tag = 'NETWORK';

SELECT *
FROM   app_logs
WHERE  user_username = 'USERID-10005-20250122'
       AND level = 'ERROR';

SELECT level,
       Count(*)
FROM   app_logs
GROUP  BY level;

SELECT tag,
       Count(*)
FROM   app_logs
GROUP  BY tag
ORDER  BY Count(*) DESC;

SELECT *
FROM   app_logs
ORDER  BY timestamp DESC
LIMIT  100;

SELECT *
FROM   app_logs
WHERE  app_version = '1.2.3';

SELECT *
FROM   app_logs
WHERE  device_info LIKE '%Android%';

SELECT *
FROM   app_logs
WHERE  exception_type IS NOT NULL
ORDER  BY timestamp DESC;

SELECT Count(*)
FROM   app_logs;

SELECT Count(*)
FROM   app_logs
WHERE  level = 'ERROR';

DELETE FROM app_logs
WHERE  created_at < '2024-09-01 00:00:00';

-- PRESENTER SEMINAR REPOSITORY QUERIES (PresenterSeminarService)
SELECT *
FROM   presenter_seminar
WHERE  presenter_username = 'USERID-10001-20250122'
ORDER  BY created_at DESC;

SELECT *
FROM   presenter_seminar_slot
WHERE  presenter_seminar_id = 'PRESEM-10001-20250122'
ORDER  BY weekday ASC,
          start_hour ASC;

SELECT Count(*) > 0
FROM   presenter_seminar_slot
WHERE  presenter_seminar_id = 'PRESEM-10001-20250122'
       AND weekday = 1
       AND start_hour = 10
       AND end_hour = 11;

-- INSERT/UPDATE/DELETE OPERATIONS


UPDATE sessions
SET    status = 'CLOSED'
WHERE  session_id = 'SESSIN-10001-20250122';

UPDATE sessions
SET    status = 'OPEN'
WHERE  session_id = 'SESSIN-10001-20250122';

UPDATE sessions
SET    end_time = '2024-09-18 11:00:00'
WHERE  session_id = 'SESSIN-10001-20250122';

UPDATE attendance
SET    method = 'MANUAL'
WHERE  attendance_id = 'ATTEND-10001-20250122';

UPDATE users
SET    first_name = 'Dr. John Updated'
WHERE  user_username = 'USERID-10001-20250122';

UPDATE users
SET    last_name = 'Smith Updated'
WHERE  user_username = 'USERID-10001-20250122';

UPDATE users
SET    email = 'dr.john.updated@university.edu'
WHERE  user_username = 'USERID-10001-20250122';

UPDATE seminars
SET    seminar_name = 'AI and Machine Learning in Healthcare Updated'
WHERE  seminar_id = 'SEMINR-10001-20250122';

UPDATE seminars
SET    description = 'Updated description for AI applications'
WHERE  seminar_id = 'SEMINR-10001-20250122';

UPDATE presenter_seminar
SET    seminar_name = 'AI Healthcare Updated'
WHERE  presenter_seminar_id = 'PRESEM-10001-20250122';

UPDATE presenter_seminar_slot
SET    start_hour = 11,
       end_hour = 12
WHERE  presenter_seminar_slot_id = 'PRESLT-10001-20250122';

UPDATE presenter_seminar_slot
SET    weekday = 2
WHERE  presenter_seminar_slot_id = 'PRESLT-10001-20250122';

DELETE FROM sessions
WHERE  session_id = 'SESSIN-10001-20250122';

DELETE FROM attendance
WHERE  attendance_id = 'ATTEND-10001-20250122';

DELETE FROM seminars
WHERE  seminar_id = 'SEMINR-10001-20250122';

DELETE FROM presenter_seminar
WHERE  presenter_seminar_id = 'PRESEM-10001-20250122';

DELETE FROM presenter_seminar_slot
WHERE  presenter_seminar_slot_id = 'PRESLT-10001-20250122';

DELETE FROM users
WHERE  user_username = 'USERID-10001-20250122';

DELETE FROM app_logs
WHERE  id = 1; 


INSERT INTO sessions
            (session_id,
             seminar_id,
             start_time,
             status)
VALUES      ('SESSIN-10001-20250122',
             'SEMINR-10001-20250122',
             '2024-09-18 10:00:00',
             'OPEN');

INSERT INTO seminars
            (seminar_id,
             seminar_name,
             seminar_code,
             description,
             presenter_username)
VALUES      ('SEMINR-10001-20250122',
             'AI Healthcare',
             'AI-HLTH-001',
             'AI applications',
             1);

INSERT INTO attendance
            (attendance_id,
             session_id,
             student_username,
             attendance_time,
             method,
             request_status)
VALUES      ('ATTEND-10001-20250122',
             'SESSIN-10001-20250122',
             'USERID-10005-20250122',
             '2024-09-18 10:05:00',
             'QR_SCAN',
             'CONFIRMED');

INSERT INTO presenter_seminar
            (presenter_seminar_id,
             presenter_username,
             seminar_name,
             created_at)
VALUES      ('PRESEM-10001-20250122',
             'USERID-10001-20250122',
             'AI Healthcare',
             '2024-09-18 10:00:00');

INSERT INTO presenter_seminar_slot
            (presenter_seminar_slot_id,
             presenter_seminar_id,
             weekday,
             start_hour,
             end_hour)
VALUES      ('PRESLT-10001-20250122',
             'PRESEM-10001-20250122',
             1,
             10,
             11);

INSERT INTO app_logs
            (timestamp,
             level,
             tag,
             message,
             user_username,
             user_role,
             device_info,
             app_version,
             exception_type)
VALUES      (1695123456789,
             'INFO',
             'LOGIN',
             'User logged in successfully',
             5,
             'STUDENT',
             'Android 13, Samsung Galaxy S21',
             '1.2.3',
             NULL);
