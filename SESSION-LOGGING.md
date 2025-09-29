# Session-Specific Logging

This document describes the session-specific logging feature implemented in the SemScan API.

## Overview

The session-specific logging system creates separate log files for each session, making it easier to track and analyze individual session activities. Each log file is named with the date and session ID for easy identification.

## Features

### Log File Naming Convention
- **Format**: `session-{SESSION_ID}-{YYYY-MM-DD}.log`
- **Location**: `logs/sessions/`
- **Example**: `session-session-abc12345-2024-09-29.log`

### Log File Structure
- **Size Limit**: 5MB per file
- **Retention**: 7 days of history
- **Total Size Cap**: 100MB for all session logs
- **Rollover**: Daily with size-based rollover

### Log Pattern
Each log entry includes:
- Timestamp
- Thread information
- Log level
- Logger name
- Session ID in brackets
- Message content

Example:
```
2024-09-29 10:30:15.123 [http-nio-8080-exec-1] INFO  e.b.s.session [SESSION:session-abc12345] - SESSION_CREATED - Session: session-abc12345, Seminar: seminar-001, Presenter: presenter-001
```

## Logged Events

### Session Lifecycle Events
1. **Session Creation**
   - Logs when a new session is created
   - Includes session ID, seminar ID, and presenter ID

2. **Status Changes**
   - Logs when session status changes (OPEN → CLOSED)
   - Includes old and new status

3. **Session Closure**
   - Logs when a session is closed
   - Includes duration and attendance count

### Attendance Events
1. **Attendance Recording**
   - Logs each student attendance
   - Includes student ID, method, and timestamp

2. **Attendance Statistics**
   - Logs session statistics
   - Includes total students, attended students, and attendance rate

### Error Events
1. **Session Errors**
   - Logs any errors related to session operations
   - Includes error message and stack trace

## Usage

### Automatic Logging
Session-specific logging is automatically triggered when:
- Creating a new session via `SessionService.createSession()`
- Updating session status via `SessionService.updateSessionStatus()`
- Recording attendance via `AttendanceService.recordAttendance()`

### Manual Logging
You can manually log session events using `SessionLoggerUtil`:

```java
// Log session creation
SessionLoggerUtil.logSessionCreated(sessionId, seminarId, presenterId);

// Log status change
SessionLoggerUtil.logSessionStatusChange(sessionId, "OPEN", "CLOSED");

// Log attendance
SessionLoggerUtil.logAttendance(sessionId, studentId, "QR_SCAN", timestamp);

// Log session closure
SessionLoggerUtil.logSessionClosed(sessionId, durationMinutes, attendanceCount);

// Log statistics
SessionLoggerUtil.logSessionStatistics(sessionId, totalStudents, attendedStudents, attendanceRate);
```

## Configuration

### Logback Configuration
The session-specific logging is configured in `src/main/resources/logback-spring.xml`:

```xml
<!-- Session-specific File Appender -->
<appender name="SESSION_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/sessions/session-${SESSION_ID:-default}-${DATE:-yyyy-MM-dd}.log</file>
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [SESSION:${SESSION_ID:-N/A}] - %msg%n</pattern>
        <charset>UTF-8</charset>
    </encoder>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/sessions/session-${SESSION_ID:-default}-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
        <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
            <maxFileSize>5MB</maxFileSize>
        </timeBasedFileNamingAndTriggeringPolicy>
        <maxHistory>7</maxHistory>
        <totalSizeCap>100MB</totalSizeCap>
    </rollingPolicy>
</appender>

<!-- Session-specific logger -->
<logger name="edu.bgu.semscanapi.session" level="INFO" additivity="false">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="SESSION_FILE"/>
</logger>
```

## Testing

### Test Endpoints
The system includes test endpoints for demonstrating session-specific logging:

- `POST /api/v1/test/session-logging/create-session?sessionId={id}`
- `POST /api/v1/test/session-logging/change-status?sessionId={id}&newStatus={status}`
- `POST /api/v1/test/session-logging/log-attendance?sessionId={id}&studentId={id}`
- `POST /api/v1/test/session-logging/close-session?sessionId={id}&durationMinutes={minutes}&attendanceCount={count}`
- `POST /api/v1/test/session-logging/log-statistics?sessionId={id}&totalStudents={total}&attendedStudents={attended}`

### Example Test
```bash
# Create a test session
curl -X POST "http://localhost:8080/api/v1/test/session-logging/create-session?sessionId=test-session-001"

# Log some attendance
curl -X POST "http://localhost:8080/api/v1/test/session-logging/log-attendance?sessionId=test-session-001&studentId=student-001"

# Close the session
curl -X POST "http://localhost:8080/api/v1/test/session-logging/close-session?sessionId=test-session-001&durationMinutes=90&attendanceCount=15"
```

## File Management

### Directory Structure
```
logs/
├── semscan-api.log                    # Main application log
├── semscan-api-error.log              # Error log
└── sessions/                          # Session-specific logs
    ├── session-abc12345-2024-09-29.log
    ├── session-def67890-2024-09-29.log
    └── session-ghi13579-2024-09-28.log
```

### Cleanup
- Session logs are automatically cleaned up after 7 days
- Total size is capped at 100MB
- Individual files are rolled over when they reach 5MB

## Benefits

1. **Isolation**: Each session has its own log file, making it easy to track specific sessions
2. **Performance**: Reduces log file size and improves search performance
3. **Debugging**: Easier to debug issues related to specific sessions
4. **Analytics**: Enables session-specific analytics and reporting
5. **Compliance**: Better audit trail for individual sessions

## Security Considerations

- Session logs may contain sensitive information (student IDs, timestamps)
- Ensure proper access controls on the logs directory
- Consider encryption for production environments
- Regular cleanup of old log files is important for security

## Monitoring

### Log File Monitoring
Monitor the following:
- Log file creation and growth
- Disk space usage in the logs/sessions directory
- Log rotation and cleanup processes
- Error rates in session logs

### Alerts
Set up alerts for:
- Failed session creation
- High error rates in session logs
- Disk space issues
- Log rotation failures
