# SemScan MVP API Specification

## Overview
This document specifies the 6 API endpoints required for the SemScan MVP. All other endpoints should NOT be implemented.

**Note:** Mobile apps send Unix milliseconds, server converts to TIMESTAMP for database storage and better logging.

## 🔐 **Authentication**
- **Teacher endpoints:** Require API key in header: `X-API-Key: presenter-xxx-api-key-12345`
- **Student endpoints:** No authentication required (public attendance submission)

## 📋 **Required Endpoints**

### **1. Create Session**
```http
POST /api/v1/sessions
```

**Headers:**
```
X-API-Key: presenter-xxx-api-key-12345
Content-Type: application/json
```

**Request Body:**
```json
{
  "seminarId": "seminar-001",
  "startTimeMs": 1726646400000
}
```

**Server Processing:**
- Convert `startTimeMs` (Unix milliseconds) to TIMESTAMP
- Store as `start_time` in database

**Response (201 Created):**
```json
{
  "sessionId": "session-123",
  "seminarId": "seminar-001", 
  "startTimeMs": 1726646400000,
  "status": "OPEN",
  "qrPayload": {
    "sessionId": "session-123"
  }
}
```

**Error Responses:**
- `401 Unauthorized` - Invalid API key
- `400 Bad Request` - Invalid seminar ID or timestamp

---

### **2. Submit Attendance**
```http
POST /api/v1/attendance
```

**Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "sessionId": "session-123",
  "userId": "student-001",
  "timestampMs": 1726646700000
}
```

**Server Processing:**
- Convert `timestampMs` (Unix milliseconds) to TIMESTAMP
- Store as `attendance_time` in database

**Success Response (200 OK):**
```json
{
  "message": "Checked in for this session",
  "attendanceId": "attendance-456",
  "timestampMs": 1726646700000
}
```

**Already Checked In (200 OK):**
```json
{
  "message": "Already checked in",
  "attendanceId": "attendance-456",
  "timestampMs": 1726646500000
}
```

**Error Responses:**
- `404 Not Found` - "Invalid session code"
- `400 Bad Request` - "Invalid session code" 
- `409 Conflict` - "This session is not accepting new check-ins"

---

### **3. List Attendance for Session**
```http
GET /api/v1/attendance?sessionId=session-123
```

**Headers:**
```
X-API-Key: presenter-xxx-api-key-12345
```

**Response (200 OK):**
```json
{
  "sessionId": "session-123",
  "attendance": [
    {
      "attendanceId": "attendance-456",
      "studentId": "student-001",
      "studentName": "Alice Johnson",
      "studentIdNumber": "STU001",
      "timestampMs": 1726646700000,
      "method": "QR_SCAN"
    },
    {
      "attendanceId": "attendance-457", 
      "studentId": "student-002",
      "studentName": "Bob Brown",
      "studentIdNumber": "STU002",
      "timestampMs": 1726647300000,
      "method": "QR_SCAN"
    }
  ],
  "totalCount": 2
}
```

**Error Responses:**
- `401 Unauthorized` - Invalid API key
- `404 Not Found` - Session not found

---

### **4. List Seminars**
```http
GET /api/v1/seminars
```

**Headers:**
```
X-API-Key: presenter-xxx-api-key-12345
```

**Response (200 OK):**
```json
{
  "seminars": [
    {
      "seminarId": "seminar-001",
      "seminarName": "AI and Machine Learning in Healthcare",
      "seminarCode": "AI-HLTH-001",
      "description": "Exploring applications of AI in medical diagnosis and treatment",
      "presenterId": "presenter-001",
      "presenterName": "Dr. John Smith"
    },
    {
      "seminarId": "seminar-002", 
      "seminarName": "Blockchain Technology and Cryptocurrency",
      "seminarCode": "BLK-CRYPT-001",
      "description": "Understanding blockchain fundamentals and crypto markets",
      "presenterId": "presenter-001",
      "presenterName": "Dr. John Smith"
    }
  ]
}
```

**Error Responses:**
- `401 Unauthorized` - Invalid API key

---

### **5. Export Attendance (Excel)**
```http
GET /api/v1/export/xlsx?sessionId=session-123
```

**Headers:**
```
X-API-Key: presenter-xxx-api-key-12345
```

**Response (200 OK):**
- Content-Type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- File download with attendance data

**Error Responses:**
- `401 Unauthorized` - Invalid API key
- `404 Not Found` - Session not found

---

### **6. Export Attendance (CSV)**
```http
GET /api/v1/export/csv?sessionId=session-123
```

**Headers:**
```
X-API-Key: presenter-xxx-api-key-12345
```

**Response (200 OK):**
- Content-Type: `text/csv`
- File download with attendance data

**Error Responses:**
- `401 Unauthorized` - Invalid API key
- `404 Not Found` - Session not found

---

## 🚫 **Endpoints NOT to Implement**

**DO NOT implement these endpoints for MVP:**
- `POST /api/v1/absence-requests`
- `PATCH /api/v1/absence-requests/{id}`
- `GET /api/v1/absence-requests`
- `POST /api/v1/seminars` (create)
- `PUT /api/v1/seminars/{seminarId}` (update)
- `DELETE /api/v1/seminars/{seminarId}`
- `GET /api/v1/attendance/all`
- `GET /api/v1/attendance/seminar/{seminarId}`

## 📱 **Mobile App Integration Notes**

### **QR Code Payload:**
The QR code should contain JSON:
```json
{
  "sessionId": "session-123"
}
```

### **Timestamp Format:**
- Mobile apps send Unix milliseconds (JavaScript `Date.now()`)
- Server converts to TIMESTAMP for database storage
- Better for logs, debugging, and database queries
- Conversion: `FROM_UNIXTIME(timestampMs/1000)`

### **Error Message Requirements:**
Mobile app expects these exact error messages:
- `"Invalid session code"` (for 404/400 errors)
- `"This session is not accepting new check-ins"` (for 409 errors)

### **Success Message Requirements:**
Mobile app expects these exact success messages:
- `"Checked in for this session"`
- `"Already checked in"`

## 🔧 **Implementation Guidelines**

### **Database Queries:**
```sql
-- Create session (convert Unix milliseconds to TIMESTAMP)
INSERT INTO sessions (session_id, course_id, start_time, status) 
VALUES (?, FROM_UNIXTIME(?/1000), 'OPEN');

-- Submit attendance (convert Unix milliseconds to TIMESTAMP)
INSERT INTO attendance (attendance_id, session_id, student_id, attendance_time, method)
VALUES (?, ?, ?, FROM_UNIXTIME(?/1000), 'QR_SCAN')
ON DUPLICATE KEY UPDATE attendance_time = FROM_UNIXTIME(?/1000);

-- List attendance (convert TIMESTAMP back to Unix milliseconds for API response)
SELECT a.*, u.first_name, u.last_name, u.student_id,
       UNIX_TIMESTAMP(a.attendance_time) * 1000 as timestamp_ms
FROM attendance a
JOIN users u ON a.student_id = u.user_id
WHERE a.session_id = ?;
```

### **API Key Validation:**
```sql
SELECT t.user_id, t.first_name, t.last_name
FROM teacher_api_keys tak
JOIN users t ON tak.teacher_id = t.user_id
WHERE tak.api_key = ? AND tak.is_active = TRUE;
```

---
*This specification should be implemented exactly as described for proper mobile app integration.*
