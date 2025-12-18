# Supervisor Linking Feature

## Overview

This feature links supervisors to users (presenters/students) at first login. A supervisor can have multiple presenters, and supervisor information is automatically used for registrations and waiting list entries.

## Changes Made

### 1. Database Schema

#### New Table: `supervisors`
- `supervisor_id` (PK)
- `name` (required)
- `email` (required, unique)
- `created_at`, `updated_at`

#### Updated Table: `users`
- Added `supervisor_id` (FK to `supervisors`, nullable)
- Foreign key constraint: `fk_users_supervisor` with `ON DELETE SET NULL`

### 2. New Entities and Repositories

- **`Supervisor` entity**: Represents a supervisor
- **`SupervisorRepository`**: JPA repository for supervisor operations

### 3. Updated User Entity

- Added `@ManyToOne` relationship to `Supervisor`
- Getter/setter for `supervisor` field

### 4. Authentication Flow Updates

#### Login Endpoint (`POST /api/v1/auth/login`)
- Detects first-time users (when `supervisor` is `null`)
- Returns `isFirstTime: true` in `LoginResponse` for users without supervisor

#### Account Setup Endpoint (`POST /api/v1/auth/setup/{username}`)
- Links supervisor to user
- Creates supervisor if doesn't exist (by email)
- Reuses existing supervisor if email matches
- Returns success with supervisor details

### 5. Service Updates

#### `WaitingListService.addToWaitingList()`
- **Priority 1**: Uses supervisor from `User.supervisor` (if linked)
- **Priority 2**: Falls back to request parameters (backward compatibility)
- **Error**: Throws exception if no supervisor available

#### `PresenterHomeService.registerForSlot()`
- **Priority 1**: Uses supervisor from `User.supervisor` (if linked)
- **Priority 2**: Falls back to request parameters (backward compatibility)
- **Error**: Returns error response if no supervisor available

#### Email Sending Logic
- Updated to use supervisor from User entity first
- Falls back to registration, then request (backward compatibility)

## Migration

### For Existing Databases

Run the migration script:
```sql
mysql -u username -p semscan_db < migration-supervisor-linking.sql
```

This will:
1. Create `supervisors` table
2. Add `supervisor_id` column to `users` table
3. Add foreign key constraint
4. Create indexes

### For New Databases

The `database-schema-ddl.sql` file has been updated with the new structure.

## API Usage

### 1. First Login (User without Supervisor)

**Request:**
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "student123",
  "password": "password"
}
```

**Response:**
```json
{
  "ok": true,
  "message": "Login successful",
  "bguUsername": "student123",
  "email": "student123@bgu.ac.il",
  "firstTime": true,
  "presenter": true,
  "participant": true
}
```

Note: `firstTime: true` indicates supervisor is not linked.

### 2. Complete Account Setup

**Request:**
```http
POST /api/v1/auth/setup/student123
Content-Type: application/json

{
  "supervisorName": "Dr. John Smith",
  "supervisorEmail": "john.smith@bgu.ac.il"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Account setup completed successfully",
  "supervisorName": "Dr. John Smith",
  "supervisorEmail": "john.smith@bgu.ac.il"
}
```

### 3. Subsequent Logins (User with Supervisor)

**Request:**
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "student123",
  "password": "password"
}
```

**Response:**
```json
{
  "ok": true,
  "message": "Login successful",
  "bguUsername": "student123",
  "email": "student123@bgu.ac.il",
  "firstTime": false,
  "presenter": true,
  "participant": true
}
```

Note: `firstTime: false` indicates supervisor is linked.

### 4. Register for Slot (After Setup)

When registering for a slot, supervisor information is automatically taken from the User entity:

**Request:**
```http
POST /api/v1/presenters/student123/slots/123/register
Content-Type: application/json

{
  "topic": "My Research Topic",
  "degree": "MSc"
}
```

**Note:** `supervisorName` and `supervisorEmail` are **optional** in the request. If the user has a supervisor linked, it will be used automatically. If not, the request parameters will be used (backward compatibility).

### 5. Add to Waiting List (After Setup)

Similar to registration, supervisor information is automatically taken from the User entity:

**Request:**
```http
POST /api/v1/slots/123/waiting-list
Content-Type: application/json

{
  "username": "student123",
  "topic": "My Research Topic"
}
```

**Note:** `supervisorName` and `supervisorEmail` are **optional** in the request.

## Backward Compatibility

The system maintains backward compatibility:

1. **Users without supervisor linked**: Can still provide supervisor info in registration/waiting list requests
2. **Warning logs**: System logs warnings when using request parameters instead of User entity
3. **Migration path**: Existing users can complete account setup at any time

## Business Rules

1. **One supervisor per user**: Each user can have only one supervisor linked
2. **Multiple users per supervisor**: A supervisor can have multiple presenters
3. **Supervisor reuse**: If a supervisor with the same email exists, it's reused (no duplicates)
4. **Required for registration**: Supervisor information is required for slot registration and waiting list
5. **First-time detection**: Users without supervisor are flagged as `firstTime: true` in login response

## Error Handling

### No Supervisor Available

If a user tries to register or join waiting list without supervisor:

**Error Response:**
```json
{
  "ok": false,
  "message": "Supervisor information is required. User student123 has no supervisor linked and none provided in request. Please complete account setup via /api/v1/auth/setup/student123",
  "code": "NO_SUPERVISOR"
}
```

## Database Queries

### Find all presenters for a supervisor

```sql
SELECT u.bgu_username, u.email, u.first_name, u.last_name
FROM users u
WHERE u.supervisor_id = (
    SELECT supervisor_id FROM supervisors WHERE email = 'john.smith@bgu.ac.il'
);
```

### Find supervisor for a user

```sql
SELECT s.name, s.email
FROM supervisors s
JOIN users u ON u.supervisor_id = s.supervisor_id
WHERE u.bgu_username = 'student123';
```

### Count presenters per supervisor

```sql
SELECT s.name, s.email, COUNT(u.id) as presenter_count
FROM supervisors s
LEFT JOIN users u ON u.supervisor_id = s.supervisor_id
GROUP BY s.supervisor_id, s.name, s.email
ORDER BY presenter_count DESC;
```

## Testing

1. **First login**: Verify `firstTime: true` for new users
2. **Account setup**: Verify supervisor is linked correctly
3. **Subsequent login**: Verify `firstTime: false` after setup
4. **Registration**: Verify supervisor is used from User entity
5. **Waiting list**: Verify supervisor is used from User entity
6. **Multiple users per supervisor**: Verify same supervisor can be linked to multiple users
7. **Backward compatibility**: Verify request parameters still work for users without supervisor

## Files Changed

- `src/main/java/edu/bgu/semscanapi/entity/Supervisor.java` (new)
- `src/main/java/edu/bgu/semscanapi/entity/User.java` (updated)
- `src/main/java/edu/bgu/semscanapi/repository/SupervisorRepository.java` (new)
- `src/main/java/edu/bgu/semscanapi/dto/AccountSetupRequest.java` (new)
- `src/main/java/edu/bgu/semscanapi/controller/AuthController.java` (updated)
- `src/main/java/edu/bgu/semscanapi/service/WaitingListService.java` (updated)
- `src/main/java/edu/bgu/semscanapi/service/PresenterHomeService.java` (updated)
- `database-schema-ddl.sql` (updated)
- `migration-supervisor-linking.sql` (new)
