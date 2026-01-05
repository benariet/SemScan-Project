# SemScan Project

## Project Overview
SemScan is a **seminar attendance tracking system** for BGU (Ben-Gurion University). It consists of:
- **SemScan** - Android mobile app for presenters and participants
- **SemScan-API** - Spring Boot backend API

### Key Features
- **Presenter Registration**: PhD/MSc students register for presentation slots with **exclusive slot rules** (see Capacity System below)
- **Supervisor Approval**: Registrations require supervisor email approval via token link
- **Waiting List**: When slots are full, users can join waiting list and get auto-promoted
- **QR Attendance**: Presenters open attendance window, participants scan QR to mark attendance
- **Session Timeout**: 401/403 responses trigger auto-redirect to login
```
## Development Guidelines

### IMPORTANT: Keep It Simple!
**This is a small application for ~20 users maximum.** Do NOT over-engineer:
- No complex performance optimizations unless there's an actual measurable problem
- No premature database indexing or query optimization
- No elaborate caching strategies
- Keep code changes minimal and focused
- Simple fixes only - avoid architectural changes for theoretical "improvements"

If a code review suggests performance improvements, ask: "Is this actually needed for 20 users?" The answer is almost always NO.

## Project Structure

### Android App (`SemScan/`)
```
src/main/java/org/example/semscan/
├── data/api/
│   ├── ApiClient.java          # Retrofit client with interceptors
│   ├── ApiService.java         # API endpoint definitions
│   ├── AuthInterceptor.java    # Session expiry detection (401/403)
│   ├── DeviceInfoInterceptor.java  # Adds X-Device-Info headers
│   └── ApiLoggingInterceptor.java  # Logs to ServerLogger
├── ui/
│   ├── auth/LoginActivity.java
│   ├── teacher/PresenterHomeActivity.java
│   ├── teacher/PresenterSlotsAdapter.java  # Slot cards with colors
│   ├── student/StudentHomeActivity.java
│   ├── BaseActivity.java       # Session expired broadcast receiver
│   └── SettingsActivity.java
└── utils/
    ├── ConfigManager.java      # Fetches config from API
    ├── ServerLogger.java       # Logs to app_logs table
    ├── PreferencesManager.java # Local preferences
    └── ErrorMessageHelper.java # User-friendly error messages
```

### Spring Boot API (`SemScan-API/`)
```
src/main/java/edu/bgu/semscanapi/
├── controller/
│   ├── AuthController.java           # Login, account setup
│   ├── PresenterHomeController.java  # Registration, cancellation
│   ├── WaitingListController.java    # Join/leave waiting list
│   ├── RegistrationApprovalController.java  # Supervisor approval
│   └── AttendanceController.java     # QR scan attendance
├── service/
│   ├── PresenterHomeService.java     # Core registration logic
│   ├── WaitingListService.java       # Waiting list management
│   ├── RegistrationApprovalService.java  # Email approval flow
│   ├── DatabaseLoggerService.java    # Logs to app_logs table
│   ├── EmailQueueService.java        # Async email sending
│   └── AppConfigService.java         # DB-based configuration
├── entity/
│   ├── User.java                     # bgu_username, degree, supervisor
│   ├── SeminarSlot.java              # Date, time, capacity, state
│   ├── SeminarSlotRegistration.java  # Composite key (slotId, username)
│   ├── WaitingListEntry.java
│   ├── AppLog.java                   # Logging entity
│   └── AppConfig.java                # Configuration entity
├── repository/                       # JPA repositories
├── dto/                              # Request/Response DTOs
└── config/
    ├── DeviceInfoInterceptor.java    # Extracts X-Device-Info header
    └── WebMvcConfig.java             # Registers interceptors
```

## Database (MySQL - semscan_db)

### Key Tables
- **users** - User accounts (bgu_username is PK for logs FK)
- **seminar_slots** - Presentation time slots
- **seminar_slot_registrations** - Composite key (slot_id, presenter_username)
- **waiting_list_entries** - Queue for full slots
- **app_logs** - Comprehensive logging table
- **app_config** - Runtime configuration (target_system: API/MOBILE/BOTH)
- **supervisors** - Supervisor contact info

### Database Credentials
- **Host**: localhost:3306
- **Database**: semscan_db
- **User**: semscan_admin
- **Password**: TAL1234

## Logging System

### app_logs Table Columns
- `level` - INFO/WARN/ERROR
- `tag` - Event type (LOGIN_SUCCESS, REGISTRATION_ATTEMPT, etc.)
- `message` - Human-readable description
- `bgu_username` - User who triggered action (FK to users)
- `user_full_name` - User's full name (first + last) for readability
- `device_info` - "Samsung SM-G991B (Android 12, SDK 31)"
- `app_version` - Mobile app version
- `payload` - Request/response details
- `exception_type` - For errors
- `stack_trace` - Full stack trace for errors
- `source` - API or MOBILE

### Log Tag Naming Paradigm

**Format:** `FEATURE_ACTION` or `FEATURE_ACTION_RESULT`

| Component | Description | Examples |
|-----------|-------------|----------|
| **FEATURE** | The domain/module | `AUTH`, `REGISTRATION`, `WAITING_LIST`, `ATTENDANCE`, `EMAIL`, `FCM` |
| **ACTION** | What is happening | `LOGIN`, `REGISTER`, `CANCEL`, `JOIN`, `PROMOTE`, `SEND` |
| **RESULT** (optional) | The outcome | `SUCCESS`, `FAILED`, `ERROR`, `BLOCKED` |

**Examples:**
- `AUTH_LOGIN_SUCCESS` - Authentication feature, login action, success result
- `REGISTRATION_API_REQUEST` - Registration feature, API request action
- `WAITING_LIST_DEGREE_MISMATCH` - Waiting list feature, degree mismatch result
- `ATTENDANCE_OPEN_BLOCKED` - Attendance feature, open action blocked

**API Request/Response Tags:**
- `{FEATURE}_API_REQUEST` - HTTP request for a feature
- `{FEATURE}_API_RESPONSE` - HTTP response for a feature
- Feature is derived from endpoint URL (e.g., `/register` → `REGISTRATION`)

**DO NOT use generic tags like:**
- ❌ `API_REQUEST`, `API_RESPONSE` (too generic)
- ❌ `SemScan-API`, `SemScan-UI` (not descriptive)
- ❌ `ERROR`, `INFO` (use level field instead)

### Key Log Tags

**Authentication:**
- `AUTH_LOGIN_ATTEMPT`, `AUTH_LOGIN_SUCCESS`, `AUTH_LOGIN_FAILED`

**Registration:**
- `SLOT_REGISTRATION_FAILED` (with payload: reason=PHD_EXCLUSIVE, reason=MSC_BLOCKS_PHD, reason=SLOT_FULL)
- `SLOT_REGISTRATION_SUCCESS`
- `SLOT_REGISTRATION_DB_ERROR`, `SLOT_REGISTRATION_ERROR`

**Waiting List:**
- `WAITING_LIST_ADD_FAILED` (with payload: reason=DEGREE_MISMATCH, reason=ALREADY_ON_LIST, reason=LIST_FULL)
- `WAITING_LIST_PROMOTED_AFTER_CANCELLATION`
- `WAITING_LIST_AUTO_PROMOTE_FAILED`
- `WAITING_LIST_DATA_INCONSISTENCY`

**Attendance:**
- `ATTENDANCE_OPEN_FAILED`, `ATTENDANCE_REOPEN_BLOCKED`, `ATTENDANCE_OPEN_BLOCKED`
- `ATTENDANCE_SESSION_NOT_FOUND`, `ATTENDANCE_SESSION_NOT_OPEN`, `ATTENDANCE_WINDOW_CLOSED`
- `ATTENDANCE_DUPLICATE`, `ATTENDANCE_RECORDING_ERROR`

**Manual Attendance:**
- `MANUAL_ATTENDANCE_SESSION_NOT_FOUND`, `MANUAL_ATTENDANCE_OUTSIDE_WINDOW`
- `MANUAL_ATTENDANCE_APPROVE_NOT_FOUND`, `MANUAL_ATTENDANCE_REJECT_NOT_FOUND`

**Registration Approval:**
- `REGISTRATION_APPROVAL_INVALID_TOKEN`, `REGISTRATION_APPROVAL_TOKEN_NOT_FOUND`
- `REGISTRATION_DECLINE_TOKEN_INVALID`, `REGISTRATION_DECLINE_INVALID_STATUS`

**Email:**
- `EMAIL_REGISTRATION_APPROVAL_EMAIL_FAILED`, `EMAIL_SEND_FAILED`, `EMAIL_SEND_ERROR`
- `EMAIL_REGISTRATION_NO_SUPERVISOR_EMAIL`

**FCM Notifications:**
- `FCM_NOTIFICATION_FAILED`

**Session:**
- `SESSION_CREATION_FAILED`, `SESSION_NOT_FOUND`, `SESSION_RETRIEVAL_ERROR`

## Capacity System - PhD/MSc Exclusivity

**IMPORTANT**: PhD and MSc students are **mutually exclusive** in a slot.

### Rules:
1. **PhD takes exclusive slot**: When a PhD registers, no MSc can register (slot is locked)
2. **MSc blocks PhD**: When any MSc is registered, PhD cannot register
3. **3 MSc per slot**: Up to 3 MSc students can share a slot (if no PhD)
4. **Waiting list is "typed"**: First person to join sets the queue type (PhD-only or MSc-only)

### Configuration:
- **PhD weight**: 3 (takes entire slot capacity)
- **MSc weight**: 1
- **Slot capacity**: 3

### Registration Scenarios:
| Slot State | PhD tries to register | MSc tries to register |
|------------|----------------------|----------------------|
| Empty | ✅ Success (takes whole slot) | ✅ Success |
| Has PhD (pending/approved) | ❌ `ALREADY_REGISTERED` | ❌ `SLOT_LOCKED` |
| Has 1 MSc | ❌ `PHD_BLOCKED_BY_MSC` | ✅ Success |
| Has 2 MSc | ❌ `PHD_BLOCKED_BY_MSC` | ✅ Success |
| Has 3 MSc | ❌ `PHD_BLOCKED_BY_MSC` | ❌ `SLOT_FULL` |

### Waiting List Scenarios (First-Sets-Type Queue):
When a PhD has a slot, the waiting list is open but **typed by whoever joins first**:
- If MSc joins first → queue becomes MSc-only
- If PhD joins first → queue becomes PhD-only
- Only same-degree users can join after the first person
- When waiting list empties completely, type resets

| Slot State | WL Empty | WL has MSc first | WL has PhD first |
|------------|----------|------------------|------------------|
| Has PhD | ✅ Anyone can join (sets type) | ✅ MSc only | ✅ PhD only |
| Has MSc | ✅ MSc can join | ✅ MSc only | N/A (PhD blocked) |

**When PhD cancels:**
- If waiting list has MSc → MSc users get promoted (up to 3)
- If waiting list has PhD → PhD gets promoted (takes whole slot)
- Slot "converts" based on who gets promoted

**Example flow:**
```
1. Ron (PhD) has slot → WL empty, open to all
2. Alice (MSc) joins WL → WL type = MSc-only
3. Bob (PhD) tries to join → ❌ Blocked (queue is MSc-only)
4. Carol (MSc) joins → position 2
5. Ron cancels → Alice promoted, Carol promoted
6. WL empty → type resets
```

## Slot Colors (Mobile UI)
- **Green**: Available (0 registrations)
- **Yellow**: Partially filled (has registrations but not full)
- **Red**: Full (approved >= capacity)

## Build Rules

### APK Naming
After building the Android APK, rename `SemScan-debug.apk` to include the version:
```bash
cp "SemScan/build/outputs/apk/debug/SemScan-debug.apk" "SemScan/build/outputs/apk/debug/semscan-1.0.0.apk"
```

## Build Locations
- **APK**: `SemScan/build/outputs/apk/debug/semscan-1.0.0.apk`
- **JAR**: `SemScan-API/build/libs/SemScan-API-1.0.0.jar`

## Deployment

### Remote Server
- **Host**: 132.72.50.53
- **Username**: webmaster
- **Password**: (stored in environment variable SEMSCAN_PASSWORD)
- **Destination**: /opt/semscan-api

### Upload Commands
After building, upload files to server:
```bash
# Upload APK
curl -k -u "webmaster:$SEMSCAN_PASSWORD" -T "SemScan/build/outputs/apk/debug/semscan-1.0.0.apk" "sftp://132.72.50.53/opt/semscan-api/semscan-1.0.0.apk"

