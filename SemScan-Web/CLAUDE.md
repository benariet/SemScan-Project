# SemScan Web App

## CRITICAL RULES

### 1. iOS/Safari First (MANDATORY)
**All libraries and browser APIs MUST prioritize iPhone Safari compatibility.**

When choosing any library or API:
1. **Check iOS Safari support FIRST** - if it doesn't work on iPhone Safari, don't use it
2. **Test on real iPhone** - Safari simulators don't catch all issues
3. **Prefer libraries that explicitly mention iOS support** in their docs

| Feature | iOS Safari Requirement |
|---------|----------------------|
| Camera/QR scanning | MUST work on iPhone Safari |
| File upload | MUST work on iPhone Safari |
| Touch events | MUST work on iPhone Safari |
| CSS/animations | MUST work on iPhone Safari |

**Reason:** The entire purpose of this web app is to support iPhone users who cannot use the Android app.

### 2. QR Scanning Library
**Use `html5-qrcode`** - chosen specifically for iOS Safari support.
- GitHub: https://github.com/mebjas/html5-qrcode
- Explicitly supports iOS Safari camera access
- Actively maintained

**DO NOT use:**
- `instascan` - known iOS issues
- `qr-scanner` - inconsistent iOS support
- Raw `getUserMedia` without wrapper - iOS quirks

### 3. HTTPS Required for Camera
Camera access (`getUserMedia`) requires HTTPS with valid certificate.
- Use Cloudflare Tunnel for free valid HTTPS
- Self-signed certificates DO NOT work reliably on iPhone Safari

## Project Structure

```
SemScan-Web/
├── index.html           # Login page
├── presenter.html       # Presenter dashboard
├── participant.html     # Participant home
├── scanner.html         # QR scanner page
├── css/
│   ├── main.css         # Base styles, CSS variables
│   ├── login.css        # Login page styles
│   ├── slots.css        # Slot cards (green/yellow/red)
│   └── scanner.css      # Camera overlay styles
├── js/
│   ├── config.js        # API URL configuration
│   ├── api.js           # API client (fetch wrapper)
│   ├── auth.js          # Login, session management
│   ├── presenter.js     # Presenter dashboard logic
│   ├── participant.js   # Participant logic
│   ├── slots.js         # Slot cards, registration
│   └── scanner.js       # QR scanning logic
├── lib/
│   └── html5-qrcode.min.js  # QR library
└── assets/
    └── logo.png
```

## Technology Stack

| Component | Choice | Why |
|-----------|--------|-----|
| Frontend | Vanilla JS (ES6+) | Simple, no build step, ~20 users |
| CSS | Pure CSS with variables | Match Android gradients |
| QR Library | html5-qrcode | Best iOS Safari support |
| HTTPS | Cloudflare Tunnel | Free valid cert, works on iPhone |

## API Base URL
```javascript
// config.js
const API_BASE = 'http://132.72.50.53:8080/api/v1';
```

When deployed via Cloudflare Tunnel, the web app and API will share the same origin, avoiding CORS issues.

## Key Pages

### Login (index.html)
- BGU username/password form
- Calls `POST /api/v1/auth/login`
- Stores session in localStorage
- Redirects to presenter.html or participant.html

### Presenter Dashboard (presenter.html)
- Lists slots with status colors (green/yellow/red)
- Register/Cancel buttons
- Open Attendance button (shows QR code)
- Join Waiting List button

### QR Scanner (scanner.html)
- Camera preview with html5-qrcode
- Parses QR data and calls attendance API
- Success/error toast notifications

## Session Management
```javascript
// Store after login
localStorage.setItem('bgu_username', username);
localStorage.setItem('session_token', token);

// Check on page load
if (!localStorage.getItem('session_token')) {
    window.location.href = 'index.html';
}

// Handle 401/403
if (response.status === 401 || response.status === 403) {
    localStorage.clear();
    window.location.href = 'index.html?expired=1';
}
```

## Slot Card Colors (CSS)
Match Android app gradients:
```css
.slot-card.available { background: linear-gradient(135deg, #4CAF50, #81C784); }
.slot-card.partial { background: linear-gradient(135deg, #FFC107, #FFD54F); }
.slot-card.full { background: linear-gradient(135deg, #F44336, #E57373); }
```

## Testing Checklist

### iPhone Safari (CRITICAL)
- [ ] Login works
- [ ] Camera permission prompt appears
- [ ] QR scanning works
- [ ] Touch scrolling smooth
- [ ] No iOS-specific CSS bugs

### Desktop Chrome
- [ ] All features work
- [ ] Responsive layout

## Deployment

The web files will be served from Spring Boot's static folder:
```bash
# Copy to API static folder
cp -r SemScan-Web/* SemScan-API/src/main/resources/static/

# Rebuild and deploy JAR
cd SemScan-API && ./gradlew bootJar
```

Access via Cloudflare Tunnel URL: `https://<tunnel-url>/index.html`
