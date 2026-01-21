# SemScan App - Flow Testing Guide

## Quick Reference

| Flow | Priority | Estimated Time |
|------|----------|----------------|
| 1. Login | HIGH | 5 min |
| 2. First Time Setup | HIGH | 10 min |
| 3. Role Selection | MEDIUM | 3 min |
| 4. Presenter Registration | HIGH | 15 min |
| 5. Presenter Start Session | HIGH | 10 min |
| 6. Participant Scan QR | HIGH | 5 min |
| 7. Manual Attendance | MEDIUM | 5 min |
| 8. Settings | LOW | 5 min |
| 9. Logout | MEDIUM | 3 min |

---

## FLOW 1: LOGIN

### Test 1.1: Valid Login
- [ ] Open app on login screen
- [ ] Verify WiFi warning banner is visible (BGU-WIFI required)
- [ ] Verify "Remember Me" checkbox is checked by default
- [ ] Enter valid BGU username
- [ ] Enter valid password
- [ ] Tap "Log in"
- [ ] Verify loading state shows "Logging in..."
- [ ] Verify navigation to next screen (Setup or Role Picker)

### Test 1.2: Invalid Credentials
- [ ] Enter invalid username or password
- [ ] Tap "Log in"
- [ ] Verify error message: "Invalid username or password"
- [ ] Verify login button is re-enabled

### Test 1.3: Empty Fields
- [ ] Leave username empty, tap "Log in"
- [ ] Verify error: "Please enter your username"
- [ ] Leave password empty, tap "Log in"
- [ ] Verify error: "Please enter your password"

### Test 1.4: Remember Me
- [ ] Login with "Remember Me" checked
- [ ] Logout
- [ ] Return to login screen
- [ ] Verify username and password are pre-filled
- [ ] Verify checkbox is still checked

### Test 1.5: Network Error
- [ ] Disconnect from WiFi
- [ ] Attempt login
- [ ] Verify network error message shown

---

## FLOW 2: FIRST TIME SETUP (New User)

### Test 2.1: Complete Setup Flow
- [ ] Login as new user (not in database)
- [ ] Verify navigation to "Complete Your Profile" screen

**Step 1: Personal Info**
- [ ] Enter First Name
- [ ] Enter Last Name
- [ ] Enter National ID (9 digits)
- [ ] Check "I confirm this ID is correct"
- [ ] Tap Next

**Step 2: Degree Selection**
- [ ] Select M.Sc. or Ph.D.
- [ ] Verify selection is highlighted

**Step 3: Participation Preference**
- [ ] For M.Sc.: Select Presenter/Participant/Both
- [ ] For Ph.D.: Presenter only option

**Step 4: Complete**
- [ ] Tap "Finish setup"
- [ ] Verify navigation to Role Picker

### Test 2.2: National ID Validation
- [ ] Enter less than 9 digits
- [ ] Verify error: "ID must be exactly 9 digits"
- [ ] Enter invalid checksum
- [ ] Verify error: "ID checksum validation failed"
- [ ] Don't check confirmation box
- [ ] Verify error: "Please confirm the ID is correct"

### Test 2.3: Required Fields
- [ ] Leave First Name empty
- [ ] Verify error shown
- [ ] Leave Last Name empty
- [ ] Verify error shown

---

## FLOW 3: ROLE SELECTION

### Test 3.1: Select Presenter Role
- [ ] On Role Picker screen, tap "Presenter" card
- [ ] Verify navigation to Presenter Home

### Test 3.2: Select Participant Role
- [ ] On Role Picker screen, tap "Participant" card
- [ ] Verify navigation to Student Home

### Test 3.3: Role Persistence
- [ ] Select a role
- [ ] Close app completely
- [ ] Reopen app
- [ ] Verify auto-navigation to correct home screen

---

## FLOW 4: PRESENTER - SLOT REGISTRATION

### Test 4.1: View Available Slots
- [ ] On Presenter Home, verify presentation details card shows
- [ ] Tap "Select a presentation slot" card
- [ ] Verify slot list loads
- [ ] Verify slots show: date, time, room, capacity

### Test 4.2: Complete Presentation Details First
- [ ] Try to register without filling presentation details
- [ ] Verify error: "Please set your supervisor info in Settings first"
- [ ] Fill in: Topic, Abstract, Supervisor Name, Supervisor Email
- [ ] Tap Save
- [ ] Verify "Details saved" message

### Test 4.3: Register for Available Slot
- [ ] Select an available slot (not full)
- [ ] Verify confirmation dialog shows supervisor info
- [ ] Tap "Register"
- [ ] Verify success message
- [ ] Verify slot shows "Pending Approval" status