# Upload JAR (keep full version name, overwrites existing)
curl -k -u "webmaster:$SEMSCAN_PASSWORD" -T "SemScan-API/build/libs/SemScan-API-1.0.0.jar" "sftp://132.72.50.53/opt/semscan-api/SemScan-API-1.0.0.jar"

# Restart the API service after JAR upload
echo "$SEMSCAN_PASSWORD" | ssh -o StrictHostKeyChecking=no webmaster@132.72.50.53 "echo '$SEMSCAN_PASSWORD' | sudo -S systemctl restart semscan-api"
```

### Note
- Only keep ONE JAR file in /opt/semscan-api. When uploading, the new JAR overwrites the existing one with the same name.
- Always restart the semscan-api service after uploading a new JAR.

## Common Commands

### Build Commands
```bash
# Build API JAR
cd SemScan-API && ./gradlew bootJar

# Build Android APK
cd SemScan && ./gradlew assembleDebug
```

### Check Logs on Server
```bash
# SSH to server
ssh webmaster@132.72.50.53

# Check service status
sudo systemctl status semscan-api

# View recent logs
sudo journalctl -u semscan-api -f

# Query app_logs table
mysql -u semscan_admin -pTAL1234 semscan_db -e "SELECT * FROM app_logs ORDER BY id DESC LIMIT 20;"
```

### Install APK on Connected Devices
```bash
adb devices
for device in $(adb devices | grep device$ | cut -f1); do
  adb -s $device install -r SemScan/build/outputs/apk/debug/semscan-1.0.0.apk
