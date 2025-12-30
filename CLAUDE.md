# SemScan Project

## Project Overview
SemScan is a **seminar attendance tracking system** for BGU (Ben-Gurion University). It consists of:
- **SemScan** - Android mobile app for presenters and participants
- **SemScan-API** - Spring Boot backend API

### Key Features
- **Presenter Registration**: PhD/MSc students register for presentation slots (PhD takes 2 capacity, MSc takes 1)
- **Supervisor Approval**: Registrations require supervisor email approval via token link
- **Waiting List**: When slots are full, users can join waiting list and get auto-promoted
- **QR Attendance**: Presenters open attendance window, participants scan QR to mark attendance
- **Session Timeout**: 401/403 responses trigger auto-redirect to login

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
- `device_info` - "Samsung SM-G991B (Android 12, SDK 31)"
- `app_version` - Mobile app version
- `payload` - Request/response details
- `exception_type` - For errors
- `stack_trace` - Full stack trace for errors
- `source` - API or MOBILE

### Key Log Tags
- LOGIN_ATTEMPT, LOGIN_SUCCESS, LOGIN_FAILED
- API_REGISTER_REQUEST, API_REGISTER_RESPONSE
- REGISTRATION_ATTEMPT, REGISTRATION_SLOT_STATE, SLOT_REGISTRATION_SUCCESS
- CANCELLATION_ATTEMPT, CANCELLATION_SLOT_STATE, CANCELLATION_SUCCESS
- API_WAITING_LIST_ADD_RESPONSE, API_WAITING_LIST_CANCEL_REQUEST
- APPROVAL_ATTEMPT, EMAIL_REGISTRATION_APPROVAL_EMAIL_SENT
- WAITING_LIST_PROMOTED_AFTER_CANCELLATION

## Capacity System
- **PhD students**: Weight = 2 (takes whole slot)
- **MSc students**: Weight = 1
- **Slot capacity**: Usually 2 (can fit 2 MSc or 1 PhD)

## Slot Colors (Mobile UI)
- **Green**: Available (0 registrations)
- **Yellow**: Partially filled (has registrations but not full)
- **Red**: Full (approved >= capacity)

## Build Rules

### APK Naming
After building the Android APK, always rename `SemScan-debug.apk` to `semscan.apk`:
```bash
cp "SemScan/build/outputs/apk/debug/SemScan-debug.apk" "SemScan/build/outputs/apk/debug/semscan.apk"
```

## Build Locations
- **APK**: `SemScan/build/outputs/apk/debug/semscan.apk`
- **JAR**: `SemScan-API/build/libs/SemScan-API-0.0.1-SNAPSHOT.jar`

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
curl -k -u "webmaster:$SEMSCAN_PASSWORD" -T "SemScan/build/outputs/apk/debug/semscan.apk" "sftp://132.72.50.53/opt/semscan-api/semscan.apk"

# Upload JAR (keep full version name, overwrites existing)
curl -k -u "webmaster:$SEMSCAN_PASSWORD" -T "SemScan-API/build/libs/SemScan-API-0.0.1-SNAPSHOT.jar" "sftp://132.72.50.53/opt/semscan-api/SemScan-API-0.0.1-SNAPSHOT.jar"

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
  adb -s $device install -r SemScan/build/outputs/apk/debug/semscan.apk
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
- `GET /api/v1/approval/{token}` - Show approval page
- `POST /api/v1/approval/{token}/approve` - Approve registration
- `POST /api/v1/approval/{token}/reject` - Reject registration

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
- `PHD` - PhD student (weight=2)
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
| `phd.capacity.weight` | 2 | PhD capacity weight |
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
- Format: `http://132.72.50.53:8080/api/v1/approval/{token}`
- Expiration: 72 hours (configurable)
- Actions: `/approve` or `/reject`

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

### Test Users (Development)
- Create via login (BGU credentials required)
- Or insert directly into `users` table

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
