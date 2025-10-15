# SemScan API - Complete API Reference

## Base URL
```
http://localhost:8080
```

## Authentication
- **API Key Required**: Most endpoints require an `x-api-key` header
- **Valid API Key**: `presenter-001-api-key-12345`
- **Public Endpoints**: Attendance endpoints and health checks (no API key needed)

---

## 📚 SEMINAR ENDPOINTS
*All require API key authentication*

### 1. Create Seminar
```http
POST /api/v1/seminars
Content-Type: application/json
x-api-key: presenter-001-api-key-12345

{
  "seminarName": "Advanced Java Programming",
  "seminarCode": "JAVA-ADV-001",
  "description": "Learn advanced Java concepts and best practices",
  "presenterId": "presenter-001"
}
```

**Response:**
```json
{
  "seminarId": "generated-uuid",
  "seminarName": "Advanced Java Programming",
  "seminarCode": "JAVA-ADV-001",
  "description": "Learn advanced Java concepts and best practices",
  "presenterId": "presenter-001"
}
```

### 2. Get All Seminars
```http
GET /api/v1/seminars
x-api-key: presenter-001-api-key-12345
```

**Response:**
```json
[
  {
    "seminarId": "seminar-001",
    "seminarName": "Advanced Java Programming",
    "seminarCode": "JAVA-ADV-001",
    "description": "Learn advanced Java concepts and best practices",
    "presenterId": "presenter-001"
  }
]
```

### 3. Get Seminar by ID
```http
GET /api/v1/seminars/{seminarId}
x-api-key: presenter-001-api-key-12345
```

### 4. Get Seminar by Code
```http
GET /api/v1/seminars/code/{seminarCode}
x-api-key: presenter-001-api-key-12345
```

### 5. Get Seminars by Presenter
```http
GET /api/v1/seminars/presenter/{presenterId}
x-api-key: presenter-001-api-key-12345
```

### 6. Search Seminars by Name
```http
GET /api/v1/seminars/search?name={searchTerm}
x-api-key: presenter-001-api-key-12345
```

### 7. Update Seminar
```http
PUT /api/v1/seminars/{seminarId}
Content-Type: application/json
x-api-key: presenter-001-api-key-12345

{
  "seminarName": "Updated Seminar Name",
  "seminarCode": "UPDATED-001",
  "description": "Updated description",
  "presenterId": "presenter-001"
}
```

### 8. Delete Seminar
```http
DELETE /api/v1/seminars/{seminarId}
x-api-key: presenter-001-api-key-12345
```

---

## 🎯 SESSION ENDPOINTS
*All require API key authentication*

### 1. Create Session
```http
POST /api/v1/sessions
Content-Type: application/json
x-api-key: presenter-001-api-key-12345

{
  "seminarId": "seminar-001",
  "startTime": "2024-01-15T10:00:00",
  "status": "OPEN"
}
```

**Response:**
```json
{
  "sessionId": "generated-uuid",
  "seminarId": "seminar-001",
  "startTime": "2024-01-15T10:00:00",
  "endTime": null,
  "status": "OPEN"
}
```

### 2. Get Session by ID
```http
GET /api/v1/sessions/{sessionId}
x-api-key: presenter-001-api-key-12345
```

### 3. Get Sessions by Seminar
```http
GET /api/v1/sessions/seminar/{seminarId}
x-api-key: presenter-001-api-key-12345
```

### 4. Get Open Sessions
```http
GET /api/v1/sessions/open
x-api-key: presenter-001-api-key-12345
```

### 5. Get Closed Sessions
```http
GET /api/v1/sessions/closed
x-api-key: presenter-001-api-key-12345
```

### 6. Update Session Status
```http
PUT /api/v1/sessions/{sessionId}/status?status=CLOSED
x-api-key: presenter-001-api-key-12345
```

### 7. Close Session (PATCH - Mobile App)
```http
PATCH /api/v1/sessions/{sessionId}/close
x-api-key: presenter-001-api-key-12345
```

### 8. Close Session (PUT - Alternative)
```http
PUT /api/v1/sessions/{sessionId}/close
x-api-key: presenter-001-api-key-12345
```

### 9. Open Session
```http
PUT /api/v1/sessions/{sessionId}/open
x-api-key: presenter-001-api-key-12345
```

### 10. Get Sessions by Date Range
```http
GET /api/v1/sessions/date-range?startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59
x-api-key: presenter-001-api-key-12345
```

### 11. Get Active Sessions by Seminar
```http
GET /api/v1/sessions/seminar/{seminarId}/active
x-api-key: presenter-001-api-key-12345
```

### 12. Delete Session
```http
DELETE /api/v1/sessions/{sessionId}
x-api-key: presenter-001-api-key-12345
```

---

## ✅ ATTENDANCE ENDPOINTS
*Most are PUBLIC endpoints (no API key required), except for the GET with sessionId parameter*

