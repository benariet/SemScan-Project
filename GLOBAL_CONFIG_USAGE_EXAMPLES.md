# Global Configuration Usage Examples

## 🎯 **How to Use the GlobalConfig Class**

The `GlobalConfig` class centralizes all your API configuration. Here's how to use it in your controllers and services:

### 1. **Inject GlobalConfig into Your Classes**

```java
@RestController
@RequestMapping("/api/v1/seminars")
public class SeminarController {
    
    @Autowired
    private GlobalConfig globalConfig;
    
    // Your controller methods...
}
```

### 2. **Get API URLs Dynamically**

```java
@GetMapping("/info")
public ResponseEntity<Map<String, String>> getApiInfo() {
    Map<String, String> info = new HashMap<>();
    info.put("serverUrl", globalConfig.getServerUrl());
    info.put("apiBaseUrl", globalConfig.getApiBaseUrl());
    info.put("seminarsEndpoint", globalConfig.getSeminarsEndpoint());
    info.put("environment", globalConfig.getEnvironment());
    
    return ResponseEntity.ok(info);
}
```

### 3. **Use Configuration in Services**

```java
@Service
public class ManualAttendanceService {
    
    @Autowired
    private GlobalConfig globalConfig;
    
    public boolean isWithinTimeWindow(LocalDateTime requestTime, LocalDateTime sessionStartTime) {
        int windowBefore = globalConfig.getManualAttendanceWindowBeforeMinutes();
        int windowAfter = globalConfig.getManualAttendanceWindowAfterMinutes();
        
        LocalDateTime windowStart = sessionStartTime.minusMinutes(windowBefore);
        LocalDateTime windowEnd = sessionStartTime.plusMinutes(windowAfter);
        
        return requestTime.isAfter(windowStart) && requestTime.isBefore(windowEnd);
    }
}
```

### 4. **Get Configuration Summary**

```java
@GetMapping("/config")
public ResponseEntity<String> getConfiguration() {
    return ResponseEntity.ok(globalConfig.getConfigurationSummary());
}
```

### 5. **Environment-Specific Logic**

```java
@PostMapping("/debug")
public ResponseEntity<String> debugEndpoint() {
    if (globalConfig.isDevelopmentMode()) {
        return ResponseEntity.ok("Debug info available in development");
    } else {
        return ResponseEntity.status(403).body("Debug not available in production");
    }
}
```

## 🔧 **Configuration Properties**

### **Server Configuration**
- `server.port` - Server port (default: 8080)
- `server.servlet.context-path` - Context path (default: /)

### **API Configuration**
- `app.api.version` - API version (default: v1)
- `app.api.base-path` - API base path (default: /api)

### **Security Configuration**
- `app.security.api-key-header` - API key header name (default: x-api-key)
- `app.security.cors.allowed-origins` - CORS allowed origins (default: *)

### **Manual Attendance Configuration**
- `app.attendance.manual.window-before-minutes` - Time window before session (default: 10)
- `app.attendance.manual.window-after-minutes` - Time window after session (default: 15)
- `app.attendance.manual.auto-approve-cap-percentage` - Auto-approve percentage cap (default: 5)
- `app.attendance.manual.auto-approve-min-cap` - Auto-approve minimum cap (default: 5)

### **Export Configuration**
- `app.export.max-file-size-mb` - Maximum export file size (default: 50)
- `app.export.allowed-formats` - Allowed export formats (default: csv,xlsx)

## 🚀 **Benefits**

✅ **Centralized Configuration** - All settings in one place
✅ **Environment-Specific** - Easy to override for different environments
✅ **Type-Safe** - No more hardcoded strings
✅ **Easy to Change** - Update properties file, no code changes needed
✅ **Self-Documenting** - Clear property names and descriptions
✅ **Validation** - Spring Boot validates configuration on startup

## 📝 **Quick Start**

1. **Use the GlobalConfig class** in your controllers and services
2. **Override properties** in `application-global.properties` as needed
3. **Create environment-specific** property files (e.g., `application-prod.properties`)
4. **Access configuration** through the injected `GlobalConfig` bean

## 🔄 **Environment Overrides**

Create different property files for different environments:

**application-dev.properties:**
```properties
app.security.cors.allowed-origins=http://localhost:3000,http://127.0.0.1:3000
app.logging.enable-request-logging=true
```

**application-prod.properties:**
```properties
app.security.cors.allowed-origins=https://yourdomain.com
app.logging.enable-request-logging=false
app.export.max-file-size-mb=100
```

This gives you a professional, maintainable configuration system! 🎉
