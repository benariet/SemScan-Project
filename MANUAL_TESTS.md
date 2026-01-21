# SemScan Manual Tests

## Overview
These tests require real devices, real users, and real-time interactions that cannot be fully automated.

---

## Test Credentials

| Username | Password | Degree | Auth Type |
|----------|----------|--------|-----------|
| `talguest2` | `tc2xqVds` | PhD | BGU SOAP |
| `talguest3` | `kbm7Xzfk` | MSc | BGU SOAP |
| `talguest4` | `atpgK2zc` | MSc | BGU SOAP |
| `benariet` | `Taltal123!` | MSc | BGU SOAP |
| `amarrev` | `Revital1990%` | MSc | BGU SOAP |
| `testmsc1-4` | `Test123!` | MSc | Bypass |
| `testphd1-2` | `Test123!` | PhD | Bypass |

---

## 1. Real Push Notification Delivery

**Purpose**: Verify FCM notifications actually appear on device

### Approval Notification
| Step | Action | Expected |
|------|--------|----------|
| 1 | Login as `talguest3` on Android device | Home screen loads |
| 2 | Register for an empty slot | Shows "Pending Approval" |
| 3 | Check supervisor's email | Approval email received |
| 4 | Click **Approve** link in email | Browser shows success page |
| 5 | Check Android device (within 5 sec) | Push: "Registration Approved!" |

### Decline Notification
| Step | Action | Expected |
|------|--------|----------|
| 1 | Register for another slot | Pending status |
| 2 | Click **Decline** in email | Enter reason, submit |
| 3 | Check Android device | Push: "Registration Declined" with reason |

---

## 2. QR Code Attendance Flow (Two Devices)

**Requires**: Two Android devices

| Step | Device | Action | Expected |
|------|--------|--------|----------|
| 1 | A | Login as presenter with approved slot | Home screen |
| 2 | A | Tap "Open Attendance" | QR code + timer |
| 3 | B | Login as participant | Home screen |
| 4 | B | Tap "Scan QR" | Camera opens |
| 5 | B | Scan QR from Device A | "Attendance Marked!" |
| 6 | A | Check attendance list | Participant appears |
| 7 | B | Scan same QR again | "Already marked" error |
| 8 | - | Wait 15 min | Session auto-closes |

---

## 3. Session Timeout Auto-Redirect

**Purpose**: Verify 401/403 triggers login redirect

| Step | Action | Expected |
|------|--------|----------|
| 1 | Login to app normally | Home screen |
| 2 | On server, invalidate session (delete user or wait for JWT expiry) | |
| 3 | In app, pull-to-refresh | Redirects to login |
| 4 | Toast message | "Session expired" |

**Server command to force logout:**
```sql
-- Option: Delete user temporarily
DELETE FROM users WHERE bgu_username = 'YOUR_USERNAME';
```

---

## 4. First-Time User Setup Flow

| Step | Action | Expected |
|------|--------|----------|
| 1 | Delete user if exists: | |
| | `DELETE FROM fcm_tokens WHERE bgu_username = 'talguest4';` | |
| | `DELETE FROM users WHERE bgu_username = 'talguest4';` | |
| 2 | Login with `talguest4` / `atpgK2zc` | Setup screen |
| 3 | Select degree (MSc/PhD) | Dropdown works |
| 4 | Select supervisor | Autocomplete works |
| 5 | Tap "Save" | "Profile saved" |
| 6 | Verify DB: `SELECT * FROM users WHERE bgu_username = 'talguest4';` | Record exists |
| 7 | Verify FCM: `SELECT * FROM fcm_tokens WHERE bgu_username = 'talguest4';` | Token exists |

---

## 5. Waiting List Promotion Notification

| Step | Action | Expected |
|------|--------|----------|
| 1 | Find FULL slot with 3 MSc | |
| 2 | Login as `amarrev` on device | |
| 3 | Join waiting list | Position 1 |
| 4 | As another user, cancel registration | |
| 5 | Check `amarrev` device | Push: "Slot Available!" |
| 6 | Open app | Shows pending approval |

