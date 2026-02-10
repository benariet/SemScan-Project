# SemScan Project

## Rules for Claude (MANDATORY)

**Git & Attribution:**
- NEVER add Co-Authored-By lines in commits
- NEVER add Claude stamps/signatures anywhere
- NEVER appear as contributor in git history
- Use user's git config for commits (tal <benariet@bgu.ac.il>)

**Code Style:**
- NEVER add emoji unless user explicitly asks
- NEVER over-engineer - this app has ~20 users max
- NEVER refactor existing code just for aesthetics
- Keep changes minimal and focused
- Ask: "Is this needed for 20 users?" → usually NO

**Android App is the Source of Truth (CRITICAL):**
- BEFORE implementing ANY new feature in Web App: Check how it's done in the Android app FIRST
- BEFORE fixing ANY bug in Web App: Check how the Android app handles it FIRST
- The Android app is WORKING - copy its logic exactly
- Don't reinvent solutions - the Android app has already solved these problems

**Deployment:**
- NEVER deploy directly to production
- ALWAYS deploy to TEST first, then promote to PRODUCTION
- ALWAYS verify environment (port 8080=prod, 8081=test)

**Communication:**
- Pretty-print JSON output with indentation
- Be concise in responses

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
## Code Review Rules
When reviewing code, check: NPE risks, method chains, format specifiers, resource leaks, dead code, unused imports, exception handling, SQL syntax.
Output as: `| Line | Issue | Severity | Fix |`

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

### CRITICAL: Always Deploy to DEV First!
**NEVER deploy directly to production.** Always:
1. Build JAR locally
2. Deploy to DEV environment (132.72.50.52:8080)
3. Test thoroughly
4. Only after testing, promote to PRODUCTION (.53:8080)

### Environments

| Environment | Server | URL | Port | Database | API Path | Service |
|-------------|--------|-----|------|----------|----------|---------|
| **Production** | 132.72.50.53 | http://132.72.50.53:8080 | 8080 | semscan_db | /opt/semscan-api | semscan-api |
| **Development** | 132.72.50.52 | http://132.72.50.52:8080 | 8080 | semscan_db | /opt/semscan-api | semscan-api |

### Remote Servers
| Server | Host | Username | Password |
|--------|------|----------|----------|
| **Production** | 132.72.50.53 | webmaster | TAL1234 |
| **Development** | 132.72.50.52 | webmaster | TAL1234 |

### Development Environment Deployment (DEFAULT)
```bash
# 1. Build JAR locally
cd SemScan-API && ./gradlew bootJar

# 2. Upload JAR to DEV (.52)
curl -k -u "webmaster:TAL1234" -T "SemScan-API/build/libs/SemScan-API-1.0.0.jar" "sftp://132.72.50.52/opt/semscan-api/SemScan-API-1.0.0.jar"

# 3. Restart DEV service
ssh -o StrictHostKeyChecking=no webmaster@132.72.50.52 "echo 'TAL1234' | sudo -S systemctl restart semscan-api"

# 4. Test on http://132.72.50.52:8080
```

### Promote DEV to Production
**Only run these commands after testing is complete on DEV:**
```bash
# 1. Upload JAR to PRODUCTION (same JAR that was tested)
curl -k -u "webmaster:TAL1234" -T "SemScan-API/build/libs/SemScan-API-1.0.0.jar" "sftp://132.72.50.53/opt/semscan-api/SemScan-API-1.0.0.jar"

# 2. Restart PRODUCTION service
ssh -o StrictHostKeyChecking=no webmaster@132.72.50.53 "echo 'TAL1234' | sudo -S systemctl restart semscan-api"
```

### APK Upload Commands
```bash
# Upload APK to DEV (.52)
curl -k -u "webmaster:TAL1234" -T "SemScan/build/outputs/apk/debug/semscan-1.0.0.apk" "sftp://132.72.50.52/opt/semscan-api/semscan-1.0.0.apk"

# Upload APK to PRODUCTION (.53)
curl -k -u "webmaster:TAL1234" -T "SemScan/build/outputs/apk/debug/semscan-1.0.0.apk" "sftp://132.72.50.53/opt/semscan-api/semscan-1.0.0.apk"
```

### Note
- Only keep ONE JAR file per environment. When uploading, the new JAR overwrites the existing one.
- Only keep ONE APK file per environment. The filename must be `semscan-1.0.0.apk`.
- Always restart the appropriate service after uploading a new JAR.

### Environment Switch

**What auto-detects (no changes needed):**