done
```

## API Endpoints

### Authentication
- `POST /api/v1/auth/login` - BGU SOAP authentication
- `POST /api/v1/auth/setup/{username}` - Link supervisor to user

### Presenter Home
- `GET /api/v1/presenters/{username}/home` - Get slots with registration status
- `POST /api/v1/presenters/{username}/home/slots/{slotId}/register` - Register for slot
- `DELETE /api/v1/presenters/{username}/home/slots/{slotId}/register` - Cancel registration
- `POST /api/v1/presenters/{username}/home/slots/{slotId}/attendance/open` - Open QR attendance

### Waiting List
- `POST /api/v1/slots/{slotId}/waiting-list` - Join waiting list
- `DELETE /api/v1/slots/{slotId}/waiting-list?username=xxx` - Leave waiting list

### Approval (Supervisor clicks email link)
- `GET /api/v1/approve/{token}` - Approve registration (returns HTML success page)
- `GET /api/v1/decline/{token}` - Decline registration (returns HTML page)

### Configuration
- `GET /api/v1/config/mobile` - Get mobile app config
- `POST /api/v1/users/{username}/upsert` - Update user profile/settings

## Important Notes

1. **Username Normalization**: All usernames are normalized to lowercase, domain stripped (user@bgu.ac.il -> user)

2. **Foreign Key on app_logs**: `bgu_username` must exist in `users` table before logging

3. **Composite Primary Key**: `seminar_slot_registrations` uses (slot_id, presenter_username)

4. **Async Email**: Emails are queued in `email_queue` table and sent by background job

5. **Device Info Headers**: Mobile app sends `X-Device-Info` and `X-App-Version` headers

6. **Session Expiry**: 401/403 responses broadcast `SESSION_EXPIRED` intent, BaseActivity redirects to login

7. **Config Caching**: AppConfigService uses Spring cache, changes require service restart or cache clear

## Enums Reference

### ApprovalStatus
- `PENDING` - Awaiting supervisor approval
- `APPROVED` - Supervisor approved
- `DECLINED` - Supervisor rejected
- `EXPIRED` - Token expired (72 hours default)

### User.Degree
- `PHD` - PhD student (weight=3, takes exclusive slot)
- `MSC` - MSc student (weight=1)

### SeminarSlot.SlotStatus
- `AVAILABLE` - Open for registration
- `FULL` - At capacity
- `CLOSED` - Registration closed

### Session.SessionStatus
- `OPEN` - Accepting attendance
- `CLOSED` - Attendance window closed

### Attendance.AttendanceMethod
- `QR_CODE` - Scanned QR code
- `MANUAL` - Manual request

### Attendance.RequestStatus
- `PENDING` - Awaiting approval
- `APPROVED` - Approved
- `REJECTED` - Rejected

### EmailQueue.Status
- `PENDING` - Waiting to send
- `SENT` - Successfully sent
- `FAILED` - Send failed

### EmailQueue.EmailType
- `REGISTRATION_APPROVAL` - Supervisor approval email
- `NOTIFICATION` - General notification

### AppLog.Source
- `API` - Server-side log
- `MOBILE` - Mobile app log

### AppLog.UserRole
- `PRESENTER`, `PARTICIPANT`, `BOTH`, `UNKNOWN`

### WaitingListPromotion.PromotionStatus
- `PENDING_APPROVAL` - Waiting for promoted user to respond
- `APPROVED` - User accepted promotion
- `DECLINED` - User declined
- `EXPIRED` - Promotion window expired

## Configuration Keys (app_config table) - ACTUAL VALUES

### Mobile Only (target_system=MOBILE)
| Key | Value | Description |
|-----|-------|-------------|
| `APP_VERSION` | 1.0.0 | Current mobile app version |
| `config_cache_ttl_hours` | 24 | Config cache TTL in hours |
| `connection_timeout_seconds` | 30 | HTTP connection timeout |
| `read_timeout_seconds` | 30 | HTTP read timeout |
| `write_timeout_seconds` | 30 | HTTP write timeout |
| `toast_duration_error` | 10000 | Error toast duration (ms) |
| `toast_duration_success` | 5000 | Success toast duration (ms) |
| `toast_duration_info` | 6000 | Info toast duration (ms) |
| `manual_attendance_window_before_minutes` | 10 | Before session for manual attendance |
| `manual_attendance_window_after_minutes` | 15 | After session for manual attendance |
| `student_attendance_window_before_minutes` | 0 | Before session for student attendance |
| `student_attendance_window_after_minutes` | 10 | After session for student attendance |
| `presenter_slot_open_window_before_minutes` | 0 | Before slot time presenter can open |
| `presenter_slot_open_window_after_minutes` | 999 | After slot time presenter can open |
| `max_export_file_size_mb` | 50 | Max export file size |

### API Only (target_system=API)
| Key | Value | Description |
|-----|-------|-------------|
| `approval_reminder_interval_days` | 2 | Days between supervisor reminders |
| `approval_token_expiry_days` | 14 | Days until approval link expires |
| `email_queue_max_retries` | 3 | Max retry attempts for emails |
| `email_queue_initial_backoff_minutes` | 5 | Initial retry delay |
| `email_queue_backoff_multiplier` | 3 | Backoff multiplier (5→15→45 min) |
| `email_queue_batch_size` | 50 | Emails per batch |
| `email_queue_process_interval_seconds` | 120 | Queue processing interval |
| `expiration_warning_hours_before` | 48 | Hours before expiry to warn |
| `promotion_offer_expiry_hours` | 48 | Hours for promotion offer |

### Shared (target_system=BOTH)
| Key | Value | Description |
|-----|-------|-------------|
| `server_url` | http://132.72.50.53:8080 | API server URL |
| `phd.capacity.weight` | 3 | PhD capacity weight (takes entire slot) |
| `waiting.list.limit.per.slot` | 3 | Max waiting list per slot |
| `waiting_list_approval_window_hours` | 168 | Hours to respond (7 days) |
| `presenter_close_session_duration_minutes` | 15 | Session auto-close duration |
| `email_from_name` | SemScan System | Email sender name |
| `email_reply_to` | noreply@bgu.ac.il | Reply-to address |
| `email_bcc_list` | benariet@bgu.ac.il | BCC recipients |
| `email_domain` | @bgu.ac.il | Email domain suffix |
| `export_email_recipients` | benariet@bgu.ac.il,talbnwork@gmail.com | Export email recipients |
| `support_email` | benariet@bgu.ac.il | Support email |
| `test_email_recipient` | talbnwork@gmail.com | Test email recipient |

## Response Codes

### Registration Responses
- `REGISTERED` - Successfully registered
- `PENDING_APPROVAL` - Awaiting supervisor approval
- `ALREADY_REGISTERED` - Already registered in slot
- `ALREADY_APPROVED` - Already have an approved registration
- `SLOT_FULL` - Slot at capacity
- `SLOT_LOCKED` - Slot reserved by PhD presenter (MSc can't register)
- `PHD_BLOCKED_BY_MSC` - PhD can't register because MSc presenters exist
- `NOT_FOUND` - Slot/user not found
- `UNREGISTERED` - Successfully cancelled

### Waiting List Responses
- `ADDED_TO_WAITING_LIST` - Successfully joined
- `REMOVED_FROM_WAITING_LIST` - Successfully left
- `ALREADY_ON_WAITING_LIST` - Already on list
- `WAITING_LIST_FULL` - List at capacity

### Email Responses
- `EMAIL_SENT` - Successfully sent
- `EMAIL_NOT_CONFIGURED` - Mail service not configured
- `EMAIL_ERROR` - Send failed
- `EMAIL_AUTH_FAILED` - SMTP auth failed

## BGU Authentication

### SOAP Service
- **Endpoint**: `https://w3.bgu.ac.il/BguAuthWebService/AuthenticationProvider.asmx`
- **Action**: `http://tempuri.org/validateUser`
- **Method**: POST with XML SOAP envelope
- **Returns**: Boolean (true if valid credentials)

### Username Handling
- Strip domain: `user@bgu.ac.il` → `user`
- Lowercase: `User` → `user`
- Trim whitespace

## Email System

### Email Templates
- **Registration Approval**: Sent to supervisor with approve/reject links
- **Cancellation Notification**: Sent when user cancels approved slot
- **Waiting List Promotion**: Sent when spot opens up

### Token Links
- Approve: `http://132.72.50.53:8080/api/v1/approve/{token}`
- Decline: `http://132.72.50.53:8080/api/v1/decline/{token}`
- Expiration: 14 days (configurable via `approval_token_expiry_days`)

## Git Branches

### Main Branches
- `main` - Production-ready code
- `feature/*` - New features
- `fix/*` - Bug fixes
- `test/*` - Test-related changes

### Recent Feature Branches
- `feature/session-timeout-error-handling-ux` - Current branch
- `feature/slot-capacity-3` - Slot capacity changes
- `feature/in-app-notifications` - Push notifications

## Testing

### Running Tests
```bash
# API unit tests
cd SemScan-API && ./gradlew test

# Run specific test class
./gradlew test --tests "PresenterHomeServiceTest"
```

### Test Users
| Username | Password | Name | Degree |
|----------|----------|------|--------|
| `talguest2` | `tc2xqVds` | Ron Levy | PhD |
| `testphd1` | `Test123!` | Alex Cohen | PhD |
| `testphd2` | `Test123!` | Maya Levi | PhD |
| `amarrev` | `Revital1990%` | Revital Amar | MSc |
| `benariet` | `Taltal123!` | Tal Ben Arie | MSc |
| `talguest3` | `kbm7Xzfk` | Dana Katz | MSc |
| `talguest4` | `atpgK2zc` | Jhon Smith | MSc |
| `testmsc1` | `Test123!` | Yael Stern | MSc |
| `testmsc2` | `Test123!` | Oren Golan | MSc |
| `testmsc3` | `Test123!` | Noa Shapira | MSc |
| `testmsc4` | `Test123!` | Eitan Peretz | MSc |

**Note:** Users starting with `test` bypass BGU authentication (all use `Test123!`). Others authenticate via BGU SOAP.

## Critical Test Cases

### PhD/MSc Exclusivity Tests (MUST TEST)
PhD and MSc students are mutually exclusive in a slot. These scenarios must be tested:

| Scenario | Setup | Expected Result |
|----------|-------|-----------------|
| PhD registers to empty slot | Empty slot | ✅ Success, slot locked |
| MSc blocked by PhD | Slot has PhD | ❌ `SLOT_LOCKED` |
| PhD blocked by MSc | Slot has any MSc | ❌ `PHD_BLOCKED_BY_MSC` |
| MSc can join MSc | Slot has 1-2 MSc | ✅ Success |
| PhD can't join waiting list with MSc | Slot has MSc | ❌ Blocked (can't be promoted) |

**How to test:**
1. Login as `talguest2` (PhD) - try to register for slot with MSc → Should fail with "slot has MSc presenters"
2. Login as `talguest3` (MSc) - try to register for slot with PhD → Should fail with "slot is reserved by PhD"

