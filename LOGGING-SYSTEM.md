# SemScan API Logging System

This document describes the comprehensive logging system implemented for the SemScan API.

## Overview

The logging system provides:
- **Structured logging** with correlation IDs for request tracing
- **Multiple log levels** (DEBUG, INFO, WARN, ERROR)
- **File rotation** with size and time-based policies
- **Performance monitoring** with request duration tracking
- **Error tracking** with stack traces
- **Business event logging** for attendance and session events

## Configuration Files

### 1. `application.properties`
Contains basic logging configuration:
- Log levels for different packages
- File output settings
- Console formatting

### 2. `logback-spring.xml`
Advanced Logback configuration with:
- Multiple appenders (Console, File, Error File)
- Log rotation policies
- Profile-specific configurations
- Async logging for better performance

## Log Files

The system creates the following log files in the `logs/` directory:

- `semscan-api.log` - Main application log
- `semscan-api-error.log` - Error-only log
- `semscan-api-YYYY-MM-DD.N.log` - Rotated daily logs
- `semscan-api-error-YYYY-MM-DD.N.log` - Rotated error logs

## Logging Components

### 1. LoggerUtil Class
Utility class providing:
- **Correlation ID management** for request tracing
- **Context setting** (userId, sessionId, seminarId)
- **Structured logging methods** for different event types
- **Performance logging** with duration tracking

### 2. RequestLoggingFilter
HTTP filter that:
- **Generates correlation IDs** for each request
- **Logs incoming requests** with headers and body
- **Logs outgoing responses** with status and duration
- **Tracks performance** and warns about slow requests

### 3. LoggingConfig
Configuration class that:
- **Sets up request logging filters**
- **Configures CommonsRequestLoggingFilter**
- **Registers custom filters**

## Usage Examples

### Basic Logging
```java
private static final Logger logger = LoggerUtil.getLogger(YourClass.class);

// Simple logging
logger.info("User logged in successfully");
logger.error("Database connection failed", exception);
```

### Structured Logging
```java
// Set context
LoggerUtil.setUserId("user-123");
LoggerUtil.setSessionId("session-456");

// Log business events
LoggerUtil.logAttendanceEvent(logger, "ATTENDANCE_RECORDED", 
    "student-789", "session-456", "QR_SCAN");

// Log API requests/responses
LoggerUtil.logApiRequest(logger, "POST", "/api/v1/attendance", requestBody);
LoggerUtil.logApiResponse(logger, "POST", "/api/v1/attendance", 200, responseBody);
```

### Performance Logging
```java
long startTime = System.currentTimeMillis();
// ... perform operation ...
long duration = System.currentTimeMillis() - startTime;
LoggerUtil.logPerformance(logger, "DATABASE_QUERY", duration);
```

## Log Levels

- **DEBUG**: Detailed information for debugging
- **INFO**: General information about application flow
- **WARN**: Warning messages for potential issues
- **ERROR**: Error messages with stack traces

## Correlation IDs

Each request gets a unique correlation ID that:
- **Tracks requests** across the entire system
- **Links related log entries** together
- **Helps with debugging** distributed requests
- **Included in response headers** as `X-Correlation-ID`

## Environment-Specific Configuration

### Development
- More verbose logging (DEBUG level)
- Console output enabled
- Detailed request/response logging

### Production
- Reduced logging (INFO level)
- File output only
- Performance-optimized async logging

## Monitoring and Alerts

The logging system supports:
- **Slow request detection** (warnings for requests > 1 second)
- **Error rate monitoring** through error log files
- **Performance metrics** with duration tracking
- **Business event tracking** for attendance and sessions

## Testing the Logging System

Use the example endpoints to test logging:

```bash
# Test basic logging
GET /api/v1/logging-example/test

# Test error logging
POST /api/v1/logging-example/error-test

# Test performance logging
GET /api/v1/logging-example/performance-test
```

## Best Practices

1. **Use appropriate log levels**:
   - DEBUG for detailed debugging info
   - INFO for normal application flow
   - WARN for potential issues
   - ERROR for actual errors

2. **Include context information**:
   - Set userId, sessionId, seminarId when available
   - Use correlation IDs for request tracing

3. **Log business events**:
   - Use structured logging for attendance events
   - Log session lifecycle events
   - Track authentication events

4. **Monitor performance**:
   - Log slow operations
   - Track database query performance
   - Monitor API response times

5. **Handle sensitive data**:
   - Never log passwords or API keys
   - Filter sensitive headers
   - Use utility methods for secure logging

## Troubleshooting

### Common Issues

1. **Log files not created**: Check directory permissions
2. **Too much logging**: Adjust log levels in application.properties
3. **Performance issues**: Enable async logging in logback-spring.xml
4. **Missing correlation IDs**: Ensure RequestLoggingFilter is registered

### Log Analysis

Use tools like:
- **grep** for searching log files
- **tail -f** for real-time monitoring
- **Log aggregation tools** (ELK stack, Splunk) for production

## Integration with Monitoring Tools

The logging system is designed to work with:
- **ELK Stack** (Elasticsearch, Logstash, Kibana)
- **Splunk** for log analysis
- **Prometheus** for metrics collection
- **Grafana** for visualization

Log files are structured to be easily parsed by these tools.
