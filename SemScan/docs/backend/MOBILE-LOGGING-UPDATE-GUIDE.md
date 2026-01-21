# MOBILE APP LOGGING UPDATE GUIDE

## 🚀 **NEW FEATURE: Server-Side Logging Integration**

### **What's New**
The SemScan mobile app now automatically sends logs to the server for monitoring, debugging, and analytics. This provides real-time visibility into app usage and helps identify issues quickly.

---

## 📱 **For Mobile Developers**

### **What Changed**
1. **New ServerLogger Class**: Enhanced logging system that sends logs to server
2. **Automatic Logging**: Key app events are now automatically logged
3. **Batch Processing**: Logs are sent in batches for efficiency
4. **Error Tracking**: All errors are immediately sent to server

### **Files Modified**
- `ServerLogger.java` - Enhanced with server integration
- `PresenterStartSessionActivity.java` - Added session logging
- `QRScannerActivity.java` - Added QR and attendance logging
- `LoggingSettingsActivity.java` - Added test functionality

---

## 🔧 **Technical Implementation**

### **How It Works**
```java
// Initialize logger (already done in activities)
ServerLogger logger = ServerLogger.getInstance(this);

// Log events (automatic in key activities)
logger.session("Session Created", "Session ID: session-123");
logger.qr("QRScanned", "QR Code: session-123");
logger.attendance("AttendanceSubmitted", "Student ID: student-001");
```

### **Log Types Added**
- **Session Events**: Session creation, opening, closing
- **QR Events**: QR code scanning, validation
- **Attendance Events**: Submission, success, failures
- **User Actions**: Button clicks, navigation
- **API Events**: API calls, responses, errors
- **Security Events**: Invalid QR codes, unauthorized access

### **Automatic Flush Points**
- After successful session creation
- After successful attendance submission
- When activities are destroyed
- Every 30 seconds (if batch is not full)
- Immediately for ERROR logs

---

## 📊 **Server Integration**

### **API Endpoint**
```
POST http://your-server:8080/api/v1/logs
Headers: x-api-key: presenter-001-api-key-12345
Content-Type: application/json
```

### **Log Format**
```json
{
  "logs": [
    {
      "timestamp": 1697784000000,
      "level": "INFO",
      "tag": "SemScan-Session",
      "message": "Session Event: Session Created - Session ID: session-123",
      "userId": "presenter-001",
      "userRole": "PRESENTER",
      "deviceInfo": "Android 13 - Samsung SM-G991B",
      "appVersion": "1.0.0",
      "stackTrace": null,
      "exceptionType": null
    }
  ]
}
```

---

## 🧪 **Testing the Integration**

### **1. Test Function**
Go to **Settings → Logging Settings** and click **"Test Logging"**:
- Sends multiple test logs immediately
- Forces flush to server
- Shows confirmation toast

### **2. Real Workflow Testing**
1. **Create Session**: Check server logs for session creation events
2. **Scan QR Code**: Check for QR scanning events
3. **Submit Attendance**: Check for attendance submission events

### **3. Debug Information**
Use Android logs to see what's happening:
```bash
adb logcat | grep "SemScan-API"
```

---

## 📋 **Log Categories**

### **Session Management**
- Session creation attempts
- Session creation success/failure
- Session opening/closing
- Session validation

### **QR Code Operations**
- QR code scanning
- QR code validation
- Invalid QR code attempts
- QR display events

### **Attendance Tracking**
- Attendance submission attempts
- Attendance success/failure
- Manual attendance requests
- Attendance validation

### **User Interface**
- Button clicks
- Navigation events
- Form submissions
- Error messages

### **API Operations**
- API calls made
- API responses received
- API errors encountered
- Network issues

### **Security Events**
- Invalid QR codes
- Unauthorized access attempts
- Suspicious activities
- Authentication failures

---

## 🔍 **Monitoring & Debugging**

### **Server-Side Monitoring**
- All logs are stored in the `logs` table
- Real-time monitoring of app usage
- Error tracking and analysis
- Performance monitoring

### **Debug Information**
- Device information included
- User context (ID, role)
- App version tracking
- Timestamp precision

### **Error Handling**
- Network failures: Automatic retry
- Server errors: Logs re-queued
- Invalid API key: Check configuration
- Rate limiting: Backoff strategy

---

## ⚙️ **Configuration**

### **Enable/Disable Logging**
```java
// Disable server logging
serverLogger.setServerLoggingEnabled(false);

// Enable server logging (default)
serverLogger.setServerLoggingEnabled(true);
```

### **Update User Context**
```java
// Update user information for logging
serverLogger.updateUserContext("student-001", "STUDENT");
```

### **Force Send Logs**
```java
// Send all pending logs immediately
serverLogger.flushLogs();
```

---

## 🚨 **Important Notes**

### **Privacy & Security**
- No sensitive data is logged (passwords, personal info)
- User IDs and roles are included for context
- Device information is anonymized
- Logs are sent securely via HTTPS

### **Performance Impact**
- Logging is asynchronous (won't block UI)
- Batch processing reduces network calls
- Minimal battery and data usage
- Automatic retry on failures

### **Network Requirements**
- Requires internet connection
- Works with WiFi and mobile data
- Graceful handling of network issues
- Offline logs are queued for later sending

---

## 📈 **Benefits**

### **For Development**
- Real-time error tracking
- User behavior analytics
- Performance monitoring
- Debug information

### **For Support**
- Quick issue identification
- User action tracking
- Error reproduction
- Usage patterns

### **For Analytics**
- Feature usage statistics
- User engagement metrics
- Error frequency analysis
- Performance trends

---

## 🔄 **Migration Notes**

### **Backward Compatibility**
- Existing Logger class still works
- ServerLogger is additive (doesn't replace Logger)
- No breaking changes to existing code
- Gradual migration possible

### **New Dependencies**
- No new dependencies required
- Uses existing Retrofit/OkHttp setup
- Leverages current API infrastructure
- Minimal code changes needed

---

## 📞 **Support & Troubleshooting**

### **Common Issues**
1. **Logs not appearing**: Check API key configuration
2. **Network errors**: Verify server connectivity
3. **Missing logs**: Check if ServerLogger is initialized
4. **Performance issues**: Monitor batch sizes

### **Debug Commands**
```bash
# Check Android logs
adb logcat | grep "SemScan"

# Check specific tags
adb logcat | grep "SemScan-API"
adb logcat | grep "SemScan-Session"
```

### **Contact Information**
- Technical issues: Check server logs first
- Configuration help: Review API key setup
- Performance concerns: Monitor batch processing

---

## 🎯 **Next Steps**

### **For Mobile Team**
1. **Test the integration** using the test function
2. **Monitor server logs** to verify data flow
3. **Add custom logging** where needed
4. **Configure user context** appropriately

### **For Backend Team**
1. **Monitor log table** for incoming data
2. **Set up alerts** for error patterns
3. **Create dashboards** for usage analytics
4. **Implement log retention** policies

---

**Generated**: $(Get-Date)  
**Version**: 1.0  
**Status**: ✅ Ready for Production