### Waiting List First-Sets-Type Queue Tests (MUST TEST)
When a PhD has a slot, the waiting list is open. First person to join sets the queue type.

| Scenario | Setup | Expected Result |
|----------|-------|-----------------|
| PhD joins WL for PhD slot (empty WL) | Slot has PhD, WL empty | ✅ Success, WL type = PhD-only |
| MSc joins WL for PhD slot (empty WL) | Slot has PhD, WL empty | ✅ Success, WL type = MSc-only |
| PhD joins WL when MSc is first | Slot has PhD, WL has MSc | ❌ Blocked (queue is MSc-only) |
| MSc joins WL when PhD is first | Slot has PhD, WL has PhD | ❌ Blocked (queue is PhD-only) |
| Same-degree joins existing WL | WL has same degree | ✅ Success |

**How to test:**
1. Login as `talguest2` (PhD), find slot with another PhD registered
2. Join waiting list → Should succeed (first in queue, sets type to PhD-only)
3. Login as `benariet` (MSc), try to join same waiting list → Should fail "queue is currently PhD-only"
4. Or reverse: MSc joins first, then PhD is blocked

### Registration Limit Tests
| User | Limit | Test |
|------|-------|------|
| MSc | 1 approved | Try registering for 2nd slot after 1st approved |
| PhD | 1 approved | Try registering for 2nd slot after 1st approved |

### API + UI Consistency
When testing API changes, always verify:
1. API returns correct response codes
2. Mobile UI shows appropriate buttons
3. Mobile UI displays correct messages

### Past Slots (MUST TEST)
Slots from past dates should NOT allow registration:

| Scenario | Expected |
|----------|----------|
| Slot date is yesterday | Should NOT show "Register" button |
| Slot date is today (time passed) | Should NOT show "Register" button |
| Slot date is today (time not passed) | CAN show "Register" button |
| Slot date is future | CAN show "Register" button |

**How to test:**
1. Check slot list includes past dates
2. Verify past slots do NOT show "Register Now" button
3. Verify past slots show appropriate message (e.g., "Slot date has passed")

### Declined Registration Re-registration Flow (MUST TEST)
Users with DECLINED registrations should be able to re-register for the same slot.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | User registers for slot | Registration created, status = PENDING |
| 2 | Supervisor clicks decline link | Status → DECLINED |
| 3 | User opens app, views slot | "Register Now" button visible |
| 4 | User clicks Register | New registration created (updates old record) |
| 5 | Check database | Old DECLINED record updated to PENDING |

**How to test:**
1. Login as any test user (e.g., `benariet`)
2. Register for an available slot
3. Check email for supervisor approval link
4. Click the "Decline" link in the email
5. Refresh the app
6. Verify the same slot now shows "Register Now" button
7. Register again - should succeed

**Database verification:**
```sql
SELECT slot_id, presenter_username, approval_status, registered_at
FROM slot_registration WHERE presenter_username = 'benariet';
```

**Bug fixed:** 2026-01-04 - DECLINED registrations were blocking re-registration in UI and API.

### Expired Registration Re-registration Flow
Same as above but for EXPIRED registrations (token expired before supervisor action).

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | User registers for slot | Status = PENDING |
| 2 | Token expires (14 days) or manually set EXPIRED | Status → EXPIRED |
| 3 | User opens app, views slot | "Register Now" button visible |
| 4 | User clicks Register | New registration created |

### Supervisor Approval Flow (MUST TEST)
Complete end-to-end flow from registration to supervisor action.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Presenter registers for slot | Status = PENDING, email queued |
| 2 | Check email_queue table | Email with type REGISTRATION_APPROVAL |
| 3 | Wait for email processing (2 min) | Email sent to supervisor |
| 4 | Supervisor opens email | Contains Approve/Decline links |
| 5a | Click Approve link | HTML success page, status → APPROVED |
| 5b | Click Decline link | HTML page with reason form |
| 6 | If declined with reason | Reason saved in DB |
| 7 | Presenter opens app | Shows "Approved" or "Declined" status |

**How to test:**
1. Login as `talguest3` (MSc)
2. Register for an empty slot
3. Check database: `SELECT * FROM email_queue WHERE email_type = 'REGISTRATION_APPROVAL' ORDER BY id DESC LIMIT 1;`
4. Wait 2 minutes for email processing, or check supervisor's email directly
5. Click Approve/Decline link in email
6. Verify token expires after 14 days (configurable)

**Database verification:**
```sql
SELECT slot_id, presenter_username, approval_status, approval_token,
       supervisor_approved_at, supervisor_declined_at, supervisor_declined_reason
FROM slot_registration WHERE presenter_username = 'talguest3';
```

### Waiting List Promotion Flow (MUST TEST)
When a registration is cancelled, the first person on waiting list gets promoted.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | User A registers for slot (MSc) | Status = PENDING/APPROVED |
| 2 | Slot becomes full | capacity = 3, 3 MSc registered |
| 3 | User B joins waiting list | Position = 1 in waiting_list |
| 4 | User A cancels registration | User A removed from slot |
| 5 | System promotes User B | User B moved to slot_registration |
| 6 | Promotion notification sent | FCM notification to User B |
| 7 | User B sees "Pending Approval" | In app, waiting for supervisor |

**How to test:**
1. Fill a slot with 3 MSc users (use talguest3, talguest4, benariet)
2. Login as another user and join waiting list
3. Cancel one of the registered users
4. Verify waiting list user was promoted automatically
5. Check FCM notification was sent

**Database verification:**
```sql
-- Check waiting list before/after
SELECT * FROM waiting_list WHERE slot_id = X;
-- Check registration after promotion
SELECT * FROM slot_registration WHERE slot_id = X;
-- Check promotion record
SELECT * FROM waiting_list_promotions WHERE slot_id = X;
```

### Cancellation Flow (MUST TEST)
Test cancelling registrations at different approval stages.

| Scenario | Initial Status | Expected Result |
|----------|---------------|-----------------|
| Cancel PENDING registration | PENDING | Record deleted, no email |
| Cancel APPROVED registration | APPROVED | Record deleted, cancellation email sent |
| Cancel when waiting list exists | Any | First waiting list user promoted |
| Cancel only registration in slot | Any | Slot becomes available (green) |

**How to test:**
1. Register for a slot, then cancel before approval → should work silently
2. Get a slot approved, then cancel → cancellation notification sent
3. With waiting list: cancel and verify automatic promotion

### QR Attendance Flow (MUST TEST)
Presenter opens attendance window, participants scan QR code.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Presenter views their registered slot | "Open Attendance" button visible |
| 2 | Presenter clicks Open Attendance | QR code displayed, timer starts |
| 3 | Participant opens app | "Scan QR" option available |
| 4 | Participant scans QR code | Attendance marked, confirmation shown |
| 5 | Timer expires (15 min) | Session auto-closes |
| 6 | Late participant tries to scan | "Session closed" error |

**How to test:**
1. Login as presenter (talguest3) with approved slot
2. Open attendance window on slot's date/time
3. Login as participant (benariet) on different device
4. Scan the QR code
5. Verify attendance record in database

**Database verification:**
```sql
SELECT * FROM attendance WHERE session_id = X;
SELECT * FROM sessions WHERE seminar_id = X;
```

### Manual Attendance Request Flow (MUST TEST)
Participant requests manual attendance (e.g., phone died).

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Participant opens session in app | "Request Manual Attendance" button |
| 2 | Clicks request, enters reason | Request submitted |
| 3 | Presenter sees pending request | In presenter's attendance list |
| 4 | Presenter approves/rejects | Status updated |
| 5 | Participant sees result | Attendance confirmed or rejected |

**Time window:** Manual requests allowed within configured window (default: 10 min before to 15 min after session).

### FCM Push Notification Flow (MUST TEST)
Test push notifications for various events.

| Event | Notification Title | Notification Body | Recipient |
|-------|-------------------|-------------------|-----------|
| Registration approved | "Registration Approved!" | "Your supervisor approved your registration for [date]" | Presenter |
| Registration declined | "Registration Declined" | "Your supervisor declined your registration. Reason: [reason]" | Presenter |
| Promoted from waiting list | "Slot Available!" | "You've been promoted from the waiting list for [date]" | Promoted user |

