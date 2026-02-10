# Android vs Web App Screen Comparison

## Authentication & Setup

| Screen | Android Activity | Web Page | Status | Differences |
|--------|-----------------|----------|--------|-------------|
| **Login** | `LoginActivity` | `index.html` | ✅ Match | Android: Remember Me checkbox, auto-login if saved. Web: Same |
| **First-Time Setup** | `FirstTimeSetupActivity` | `first-time-setup.html` | ✅ Match | Both: First name, last name, national ID, degree selection |
| **Degree Selection** | `DegreeSelectionActivity` | (in first-time-setup) | ✅ Integrated | Android has separate screen, Web combines into setup |
| **Role Picker** | `RolePickerActivity` | N/A | ❌ Missing | Android: Choose Presenter/Participant. Web: Direct links |

## Presenter Screens

| Screen | Android Activity | Web Page | Status | Differences |
|--------|-----------------|----------|--------|-------------|
| **Presenter Home** | `PresenterHomeActivity` | `presenter.html` | ✅ Match | Both have: Presentation Details, Select Slot, Start Session, My Slot, Change Role |
| **Presentation Details** | (expandable in Home) | `presentation-details.html` | ⚠️ Different | Android: Expandable card in home. Web: Separate page |
| **Slot Selection** | `PresenterSlotSelectionActivity` | `slots.html` | ✅ Match | Both: Slot list with colors, register/cancel, waiting list |
| **My Time Slot** | `PresenterMySlotActivity` | `my-slot.html` | ✅ Match | Both: Slot details, approval status, cancel button |
| **Start Session** | `PresenterStartSessionActivity` | `session-qr.html` | ✅ Match | Both: Open attendance, show QR |
| **QR Display** | `PresenterAttendanceQrActivity` | `session-qr.html` | ✅ Match | Both: QR code, timer, attendance count |
| **Session Summary** | (after close) | `session-summary.html` | ✅ Match | Both: Attendance summary after session |
| **Session Open Banner** | (banner in Home) | N/A | ❌ Missing | Android: Shows pulsing banner if session already open |

## Participant Screens

| Screen | Android Activity | Web Page | Status | Differences |
|--------|-----------------|----------|--------|-------------|
| **Participant Home** | `StudentHomeActivity` | `participant.html` | ✅ Match | Both: Scan QR, Manual Attendance, History, Change Role |
| **QR Scanner** | `ModernQRScannerActivity` | `scanner.html` | ✅ Match | Both: Camera scan, check open sessions first |
| **Attendance History** | `AttendanceHistoryActivity` | (in participant.html) | ⚠️ Different | Android: Separate screen. Web: Expandable section |
| **Manual Attendance** | `ManualAttendanceRequestActivity` | `manual-attendance.html` | ❓ Check | Web may be missing or incomplete |

## Settings & Common

| Screen | Android Activity | Web Page | Status | Differences |
|--------|-----------------|----------|--------|-------------|
| **Settings** | `SettingsActivity` | `settings.html` | ⚠️ Partial | Android: Has Report Bug button. Web: Missing Report Bug |
| **Logout** | (in menu/settings) | `logout.html` | ✅ Match | Both clear session and redirect |

---

## Feature Comparison by Screen

### Login Screen

| Feature | Android | Web |
|---------|---------|-----|
| Username field | ✅ | ✅ |
| Password field | ✅ | ✅ |
| Remember Me checkbox | ✅ | ✅ |
| Auto-login if remembered | ✅ | ✅ |
| Error message display | ✅ | ✅ |
| Loading state | ✅ | ✅ |

### Presenter Home

| Feature | Android | Web |
|---------|---------|-----|
| Presentation Details card | ✅ Expandable | ✅ Link to page |
| Select Slot card | ✅ | ✅ |
| Start Session card | ✅ | ✅ |
| My Time Slot card | ✅ | ✅ |
| Change Role card | ✅ | ✅ |
| Cards disabled until details filled | ✅ | ✅ |
| Cards disabled until approved | ✅ | ✅ |
| Session Open Banner (pulsing) | ✅ | ❌ Missing |
| Hamburger menu | ✅ | ✅ |
| Auto-refresh on resume/tab switch | ✅ | ✅ |