| Component | How It Detects |
|-----------|----------------|
| **Web App** | Relative URL `/api/v1` uses current origin automatically |

**What needs manual change:**

| Component | File | Production (.53) | Dev (.52) |
|-----------|------|------------------|-----------|
| **Android APK** | `SemScan/src/main/java/org/example/semscan/constants/ApiConstants.java` lines 17-18 | `.53:8080` | `.52:8080` |

**Server paths:**

| Environment | Server | Port | Database | JAR/APK Path | Service |
|-------------|--------|------|----------|--------------|---------|
| **Production** | .53 | 8080 | semscan_db | `/opt/semscan-api/` | semscan-api |
| **Development** | .52 | 8080 | semscan_db | `/opt/semscan-api/` | semscan-api |

**Switch to DEV (.52):**
```bash
# 1. Edit ApiConstants.java - change .53 → .52 (port stays 8080)
# 2. Build
cd SemScan && ./gradlew assembleDebug
cd SemScan-API && ./gradlew bootJar
# 3. Upload to DEV (.52)
curl -k -u "webmaster:TAL1234" -T "SemScan-API/build/libs/SemScan-API-1.0.0.jar" "sftp://132.72.50.52/opt/semscan-api/SemScan-API-1.0.0.jar"
curl -k -u "webmaster:TAL1234" -T "SemScan/build/outputs/apk/debug/SemScan-debug.apk" "sftp://132.72.50.52/opt/semscan-api/semscan-1.0.0.apk"
ssh webmaster@132.72.50.52 "echo 'TAL1234' | sudo -S systemctl restart semscan-api"
# 4. Install on device
adb install -r SemScan/build/outputs/apk/debug/SemScan-debug.apk
```

**Switch to PRODUCTION (.53):**
```bash
# 1. Edit ApiConstants.java - change .52 → .53 (port stays 8080)
# 2. Build
cd SemScan && ./gradlew assembleDebug
cd SemScan-API && ./gradlew bootJar
# 3. Upload to PRODUCTION (.53)
curl -k -u "webmaster:TAL1234" -T "SemScan-API/build/libs/SemScan-API-1.0.0.jar" "sftp://132.72.50.53/opt/semscan-api/SemScan-API-1.0.0.jar"
curl -k -u "webmaster:TAL1234" -T "SemScan/build/outputs/apk/debug/SemScan-debug.apk" "sftp://132.72.50.53/opt/semscan-api/semscan-1.0.0.apk"
ssh webmaster@132.72.50.53 "echo 'TAL1234' | sudo -S systemctl restart semscan-api"
```

**Note:** Both environments use identical configuration (port 8080, semscan_db) - only the server IP differs.

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
# SSH to DEV server (.52)
ssh webmaster@132.72.50.52

# SSH to PRODUCTION server (.53)
ssh webmaster@132.72.50.53

# Check service status (same command on both servers)
sudo systemctl status semscan-api

# View recent logs (same command on both servers)
sudo journalctl -u semscan-api -f

# Query app_logs table (same on both servers)
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

### Tags Reference
Search configs by tag: `SELECT * FROM app_config WHERE tags LIKE '%#EMAIL%';`

| Tag | Description |
|-----|-------------|
| `#APPROVAL` | Supervisor approval flow |
| `#ATTENDANCE` | QR/manual attendance windows |
| `#CACHE` | Caching settings |
| `#CAPACITY` | Slot capacity rules |
| `#DEBUG` | Testing only |
| `#EMAIL` | Email sending settings |
| `#EXPIRY` | Expiration settings |
| `#EXPORT` | Data export |
| `#LIMIT` | Maximum limits |
| `#NETWORK` | HTTP/connection settings |
| `#REGISTRATION` | Slot registration |
| `#TIMEOUT` | Time limits |
| `#UI` | User interface (toasts) |
| `#VERSION` | App version |
| `#WAITINGLIST` | Waiting list settings |

**Note:** `target_system` column (MOBILE/API/BOTH) indicates where config is used. Tags are for functional categorization only.

### Registration & Capacity (#REGISTRATION)
| Key | Value | Tags | Description |
|-----|-------|------|-------------|
| `phd.capacity.weight` | 3 | `#REGISTRATION #CAPACITY` | PhD takes entire slot (weight=3, capacity=3) |
| `registration.max_approved` | 1 | `#REGISTRATION #LIMIT` | Max approved registrations per user (once per degree - students can only present once during their entire degree) |
| `registration.max_pending.msc` | 2 | `#REGISTRATION #LIMIT` | Max pending registrations for MSc |
| `registration.max_pending.phd` | 1 | `#REGISTRATION #LIMIT` | Max pending registrations for PhD |

