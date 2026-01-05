# SemScan Test Plan

## Test Users
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

---

## Part 1: API-Level Edge Case Tests (Automated - Claude can run)

These tests can be run via curl/SQL without UI interaction.

### REG: Registration Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| REG-01 | PhD registers to empty slot | PENDING, slot->FULL | PASS |
| REG-02 | MSc registers to empty slot | PENDING, slot->SEMI | PASS |
| REG-03 | PhD blocked by MSc | PHD_BLOCKED_BY_MSC | PASS |
| REG-04 | PhD blocked by 2 MSc | PHD_BLOCKED_BY_MSC | PASS |
| REG-05 | MSc blocked by PhD | SLOT_LOCKED | PASS |
| REG-06 | 3rd MSc fills slot | slot->FULL | PASS |
| REG-07 | 4th MSc blocked | SLOT_FULL | PASS |
| REG-08 | Re-register after DECLINED | Success | PASS |
| REG-09 | Re-register after EXPIRED | Success | PASS |
| REG-10 | Block 2nd pending | PENDING_LIMIT_EXCEEDED | PASS |
| REG-11 | Block 2nd approved | REGISTRATION_LIMIT_EXCEEDED | PASS |
| REG-12 | Past slot registration | Blocked | SKIP |
| REG-13 | No supervisor | NO_SUPERVISOR | PASS |
| REG-14 | Duplicate registration | ALREADY_REGISTERED | PASS |

### WL: Waiting List Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| WL-01 | MSc joins empty WL (PhD slot) | Position=1, type=MSc | PASS |
| WL-02 | PhD joins empty WL (PhD slot) | Position=1, type=PhD | PASS |
| WL-03 | Same degree joins | Success | PASS |
| WL-04 | PhD blocked from MSc-type WL | queue is MSc-only | PASS |
| WL-05 | PhD joins PhD-type WL | Success | PASS |
| WL-06 | MSc blocked from PhD-type WL | queue is PhD-only | PASS |
| WL-07 | WL full (3 users) | WAITING_LIST_FULL | SKIP |
| WL-08 | Already on this WL | ALREADY_ON_WAITING_LIST | PASS |
| WL-09 | Already on another WL | ALREADY_ON_ANOTHER | PASS |
| WL-10 | Has active registration | HAS_ACTIVE_REGISTRATION | PASS |
| WL-11 | Middle user leaves, renumber | Positions 1,2 (not 1,3) | PASS |
| WL-12 | Queue type resets when empty | Any degree can join | PASS |

### APR: Approval Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| APR-01 | Valid approve | Status->APPROVED | PASS |
| APR-02 | Valid decline | Status->DECLINED | PASS |
| APR-03 | Approve already approved | Idempotent (no error) | PASS |
| APR-04 | Decline already declined | Idempotent | PASS |
| APR-05 | Approve after decline | Shows already processed | PASS |
| APR-06 | Expired token | TOKEN_EXPIRED | PASS |
| APR-07 | Invalid token | REGISTRATION_NOT_FOUND | PASS |
| APR-08 | Auto-cancel others on approve | Other PENDING deleted | PASS |

### PRM: Promotion Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| PRM-01 | PhD cancels, MSc on WL | All MSc promoted | PASS |
| PRM-02 | PhD cancels, PhD on WL | PhD promoted | PASS |
| PRM-03 | Multiple MSc promoted | Up to 3 promoted | PASS |
| PRM-04 | Only 1 PhD promoted | Single PhD | PASS |

### CAN: Cancellation Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| CAN-01 | Cancel PENDING | Deleted, no email | PASS |
| CAN-02 | Cancel APPROVED | Deleted, notification | PARTIAL* |
| CAN-03 | Cancel triggers promotion | WL user promoted | PASS |
| CAN-04 | Cancel all in slot | Slot->FREE | PASS |
| CAN-05 | Cancel 1 of 3 MSc | Slot->SEMI | PASS |

*CAN-02 Note: API cancellation works, but email notification not implemented

