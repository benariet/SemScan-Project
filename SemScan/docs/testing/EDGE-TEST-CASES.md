# SemScan Edge Test Cases

## Overview
This document contains comprehensive edge test cases for the SemScan application to ensure robust functionality under various conditions and edge cases.

## Table of Contents
1. [Authentication & User Management](#1-authentication--user-management)
2. [Network Connectivity](#2-network-connectivity)
3. [QR Code Scanning](#3-qr-code-scanning)
4. [Session Management](#4-session-management)
5. [Attendance Management](#5-attendance-management)
6. [Manual Attendance Requests](#6-manual-attendance-requests)
7. [Data Export](#7-data-export)
8. [UI/UX Edge Cases](#8-uiux-edge-cases)
9. [Performance Edge Cases](#9-performance-edge-cases)
10. [Security Edge Cases](#10-security-edge-cases)
11. [Integration Edge Cases](#11-integration-edge-cases)
12. [Error Recovery Edge Cases](#12-error-recovery-edge-cases)

---

## 1. Authentication & User Management

### 1.1 API Key Edge Cases

#### Test Case: API_KEY_001
**Description:** Test with null API key  
**Steps:**
1. Clear API key from preferences
2. Launch app as presenter
3. Try to start session

**Expected:** Should show "API key not configured" error

#### Test Case: API_KEY_002
**Description:** Test with empty API key  
**Steps:**
1. Set API key to empty string
2. Launch app as presenter
3. Try to start session

**Expected:** Should show "API key not configured" error

#### Test Case: API_KEY_003
**Description:** Test with invalid API key  
**Steps:**
1. Set API key to "invalid-key-12345"
2. Launch app as presenter
3. Try to start session

**Expected:** Should show "Authentication failed" error

### 1.2 User Role Edge Cases

#### Test Case: ROLE_001
**Description:** Test student trying to access presenter functions  
**Steps:**
1. Login as student
2. Try to access presenter-only features

**Expected:** Should be blocked or redirected

#### Test Case: ROLE_002
**Description:** Test presenter trying to mark attendance  
**Steps:**
1. Login as presenter
2. Try to scan QR code for attendance

**Expected:** Should show "Access denied" error

---

## 2. Network Connectivity

### 2.1 Network Failure Edge Cases

#### Test Case: NETWORK_001
**Description:** Test with no internet connection  
**Steps:**
1. Disable WiFi/mobile data
2. Launch app
3. Try to fetch seminars

**Expected:** Should show "Network error" with retry option

#### Test Case: NETWORK_002
**Description:** Test with slow network connection  
**Steps:**
1. Throttle network to 2G speed
2. Launch app
3. Try to fetch seminars

**Expected:** Should timeout after 5 seconds and show error

#### Test Case: NETWORK_003
**Description:** Test with intermittent connection  
**Steps:**
1. Start API call
2. Disconnect network mid-request
3. Reconnect network

**Expected:** Should handle gracefully and allow retry

### 2.2 Server Edge Cases

#### Test Case: SERVER_001
**Description:** Test with server down  
**Steps:**
1. Stop backend server
2. Launch app
3. Try to fetch seminars

**Expected:** Should show "Server unavailable" error

#### Test Case: SERVER_002
**Description:** Test with server returning 500 error  
**Steps:**
1. Mock server to return 500 error
2. Launch app
3. Try to fetch seminars

**Expected:** Should show "Server error" message

#### Test Case: SERVER_003
**Description:** Test with server returning malformed JSON  
**Steps:**
1. Mock server to return invalid JSON
2. Launch app
3. Try to fetch seminars

**Expected:** Should show "Data parsing error" message

---

## 3. QR Code Scanning

### 3.1 QR Code Edge Cases

#### Test Case: QR_001
**Description:** Test with invalid QR code format  
**Steps:**
1. Generate QR code with invalid format
2. Scan with app

**Expected:** Should show "Invalid QR code format" error

#### Test Case: QR_002
**Description:** Test with expired session QR code  
**Steps:**
1. Generate QR code for closed session
2. Scan with app

**Expected:** Should show "Session not active" error

#### Test Case: QR_003
**Description:** Test with QR code for different session  
**Steps:**
1. Generate QR code for session A
2. Try to scan in session B

**Expected:** Should show "Invalid session" error

#### Test Case: QR_004
**Description:** Test with corrupted QR code  
**Steps:**
1. Partially damage QR code
2. Try to scan

**Expected:** Should show "Cannot read QR code" error

### 3.2 Camera Edge Cases

#### Test Case: CAMERA_001
**Description:** Test with camera permission denied  
**Steps:**
1. Deny camera permission
2. Try to open QR scanner

**Expected:** Should show permission request dialog

#### Test Case: CAMERA_002
**Description:** Test with camera hardware failure  
**Steps:**
1. Mock camera hardware failure
2. Try to open QR scanner

**Expected:** Should show "Camera unavailable" error

#### Test Case: CAMERA_003
**Description:** Test with low light conditions  
**Steps:**
1. Test in dark environment
2. Try to scan QR code

**Expected:** Should show "Low light detected" warning

---

## 4. Session Management

### 4.1 Session Edge Cases

#### Test Case: SESSION_001
**Description:** Test creating session with invalid seminar ID  
**Steps:**
1. Select invalid seminar ID
2. Try to start session

**Expected:** Should show "Invalid seminar" error

#### Test Case: SESSION_002
**Description:** Test creating multiple sessions simultaneously  
**Steps:**
1. Start session A
2. Try to start session B

**Expected:** Should show "Active session exists" dialog

#### Test Case: SESSION_003
**Description:** Test closing session that's already closed  
**Steps:**
1. Close session
2. Try to close again

**Expected:** Should show "Session already closed" message

#### Test Case: SESSION_004
**Description:** Test with session timeout  
**Steps:**
1. Start session
2. Wait for timeout period
3. Try to scan QR code

**Expected:** Should show "Session expired" error

---

## 5. Attendance Management

### 5.1 Attendance Edge Cases

#### Test Case: ATTENDANCE_001
**Description:** Test marking attendance twice  
**Steps:**
1. Mark attendance for session
2. Try to mark again

**Expected:** Should show "Already attended" error

#### Test Case: ATTENDANCE_002
**Description:** Test marking attendance for closed session  
**Steps:**
1. Close session
2. Try to mark attendance

**Expected:** Should show "Session not active" error

#### Test Case: ATTENDANCE_003
**Description:** Test with invalid student ID  
**Steps:**
1. Use invalid student ID
2. Try to mark attendance

**Expected:** Should show "Invalid student" error

#### Test Case: ATTENDANCE_004
**Description:** Test with attendance time in future  
**Steps:**
1. Set system time to future
2. Try to mark attendance

**Expected:** Should show "Invalid timestamp" error

---

## 6. Manual Attendance Requests

### 6.1 Manual Request Edge Cases

#### Test Case: MANUAL_001
**Description:** Test manual request with empty reason  
**Steps:**
1. Submit manual request with empty reason

**Expected:** Should show "Reason required" error

#### Test Case: MANUAL_002
**Description:** Test manual request with very long reason  
**Steps:**
1. Submit manual request with 1000+ character reason

**Expected:** Should show "Reason too long" error

#### Test Case: MANUAL_003
**Description:** Test manual request for non-existent session  
**Steps:**
1. Submit manual request for invalid session

**Expected:** Should show "Session not found" error

#### Test Case: MANUAL_004
**Description:** Test approving already approved request  
**Steps:**
1. Approve manual request
2. Try to approve again

**Expected:** Should show "Already approved" message

---

## 7. Data Export

### 7.1 Export Edge Cases

#### Test Case: EXPORT_001
**Description:** Test export with no attendance data  
**Steps:**
1. Create session with no attendance
2. Try to export data

**Expected:** Should show "No data to export" message

#### Test Case: EXPORT_002
**Description:** Test export with large dataset  
**Steps:**
1. Create session with 1000+ attendance records
2. Try to export data

**Expected:** Should handle large dataset gracefully

#### Test Case: EXPORT_003
**Description:** Test export with corrupted data  
**Steps:**
1. Mock corrupted attendance data
2. Try to export data

**Expected:** Should show "Data corruption detected" error

---

## 8. UI/UX Edge Cases

### 8.1 UI Edge Cases

#### Test Case: UI_001
**Description:** Test with very long names  
**Steps:**
1. Set user name to 100+ characters
2. Check welcome message display

**Expected:** Should truncate or wrap text properly

#### Test Case: UI_002
**Description:** Test with special characters in names  
**Steps:**
1. Set user name with emojis and special chars
2. Check welcome message display

**Expected:** Should display correctly without crashes

#### Test Case: UI_003
**Description:** Test with rapid button clicks  
**Steps:**
1. Rapidly click "Start Session" button
2. Check for duplicate sessions

**Expected:** Should prevent duplicate actions

#### Test Case: UI_004
**Description:** Test with screen rotation  
**Steps:**
1. Rotate device during QR scanning
2. Check camera functionality

**Expected:** Should maintain camera functionality

---

## 9. Performance Edge Cases

### 9.1 Performance Edge Cases

#### Test Case: PERF_001
**Description:** Test with low memory  
**Steps:**
1. Simulate low memory conditions
2. Launch app and use features

**Expected:** Should handle gracefully without crashes

#### Test Case: PERF_002
**Description:** Test with high CPU usage  
**Steps:**
1. Run CPU-intensive background tasks
2. Use app features

**Expected:** Should maintain responsiveness

#### Test Case: PERF_003
**Description:** Test with battery optimization  
**Steps:**
1. Enable battery optimization
2. Use app in background

**Expected:** Should handle background restrictions

---

## 10. Security Edge Cases

### 10.1 Security Edge Cases

#### Test Case: SEC_001
**Description:** Test with SQL injection in user input  
**Steps:**
1. Enter SQL injection strings in forms
2. Submit data

**Expected:** Should sanitize input and prevent injection

#### Test Case: SEC_002
**Description:** Test with XSS in user input  
**Steps:**
1. Enter JavaScript code in forms
2. Submit data

**Expected:** Should sanitize input and prevent XSS

#### Test Case: SEC_003
**Description:** Test with API key exposure  
**Steps:**
1. Check logs for API key exposure
2. Verify key is not logged in plain text

**Expected:** Should mask API keys in logs

---

## 11. Integration Edge Cases

### 11.1 Integration Edge Cases

#### Test Case: INT_001
**Description:** Test with database connection failure  
**Steps:**
1. Simulate database connection failure
2. Try to perform operations

**Expected:** Should show "Database unavailable" error

#### Test Case: INT_002
**Description:** Test with file system full  
**Steps:**
1. Fill device storage
2. Try to export data

**Expected:** Should show "Insufficient storage" error

#### Test Case: INT_003
**Description:** Test with concurrent users  
**Steps:**
1. Simulate multiple users accessing same session
2. Check for race conditions

**Expected:** Should handle concurrent access properly

---

## 12. Error Recovery Edge Cases

### 12.1 Error Recovery Edge Cases

#### Test Case: RECOVERY_001
**Description:** Test app recovery after crash  
**Steps:**
1. Force app crash
2. Restart app

**Expected:** Should recover gracefully and maintain state

#### Test Case: RECOVERY_002
**Description:** Test data recovery after corruption  
**Steps:**
1. Corrupt local data
2. Launch app

**Expected:** Should detect corruption and reset data

#### Test Case: RECOVERY_003
**Description:** Test network recovery  
**Steps:**
1. Lose network connection
2. Regain connection
3. Check app functionality

**Expected:** Should automatically retry failed operations

---

## Test Execution Guidelines

### Prerequisites
- Android device or emulator
- Backend server running
- Test data prepared
- Network connectivity

### Test Environment Setup
1. Install latest app version
2. Configure test API keys
3. Set up test user accounts
4. Prepare test data sets

### Test Execution Order
1. Authentication tests
2. Network connectivity tests
3. Core functionality tests
4. Edge case tests
5. Performance tests
6. Security tests

### Reporting
- Document all test results
- Capture screenshots of failures
- Log error messages
- Track performance metrics

---

## Conclusion

These edge test cases ensure the SemScan application is robust and handles various edge cases gracefully. Regular execution of these tests helps maintain application quality and reliability.

**Last Updated:** October 19, 2025  
**Version:** 1.0  
**Author:** SemScan Development Team