#### FCM Token Registration Test (MUST TEST)
Test that FCM tokens are registered correctly for new and existing users.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | New user logs in for first time | Redirected to FirstTimeSetupActivity |
| 2 | User completes setup (degree, supervisor) | User record created in `users` table |
| 3 | After setup completes | FCM token registered in `fcm_tokens` table |
| 4 | Check database | Token exists with correct username |

**First-time user flow:**
```
Login → FirstTimeSetupActivity → upsertUser() → registerFcmToken()
```

**Existing user flow:**
```
Login → registerFcmToken() → Home screen
```

**How to test first-time user:**
1. Create a new BGU account or use one that hasn't logged in before
2. Login to the app
3. Complete the first-time setup form
4. Check database for FCM token

**Database verification:**
```sql
-- Check FCM token was registered
SELECT * FROM fcm_tokens WHERE bgu_username = 'newuser';

-- Check user exists (must exist before FCM token)
SELECT * FROM users WHERE bgu_username = 'newuser';
```

**Bug fixed:** 2026-01-04 - FCM token registration was failing for first-time users because it happened before user record was created (FK constraint violation).

#### FCM Approval Notification Test (MUST TEST)
| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | User registers for slot | Registration created, PENDING status |
| 2 | Supervisor clicks approve link | Status → APPROVED |
| 3 | User receives push notification | Title: "Registration Approved!" |
| 4 | Notification contains slot date | Body mentions the slot date |

**How to test:**
1. Login as `talguest3` on Android device
2. Register for an available slot
3. Check supervisor's email for approval link
4. Click approve link
5. Verify notification appears on device within seconds

#### FCM Decline Notification Test (MUST TEST)
| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | User registers for slot | Registration created, PENDING status |
| 2 | Supervisor clicks decline link | Decline form shown |
| 3 | Supervisor enters reason and submits | Status → DECLINED |
| 4 | User receives push notification | Title: "Registration Declined" |
| 5 | Notification shows reason | Body includes decline reason |

#### FCM Waiting List Promotion Test (MUST TEST)
| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Fill slot with 3 MSc users | Slot is full |
| 2 | User B joins waiting list | Position = 1 |
| 3 | User A cancels registration | Slot has opening |
| 4 | System promotes User B | User B moved to registrations |
| 5 | User B receives notification | Title: "Slot Available!" |

**How to test:**
1. Fill a slot (e.g., talguest3, talguest4, benariet)
2. Login as another user (e.g., amarrev) and join waiting list
3. Cancel one of the registered users
4. Verify amarrev receives push notification

**Database verification:**
```sql
-- Check FCM token exists for user
SELECT * FROM fcm_tokens WHERE bgu_username = 'testuser';

-- Check notification was attempted (check server logs)
-- journalctl -u semscan-api | grep "FCM"
```

#### FCM Error Handling Test
| Scenario | Expected Behavior |
|----------|-------------------|
| User has no FCM token | Notification skipped, no error |
| Invalid FCM token | Token removed from database, logged as warning |
| Firebase service unavailable | Logged as error, operation continues |
| User not in database | FCM token not saved (FK constraint) |

**Note:** FCM failures should never block the main operation (approval, promotion, etc.)

### Session Timeout Flow (MUST TEST)
Test that expired sessions redirect to login.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login to app | Session token stored |
| 2 | Wait for token expiry (or revoke server-side) | Token becomes invalid |
| 3 | Perform any API action | 401/403 response |
| 4 | App receives 401/403 | SESSION_EXPIRED broadcast |
| 5 | BaseActivity handles broadcast | Auto-redirect to login |
| 6 | User sees login screen | Message: "Session expired" |

**How to test manually:**
1. Login normally
2. On server, revoke the user's token or wait for expiry
3. Try to refresh slot list
4. Verify app redirects to login with appropriate message

### Waiting List Business Rules (MUST TEST)
Test that waiting list rejections are logged as WARN (not ERROR) since they're expected behavior.

**First-Sets-Type Queue System**: When a PhD-occupied slot has an empty waiting list, the first person to join sets the queue type. Only same-degree users can join after.

| Scenario | Setup | Expected Result | Log Tag (WARN level) |
|----------|-------|-----------------|---------------------|
| PhD joins PhD slot first | PhD slot, empty waiting list | ✅ Success, queue type = PhD | - |
| MSc joins PhD slot first | PhD slot, empty waiting list | ✅ Success, queue type = MSc | - |
| PhD joins after MSc | PhD slot, MSc in position 1 | ❌ Rejected (queue is MSc-only) | `WAITING_LIST_DEGREE_MISMATCH` |
| MSc joins after PhD | PhD slot, PhD in position 1 | ❌ Rejected (queue is PhD-only) | `WAITING_LIST_DEGREE_MISMATCH` |
| Same degree joins | PhD slot, queue type matches | ✅ Success | - |
| Already on this list | User already on slot's waiting list | Rejected as duplicate | `WAITING_LIST_ALREADY_ON_LIST` |
| Already on another list | User on different slot's waiting list | Rejected (1 list max) | `WAITING_LIST_ALREADY_ON_ANOTHER` |
| Has active registration | User has PENDING/APPROVED for slot | Cannot also join waiting list | `WAITING_LIST_HAS_ACTIVE_REGISTRATION` |
| Waiting list full | List at capacity (default: 3) | Rejected | `WAITING_LIST_FULL` |

**How to test first-sets-type queue:**
1. Find slot with PhD registered and **empty** waiting list
2. Login as `talguest3` (MSc) → Join waiting list → Should succeed (sets queue to MSc-only)
3. Login as `talguest2` (PhD) → Try to join same slot → Should fail with "queue is currently MSc-only"
4. Login as `talguest4` (MSc) → Join waiting list → Should succeed (same degree)
5. Have both MSc users cancel → Queue resets
6. Login as `talguest2` (PhD) → Join waiting list → Should succeed (now sets queue to PhD-only)

**Database verification:**
```sql
-- Check recent waiting list rejections are WARN not ERROR
SELECT log_timestamp, level, tag, message, bgu_username
FROM app_logs
WHERE tag LIKE 'WAITING_LIST_%'
ORDER BY log_id DESC LIMIT 10;

-- Check queue type (degree of position 1)
SELECT slot_id, presenter_username, degree, position
FROM waiting_list
WHERE slot_id = X
ORDER BY position ASC;

-- Should see level='WARN' for business rules, level='ERROR' only for true errors
```

**Log levels summary:**
- **WARN**: Business rule rejections (degree mismatch, list full, already on list, etc.)
- **ERROR**: True errors (user not found, database error, null values)

### First-Time User Setup Flow (MUST TEST)
Test the complete flow for a new user logging in for the first time.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | New user enters BGU credentials | Login succeeds via SOAP |
| 2 | System detects first login | Redirected to FirstTimeSetupActivity |
| 3 | User selects degree (MSc/PhD) | Degree stored |
| 4 | User selects/enters supervisor | Supervisor linked |
| 5 | User clicks Save | User record created in `users` table |
| 6 | After save completes | FCM token registered |
| 7 | Redirected to home screen | Shows presenter/participant options |

**How to test:**
1. Use a BGU account that hasn't logged in before
2. Or delete user from database: `DELETE FROM users WHERE bgu_username = 'testuser';`
3. Login and complete setup
4. Verify user record and FCM token created

**Database verification:**
```sql
SELECT * FROM users WHERE bgu_username = 'testuser';
SELECT * FROM fcm_tokens WHERE bgu_username = 'testuser';
```

### Username Normalization Flow (MUST TEST)
Test that usernames are normalized consistently across the system.

| Input | Expected Normalized |
|-------|---------------------|
| `User@bgu.ac.il` | `user` |
| `USER` | `user` |
| `User` | `user` |
| `  user  ` | `user` |
| `USER@BGU.AC.IL` | `user` |

**How to test:**
1. Login with username in different cases (e.g., `BENARIET@bgu.ac.il`)
2. Verify login succeeds and finds existing user record
3. Check logs use normalized username

**Database verification:**
```sql
-- All should return same user
SELECT * FROM users WHERE bgu_username = 'benariet';
```

### Slot Status Transitions (MUST TEST)
Test that slot status (FREE/SEMI/FULL) updates correctly.

| Action | Before Status | After Status |
|--------|---------------|--------------|
| First MSc registers | FREE | SEMI |
| Second MSc registers | SEMI | SEMI |
| Third MSc registers (slot full) | SEMI | FULL |
| One MSc cancels | FULL | SEMI |
| All MSc cancel | SEMI | FREE |
| PhD registers (takes whole slot) | FREE | FULL |

