# 📁 SemScan Backend - Complete File List

This document lists all files that need to be copied when creating the new backend project.

---

## 🗂️ **Complete File Structure**

### **Root Level Files**
```
semscan-backend/
├── pom.xml                                    # Maven configuration
├── mvnw.cmd                                   # Maven wrapper (Windows)
├── mvnw                                       # Maven wrapper (Unix) - if exists
└── .mvn/wrapper/                              # Maven wrapper configuration
    ├── maven-wrapper.jar                      # Maven wrapper JAR
    └── maven-wrapper.properties               # Maven wrapper properties
```

### **Source Code Files**
```
src/main/java/com/semscan/backend/
├── SemScanBackendApplication.java             # Main application class
├── entity/                                    # JPA Entities (6 files)
│   ├── User.java                              # User management entity
│   ├── Course.java                            # Course entity
│   ├── Session.java                           # Session entity
│   ├── Attendance.java                        # Attendance entity
│   ├── AbsenceRequest.java                    # AbsenceRequest entity
│   └── TeacherApiKey.java                     # API Key entity
├── repository/                                # Data Access Layer (6 files)
│   ├── UserRepository.java                    # User repository
│   ├── CourseRepository.java                  # Course repository
│   ├── SessionRepository.java                 # Session repository
│   ├── AttendanceRepository.java              # Attendance repository
│   ├── AbsenceRequestRepository.java          # AbsenceRequest repository
│   └── TeacherApiKeyRepository.java           # API Key repository
├── controller/                                # REST Controllers (4 files)
│   ├── CourseController.java                  # Course management API
│   ├── SessionController.java                 # Session management API
│   ├── AttendanceController.java              # Attendance tracking API
│   └── AbsenceRequestController.java          # Absence request API
└── config/                                    # Configuration (2 files)
    ├── CorsConfig.java                        # CORS configuration
    └── SecurityConfig.java                    # Security configuration
```

### **Configuration Files**
```
src/main/resources/
└── application.properties                     # Spring Boot configuration
```

### **Test Files** (if any exist)
```
src/test/java/com/semscan/backend/
└── [test files if they exist]
```

---

## 📋 **File Copy Checklist**

### **Essential Files to Copy**
- [ ] `pom.xml` - Maven configuration
- [ ] `mvnw.cmd` - Maven wrapper for Windows
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

## 🐳 **Additional Files for Containerization**

### **Docker Files** (to be created)
- [ ] `Dockerfile` - Docker image configuration
- [ ] `docker-compose.yml` - Multi-container setup
- [ ] `.dockerignore` - Docker ignore file

### **Database Files** (to be copied from current project)
- [ ] `database/schema.sql` - Database schema
- [ ] `database/sample-data.sql` - Sample data
- [ ] `database/init.sql` - Database initialization

### **Documentation Files** (to be created)
- [ ] `README.md` - Project documentation
- [ ] `API.md` - API documentation
- [ ] `DEPLOYMENT.md` - Deployment guide

---

## 📊 **File Count Summary**

- **Total Java Files**: 19 files
  - 1 Main application class
  - 6 Entity classes
  - 6 Repository classes
  - 4 Controller classes
  - 2 Configuration classes
- **Configuration Files**: 2 files
  - 1 Maven configuration (pom.xml)
  - 1 Spring Boot configuration (application.properties)
- **Maven Wrapper Files**: 3 files
  - 1 Windows wrapper script
  - 1 Maven wrapper JAR
  - 1 Maven wrapper properties

**Total Files to Copy**: 24 files

---

## 🚀 **Quick Copy Commands**

### **Windows PowerShell**
```powershell
# Create new backend directory
mkdir semscan-backend-new
cd semscan-backend-new

# Copy all backend files from current project
Copy-Item -Path "..\semscan-backend\*" -Destination "." -Recurse
```

### **Linux/Mac**
```bash
# Create new backend directory
mkdir semscan-backend-new
cd semscan-backend-new

# Copy all backend files from current project
cp -r ../semscan-backend/* .
```

---

## ✅ **Verification Checklist**

After copying files, verify:
- [ ] All 19 Java files are present
- [ ] Maven configuration (pom.xml) is correct
- [ ] Application properties are configured
- [ ] Maven wrapper files are present
- [ ] Project structure matches expected layout
- [ ] All package declarations are correct
- [ ] All imports are valid

---

**Status**: Ready for backend project separation! 🎉
