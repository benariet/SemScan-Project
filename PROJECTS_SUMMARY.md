# SemScan Projects - Complete Context Summary

## 📋 Overview

This workspace contains **two integrated projects** that work together to provide a QR-based attendance system for seminars:

1. **SemScan** - Android Mobile Application (Java)
2. **SemScan-API** - Spring Boot Backend API (Java)

---

## 🏗️ Project 1: SemScan (Android Mobile App)

### **Location**: `SemScan/`

### **Technology Stack**
- **Language**: Java 17
- **Platform**: Android (Min SDK 26, Target SDK 35)
- **Build System**: Gradle 8.13.1
- **Architecture**: MVVM with Activities
- **UI Framework**: Material Components
- **Networking**: Retrofit 2.9.0
- **QR Code**: ZXing (com.journeyapps:zxing-android-embedded:4.3.0)

### **Key Features**
- **Role-based system**: Teacher and Student roles
- **QR Code functionality**: Generation and scanning
- **Core Attendance**: Student scan QR → submit attendance → get feedback
- **Session Management**: Teacher start session → display QR → export data
- **Simplified Export**: Session-based CSV/Excel export
- **Settings**: Role switching and basic configuration

### **Project Structure**
```
SemScan/
├── src/main/java/org/example/semscan/
│   ├── ui/
│   │   ├── auth/              # Login, Role Selection
│   │   ├── teacher/           # Teacher activities (Start Session, Export)
│   │   ├── student/            # Student activities (Scan QR)
│   │   └── qr/                # QR Scanner/Display
│   ├── data/
│   │   ├── api/               # Retrofit API client and service
│   │   └── model/             # Data models
│   ├── utils/                 # Utilities (QR, Preferences, Logging)
│   └── constants/             # API constants and configuration
├── src/main/res/              # Layouts, strings, drawables
├── build.gradle.kts          # Dependencies and build config
└── docs/                     # Project documentation
```

### **API Configuration**
- **Base URL**: `http://132.72.50.53:8080` (Production server)
- **API Base**: `http://132.72.50.53:8080/api/v1`
- **Connection Timeout**: 10 seconds
- **SSL**: Configured for self-signed certificates (development)

### **How to Run**

#### **Option 1: Android Studio**
1. Open `SemScan/` in Android Studio
2. Wait for Gradle sync
3. Connect Android device or start emulator
4. Click "Run" button or press `Shift+F10`

#### **Option 2: Command Line**
```powershell
cd SemScan
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

#### **Option 3: Build and Install on All Devices**
```powershell
cd SemScan
.\gradlew.bat installDebugAllDevices
```

### **Key Files**
- `src/main/java/org/example/semscan/constants/ApiConstants.java` - API configuration
- `src/main/java/org/example/semscan/data/api/ApiClient.java` - Retrofit client setup
- `src/main/java/org/example/semscan/data/api/ApiService.java` - API endpoint definitions
- `build.gradle.kts` - Dependencies and build configuration

---

## 🏗️ Project 2: SemScan-API (Spring Boot Backend)

### **Location**: `SemScan-API/`

### **Technology Stack**
- **Language**: Java 21
- **Framework**: Spring Boot 3.5.5
- **Build System**: Gradle
- **Database**: MySQL (via SSH tunnel on port 3307)
- **Security**: Spring Security with JWT
- **API Documentation**: Swagger/OpenAPI
- **Email**: Spring Mail (SMTP)

### **Key Features**
- **REST API**: Complete RESTful API for attendance system
- **Authentication**: BGU SOAP authentication + JWT tokens
- **Seminars Management**: Create and manage seminars
- **Sessions Management**: Open/close sessions, QR code generation
- **Attendance Tracking**: Record and manage attendance
- **Waiting List**: Registration waiting list with promotion system
- **Export**: CSV/Excel export functionality
- **Logging**: Comprehensive logging system
- **Email Notifications**: Email support for various events

### **Project Structure**
```
SemScan-API/
├── src/main/java/edu/bgu/semscanapi/
│   ├── controller/            # REST controllers
│   ├── service/               # Business logic
│   ├── repository/            # Data access (JPA)
│   ├── entity/                # JPA entities
│   ├── dto/                   # Data transfer objects
│   ├── config/                # Configuration classes
│   ├── exception/             # Exception handling
│   └── util/                  # Utilities
├── src/main/resources/
│   ├── application.properties # Main configuration
│   └── application-global.properties # Global config
├── build.gradle              # Dependencies
└── docs/                     # API documentation
```

### **Database Configuration**
- **Host**: `127.0.0.1:3307` (SSH tunnel to remote MySQL)
- **Database**: `semscan_db`
- **Username**: `semscan_admin`
- **Timezone**: `Asia/Jerusalem`

### **Server Configuration**
- **Port**: `8080`
- **Base URL**: `http://localhost:8080`
- **API Base**: `http://localhost:8080/api/v1`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **Actuator**: `http://localhost:8080/actuator`

### **How to Run**

#### **Option 1: IntelliJ IDEA (Recommended)**
1. Open `SemScan-API/` in IntelliJ IDEA
2. Open `src/main/java/edu/bgu/semscanapi/SemScanApiApplication.java`
3. Right-click → **"Run 'SemScanApiApplication.main()'"**
4. Wait for: `🚀 SemScan API started successfully!`

#### **Option 2: Gradle Command**
```powershell
cd SemScan-API
.\gradlew.bat bootRun
```

#### **Option 3: Run JAR**
```powershell
cd SemScan-API
.\gradlew.bat build
java -jar build\libs\SemScan-API-0.0.1-SNAPSHOT.jar
```