---

## 6. Supervisor Email Flow

### Approval
| Step | Action | Expected |
|------|--------|----------|
| 1 | Register for slot | Email sent to supervisor |
| 2 | Open email (Gmail/Outlook) | From "SemScan System" |
| 3 | Verify content | Student name, slot date, topic |
| 4 | Click green "Approve" button | Success page in browser |
| 5 | Click again | Still success (idempotent) |

### Decline
| Step | Action | Expected |
|------|--------|----------|
| 1 | Register for another slot | Email sent |
| 2 | Click red "Decline" button | Reason form |
| 3 | Enter reason, submit | Declined page |
| 4 | Check DB for reason | Stored in `supervisor_declined_reason` |

---

## 7. PhD Capacity - Join Waiting List Button

**Purpose**: PhD sees "Join Waiting List" when not enough capacity

### Setup
```sql
-- Create slot with 2 MSc (1 capacity remaining, not enough for PhD weight=3)
DELETE FROM slot_registration WHERE slot_id = 230;
INSERT INTO slot_registration (slot_id, presenter_username, degree, topic, supervisor_name, supervisor_email, approval_status)
VALUES
  (230, 'testmsc1', 'MSc', 'Topic 1', 'Prof', 'prof@bgu.ac.il', 'PENDING'),
  (230, 'testmsc2', 'MSc', 'Topic 2', 'Prof', 'prof@bgu.ac.il', 'PENDING');
UPDATE slots SET status = 'SEMI' WHERE slot_id = 230;
```

| Step | Action | Expected |
|------|--------|----------|
| 1 | Login as `talguest2` (PhD) | |
| 2 | Find slot 230 | Shows "1/3 Available" |
| 3 | Verify button | "Join Waiting List" (NOT "Register") |
| 4 | Verify message | "Not enough capacity for PhD" |
| 5 | Tap button | Added to waiting list |

---

## 8. Slot Card Colors (Visual)

| Slot State | Color | Drawable |
|------------|-------|----------|
| Empty (FREE) | Green | `bg_slot_green_gradient.xml` |
| Partial (SEMI) | Yellow | `bg_slot_yellow_gradient.xml` |
| Full (FULL) | Red | `bg_slot_red_gradient.xml` |

**Test**: Open presenter home, verify colors match slot states

---

## 9. Offline / Network Error Handling

| Step | Action | Expected |
|------|--------|----------|
| 1 | Login normally | |
| 2 | Enable Airplane Mode | |
| 3 | Refresh slot list | Error toast, no crash |
| 4 | Try to register | Error handling |
| 5 | Disable Airplane Mode | |
| 6 | Retry | Success |

---

## 10. Concurrent Registration (Race Condition)

**Requires**: Two devices, slot with 1 spot left

| Device A | Device B |
|----------|----------|
| Login as testmsc1 | Login as testmsc2 |
| Find slot with 1 spot | Same slot |
| Tap "Register" simultaneously | |
| **One succeeds** | **One gets "Slot Full"** |

---

## 11. Manual Attendance Request

| Step | Device | Action | Expected |
|------|--------|--------|----------|
| 1 | A (Presenter) | Open attendance | QR displayed |
| 2 | B (Participant) | Tap "Request Manual" | Form |
| 3 | B | Enter reason, submit | "Request submitted" |
| 4 | A | Check "Pending Requests" | Request visible |
| 5 | A | Tap "Approve" | Approved |
| 6 | B | Refresh | "Attendance Confirmed" |

---

## 12. Session Auto-Close (15 Minutes)

| Step | Action | Expected |
|------|--------|----------|
| 1 | Open attendance | Timer shows ~15:00 |
| 2 | Wait for timer to expire | |
| 3 | Try to scan QR | "Session closed" |
| 4 | Check DB: `SELECT status FROM sessions WHERE session_id = X;` | CLOSED |

