# SemScan MVP - Critical Backend Requirements Update

## 🎯 **Essential Server-Side Communication Points**

### **1. 🗄️ Database Schema Changes (CRITICAL)**

#### **TIMESTAMP HANDLING:**
- **Database:** Uses proper `TIMESTAMP` fields for better logging
- **API:** Mobile apps send Unix milliseconds, server converts to TIMESTAMP
- **Benefits:** Better logs, debugging, and database queries

#### **REMOVED TABLES:**
- `absence_requests` (entire table deleted)
- `audit_log` (simplified for MVP)

#### **SIMPLIFIED TABLES:**
- `users`: Only 'STUDENT' and 'PRESENTER' roles (removed 'ADMIN')
- `attendance`: Removed location tracking (latitude, longitude, device_info)
- `sessions`: Removed `qr_code_data` (QR generated dynamically)

#### **NEW TABLE:**
- `presenter_api_keys` (for API authentication)

### **2. 🔌 API Endpoint Changes (CRITICAL)**

#### **REMOVED ENDPOINTS (don't implement these):**
- `POST /api/v1/absence-requests`
- `PATCH /api/v1/absence-requests/{id}`
- `GET /api/v1/absence-requests`
- `POST /api/v1/seminars` (create)
- `PUT /api/v1/seminars/{seminarId}` (update)
- `DELETE /api/v1/seminars/{seminarId}`
- `GET /api/v1/attendance/all`
- `GET /api/v1/attendance/seminar/{seminarId}`

#### **KEEP ONLY THESE ENDPOINTS:**
- `POST /api/v1/sessions` (create session)
- `POST /api/v1/attendance` (submit attendance)
- `GET /api/v1/attendance?sessionId=X` (list attendance)
- `GET /api/v1/seminars` (list seminars only)
- `GET /api/v1/export/xlsx?sessionId=X`
- `GET /api/v1/export/csv?sessionId=X`

### **3. 📱 Mobile App Expectations**

#### **QR PAYLOAD FORMAT:**
```json
{
  "sessionId": "uuid-string"
}
```

#### **ATTENDANCE REQUEST:**
```json
{
  "sessionId": "uuid-string",
  "userId": "uuid-string", 
  "timestampMs": 1640995200000
}
```

#### **SUCCESS RESPONSES:**
- "Checked in for this session"
- "Already checked in"

#### **ERROR RESPONSES:**
- "Invalid session code" (404, 400)
- "This session is not accepting new check-ins" (409)

### **4. 🎯 MVP Scope Clarification**

#### **WHAT THE APP DOES NOW:**
✅ Student: Scan QR → Submit attendance → Get feedback  
✅ Teacher: Start session → Display QR → Export attendance

#### **WHAT THE APP DOESN'T DO (MVP):**
❌ No absence request submission  
❌ No course management (create/edit/delete)  
❌ No detailed attendance analytics  
❌ No approval workflows  
❌ No complex teacher dashboards

#### **EXPORT REQUIREMENTS:**
- Only session-based export (not date range)
- CSV and Excel formats
- Simple attendance list with: student info, timestamp, status

### **5. 🔧 Technical Requirements**

#### **TIMESTAMP HANDLING:**
- Mobile apps send Unix milliseconds (JavaScript: `Date.now()`)
- Server converts to TIMESTAMP for database storage
- Better for logs, debugging, and database queries
- Conversion: `FROM_UNIXTIME(timestampMs/1000)`

#### **AUTHENTICATION:**
- Presenter API keys required for presenter endpoints
- Simple API key validation (no complex auth)

#### **ERROR HANDLING:**
- Return clear error messages matching mobile app expectations
- Use HTTP status codes: 400, 404, 409, 500

## 🚨 **Most Critical Points for Server Team:**

1. **Database uses TIMESTAMP fields** - better for logs and debugging
2. **No absence request functionality** - don't build it
3. **Only 6 API endpoints needed** - much simpler than before
4. **Specific error messages** - mobile app expects exact text
5. **Session-based export only** - no date range exports
6. **Server converts timestamps** - mobile sends Unix ms, server stores as TIMESTAMP

## 📁 **Files to Reference:**
- `database-schema-mvp.sql` - Updated schema with Unix milliseconds
- `MVP-SCHEMA-CHANGES.md` - Detailed schema change documentation
- `API-SPECIFICATION.md` - Complete API endpoint specifications

---
*Last Updated: $(date)*
*Version: MVP 1.0*
