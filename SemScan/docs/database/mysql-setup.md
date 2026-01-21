# 🗄️ MySQL Database Setup - SemScan

## ✅ **Current Status: DATABASE READY!**

The MySQL database is already set up and running with complete schema and sample data.

---

## 🐳 **Docker Setup (Current Method)**

### **Container Information**
- **Container Name**: `attend-mysql`
- **Status**: ✅ Running
- **Port**: `3306`
- **Image**: `mysql:8.4`

### **Database Details**
- **Database Name**: `attendance`
- **Character Set**: `utf8mb4`
- **Collation**: `utf8mb4_unicode_ci`

### **User Credentials**

#### **Application User (for Spring Boot backend)**
```
Host: localhost (or 10.0.2.2 from Android emulator)
Port: 3306
Database: attendance
Username: attend
Password: strongpass
```

#### **Root User (for admin access)**
```
Host: localhost
Port: 3306
Username: root
Password: root
```

### **Connection URLs**

#### **For Spring Boot Backend**
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/attendance?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4&useUnicode=true
spring.datasource.username=attend
spring.datasource.password=strongpass
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

#### **For Android App (API calls)**
```
Base URL: http://10.0.2.2:8080/
API Key: test-api-key-12345
```

### **Direct MySQL Access Commands**

#### **Connect as application user**
```bash
docker exec -it attend-mysql mysql -u attend -pstrongpass attendance
```

#### **Connect as root user**
```bash
docker exec -it attend-mysql mysql -u root -proot attendance
```

---

## 📊 **Database Schema Status**

### **Tables Created (6 total)**
- ✅ `users` - User management (students, teachers, admins)
- ✅ `courses` - Course information and details
- ✅ `sessions` - Attendance sessions with QR codes
- ✅ `attendance` - Individual attendance records
- ✅ `absence_requests` - Student absence requests and approvals
- ✅ `teacher_api_keys` - API authentication for teachers

### **Sample Data Loaded**
- ✅ **1 Teacher**: John Smith (teacher@university.edu)
- ✅ **5 Students**: Alice, Bob, Charlie, Diana, Eve
- ✅ **3 Courses**: CS101, CS201, CS301
- ✅ **4 Sessions**: Mix of open/closed sessions
- ✅ **API Key**: `test-api-key-12345`

---

## 🚀 **Quick Start Commands**

### **Start Database (if not running)**
```bash
docker run --name attend-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=attendance -e MYSQL_USER=attend -e MYSQL_PASSWORD=strongpass -v attend_mysql_data:/var/lib/mysql mysql:8.4 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

### **Check Database Status**
```bash
docker exec -it attend-mysql mysql -u root -proot -e "USE attendance; SHOW TABLES;"
```

### **View Sample Data**
```bash
docker exec -it attend-mysql mysql -u root -proot -e "USE attendance; SELECT course_name, course_code FROM courses; SELECT COUNT(*) as user_count FROM users;"
```

---

## 📁 **Database Files Location**

All database files are organized in the `mysql-setup/` directory:

- `database-schema.sql` - Complete schema with views and procedures
- `database-setup.sql` - Essential tables (already executed)
- `sample-data.sql` - Test data (already loaded)
- `DATABASE-READY.md` - Complete database documentation

---

## 🔧 **Alternative Setup Methods**

### **Option 1: Docker Compose**
```yaml
# docker-compose.yml (already created)
version: '3.8'
services:
  mysql:
    image: mysql:8.4
    container_name: attend-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: attendance
      MYSQL_USER: attend
      MYSQL_PASSWORD: strongpass
    ports:
      - "3306:3306"
    volumes:
      - attend_mysql_data:/var/lib/mysql
    restart: unless-stopped
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

### **Option 2: Local MySQL Installation**

#### **Download MySQL**
1. Go to: https://dev.mysql.com/downloads/mysql/
2. Download MySQL Community Server
3. Install with these settings:
   - Root Password: `root`
   - Port: `3306`

#### **Create Database**
```sql
CREATE DATABASE attendance;
CREATE USER 'attend'@'localhost' IDENTIFIED BY 'strongpass';
GRANT ALL PRIVILEGES ON attendance.* TO 'attend'@'localhost';
FLUSH PRIVILEGES;
```

#### **Connection Details (Local)**
```
Host: localhost
Port: 3306
Database: attendance
Username: attend
Password: strongpass
```

### **Option 3: XAMPP (Easiest)**
1. Download XAMPP: https://www.apachefriends.org/
2. Install and start MySQL service
3. Open phpMyAdmin: http://localhost/phpmyadmin
4. Create database `attendance`
5. Create user `attend` with password `strongpass`

---

## ✅ **Database is Ready!**

Your MySQL database is fully set up with:
- ✅ **Complete schema** with all required tables
- ✅ **Foreign key relationships** properly configured
- ✅ **Sample data** for testing
- ✅ **API key** for authentication testing
- ✅ **Indexes** for performance optimization

**Status**: Ready for Spring Boot backend development! 🎉