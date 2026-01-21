# Fix Transaction Timeout Issue

## Problem
The `registerForSlot` method was timing out after 10 seconds with error:
```
org.springframework.orm.jpa.JpaSystemException: transaction timeout expired
```

## Root Cause
The transaction timeout of 10 seconds was too short for all the database operations:
1. Finding the presenter
2. Checking if already registered
3. Loading all user registrations (for limit checks)
4. Loading all slot registrations (for capacity checks)
5. Loading the slot entity
6. Saving the registration
7. Updating slot status
8. Saving the slot

## Solution Applied
✅ **Increased transaction timeout from 10 to 60 seconds** in `PresenterHomeService.registerForSlot()`

This gives enough time for:
- Multiple database queries
- Potential database locks
- Network latency
- Database logging (though it's async)

## Additional Recommendations

### 1. Verify Database Indexes
Ensure these indexes exist for optimal performance:
```sql
-- Check existing indexes
SHOW INDEXES FROM slot_registration;

-- Should have:
-- idx_slot_registration_presenter ON (presenter_username)
-- idx_slot_registration_approval_status ON (approval_status)
-- idx_slot_registration_approval_token ON (approval_token)
-- PRIMARY KEY ON (slot_id, presenter_username)

-- If missing, add index on slot_id for faster findByIdSlotId queries:
CREATE INDEX IF NOT EXISTS idx_slot_registration_slot_id ON slot_registration(slot_id);
```

### 2. Monitor Transaction Duration
Check application logs for transaction duration warnings:
```bash
grep -i "transaction.*timeout\|slow.*transaction" logs/semscan-api.log
```

### 3. Database Connection Pool
If timeouts persist, check database connection pool settings:
```properties
# In application.properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
```

### 4. Query Optimization
If the issue persists, consider:
- Using `@Query` with specific indexes hints
- Breaking down the transaction into smaller chunks
- Using database views for complex queries

## Testing
After applying the fix:
1. Test registration with a user who has many existing registrations
2. Test registration when slot has many existing registrations
3. Monitor transaction duration in logs
4. Check for any remaining timeout errors

## Related Issue: "Registration not found for this token"
If you're seeing "Registration not found for this token", it's likely because:
1. The registration transaction timed out before completing
2. The approval token wasn't generated
3. You're using an incorrect/expired token

**Solution**: After fixing the timeout, try registering again. The registration should complete successfully and generate a valid approval token.