### Slot Selection

| Feature | Android | Web |
|---------|---------|-----|
| Slot list with colors (green/yellow/red) | ✅ | ✅ |
| Register button | ✅ | ✅ |
| Cancel registration | ✅ | ✅ |
| Join waiting list | ✅ | ✅ |
| Leave waiting list | ✅ | ✅ |
| Refresh button | ✅ | ✅ |
| PhD/MSc exclusivity rules | ✅ | ✅ |
| Show other presenters in slot | ✅ | ✅ |

### Session QR Screen

| Feature | Android | Web |
|---------|---------|-----|
| QR code display | ✅ | ✅ |
| Countdown timer | ✅ | ✅ |
| Attendance count | ✅ | ✅ |
| Close Session button | ✅ | ✅ |
| Auto-close on timer end | ✅ | ✅ |

### Participant Home

| Feature | Android | Web |
|---------|---------|-----|
| Scan Attendance QR card | ✅ | ✅ |
| Manual Attendance Request card | ✅ (if enabled) | ✅ (if enabled) |
| Attendance History card | ✅ | ✅ Expandable |
| Change Role card | ✅ | ✅ |
| Check open sessions before scan | ✅ | ✅ |

### QR Scanner

| Feature | Android | Web |
|---------|---------|-----|
| Camera preview | ✅ | ✅ |
| QR detection | ✅ | ✅ |
| Success message | ✅ | ✅ |
| Error message | ✅ | ✅ |
| Scan Another button | ✅ | ❌ Removed (by design) |

### Settings

| Feature | Android | Web |
|---------|---------|-----|
| Username (read-only) | ✅ | ✅ |
| First Name | ✅ | ✅ |
| Last Name | ✅ | ✅ |
| National ID | ✅ | ✅ |
| Degree dropdown | ✅ | ✅ |
| Save button | ✅ | ✅ |
| Logout button | ✅ | ✅ |
| Report Bug button | ✅ | ❌ Missing |
| Version display | ✅ | ❌ Missing |

---

## Summary of Missing/Different Features in Web App

| Priority | Feature | Location | Notes |
|----------|---------|----------|-------|
| Low | Session Open Banner | presenter.html | Pulsing banner when session already open |
| Low | Report Bug button | settings.html | Opens email with template |
| Low | Version display | settings.html | Shows app version |
| Low | Role Picker screen | N/A | Web uses direct navigation |
| Info | Presentation Details | Different UX | Android: expandable card. Web: separate page |
| Info | Attendance History | Different UX | Android: separate screen. Web: expandable section |

---

## File Mappings

### Android Activities → Web Pages

| Android Activity | Web Page |
|-----------------|----------|
| `LoginActivity` | `index.html` |
| `FirstTimeSetupActivity` | `first-time-setup.html` |
| `RolePickerActivity` | N/A (direct links) |
| `PresenterHomeActivity` | `presenter.html` |
| `PresenterSlotSelectionActivity` | `slots.html` |
| `PresenterMySlotActivity` | `my-slot.html` |
| `PresenterStartSessionActivity` | `session-qr.html` |
| `PresenterAttendanceQrActivity` | `session-qr.html` |
| `StudentHomeActivity` | `participant.html` |
| `ModernQRScannerActivity` | `scanner.html` |
| `AttendanceHistoryActivity` | (in `participant.html`) |
| `ManualAttendanceRequestActivity` | `manual-attendance.html` |
| `SettingsActivity` | `settings.html` |

### Web-Only Pages

| Page | Purpose |
|------|---------|
| `home.html` | Landing/redirect page |
| `presentation-details.html` | Separate page for presentation details |
| `session-summary.html` | Session summary after close |
| `logout.html` | Logout utility page |
| `login2.html` | Debug/test page |

---

*Last updated: 2026-02-10*
