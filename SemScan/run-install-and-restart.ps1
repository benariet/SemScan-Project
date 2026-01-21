<# 
Run from your project root:
  .\run-install-and-restart.ps1
Optional: target a specific device:
  $env:ANDROID_SERIAL="emulator-5554"; .\run-install-and-restart.ps1
#>

$ErrorActionPreference = 'Stop'

# === Config ===
$PKG = 'org.example.semscan'           # <-- change if your package id is different
$ACTIVITY = 'org.example.semscan.ui.auth.LoginActivity'  # Launcher activity
$GRADLEW = '.\gradlew'                 # Path to gradlew in your project root

Write-Host "==> Verifying adb is available..."
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
  throw "adb not found in PATH"
}

Write-Host "==> Connected devices:"
adb devices

# Build ADB command (respect ANDROID_SERIAL if set, otherwise auto-select physical device)
$adbCmd = 'adb'
$adbArgs = @()

# Define function before it's used
function InstallAndLaunchOnDevice {
    param(
        [string]$ApkPath = $null,
        [string]$DeviceSerial = $null
    )
    
    # Build adb command for this device
    $localAdbCmd = 'adb'
    $localAdbArgs = @()
    if ($DeviceSerial) {
        $localAdbArgs = @('-s', $DeviceSerial)
    } elseif ($script:adbArgs.Count -gt 0) {
        $localAdbArgs = $script:adbArgs
    }
    
    Write-Host "==> Setting up port forwarding (localhost:8080 -> dev machine:8080)"
    try { 
        $cmdArgs = $localAdbArgs + @('reverse', 'tcp:8080', 'tcp:8080')
        & $localAdbCmd $cmdArgs
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  [OK] Port forwarding active" -ForegroundColor Green
        } else {
            Write-Host "  [WARN] Port forwarding failed (may already be active)" -ForegroundColor Yellow
        }
    } catch { 
        Write-Host "  [WARN] Port forwarding error: $_" -ForegroundColor Yellow
    }

    Write-Host "==> Stopping app: $PKG"
    try { 
        $cmdArgs = $localAdbArgs + @('shell', 'am', 'force-stop', $PKG)
        & $localAdbCmd $cmdArgs | Out-Null 
    } catch { }

    if ($ApkPath) {
        # Install specific APK on specific device (for multi-device scenario)
        Write-Host "==> Installing APK on device"
        $cmdArgs = $localAdbArgs + @('install', '-r', '-d', $ApkPath)
        & $localAdbCmd $cmdArgs
        if ($LASTEXITCODE -ne 0) {
            Write-Host "  [ERROR] Installation failed" -ForegroundColor Red
            return
        }
        Write-Host "  [OK] Installed successfully" -ForegroundColor Green
    } else {
        # Use Gradle installDebug (for single device scenario)
        Write-Host "==> Installing debug build via Gradle"
        & $GRADLEW installDebug
    }

    Write-Host "==> Launching $PKG/$ACTIVITY"
    $cmdArgs = $localAdbArgs + @('shell', 'am', 'start', '-n', "$PKG/$ACTIVITY")
    & $localAdbCmd $cmdArgs
}
if ($env:ANDROID_SERIAL) { 
    $adbArgs = @('-s', $env:ANDROID_SERIAL)
    Write-Host "==> Using specified device: $($env:ANDROID_SERIAL)" -ForegroundColor Green
} else {
    # Auto-select physical device (exclude emulators)
    $allDevs = (adb devices) | Select-String "`tdevice$" | ForEach-Object { $_.Line.Split("`t")[0] }
    $physicalDevs = $allDevs | Where-Object { $_ -notmatch "^emulator-" }
    if ($physicalDevs.Count -eq 1) {
        $env:ANDROID_SERIAL = $physicalDevs[0]
        Write-Host "==> Auto-selected physical device: $($env:ANDROID_SERIAL)" -ForegroundColor Green
        $adbArgs = @('-s', $env:ANDROID_SERIAL)
    } elseif ($physicalDevs.Count -gt 1) {
        Write-Host "==> Multiple physical devices found: $($physicalDevs -join ', ')" -ForegroundColor Yellow
        Write-Host "==> Auto-selecting all devices for installation" -ForegroundColor Green
        
        # Automatically select all devices
            $selectedDevices = $physicalDevs
            Write-Host "==> Selected all devices: $($selectedDevices -join ', ')" -ForegroundColor Green
        
        # If multiple devices selected, build once then install on each device
        if ($selectedDevices.Count -gt 1) {
            Write-Host ""
            Write-Host "==> Building APK (will install on $($selectedDevices.Count) device(s))"
            & $GRADLEW assembleDebug
            if ($LASTEXITCODE -ne 0) {
                throw "Gradle build failed."
                    }
            
            # Find built APK
            $apks = Get-ChildItem -Recurse "build\outputs\apk" -Filter "*-debug.apk" | Sort-Object LastWriteTime -Descending
            if (-not $apks) {
                throw "No APK found after build"
            }
            $apkPath = $apks[0].FullName
            Write-Host "  [OK] APK built: $($apks[0].Name)" -ForegroundColor Green
        
            # Install on each device
            foreach ($device in $selectedDevices) {
                Write-Host ""
                Write-Host "==> Processing device: $device" -ForegroundColor Cyan
                $env:ANDROID_SERIAL = $device
                InstallAndLaunchOnDevice -ApkPath $apkPath -DeviceSerial $device
            }
            Write-Host ""
            Write-Host "==> Done. Installed on $($selectedDevices.Count) device(s)." -ForegroundColor Green
            exit 0
        } else {
            # Single device - continue with normal flow
            $env:ANDROID_SERIAL = $selectedDevices[0]
            $adbArgs = @('-s', $selectedDevices[0])
        }
    } elseif ($allDevs.Count -gt 0) {
        Write-Host "==> Only emulators found. Please connect a physical device." -ForegroundColor Yellow
        throw "No physical devices found. Only emulators detected."
    } else {
        throw "No devices found. Enable USB debugging on your device."
    }
}

# Install and launch on the selected device(s)
if ($adbArgs.Count -gt 0) {
    InstallAndLaunchOnDevice -DeviceSerial $adbArgs[1]
} else {
InstallAndLaunchOnDevice
}

Write-Host "==> Done."
