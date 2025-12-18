# SemScan API - User Story Flow Diagram

This diagram shows all user stories from the perspective of different user types (Student, Supervisor) and their interactions with the system.

---

## Student User Stories

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         STUDENT USER STORIES                             │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ STORY 1: Register for Available Slot                                     │
└─────────────────────────────────────────────────────────────────────────┘

[Student Opens Mobile App]
         │
         ▼
[Views Slot Catalog]
         │
         ▼
[Sees Slot A: Available (3 spots)]
         │
         ▼
[Taps "Register" Button]
         │
         ▼
[Fills Form: Topic, Supervisor Name, Supervisor Email]
         │
         ▼
[Taps "Submit"]
         │
         ▼
[API: POST /api/v1/presenters/{username}/slots/{slotId}/register]
         │
         ├─► SUCCESS
         │   │
         │   ├─► [Mobile Shows: "Registration submitted! Waiting for approval"]
         │   │
         │   ├─► [Supervisor Receives Approval Email]
         │   │
         │   └─► [Student's Slot Card Shows: "Pending Approval" status]
         │
         └─► FAILURE
             │
             ├─► Slot Full → [Shows: "Slot is full. Join waiting list?"]
             │
             ├─► Already Registered → [Shows: "You are already registered"]
             │
             └─► Pending Limit Reached → [Shows: "You have reached pending limit"]


┌─────────────────────────────────────────────────────────────────────────┐
│ STORY 2: Join Waiting List for Full Slot                                │
└─────────────────────────────────────────────────────────────────────────┘

[Student Opens Mobile App]
         │
         ▼
[Views Slot Catalog]
         │
         ▼
[Sees Slot B: Full (0 spots available)]
         │
         ▼
[Taps "Join Waiting List" Button]
         │
         ▼
[Fills Form: Topic, Supervisor Name, Supervisor Email]
         │
         ▼
[Taps "Submit"]
         │
         ▼
[API: POST /api/v1/waiting-list/{slotId}]
         │
         ├─► SUCCESS
         │   │
         │   ├─► [Mobile Shows: "You are #2 on the waiting list"]
         │   │
         │   └─► [Slot Card Shows: onWaitingList=true, waitingListCount=2]
         │
         └─► FAILURE
             │
             ├─► Already on Waiting List → [Shows: "You are already on waiting list"]
             │
             └─► Already Registered → [Shows: "You are already registered"]


┌─────────────────────────────────────────────────────────────────────────┐
│ STORY 3: Register for Different Slot While on Waiting List            │
└─────────────────────────────────────────────────────────────────────────┘

[Student on Waiting List for Slot A]
         │
         ▼
[Opens Mobile App]
         │
         ▼
[Sees Slot A: onWaitingList=true]
[Sees Slot B: Available, canRegister=true]
         │
         ▼
[Taps "Register" on Slot B]
         │
         ▼
[Fills Form and Submits]
         │
         ▼
[API: POST /api/v1/presenters/{username}/slots/{slotB}/register]
         │
         ├─► SUCCESS
         │   │
         │   ├─► [Slot B: Shows "Pending Approval"]
         │   │
         │   └─► [Slot A: Still shows onWaitingList=true]
         │
         └─► FAILURE
             └─► [Shows error message]


┌─────────────────────────────────────────────────────────────────────────┐
│ STORY 4: Receive Promotion Email (From Waiting List)                   │
└─────────────────────────────────────────────────────────────────────────┘

[Student on Waiting List]
         │
         ▼
[Another Student Cancels Registration]
         │
         ▼
[System Automatically Promotes Next Person]
         │
         ▼
[Student Receives Email: "Slot Available!"]
         │
         ├─► Email Contains:
         │   ├─► "You've been promoted from the waiting list"
         │   ├─► Slot details (date, time, location, topic)
         │   ├─► Button: "Yes, I Want This Slot"
         │   └─► Button: "No, Decline"
         │
         ▼
[Student Opens Email]
         │
         ├─► [Clicks "Yes, I Want This Slot"]
         │   │
         │   ├─► [Browser Opens: /api/v1/student-confirm/{token}]
         │   │
         │   ├─► [Page Shows: "Promotion Confirmed. Supervisor will receive approval email."]
         │   │
         │   ├─► [Supervisor Receives Approval Email]
         │   │
         │   └─► [Mobile App: Slot shows "Pending Approval" (waiting for supervisor)]
         │
         └─► [Clicks "No, Decline"]
             │
             ├─► [Browser Opens: /api/v1/student-decline/{token}]
             │
             ├─► [Page Shows: "Promotion Declined. Registration cancelled."]
             │
             ├─► [Next Person on Waiting List Automatically Promoted]
             │
             └─► [Mobile App: Slot shows canRegister=true (can register again)]


┌─────────────────────────────────────────────────────────────────────────┐
│ STORY 5: Cancel Registration                                            │
└─────────────────────────────────────────────────────────────────────────┘

[Student with APPROVED Registration]
         │
         ▼
[Opens Mobile App]
         │
         ▼
[Sees Slot Card: approvalStatus="APPROVED"]
         │
         ▼
[Taps "Cancel Registration" Button]
         │
         ▼
[Confirms Cancellation]
         │
         ▼
[API: DELETE /api/v1/presenters/{username}/slots/{slotId}/register]
         │
         ├─► SUCCESS
         │   │
         │   ├─► [Registration Deleted]
         │   │
         │   ├─► [If Slot Had Waiting List → Next Person Promoted]
         │   │
         │   └─► [Mobile App: Slot shows canRegister=true]
         │
         └─► FAILURE
             └─► [Shows error message]


┌─────────────────────────────────────────────────────────────────────────┐
│ STORY 6: View Registration Status                                        │
└─────────────────────────────────────────────────────────────────────────┘

[Student Opens Mobile App]
         │
         ▼
[API: GET /api/v1/presenters/{username}/home]
         │
         ▼
[Receives Slot Cards with Status]
         │
         ├─► Slot A: approvalStatus="PENDING_APPROVAL"
         │   ├─► Shows: "Waiting for supervisor approval"
         │   └─► Button: "Cancel Registration"
         │
         ├─► Slot B: approvalStatus="APPROVED"
         │   ├─► Shows: "Registration approved ✓"
         │   └─► Button: "Cancel Registration"
         │
         ├─► Slot C: onWaitingList=true, waitingListCount=3
         │   ├─► Shows: "You are on waiting list (#2)"
         │   └─► Button: "Leave Waiting List"
         │
         └─► Slot D: canRegister=true, availableCount=2
             ├─► Shows: "Available: 2 spots"
             └─► Button: "Register"


┌─────────────────────────────────────────────────────────────────────────┐
│ STORY 7: Leave Waiting List                                              │
└─────────────────────────────────────────────────────────────────────────┘

[Student on Waiting List]
         │
         ▼
[Opens Mobile App]
         │
         ▼
[Sees Slot Card: onWaitingList=true]
         │
         ▼
[Taps "Leave Waiting List" Button]
         │
         ▼
[Confirms Removal]
         │
         ▼
[API: DELETE /api/v1/waiting-list/{slotId}/{username}]
         │
         ├─► SUCCESS
         │   │
         │   ├─► [Removed from Waiting List]
         │   │
         │   ├─► [Positions of Others Updated]
         │   │
         │   └─► [Mobile App: Slot shows onWaitingList=false]
         │
         └─► FAILURE
             └─► [Shows error message]

```

---

## Supervisor User Stories

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       SUPERVISOR USER STORIES                            │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ STORY 8: Approve Student Registration                                    │
└─────────────────────────────────────────────────────────────────────────┘

[Student Registers for Slot]
         │
         ▼
[Supervisor Receives Email: "Approval Required"]
         │
         ├─► Email Contains:
         │   ├─► Student name and username
         │   ├─► Slot date, time, location
         │   ├─► Topic
         │   ├─► Button: "Approve Registration"
         │   ├─► Button: "Decline Registration"
         │   └─► Expiration date
         │
         ▼
[Supervisor Opens Email]
         │
         ├─► [Clicks "Approve Registration"]
         │   │
         │   ├─► [Browser Opens: /api/v1/approve/{token}]
         │   │
         │   ├─► [Page Shows: "Registration Approved ✓"]
         │   │
         │   ├─► [Registration Status: PENDING → APPROVED]
         │   │
         │   ├─► [Student Receives Notification Email]
         │   │
         │   └─► [Student's Mobile App: Slot shows "Approved"]
         │
         └─► [Clicks "Decline Registration"]
             │
             ├─► [Browser Opens: /api/v1/decline/{token}]
             │
             ├─► [Page Shows: "Registration Declined"]
             │
             ├─► [Registration Status: PENDING → DECLINED]
             │
             └─► [Student's Mobile App: Slot shows canRegister=true]


┌─────────────────────────────────────────────────────────────────────────┐
│ STORY 9: Receive Waiting List Cancellation Email                        │
└─────────────────────────────────────────────────────────────────────────┘

[Student on Waiting List]
         │
         ▼
[Student Leaves Waiting List or Registers for Same Slot]
         │
         ▼
[Supervisor Receives Email: "Waiting List Cancellation"]
         │
         ├─► Email Contains:
         │   ├─► Student name
         │   ├─► "Student has been removed from waiting list"
         │   ├─► Slot details
         │   └─► Reason (if applicable)
         │
         └─► [No Action Required - Informational Only]

```

---

## Complete User Journey Flows

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    COMPLETE USER JOURNEY FLOWS                           │
└─────────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════
FLOW A: Happy Path - Registration → Approval → Confirmed
═══════════════════════════════════════════════════════════════════════════

Step 1: [Student] Opens app → Sees available slot → Taps "Register"
Step 2: [Student] Fills form → Submits
Step 3: [System] Creates PENDING registration → Sends email to supervisor
Step 4: [Supervisor] Receives email → Clicks "Approve"
Step 5: [System] Registration becomes APPROVED → Sends notification to student
Step 6: [Student] Opens app → Sees "Registration approved ✓"

RESULT: ✅ Student registered and approved


═══════════════════════════════════════════════════════════════════════════
FLOW B: Waiting List → Promotion → Student Confirms → Supervisor Approves
═══════════════════════════════════════════════════════════════════════════

Step 1: [Student1] Registers → Slot full → Added to waiting list
Step 2: [Student2] Registers → Slot full → Added to waiting list (position 2)
Step 3: [Student3] Cancels APPROVED registration
Step 4: [System] Promotes Student1 → Sends student confirmation email
Step 5: [Student1] Receives email → Clicks "Yes, I Want This Slot"
Step 6: [System] Sends supervisor approval email
Step 7: [Supervisor] Receives email → Clicks "Approve"
Step 8: [System] Registration becomes APPROVED
Step 9: [Student1] Opens app → Sees "Registration approved ✓"

RESULT: ✅ Student1 registered and approved, Student2 still on waiting list


═══════════════════════════════════════════════════════════════════════════
FLOW C: Promotion Chain - Multiple Declines
═══════════════════════════════════════════════════════════════════════════

Step 1: [Student1] Cancels → [Student2] Promoted → Receives confirmation email
Step 2: [Student2] Declines → [Student3] Promoted → Receives confirmation email
Step 3: [Student3] Confirms → Supervisor receives approval email
Step 4: [Supervisor] Approves → [Student3] Registration APPROVED
Step 5: [Student4] Still on waiting list (position 1)

RESULT: ✅ Student3 registered, Student4 waiting


═══════════════════════════════════════════════════════════════════════════
FLOW D: Student on Waiting List Registers for Different Slot
═══════════════════════════════════════════════════════════════════════════

Step 1: [Student] On waiting list for Slot A (position 2)
Step 2: [Student] Opens app → Sees Slot B available
Step 3: [Student] Registers for Slot B → Success
Step 4: [System] Creates PENDING registration for Slot B
Step 5: [Student] Still on waiting list for Slot A
Step 6: [Student] Opens app:
        - Slot A: onWaitingList=true, waitingListCount=3
        - Slot B: approvalStatus="PENDING_APPROVAL"

RESULT: ✅ Student has both: waiting list position + pending registration


═══════════════════════════════════════════════════════════════════════════
FLOW E: Supervisor Declines → Student Can Register Again
═══════════════════════════════════════════════════════════════════════════

Step 1: [Student] Registers → Supervisor receives approval email
Step 2: [Supervisor] Clicks "Decline"
Step 3: [System] Registration becomes DECLINED
Step 4: [Student] Opens app → Sees canRegister=true
Step 5: [Student] Can register again (if slot still available)

RESULT: ✅ Student can retry registration


═══════════════════════════════════════════════════════════════════════════
FLOW F: Registration Limits (MSc vs PhD)
═══════════════════════════════════════════════════════════════════════════

Step 1: [MSc Student] Registers for Slot A → PENDING (1/3 limit)
Step 2: [MSc Student] Registers for Slot A again → PENDING (2/3 limit)
Step 3: [MSc Student] Registers for Slot A again → PENDING (3/3 limit)
Step 4: [MSc Student] Tries to register for Slot A again → FAILS
        Error: "MSc students can have maximum 3 pending approvals per slot"
Step 5: [PhD Student] Registers for Slot A → PENDING (1/1 limit)
Step 6: [PhD Student] Tries to register for Slot A again → FAILS
        Error: "PhD students can have maximum 1 pending approval per slot"

RESULT: ✅ Limits enforced correctly


═══════════════════════════════════════════════════════════════════════════
FLOW G: Expired Token Handling
═══════════════════════════════════════════════════════════════════════════

Step 1: [Student] Registers → Supervisor receives approval email
Step 2: [Time Passes] Token expires (after 7 days default)
Step 3: [Supervisor] Clicks approve link → FAILS
        Error: "Approval token has expired"
Step 4: [System] Registration status: PENDING → EXPIRED
Step 5: [Student] Opens app → Sees canRegister=true (can register again)
Step 6: [System] Expired registration doesn't count toward capacity

RESULT: ✅ Expired registrations handled gracefully

```

---

## User Action → System Response Matrix

```
┌─────────────────────────────────────────────────────────────────────────┐
│              USER ACTION → SYSTEM RESPONSE MATRIX                       │
└─────────────────────────────────────────────────────────────────────────┘

STUDENT ACTIONS:
┌─────────────────────────────┬──────────────────────────────────────────┐
│ Student Action              │ System Response                         │
├─────────────────────────────┼──────────────────────────────────────────┤
│ Register for Available Slot │ → PENDING registration created          │
│                             │ → Supervisor receives approval email     │
│                             │ → Mobile shows "Pending Approval"       │
├─────────────────────────────┼──────────────────────────────────────────┤
│ Join Waiting List           │ → Added to waiting list (position N)    │
│                             │ → Mobile shows "On waiting list (#N)"   │
├─────────────────────────────┼──────────────────────────────────────────┤
│ Cancel Registration        │ → Registration deleted                 │
│ (APPROVED)                  │ → If waiting list exists → Next person │
│                             │   promoted → Receives confirmation email │
├─────────────────────────────┼──────────────────────────────────────────┤
│ Cancel Registration         │ → Registration deleted                  │
│ (PENDING)                   │ → No promotion (slot already had space) │
├─────────────────────────────┼──────────────────────────────────────────┤
│ Leave Waiting List          │ → Removed from waiting list             │
│                             │ → Positions of others updated            │
├─────────────────────────────┼──────────────────────────────────────────┤
│ Confirm Promotion Email     │ → Supervisor receives approval email    │
│ ("Yes, I Want This Slot")   │ → Registration remains PENDING           │
│                             │ → Waiting for supervisor approval        │
├─────────────────────────────┼──────────────────────────────────────────┤
│ Decline Promotion Email    │ → Registration deleted                    │
│ ("No, Decline")             │ → Next person automatically promoted     │
│                             │ → Next person receives confirmation email│
└─────────────────────────────┴──────────────────────────────────────────┘

SUPERVISOR ACTIONS:
┌─────────────────────────────┬──────────────────────────────────────────┐
│ Supervisor Action           │ System Response                         │
├─────────────────────────────┼──────────────────────────────────────────┤
│ Approve Registration        │ → Registration: PENDING → APPROVED      │
│ (from email link)           │ → Student receives notification email    │
│                             │ → Student's mobile shows "Approved"      │
├─────────────────────────────┼──────────────────────────────────────────┤
│ Decline Registration        │ → Registration: PENDING → DECLINED      │
│ (from email link)           │ → Student's mobile shows canRegister=true│
│                             │ → Student can register again            │
└─────────────────────────────┴──────────────────────────────────────────┘

SYSTEM AUTOMATIC ACTIONS:
┌─────────────────────────────┬──────────────────────────────────────────┐
│ System Event                │ Automatic Response                      │
├─────────────────────────────┼──────────────────────────────────────────┤
│ APPROVED registration       │ → Check if waiting list exists          │
│ cancelled + slot has        │ → Promote next person from waiting list  │
│ capacity                    │ → Send student confirmation email        │
├─────────────────────────────┼──────────────────────────────────────────┤
│ Student confirms promotion  │ → Clear student confirmation token       │
│                             │ → Send supervisor approval email         │
├─────────────────────────────┼──────────────────────────────────────────┤
│ Student declines promotion  │ → Delete registration                    │
│                             │ → Automatically promote next person      │
│                             │ → Send confirmation email to next person │
├─────────────────────────────┼──────────────────────────────────────────┤
│ Student registers for same  │ → Automatically remove from waiting list │
│ slot they're waiting for    │ → Log: WAITING_LIST_AUTO_REMOVED        │
└─────────────────────────────┴──────────────────────────────────────────┘

```

---

## Mobile App UI States

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    MOBILE APP UI STATES                                 │
└─────────────────────────────────────────────────────────────────────────┘

SLOT CARD STATES:

┌─────────────────────────────────────────────────────────────────────────┐
│ STATE 1: Available Slot (Can Register)                                  │
├─────────────────────────────────────────────────────────────────────────┤
│ Slot Name: "Seminar Slot - Jan 15, 10:00"                              │
│ Status: "Available: 3 spots"                                            │
│ Waiting List: "0 people waiting"                                        │
│                                                                          │
│ [Register Button] ← Enabled, visible                                     │
│ [Join Waiting List Button] ← Hidden                                     │
│ [Cancel Registration Button] ← Hidden                                   │
│ [Leave Waiting List Button] ← Hidden                                    │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ STATE 2: Full Slot (Can Join Waiting List)                             │
├─────────────────────────────────────────────────────────────────────────┤
│ Slot Name: "Seminar Slot - Jan 15, 10:00"                              │
│ Status: "Full (0 spots available)"                                      │
│ Waiting List: "3 people waiting"                                        │
│                                                                          │
│ [Register Button] ← Hidden                                              │
│ [Join Waiting List Button] ← Enabled, visible                           │
│ [Cancel Registration Button] ← Hidden                                   │
│ [Leave Waiting List Button] ← Hidden                                    │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ STATE 3: Student Registered (Pending Approval)                           │
├─────────────────────────────────────────────────────────────────────────┤
│ Slot Name: "Seminar Slot - Jan 15, 10:00"                              │
│ Status: "Waiting for supervisor approval"                               │
│ Your Status: "Pending Approval"                                         │
│ Waiting List: "0 people waiting"                                        │
│                                                                          │
│ [Register Button] ← Hidden                                              │
│ [Join Waiting List Button] ← Hidden                                     │
│ [Cancel Registration Button] ← Enabled, visible                         │
│ [Leave Waiting List Button] ← Hidden                                    │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ STATE 4: Student Registered (Approved)                                   │
├─────────────────────────────────────────────────────────────────────────┤
│ Slot Name: "Seminar Slot - Jan 15, 10:00"                              │
│ Status: "Registration approved ✓"                                       │
│ Your Status: "Approved"                                                  │
│ Waiting List: "0 people waiting"                                        │
│                                                                          │
│ [Register Button] ← Hidden                                              │
│ [Join Waiting List Button] ← Hidden                                     │
│ [Cancel Registration Button] ← Enabled, visible                         │
│ [Leave Waiting List Button] ← Hidden                                    │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ STATE 5: Student on Waiting List                                        │
├─────────────────────────────────────────────────────────────────────────┤
│ Slot Name: "Seminar Slot - Jan 15, 10:00"                              │
│ Status: "Full (0 spots available)"                                      │
│ Your Status: "On waiting list (#2)"                                     │
│ Waiting List: "3 people waiting"                                        │
│                                                                          │
│ [Register Button] ← Hidden                                              │
│ [Join Waiting List Button] ← Hidden                                     │
│ [Cancel Registration Button] ← Hidden                                   │
│ [Leave Waiting List Button] ← Enabled, visible                           │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│ STATE 6: Student Promoted (Awaiting Confirmation)                       │
├─────────────────────────────────────────────────────────────────────────┤
│ Slot Name: "Seminar Slot - Jan 15, 10:00"                              │
│ Status: "Slot available! Confirm your registration"                    │
│ Your Status: "Pending Confirmation" (check email)                       │
│ Waiting List: "0 people waiting"                                        │
│                                                                          │
│ [Register Button] ← Hidden                                              │
│ [Join Waiting List Button] ← Hidden                                     │
│ [Cancel Registration Button] ← Enabled, visible                         │
│ [Leave Waiting List Button] ← Hidden                                    │
│                                                                          │
│ NOTE: Student should check email for confirmation link                   │
└─────────────────────────────────────────────────────────────────────────┘

```

---

## Email Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         EMAIL FLOW DIAGRAM                               │
└─────────────────────────────────────────────────────────────────────────┘

NORMAL REGISTRATION FLOW:
┌──────────────┐
│   Student    │
│  Registers   │
└──────┬───────┘
       │
       ▼
┌─────────────────────┐
│ System Creates      │
│ PENDING Registration│
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐      ┌──────────────────┐
│ Email Sent To:     │─────►│   Supervisor     │
│ Supervisor         │      │ Approval Email   │
└─────────────────────┘      └────────┬─────────┘
                                     │
                                     ▼
                            ┌──────────────────┐
                            │ Supervisor      │
                            │ Clicks Approve  │
                            └────────┬─────────┘
                                     │
                                     ▼
                            ┌──────────────────┐      ┌──────────────────┐
                            │ Registration     │─────►│   Student        │
                            │ APPROVED         │      │ Notification     │
                            └──────────────────┘      │ Email            │
                                                       └──────────────────┘


WAITING LIST PROMOTION FLOW:
┌──────────────┐
│   Student1   │
│   Cancels    │
│  (APPROVED)  │
└──────┬───────┘
       │
       ▼
┌─────────────────────┐
│ System Promotes     │
│ Student2 from       │
│ Waiting List        │
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐      ┌──────────────────┐
│ Email Sent To:     │─────►│   Student2       │
│ Student2           │      │ Confirmation     │
│ (NOT Supervisor)  │      │ Email            │
└─────────────────────┘      └────────┬─────────┘
                                     │
                    ┌────────────────┴────────────────┐
                    │                                 │
                    ▼                                 ▼
         ┌──────────────────┐              ┌──────────────────┐
         │ Student2 Clicks │              │ Student2 Clicks  │
         │ "Yes, I Want"   │              │ "No, Decline"    │
         └────────┬─────────┘              └────────┬─────────┘
                  │                                 │
                  ▼                                 ▼
         ┌──────────────────┐              ┌──────────────────┐
         │ Email Sent To:  │              │ Registration     │
         │ Supervisor      │              │ Deleted          │
         │ Approval Email  │              │                  │
         └────────┬─────────┘              └────────┬─────────┘
                  │                                 │
                  ▼                                 ▼
         ┌──────────────────┐              ┌──────────────────┐
         │ Supervisor      │              │ Student3         │
         │ Approves        │              │ Auto-Promoted    │
         └────────┬─────────┘              │                  │
                  │                        └────────┬─────────┘
                  ▼                                 │
         ┌──────────────────┐                       ▼
         │ Student2        │              ┌──────────────────┐
         │ Receives        │              │ Student3         │
         │ Approval        │              │ Receives         │
         │ Notification    │              │ Confirmation     │
         └──────────────────┘              │ Email            │
                                           └──────────────────┘

```

---

## Decision Points

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         DECISION POINTS                                 │
└─────────────────────────────────────────────────────────────────────────┘

DECISION 1: Can Student Register?
    │
    ├─► Slot has capacity? ──NO──► Show "Join Waiting List" button
    │   │
    │   └─YES
    │       │
    │       ├─► Student already registered? ──YES──► Show "Cancel Registration"
    │       │   │
    │       │   └─NO
    │       │       │
    │       │       ├─► Student on waiting list for this slot? ──YES──► Auto-remove from waiting list, register
    │       │       │   │
    │       │       │   └─NO
    │       │       │       │
    │       │       │       ├─► Pending limit reached? ──YES──► Show error "Pending limit reached"
    │       │       │       │   │
    │       │       │       │   └─NO──► Show "Register" button
    │       │       │       │
    │       │       └─► Student on waiting list for OTHER slot? ──YES──► Allow registration (different slot)
    │       │
    │       └─► Slot full? ──YES──► Show "Join Waiting List" button


DECISION 2: Should Promote from Waiting List?
    │
    ├─► APPROVED registration cancelled? ──NO──► Don't promote
    │   │
    │   └─YES
    │       │
    │       ├─► Slot has capacity now? ──NO──► Don't promote
    │       │   │
    │       │   └─YES
    │       │       │
    │       │       ├─► Waiting list has people? ──NO──► Don't promote
    │       │       │   │
    │       │       │   └─YES
    │       │       │       │
    │       │       │       ├─► Next person already registered? ──YES──► Remove from waiting list, skip
    │       │       │       │   │
    │       │       │       │   └─NO──► Promote (create PENDING registration, send student confirmation email)


DECISION 3: What Email to Send?
    │
    ├─► Normal registration? ──YES──► Send supervisor approval email
    │   │
    │   └─NO
    │       │
    │       ├─► Promotion from waiting list? ──YES──► Send student confirmation email (NOT supervisor)
    │       │   │
    │       │   └─NO
    │       │       │
    │       │       └─► Student confirmed promotion? ──YES──► Send supervisor approval email

```

---

## Summary

**Student can:**
- Register for available slots
- Join waiting list for full slots
- Register for different slots while on waiting list
- Cancel registrations
- Leave waiting list
- Confirm/decline promotion emails

**Supervisor can:**
- Approve registrations via email link
- Decline registrations via email link

**System automatically:**
- Promotes next person when APPROVED registration cancelled
- Sends student confirmation email (not supervisor) when promoted
- Promotes next person when student declines promotion
- Removes from waiting list when student registers for same slot