### STS: Slot Status Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| STS-01 | FREE->SEMI | 1st MSc registers | PASS |
| STS-02 | SEMI->FULL (MSc) | 3rd MSc registers | PASS |
| STS-03 | FREE->FULL (PhD) | PhD registers | PASS |
| STS-04 | FULL->SEMI | 1 MSc cancels | PASS |
| STS-05 | SEMI->FREE | All cancel | PASS |

### USR: Username/Auth Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| USR-01 | Uppercase username | Finds lowercase user | PASS |
| USR-02 | Mixed case | Normalized | PASS |
| USR-03 | Test user bypass | Login with Test123! | PASS |

### FCM: Push Notification Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| FCM-01 | Token registration | Stored in fcm_tokens | PASS |
| FCM-02 | Token update (same user) | Replaces existing token | PASS |
| FCM-03 | Token deletion | Removed from fcm_tokens | PASS |

### CFG: Config Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| CFG-01 | GET /api/v1/config/mobile | Returns all config values | PASS |

### USR: User Profile Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| USR-04 | Check user exists (true) | {"exists": true} | PASS |
| USR-05 | Check user exists (false) | {"exists": false} | PASS |
| USR-06 | Get user profile | Returns user details | PASS |

### ATT: Attendance Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| ATT-01 | Open before time window | TOO_EARLY with message | PASS |

### VAL: Input Validation Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| VAL-01 | Invalid slot ID | Slot not found | PASS |
| VAL-02 | Non-existent user | User not found | PASS |
| VAL-03 | Wrong password login | Invalid credentials | PASS |
| VAL-04 | Missing password | password is required | PASS |
| VAL-05 | Non-existent presenter | Presenter not found | PASS |

### EML: Email Queue Tests
| Test ID | Scenario | Expected | Status |
|---------|----------|----------|--------|
| EML-01 | Queue processing | All emails SENT (0 pending) | PASS |

---

## Part 2: E2E Flow Tests (Manual - Requires User on Emulators)

---

### E2E-01: Complete Registration → Approval Flow

**Goal**: Verify full registration cycle from user registration to supervisor approval with FCM notification.

**Prerequisites**:
- Clean database state (run cleanup commands below)
- Two emulators running with app installed
- Emulator 1: MSc user (testmsc1 / Test123!)
- Check supervisor email access (talbnwork@gmail.com)

**Cleanup Command** (run before test):
```sql
DELETE FROM waiting_list WHERE presenter_username LIKE 'test%';
DELETE FROM slot_registration WHERE presenter_username LIKE 'test%';
UPDATE slots SET status = 'FREE' WHERE slot_date >= CURDATE();
```

| Step | Device | Action | Expected Result |
|------|--------|--------|-----------------|
| 1 | Emulator 1 | Open app, login as `testmsc1` / `Test123!` | Home screen shows available slots |
| 2 | Emulator 1 | Find a GREEN slot (Jan 12 or later) | Slot card shows "Register Now" button |
| 3 | Emulator 1 | Tap "Register Now" button | Dialog shows registration form |
| 4 | Emulator 1 | Fill topic, select supervisor, tap Submit | Toast: "Registration submitted" |
| 5 | Emulator 1 | View the same slot | Shows "Pending Approval" status, YELLOW color |
| 6 | Terminal | Verify email queued | Run: `SELECT * FROM email_queue ORDER BY id DESC LIMIT 1;` |
| 7 | Email | Wait 2 min, check supervisor email | Email with Approve/Decline links |
| 8 | Browser | Click "Approve" link in email | Success page: "Registration Approved" |
| 9 | Emulator 1 | Check notification tray | FCM: "Registration Approved!" |
| 10 | Emulator 1 | Pull down to refresh slot list | Slot shows "Approved" status, RED color |

**Verification Query**:
```sql
SELECT slot_id, presenter_username, approval_status, supervisor_approved_at
FROM slot_registration WHERE presenter_username = 'testmsc1';
```

---

### E2E-02: PhD/MSc Conflict (Visual Verification)