**Config**: `presenter_close_session_duration_minutes` (default: 15)

---

## 13. Announcement Banner

### Enable
```sql
UPDATE announce_config SET
  is_active = 1,
  title = 'System Maintenance',
  message = 'Scheduled maintenance tonight at 10 PM',
  is_blocking = 0
WHERE id = 1;
```

| Step | Action | Expected |
|------|--------|----------|
| 1 | Open app | Banner at top |
| 2 | Verify title/message | Matches DB |
| 3 | Set `is_blocking = 1` | Must dismiss to continue |

### Disable
```sql
UPDATE announce_config SET is_active = 0 WHERE id = 1;
```

---

## 14. Real BGU Authentication

| Step | Action | Expected |
|------|--------|----------|
| 1 | Login with `benariet` / `Taltal123!` | Success |
| 2 | Check logs: `sudo journalctl -u semscan-api -f` | SOAP call logged |
| 3 | Try wrong password | "Invalid credentials" |

---

# Configuration Strategy

## Goal: Minimize APK Updates

Since users install the app once but you can update the API anytime, push decisions to the server.

## Currently Configurable (No APK Update Needed)

### Via `app_config` Table

| Config Key | What It Controls | Change Impact |
|------------|------------------|---------------|
| `phd.capacity.weight` | PhD slot weight (default: 3) | Immediate |
| `waiting.list.limit.per.slot` | Max WL size (default: 3) | Immediate |
| `presenter_close_session_duration_minutes` | Session auto-close time | Immediate |
| `approval_token_expiry_days` | Email link expiry (default: 14) | New tokens only |
| `toast_duration_error` | Error toast display time (ms) | Immediate |
| `toast_duration_success` | Success toast time (ms) | Immediate |
| `manual_attendance_window_before_minutes` | Manual attendance window | Immediate |
| `manual_attendance_window_after_minutes` | Manual attendance window | Immediate |
| `presenter_slot_open_window_before_minutes` | When presenter can open | Immediate |
| `presenter_slot_open_window_after_minutes` | When presenter can open | Immediate |
| `connection_timeout_seconds` | HTTP timeout | Next app launch |
| `server_url` | API URL | Next app launch |

### Via `announce_config` Table

| Field | Purpose |
|-------|---------|
| `is_active` | Show/hide banner |
| `title` | Banner title |
| `message` | Banner content |
| `is_blocking` | Force user to dismiss |
| `start_at` / `end_at` | Scheduled display |

### Via Database Direct

| Change | How |
|--------|-----|
| Slot capacity | `UPDATE slots SET capacity = X` |
| Slot dates/times | `UPDATE slots SET slot_date = ...` |
| User degree | `UPDATE users SET degree = ...` |
| Cancel registration | `DELETE FROM slot_registration WHERE ...` |
| Clear waiting list | `DELETE FROM waiting_list WHERE ...` |

## Recommendations for Future API-Driven Features

### 1. Feature Flags (Add to `app_config`)
```sql
-- Example: Disable waiting list feature
INSERT INTO app_config (config_key, config_value, config_type, target_system)
VALUES ('feature.waiting_list.enabled', 'true', 'BOOLEAN', 'BOTH');

-- Example: Disable manual attendance
INSERT INTO app_config (config_key, config_value, config_type, target_system)
VALUES ('feature.manual_attendance.enabled', 'true', 'BOOLEAN', 'BOTH');
```

**Mobile app checks these flags before showing UI elements.**

### 2. Dynamic UI Text (Add to `app_config`)
```sql
-- Example: Custom registration success message
INSERT INTO app_config (config_key, config_value, config_type, target_system)
VALUES ('ui.message.registration_success', 'Your registration is pending supervisor approval.', 'STRING', 'MOBILE');

-- Example: Custom error message
INSERT INTO app_config (config_key, config_value, config_type, target_system)
VALUES ('ui.message.slot_full', 'This slot is full. Join the waiting list?', 'STRING', 'MOBILE');
```

