# Sanity Tests - Quick Verification Guide

This document provides quick sanity checks to verify that the integration script ran successfully and the system is functioning correctly.

## Prerequisites

- Database integration script (`integration-app-config.sql`) has been executed
- Spring Boot application is running
- Database connection is working

## Quick Test Checklist

### ✅ Database Tests (SQL)

Run the SQL queries in `scripts/sanity-tests.sql`:

1. **Tables Exist**: Verify `app_config` and `waiting_list_promotions` tables exist
2. **Table Structure**: Verify columns match expected schema
3. **Default Values**: Verify all default config values were inserted
4. **Indexes**: Verify all indexes were created
5. **Foreign Keys**: Verify foreign key constraints exist
6. **Data Integrity**: Check for NULL values in required fields

**Quick SQL Check:**
```sql
-- Quick count check
SELECT COUNT(*) AS app_config_count FROM app_config;
SELECT COUNT(*) AS waiting_list_promotions_count FROM waiting_list_promotions;

-- Should return ~20+ rows for app_config, 0+ for waiting_list_promotions
```

### ✅ API Tests (HTTP)

Run the HTTP requests in `scripts/sanity-tests-api.http`:

1. **Health Check**: `GET /api/v1/health` → Should return 200
2. **Mobile Config**: `GET /api/v1/config/mobile` → Should return all MOBILE and BOTH configs
3. **All Config**: `GET /api/v1/config/all` → Should return all configs
4. **Config by Key**: `GET /api/v1/config/waiting_list_approval_window_hours` → Should return "24"
5. **Config by Category**: `GET /api/v1/config/category/EMAIL` → Should return email configs
6. **Config by Target**: `GET /api/v1/config/target/BOTH` → Should return BOTH configs

**Quick API Check:**
```bash
# Using curl
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/api/v1/config/mobile
curl http://localhost:8080/api/v1/config/waiting_list_approval_window_hours
```

### ✅ Functional Tests

#### 1. Config Service Test
- **Action**: Call `GET /api/v1/config/mobile`
- **Expected**: Returns JSON with all mobile configs including:
  - `waiting_list_approval_window_hours: 24`
  - `email_from_name: "SemScan System"`
  - `email_reply_to: "noreply@bgu.ac.il"`
  - All MOBILE-specific configs

#### 2. Waiting List Promotion Test
- **Prerequisites**: 
  - A slot with capacity = 1
  - One approved registration
  - One person on waiting list
- **Action**: Cancel the approved registration
- **Expected**: 
  - Next person on waiting list gets promoted
  - `WaitingListPromotion` record created with status=PENDING
  - Email sent to promoted student
  - Registration created with status=PENDING

#### 3. Time Window Test
- **Action**: Try to open attendance for a slot
- **Expected**: 
  - Uses `presenter_slot_open_window_before_minutes` from `app_config` (default: 30)
  - Uses `presenter_slot_open_window_after_minutes` from `app_config` (default: 15)
  - Error messages include configurable window values

#### 4. Email Configuration Test
- **Action**: Check email status endpoint
- **Expected**: 
  - `GET /api/v1/mail/status` returns configured email settings
  - Email sending uses `email_from_name`, `email_reply_to`, `email_bcc_list` from `app_config`

## Common Issues & Solutions

### Issue: "Table doesn't exist"
**Solution**: Run `integration-app-config.sql` or `integration-app-config-safe.sql`

### Issue: "Index already exists" error
**Solution**: Use `integration-app-config-safe.sql` which checks for existing indexes

### Issue: "Config values not found"
**Solution**: 
1. Check `app_config` table has data: `SELECT COUNT(*) FROM app_config;`
2. If empty, run the INSERT statements from the integration script
3. Verify `target_system` values are correct ('MOBILE', 'API', or 'BOTH')

### Issue: "API returns 404 for /config endpoints"
**Solution**: 
1. Verify `AppConfigController` is registered
2. Check application logs for startup errors
3. Verify Spring Boot application started successfully

### Issue: "Config values are null"
**Solution**: 
1. Check `app_config` table: `SELECT * FROM app_config WHERE config_key = '...';`
2. Verify `AppConfigService` is reading from database (check logs for `APP_CONFIG_READ` tags)
3. Check for fallback to defaults (look for `GLOBAL_CONFIG_FALLBACK` in logs)

## Expected Results

### Database
- ✅ `app_config` table exists with ~20+ rows
- ✅ `waiting_list_promotions` table exists (may be empty)
- ✅ All indexes created successfully
- ✅ Foreign keys constraints exist

### API
- ✅ `/api/v1/config/mobile` returns all mobile configs
- ✅ `/api/v1/config/all` returns all configs
- ✅ Config filtering works (by category, target_system, key)
- ✅ Health check returns 200

### Application Logs
- ✅ No errors during startup
- ✅ `APP_CONFIG_READ` logs appear when configs are accessed
- ✅ `GLOBAL_CONFIG_READ_FROM_APP_CONFIG` logs appear (not fallback)

## Next Steps

After passing sanity tests:
1. Test waiting list promotion flow
2. Test email sending with new config values
3. Test time window validations
4. Monitor `app_logs` table for `EMAIL_`, `WAITING_LIST_`, `APP_CONFIG_` tags