### Waiting List (#WAITINGLIST)
| Key | Value | Tags | Description |
|-----|-------|------|-------------|
| `waiting.list.limit.per.slot` | 3 | `#WAITINGLIST` | Max users on waiting list per slot |
| `waiting_list_approval_window_hours` | 168 | `#WAITINGLIST #EXPIRY` | Hours to respond when promoted (7 days) |
| `promotion_offer_expiry_hours` | 48 | `#WAITINGLIST #EXPIRY` | Hours for promotion offer to expire |

### Attendance Windows (#ATTENDANCE)
| Key | Value | Tags | Description |
|-----|-------|------|-------------|
| `presenter_close_session_duration_minutes` | 15 | `#ATTENDANCE #TIMEOUT` | Session auto-close after opening |
| `presenter_slot_open_window_before_minutes` | 0 | `#ATTENDANCE` | Minutes before slot presenter can open |
| `presenter_slot_open_window_after_minutes` | 15 | `#ATTENDANCE` | Minutes after slot presenter can open |
| `student_attendance_window_before_minutes` | 0 | `#ATTENDANCE` | Minutes before session for QR scan |
| `student_attendance_window_after_minutes` | 10 | `#ATTENDANCE` | Minutes after session for QR scan |
| `manual_attendance_window_before_minutes` | 10 | `#ATTENDANCE` | Minutes before session for manual request |
| `manual_attendance_window_after_minutes` | 15 | `#ATTENDANCE` | Minutes after session for manual request |

### Email Settings (#EMAIL)
| Key | Value | Tags | Description |
|-----|-------|------|-------------|
| `approval_token_expiry_days` | 14 | `#APPROVAL #EXPIRY` | Days until supervisor link expires |
| `approval_reminder_interval_days` | 2 | `#EMAIL` | Days between supervisor reminders |
| `expiration_warning_hours_before` | 48 | `#EMAIL #EXPIRY` | Hours before expiry to warn student |
| `email_from_name` | SemScan System | `#EMAIL` | Email sender display name |
| `email_reply_to` | noreply@bgu.ac.il | `#EMAIL` | Reply-to address |
| `email_domain` | @bgu.ac.il | `#EMAIL` | Email domain suffix |
| `email_bcc_list` | benariet@bgu.ac.il | `#EMAIL` | BCC recipients (comma-separated) |
| `export_email_recipients` | benariet@bgu.ac.il,talbnwork@gmail.com | `#EMAIL` | Export recipients |
| `support_email` | benariet@bgu.ac.il | `#EMAIL` | Support contact |
| `test_email_recipient` | talbnwork@gmail.com | `#EMAIL #DEBUG` | Test email recipient |
| `email_queue_max_retries` | 3 | `#EMAIL` | Max retry attempts |
| `email_queue_initial_backoff_minutes` | 5 | `#EMAIL` | Initial retry delay |
| `email_queue_backoff_multiplier` | 3 | `#EMAIL` | Backoff multiplier (5→15→45 min) |
| `email_queue_batch_size` | 50 | `#EMAIL` | Emails per batch |
| `email_queue_process_interval_seconds` | 120 | `#EMAIL` | Queue check interval |

### Network & Timeouts (#NETWORK)
| Key | Value | Tags | Description |
|-----|-------|------|-------------|
| `server_url` | http://132.72.50.53:8080 | `#NETWORK` | API base URL |
| `connection_timeout_seconds` | 30 | `#NETWORK #TIMEOUT` | HTTP connection timeout |
| `read_timeout_seconds` | 30 | `#NETWORK #TIMEOUT` | HTTP read timeout |
| `write_timeout_seconds` | 30 | `#NETWORK #TIMEOUT` | HTTP write timeout |

### Mobile App (target_system=MOBILE)
| Key | Value | Tags | Description |
|-----|-------|------|-------------|
| `APP_VERSION` | 1.0.0 | `#VERSION` | Current mobile app version |
| `config_cache_ttl_hours` | 24 | `#CACHE` | Config cache TTL in hours |
| `max_export_file_size_mb` | 50 | `#EXPORT` | Max export file size |
| `toast_duration_error` | 10000 | `#UI` | Error toast duration (ms) |
| `toast_duration_success` | 5000 | `#UI` | Success toast duration (ms) |
| `toast_duration_info` | 6000 | `#UI` | Info toast duration (ms) |

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
| `testphd3` | `Test123!` | David Rosen | PhD |
| `amarrev` | `Revital1990%` | Revital Amar | MSc |
| `benariet` | `Taltal123!` | Tal Ben Arie | MSc |
| `talguest3` | `kbm7Xzfk` | Dana Katz | MSc |
| `talguest4` | `atpgK2zc` | Jhon Smith | MSc |
| `testmsc1` | `Test123!` | Yael Stern | MSc |
| `testmsc2` | `Test123!` | Oren Golan | MSc |
| `testmsc3` | `Test123!` | Noa Shapira | MSc |
| `testmsc4` | `Test123!` | Eitan Peretz | MSc |
| `testmsc5` | `Test123!` | Shira Avraham | MSc |

