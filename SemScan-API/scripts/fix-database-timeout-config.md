# Fix Database Timeout Configuration

## Problem
Transaction timeouts occurring even with minimal load (single user, single query). This indicates a **configuration issue** rather than a performance problem.

## Root Causes

### 1. Missing HikariCP Connection Pool Configuration
The application is using default HikariCP settings which may have low timeouts.

### 2. Missing Hibernate Query Timeout
No explicit query timeout configured, so queries can hang indefinitely.

### 3. MySQL Server-Side Timeouts
MySQL server may have low timeout settings that cause connections to drop.

## Solutions Applied

### ✅ 1. Added HikariCP Connection Pool Configuration
Added to `application.properties`:
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.leak-detection-threshold=60000
spring.datasource.hikari.connection-test-query=SELECT 1
```

### ✅ 2. Added Hibernate Query Timeout
Added to `application.properties`:
```properties
spring.jpa.properties.javax.persistence.query.timeout=60000
spring.jpa.properties.hibernate.query.timeout=60
```

### ✅ 3. Check MySQL Server Timeouts
Run `scripts/check-database-timeouts.sql` to check MySQL server settings.

## Verification Steps

### 1. Restart Application
After updating `application.properties`, restart the Spring Boot application.

### 2. Check Application Logs
Look for HikariCP connection pool initialization:
```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
```

### 3. Check MySQL Server Settings
Run the SQL script to check MySQL timeout settings:
```bash
mysql -u semscan_admin -p semscan_db < scripts/check-database-timeouts.sql
```

### 4. Test Registration
Try registering for a slot again. The transaction should complete successfully.

## If Timeouts Persist

### Check for Database Locks
```sql
-- Check for transactions waiting for locks (MySQL 8.0+ compatible)
SELECT * FROM information_schema.innodb_trx 
WHERE trx_state = 'LOCK WAIT';

-- Check for long-running transactions
SELECT * FROM information_schema.innodb_trx 
WHERE trx_started < DATE_SUB(NOW(), INTERVAL 10 SECOND);

-- Check all active transactions
SELECT trx_id, trx_state, trx_started, trx_query 
FROM information_schema.innodb_trx;
```

### Check Connection Pool Status
Add to `application.properties` for debugging:
```properties
logging.level.com.zaxxer.hikari=DEBUG
```

### Increase MySQL Timeouts (if needed)
If MySQL server timeouts are too low, increase them:
```sql
SET GLOBAL innodb_lock_wait_timeout = 120;
SET GLOBAL wait_timeout = 28800;
```

**Note**: To make these permanent, add to MySQL `my.cnf` configuration file.

## Expected Behavior After Fix

- ✅ Transactions complete within 60 seconds
- ✅ No timeout errors with minimal load
- ✅ Connection pool properly manages connections
- ✅ Queries timeout gracefully if they take too long

## Related Files
- `src/main/resources/application.properties` - Connection pool and query timeout settings
- `scripts/check-database-timeouts.sql` - MySQL timeout diagnostic queries
