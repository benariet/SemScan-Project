# ADB Commands Guide for SemScan API Development

## Overview
This guide provides the essential ADB (Android Debug Bridge) commands for connecting your Android devices to the SemScan API backend running on your PC.

## Device Information
- **Device 1**: `9b7bf207`
- **Device 2**: `HGAJ74G6`
- **Backend Server**: `localhost:8080`

---

## 🔍 1. List Connected Devices

Check which devices are connected and get their details:

```bash
adb devices -l
```

**Expected Output:**
```
List of devices attached
9b7bf207    device usb:1-1 product:device_name model:device_model device:device_id
HGAJ74G6    device usb:1-2 product:device_name model:device_model device:device_id
```

---

## 🔗 2. Create Reverse Tunnel (Device → PC :8080)

Set up port forwarding so your Android devices can access the API on your PC:

### For Device 1 (9b7bf207):
```bash
adb -s 9b7bf207 reverse tcp:8080 tcp:8080
```

### For Device 2 (HGAJ74G6):
```bash
adb -s HGAJ74G6 reverse tcp:8080 tcp:8080
```

**What this does:**
- Maps `localhost:8080` on the Android device to `localhost:8080` on your PC
- Allows your Android app to connect to `http://localhost:8080/` and reach your backend

---

## ✅ 3. Verify the Tunnel

Check if the reverse tunnel is working correctly:

### For Device 1 (9b7bf207):
```bash
adb -s 9b7bf207 reverse --list
```

### For Device 2 (HGAJ74G6):
```bash
adb -s HGAJ74G6 reverse --list
```

**Expected Output:**
```
UsbFfs tcp:8080 tcp:8080
```

---

## 🧪 4. Quick Connectivity Check

Test if your devices can reach the backend server:

### For Device 1 (9b7bf207):
```bash
adb -s 9b7bf207 shell curl -I http://localhost:8080/
```

### For Device 2 (HGAJ74G6):
```bash
adb -s HGAJ74G6 shell curl -I http://localhost:8080/
```

**Expected Output:**
```
HTTP/1.1 200 OK
Server: Apache-Coyote/1.1
Content-Type: text/html;charset=UTF-8
Content-Length: 1234
Date: Wed, 16 Oct 2025 16:00:00 GMT
```

---

## 🔄 5. Reset Commands (Optional)

If you encounter issues or need to reset the connections:

### Remove all reverse tunnels for Device 1:
```bash
adb -s 9b7bf207 reverse --remove-all
```

### Remove all reverse tunnels for Device 2:
```bash
adb -s HGAJ74G6 reverse --remove-all
```

---

## 🚀 Complete Setup Workflow

Here's the complete sequence to set up both devices:

```bash
# 1. Check connected devices
adb devices -l

# 2. Set up reverse tunnels
adb -s 9b7bf207 reverse tcp:8080 tcp:8080
adb -s HGAJ74G6 reverse tcp:8080 tcp:8080

# 3. Verify tunnels
adb -s 9b7bf207 reverse --list
adb -s HGAJ74G6 reverse --list

# 4. Test connectivity
adb -s 9b7bf207 shell curl -I http://localhost:8080/
adb -s HGAJ74G6 shell curl -I http://localhost:8080/
```

---

## 🔧 Troubleshooting

### Common Issues:

1. **"device not found"**
   - Ensure USB debugging is enabled
   - Check USB cable connection
   - Run `adb devices` to verify device is listed

2. **"Connection refused"**
   - Ensure your SemScan API is running on port 8080
   - Check if the reverse tunnel is active with `adb reverse --list`

3. **"Permission denied"**
   - Make sure you have ADB installed and in your PATH
   - Try running with `sudo` on Linux/Mac

### Reset Everything:
```bash
# Kill ADB server
adb kill-server

# Start ADB server
adb start-server

# Reconnect devices
adb devices -l
```

---

## 📱 Android App Configuration

In your Android app, use these URLs:

```java
// For development with ADB reverse tunnel
private static final String BASE_URL = "http://localhost:8080/";

// Alternative (if reverse tunnel doesn't work)
private static final String BASE_URL = "http://10.0.2.2:8080/"; // Android Emulator
// or
private static final String BASE_URL = "http://YOUR_PC_IP:8080/"; // Physical device
```

---

## 📋 Quick Reference

| Command | Purpose | Example |
|---------|---------|---------|
| `adb devices -l` | List devices | `adb devices -l` |
| `adb -s DEVICE reverse tcp:8080 tcp:8080` | Create tunnel | `adb -s 9b7bf207 reverse tcp:8080 tcp:8080` |
| `adb -s DEVICE reverse --list` | Verify tunnel | `adb -s 9b7bf207 reverse --list` |
| `adb -s DEVICE shell curl -I http://localhost:8080/` | Test connection | `adb -s 9b7bf207 shell curl -I http://localhost:8080/` |
| `adb -s DEVICE reverse --remove-all` | Reset tunnels | `adb -s 9b7bf207 reverse --remove-all` |

---

*Generated on: 2025-10-16*  
*Project: SemScan API*  
*Devices: 9b7bf207, HGAJ74G6*