**How to test:**
1. Find empty slot, verify status = FREE
2. Register MSc user, verify status = SEMI
3. Continue registering until full
4. Cancel and verify status decrements

**Database verification:**
```sql
SELECT slot_id, status, capacity FROM slots WHERE slot_id = X;
SELECT COUNT(*) as registrations FROM slot_registration
WHERE slot_id = X AND approval_status IN ('PENDING', 'APPROVED');
```

### One Approved Registration Auto-Cancel (MUST TEST)
When user gets one registration approved, other pending registrations are auto-cancelled.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | User registers for Slot A | Status = PENDING |
| 2 | User registers for Slot B | Status = PENDING |
| 3 | User joins waiting list for Slot C | Position = 1 |
| 4 | Supervisor approves Slot A | Slot A → APPROVED |
| 5 | System auto-cancels others | Slot B deleted, removed from Slot C waiting list |

**How to test:**
1. Register same user for multiple available slots
2. Approve one registration via supervisor link
3. Verify other registrations cancelled

**Database verification:**
```sql
-- Should only show one APPROVED, no PENDING
SELECT * FROM slot_registration WHERE presenter_username = 'testuser';
-- Should be empty
SELECT * FROM waiting_list WHERE presenter_username = 'testuser';
```

### Waiting List Position Management (MUST TEST)
Test that positions stay contiguous when users leave.

| Step | Action | Positions Before | Positions After |
|------|--------|------------------|-----------------|
| 1 | User A joins | - | A=1 |
| 2 | User B joins | A=1 | A=1, B=2 |
| 3 | User C joins | A=1, B=2 | A=1, B=2, C=3 |
| 4 | User B cancels | A=1, B=2, C=3 | A=1, C=2 |
| 5 | User A promoted | A=1, C=2 | C=1 |

**How to test:**
1. Fill a slot so waiting list is needed
2. Add 3 users to waiting list
3. Have middle user cancel
4. Verify positions are 1, 2 (not 1, 3)

**Database verification:**
```sql
SELECT presenter_username, position FROM waiting_list
WHERE slot_id = X ORDER BY position;
-- Positions should be contiguous: 1, 2, 3...
```

### Duplicate Registration Prevention (MUST TEST)
Test that users cannot register twice for same slot.

| Scenario | Expected Response |
|----------|-------------------|
| User registers, then registers again (PENDING) | `ALREADY_REGISTERED` |
| User registers, approved, registers again | `ALREADY_APPROVED` |
| User registers, declined, registers again | Success (re-registration allowed) |
| User registers, expired, registers again | Success (re-registration allowed) |

**How to test:**
1. Register for a slot
2. Try to register again → Should fail
3. Get declined, try again → Should succeed

### Invalid Request Handling (MUST TEST)
Test API validation and error responses.

| Scenario | Expected Response | HTTP Status |
|----------|-------------------|-------------|
| Missing `presenterUsername` | "Presenter username is required" | 400 |
| Invalid `slotId` (non-existent) | "Slot not found" | 404 |
| Invalid `slotId` (negative) | Validation error | 400 |
| Empty request body | "Request body is null" | 400 |
| Invalid supervisor email format | Validation error | 400 |

**How to test with curl:**
```bash
# Missing username
curl -X POST "http://132.72.50.53:8080/api/v1/slots/999/waiting-list" \
  -H "Content-Type: application/json" \
  -d '{}'

# Non-existent slot
curl -X POST "http://132.72.50.53:8080/api/v1/slots/99999/waiting-list" \
  -H "Content-Type: application/json" \
  -d '{"presenterUsername": "testuser"}'
```

### Concurrent Registration Handling (EDGE CASE)
Test race condition when two users try to get last spot.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Slot has 1 spot left | capacity - registered = 1 |
| 2 | User A and User B click Register simultaneously | Both requests hit server |
| 3 | Database constraint enforces | One succeeds, one fails |
| 4 | Failed user sees | "Slot is full" message |

**How to test:**
1. Create slot with capacity 1
2. Use two browser tabs/devices
3. Click Register at same time
4. Verify only one registration exists

### Announcement Banner Flow (MUST TEST)
Test system-wide announcements via `announce_config` table.

| Field | Description |
|-------|-------------|
| `is_active` | 1 = show banner, 0 = hide |
| `title` | Banner title |
| `message` | Banner message text |
| `is_blocking` | 1 = must dismiss, 0 = can ignore |
| `start_at` / `end_at` | Display window (NULL = always) |

**How to test:**
1. Enable announcement in database:
```sql
UPDATE announce_config SET
  is_active = 1,
  title = 'Test Announcement',
  message = 'This is a test message',
  is_blocking = 0
WHERE id = 1;
```
2. Open app → Should see banner
3. Disable: `UPDATE announce_config SET is_active = 0 WHERE id = 1;`

### Device Info Logging (MUST TEST)
Test that mobile device info is captured in logs.

**Expected X-Device-Info format:**
```
Samsung SM-G991B (Android 12, SDK 31)
```

**How to test:**
1. Perform any action from mobile app
2. Check app_logs table for device_info column

**Database verification:**
```sql
SELECT log_timestamp, tag, device_info, app_version
FROM app_logs
WHERE source = 'MOBILE'
ORDER BY log_id DESC LIMIT 10;
```

### Mobile vs API Timestamp Sync (MUST TEST)
Test that mobile log timestamps match server time (Israel timezone).

**Bug fixed:** 2026-01-05 - Mobile logs were showing 2 hours behind API logs (UTC vs Israel time).

| Source | Expected Timestamp |
|--------|-------------------|
| API logs | Server time (Israel) |
| MOBILE logs | Server time (Israel) - converted from epoch |

**How to test:**
1. Note current server time: `date '+%H:%M:%S'`
2. Perform action on mobile app (login, refresh slots)
3. Check logs immediately

**Database verification:**
```sql
-- Compare API and MOBILE timestamps - should be within seconds of each other
SELECT log_timestamp, source, tag
FROM app_logs
ORDER BY log_id DESC LIMIT 10;

-- Server time check
SELECT NOW() as server_time;
```

**Expected:** MOBILE log_timestamp should match server time (not 2 hours behind).

### Registration with Missing Supervisor (EDGE CASE)
Test registration when user has no supervisor linked.

| Scenario | Expected Result |
|----------|-----------------|
| User has no supervisor, provides in request | Registration succeeds |
| User has no supervisor, doesn't provide | Error: "Supervisor information required" |
| User has supervisor linked | Uses linked supervisor |

**How to test:**
1. Remove supervisor from user: `UPDATE users SET supervisor_id = NULL WHERE bgu_username = 'testuser';`
2. Try to register without providing supervisor → Should fail
3. Try to register with supervisor in request → Should succeed

### Null/Empty Field Handling (EDGE CASE)
Test graceful handling of optional fields.

| Field | If NULL/Empty | Expected |
|-------|---------------|----------|
| `topic` | NULL | Stored as NULL, email shows "N/A" |
| `seminar_abstract` | NULL | Stored as NULL |
| `supervisor_name` | Empty string | Stored as empty |
| `national_id_number` | NULL | Stored as NULL |

**How to test:**
1. Register with minimal fields (only required ones)
2. Verify registration succeeds
3. Check approval email renders correctly with "N/A" for missing fields

### Idempotent Approval Operations (MUST TEST)
Test that clicking approve/decline twice doesn't cause errors.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Supervisor clicks Approve link | Success page shown |
| 2 | Supervisor clicks same link again | Success page (already approved) |
| 3 | Check database | Single approval, not duplicated |

**How to test:**
1. Register for slot, get approval email
2. Click approve link → Success
3. Click approve link again → Still success (idempotent)
4. Verify `supervisor_approved_at` only set once

### Attendance Duplicate Prevention (MUST TEST)
Test that scanning QR twice doesn't create duplicate attendance.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Student scans QR | Attendance recorded |
| 2 | Student scans same QR again | "Already marked attendance" |
| 3 | Check database | Single attendance record |

**Database verification:**
```sql
SELECT COUNT(*) FROM attendance
WHERE session_id = X AND student_username = 'testuser';
-- Should always be 1, never 2
```

## Troubleshooting

### Common Issues

1. **"User not found" in logs**
   - User hasn't logged in yet (not in `users` table)
   - Username not normalized (check case, domain)