**Goal**: Verify PhD takes exclusive slot and MSc sees "Join Waiting List" button.

**Prerequisites**:
- Two emulators running
- Emulator 1: PhD user (testphd1 / Test123!)
- Emulator 2: MSc user (testmsc1 / Test123!)

**Cleanup Command**:
```sql
DELETE FROM waiting_list WHERE slot_id = 234;
DELETE FROM slot_registration WHERE slot_id = 234;
UPDATE slots SET status = 'FREE' WHERE slot_id = 234;
```

| Step | Device | Action | Expected Result |
|------|--------|--------|-----------------|
| 1 | Emulator 1 | Login as `testphd1` / `Test123!` | Home screen |
| 2 | Emulator 1 | Find slot 234 (should be GREEN) | Shows "Register Now" button |
| 3 | Emulator 1 | Tap "Register Now", fill form, submit | Toast: "Registration submitted" |
| 4 | Emulator 1 | View slot 234 | Slot is now RED, shows "Pending Approval" |
| 5 | Emulator 2 | Login as `testmsc1` / `Test123!` | Home screen |
| 6 | Emulator 2 | Find slot 234 | Slot is RED |
| 7 | Emulator 2 | Tap on slot 234 | Shows "Join Waiting List" button (NOT Register) |
| 8 | Emulator 2 | Tap "Join Waiting List" | Toast: "Added to waiting list" |
| 9 | Emulator 2 | View slot 234 again | Shows "Cancel Waiting List" button, position #1 |

**Verification Query**:
```sql
SELECT * FROM waiting_list WHERE slot_id = 234;
-- Should show testmsc1 at position 1
```

---

### E2E-03: Waiting List Promotion Flow

**Goal**: Verify automatic promotion from waiting list when slot opens up, with FCM notification.

**Prerequisites**:
- Two emulators running
- Emulator 1: testmsc1 (will register and cancel)
- Emulator 2: testmsc2 (will join waiting list)

**Setup Command** (create a full slot):
```sql
-- Insert 2 MSc registrations to make slot SEMI (need 3rd to fill)
INSERT INTO slot_registration (slot_id, presenter_username, degree, approval_status, supervisor_name, supervisor_email)
VALUES
(234, 'testmsc3', 'MSc', 'APPROVED', 'Dr. Test', 'test@bgu.ac.il'),
(234, 'testmsc4', 'MSc', 'APPROVED', 'Dr. Test', 'test@bgu.ac.il');
UPDATE slots SET status = 'SEMI' WHERE slot_id = 234;
```

| Step | Device | Action | Expected Result |
|------|--------|--------|-----------------|
| 1 | Emulator 1 | Login as `testmsc1` / `Test123!` | Home screen |
| 2 | Emulator 1 | Find slot 234 (YELLOW - 2 MSc) | Shows "Register Now" button |
| 3 | Emulator 1 | Register for slot 234 | Slot becomes RED (FULL with 3 MSc) |
| 4 | Emulator 2 | Login as `testmsc2` / `Test123!` | Home screen |
| 5 | Emulator 2 | Find slot 234 (RED - FULL) | Shows "Join Waiting List" button |
| 6 | Emulator 2 | Tap "Join Waiting List" | Toast: "Position 1 on waiting list" |
| 7 | Emulator 1 | Find slot 234, tap to view details | Shows "Cancel Registration" button |
| 8 | Emulator 1 | Tap "Cancel Registration", confirm | Toast: "Registration cancelled" |
| 9 | Emulator 2 | Check notification tray (within 5 sec) | FCM: "Slot Available!" |
| 10 | Emulator 2 | Pull down to refresh | Slot 234 shows "Pending Approval" (promoted!) |

**Verification Query**:
```sql
-- testmsc2 should now be in slot_registration, not waiting_list
SELECT * FROM slot_registration WHERE slot_id = 234 AND presenter_username = 'testmsc2';
SELECT * FROM waiting_list WHERE slot_id = 234;  -- Should be empty or not have testmsc2
```

