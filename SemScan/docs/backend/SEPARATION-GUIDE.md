# 🔄 SemScan Backend - Project Separation Guide

This guide provides step-by-step instructions for separating the backend from the current project and creating a new standalone backend project.

---

## 📋 **Overview**

### **Current Situation**
- Backend code is currently in `semscan-backend/` directory within the main project
- Backend is fully implemented and ready for separation
- All context and documentation has been saved

### **Goal**
- Create a new, independent backend project
- Remove backend from current project
- Prepare for future containerization

---

## 🗂️ **Step-by-Step Separation Process**

### **Step 1: Create New Backend Project Directory**
```bash
# Navigate to parent directory of current project
cd ..

# Create new backend project directory
mkdir semscan-backend-standalone
cd semscan-backend-standalone
```

### **Step 2: Copy All Backend Files**
```bash
# Copy entire backend directory structure
cp -r ../SemScan/semscan-backend/* .

# Or on Windows PowerShell:
# Copy-Item -Path "..\SemScan\semscan-backend\*" -Destination "." -Recurse
```

### **Step 3: Verify File Structure**
Ensure the following structure exists:
```
semscan-backend-standalone/
├── pom.xml
├── mvnw.cmd
├── .mvn/wrapper/
│   ├── maven-wrapper.jar
│   └── maven-wrapper.properties
└── src/main/
    ├── java/com/semscan/backend/
    │   ├── SemScanBackendApplication.java
    │   ├── entity/ (6 files)
    │   ├── repository/ (6 files)
    │   ├── controller/ (4 files)
    │   └── config/ (2 files)
    └── resources/
        └── application.properties
```

### **Step 4: Test the New Backend Project**
```bash
# Build the project
./mvnw clean compile

# Run the application
./mvnw spring-boot:run
```

### **Step 5: Remove Backend from Original Project**
```bash
# Navigate back to original project
cd ../SemScan

# Remove the backend directory
rm -rf semscan-backend/

# Or on Windows PowerShell:
# Remove-Item -Path "semscan-backend" -Recurse -Force
```

### **Step 6: Update Original Project Documentation**
Update any references to the backend in the original project:
- Remove backend-related sections from README files
- Update project context documentation
- Remove backend configuration files

---

## 📁 **Files to Copy**

### **Essential Files (24 total)**
- [ ] `pom.xml` - Maven configuration
- [ ] `mvnw.cmd` - Maven wrapper (Windows)
- [ ] `.mvn/wrapper/maven-wrapper.jar` - Maven wrapper JAR
- [ ] `.mvn/wrapper/maven-wrapper.properties` - Maven wrapper properties
- [ ] `src/main/java/com/semscan/backend/SemScanBackendApplication.java` - Main class
- [ ] `src/main/resources/application.properties` - Configuration

### **Entity Files (6 files)**
- [ ] `src/main/java/com/semscan/backend/entity/User.java`
- [ ] `src/main/java/com/semscan/backend/entity/Course.java`
- [ ] `src/main/java/com/semscan/backend/entity/Session.java`
- [ ] `src/main/java/com/semscan/backend/entity/Attendance.java`
- [ ] `src/main/java/com/semscan/backend/entity/AbsenceRequest.java`
- [ ] `src/main/java/com/semscan/backend/entity/TeacherApiKey.java`

### **Repository Files (6 files)**
- [ ] `src/main/java/com/semscan/backend/repository/UserRepository.java`
- [ ] `src/main/java/com/semscan/backend/repository/CourseRepository.java`
- [ ] `src/main/java/com/semscan/backend/repository/SessionRepository.java`
- [ ] `src/main/java/com/semscan/backend/repository/AttendanceRepository.java`
- [ ] `src/main/java/com/semscan/backend/repository/AbsenceRequestRepository.java`
- [ ] `src/main/java/com/semscan/backend/repository/TeacherApiKeyRepository.java`

