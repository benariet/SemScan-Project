# Verify ADB Port Forwarding Script
# This script checks if ADB port forwarding is working correctly

Write-Host "=== ADB Port Forwarding Verification ===" -ForegroundColor Cyan
Write-Host ""

# Check if ADB is available
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: ADB not found in PATH" -ForegroundColor Red
    Write-Host "Please install Android SDK Platform Tools" -ForegroundColor Yellow
    exit 1
}

# List all connected devices
Write-Host "Connected devices:" -ForegroundColor Green
$devices = adb devices | Select-String "`tdevice$" | ForEach-Object { $_.Line.Split("`t")[0] }
if ($devices.Count -eq 0) {
    Write-Host "  No devices found!" -ForegroundColor Red
    Write-Host "  Please connect a device or start an emulator" -ForegroundColor Yellow
    exit 1
}

foreach ($device in $devices) {
    Write-Host "  - $device" -ForegroundColor White
}

Write-Host ""
Write-Host "Checking port forwarding for each device..." -ForegroundColor Green

foreach ($device in $devices) {
    Write-Host ""
    Write-Host "Device: $device" -ForegroundColor Cyan
    
    # Check if port forwarding is active
    Write-Host "  Checking port forwarding..." -ForegroundColor Yellow
    $forwarding = adb -s $device forward --list | Select-String "tcp:8080"
    
    if ($forwarding) {
        Write-Host "  ✓ Port forwarding is ACTIVE" -ForegroundColor Green
        Write-Host "    $forwarding" -ForegroundColor Gray
    } else {
        Write-Host "  ✗ Port forwarding is NOT active" -ForegroundColor Red
        Write-Host "  Setting up port forwarding..." -ForegroundColor Yellow
        adb -s $device reverse tcp:8080 tcp:8080
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  ✓ Port forwarding set up successfully" -ForegroundColor Green
        } else {
            Write-Host "  ✗ Failed to set up port forwarding" -ForegroundColor Red
        }
    }
    
    # Test connection by trying to reach localhost:8080 from the device
    Write-Host "  Testing connection to localhost:8080..." -ForegroundColor Yellow
    $testResult = adb -s $device shell "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 http://localhost:8080/actuator/health 2>/dev/null || echo 'FAILED'"
    
    if ($testResult -match "200|404|401") {
        Write-Host "  ✓ Connection successful (HTTP $testResult)" -ForegroundColor Green
        Write-Host "    Backend server is reachable!" -ForegroundColor Green
    } elseif ($testResult -eq "FAILED") {
        Write-Host "  ✗ Connection failed" -ForegroundColor Red
        Write-Host "    Possible issues:" -ForegroundColor Yellow
        Write-Host "    1. Backend server is not running on localhost:8080" -ForegroundColor Yellow
        Write-Host "    2. Port forwarding is not working correctly" -ForegroundColor Yellow
        Write-Host "    3. Firewall is blocking the connection" -ForegroundColor Yellow
    } else {
        Write-Host "  ⚠ Connection test returned: $testResult" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== Verification Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "If port forwarding is active but connection still fails:" -ForegroundColor Yellow
Write-Host "  1. Verify backend server is running: http://localhost:8080" -ForegroundColor White
Write-Host "  2. Check backend server logs for errors" -ForegroundColor White
Write-Host "  3. Try restarting ADB: adb kill-server; adb start-server" -ForegroundColor White
Write-Host "  4. Re-run port forwarding: adb reverse tcp:8080 tcp:8080" -ForegroundColor White