### Test 4.4: Slot Full - Join Waiting List
- [ ] Find a full slot
- [ ] Tap on it
- [ ] Verify "Join Waiting List" option appears
- [ ] Tap "Join Waiting List"
- [ ] Verify waiting list position shown

### Test 4.5: Cancel Registration
- [ ] Go to "My registered slot"
- [ ] Tap "Cancel registration"
- [ ] Confirm cancellation
- [ ] Verify slot is freed

### Test 4.6: Leave Waiting List
- [ ] While on waiting list, tap slot
- [ ] Tap "Cancel Waiting List"
- [ ] Verify removed from waiting list

---

## FLOW 5: PRESENTER - START SESSION

### Test 5.1: Open Session (Show QR)
- [ ] Have an approved slot registration
- [ ] On Presenter Home, tap "Open session QR" card
- [ ] Verify QR code displays
- [ ] Verify session info shows (time, date)

### Test 5.2: Cannot Open Too Early
- [ ] Try to open session more than 10 min before slot time
- [ ] Verify error: "Can only open 10 minutes before slot"

### Test 5.3: End Session
- [ ] While QR is showing, tap "End Session"
- [ ] Confirm
- [ ] Verify session closed
- [ ] Verify navigation back to home

---

## FLOW 6: PARTICIPANT - SCAN QR CODE

### Test 6.1: Successful QR Scan
- [ ] On Student Home, tap "Scan Attendance" card
- [ ] Verify camera opens
- [ ] Scan presenter's QR code
- [ ] Verify "Attendance Confirmed" message
- [ ] Verify return to home screen

### Test 6.2: No Open Sessions
- [ ] When no sessions are open
- [ ] Tap "Scan Attendance"
- [ ] Verify error message about no open sessions

### Test 6.3: Already Scanned
- [ ] Scan same QR code twice
- [ ] Verify "Already marked present" message

### Test 6.4: Invalid QR Code
- [ ] Scan random QR code (not from SemScan)
- [ ] Verify error handling

---

## FLOW 7: PARTICIPANT - MANUAL ATTENDANCE

### Test 7.1: Submit Manual Request
- [ ] On Student Home, tap "Manual Attendance Request"
- [ ] Verify list of open sessions loads
- [ ] Select a session
- [ ] Enter reason for manual request
- [ ] Tap Submit
- [ ] Verify success message

### Test 7.2: No Open Sessions
- [ ] When no sessions available
- [ ] Verify empty state message

---

## FLOW 8: SETTINGS

### Test 8.1: View Profile
- [ ] Open Settings from menu
- [ ] Verify username displayed (read-only)
- [ ] Verify First Name, Last Name shown
- [ ] Verify National ID shown
- [ ] Verify Degree shown

### Test 8.2: Report Bug
- [ ] Tap "Report a bug or having problems?"
- [ ] Verify email client opens
- [ ] Verify pre-filled template with username

### Test 8.3: App Version
- [ ] Verify version number displayed

---

## FLOW 9: LOGOUT

### Test 9.1: Logout from Menu
- [ ] Tap hamburger menu
- [ ] Tap "Logout"
- [ ] Verify confirmation dialog
- [ ] Tap confirm
- [ ] Verify return to Login screen

### Test 9.2: Credentials Cleared
- [ ] After logout, verify login fields are empty (if Remember Me was off)
- [ ] Verify cannot access home screens without logging in

### Test 9.3: Announcement After Logout
- [ ] Set announcement version to new value in database
- [ ] Logout
- [ ] Login again
- [ ] Verify announcement popup shows (was previously fixed)

---

## EDGE CASES & ERROR HANDLING

### Network Errors
- [ ] Test all flows with slow/no network
- [ ] Verify timeout messages are user-friendly
- [ ] Verify retry options where applicable

### Session Expiry
- [ ] Test behavior when session expires mid-action
- [ ] Verify graceful handling

### Concurrent Access
- [ ] Two users registering for same slot simultaneously
- [ ] Verify only one succeeds

### Data Validation
- [ ] Test all text inputs with special characters
- [ ] Test email validation (supervisor email)
- [ ] Test maximum length inputs

---

## TEST RESULTS LOG

| Date | Tester | Flow | Result | Notes |
|------|--------|------|--------|-------|
| | | | | |
| | | | | |
| | | | | |

---

## BUGS FOUND

| # | Flow | Description | Severity | Status |
|---|------|-------------|----------|--------|
| | | | | |
| | | | | |
| | | | | |
