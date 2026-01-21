-- Kill the stuck transaction
-- This will rollback the transaction and release all locks

-- Kill thread 28 (the one with the stuck transaction)
KILL 28;

-- Verify it's been killed
SHOW PROCESSLIST;

-- Check for any remaining active transactions
SELECT * FROM information_schema.innodb_trx;

