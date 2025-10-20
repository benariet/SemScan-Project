# SemScan Logging System - Usage Examples
=====================================================

This document provides examples of how to use the SemScan logging system.

## 🎯 **API Endpoints Available:**

### **1. Send Logs from Mobile App**
```
POST /api/v1/logs
Headers: x-api-key: presenter-001-api-key-12345
Content-Type: application/json
```

### **2. Get Error Logs**
```
GET /api/v1/logs/errors
Headers: x-api-key: presenter-001-api-key-12345
```

### **3. Get Recent Logs**
```
GET /api/v1/logs/recent?limit=100
Headers: x-api-key: presenter-001-api-key-12345
```

### **4. Get Log Statistics**
```
GET /api/v1/logs/stats
Headers: x-api-key: presenter-001-api-key-12345
```

## 📱 **Mobile App Integration Examples:**

### **Android - Send Logs**
```java
// Example Android code to send logs
public class LogSender {
    private static final String API_URL = "http://localhost:8080/api/v1/logs";
    private static final String API_KEY = "presenter-001-api-key-12345";
    
    public void sendLog(String level, String tag, String message) {
        // Create log entry
        AppLogEntry logEntry = new AppLogEntry();
        logEntry.setTimestamp(System.currentTimeMillis());
        logEntry.setLevel(level);
        logEntry.setTag(tag);
        logEntry.setMessage(message);
        logEntry.setUserId("student-001");
        logEntry.setUserRole("STUDENT");
        logEntry.setDeviceInfo("Android 13 - Samsung SM-G991B");
        logEntry.setAppVersion("1.0.0");
        
        // Create request
        LogRequest request = new LogRequest();
        request.setLogs(Arrays.asList(logEntry));
        
        // Send to server
        sendToServer(request);
    }
}
```

### **Example Log Entries:**
```json
{
  "logs": [
    {
      "timestamp": 1697784000000,
      "level": "INFO",
      "tag": "SemScan-UI",
      "message": "User logged in successfully",
      "userId": "student-001",
      "userRole": "STUDENT",
      "deviceInfo": "Android 13 - Samsung SM-G991B",
      "appVersion": "1.0.0",
      "stackTrace": null,
      "exceptionType": null
    },
    {
      "timestamp": 1697784001000,
      "level": "ERROR",
      "tag": "SemScan-API",
      "message": "Failed to connect to server",
      "userId": "student-001",
      "userRole": "STUDENT",
      "deviceInfo": "Android 13 - Samsung SM-G991B",
      "appVersion": "1.0.0",
      "stackTrace": "java.net.ConnectException: Connection refused\n\tat java.net.PlainSocketImpl.socketConnect...",
      "exceptionType": "java.net.ConnectException"
    }
  ]
}
```

## 🧪 **Testing with cURL:**

### **1. Send Test Logs**
```bash
curl -X POST http://localhost:8080/api/v1/logs \
  -H "Content-Type: application/json" \
  -H "x-api-key: presenter-001-api-key-12345" \
  -d '{
    "logs": [
      {
        "timestamp": 1697784000000,
        "level": "INFO",
        "tag": "SemScan-Test",
        "message": "Test log message",
        "userId": "student-001",
        "userRole": "STUDENT",
        "deviceInfo": "Android 13 - Test Device",
        "appVersion": "1.0.0"
      }
    ]
  }'
```

### **2. Get Error Logs**
```bash
curl -X GET http://localhost:8080/api/v1/logs/errors \
  -H "x-api-key: presenter-001-api-key-12345"
```

### **3. Get Recent Logs**
```bash
curl -X GET http://localhost:8080/api/v1/logs/recent?limit=50 \
  -H "x-api-key: presenter-001-api-key-12345"
```

### **4. Get Log Statistics**
```bash
curl -X GET http://localhost:8080/api/v1/logs/stats \
  -H "x-api-key: presenter-001-api-key-12345"
```

## 📊 **Expected Responses:**

### **Send Logs Response:**
```json
{
  "success": true,
  "message": "Logs processed successfully",
  "processedCount": 1
}
```

### **Error Logs Response:**
```json
[
  {
    "id": 1,
    "timestamp": 1697784000000,
    "level": "ERROR",
    "tag": "SemScan-API",
    "message": "Connection failed",
    "userId": "student-001",
    "userRole": "STUDENT",
    "deviceInfo": "Android 13 - Samsung SM-G991B",
    "appVersion": "1.0.0",
    "stackTrace": "java.net.ConnectException...",
    "exceptionType": "java.net.ConnectException",
    "createdAt": "2024-10-19T16:30:00"
  }
]
```

### **Log Statistics Response:**
```json
{
  "totalLogs": 150,
  "errorLogs": 5,
  "infoLogs": 120,
  "warnLogs": 20,
  "debugLogs": 5
}
```

## 🔧 **Database Schema:**

The logging system uses these tables:

### **app_logs table:**
- `id` - Primary key
- `timestamp` - Unix timestamp from mobile app
- `level` - Log level (DEBUG, INFO, WARN, ERROR)
- `tag` - Log tag/category
- `message` - Log message
- `user_id` - User who generated the log
- `user_role` - User role (STUDENT, PRESENTER)
- `device_info` - Device information
- `app_version` - App version
- `stack_trace` - Exception stack trace
- `exception_type` - Exception type
- `created_at` - Server timestamp

## 🚀 **Integration Steps:**

1. **Update your mobile app** to send logs to `/api/v1/logs`
2. **Use the API key** `presenter-001-api-key-12345` for authentication
3. **Send logs in batches** for better performance
4. **Monitor error logs** using `/api/v1/logs/errors`
5. **Check statistics** using `/api/v1/logs/stats`

## 📈 **Analytics Features:**

- **Log aggregation** by level, tag, user
- **Error tracking** and monitoring
- **User behavior analysis**
- **Device compatibility tracking**
- **App version analytics**

## 🔒 **Security Features:**

- **API key authentication** required
- **Input validation** and sanitization
- **Rate limiting** (can be implemented)
- **Data privacy** compliance

## 📝 **Log Levels:**

- **DEBUG** - Detailed debugging information
- **INFO** - General information messages
- **WARN** - Warning messages
- **ERROR** - Error messages with stack traces

## 🏷️ **Recommended Tags:**

- `SemScan-UI` - User interface events
- `SemScan-API` - API communication
- `SemScan-Auth` - Authentication events
- `SemScan-QR` - QR code scanning
- `SemScan-Attendance` - Attendance tracking
- `SemScan-Network` - Network operations

This logging system provides comprehensive monitoring and analytics for your SemScan mobile application!