### **Controller Files (4 files)**
- [ ] `src/main/java/com/semscan/backend/controller/CourseController.java`
- [ ] `src/main/java/com/semscan/backend/controller/SessionController.java`
- [ ] `src/main/java/com/semscan/backend/controller/AttendanceController.java`
- [ ] `src/main/java/com/semscan/backend/controller/AbsenceRequestController.java`

### **Configuration Files (2 files)**
- [ ] `src/main/java/com/semscan/backend/config/CorsConfig.java`
- [ ] `src/main/java/com/semscan/backend/config/SecurityConfig.java`

---

## 🔧 **Post-Separation Configuration**

### **Update Application Properties**
Ensure the new backend project has correct configuration:
```properties
# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/attendance?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4&useUnicode=true
spring.datasource.username=attend
spring.datasource.password=strongpass

# Server Configuration
server.port=8080

# CORS Configuration
cors.allowed.origins=http://localhost:8080,http://10.0.2.2:8080,http://localhost:3000
```

### **Create New Project README**
Create a comprehensive README for the new backend project:
```markdown
# SemScan Backend

Spring Boot REST API for the SemScan QR Attendance System.

## Quick Start
1. Ensure MySQL is running
2. Run: `./mvnw spring-boot:run`
3. API available at: http://localhost:8080

## API Documentation
- Health Check: http://localhost:8080/actuator/health
- API Base URL: http://localhost:8080/api/v1/
```

---

## 🧪 **Testing the Separation**

### **Test 1: Build Verification**
```bash
cd semscan-backend-standalone
./mvnw clean compile
```
**Expected**: Build succeeds without errors

### **Test 2: Application Startup**
```bash
./mvnw spring-boot:run
```
**Expected**: Application starts and shows startup messages

### **Test 3: Health Check**
```bash
curl http://localhost:8080/actuator/health
```
**Expected**: Returns health status

### **Test 4: API Endpoint Test**
```bash
curl -H "X-API-Key: test-api-key-12345" http://localhost:8080/api/v1/courses
```
**Expected**: Returns course data or empty array

---

## 🐳 **Prepare for Containerization**

### **Create Dockerfile**
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/semscan-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### **Create Docker Compose**
```yaml
version: '3.8'
services:
  semscan-backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/attendance
    depends_on:
      - mysql
  
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: attendance
      MYSQL_USER: attend
      MYSQL_PASSWORD: strongpass
    ports:
      - "3306:3306"
```

---

## 📊 **Verification Checklist**

### **Backend Project Verification**
- [ ] All 24 files copied successfully
- [ ] Project builds without errors
- [ ] Application starts successfully
- [ ] Health check endpoint responds
- [ ] API endpoints are accessible
- [ ] Database connection works

### **Original Project Cleanup**
- [ ] Backend directory removed
- [ ] Documentation updated
- [ ] No broken references remain
- [ ] Project still builds successfully

### **Documentation**
- [ ] New backend project has README
- [ ] API documentation is available
- [ ] Containerization files are ready
- [ ] All context is preserved in docs/

---

## 🚨 **Important Notes**

### **Before Separation**
- ✅ All backend context has been saved in `docs/backend/`
- ✅ Complete file list is documented
- ✅ Containerization plan is ready
- ✅ All code is fully implemented

### **After Separation**
- 🔄 Test the new backend project thoroughly
- 🔄 Update any Android app API URLs if needed
- 🔄 Ensure database is accessible from new project
- 🔄 Verify all functionality works independently

### **Database Considerations**
- The MySQL database can remain in the original project
- Or create a separate database setup for the new backend
- Ensure proper connection configuration

---

## 🎯 **Next Steps After Separation**

1. **Test the new backend project**
2. **Create containerization files**
3. **Set up CI/CD pipeline**
4. **Deploy to production environment**
5. **Update Android app configuration if needed**

---

**Status**: Ready for backend project separation! 🔄

**Total Files to Copy**: 24 files
**Estimated Time**: 15-30 minutes
**Risk Level**: Low (all context preserved)