2. **Foreign key violation on app_logs**
   - `bgu_username` must exist in `users` table
   - DatabaseLoggerService checks before logging

3. **Email not sending**
   - Check `email_queue` table for failures
   - Verify SMTP credentials in environment

4. **Session expired immediately**
   - Check JWT secret matches
   - Verify server time sync

5. **Slot shows wrong capacity**
   - Check `phd.capacity.weight` config
   - Verify degree is set correctly on user

### Checking Server State
```bash
# SSH and check service
ssh webmaster@132.72.50.53
sudo systemctl status semscan-api
sudo journalctl -u semscan-api -f

# Check database
mysql -u semscan_admin -pTAL1234 semscan_db

# Useful queries
SELECT * FROM app_logs ORDER BY id DESC LIMIT 20;
SELECT * FROM seminar_slot_registrations WHERE slot_id = X;
SELECT * FROM waiting_list_entries WHERE slot_id = X;
SELECT * FROM email_queue WHERE status = 'PENDING';
```

## Mobile App Resources

### Key Layouts
- `activity_login.xml` - Login screen
- `activity_presenter_home.xml` - Presenter main screen
- `item_presenter_slot.xml` - Slot card in list
- `activity_student_home.xml` - Student main screen

### Drawables
- `bg_slot_green_gradient.xml` - Available slot
- `bg_slot_yellow_gradient.xml` - Partially filled
- `bg_slot_red_gradient.xml` - Full slot

### Preferences Keys
- `bgu_username` - Logged in user
- `is_presenter` - User is presenter
- `is_participant` - User is participant
- `supervisor_id` - Linked supervisor

## Database Schema (Full DDL)

**IMPORTANT**: Always reference this schema when writing SQL queries. The actual table/column names may differ from JPA entity names.

```sql
-- Table: announce_config
CREATE TABLE `announce_config` (
  `id` int NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '1',
  `title` varchar(100) DEFAULT '',
  `message` text,
  `is_blocking` tinyint(1) NOT NULL DEFAULT '0',
  `start_at` timestamp NULL DEFAULT NULL,
  `end_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- Table: app_config
CREATE TABLE `app_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(255) NOT NULL,
  `config_value` text NOT NULL,
  `config_type` enum('STRING','INTEGER','BOOLEAN','JSON') NOT NULL,
  `target_system` enum('MOBILE','API','BOTH') NOT NULL,
  `category` varchar(50) DEFAULT NULL,
  `description` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `config_key` (`config_key`)
);

-- Table: app_logs
CREATE TABLE `app_logs` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `log_timestamp` datetime NOT NULL,
  `level` varchar(20) NOT NULL,
  `tag` varchar(100) DEFAULT NULL,
  `message` text NOT NULL,
  `source` enum('API','MOBILE') NOT NULL DEFAULT 'API',
  `correlation_id` varchar(50) DEFAULT NULL,
  `bgu_username` varchar(50) DEFAULT NULL,
  `user_full_name` varchar(200) DEFAULT NULL,
  `user_role` enum('PARTICIPANT','PRESENTER','BOTH','UNKNOWN') DEFAULT 'UNKNOWN',
  `device_info` varchar(255) DEFAULT NULL,
  `app_version` varchar(50) DEFAULT NULL,
  `stack_trace` text,
  `exception_type` varchar(100) DEFAULT NULL,
  `payload` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`),
  KEY `idx_logs_user` (`bgu_username`),
  CONSTRAINT `fk_logs_user` FOREIGN KEY (`bgu_username`) REFERENCES `users` (`bgu_username`) ON DELETE SET NULL
);

-- Table: users
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `bgu_username` varchar(50) NOT NULL,
  `first_name` varchar(100) NOT NULL,
  `last_name` varchar(100) NOT NULL,
  `degree` enum('MSc','PhD') DEFAULT NULL,
  `is_presenter` tinyint(1) DEFAULT '0',
  `is_participant` tinyint(1) DEFAULT '0',
  `national_id_number` varchar(50) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `supervisor_id` bigint DEFAULT NULL,
  `seminar_abstract` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `bgu_username` (`bgu_username`)
);

-- Table: slots
CREATE TABLE `slots` (
  `slot_id` bigint NOT NULL AUTO_INCREMENT,
  `semester_label` varchar(50) DEFAULT NULL,
  `slot_date` date NOT NULL,
  `start_time` time NOT NULL,
  `end_time` time NOT NULL,
  `building` varchar(50) DEFAULT NULL,
  `room` varchar(50) DEFAULT NULL,
  `capacity` int NOT NULL,
  `status` enum('FREE','SEMI','FULL') NOT NULL DEFAULT 'FREE',
  `attendance_opened_at` datetime DEFAULT NULL,
  `attendance_closes_at` datetime DEFAULT NULL,
  `attendance_opened_by` varchar(50) DEFAULT NULL,
  `legacy_seminar_id` bigint DEFAULT NULL,
  `legacy_session_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`slot_id`)
);

-- Table: slot_registration (NOT seminar_slot_registrations!)
CREATE TABLE `slot_registration` (
  `slot_id` bigint NOT NULL,
  `presenter_username` varchar(50) NOT NULL,
  `degree` enum('MSc','PhD') NOT NULL,
  `topic` varchar(255) DEFAULT NULL,
  `seminar_abstract` text,
  `supervisor_name` varchar(255) DEFAULT NULL,
  `supervisor_email` varchar(255) DEFAULT NULL,
  `national_id_number` varchar(50) DEFAULT NULL,
  `registered_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approval_status` enum('PENDING','APPROVED','DECLINED','EXPIRED') NOT NULL DEFAULT 'PENDING',
  `approval_token` varchar(255) DEFAULT NULL,
  `approval_token_expires_at` datetime DEFAULT NULL,
  `supervisor_approved_at` datetime DEFAULT NULL,
  `supervisor_declined_at` datetime DEFAULT NULL,
  `supervisor_declined_reason` text,
  `last_reminder_sent_at` datetime DEFAULT NULL,
  PRIMARY KEY (`slot_id`,`presenter_username`),
  UNIQUE KEY `approval_token` (`approval_token`),
  CONSTRAINT `fk_slot_registration_presenter` FOREIGN KEY (`presenter_username`) REFERENCES `users` (`bgu_username`) ON DELETE CASCADE,
  CONSTRAINT `fk_slot_registration_slot` FOREIGN KEY (`slot_id`) REFERENCES `slots` (`slot_id`) ON DELETE CASCADE
);

-- Table: waiting_list (NOT waiting_list_entries!)
CREATE TABLE `waiting_list` (
  `waiting_list_id` bigint NOT NULL AUTO_INCREMENT,
  `slot_id` bigint NOT NULL,
  `presenter_username` varchar(50) NOT NULL,
  `degree` enum('MSc','PhD') NOT NULL,
  `topic` varchar(255) DEFAULT NULL,
  `supervisor_name` varchar(255) DEFAULT NULL,
  `supervisor_email` varchar(255) DEFAULT NULL,
  `position` int NOT NULL,
  `added_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `promotion_token` varchar(255) DEFAULT NULL,
  `promotion_token_expires_at` datetime DEFAULT NULL,
  `promotion_offered_at` datetime DEFAULT NULL,
  PRIMARY KEY (`waiting_list_id`),
  UNIQUE KEY `unique_slot_presenter_waiting` (`slot_id`,`presenter_username`),
  CONSTRAINT `fk_waiting_list_presenter` FOREIGN KEY (`presenter_username`) REFERENCES `users` (`bgu_username`) ON DELETE CASCADE,
  CONSTRAINT `fk_waiting_list_slot` FOREIGN KEY (`slot_id`) REFERENCES `slots` (`slot_id`) ON DELETE CASCADE
);

