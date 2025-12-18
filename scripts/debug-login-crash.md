# Debug Login Crash - Diagnostic Guide

## Problem
Mobile app crashes when clicking login button. Login page loads fine, but crash occurs when attempting to login.

## Diagnostic Steps

### 1. Check if Request Reaches Server

When you click login, check your **application console/terminal** for these messages:

```
=========================================
FILTER ENTRY - REQUEST RECEIVED
=========================================
Method: POST
URI: /api/v1/auth/login
...
=========================================
```

**If you see this**: Request reached the server - check further logs below.
**If you DON'T see this**: Request never reached the server (network issue, CORS, or client-side crash).

### 2. Check Login Endpoint Entry

Look for:
```
=========================================
LOGIN ENDPOINT CALLED
=========================================
Request is null: false
Username: [username]
Password: ***
Correlation ID: [id]
=========================================
```

**If you see this**: Request reached the controller - check for exceptions below.
**If you DON'T see this**: Request failed before reaching controller (filter exception, validation error).

### 3. Check for Exceptions

Look for any of these messages:
- `EXCEPTION CAUGHT IN FILTER`
- `EXCEPTION IN LOGIN ENDPOINT`
- `GLOBAL EXCEPTION HANDLER TRIGGERED`
- `ERROR CONTROLLER TRIGGERED`

### 4. Test Diagnostic Endpoints

Test if server is reachable:
```bash
# Test ping
curl http://localhost:8080/api/v1/diagnostic/ping

# Test echo (simulates login request structure)
curl -X POST http://localhost:8080/api/v1/diagnostic/echo \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}'

# Test login endpoint structure
curl -X POST http://localhost:8080/api/v1/diagnostic/test-login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}'
```

### 5. Check Database Logs

Query `app_logs` table for recent errors:
```sql
SELECT * FROM app_logs 
WHERE tag IN ('FILTER_EXCEPTION', 'GLOBAL_EXCEPTION', 'ERROR_CONTROLLER', 'EARLY_REQUEST_LOG', 'LOGIN_ERROR')
ORDER BY log_timestamp DESC 
LIMIT 20;
```

### 6. Check Application Logs

Look in your log files for:
- `FILTER ENTRY - REQUEST RECEIVED`
- `LOGIN ENDPOINT CALLED`
- Any exception stack traces

## Common Causes

1. **Client-Side Crash**: If no server logs appear, the crash is likely in the mobile app (JSON parsing, response handling, etc.)
2. **CORS Issue**: Request blocked by browser/app before reaching server
3. **Network Timeout**: Request times out before reaching server
4. **Malformed Request**: Request body format doesn't match expected structure
5. **Response Parsing**: Server responds but mobile app crashes when parsing response

## Next Steps

1. **Check console output** when clicking login - do you see "FILTER ENTRY"?
2. **Check mobile app logs** - Android logcat or iOS console
3. **Test with curl/Postman** - does the login endpoint work from command line?
4. **Check network tab** - does the HTTP request appear in network inspector?
