# SemScan MVP Database Schema Changes

## Overview
Updated the database schema to match the lean MVP requirements by removing absence request functionality and simplifying the structure.

## Key Changes Made

### ✅ **Removed Tables:**
- `absence_requests` - No longer needed for lean MVP
- `audit_log` - Simplified for POC
- `system_settings` (optional features)

### ✅ **Simplified Tables:**
- **users**: Removed `ADMIN` role, only `STUDENT` and `TEACHER`
- **attendance**: 
  - Changed `attendance_time` to `timestamp_ms` (Unix timestamp in milliseconds)
  - Removed location tracking fields (`latitude`, `longitude`, `device_info`)
  - Removed `PROXY` method, only `QR_SCAN` and `MANUAL`
- **sessions**:
  - Changed `start_time`/`end_time` to `start_time_ms`/`end_time_ms` (Unix timestamps in milliseconds)
  - Removed `qr_code_data` (QR codes generated dynamically)

### ✅ **Added Tables:**
- `teacher_api_keys` - For API authentication (essential for MVP)

### ✅ **Updated Data Types:**
- All timestamps now use Unix timestamps in milliseconds for better mobile app compatibility
- Simplified ENUM values to uppercase for consistency

## Files Updated

### 1. **mysql-setup/database-schema-mvp.sql** (NEW)
- Complete lean MVP schema
- Only essential tables and fields
- Simplified sample data
- Optimized for POC usage

### 2. **mysql-setup/database-schema.sql** (UPDATED)
- Removed absence_requests table
- Updated timestamp fields to standard TIMESTAMP
- Added teacher_api_keys table
- Updated sample data

## MVP Schema Structure

```
users (STUDENT/TEACHER only)
├── courses
│   ├── enrollments
│   └── sessions
│       └── attendance
└── teacher_api_keys
```

## Key Features Supported

### ✅ **Student Flow:**
1. Scan QR code (contains sessionId)
2. Submit attendance with timestamp
3. Get success/error feedback

### ✅ **Teacher Flow:**
1. Start session (creates session with timestamp)
2. Display QR code (generated dynamically)
3. Export attendance data (CSV/Excel)

### ✅ **API Endpoints Supported:**
- `POST /api/v1/sessions` - Create session
- `POST /api/v1/attendance` - Submit attendance
- `GET /api/v1/attendance` - List attendance
- `GET /api/v1/export/xlsx` - Export Excel
- `GET /api/v1/export/csv` - Export CSV

## Migration Notes

### From Old Schema:
```sql
-- Standard MySQL TIMESTAMP (no changes needed)
start_time TIMESTAMP
attendance_time TIMESTAMP

-- Note: Removed complex Unix millisecond approach
-- Using standard MySQL TIMESTAMP for simplicity
```

### Sample Data:
- All sample timestamps using standard MySQL TIMESTAMP format
- Removed absence request sample data
- Added teacher API key for testing

## Usage

### For Development:
Use `database-schema-mvp.sql` - clean, lean schema

### For Production:
Use updated `database-schema.sql` - includes more features if needed later

## Benefits of MVP Schema

1. **Simpler**: Fewer tables, cleaner relationships
2. **Faster**: Optimized for core attendance functionality
3. **Standard**: Using MySQL TIMESTAMP for simplicity and compatibility
4. **API-ready**: Structured for REST API consumption
5. **Export-ready**: Optimized for CSV/Excel export functionality

## Next Steps

1. Update backend API to use new schema
2. Update Android app data models if needed
3. Test with sample data
4. Deploy and verify functionality
