---
name: App Config and Time Windows System
overview: Implement a unified app_config database table for both backend and mobile, with configurable email settings, time windows, and waiting list promotion tracking with 24-hour approval windows.
todos:
  - id: create-app-config-table
    content: Create app_config table migration script with config_key, config_value, config_type, target_system, category, description
    status: pending
  - id: create-waiting-list-promotions-table
    content: Create waiting_list_promotions table with slot_id, presenter_username, registration references, promoted_at, expires_at, status
    status: pending
  - id: create-app-config-entity
    content: Create AppConfig entity with ConfigType and TargetSystem enums
    status: pending
  - id: create-waiting-list-promotion-entity
    content: Create WaitingListPromotion entity with PromotionStatus enum
    status: pending
  - id: create-app-config-repository
    content: Create AppConfigRepository with findByConfigKey, findByTargetSystemIn, findByCategory methods
    status: pending
  - id: create-waiting-list-promotion-repository
    content: Create WaitingListPromotionRepository with findByExpiresAtBeforeAndStatus, findByRegistration methods
    status: pending
  - id: create-app-config-service
    content: Create AppConfigService with getConfigValue, getConfigsForApi, getConfigsForMobile, type conversion, caching
    status: pending
  - id: update-mail-service
    content: Update MailService to use AppConfigService for email.from.name, email.reply.to, email.bcc.list
    status: pending
  - id: update-presenter-home-service
    content: Update PresenterHomeService to read presenter open window from config and create WaitingListPromotion records
    status: pending
  - id: update-manual-attendance-service
    content: Update ManualAttendanceService to read student attendance windows from AppConfigService
    status: pending
  - id: update-registration-approval-service
    content: Update RegistrationApprovalService to check WaitingListPromotion expiry and auto-decline
    status: pending
  - id: update-waiting-list-service
    content: Update WaitingListService to create WaitingListPromotion records when promoting
    status: pending
  - id: create-promotion-expiry-checker
    content: Create WaitingListPromotionExpiryChecker scheduled job to auto-decline expired promotions
    status: pending
  - id: create-mobile-config-controller
    content: Create MobileConfigController with GET /api/v1/config/mobile endpoint
    status: pending
  - id: create-migration-script
    content: Create migration script to insert default config values with target_system=BOTH
    status: pending
  - id: update-global-config
    content: Update GlobalConfig to integrate with AppConfigService with fallback to @Value
    status: pending
  - id: update-application-properties
    content: Update application-global.properties with comments indicating config is now in database
    status: pending
---

# App Config and Time Windows System Implementation Plan

## Overview

Create a unified `app_config` database table that serves both backend API and mobile app, ensuring consistency through a `target_system` column. Implement configurable email settings, time windows for attendance, and waiting list promotion tracking with 24-hour approval windows.

## Database Schema

### 1. Create `app_config` Table

**File**: Create migration script or add to `database-schema-ddl.sql`

```sql
CREATE TABLE app_config (
    config_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    config_type ENUM('STRING', 'INTEGER', 'BOOLEAN', 'JSON') NOT NULL,
    target_system ENUM('MOBILE', 'API', 'BOTH') NOT NULL,
    category VARCHAR(50),
    description TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_config_target_system ON app_config(target_system);
CREATE INDEX idx_app_config_category ON app_config(category);
CREATE INDEX idx_app_config_key ON app_config(config_key);
```

### 2. Create `waiting_list_promotions` Table

**File**: Add to `database-schema-ddl.sql`

```sql
CREATE TABLE waiting_list_promotions (
    promotion_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_id BIGINT NOT NULL,
    presenter_username VARCHAR(50) NOT NULL,
    registration_slot_id BIGINT NOT NULL,
    registration_presenter_username VARCHAR(50) NOT NULL,
    promoted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'DECLINED', 'EXPIRED') NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_promotions_slot FOREIGN KEY (slot_id) REFERENCES slots(slot_id) ON DELETE CASCADE,
    CONSTRAINT fk_promotions_user FOREIGN KEY (presenter_username) REFERENCES users(bgu_username) ON DELETE CASCADE,
    CONSTRAINT fk_promotions_registration FOREIGN KEY (registration_slot_id, registration_presenter_username) 
        REFERENCES slot_registration(slot_id, presenter_username) ON DELETE CASCADE
);

CREATE INDEX idx_promotions_expires_at ON waiting_list_promotions(expires_at);
CREATE INDEX idx_promotions_status ON waiting_list_promotions(status);
```

## Entity Classes

### 3. Create `AppConfig` Entity

**File**: `src/main/java/edu/bgu/semscanapi/entity/AppConfig.java`

- Fields: `configId`, `configKey`, `configValue`, `configType` (enum), `targetSystem` (enum), `category`, `description`, `createdAt`, `updatedAt`
- Enums: `ConfigType` (STRING, INTEGER, BOOLEAN, JSON), `TargetSystem` (MOBILE, API, BOTH)

### 4. Create `WaitingListPromotion` Entity