**Note:** These 30 test users bypass BGU authentication (all use password `Test123!`):
- PhD: `testphd1` through `testphd10`
- MSc: `testmsc1` through `testmsc20`

All other users authenticate via BGU SOAP.


## Critical Test Cases

### 1. PhD/MSc Exclusivity Tests

| Test | Setup | Action | Expected |
|------|-------|--------|----------|
| PhD registers empty | Empty slot | PhD registers | ✅ Success, slot FULL |
| MSc blocked by PhD | Slot has PhD | MSc registers | ❌ `SLOT_LOCKED` |
| PhD blocked by MSc | Slot has MSc | PhD registers | ❌ `PHD_BLOCKED_BY_MSC` |
| MSc joins MSc slot | Slot has 1-2 MSc | MSc registers | ✅ Success |
| 3rd MSc fills slot | Slot has 2 MSc | MSc registers | ✅ Success, slot FULL |
| 4th MSc blocked | Slot has 3 MSc | MSc registers | ❌ `SLOT_FULL` |

**Test users:** `talguest2` (PhD), `talguest3`/`talguest4`/`benariet` (MSc)

### 2. Registration Flow Tests

| Test | Steps | Expected |
|------|-------|----------|
| Basic registration | Login → Select slot → Register | Status = PENDING, email queued |
| Re-register after DECLINED | Register → Decline → Register again | ✅ New PENDING registration |
| Re-register after EXPIRED | Register → Expire → Register again | ✅ New PENDING registration |
| Block re-register PENDING | Register → Register again | ❌ `ALREADY_REGISTERED` |
| Block re-register APPROVED | Register → Approve → Register another | ❌ `ALREADY_APPROVED` |
| Past slot blocked | Select past slot → Register | ❌ No register button shown |

**Verify DB:** `SELECT * FROM slot_registration WHERE presenter_username = 'X';`

### 3. Waiting List Tests

| Test | Setup | Action | Expected |
|------|-------|--------|----------|
| Join empty WL | Full slot, empty WL | Join WL | ✅ Position 1 |
| PhD sets WL type | PhD slot, empty WL | PhD joins | WL type = PhD-only |
| MSc sets WL type | PhD slot, empty WL | MSc joins | WL type = MSc-only |
| Wrong degree blocked | WL has MSc first | PhD tries join | ❌ `DEGREE_MISMATCH` |
| Same degree joins | WL has MSc | MSc joins | ✅ Position 2 |
| WL full | WL has 3 users | Join WL | ❌ `WAITING_LIST_FULL` |
| Already on WL | User on slot WL | Join same WL | ❌ `ALREADY_ON_LIST` |
| Already on other WL | User on slot A WL | Join slot B WL | ❌ `ALREADY_ON_ANOTHER` |

**Verify DB:** `SELECT * FROM waiting_list WHERE slot_id = X ORDER BY position;`

### 4. Waiting List Promotion Tests

| Test | Setup | Action | Expected |
|------|-------|--------|----------|
| Auto-promote on cancel | WL has users | Cancel registration | First WL user → PENDING registration |
| Positions shift | WL: A=1, B=2, C=3 | A promoted | B=1, C=2 |
| MSc fills after PhD cancel | PhD slot, MSc WL | PhD cancels | Up to 3 MSc promoted |
| FCM on promotion | User on WL | Promoted | FCM "Slot Available!" |

### 5. Supervisor Approval Flow Tests

| Test | Steps | Expected |
|------|-------|----------|
| Full approval flow | Register → Check email → Click approve | Status → APPROVED, FCM sent |
| Full decline flow | Register → Check email → Click decline → Enter reason | Status → DECLINED, reason saved |
| Token expiry | Register → Wait 14 days | Status → EXPIRED |
| Idempotent approve | Click approve link twice | Same result, no error |
| Auto-cancel others | Have 2 PENDING → Approve 1 | Other PENDING cancelled |

