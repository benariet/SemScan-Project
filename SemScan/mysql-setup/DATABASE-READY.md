# 🗄️ SemScan Database Schema - READY!

## ✅ Database Status
- **MySQL Container**: `attend-mysql` (Running)
- **Database**: `attendance`
- **Tables**: 6 tables created successfully
- **Sample Data**: 6 users, 3 courses, 4 sessions loaded

## 📊 Database Tables Created

### 1. **users** - User Management
- `user_id` (VARCHAR(36), PRIMARY KEY)
- `email` (VARCHAR(255), UNIQUE)
- `first_name`, `last_name` (VARCHAR(100))
- `role` (ENUM: 'student', 'teacher', 'admin')
- `student_id` (VARCHAR(50), UNIQUE for students)
- `created_at`, `updated_at` (TIMESTAMP)
- `is_active` (BOOLEAN)

### 2. **courses** - Course Information
- `course_id` (VARCHAR(36), PRIMARY KEY)
- `course_name` (VARCHAR(255))
- `course_code` (VARCHAR(50), UNIQUE)
- `description` (TEXT)
- `lecturer_id` (VARCHAR(36), FOREIGN KEY to users)
- `created_at`, `updated_at` (TIMESTAMP)
- `is_active` (BOOLEAN)

### 3. **sessions** - Attendance Sessions
- `session_id` (VARCHAR(36), PRIMARY KEY)
- `course_id` (VARCHAR(36), FOREIGN KEY to courses)
- `start_time`, `end_time` (TIMESTAMP)
- `status` (ENUM: 'open', 'closed')
- `qr_code_data` (TEXT)
- `created_at`, `updated_at` (TIMESTAMP)

### 4. **attendance** - Attendance Records
- `attendance_id` (VARCHAR(36), PRIMARY KEY)
- `session_id` (VARCHAR(36), FOREIGN KEY to sessions)
- `student_id` (VARCHAR(36), FOREIGN KEY to users)
- `attendance_time` (TIMESTAMP)
- `status` (ENUM: 'present', 'late', 'absent')
- `method` (ENUM: 'qr_scan', 'manual', 'proxy')
- `created_at` (TIMESTAMP)

### 5. **absence_requests** - Absence Management
- `request_id` (VARCHAR(36), PRIMARY KEY)
- `student_id` (VARCHAR(36), FOREIGN KEY to users)
- `course_id` (VARCHAR(36), FOREIGN KEY to courses)
- `session_id` (VARCHAR(36), FOREIGN KEY to sessions, NULLABLE)
- `reason` (VARCHAR(255))
- `note` (TEXT)
- `status` (ENUM: 'pending', 'approved', 'rejected')
- `reviewed_by` (VARCHAR(36), FOREIGN KEY to users)
- `reviewed_at`, `submitted_at`, `updated_at` (TIMESTAMP)

### 6. **teacher_api_keys** - API Authentication
- `key_id` (VARCHAR(36), PRIMARY KEY)
- `teacher_id` (VARCHAR(36), FOREIGN KEY to users)
- `api_key` (VARCHAR(255), UNIQUE)
- `key_name` (VARCHAR(100))
- `is_active` (BOOLEAN)
- `created_at`, `last_used_at` (TIMESTAMP)

## 📝 Sample Data Loaded

### Users (6 total)
- **Teacher**: John Smith (teacher@university.edu)
- **Students**: Alice Johnson, Bob Brown, Charlie Davis, Diana Wilson, Eve Garcia

### Courses (3 total)
- **CS101**: Introduction to Computer Science
- **CS201**: Data Structures and Algorithms  
- **CS301**: Database Systems

### Sessions (4 total)
- 2 sessions for CS101 (1 closed, 1 open)
- 1 session for CS201 (closed)
- 1 session for CS301 (open)

### API Key
- **Test API Key**: `test-api-key-12345` (for teacher John Smith)

## 🔗 Database Connection Info

**For Spring Boot Backend:**
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/attendance?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4&useUnicode=true
spring.datasource.username=attend
spring.datasource.password=strongpass
```

**For Direct MySQL Access:**
- **Host**: localhost (or 10.0.2.2 from Android emulator)
- **Port**: 3306
- **Database**: attendance
- **Username**: attend
- **Password**: strongpass
- **Root Password**: root

## 🚀 Ready for Backend Development!

Your database is now fully set up with:
- ✅ **Complete schema** with all required tables
- ✅ **Foreign key relationships** properly configured
- ✅ **Sample data** for testing
- ✅ **API key** for authentication testing
- ✅ **Indexes** for performance optimization

## 🎯 Next Steps

1. **Create your Spring Boot backend** using the connection info above
2. **Test API endpoints** with the sample data
3. **Connect your Android app** to the backend
4. **Test the complete flow**: Course management → Session creation → QR scanning → Attendance tracking

## 📁 Files Created

- `database-schema.sql` - Complete schema with views and procedures
- `database-setup.sql` - Essential tables only
- `sample-data.sql` - Sample data for testing
- `DATABASE-READY.md` - This documentation

**Status**: Database is ready for your Spring Boot backend! 🎉