**File**: `src/main/java/edu/bgu/semscanapi/entity/WaitingListPromotion.java`

- Fields: `promotionId`, `slotId`, `presenterUsername`, `registrationSlotId`, `registrationPresenterUsername`, `promotedAt`, `expiresAt`, `status` (enum)
- Enum: `PromotionStatus` (PENDING, APPROVED, DECLINED, EXPIRED)

## Repositories

### 5. Create `AppConfigRepository`

**File**: `src/main/java/edu/bgu/semscanapi/repository/AppConfigRepository.java`

- Extends `JpaRepository<AppConfig, Long>`
- Methods:
  - `Optional<AppConfig> findByConfigKey(String configKey)`
  - `List<AppConfig> findByTargetSystemIn(List<TargetSystem> targetSystems)`
  - `List<AppConfig> findByCategory(String category)`

### 6. Create `WaitingListPromotionRepository`

**File**: `src/main/java/edu/bgu/semscanapi/repository/WaitingListPromotionRepository.java`

- Extends `JpaRepository<WaitingListPromotion, Long>`
- Methods:
  - `List<WaitingListPromotion> findByExpiresAtBeforeAndStatus(LocalDateTime now, PromotionStatus status)`
  - `Optional<WaitingListPromotion> findByRegistrationSlotIdAndRegistrationPresenterUsername(Long slotId, String username)`

## Service Layer

### 7. Create `AppConfigService`

**File**: `src/main/java/edu/bgu/semscanapi/service/AppConfigService.java`

**Key Methods:**

- `getConfigValue(String key, Class<T> type, T defaultValue)` - Get config with type conversion
- `getConfigsForApi()` - Returns configs where `target_system='API'` or `'BOTH'`
- `getConfigsForMobile()` - Returns configs where `target_system='MOBILE'` or `'BOTH'`
- `getIntegerConfig(String key, int defaultValue)` - Convenience method for integers
- `getStringConfig(String key, String defaultValue)` - Convenience method for strings
- `getBooleanConfig(String key, boolean defaultValue)` - Convenience method for booleans
- `@Cacheable` annotation for caching config values (refresh on update)

**Implementation Notes:**

- Use Spring's `@Cacheable` with cache name `appConfigCache`
- Type conversion: STRING → String, INTEGER → Integer, BOOLEAN → Boolean, JSON → parse as needed
- Fallback to `@Value` defaults if config not found in database

### 8. Update `MailService`

**File**: `src/main/java/edu/bgu/semscanapi/service/MailService.java`

**Changes:**

- Inject `AppConfigService`
- Replace `@Value("${app.email.from-name:SemScan System}")` with `appConfigService.getStringConfig("email.from.name", "SemScan System")`
- Replace `@Value("${app.email.reply-to:revital@bgu.ac.il}")` with `appConfigService.getStringConfig("email.reply.to", "revital@bgu.ac.il")`
- Replace `@Value("${app.email.monitoring-bcc:}")` with `appConfigService.getStringConfig("email.bcc.list", "")`
- Parse BCC list (comma-separated) and add to `MimeMessageHelper.setBcc()`

### 9. Update `PresenterHomeService`

**File**: `src/main/java/edu/bgu/semscanapi/service/PresenterHomeService.java`

**Changes:**

- Inject `AppConfigService`
- In `promoteFromWaitingListAfterCancellation()` method:
  - Read `waiting_list.approval.window.hours` from `AppConfigService` (default: 24)
  - Create `WaitingListPromotion` record with `expiresAt = promotedAt + approvalWindowHours`
  - Save promotion record
- Add validation: Check if presenter can open attendance based on `attendance.presenter.open.window.minutes` config (default: 10 minutes before session start)

### 10. Update `ManualAttendanceService` (or create if doesn't exist)

**File**: `src/main/java/edu/bgu/semscanapi/service/ManualAttendanceService.java` or similar

**Changes:**

- Inject `AppConfigService`
- Read `attendance.student.window.before.minutes` (default: 0)
- Read `attendance.student.window.after.minutes` (default: 15)
- Validate student attendance marking is within these windows

### 11. Update `RegistrationApprovalService`

**File**: `src/main/java/edu/bgu/semscanapi/service/RegistrationApprovalService.java`

**Changes:**

- Inject `AppConfigService` and `WaitingListPromotionRepository`
- When checking for expired approvals, also check `WaitingListPromotion` records
- Auto-decline registrations if `WaitingListPromotion.expiresAt < now` and status is PENDING
- Update promotion status to EXPIRED when auto-declining

### 12. Update `WaitingListService`

**File**: `src/main/java/edu/bgu/semscanapi/service/WaitingListService.java`

**Changes:**

- Inject `WaitingListPromotionRepository` and `AppConfigService`
- When promoting from waiting list, create `WaitingListPromotion` record

## Scheduled Jobs

### 13. Create `WaitingListPromotionExpiryChecker`

**File**: `src/main/java/edu/bgu/semscanapi/scheduled/WaitingListPromotionExpiryChecker.java`

