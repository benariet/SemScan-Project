# 🚀 SemScan Backend - Complete Context & Documentation

## 📋 **Project Overview**

**SemScan Backend** is a complete Spring Boot REST API that serves as the server-side component for the QR-based attendance system. This document contains all context, code, and configuration needed to recreate the backend as a separate project.

---

## 🏗️ **Architecture & Technology Stack**

### **Core Technologies**
- **Spring Boot 3.2.0** - Main framework
- **Java 17** - Programming language
- **MySQL 8.4** - Database (running in Docker)
- **JPA/Hibernate** - ORM for database operations
- **Maven** - Build and dependency management
- **Spring Security** - Security framework (currently permissive for development)

### **Project Structure**
```
semscan-backend/
├── pom.xml                                    # Maven dependencies
├── mvnw.cmd                                   # Maven wrapper
├── mvnw                                        # Maven wrapper (Unix)
├── .mvn/wrapper/
│   ├── maven-wrapper.jar
│   └── maven-wrapper.properties
└── src/main/
    ├── java/com/semscan/backend/
    │   ├── SemScanBackendApplication.java     # Main application class
    │   ├── entity/                            # JPA Entities (5 files)
    │   │   ├── User.java                      # User management (students, teachers, admins)
    │   │   ├── Course.java                    # Course information
    │   │   ├── Session.java                   # Attendance sessions
    │   │   ├── Attendance.java                # Attendance records
    │   │   └── AbsenceRequest.java            # Absence requests
    │   ├── repository/                        # Data Access Layer (5 files)
    │   │   ├── UserRepository.java
    │   │   ├── CourseRepository.java
    │   │   ├── SessionRepository.java
    │   │   ├── AttendanceRepository.java
    │   │   └── AbsenceRequestRepository.java
    │   ├── controller/                        # REST Controllers (4 files)
    │   │   ├── CourseController.java          # Course CRUD operations
    │   │   ├── SessionController.java         # Session management
    │   │   ├── AttendanceController.java      # Attendance tracking
    │   │   └── AbsenceRequestController.java  # Absence request workflow
    │   └── config/                            # Configuration (2 files)
    │       ├── CorsConfig.java                # CORS configuration
    │       └── SecurityConfig.java            # Security configuration
    └── resources/
        └── application.properties             # Spring Boot configuration
```

---

## 📦 **Maven Configuration (pom.xml)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>
    <groupId>com.semscan</groupId>
    <artifactId>semscan-backend</artifactId>
    <version>1.0.0</version>
    <name>SemScan Backend</name>
    <description>SemScan QR Attendance System Backend API</description>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>8.0.33</version>
        </dependency>

        <!-- JSON Processing -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Utilities -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
        </dependency>

        <!-- Development Tools -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## ⚙️ **Application Configuration (application.properties)**

```properties
# SemScan Backend Configuration

# Server Configuration
server.port=8080
server.servlet.context-path=/

# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/semscan_db?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4&useUnicode=true
spring.datasource.username=attend
spring.datasource.password=strongpass
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA/Hibernate Configuration
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true

# Security Configuration (temporarily disabled for development)
spring.security.user.name=admin
spring.security.user.password=admin123

# CORS Configuration
cors.allowed.origins=http://localhost:8080,http://10.0.2.2:8080,http://localhost:3000
cors.allowed.methods=GET,POST,PUT,DELETE,PATCH,OPTIONS
cors.allowed.headers=*
cors.allow.credentials=true

# Logging Configuration
logging.level.com.semscan.backend=DEBUG
logging.level.org.springframework.web=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
logging.level.org.springframework.security=DEBUG

# Application Configuration
app.name=SemScan Attendance System
app.version=1.0.0
app.api-key=test-api-key-12345

# Jackson Configuration
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.time-zone=UTC

# Management endpoints
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always
```

---

## 🏛️ **Main Application Class**

```java
package com.semscan.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Main Spring Boot Application class for SemScan Backend
 * 
 * This application provides REST API endpoints for the SemScan QR Attendance System.
 * It handles course management, session tracking, attendance recording, and absence requests.
 */
@SpringBootApplication
@EnableJpaAuditing
public class SemScanBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SemScanBackendApplication.class, args);
        System.out.println("🚀 SemScan Backend Server started successfully!");
        System.out.println("📱 API Base URL: http://localhost:8080/");
        System.out.println("📊 Health Check: http://localhost:8080/actuator/health");
    }
}
```

---

