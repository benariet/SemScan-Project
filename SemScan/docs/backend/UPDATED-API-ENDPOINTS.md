# 🌐 **Updated REST API Endpoints - Simplified Schema**

Based on the new simplified database schema, here are the updated REST API endpoints:

---

## 📚 **Course Management API** (`/api/v1/courses`)

### **Get All Courses**
```http
GET /api/v1/courses
Headers: X-API-Key: test-api-key-12345
Response: List<Course>
```

### **Get Course by ID**
```http
GET /api/v1/courses/{courseId}
Headers: X-API-Key: test-api-key-12345
Response: Course
```

### **Create New Course**
```http
POST /api/v1/courses
Headers: X-API-Key: test-api-key-12345
Content-Type: application/json
Body: {
  "courseName": "Introduction to Computer Science",
  "courseCode": "CS101",
  "description": "Basic programming concepts",
  "lecturerId": "teacher-001"
}
Response: Course
```

### **Update Course**
```http
PUT /api/v1/courses/{courseId}
Headers: X-API-Key: test-api-key-12345
Content-Type: application/json
Body: {
  "courseName": "Updated Course Name",
  "courseCode": "CS101",
  "description": "Updated description",
  "lecturerId": "teacher-001"
}
Response: Course
```

### **Delete Course**
```http
DELETE /api/v1/courses/{courseId}
Headers: X-API-Key: test-api-key-12345
Response: 204 No Content
```

### **Get Courses by Teacher**
```http
GET /api/v1/courses/teacher/{teacherId}
Headers: X-API-Key: test-api-key-12345
Response: List<Course>
```

### **Search Courses**
```http
GET /api/v1/courses/search?name={courseName}
Headers: X-API-Key: test-api-key-12345
Response: List<Course>
```

---

## 🎯 **Session Management API** (`/api/v1/sessions`)

### **Create New Session**
```http
POST /api/v1/sessions
Headers: X-API-Key: test-api-key-12345
Content-Type: application/json
Body: {
  "courseId": "course-001",
  "startTime": "2024-09-20T10:00:00",
  "qrCodeData": "QR_CS101_002"
}
Response: Session
```

### **Get Session by ID**
```http
GET /api/v1/sessions/{sessionId}
Headers: X-API-Key: test-api-key-12345
Response: Session
```

### **Close Session**
```http
PUT /api/v1/sessions/{sessionId}/close
Headers: X-API-Key: test-api-key-12345
Response: Session
```

### **Get Sessions by Course**
```http
GET /api/v1/sessions/course/{courseId}
Headers: X-API-Key: test-api-key-12345
Response: List<Session>
```

### **Get Open Sessions**
```http
GET /api/v1/sessions/open
Headers: X-API-Key: test-api-key-12345
Response: List<Session>
```

### **Get Closed Sessions**
```http
GET /api/v1/sessions/closed
Headers: X-API-Key: test-api-key-12345
Response: List<Session>
```

### **Get Sessions by Date Range**
```http
GET /api/v1/sessions/date-range?from=2024-09-01&to=2024-09-30
Headers: X-API-Key: test-api-key-12345
Response: List<Session>
```

---

## ✅ **Attendance Tracking API** (`/api/v1/attendance`)

### **Record Attendance**
```http
POST /api/v1/attendance
Headers: X-API-Key: test-api-key-12345
Content-Type: application/json
Body: {
  "sessionId": "session-001",
  "studentId": "student-001",
  "attendanceTime": "2024-09-18T10:05:00",
  "status": "PRESENT",
  "method": "QR_SCAN"
}
Response: Attendance
```

### **Get Attendance by Session**
```http
GET /api/v1/attendance/session/{sessionId}
Headers: X-API-Key: test-api-key-12345
Response: List<Attendance>
```

### **Get Attendance by Student**
```http
GET /api/v1/attendance/student/{studentId}
Headers: X-API-Key: test-api-key-12345
Response: List<Attendance>
```

### **Get All Attendance Records**
```http
GET /api/v1/attendance/all
Headers: X-API-Key: test-api-key-12345
Response: List<Attendance>
```

### **Get Attendance by Course**
```http
GET /api/v1/attendance/course/{courseId}
Headers: X-API-Key: test-api-key-12345
Response: List<Attendance>
```

