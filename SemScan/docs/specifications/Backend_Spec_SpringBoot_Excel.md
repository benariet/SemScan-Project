# Backend Spec – Java + Spring Boot + Excel (XLSX)
_Updated on 2025-09-17_

## 1) Overview
- **Spring Boot 3** REST API replacing Firebase.
- DB: **PostgreSQL** (Prod), **H2** (Dev) with **Flyway** migrations.
- **Excel (XLSX)** exports via **Apache POI** (SXSSF). CSV fallback available.
- Android Java app calls this API via Retrofit.

## 2) Tech Stack
- Spring Boot: Web, Validation, Data JPA, Actuator, (optional) Security
- DB: PostgreSQL 15+ (Prod), H2 (Dev), migration with Flyway
- Docs: springdoc-openapi (Swagger UI)
- Excel: Apache POI (poi-ooxml) + SXSSF

## 3) Architecture
- Controllers → Services → Repositories (JPA) → DB
- DTOs for request/response; MapStruct optional
- Security (MVP): Teacher endpoints protected with header `X-API-Key: <SECRET>`
- CORS allowlist for emulator/device origins

## 4) Data Model
users(user_id PK, name, email?, role: teacher|student)
courses(course_id PK, name, lecturer_id → users)
sessions(session_id PK, course_id, start_time BIGINT, end_time BIGINT?, status: open|closed)
attendance(session_id, user_id, timestamp BIGINT, status: present, PK(session_id,user_id))
absence_requests(id PK, user_id, course_id?, session_id?, reason, note?, timestamp BIGINT, status: submitted|approved|rejected)

Indexes: attendance(session_id), sessions(course_id,start_time desc), absence_requests(status)

## 5) QR Payload (unchanged)
```json
{{ "sessionId": "EE-401-1715172000000" }}
```

## 6) Business Rules (server)
- Scan window: reject if now > start_time + 15m → 409 `WINDOW_CLOSED`
- Idempotency: PK prevents duplicates; return 200 with `alreadyPresent=true` if exists
- Session lifecycle: `open` on create; `closed` on close; closed rejects check‑ins
- Absence requests: teacher sets submitted → approved/rejected; exports mark `approved_absence`

## 7) REST API (v1)
Headers: JSON; teacher endpoints require `X-API-Key: <SECRET>`

Sessions (Teacher)
- POST /api/v1/sessions → {{courseId,startTime}} → {{sessionId,status:"open"}}
- PATCH /api/v1/sessions/{{sessionId}}/close → {{sessionId,status:"closed",endTime}}
- GET /api/v1/sessions?courseId=...&from=...&to=... → list

Attendance
- POST /api/v1/attendance → {{sessionId,userId,timestamp}} → {{status:"present",alreadyPresent:false}}
- GET /api/v1/attendance?sessionId=... (Teacher) → list present

Absence Requests
- POST /api/v1/absence-requests → {{userId,courseId|sessionId,reason,note}} → {{id,status:"submitted"}}
- PATCH /api/v1/absence-requests/{{id}} → {{status:"approved"|"rejected"}}
- GET /api/v1/absence-requests?courseId=...&status=submitted → list

Export
- GET /api/v1/export/xlsx?sessionId=... → application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
  - Filename: attendance_{{sessionId}}.xlsx
  - Columns: date,course_id,session_id,student_id,status,reason,note,timestamp
- GET /api/v1/export/csv?sessionId=... → text/csv; charset=utf-8

## 8) Maven Deps
- spring-boot-starter-web
- spring-boot-starter-validation
- spring-boot-starter-data-jpa
- spring-boot-starter-actuator
- org.springdoc:springdoc-openapi-starter-webmvc-ui
- org.postgresql:postgresql (prod), com.h2database:h2 (dev)
- org.flywaydb:flyway-core
- org.apache.poi:poi-ooxml

## 9) Configuration
application.yml
- datasource (Postgres), JPA props, ISO formats
- app.teacherApiKey, app.attendance.windowMinutes: 15

application-dev.yml (H2)
- jdbc:h2:file:./.localdb/attendance;MODE=PostgreSQL, user sa

## 10) Excel Export Details
- SXSSFWorkbook streaming, bold header, freeze header row, autosize key cols
- Date col ISO yyyy-MM-dd; raw epoch kept in `timestamp`

## 11) Errors (HTTP)
- 400 invalid payload, 401 invalid API key, 404 not found, 409 WINDOW_CLOSED / SESSION_CLOSED / ALREADY_PRESENT

## 12) OpenAPI
- Swagger UI at /swagger-ui.html; JSON at /v3/api-docs

## 13) Dev & Deploy
- Dev: spring.profiles.active=dev → H2; Swagger enabled
- Prod: Docker for app + Postgres; NGINX; TLS via Let's Encrypt; Flyway migrations

## 14) Acceptance (backend)
1. Create session persists with open
2. First attendance OK; duplicate → alreadyPresent=true
3. Late check-in → 409 WINDOW_CLOSED
4. Absence workflow functional
5. /export/xlsx downloads valid XLSX with exact column order
6. Swagger lists all endpoints