### **Verify API is Running**
```powershell
# Test ping endpoint
Invoke-WebRequest -Uri http://localhost:8080/api/v1/diagnostic/ping -Method GET

# Or use curl
curl.exe http://localhost:8080/api/v1/diagnostic/ping
```

### **Key Files**
- `src/main/java/edu/bgu/semscanapi/SemScanApiApplication.java` - Main application class
- `src/main/resources/application.properties` - Configuration
- `build.gradle` - Dependencies
- `COMPLETE_API_DOCUMENTATION.json` - Full API documentation

---

## 🔗 Integration Between Projects

### **Connection Flow**
1. **Android App** (`SemScan`) connects to **Backend API** (`SemScan-API`)
2. **API Base URL**: Configured in `SemScan/src/main/java/org/example/semscan/constants/ApiConstants.java`
3. **Current Production URL**: `http://132.72.50.53:8080`
4. **For Local Development**: Change to `http://10.0.2.2:8080` (Android emulator) or `http://localhost:8080` (physical device with port forwarding)

### **API Endpoints Used by Mobile App**
- `/api/v1/seminars` - Get/create seminars
- `/api/v1/sessions` - Session management
- `/api/v1/attendance` - Attendance submission
- `/api/v1/auth/login` - User authentication
- `/api/v1/export/csv` - CSV export
- `/api/v1/export/xlsx` - Excel export

### **Port Forwarding (Android Emulator)**
If running API locally and testing on Android emulator:
```powershell
adb reverse tcp:8080 tcp:8080
```

---

## 🚀 Quick Start Guide

### **Step 1: Start the Backend API**
```powershell
# Terminal 1
cd SemScan-API
.\gradlew.bat bootRun
```
Wait for: `🚀 SemScan API started successfully!`

### **Step 2: Start the Android App**
```powershell
# Terminal 2 (or Android Studio)
cd SemScan
.\gradlew.bat installDebug
```

### **Step 3: Verify Connection**
- Open Android app
- Check logs for API calls
- Verify attendance flow works

---

## 📊 Project Status

### **SemScan (Android App)**
- ✅ **Status**: Lean MVP Complete
- ✅ **Build**: Working (Java 17, Gradle 8.13.1)
- ✅ **Features**: Core attendance functionality implemented
- 🔄 **Ready for**: Backend integration testing

### **SemScan-API (Backend)**
- ✅ **Status**: Fully Functional
- ✅ **Build**: Working (Java 21, Spring Boot 3.5.5)
- ✅ **Features**: Complete REST API with authentication
- ✅ **Database**: MySQL connection configured
- ✅ **Documentation**: Swagger/OpenAPI available

---

## 🔧 Development Workflow

### **Making Changes**

1. **Backend Changes**:
   - Edit files in `SemScan-API/src/main/java/`
   - Restart Spring Boot application
   - Test via Swagger UI or Postman

2. **Mobile App Changes**:
   - Edit files in `SemScan/src/main/java/`
   - Rebuild APK: `.\gradlew.bat assembleDebug`
   - Install: `.\gradlew.bat installDebug`

### **Testing Integration**

1. **Start Backend**: `cd SemScan-API && .\gradlew.bat bootRun`
2. **Start Mobile App**: Run from Android Studio or install APK
3. **Test Flow**: 
   - Login → Create Session → Scan QR → Submit Attendance → Export

---

## 📝 Important Notes

### **Database Connection**
- Backend requires **SSH tunnel** to MySQL database on port **3307**
- Ensure tunnel is active before starting API
- Connection string: `jdbc:mysql://127.0.0.1:3307/semscan_db`

### **API URL Configuration**
- **Production**: `http://132.72.50.53:8080` (hardcoded in `ApiConstants.java`)
- **Local Development**: Change to `http://10.0.2.2:8080` for emulator
- **Physical Device**: Use `http://<your-ip>:8080` or set up port forwarding

### **Build Requirements**
- **Java**: 
  - Android App: Java 17
  - Backend API: Java 21
- **Gradle**: Both projects use Gradle wrapper
- **Android SDK**: Required for mobile app (Android Studio)

---

## 📚 Documentation

### **Android App Documentation**
- `SemScan/docs/project-context/PROJECT-CONTEXT.md` - Complete project context
- `SemScan/docs/backend/` - Backend integration guides

### **API Documentation**
- `SemScan-API/README.md` - API overview
- `SemScan-API/COMPLETE_API_DOCUMENTATION.json` - Full API docs
- `SemScan-API/scripts/start-server.md` - Server startup guide
- Swagger UI: `http://localhost:8080/swagger-ui.html` (when running)

---

## 🐛 Troubleshooting

### **Backend Won't Start**
1. Check MySQL SSH tunnel is active (port 3307)
2. Verify port 8080 is not in use
3. Check logs: `SemScan-API/logs/semscan-api-error.log`

### **Mobile App Can't Connect**
1. Verify backend is running: `curl http://localhost:8080/api/v1/diagnostic/ping`
2. Check API URL in `ApiConstants.java`
3. For emulator: Set up port forwarding: `adb reverse tcp:8080 tcp:8080`
4. Check network security config in `network_security_config.xml`

### **Build Errors**
1. **Android**: Ensure Java 17 is set in project
2. **Backend**: Ensure Java 21 is set in project
3. Run `.\gradlew.bat clean` and rebuild

---

## ✅ Summary

Both projects are **fully functional** and ready for development:

- **SemScan (Android)**: Lean MVP with core attendance features
- **SemScan-API (Backend)**: Complete REST API with authentication and database

The projects are **properly integrated** and can communicate via REST API. The mobile app is configured to connect to the production server, but can be easily switched to local development.

---

**Last Updated**: January 2025  
**Workspace**: `C:\Users\benariet\IdeaProjects\SemScan-Project`  
**Status**: ✅ Both projects analyzed and documented