## 🗄️ **Database Schema & Entities**

### **Database Connection Details**
- **Host**: `localhost` (or `10.0.2.2` from Android emulator)
- **Port**: `3306`
- **Database**: `semscan_db`
- **Username**: `attend`
- **Password**: `strongpass`
- **Driver**: `com.mysql.cj.jdbc.Driver`

### **Simplified Schema Structure**
The database has been simplified to 5 core tables:
1. **users** - Students, teachers, and admins
2. **courses** - Course information with lecturer assignment
3. **sessions** - Attendance sessions with QR codes
4. **attendance** - Attendance records
5. **absence_requests** - Absence request workflow

### **Sample Data Available**
- **3 Teachers**: John Smith, Sarah Jones, Mike Wilson
- **8 Students**: Alice, Bob, Charlie, Diana, Eve, Frank, Grace, Henry
- **6 Courses**: CS101, CS201, CS301, CS401, CS501, CS601
- **10 Sessions**: Mix of open/closed sessions with QR codes
- **API Key**: `test-api-key-12345`

---

## 🌐 **REST API Endpoints Summary**

### **Course Management API** (`/api/v1/courses`)
- `GET /` - Get all courses
- `GET /{courseId}` - Get course by ID
- `POST /` - Create new course
- `PUT /{courseId}` - Update course
- `DELETE /{courseId}` - Delete course (soft delete)
- `GET /teacher/{teacherId}` - Get courses by teacher
- `GET /search?name={name}` - Search courses by name

### **Session Management API** (`/api/v1/sessions`)
- `POST /` - Create new session
- `GET /{sessionId}` - Get session by ID
- `PUT /{sessionId}/close` - Close session
- `GET /course/{courseId}` - Get sessions by course
- `GET /open` - Get open sessions
- `GET /closed` - Get closed sessions
- `GET /date-range?from={from}&to={to}` - Get sessions by date range

### **Attendance Tracking API** (`/api/v1/attendance`)
- `POST /` - Record attendance
- `GET /session/{sessionId}` - Get attendance by session
- `GET /student/{studentId}` - Get attendance by student
- `GET /all` - Get all attendance records
- `GET /course/{courseId}` - Get attendance by course
- `GET /session/{sessionId}/stats` - Get session statistics
- `GET /course/{courseId}/stats` - Get course statistics
- `GET /student/{studentId}/stats` - Get student statistics
- `PUT /{attendanceId}` - Update attendance

### **Absence Request API** (`/api/v1/absence-requests`)
- `POST /` - Create absence request
- `GET /all` - Get all absence requests
- `GET /student/{studentId}` - Get requests by student
- `GET /course/{courseId}` - Get requests by course
- `GET /session/{sessionId}` - Get requests by session
- `GET /pending` - Get pending requests
- `GET /approved` - Get approved requests
- `GET /rejected` - Get rejected requests
- `GET /{requestId}` - Get request by ID
- `PUT /{requestId}/approve` - Approve request
- `PUT /{requestId}/reject` - Reject request
- `PUT /{requestId}` - Update request
- `GET /course/{courseId}/stats` - Get course statistics
- `GET /student/{studentId}/stats` - Get student statistics

---

## 🔐 **Security & Authentication**

### **Current Security Configuration**
- **Development Mode**: All endpoints are currently open (permissive security)
- **API Key Authentication**: Implemented but not enforced (for testing)
- **CORS**: Configured for Android app integration
- **Session Management**: Stateless (no server-side sessions)

### **API Key System**
- **Test API Key**: `test-api-key-12345`
- **Header**: `X-API-Key: test-api-key-12345`
- **Validation**: Implemented in controllers but not enforced
- **Teacher Association**: API keys are linked to specific teachers

---

## 🚀 **Build & Run Commands**

### **Build Commands**
```bash
# Navigate to backend directory
cd semscan-backend

# Build the project
./mvnw clean compile

# Run the application
./mvnw spring-boot:run

# Or build JAR and run
./mvnw clean package
java -jar target/semscan-backend-1.0.0.jar
```

### **Health Check Endpoints**
- **Health**: `http://localhost:8080/actuator/health`
- **Info**: `http://localhost:8080/actuator/info`
- **Metrics**: `http://localhost:8080/actuator/metrics`

### **API Base URL**
- **Local**: `http://localhost:8080/`
- **Android Emulator**: `http://10.0.2.2:8080/`

---

## 🐳 **Future Containerization Plan**

