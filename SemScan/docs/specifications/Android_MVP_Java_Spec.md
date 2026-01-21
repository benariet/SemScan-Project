# QR Attendance – Android MVP (Java) – Product Specification
_Updated on 2025-09-17_

## Overview & Goals
- Single **Android app (Java)** with two roles: **Teacher** and **Student**.
- Core functions: **scan attendance**, **submit absence reason**, **start session & project QR**, **view attendance / approvals**, **export CSV**.
- Backend: calls your **Spring Boot** REST API (no Firebase).

## Target & Assumptions
- **Min SDK**: 24 (Android 7.0), **Target SDK**: 34+.
- Locales: English & Hebrew (RTL support).
- Online required for submit; teacher must be online to create sessions.
- **QR**: static per session (payload contains only `sessionId`).
- **Scan window**: 15 minutes after scheduled start (client check; server also enforces).

## Roles
- **Student**: Scan → confirm presence; submit absence reason.
- **Teacher**: Start session → show QR; view attendance; approve/reject absence requests; export CSV.

## Navigation Map
1. **RolePickerActivity** (first launch) → pick **Teacher** or **Student** (saved in settings).
2. **StudentHomeActivity** → {{ Scan Attendance, I’m Absent — Reason, Settings }}.
3. **TeacherHomeActivity** → {{ Start Session (Show QR), Attendance / Approvals, Export to Excel (CSV), Settings }}.
4. **TeacherStartSessionActivity** → create session → **QR Screen** (large, keeps screen awake) → End Session.
5. **TeacherAttendanceActivity** → tabs: **Present** / **Absence Requests** (Approve/Reject).
6. **SubmitAbsenceActivity** → pick course/session → reason → note → Submit.

## Key Screens
### Student – Home
- **Scan attendance** → camera (QR) → show session info → **Confirm** → success.
- **I’m absent — reason** → course/session → reason (Sick/Family/Exam/Other) → optional note → **Submit**.

### Teacher – Home
- **Start session (Show QR)** → select course → **Start** → big QR showing `{{"sessionId": "<id>"}}`; live present count → **End session**.
- **Attendance / Approvals** → list of present (timestamp) and absence requests with Approve/Reject.
- **Export to Excel (CSV)** → pick course + date range or session → generate CSV → Android share sheet.

## Business Rules (MVP)
- **Unique attendance** = one record per `(sessionId, userId)` (duplicate scans ignored on client; idempotent on server).
- **Scan window** = startTime + 15 minutes; otherwise “Window closed”.
- **Absence requests**: status `submitted` → teacher may set to `approved`/`rejected`.
- **Export**: includes `present` and `approved_absence`; `rejected` excluded from present stats.

## QR Payload (MVP)
```json
{{ "sessionId": "EE-401-1715172000000" }}
```

## CSV Export (on device)
Columns (order fixed):
`date, course_id, session_id, student_id, status, reason, note, timestamp`

## Accessibility & I18N
- Content descriptions for icons; color contrast ≥ WCAG AA.
- RTL mirroring (Hebrew) and LTR (English) with `strings.xml` locales.

## Acceptance Criteria (client)
1. Scan → “Checked in” within ~2s on Wi‑Fi.
2. Duplicate scan ignored with proper message.
3. QR screen shows live count while session open.
4. Closing a session stops further check‑ins.
5. Absence request flow works; CSV export produces correct columns (UTF‑8).
