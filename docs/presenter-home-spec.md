## Presenter Home Screen Plan

### Overview
The presenter home is the single entry point for presenters after login. It exposes:
- **Registration Slots** list for self enrollment.
- **My Slot** summary tile for the presenter’s booked seminar.
- **Open Attendance** tile that drives QR activation.
- A **Refresh slots** action (button + pull-to-refresh later).

### Screen Structure
1. **Header**: presenter name + cycle context, `Refresh` button.
2. **Registration Slots Card List**:
   - Scrollable list of cards ordered by date/time.
   - Each card renders: date, time range, state color, capacity chips, registered presenters, room/building, `Register now` CTA (when eligible).
3. **My Slot Tile**:
   - Visible only when the presenter has a future registration.
   - Shows date/time, location, co-presenters/topics.
4. **Open Attendance Tile**:
   - CTA to confirmation dialog; on confirm, navigates to QR display screen.

### Home Endpoint Contract (Draft)
Response envelope: `{ presenter, mySlot, slotCatalog[], attendance }`

| Field | Type | Notes |
| --- | --- | --- |
| `presenter` | object | `{ id, name, degree, alreadyRegistered, currentCycleId }` |
| `mySlot` | object\|null | `null` if none. When present: `{ slotId, date, timeRange, room, building, coPresenters[] }` |
| `slotCatalog` | array | Items ordered by date/time |
| `attendance` | object | `{ canOpen, openQrUrl? }` QR URL fetched lazily |

`slotCatalog` item shape:
```
{
  "slotId": 123,
  "date": "2025-11-12",
  "timeRange": "13:00–15:00",
  "room": "210",
  "building": "Building 1",
  "capacity": {
    "mscTotal": 2,
    "mscUsed": 1,
    "phdTotal": 1,
    "phdUsed": 0
  },
  "state": "YELLOW",          // enum: GREEN, YELLOW, RED
  "phdLocked": false,
  "canRegister": true,
  "disableReason": null        // string when canRegister=false (e.g., "Slot full", "PhD-only")
}
```

### State & Rules Mapping
- **Color state** computed server-side:
  - `GREEN`: all capacities open.
  - `YELLOW`: partially filled.
  - `RED`: full.
- `canRegister` respects:
  - Slot capacity.
  - Degree restrictions (PhD lockout).
  - Once-per-cycle rule.
  - Existing registration.
- No waitlist exposed for now.
- Supervisor email collection stays in registration confirmation step.

### Interactions
1. **Refresh** button triggers home endpoint reload.
2. **Register Now** flow:
   - Tap CTA → modal collects supervisor email.
   - Submit → POST `/presenter/slots/{slotId}/register`.
   - On success → toast + refresh home payload.
3. **My Slot Tile**:
   - Displays current registration; future iteration may add change/unregister.
4. **Open Attendance Tile**:
   - Enabled when backend flags `canOpen=true`.
   - Tap → confirmation dialog → on confirm fetch QR payload and navigate.

### Implementation Sequencing
1. Backend: implement `GET /presenter/home` returning contract above.
2. Mobile: build home layout with mocked data + refresh action.
3. Integrate API, populate cards, enforce state flags.
4. Implement registration flow, tie into backend POST + refresh.
5. Populate `My Slot` tile from response.
6. Connect Open Attendance CTA to QR screen (stub).
7. Plan websocket/polling enhancement later for real-time updates.

### Outstanding Questions
- Do we need cycle metadata (e.g., start/end dates) on the home response?
- Should we surface supervisor email status (submitted/pending) alongside My Slot?
- What is the timeout window after “Open Attendance” to keep QR active?