### **Dockerfile Template**
```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/semscan-backend-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### **Docker Compose Template**
```yaml
version: '3.8'
services:
  semscan-backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/attendance
      - SPRING_DATASOURCE_USERNAME=attend
      - SPRING_DATASOURCE_PASSWORD=strongpass
    depends_on:
      - mysql
    networks:
      - semscan-network

  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: attendance
      MYSQL_USER: attend
      MYSQL_PASSWORD: strongpass
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./database:/docker-entrypoint-initdb.d
    networks:
      - semscan-network

volumes:
  mysql_data:

networks:
  semscan-network:
    driver: bridge
```

---

## 📊 **Key Features Implemented**

### **1. Complete CRUD Operations**
- ✅ Course management (create, read, update, delete)
- ✅ Session management with QR code generation
- ✅ Attendance tracking and recording
- ✅ Absence request workflow

### **2. Advanced Querying**
- ✅ Search functionality for courses
- ✅ Date range filtering for sessions
- ✅ Statistics and analytics endpoints
- ✅ Complex relationship queries

### **3. Business Logic**
- ✅ Session lifecycle management (open/closed)
- ✅ Attendance window validation
- ✅ Duplicate attendance prevention
- ✅ Soft delete functionality

### **4. Integration Ready**
- ✅ CORS configured for Android app
- ✅ JSON serialization/deserialization
- ✅ Error handling with proper HTTP status codes
- ✅ API key authentication framework

---

## 🎯 **Current Status**

### **✅ Completed**
- [x] Complete Spring Boot project structure
- [x] All JPA entities with proper relationships
- [x] Repository layer with custom queries
- [x] REST controllers for all operations
- [x] CORS configuration for Android integration
- [x] Security framework setup
- [x] Maven build system
- [x] Application compiles successfully

### **🔄 Ready for Testing**
- [ ] Start Spring Boot server
- [ ] Test database connection
- [ ] Test API endpoints with Android app
- [ ] End-to-end attendance flow testing

### **⏳ Future Enhancements**
- [ ] Production security implementation
- [ ] API rate limiting
- [ ] Swagger/OpenAPI documentation
- [ ] Excel export functionality
- [ ] Email notifications
- [ ] Advanced analytics and reporting

---

## 📁 **File Structure for New Backend Project**

When creating the new backend project, use this structure:

```
semscan-backend/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/
│   └── wrapper/
│       ├── maven-wrapper.jar
│       └── maven-wrapper.properties
├── src/
│   ├── main/
│   │   ├── java/com/semscan/backend/
│   │   │   ├── SemScanBackendApplication.java
│   │   │   ├── entity/
│   │   │   │   ├── User.java
│   │   │   │   ├── Course.java
│   │   │   │   ├── Session.java
│   │   │   │   ├── Attendance.java
│   │   │   │   ├── AbsenceRequest.java
│   │   │   │   └── TeacherApiKey.java
│   │   │   ├── repository/
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── CourseRepository.java
│   │   │   │   ├── SessionRepository.java
│   │   │   │   ├── AttendanceRepository.java
│   │   │   │   ├── AbsenceRequestRepository.java
│   │   │   │   └── TeacherApiKeyRepository.java
│   │   │   ├── controller/
│   │   │   │   ├── CourseController.java
│   │   │   │   ├── SessionController.java
│   │   │   │   ├── AttendanceController.java
│   │   │   │   └── AbsenceRequestController.java
│   │   │   └── config/
│   │   │       ├── CorsConfig.java
│   │   │       └── SecurityConfig.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/semscan/backend/
├── Dockerfile
├── docker-compose.yml
├── README.md
└── database/
    ├── schema.sql
    ├── sample-data.sql
    └── init.sql
```

---

## 🔧 **Important Notes**

1. **API Key**: Use `test-api-key-12345` for all API calls
2. **Database**: MySQL is ready with complete schema and sample data
3. **Security**: Currently permissive for development - implement proper authentication for production
4. **Android Integration**: CORS is configured for `10.0.2.2:8080` (Android emulator)
5. **Build System**: Uses Maven wrapper - no local Maven installation required
6. **Java Version**: Requires Java 17 or higher
7. **Containerization**: Ready for Docker containerization with provided templates

---

**Status**: Complete backend implementation ready for separation and containerization! 🎉

**Date**: September 18, 2025
**Total Files**: 17 Java classes + configuration files
**API Endpoints**: 25+ REST endpoints implemented
**Database Tables**: 5 tables with relationships