### 1. Record Attendance
```http
POST /api/v1/attendance
Content-Type: application/json

{
  "sessionId": "session-001",
  "studentId": "student-001",
  "attendanceTime": "2024-01-15T10:30:00",
  "method": "QR_SCAN"
}
```

**Response:**
```json
{
  "attendanceId": "generated-uuid",
  "sessionId": "session-001",
  "studentId": "student-001",
  "attendanceTime": "2024-01-15T10:30:00",
  "method": "QR_SCAN"
}
```

### 2. Get Attendance by Session (with API Key)
```http
GET /api/v1/attendance?sessionId={sessionId}
x-api-key: presenter-001-api-key-12345
```

**Response:**
```json
[
  {
    "attendanceId": "attendance-001",
    "sessionId": "session-001",
    "studentId": "student-001",
    "attendanceTime": "2024-01-15T10:30:00",
    "method": "QR_SCAN"
  }
]
```

### 3. Get Attendance by ID
```http
GET /api/v1/attendance/{attendanceId}
```

### 4. Get Attendance by Session
```http
GET /api/v1/attendance/session/{sessionId}
```

### 5. Get Attendance by Student
```http
GET /api/v1/attendance/student/{studentId}
```

### 6. Check if Student Attended
```http
GET /api/v1/attendance/check?sessionId={sessionId}&studentId={studentId}
```

**Response:**
```json
true
```

### 7. Get Attendance by Method
```http
GET /api/v1/attendance/method/{method}
```
*Methods: QR_SCAN, MANUAL, PROXY*

### 8. Get Attendance by Date Range
```http
GET /api/v1/attendance/date-range?startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59
```

### 9. Get Session Attendance Statistics
```http
GET /api/v1/attendance/session/{sessionId}/stats
```

**Response:**
```json
{
  "totalAttendance": 25,
  "qrScanCount": 20,
  "manualCount": 3,
  "proxyCount": 2
}
```

### 10. Delete Attendance Record
```http
DELETE /api/v1/attendance/{attendanceId}
```

### 11. Export Attendance as CSV
```http
GET /api/v1/attendance/export/csv?sessionId={sessionId}
x-api-key: presenter-001-api-key-12345
```

**Response:** Downloads CSV file with attendance data

### 12. Export Attendance as Excel (XLSX)
```http
GET /api/v1/attendance/export/xlsx?sessionId={sessionId}
x-api-key: presenter-001-api-key-12345
```

**Response:** Downloads Excel file with attendance data

---

## 📊 EXPORT ENDPOINTS
*These endpoints are specifically for the mobile app export functionality*

### 1. Export CSV (Mobile App)
```http
GET /api/v1/export/csv?sessionId={sessionId}
x-api-key: presenter-001-api-key-12345
```

**Response:** Downloads CSV file with attendance data

### 2. Export Excel (Mobile App)
```http
GET /api/v1/export/xlsx?sessionId={sessionId}
x-api-key: presenter-001-api-key-12345
```

**Response:** Downloads Excel file with attendance data

---

## 🧪 TEST ENDPOINTS
*For testing session logging functionality*

### 1. Test Session Creation Logging
```http
POST /api/v1/test/session-logging/create-session?sessionId=test-session-001
```

**Response:**
```json
{
  "status": "success",
  "message": "Session logging test completed",
  "sessionId": "test-session-001",
  "logFile": "logs/sessions/session-test-session-001-2024-01-15.log",
  "timestamp": 1705312800000
}
```

### 2. Test Status Change Logging
```http
POST /api/v1/test/session-logging/change-status?sessionId=test-session-001&newStatus=CLOSED
```

### 3. Test Attendance Logging
```http
POST /api/v1/test/session-logging/log-attendance?sessionId=test-session-001&studentId=student-001
```

### 4. Test Session Closure Logging
```http
POST /api/v1/test/session-logging/close-session?sessionId=test-session-001&durationMinutes=90&attendanceCount=15
```

### 5. Test Statistics Logging
```http
POST /api/v1/test/session-logging/log-statistics?sessionId=test-session-001&totalStudents=25&attendedStudents=20
```

---

## 🔍 HEALTH & MONITORING ENDPOINTS

### 1. Health Check (Public)
```http
GET /actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    }
  }
}
```

### 2. Application Info (Public)
```http
GET /actuator/info
```

### 3. Other Actuator Endpoints (Require API Key)
```http
GET /actuator/loggers
x-api-key: presenter-001-api-key-12345
```

---

## 📋 DATA MODELS

### Seminar
```json
{
  "seminarId": "string (36 chars, auto-generated)",
  "seminarName": "string (required)",
  "seminarCode": "string (required, unique)",
  "description": "string (optional)",
  "presenterId": "string (36 chars, required)"
}
```

