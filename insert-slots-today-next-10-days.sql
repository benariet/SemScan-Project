-- =============================
--  PRESENTER SEMINAR SLOTS (FIXED SCHEDULE)
--  Today and next 10 days (11 days total)
-- =============================

-- Today
INSERT INTO slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', DATE(NOW()), '00:01:00', '23:59:00', '37', '201', 2, 'FREE');

-- Day 1 (Tomorrow)
INSERT INTO slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', DATE(NOW() + INTERVAL 1 DAY), '00:01:00', '23:59:00', '37', '201', 2, 'FREE');

-- Day 2
INSERT INTO slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', DATE(NOW() + INTERVAL 2 DAY), '00:01:00', '23:59:00', '37', '201', 2, 'FREE');

-- Day 3
INSERT INTO slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', DATE(NOW() + INTERVAL 3 DAY), '00:01:00', '23:59:00', '37', '201', 2, 'FREE');

-- Day 4
INSERT INTO slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', DATE(NOW() + INTERVAL 4 DAY), '00:01:00', '23:59:00', '37', '201', 2, 'FREE');

-- Day 5
INSERT INTO slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', DATE(NOW() + INTERVAL 5 DAY), '00:01:00', '23:59:00', '37', '201', 2, 'FREE');

-- Day 6
INSERT INTO slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', DATE(NOW() + INTERVAL 6 DAY), '00:01:00', '23:59:00', '37', '201', 2, 'FREE');

-- Day 7
INSERT INTO slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', DATE(NOW() + INTERVAL 7 DAY), '00:01:00', '23:59:00', '37', '201', 2, 'FREE');

-- Day 8
INSERT INTO slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', DATE(NOW() + INTERVAL 8 DAY), '00:01:00', '23:59:00', '37', '201', 2, 'FREE');

-- Day 9
INSERT INTO slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', DATE(NOW() + INTERVAL 9 DAY), '00:01:00', '23:59:00', '37', '201', 2, 'FREE');

-- Day 10
INSERT INTO slots (semester_label, slot_date, start_time, end_time, building, room, capacity, status)
VALUES ('SEM A', DATE(NOW() + INTERVAL 10 DAY), '00:01:00', '23:59:00', '37', '201', 2, 'FREE');

