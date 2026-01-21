# === Build and install APK to all connected devices ===
# Simple script - just run: .\build.ps1

$Wrapper = if (Test-Path ".\gradlew.bat") { ".\gradlew.bat" } else { ".\gradlew" }

# 1) Build APK
Write-Host "Building APK..." -ForegroundColor Green
& $Wrapper assembleDebug -x lintDebug
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed." }

# 2) Find built APKs
$apks = Get-ChildItem -Recurse "build\outputs\apk" -Filter "*-debug.apk" | Sort-Object LastWriteTime -Descending
if (-not $apks) { throw "No APKs found after build" }
Write-Host "Found APK: $($apks[0].Name)" -ForegroundColor Green

# 3) Devices - Include both physical devices and emulators
$allDevs = (adb devices) | Select-String "`tdevice$" | ForEach-Object { $_.Line.Split("`t")[0] }
$devs = $allDevs | Where-Object { $_.Trim() -ne "" }
if (-not $devs) { 
    Write-Host "No devices found. Please connect a device or start an emulator with USB debugging enabled." -ForegroundColor Yellow
    throw "No devices found. Enable USB debugging on your device or emulator."
}
Write-Host "Installing to devices: $($devs -join ', ')" -ForegroundColor Green

# 3.5) Setup port forwarding for API access (localhost:8080 -> dev machine:8080)
Write-Host "Setting up port forwarding..." -ForegroundColor Cyan
foreach ($d in $devs) {
  Write-Host "→ Setting up port forwarding on $d ..." -ForegroundColor Yellow
  adb -s $d reverse tcp:8080 tcp:8080
  if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✓ Port forwarding active on $d" -ForegroundColor Green
  } else {
    Write-Host "  ⚠ Port forwarding failed on $d (may already be active)" -ForegroundColor Yellow
  }
}

# 4) Install
foreach ($d in $devs) {
  Write-Host "→ Installing on $d ..." -ForegroundColor Yellow
  adb -s $d install -r -d "$($apks[0].FullName)" | Out-Null
}
Write-Host "✅ Done!" -ForegroundColor Green