### **Get Session Statistics**
```http
GET /api/v1/attendance/session/{sessionId}/stats
Headers: X-API-Key: test-api-key-12345
Response: {
  "totalStudents": 8,
  "presentCount": 4,
  "lateCount": 1,
  "absentCount": 3,
  "attendanceRate": 62.5
}
```

### **Get Course Statistics**
```http
GET /api/v1/attendance/course/{courseId}/stats
Headers: X-API-Key: test-api-key-12345
Response: {
  "totalSessions": 3,
  "totalAttendance": 12,
  "averageAttendanceRate": 75.0,
  "studentStats": [...]
}
```

### **Get Student Statistics**
```http
GET /api/v1/attendance/student/{studentId}/stats
Headers: X-API-Key: test-api-key-12345
Response: {
  "totalSessions": 5,
  "presentCount": 4,
  "lateCount": 1,
  "absentCount": 0,
  "attendanceRate": 100.0
}
```

### **Update Attendance**
```http
PUT /api/v1/attendance/{attendanceId}
Headers: X-API-Key: test-api-key-12345
Content-Type: application/json
Body: {
  "status": "LATE",
  "method": "MANUAL"
}
Response: Attendance
```

---

## 📝 **Absence Request API** (`/api/v1/absence-requests`)

### **Create Absence Request**
```http
POST /api/v1/absence-requests
Headers: X-API-Key: test-api-key-12345
Content-Type: application/json
Body: {
  "studentId": "student-001",
  "courseId": "course-001",
  "sessionId": "session-001",
  "reason": "Medical appointment",
  "note": "Had a doctor appointment"
}
Response: AbsenceRequest
```

### **Get All Absence Requests**
```http
GET /api/v1/absence-requests/all
Headers: X-API-Key: test-api-key-12345
Response: List<AbsenceRequest>
```

### **Get Requests by Student**
```http
GET /api/v1/absence-requests/student/{studentId}
Headers: X-API-Key: test-api-key-12345
Response: List<AbsenceRequest>
```

### **Get Requests by Course**
```http
GET /api/v1/absence-requests/course/{courseId}
Headers: X-API-Key: test-api-key-12345
Response: List<AbsenceRequest>
```

### **Get Requests by Session**
```http
GET /api/v1/absence-requests/session/{sessionId}
Headers: X-API-Key: test-api-key-12345
Response: List<AbsenceRequest>
```

### **Get Pending Requests**
```http
GET /api/v1/absence-requests/pending
Headers: X-API-Key: test-api-key-12345
Response: List<AbsenceRequest>
```

### **Get Approved Requests**
```http
GET /api/v1/absence-requests/approved
Headers: X-API-Key: test-api-key-12345
Response: List<AbsenceRequest>
```

### **Get Rejected Requests**
```http
GET /api/v1/absence-requests/rejected
Headers: X-API-Key: test-api-key-12345
Response: List<AbsenceRequest>
```

### **Get Request by ID**
```http
GET /api/v1/absence-requests/{requestId}
Headers: X-API-Key: test-api-key-12345
Response: AbsenceRequest
```

### **Approve Request**
```http
PUT /api/v1/absence-requests/{requestId}/approve
Headers: X-API-Key: test-api-key-12345
Response: AbsenceRequest
```

### **Reject Request**
```http
PUT /api/v1/absence-requests/{requestId}/reject
Headers: X-API-Key: test-api-key-12345
Response: AbsenceRequest
```

### **Update Request**
```http
PUT /api/v1/absence-requests/{requestId}
Headers: X-API-Key: test-api-key-12345
Content-Type: application/json
Body: {
  "reason": "Updated reason",
  "note": "Updated note"
}
Response: AbsenceRequest
```

### **Get Course Statistics**
```http
GET /api/v1/absence-requests/course/{courseId}/stats
Headers: X-API-Key: test-api-key-12345
Response: {
  "totalRequests": 5,
  "pendingCount": 2,
  "approvedCount": 2,
  "rejectedCount": 1,
  "approvalRate": 66.7
}
```

### **Get Student Statistics**
```http
GET /api/v1/absence-requests/student/{studentId}/stats
Headers: X-API-Key: test-api-key-12345
Response: {
  "totalRequests": 3,
  "pendingCount": 1,
  "approvedCount": 2,
  "rejectedCount": 0,
  "approvalRate": 100.0
}
```

