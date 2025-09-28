# MVP Database Schema Changes

## Overview
This document outlines the critical database schema changes required for the SemScan MVP implementation.

## 🚨 **BREAKING CHANGES**

### **1. Timestamp Handling (CRITICAL)**

#### **Database Schema (KEPT AS TIMESTAMP):**
```sql
-- Sessions table
start_time TIMESTAMP NOT NULL,
end_time TIMESTAMP NULL,

-- Attendance table  
attendance_time TIMESTAMP NOT NULL,
```

#### **API Integration:**
```json
// Mobile app sends Unix milliseconds
{
  "timestampMs": 1726646700000
}
```

#### **Server Processing:**
- **Reason:** TIMESTAMP fields are better for logs and debugging
- **Format:** Mobile sends Unix milliseconds, server converts to TIMESTAMP
- **Storage:** Database stores as `TIMESTAMP` for better querying
- **Conversion:** `FROM_UNIXTIME(timestampMs/1000)` in SQL

### **2. Removed Tables**

#### **`absence_requests` Table - COMPLETELY REMOVED**
```sql
-- This entire table is deleted for MVP
DROP TABLE IF EXISTS absence_requests;
```

**Reason:** MVP focuses only on attendance tracking, not absence management.

### **3. Simplified Tables**

#### **`users` Table Changes:**
```sql
-- REMOVED: 'ADMIN' role
-- OLD: role ENUM('STUDENT', 'TEACHER', 'ADMIN')
-- NEW: role ENUM('STUDENT', 'TEACHER')
```

#### **`sessions` Table Changes:**
```sql
-- REMOVED: qr_code_data column
-- OLD: qr_code_data TEXT,
-- NEW: (column removed - QR generated dynamically)
```

#### **`attendance` Table Changes:**
```sql
-- REMOVED: location tracking columns
-- OLD: latitude DECIMAL(10,8), longitude DECIMAL(11,8), device_info TEXT
-- NEW: (columns removed - simplified for MVP)
```

### **4. New Tables**

#### **`teacher_api_keys` Table - NEW**
```sql
CREATE TABLE teacher_api_keys (
    api_key_id VARCHAR(36) PRIMARY KEY,
    teacher_id VARCHAR(36) NOT NULL,
    api_key VARCHAR(255) UNIQUE NOT NULL,
    created_at_ms BIGINT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (teacher_id) REFERENCES users(user_id) ON DELETE CASCADE
);
```

**Purpose:** Simple API authentication for teacher endpoints.

## 📊 **Data Migration Strategy**

### **For Existing Data:**
```sql
-- No migration needed - keeping TIMESTAMP fields
-- Existing data remains as TIMESTAMP
```

### **For New Data:**
- Mobile app sends Unix milliseconds
- Server converts to TIMESTAMP using `FROM_UNIXTIME(timestampMs/1000)`
- Store as `TIMESTAMP` in database for better logging

## 🔧 **Implementation Notes**

### **JavaScript Integration:**
```javascript
// Mobile app sends:
{
  "timestampMs": Date.now()  // Unix milliseconds
}

// Server converts to TIMESTAMP for database storage
```

### **Database Queries:**
```sql
-- Find sessions starting after a specific time
SELECT * FROM sessions 
WHERE start_time > FROM_UNIXTIME(1640995200000/1000);

-- Find attendance within time range
SELECT * FROM attendance 
WHERE attendance_time BETWEEN FROM_UNIXTIME(1640995200000/1000) AND FROM_UNIXTIME(1640998800000/1000);
```

### **Backward Compatibility:**
- **FULL** - No breaking changes to database schema
- Existing TIMESTAMP data remains unchanged
- Mobile app sends Unix milliseconds, server handles conversion

## ⚠️ **Critical Implementation Points**

1. **Keep TIMESTAMP columns** - Better for logs and debugging
2. **No absence_requests table** - Don't implement absence functionality
3. **No ADMIN role** - Only STUDENT and TEACHER roles
4. **No QR storage** - Generate QR codes dynamically
5. **No location tracking** - Simplified attendance only

## 📋 **Migration Checklist**

- [ ] Keep database schema with TIMESTAMP fields
- [ ] Remove `absence_requests` table
- [ ] Remove `ADMIN` role from users table
- [ ] Remove `qr_code_data` from sessions table
- [ ] Add `teacher_api_keys` table
- [ ] No migration needed - keep existing TIMESTAMP data
- [ ] Update all API endpoints to convert Unix milliseconds to TIMESTAMP
- [ ] Test mobile app integration with new format

---
*This document should be reviewed by the entire backend team before implementation.*