-- Table: waiting_list_promotions
CREATE TABLE `waiting_list_promotions` (
  `promotion_id` bigint NOT NULL AUTO_INCREMENT,
  `slot_id` bigint NOT NULL,
  `presenter_username` varchar(50) NOT NULL,
  `registration_slot_id` bigint NOT NULL,
  `registration_presenter_username` varchar(50) NOT NULL,
  `promoted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime NOT NULL,
  `status` enum('PENDING','APPROVED','DECLINED','EXPIRED') NOT NULL DEFAULT 'PENDING',
  PRIMARY KEY (`promotion_id`),
  CONSTRAINT `fk_promotions_registration` FOREIGN KEY (`registration_slot_id`, `registration_presenter_username`) REFERENCES `slot_registration` (`slot_id`, `presenter_username`) ON DELETE CASCADE,
  CONSTRAINT `fk_promotions_slot` FOREIGN KEY (`slot_id`) REFERENCES `slots` (`slot_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_promotions_user` FOREIGN KEY (`presenter_username`) REFERENCES `users` (`bgu_username`) ON DELETE CASCADE
);

-- Table: seminars
CREATE TABLE `seminars` (
  `seminar_id` bigint NOT NULL AUTO_INCREMENT,
  `seminar_name` varchar(255) NOT NULL,
  `description` text,
  `presenter_username` varchar(50) NOT NULL,
  `max_enrollment_capacity` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`seminar_id`),
  CONSTRAINT `fk_seminars_presenter` FOREIGN KEY (`presenter_username`) REFERENCES `users` (`bgu_username`) ON DELETE CASCADE
);

-- Table: sessions
CREATE TABLE `sessions` (
  `session_id` bigint NOT NULL AUTO_INCREMENT,
  `seminar_id` bigint NOT NULL,
  `start_time` datetime NOT NULL,
  `end_time` datetime DEFAULT NULL,
  `status` enum('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
  `location` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`session_id`),
  CONSTRAINT `fk_sessions_seminar` FOREIGN KEY (`seminar_id`) REFERENCES `seminars` (`seminar_id`) ON DELETE CASCADE
);

-- Table: attendance
CREATE TABLE `attendance` (
  `attendance_id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `student_username` varchar(50) NOT NULL,
  `attendance_time` datetime NOT NULL,
  `method` enum('QR_SCAN','MANUAL','MANUAL_REQUEST','PROXY') NOT NULL,
  `request_status` enum('PENDING_APPROVAL','CONFIRMED','REJECTED') DEFAULT NULL,
  `manual_reason` varchar(255) DEFAULT NULL,
  `requested_at` datetime DEFAULT NULL,
  `approved_by_username` varchar(50) DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `device_id` varchar(100) DEFAULT NULL,
  `auto_flags` text,
  `notes` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`attendance_id`),
  UNIQUE KEY `uq_attendance_session_student` (`session_id`,`student_username`),
  CONSTRAINT `fk_attendance_approver` FOREIGN KEY (`approved_by_username`) REFERENCES `users` (`bgu_username`) ON DELETE SET NULL,
  CONSTRAINT `fk_attendance_session` FOREIGN KEY (`session_id`) REFERENCES `sessions` (`session_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_attendance_student` FOREIGN KEY (`student_username`) REFERENCES `users` (`bgu_username`) ON DELETE CASCADE
);

-- Table: email_queue
CREATE TABLE `email_queue` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `to_email` varchar(255) NOT NULL,
  `cc_email` varchar(255) DEFAULT NULL,
  `bcc_email` varchar(255) DEFAULT NULL,
  `subject` varchar(500) NOT NULL,
  `html_content` text NOT NULL,
  `email_type` varchar(50) NOT NULL,
  `registration_id` bigint DEFAULT NULL,
  `slot_id` bigint DEFAULT NULL,
  `username` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT '0',
  `max_retries` int NOT NULL DEFAULT '3',
  `last_error` text,
  `last_error_code` varchar(50) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `scheduled_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `last_attempt_at` timestamp NULL DEFAULT NULL,
  `sent_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
);

-- Table: email_log
CREATE TABLE `email_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `to_email` varchar(255) NOT NULL,
  `subject` varchar(500) NOT NULL,
  `email_type` varchar(50) NOT NULL,
  `status` varchar(20) NOT NULL,
  `error_message` text,
  `error_code` varchar(50) DEFAULT NULL,
  `registration_id` bigint DEFAULT NULL,
  `slot_id` bigint DEFAULT NULL,
  `username` varchar(100) DEFAULT NULL,
  `queue_id` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- Table: seminar_participants
CREATE TABLE `seminar_participants` (
  `participant_id` bigint NOT NULL AUTO_INCREMENT,
  `seminar_id` bigint NOT NULL,
  `participant_username` varchar(50) NOT NULL,
  `role` enum('PARTICIPANT','PRESENTER') NOT NULL,
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`participant_id`),
  UNIQUE KEY `unique_seminar_user` (`seminar_id`,`participant_username`),
  CONSTRAINT `fk_seminar_participants_seminar` FOREIGN KEY (`seminar_id`) REFERENCES `seminars` (`seminar_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_seminar_participants_user` FOREIGN KEY (`participant_username`) REFERENCES `users` (`bgu_username`) ON DELETE CASCADE
);

-- Table: supervisor_reminder_tracking
CREATE TABLE `supervisor_reminder_tracking` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registration_id` bigint NOT NULL,
  `supervisor_email` varchar(255) NOT NULL,
  `reminder_date` date NOT NULL,
  `sent_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_reminder` (`registration_id`,`reminder_date`)
);

-- Table: test_flows (for QA testing)
CREATE TABLE `test_flows` (
  `test_id` varchar(20) NOT NULL,
  `category` varchar(50) DEFAULT NULL,
  `test_name` varchar(150) DEFAULT NULL,
  `priority` varchar(10) DEFAULT NULL,
  `db_setup` text,
  `presenter_setup` text,
  `preconditions` text,
  `step_1` varchar(300) DEFAULT NULL,
  `step_2` varchar(300) DEFAULT NULL,
  `step_3` varchar(300) DEFAULT NULL,
  `step_4` varchar(300) DEFAULT NULL,
  `step_5` varchar(300) DEFAULT NULL,
  `step_6` varchar(300) DEFAULT NULL,
  `expected_result` text,
  `pass_fail` varchar(10) DEFAULT NULL,
  `tester` varchar(50) DEFAULT NULL,
  `test_date` date DEFAULT NULL,
  `notes` text,
  PRIMARY KEY (`test_id`)
);
```

### Table Name Mapping (JPA Entity → Actual Table)
| JPA Entity | Actual Table Name |
|------------|-------------------|
| `SeminarSlotRegistration` | `slot_registration` |
| `WaitingListEntry` | `waiting_list` |
| `SeminarSlot` | `slots` |
| `AppLog` | `app_logs` |
| `User` | `users` |
| `Session` | `sessions` |
| `Seminar` | `seminars` |
| `Attendance` | `attendance` |
| `EmailQueue` | `email_queue` |
| `AppConfig` | `app_config` |

## Capacity Change Runbook

**Frequency**: ~2-3 times per year. Manual process - no automation needed.

### Decreasing Capacity (e.g., 3 → 2)

**Migration script**: `SemScan-API/src/main/resources/db/migration/capacity_migration_3_to_2.sql`

Steps:
1. Run diagnostic queries to find over-capacity slots
2. Create backups
3. Cancel PENDING registrations that overflow (oldest stays, newest cancelled)
4. Update `slots.capacity`
5. Update `app_config` → `waiting.list.limit.per.slot`
6. Recalculate slot status
7. **Restart API service** to clear config cache

**WARNING**: APPROVED registrations are NEVER auto-cancelled. If APPROVED > new capacity, manually contact users to reschedule.

### Increasing Capacity (e.g., 2 → 3)

**Important**: Waiting list is NOT auto-promoted when capacity increases!

```sql
-- 1. Update capacity
UPDATE slots SET capacity = 3, updated_at = NOW() WHERE capacity = 2 AND slot_date >= CURDATE();

-- 2. Update config
UPDATE app_config SET config_value = '3', updated_at = NOW() WHERE config_key = 'waiting.list.limit.per.slot';

-- 3. Find slots with waiting list that now have room
SELECT s.slot_id, s.slot_date,
       s.capacity - COALESCE(SUM(CASE WHEN r.degree = 'PhD' THEN 2 ELSE 1 END), 0) AS available,
       (SELECT GROUP_CONCAT(w.presenter_username) FROM waiting_list w WHERE w.slot_id = s.slot_id) AS waiting
FROM slots s
LEFT JOIN slot_registration r ON s.slot_id = r.slot_id AND r.approval_status IN ('PENDING', 'APPROVED')
WHERE s.slot_date >= CURDATE()
GROUP BY s.slot_id
HAVING available > 0 AND waiting IS NOT NULL;

-- 4. Restart API service
sudo systemctl restart semscan-api
```

**Manual promotion options**:
- Tell waiting list users to cancel and re-register through the app
- Or manually INSERT into `slot_registration` and DELETE from `waiting_list`