---

## 👥 **User Management API** (`/api/v1/users`)

### **Get All Users**
```http
GET /api/v1/users
Headers: X-API-Key: test-api-key-12345
Response: List<User>
```

### **Get User by ID**
```http
GET /api/v1/users/{userId}
Headers: X-API-Key: test-api-key-12345
Response: User
```

### **Get Users by Role**
```http
GET /api/v1/users/role/{role}
Headers: X-API-Key: test-api-key-12345
Response: List<User>
```

### **Get All Students**
```http
GET /api/v1/users/students
Headers: X-API-Key: test-api-key-12345
Response: List<User>
```

### **Get All Teachers**
```http
GET /api/v1/users/teachers
Headers: X-API-Key: test-api-key-12345
Response: List<User>
```

### **Create New User**
```http
POST /api/v1/users
Headers: X-API-Key: test-api-key-12345
Content-Type: application/json
Body: {
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@university.edu",
  "role": "STUDENT",
  "studentId": "STU009"
}
Response: User
```

### **Update User**
```http
PUT /api/v1/users/{userId}
Headers: X-API-Key: test-api-key-12345
Content-Type: application/json
Body: {
  "firstName": "Updated Name",
  "lastName": "Updated Last",
  "email": "updated@university.edu"
}
Response: User
```

---

## 🔍 **Search & Analytics API** (`/api/v1/analytics`)

### **Get Overall Statistics**
```http
GET /api/v1/analytics/overview
Headers: X-API-Key: test-api-key-12345
Response: {
  "totalUsers": 12,
  "totalCourses": 6,
  "totalSessions": 10,
  "totalAttendance": 10,
  "totalAbsenceRequests": 5
}
```

### **Get Attendance Trends**
```http
GET /api/v1/analytics/attendance-trends?from=2024-09-01&to=2024-09-30
Headers: X-API-Key: test-api-key-12345
Response: {
  "dailyAttendance": [...],
  "weeklyTrends": [...],
  "monthlySummary": {...}
}
```

### **Get Course Performance**
```http
GET /api/v1/analytics/course-performance
Headers: X-API-Key: test-api-key-12345
Response: List<{
  "courseId": "course-001",
  "courseName": "CS101",
  "averageAttendance": 75.0,
  "totalSessions": 3,
  "totalStudents": 8
}>
```

---

## 🔐 **Authentication & Security**

### **API Key Validation**
All endpoints require the `X-API-Key` header:
```http
X-API-Key: test-api-key-12345
```

### **CORS Configuration**
- **Allowed Origins**: `http://localhost:8080`, `http://10.0.2.2:8080`, `http://localhost:3000`
- **Allowed Methods**: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`
- **Allowed Headers**: `*`
- **Allow Credentials**: `true`

---

## 📊 **Response Formats**

### **Success Response**
```json
{
  "success": true,
  "data": { ... },
  "message": "Operation completed successfully",
  "timestamp": "2024-09-18T10:00:00Z"
}
```

### **Error Response**
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input data",
    "details": { ... }
  },
  "timestamp": "2024-09-18T10:00:00Z"
}
```

### **Pagination Response**
```json
{
  "success": true,
  "data": [ ... ],
  "pagination": {
    "page": 1,
    "size": 10,
    "totalElements": 50,
    "totalPages": 5
  },
  "timestamp": "2024-09-18T10:00:00Z"
}
```

---

## 🚀 **Key Changes from Previous API:**

### **Simplified Endpoints:**
- ❌ Removed TeacherApiKey management endpoints
- ❌ Removed Enrollment management endpoints
- ❌ Removed SystemSettings endpoints
- ✅ Added User management endpoints
- ✅ Enhanced Analytics endpoints

### **Improved Response Format:**
- ✅ Consistent JSON response structure
- ✅ Better error handling
- ✅ Pagination support
- ✅ Timestamp tracking

### **Enhanced Functionality:**
- ✅ Better statistics and analytics
- ✅ Improved search capabilities
- ✅ More flexible filtering options
- ✅ Comprehensive user management

This simplified API maintains all core functionality while being much easier to use and maintain! 🎯
