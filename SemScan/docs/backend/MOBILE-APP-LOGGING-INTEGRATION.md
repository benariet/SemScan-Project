# MOBILE APP LOGS INTEGRATION GUIDE

## OVERVIEW
This guide shows how to integrate the SemScan API logging system into your mobile app (Android/iOS) to send logs to the server.

## API ENDPOINT
```
POST http://your-server:8080/api/v1/logs
Headers: x-api-key: presenter-001-api-key-12345
Content-Type: application/json
```

## ANDROID IMPLEMENTATION

### 1. Dependencies (Already included in build.gradle)
```gradle
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
```

### 2. Usage in Your Android App

#### Initialize the Logger
```java
// In your Activity or Application class
ServerLogger logger = ServerLogger.getInstance(this);

// Configure logging
logger.setServerLoggingEnabled(true);
logger.updateUserContext("student-001", "STUDENT");
```

#### Basic Logging
```java
// Different log levels
logger.i(ServerLogger.TAG_UI, "User logged in successfully");
logger.d(ServerLogger.TAG_QR, "QR code scanned: " + qrCode);
logger.w(ServerLogger.TAG_NETWORK, "Slow network connection detected");

// Error logging with exception
try {
    // Some risky operation
} catch (Exception e) {
    logger.e(ServerLogger.TAG_API, "Payment processing failed", e);
}
```

#### Specialized Logging Methods
```java
// User actions
logger.userAction("Login", "User logged in with email");

// Session events
logger.session("SessionStarted", "Session ID: session-123");

// QR code events
logger.qr("QRScanned", "QR Code: session-123");

// Attendance events
logger.attendance("AttendanceSubmitted", "Student ID: student-001");

// Security events
logger.security("InvalidQRCode", "Attempted to scan invalid QR code");

// Performance events
logger.performance("SlowResponse", "API call took 5.2 seconds");

// API logging
logger.api("POST", "/api/v1/attendance", "Submitting attendance");
logger.apiResponse("POST", "/api/v1/attendance", 200, "Success");
logger.apiError("POST", "/api/v1/attendance", 500, "Internal server error");
```

#### Force Send Logs
```java
// Force send all pending logs immediately
logger.flushLogs();
```

### 3. Available Log Tags
- `ServerLogger.TAG_UI` - User interface events
- `ServerLogger.TAG_API` - API calls and responses
- `ServerLogger.TAG_QR` - QR code scanning events
- `ServerLogger.TAG_SESSION` - Session management
- `ServerLogger.TAG_ATTENDANCE` - Attendance tracking
- `ServerLogger.TAG_SECURITY` - Security events
- `ServerLogger.TAG_PERFORMANCE` - Performance monitoring

### 4. Log Levels
- `VERBOSE` - Detailed debugging information
- `DEBUG` - Debug information
- `INFO` - General information
- `WARN` - Warning messages
- `ERROR` - Error messages

## FEATURES

### Batch Logging
- Logs are automatically batched and sent in groups of 10
- ERROR logs are sent immediately
- Logs are also sent after 30 seconds if batch is not full
- Automatic retry on network failures

### Device Information
The logger automatically includes:
- Device model and manufacturer
- Android version
- App version
- User ID and role
- Timestamp

### Error Handling
- Network errors: Automatic retry with exponential backoff
- Server errors: Logs are re-queued for retry
- Invalid API key: Check configuration
- Rate limiting: Implemented backoff strategy

## SAMPLE JSON REQUEST
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
      "message": "Payment processing failed",
      "userId": "student-001",
      "userRole": "STUDENT",
      "deviceInfo": "Android 13 - Samsung SM-G991B",
      "appVersion": "1.0.0",
      "stackTrace": "java.lang.Exception: Network timeout...",
      "exceptionType": "java.net.SocketTimeoutException"
    }
  ]
}
```

## EXPECTED RESPONSE
```json
{
  "success": true,
  "message": "Logs processed successfully",
  "processedCount": 2
}
```

## BEST PRACTICES

1. **Use appropriate log levels**: INFO for normal operations, ERROR for failures
2. **Include meaningful context**: Add relevant details to log messages
3. **Use specialized methods**: Use `userAction()`, `session()`, etc. for better categorization
4. **Handle errors gracefully**: Always log exceptions with context
5. **Don't block UI**: Logging is asynchronous and won't block the UI thread
6. **Configure appropriately**: Enable/disable server logging based on user preferences

## TESTING

### Test the Integration
```java
// Test basic logging
ServerLogger logger = ServerLogger.getInstance(this);
logger.i(ServerLogger.TAG_UI, "Test log message");
logger.flushLogs(); // Force send to test immediately
```

### Verify Server Reception
Check your server logs to ensure logs are being received and processed correctly.

## CONFIGURATION

### Enable/Disable Server Logging
```java
logger.setServerLoggingEnabled(false); // Disable server logging
logger.setServerLoggingEnabled(true);  // Enable server logging
```

### Update User Context
```java
logger.updateUserContext("student-001", "STUDENT");
logger.updateUserContext("presenter-001", "PRESENTER");
```

## TROUBLESHOOTING

### Common Issues
1. **Logs not appearing on server**: Check API key and server URL
2. **Network errors**: Ensure device has internet connection
3. **Large log batches**: Consider reducing log frequency for production

### Debug Mode
Enable Android system logging to see local logs:
```java
// Check Android logs for debugging
adb logcat | grep "SemScan"
```

## INTEGRATION IN EXISTING ACTIVITIES

### Example: QR Scanner Activity
```java
public class QRScannerActivity extends AppCompatActivity {
    private ServerLogger logger;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger = ServerLogger.getInstance(this);
        logger.session("QRScannerOpened", "QR Scanner activity started");
    }
    
    private void onQRCodeScanned(String qrCode) {
        logger.qr("QRScanned", "QR Code: " + qrCode);
        // Process QR code...
    }
    
    @Override
    protected void onDestroy() {
        logger.session("QRScannerClosed", "QR Scanner activity closed");
        logger.flushLogs(); // Ensure logs are sent
        super.onDestroy();
    }
}
```

This integration provides comprehensive logging capabilities for your SemScan mobile app, ensuring all important events are tracked and sent to the server for analysis and debugging.