### 3. Business Rules (Already in API, but document)

| Rule | Where Controlled | Config Key |
|------|------------------|------------|
| PhD/MSc exclusivity | API code | (hardcoded - consider making configurable) |
| Max pending registrations | API code | Could add `max_pending_registrations_msc`, `max_pending_registrations_phd` |
| Registration limit per user | API code | Could add `max_approved_registrations_per_user` |
| Waiting list promotion logic | API code | N/A (complex logic) |

### 4. Suggested New Config Keys

```sql
-- Registration limits (currently hardcoded)
INSERT INTO app_config VALUES (NULL, 'registration.max_pending.msc', '2', 'INTEGER', 'API', 'registration', 'Max pending registrations for MSc', NOW(), NULL);
INSERT INTO app_config VALUES (NULL, 'registration.max_pending.phd', '1', 'INTEGER', 'API', 'registration', 'Max pending registrations for PhD', NOW(), NULL);
INSERT INTO app_config VALUES (NULL, 'registration.max_approved', '1', 'INTEGER', 'API', 'registration', 'Max approved registrations per user', NOW(), NULL);

-- Feature flags
INSERT INTO app_config VALUES (NULL, 'feature.waiting_list.enabled', 'true', 'BOOLEAN', 'BOTH', 'features', 'Enable waiting list feature', NOW(), NULL);
INSERT INTO app_config VALUES (NULL, 'feature.manual_attendance.enabled', 'true', 'BOOLEAN', 'BOTH', 'features', 'Enable manual attendance requests', NOW(), NULL);
INSERT INTO app_config VALUES (NULL, 'feature.qr_attendance.enabled', 'true', 'BOOLEAN', 'BOTH', 'features', 'Enable QR attendance', NOW(), NULL);

-- UI customization
INSERT INTO app_config VALUES (NULL, 'ui.slot_card.show_presenter_name', 'true', 'BOOLEAN', 'MOBILE', 'ui', 'Show presenter name on slot cards', NOW(), NULL);
INSERT INTO app_config VALUES (NULL, 'ui.slot_card.show_topic', 'true', 'BOOLEAN', 'MOBILE', 'ui', 'Show topic on slot cards', NOW(), NULL);
```

## What REQUIRES APK Update

| Change | Why |
|--------|-----|
| New screens/activities | Layout XML in APK |
| New API endpoints | Retrofit interface in APK |
| Major UI redesign | Layouts in APK |
| New permissions | AndroidManifest in APK |
| New libraries/SDKs | Dependencies in APK |
| Core navigation changes | Activity flow in APK |

## Config Cache

**Important**: Mobile app caches config for `config_cache_ttl_hours` (default: 24).

To force immediate config refresh:
1. User can: Kill app and reopen
2. You can: Reduce `config_cache_ttl_hours` temporarily
3. Or: Add "refresh config" button in settings (future feature)

---

# Server Commands Reference

## Check Service
```bash
sudo systemctl status semscan-api
sudo journalctl -u semscan-api -f
```

## Database Queries
```bash
mysql -u semscan_admin -pTAL1234 semscan_db
```

```sql
-- Recent logs
SELECT * FROM app_logs ORDER BY log_id DESC LIMIT 20;

-- Registrations for slot
SELECT * FROM slot_registration WHERE slot_id = X;

-- Waiting list for slot
SELECT * FROM waiting_list WHERE slot_id = X ORDER BY position;

-- Email queue status
SELECT status, COUNT(*) FROM email_queue GROUP BY status;

-- FCM tokens
SELECT * FROM fcm_tokens;

-- Config values
SELECT config_key, config_value FROM app_config WHERE target_system IN ('MOBILE', 'BOTH');
```

## Restart API (After Config Changes)
```bash
sudo systemctl restart semscan-api
```
