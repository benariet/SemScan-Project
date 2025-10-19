# Android App Polling Issue - Solution Guide

## Problem Identified
Your Android app is making repeated API calls to `/api/v1/attendance?sessionId=session-e2d0ed7c` every 5 seconds, causing:
- Excessive database queries
- Log spam in the backend
- Unnecessary server load
- Poor user experience

## Root Cause
The Android app likely has one of these issues:
1. **Auto-refresh/polling mechanism** that's too aggressive
2. **Timer-based updates** that don't stop when the app is idle
3. **Background service** that continues polling even when not needed
4. **UI refresh loop** that triggers API calls continuously

## Immediate Backend Fixes Applied

### 1. Reduced Logging Verbosity
- Changed Hibernate SQL logging from `DEBUG` to `WARN`
- Reduced Spring Web logging from `DEBUG` to `INFO`
- This will stop the SQL query spam in logs

### 2. Backend Logging Configuration Updated
```properties
# Before (causing log spam)
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
logging.level.org.springframework.web.filter.CommonsRequestLoggingFilter=DEBUG
logging.level.org.springframework.web.servlet.DispatcherServlet=DEBUG

# After (reduced verbosity)
logging.level.org.hibernate.SQL=WARN
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=WARN
logging.level.org.springframework.web.filter.CommonsRequestLoggingFilter=INFO
logging.level.org.springframework.web.servlet.DispatcherServlet=INFO
```

## Android App Fixes Needed

### 1. Check for Auto-Refresh/Polling Code
Look for these patterns in your Android code:

```java
// BAD - Continuous polling
Timer timer = new Timer();
timer.scheduleAtFixedRate(new TimerTask() {
    @Override
    public void run() {
        // This runs every 5 seconds regardless of app state
        fetchAttendanceData();
    }
}, 0, 5000); // 5 seconds

// BAD - Handler with postDelayed loop
Handler handler = new Handler();
Runnable runnable = new Runnable() {
    @Override
    public void run() {
        fetchAttendanceData();
        handler.postDelayed(this, 5000); // Infinite loop
    }
};
handler.post(runnable);
```

### 2. Implement Smart Polling
```java
// GOOD - Smart polling with conditions
private void startSmartPolling() {
    if (isAppInForeground() && isUserActive() && hasActiveSession()) {
        fetchAttendanceData();
        scheduleNextPoll();
    }
}

private void scheduleNextPoll() {
    // Only poll if conditions are met
    if (shouldContinuePolling()) {
        handler.postDelayed(this::startSmartPolling, 30000); // 30 seconds instead of 5
    }
}

private boolean shouldContinuePolling() {
    return isAppInForeground() && 
           isUserActive() && 
           hasActiveSession() && 
           !isDataUpToDate();
}
```

### 3. Stop Polling When Appropriate
```java
@Override
protected void onPause() {
    super.onPause();
    stopPolling(); // Stop when app goes to background
}

@Override
protected void onDestroy() {
    super.onDestroy();
    stopPolling(); // Stop when activity is destroyed
}

private void stopPolling() {
    if (handler != null && runnable != null) {
        handler.removeCallbacks(runnable);
    }
    if (timer != null) {
        timer.cancel();
        timer = null;
    }
}
```

### 4. Use WebSocket or Server-Sent Events (Advanced)
For real-time updates without polling:
```java
// Implement WebSocket connection for real-time updates
// This eliminates the need for polling entirely
```

## Recommended Polling Strategy

### Current (Problematic)
- **Frequency**: Every 5 seconds
- **Conditions**: Always active
- **Result**: 720 requests per hour per session

### Recommended
- **Frequency**: Every 30-60 seconds
- **Conditions**: Only when app is active and user is engaged
- **Result**: 60-120 requests per hour per session (6-12x reduction)

### Implementation Example
```java
public class AttendanceManager {
    private static final long POLLING_INTERVAL = 30000; // 30 seconds
    private Handler handler;
    private Runnable pollingRunnable;
    private boolean isPolling = false;
    
    public void startPolling(String sessionId) {
        if (isPolling) return;
        
        isPolling = true;
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (shouldPoll()) {
                    fetchAttendanceData(sessionId);
                }
                if (isPolling) {
                    handler.postDelayed(this, POLLING_INTERVAL);
                }
            }
        };
        handler.post(pollingRunnable);
    }
    
    public void stopPolling() {
        isPolling = false;
        if (handler != null && pollingRunnable != null) {
            handler.removeCallbacks(pollingRunnable);
        }
    }
    
    private boolean shouldPoll() {
        return isAppInForeground() && 
               isUserActive() && 
               hasActiveSession();
    }
}
```

## Testing the Fix

### 1. Backend Testing
After restarting the backend, you should see:
- No more SQL query spam in logs
- Cleaner log output
- Reduced server load

### 2. Android Testing
After implementing the fixes:
- Monitor network requests in Android Studio
- Verify polling stops when app goes to background
- Check that polling frequency is reasonable (30+ seconds)

## Additional Recommendations

### 1. Implement Caching
```java
// Cache attendance data locally
private void cacheAttendanceData(List<Attendance> data) {
    // Store in SharedPreferences or Room database
    // Only fetch from server if cache is stale
}
```

### 2. Use Pull-to-Refresh
```java
// Let users manually refresh instead of auto-polling
SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipe_refresh);
swipeRefresh.setOnRefreshListener(() -> {
    fetchAttendanceData();
    swipeRefresh.setRefreshing(false);
});
```

### 3. Implement Smart Notifications
```java
// Only show notifications for important changes
// Don't poll just to check for updates
```

## Files to Check in Android Project

1. **MainActivity.java** - Look for timers or handlers
2. **AttendanceActivity.java** - Check for auto-refresh logic
3. **ApiService.java** - Verify request frequency
4. **Background services** - Check for continuous polling
5. **Fragment lifecycle** - Ensure polling stops appropriately

## Expected Results After Fix

- **Log spam eliminated**: No more repeated SQL queries in backend logs
- **Reduced server load**: 6-12x fewer API requests
- **Better battery life**: Less network activity on Android
- **Improved user experience**: Smoother app performance
- **Cleaner logs**: Easier debugging and monitoring

## Next Steps

1. **Restart the backend** to apply logging changes
2. **Review Android code** for polling mechanisms
3. **Implement smart polling** with proper lifecycle management
4. **Test the changes** to ensure polling behavior is reasonable
5. **Monitor logs** to confirm the issue is resolved

The backend logging changes will take effect immediately after restart. The Android app changes will require code modifications and a new build.
