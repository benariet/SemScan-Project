-- Automatically find and kill stuck transactions on slot_registration table
-- This script will identify stuck transactions and kill them automatically

-- Step 1: Find stuck transactions (Sleep processes running for more than 50 seconds)
-- These are likely holding locks
SELECT 
    CONCAT('KILL ', ID, ';') as kill_command,
    ID,
    USER,
    HOST,
    DB,
    COMMAND,
    TIME,
    STATE,
    LEFT(INFO, 100) as QUERY_PREVIEW
FROM information_schema.PROCESSLIST
WHERE (COMMAND = 'Sleep' AND TIME > 50)
   OR (COMMAND != 'Sleep' AND TIME > 30)
ORDER BY TIME DESC;

-- Step 2: Find blocking transactions from InnoDB
-- These are transactions that are blocking others
SELECT 
    CONCAT('KILL ', b.trx_mysql_thread_id, ';') as kill_command,
    b.trx_mysql_thread_id as blocking_thread_id,
    b.trx_id as blocking_trx_id,
    b.trx_started,
    TIMESTAMPDIFF(SECOND, b.trx_started, NOW()) as transaction_age_seconds,
    b.trx_query as blocking_query,
    r.trx_mysql_thread_id as waiting_thread_id,
    r.trx_query as waiting_query
FROM information_schema.innodb_lock_waits w
INNER JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id
INNER JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id;

-- Step 3: Find all long-running InnoDB transactions
SELECT 
    CONCAT('KILL ', trx_mysql_thread_id, ';') as kill_command,
    trx_mysql_thread_id,
    trx_id,
    trx_state,
    trx_started,
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) as transaction_age_seconds,
    trx_wait_started,
    TIMESTAMPDIFF(SECOND, trx_wait_started, NOW()) as wait_seconds,
    LEFT(trx_query, 100) as query_preview,
    trx_tables_locked,
    trx_rows_locked
FROM information_schema.innodb_trx
WHERE TIMESTAMPDIFF(SECOND, trx_started, NOW()) > 30
ORDER BY trx_started;

-- Step 4: AUTO-KILL - Execute the kill commands
-- WARNING: This will kill all stuck transactions automatically
-- Uncomment the section below to enable auto-kill

/*
-- Create temporary table to store thread IDs to kill
CREATE TEMPORARY TABLE IF NOT EXISTS threads_to_kill (
    thread_id INT PRIMARY KEY,
    reason VARCHAR(255)
);

-- Insert stuck Sleep processes
INSERT INTO threads_to_kill (thread_id, reason)
SELECT 
    ID,
    CONCAT('Sleep process running for ', TIME, ' seconds')
FROM information_schema.PROCESSLIST
WHERE COMMAND = 'Sleep' AND TIME > 50
ON DUPLICATE KEY UPDATE reason = VALUES(reason);

-- Insert blocking transactions
INSERT INTO threads_to_kill (thread_id, reason)
SELECT DISTINCT
    b.trx_mysql_thread_id,
    CONCAT('Blocking transaction started ', TIMESTAMPDIFF(SECOND, b.trx_started, NOW()), ' seconds ago')
FROM information_schema.innodb_lock_waits w
INNER JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id
ON DUPLICATE KEY UPDATE reason = VALUES(reason);

-- Insert long-running transactions
INSERT INTO threads_to_kill (thread_id, reason)
SELECT 
    trx_mysql_thread_id,
    CONCAT('Long-running transaction: ', TIMESTAMPDIFF(SECOND, trx_started, NOW()), ' seconds')
FROM information_schema.innodb_trx
WHERE TIMESTAMPDIFF(SECOND, trx_started, NOW()) > 30
ON DUPLICATE KEY UPDATE reason = VALUES(reason);

-- Show what will be killed
SELECT 
    thread_id,
    reason,
    CONCAT('KILL ', thread_id, ';') as kill_command
FROM threads_to_kill
ORDER BY thread_id;

-- Execute kill commands (requires prepared statements or manual execution)
-- Note: MySQL doesn't support dynamic KILL in a single query easily
-- You'll need to execute each KILL command manually or use a stored procedure
*/

-- Step 5: Manual execution - Copy and run the KILL commands from Step 1, 2, or 3 above
-- Or use this stored procedure approach:

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS kill_stuck_transactions()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE thread_id INT;
    DECLARE kill_cmd VARCHAR(100);
    
    -- Cursor for stuck processes
    DECLARE cur CURSOR FOR 
        SELECT ID 
        FROM information_schema.PROCESSLIST
        WHERE (COMMAND = 'Sleep' AND TIME > 50)
           OR (COMMAND != 'Sleep' AND TIME > 30);
    
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
    OPEN cur;
    
    read_loop: LOOP
        FETCH cur INTO thread_id;
        IF done THEN
            LEAVE read_loop;
        END IF;
        
        SET kill_cmd = CONCAT('KILL ', thread_id);
        SET @sql = kill_cmd;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        
        SELECT CONCAT('Killed thread ', thread_id) as result;
    END LOOP;
    
    CLOSE cur;
END$$

DELIMITER ;

-- To use the stored procedure, run:
-- CALL kill_stuck_transactions();

-- To drop the procedure later:
-- DROP PROCEDURE IF EXISTS kill_stuck_transactions;