---

### E2E-04: Decline → Re-register Flow

**Goal**: Verify user can re-register after supervisor declines.

**Prerequisites**:
- One emulator with testmsc1
- Access to supervisor email

| Step | Device | Action | Expected Result |
|------|--------|--------|-----------------|
| 1 | Emulator 1 | Login as `testmsc1` / `Test123!` | Home screen |
| 2 | Emulator 1 | Register for an empty slot (e.g., slot 235) | Shows "Pending Approval" |
| 3 | Terminal | Get approval token | `SELECT approval_token FROM slot_registration WHERE presenter_username='testmsc1' AND slot_id=235;` |
| 4 | Browser | Open decline URL | `http://132.72.50.53:8080/api/v1/decline/{token}` |
| 5 | Browser | Enter decline reason, submit | Success page: "Registration Declined" |
| 6 | Emulator 1 | Check notification tray | FCM: "Registration Declined" with reason |
| 7 | Emulator 1 | Pull down to refresh | Slot 235 shows "Register Now" button again (GREEN) |
| 8 | Emulator 1 | Tap "Register Now" again | Can register again |
| 9 | Emulator 1 | Submit new registration | Shows "Pending Approval" |

**Verification Query**:
```sql
SELECT slot_id, approval_status, registered_at, supervisor_declined_reason
FROM slot_registration WHERE presenter_username = 'testmsc1' AND slot_id = 235;
-- Should show PENDING (new registration after decline)
```

---

### E2E-05: QR Attendance Flow

**Goal**: Verify presenter can open attendance and participant can scan QR.

**Prerequisites**:
- Two emulators (or one emulator + physical phone)
- Emulator 1: Presenter with APPROVED registration for TODAY's slot
- Emulator 2: Participant user
- Current time must be within slot's time window

**Setup Command** (create approved registration for today):
```sql
-- Find today's slot
SELECT slot_id, start_time, end_time FROM slots WHERE slot_date = CURDATE();

-- Create approved registration (use slot_id from above, e.g., 229)
INSERT INTO slot_registration (slot_id, presenter_username, degree, approval_status, supervisor_name, supervisor_email)
VALUES (229, 'testmsc1', 'MSc', 'APPROVED', 'Dr. Test', 'test@bgu.ac.il')
ON DUPLICATE KEY UPDATE approval_status = 'APPROVED';
UPDATE slots SET status = 'SEMI' WHERE slot_id = 229;
```

| Step | Device | Action | Expected Result |
|------|--------|--------|-----------------|
| 1 | Emulator 1 | Login as `testmsc1` (presenter) | Home screen |
| 2 | Emulator 1 | Find today's slot (229) | Shows "Open Attendance" button |
| 3 | Emulator 1 | Tap "Open Attendance" | QR code displayed with timer |
| 4 | Emulator 2 | Login as `testmsc2` (participant) | Home screen |
| 5 | Emulator 2 | Tap "Scan QR" or participant mode | Camera opens |
| 6 | Emulator 2 | Point camera at Emulator 1's QR | Toast: "Attendance marked!" |
| 7 | Emulator 1 | View attendance list | testmsc2 appears in list |
| 8 | Emulator 1 | Wait 15 min OR tap "Close Session" | Session closes |
| 9 | Emulator 2 | Try to scan again | Error: "Session closed" |

**Verification Query**:
```sql
SELECT a.*, s.start_time
FROM attendance a
JOIN sessions s ON a.session_id = s.session_id
WHERE a.student_username = 'testmsc2'
ORDER BY a.attendance_id DESC LIMIT 1;
```

**Note**: If current time is before slot time, you'll see "Cannot open session yet" message.

---

### E2E-06: Slot Color Coding Verification

**Goal**: Verify slot cards show correct colors based on capacity.

**Prerequisites**:
- One emulator
- Multiple slots in different states

