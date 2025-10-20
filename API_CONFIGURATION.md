# SemScan API Configuration

## 🚀 Quick Start

### Server Configuration
- **Port:** 8080
- **Base URL:** `http://localhost:8080` (development)
- **Production URL:** `http://132.73.167.231:8080`

### Database Configuration
- **Type:** MySQL
- **Host:** 127.0.0.1:3306
- **Database:** semscan_db
- **Username:** root
- **Password:** root

## 📱 Android App Configuration

### Network Setup
```bash
# For Android development, use adb reverse proxy
adb reverse tcp:8080 tcp:8080
```

### Base URL Configuration
```java
// Android app base URL
private static final String BASE_URL = "http://localhost:8080/";
```

### API Key Authentication
```java
// Add API key to all protected endpoints
@Header("x-api-key") String apiKey
```

## 🔑 Authentication

### API Key Required Endpoints
- `/api/v1/seminars` (GET, POST)
- `/api/v1/sessions` (GET, POST)
- `/api/v1/sessions/open` (GET)
- `/api/v1/attendance/pending-requests` (GET)
- `/api/v1/attendance/{id}/approve` (POST)
- `/api/v1/attendance/{id}/reject` (POST)
- `/api/v1/export/csv` (GET)
- `/api/v1/export/xlsx` (GET)

### Public Endpoints (No API Key Required)
- `/api/v1/info/endpoints` (GET)
- `/api/v1/attendance` (GET, POST)
- `/api/v1/attendance/session/{sessionId}` (GET)
- `/api/v1/attendance/manual-request` (POST)
- `/api/v1/qr/session/{sessionId}` (GET)
- `/api/v1/qr/config` (GET)

## 🌐 Environment Configuration

### Development
```properties
server.port=8080
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/semscan_db
spring.jpa.hibernate.ddl-auto=update
```

### Production
```properties
server.port=8080
spring.datasource.url=jdbc:mysql://production-host:3306/semscan_db
spring.jpa.hibernate.ddl-auto=validate
```

## 📊 Logging Configuration

### Log Files
- **Main Log:** `logs/semscan-api.log`
- **Session Logs:** `logs/sessions/session-default-{date}.log`
- **Error Log:** `logs/semscan-api.log-error.log`

### Log Levels
```properties
logging.level.edu.bgu.semscanapi=INFO
logging.level.org.springframework.web=INFO
logging.level.org.hibernate.SQL=WARN
```

## 🔧 CORS Configuration

```java
@CrossOrigin(origins = "*")
```

All endpoints support CORS for cross-origin requests.

## 📡 Network Requirements

### Firewall Rules
```bash
# Allow port 8080 through Windows Firewall
netsh advfirewall firewall add rule name="SemScan API Port 8080" dir=in action=allow protocol=TCP localport=8080
```

### Android Development
- Use `adb reverse tcp:8080 tcp:8080` for local development
- Android devices must be connected via USB
- Both devices and computer must be on same network

## 🗄️ Database Schema

### Key Tables
- `seminars` - Seminar information
- `sessions` - Session details
- `attendance` - Attendance records
- `users` - User accounts
- `presenter_api_keys` - API authentication keys

### Migration Files
- `V2__add_manual_attendance_fields.sql` - Manual attendance support

## 🚨 Security

### API Key Management
- API keys stored in `presenter_api_keys` table
- Keys must be active (`is_active = true`)
- Keys are validated on each request

### Request Validation
- All protected endpoints require valid API key
- Session and student IDs must be valid
- Request body validation for all POST requests

## 📈 Monitoring

### Health Checks
- `/actuator/health` - Server health status
- `/actuator/info` - Application information
- `/actuator/loggers` - Logging configuration

### Metrics
- Request/response logging with correlation IDs
- Authentication event logging
- Database operation logging

## 🔄 Deployment

### Development
1. Start MySQL database
2. Run Spring Boot application
3. Set up adb reverse for Android development
4. Configure API keys in database

### Production
1. Deploy to production server
2. Configure production database
3. Set up proper firewall rules
4. Configure monitoring and logging

## 📞 Troubleshooting

### Common Issues
1. **Network connectivity:** Use adb reverse for Android development
2. **API key errors:** Check database for valid API keys
3. **Database connection:** Verify MySQL is running and accessible
4. **Firewall issues:** Add Windows Firewall rule for port 8080

### Log Files
- Check `logs/semscan-api.log` for application logs
- Check `logs/semscan-api.log-error.log` for error logs
- Check session logs in `logs/sessions/` directory