### Session
```json
{
  "sessionId": "string (36 chars, auto-generated)",
  "seminarId": "string (36 chars, required)",
  "startTime": "datetime (required)",
  "endTime": "datetime (optional)",
  "status": "OPEN | CLOSED (default: OPEN)"
}
```

### Attendance
```json
{
  "attendanceId": "string (36 chars, auto-generated)",
  "sessionId": "string (36 chars, required)",
  "studentId": "string (36 chars, required)",
  "attendanceTime": "datetime (required)",
  "method": "QR_SCAN | MANUAL | PROXY (required)"
}
```

### User (No REST endpoints available)
```json
{
  "userId": "string (36 chars, auto-generated)",
  "firstName": "string (required)",
  "lastName": "string (required)",
  "email": "string (required, unique)",
  "role": "STUDENT | PRESENTER (required)",
  "studentId": "string (optional, unique, for students only)"
}
```

---

## 🔐 AUTHENTICATION NOTES

1. **API Key Header**: Always include `x-api-key: presenter-001-api-key-12345` for protected endpoints
2. **Public Endpoints**: Most attendance endpoints and health checks don't require authentication
3. **Protected Attendance Endpoints**: 
   - `GET /api/v1/attendance?sessionId={sessionId}` requires API key authentication
   - `GET /api/v1/attendance/export/csv?sessionId={sessionId}` requires API key authentication
   - `GET /api/v1/attendance/export/xlsx?sessionId={sessionId}` requires API key authentication
4. **Protected Export Endpoints (Mobile App)**:
   - `GET /api/v1/export/csv?sessionId={sessionId}` requires API key authentication
   - `GET /api/v1/export/xlsx?sessionId={sessionId}` requires API key authentication
5. **CORS**: All origins are allowed for development
6. **Error Responses**: 
   - `401 Unauthorized`: Missing or invalid API key
   - `403 Forbidden`: Valid API key but insufficient permissions
   - `400 Bad Request`: Invalid request data
   - `404 Not Found`: Resource not found
   - `500 Internal Server Error`: Server error

---

## 📱 MOBILE APP INTEGRATION TIPS

1. **QR Code Scanning**: Use the attendance recording endpoint with `method: "QR_SCAN"`
2. **Session Management**: Check for open sessions before allowing attendance
3. **Offline Support**: Cache seminar and session data for offline viewing
4. **Error Handling**: Always check response status codes and handle errors gracefully
5. **Date Format**: Use ISO 8601 format for all datetime fields (`YYYY-MM-DDTHH:mm:ss`)

---

## 🚀 QUICK START EXAMPLES

### Create a Seminar and Session
```bash
# 1. Create a seminar
curl -X POST "http://localhost:8080/api/v1/seminars" \
  -H "x-api-key: presenter-001-api-key-12345" \
  -H "Content-Type: application/json" \
  -d '{
    "seminarName": "Mobile Development",
    "seminarCode": "MOBILE-001",
    "description": "Learn mobile app development",
    "presenterId": "presenter-001"
  }'

# 2. Create a session for the seminar
curl -X POST "http://localhost:8080/api/v1/sessions" \
  -H "x-api-key: presenter-001-api-key-12345" \
  -H "Content-Type: application/json" \
  -d '{
    "seminarId": "seminar-id-from-step-1",
    "startTime": "2024-01-15T10:00:00",
    "status": "OPEN"
  }'
```

### Record Attendance (QR Scan)
```bash
curl -X POST "http://localhost:8080/api/v1/attendance" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "session-id-from-above",
    "studentId": "student-001",
    "attendanceTime": "2024-01-15T10:30:00",
    "method": "QR_SCAN"
  }'
```

### Check Session Status
```bash
curl -X GET "http://localhost:8080/api/v1/sessions/open" \
  -H "x-api-key: presenter-001-api-key-12345"
```

---

## 📊 RESPONSE STATUS CODES

| Code | Description | Usage |
|------|-------------|-------|
| 200 | OK | Successful GET, PUT requests |
| 201 | Created | Successful POST requests |
| 204 | No Content | Successful DELETE requests |
| 400 | Bad Request | Invalid request data |
| 401 | Unauthorized | Missing or invalid API key |
| 403 | Forbidden | Valid API key but insufficient permissions |
| 404 | Not Found | Resource not found |
| 500 | Internal Server Error | Server error |

---

## 🔧 DEVELOPMENT NOTES

- **Base URL**: `http://localhost:8080` (development)
- **API Version**: v1
- **Content-Type**: `application/json` for POST/PUT requests
- **Date Format**: ISO 8601 (`YYYY-MM-DDTHH:mm:ss`)
- **UUID Format**: 36-character UUIDs for all IDs
- **Logging**: All requests are logged with correlation IDs
- **Session Logging**: Special session-specific log files are created

This API provides everything needed for a comprehensive seminar attendance system with QR code scanning capabilities!