**Setup Commands**:
```sql
-- Slot A: Empty (GREEN)
DELETE FROM slot_registration WHERE slot_id = 233;
UPDATE slots SET status = 'FREE' WHERE slot_id = 233;

-- Slot B: 1 MSc (YELLOW)
INSERT INTO slot_registration (slot_id, presenter_username, degree, approval_status, supervisor_name, supervisor_email)
VALUES (234, 'testmsc1', 'MSc', 'APPROVED', 'Dr. Test', 'test@bgu.ac.il')
ON DUPLICATE KEY UPDATE approval_status = 'APPROVED';
UPDATE slots SET status = 'SEMI' WHERE slot_id = 234;

-- Slot C: 3 MSc or 1 PhD (RED)
INSERT INTO slot_registration (slot_id, presenter_username, degree, approval_status, supervisor_name, supervisor_email)
VALUES (235, 'testphd1', 'PhD', 'APPROVED', 'Dr. Test', 'test@bgu.ac.il')
ON DUPLICATE KEY UPDATE approval_status = 'APPROVED';
UPDATE slots SET status = 'FULL' WHERE slot_id = 235;
```

| Step | Device | Action | Expected Result |
|------|--------|--------|-----------------|
| 1 | Emulator | Login as any test user | Home screen with slot list |
| 2 | Emulator | Find slot 233 | GREEN background gradient |
| 3 | Emulator | Find slot 234 | YELLOW background gradient |
| 4 | Emulator | Find slot 235 | RED background gradient |
| 5 | Emulator | Tap on GREEN slot | Shows "Register Now" button |
| 6 | Emulator | Tap on YELLOW slot | Shows "Register Now" (if MSc) or "Join Waiting List" (if PhD) |
| 7 | Emulator | Tap on RED slot | Shows "Join Waiting List" button |

**Color Mapping**:
| Status | Capacity Used | Color | Drawable |
|--------|---------------|-------|----------|
| FREE | 0 | Green | `bg_slot_green_gradient` |
| SEMI | 1-2 MSc | Yellow | `bg_slot_yellow_gradient` |
| FULL | 3 MSc or 1 PhD | Red | `bg_slot_red_gradient` |

---

### E2E-07: PhD Can't Fit - Waiting List Flow

**Goal**: Verify PhD user sees "Join Waiting List" when slot has 2 MSc (not enough capacity for PhD).

**Setup Command**:
```sql
-- Create slot with 2 MSc (1 capacity left, PhD needs 3)
DELETE FROM slot_registration WHERE slot_id = 234;
DELETE FROM waiting_list WHERE slot_id = 234;
INSERT INTO slot_registration (slot_id, presenter_username, degree, approval_status, supervisor_name, supervisor_email)
VALUES
(234, 'testmsc1', 'MSc', 'APPROVED', 'Dr. Test', 'test@bgu.ac.il'),
(234, 'testmsc2', 'MSc', 'APPROVED', 'Dr. Test', 'test@bgu.ac.il');
UPDATE slots SET status = 'SEMI' WHERE slot_id = 234;
```

| Step | Device | Action | Expected Result |
|------|--------|--------|-----------------|
| 1 | Emulator | Login as `testphd1` / `Test123!` (PhD) | Home screen |
| 2 | Emulator | Find slot 234 (YELLOW - 2 MSc) | Shows YELLOW, "1/3 Available" |
| 3 | Emulator | Tap on slot 234 | Shows "Join Waiting List" button (NOT Register) |
| 4 | Emulator | Check message | "Not enough capacity for PhD registration" |
| 5 | Emulator | Tap "Join Waiting List" | Toast: "Added to waiting list" |
| 6 | Emulator | View slot again | Shows "Cancel Waiting List", position #1 |

**Verification Query**:
```sql
SELECT * FROM waiting_list WHERE slot_id = 234 AND presenter_username = 'testphd1';
```

---

## Commands

### Re-run API tests
Ask Claude: "Run the API edge case tests"

### Clean up for fresh test
DELETE FROM waiting_list;
DELETE FROM slot_registration;
UPDATE slots SET status = 'FREE' WHERE slot_date >= CURDATE();
