SET @slot_id = (SELECT MAX(slot_id) FROM slots);
START TRANSACTION;
DELETE FROM waiting_list_promotions WHERE slot_id = @slot_id;
DELETE FROM waiting_list WHERE slot_id = @slot_id;
DELETE FROM slot_registration WHERE slot_id = @slot_id;
UPDATE slots SET status = 'FREE', attendance_opened_at = NULL, attendance_closes_at = NULL, attendance_opened_by = NULL WHERE slot_id = @slot_id;
COMMIT;