**Verify email:** `SELECT * FROM email_queue WHERE email_type = 'REGISTRATION_APPROVAL' ORDER BY id DESC;`

### 6. Attendance Flow Tests

| Test | Setup | Action | Expected |
|------|-------|--------|----------|
| Open attendance | Approved slot, during time | Click Open | QR displayed, session OPEN |
| Too early to open | Slot in future | Try open | ❌ "Too early" |
| Too late to open | Slot ended + 15min | Try open | ❌ "Window closed" |
| Scan QR | Session OPEN | Participant scans | ✅ Attendance recorded |
| Duplicate scan | Already scanned | Scan again | ❌ "Already marked" |
| Session auto-close | Session OPEN | Wait 15 min | Session → CLOSED |
| Scan closed session | Session CLOSED | Try scan | ❌ "Session closed" |

**Verify DB:** `SELECT * FROM attendance WHERE session_id = X;`

### 7. Manual Attendance Tests

| Test | Setup | Action | Expected |
|------|-------|--------|----------|
| Request manual | During window | Submit request + reason | Status = PENDING_APPROVAL |
| Outside window | After window | Submit request | ❌ "Outside window" |
| Approve request | Pending request | Presenter approves | Status → CONFIRMED |
| Reject request | Pending request | Presenter rejects | Status → REJECTED |

### 8. FCM Notification Tests

| Event | Expected Title | Expected Body |
|-------|----------------|---------------|
| Registration approved | "Registration Approved!" | Contains slot date |
| Registration declined | "Registration Declined" | Contains reason |
| WL promotion | "Slot Available!" | Contains slot date |
| First-time user | After setup complete | FCM token registered |

**Verify:** `SELECT * FROM fcm_tokens WHERE bgu_username = 'X';`

### 9. Session/Auth Tests

| Test | Action | Expected |
|------|--------|----------|
| Login success | Valid BGU credentials | Token stored, home screen |
| Login fail | Invalid credentials | Error message |
| Session expired | 401/403 from API | Auto-redirect to login |
| First-time user | New user login | → FirstTimeSetupActivity |

### 10. Slot Status Tests

| Action | Before | After |
|--------|--------|-------|
| 1st MSc registers | FREE | SEMI |
| 2nd MSc registers | SEMI | SEMI |
| 3rd MSc registers | SEMI | FULL |
| MSc cancels (2 left) | FULL | SEMI |
| All cancel | SEMI | FREE |
| PhD registers | FREE | FULL |
| PhD cancels | FULL | FREE |

### 11. Edge Case Tests

| Test | Scenario | Expected |
|------|----------|----------|
| Concurrent registration | 2 users click at same time | 1 wins, 1 gets SLOT_FULL |
| Missing supervisor | Register without supervisor | ❌ Error |
| Username normalization | Login as "USER@BGU.AC.IL" | Normalized to "user" |
| Null fields | Register with no topic | ✅ Stored as NULL |

### 12. Unit Test Commands

```bash
# Run all API tests
cd SemScan-API && ./gradlew test

# Run specific test class
./gradlew test --tests "PresenterHomeServiceTest"
./gradlew test --tests "WaitingListServiceTest"
./gradlew test --tests "RegistrationApprovalServiceTest"

# Run with verbose output
./gradlew test --info
```

### Quick DB Verification Queries

```sql
-- Check registration status
SELECT slot_id, presenter_username, approval_status, registered_at
FROM slot_registration WHERE presenter_username = 'X';

-- Check waiting list
SELECT slot_id, presenter_username, degree, position
FROM waiting_list WHERE slot_id = X ORDER BY position;

-- Check recent logs
SELECT log_timestamp, level, tag, message
FROM app_logs ORDER BY log_id DESC LIMIT 20;

-- Check email queue
SELECT id, to_email, email_type, status, created_at
FROM email_queue ORDER BY id DESC LIMIT 10;

-- Check FCM tokens
SELECT * FROM fcm_tokens WHERE bgu_username = 'X';
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

## Database Schema

**Note**: Full DDL available in database. Key table name mapping below.

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
| `SlotPresenterAttendance` | `slot_presenter_sessions` |
| `AnnounceConfig` | `announce_config` |

### Key Tables
- **users** - bgu_username (PK), degree, supervisor_id
- **slots** - slot_id, slot_date, start_time, end_time, capacity, status
- **slot_registration** - Composite PK (slot_id, presenter_username), approval_status
- **waiting_list** - slot_id, presenter_username, degree, position
- **app_logs** - bgu_username FK to users, level, tag, message
- **app_config** - config_key, config_value, target_system

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