- `@Scheduled(fixedRate = 3600000)` - Run every hour
- Find all `WaitingListPromotion` records where `expiresAt < now` and `status = PENDING`
- Auto-decline the corresponding `SeminarSlotRegistration`
- Update promotion status to EXPIRED
- Log to `app_logs` with tag `WAITING_LIST_PROMOTION_EXPIRED`

### 14. Create `ReminderEmailSender` (Optional - for future)

**File**: `src/main/java/edu/bgu/semscanapi/scheduled/ReminderEmailSender.java`

- Send reminder emails for pending approvals (if needed in future)

## Controller

### 15. Create `MobileConfigController`

**File**: `src/main/java/edu/bgu/semscanapi/controller/MobileConfigController.java`

**Endpoint:**

- `GET /api/v1/config/mobile` (public, no auth required)
- Returns JSON:
```json
{
  "configs": [
    {
      "configKey": "email.from.name",
      "configValue": "SemScan System",
      "configType": "STRING",
      "category": "EMAIL",
      "targetSystem": "BOTH",
      "description": "Email sender name"
    }
  ]
}
```

- Uses `AppConfigService.getConfigsForMobile()`

## Migration Script

### 16. Create Migration Script

**File**: `migration-app-config.sql` or add to existing migration

**Insert default config values:**

```sql
INSERT INTO app_config (config_key, config_value, config_type, target_system, category, description) VALUES
('email.from.name', 'SemScan System', 'STRING', 'BOTH', 'EMAIL', 'Email sender display name'),
('email.reply.to', 'revital@bgu.ac.il', 'STRING', 'BOTH', 'EMAIL', 'Reply-to email address'),
('email.bcc.list', 'benariet@bgu.ac.il', 'STRING', 'BOTH', 'EMAIL', 'Comma-separated BCC recipients'),
('attendance.presenter.open.window.minutes', '10', 'INTEGER', 'BOTH', 'ATTENDANCE', 'Minutes before session start presenter can open'),
('attendance.student.window.before.minutes', '0', 'INTEGER', 'BOTH', 'ATTENDANCE', 'Minutes before session start students can attend'),
('attendance.student.window.after.minutes', '15', 'INTEGER', 'BOTH', 'ATTENDANCE', 'Minutes after session start students can attend'),
('waiting_list.approval.window.hours', '24', 'INTEGER', 'BOTH', 'WAITING_LIST', 'Hours for approval window (24-hour countdown)');
```

## Integration Updates

### 17. Update `GlobalConfig`

**File**: `src/main/java/edu/bgu/semscanapi/config/GlobalConfig.java`

- Inject `AppConfigService`
- For email and time window properties, try `AppConfigService` first, fallback to `@Value` if not found
- This ensures backward compatibility during migration

### 18. Update `application-global.properties`

**File**: `src/main/resources/application-global.properties`

- Add comments indicating that email and time window settings are now managed via `app_config` table
- Keep existing values as fallback defaults

## Testing Checklist

- [ ] `app_config` table created with correct schema
- [ ] `waiting_list_promotions` table created with correct schema
- [ ] Entities, repositories, and services compile
- [ ] `AppConfigService` returns correct values for API and Mobile
- [ ] `MailService` uses config values from database
- [ ] Time window validations work in `PresenterHomeService` and `ManualAttendanceService`
- [ ] `WaitingListPromotion` records created on promotion
- [ ] Scheduled job expires promotions correctly
- [ ] `GET /api/v1/config/mobile` returns correct JSON
- [ ] Migration script inserts default values
- [ ] Fallback to `@Value` works if config not in database

## Files to Create/Modify

**New Files:**

1. `src/main/java/edu/bgu/semscanapi/entity/AppConfig.java`
2. `src/main/java/edu/bgu/semscanapi/entity/WaitingListPromotion.java`
3. `src/main/java/edu/bgu/semscanapi/repository/AppConfigRepository.java`
4. `src/main/java/edu/bgu/semscanapi/repository/WaitingListPromotionRepository.java`
5. `src/main/java/edu/bgu/semscanapi/service/AppConfigService.java`
6. `src/main/java/edu/bgu/semscanapi/scheduled/WaitingListPromotionExpiryChecker.java`
7. `src/main/java/edu/bgu/semscanapi/controller/MobileConfigController.java`
8. `migration-app-config.sql`

**Modified Files:**

1. `database-schema-ddl.sql` - Add table definitions
2. `src/main/java/edu/bgu/semscanapi/service/MailService.java`
3. `src/main/java/edu/bgu/semscanapi/service/PresenterHomeService.java`
4. `src/main/java/edu/bgu/semscanapi/service/RegistrationApprovalService.java`
5. `src/main/java/edu/bgu/semscanapi/service/WaitingListService.java`
6. `src/main/java/edu/bgu/semscanapi/service/ManualAttendanceService.java` (if exists)
7. `src/main/java/edu/bgu/semscanapi/config/GlobalConfig.java`
8. `src/main/resources/application-global.properties` - Add comments