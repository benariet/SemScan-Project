# 🏗️ **Updated JPA Entity Models - Simplified Schema**

Based on the new simplified database schema, here are the updated JPA entity models:

---

## 📋 **1. User Entity**

```java
package com.semscan.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "users")
public class User {
    
    @Id
    @Column(name = "user_id", length = 36)
    private String userId;
    
    @NotBlank
    @Size(max = 100)
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;
    
    @NotBlank
    @Size(max = 100)
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;
    
    @Email
    @NotBlank
    @Size(max = 255)
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role = UserRole.STUDENT;
    
    @Size(max = 50)
    @Column(name = "student_id", unique = true, length = 50)
    private String studentId;
    
    // Constructors
    public User() {}
    
    public User(String userId, String firstName, String lastName, String email, UserRole role) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;
    }
    
    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    
    // Utility methods
    public String getFullName() {
        return firstName + " " + lastName;
    }
    
    public boolean isStudent() {
        return role == UserRole.STUDENT;
    }
    
    public boolean isTeacher() {
        return role == UserRole.TEACHER;
    }
    
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}

// User Role Enum
enum UserRole {
    STUDENT, TEACHER, ADMIN
}
```

---

## 📚 **2. Course Entity**

```java
package com.semscan.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "courses")
public class Course {
    
    @Id
    @Column(name = "course_id", length = 36)
    private String courseId;
    
    @NotBlank
    @Size(max = 255)
    @Column(name = "course_name", nullable = false, length = 255)
    private String courseName;
    
    @NotBlank
    @Size(max = 50)
    @Column(name = "course_code", nullable = false, unique = true, length = 50)
    private String courseCode;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id", nullable = false)
    private User lecturer;
    
    // Constructors
    public Course() {}
    
    public Course(String courseId, String courseName, String courseCode, String description, User lecturer) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.courseCode = courseCode;
        this.description = description;
        this.lecturer = lecturer;
    }
    
    // Getters and Setters
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public User getLecturer() { return lecturer; }
    public void setLecturer(User lecturer) { this.lecturer = lecturer; }
}
```

---

## 🎯 **3. Session Entity**

```java
package com.semscan.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
public class Session {
    
    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
    
    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status = SessionStatus.OPEN;
    
    @Column(name = "qr_code_data", columnDefinition = "TEXT")
    private String qrCodeData;
    
    // Constructors
    public Session() {}
    
    public Session(String sessionId, Course course, LocalDateTime startTime, String qrCodeData) {
        this.sessionId = sessionId;
        this.course = course;
        this.startTime = startTime;
        this.qrCodeData = qrCodeData;
        this.status = SessionStatus.OPEN;
    }
    
    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    
    public String getQrCodeData() { return qrCodeData; }
    public void setQrCodeData(String qrCodeData) { this.qrCodeData = qrCodeData; }
    
    // Utility methods
    public boolean isOpen() {
        return status == SessionStatus.OPEN;
    }
    
    public boolean isClosed() {
        return status == SessionStatus.CLOSED;
    }
    
    public void close() {
        this.status = SessionStatus.CLOSED;
        this.endTime = LocalDateTime.now();
    }
}

// Session Status Enum
enum SessionStatus {
    OPEN, CLOSED
}
```

---

## ✅ **4. Attendance Entity**

```java
package com.semscan.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "student_id"}))
public class Attendance {
    
    @Id
    @Column(name = "attendance_id", length = 36)
    private String attendanceId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    @NotNull
    @Column(name = "attendance_time", nullable = false)
    private LocalDateTime attendanceTime;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceStatus status = AttendanceStatus.PRESENT;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private AttendanceMethod method = AttendanceMethod.QR_SCAN;
    
    // Constructors
    public Attendance() {}
    
    public Attendance(String attendanceId, Session session, User student, 
                     LocalDateTime attendanceTime, AttendanceStatus status, AttendanceMethod method) {
        this.attendanceId = attendanceId;
        this.session = session;
        this.student = student;
        this.attendanceTime = attendanceTime;
        this.status = status;
        this.method = method;
    }
    
    // Getters and Setters
    public String getAttendanceId() { return attendanceId; }
    public void setAttendanceId(String attendanceId) { this.attendanceId = attendanceId; }
    
    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }
    
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    
    public LocalDateTime getAttendanceTime() { return attendanceTime; }
    public void setAttendanceTime(LocalDateTime attendanceTime) { this.attendanceTime = attendanceTime; }
    
    public AttendanceStatus getStatus() { return status; }
    public void setStatus(AttendanceStatus status) { this.status = status; }
    
    public AttendanceMethod getMethod() { return method; }
    public void setMethod(AttendanceMethod method) { this.method = method; }
}

// Attendance Status Enum
enum AttendanceStatus {
    PRESENT, LATE, ABSENT
}

// Attendance Method Enum
enum AttendanceMethod {
    QR_SCAN, MANUAL, PROXY
}
```

---

## 📝 **5. AbsenceRequest Entity**

```java
package com.semscan.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(name = "absence_requests")
public class AbsenceRequest {
    
    @Id
    @Column(name = "request_id", length = 36)
    private String requestId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private Session session;
    
    @NotBlank
    @Size(max = 255)
    @Column(name = "reason", nullable = false, length = 255)
    private String reason;
    
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status = RequestStatus.PENDING;
    
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    
    // Constructors
    public AbsenceRequest() {
        this.submittedAt = LocalDateTime.now();
    }
    
    public AbsenceRequest(String requestId, User student, Course course, 
                         String reason, String note) {
        this.requestId = requestId;
        this.student = student;
        this.course = course;
        this.reason = reason;
        this.note = note;
        this.status = RequestStatus.PENDING;
        this.submittedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    
    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    
    // Utility methods
    public boolean isPending() {
        return status == RequestStatus.PENDING;
    }
    
    public boolean isApproved() {
        return status == RequestStatus.APPROVED;
    }
    
    public boolean isRejected() {
        return status == RequestStatus.REJECTED;
    }
    
    public void approve() {
        this.status = RequestStatus.APPROVED;
    }
    
    public void reject() {
        this.status = RequestStatus.REJECTED;
    }
}

// Request Status Enum
enum RequestStatus {
    PENDING, APPROVED, REJECTED
}
```

---

## 🔧 **Key Changes from Previous Schema:**

### **Removed Entities:**
- ❌ **TeacherApiKey** - Simplified authentication approach
- ❌ **Enrollment** - Direct course-student relationship through attendance
- ❌ **SystemSettings** - Configuration moved to application.properties
- ❌ **AuditLog** - Simplified for MVP

### **Simplified Relationships:**
- ✅ **User** - Single table for students, teachers, and admins
- ✅ **Course** - Direct lecturer assignment
- ✅ **Session** - Direct course relationship
- ✅ **Attendance** - Direct session and student relationships
- ✅ **AbsenceRequest** - Optional session reference

### **Database Changes:**
- **Database name**: `attendance` → `semscan_db`
- **Table count**: 9 tables → 5 tables
- **Complexity**: Reduced by ~60%
- **Maintainability**: Significantly improved

---

## 📊 **Entity Relationship Summary:**

```
User (1) ←→ (N) Course [lecturer]
Course (1) ←→ (N) Session
Session (1) ←→ (N) Attendance
User (1) ←→ (N) Attendance [student]
User (1) ←→ (N) AbsenceRequest [student]
Course (1) ←→ (N) AbsenceRequest
Session (1) ←→ (0..1) AbsenceRequest [optional]
```

This simplified schema maintains all core functionality while being much easier to understand, implement, and maintain! 🚀
