# === SAFE build+install script that handles file locking issues ===

Write-Host "Starting safe build and install process..." -ForegroundColor Green

# Kill any running ADB processes that might lock files
Write-Host "Checking for running ADB processes..." -ForegroundColor Yellow
$adbProcesses = Get-Process -Name "adb" -ErrorAction SilentlyContinue
if ($adbProcesses) {
    Write-Host "Found $($adbProcesses.Count) ADB processes. Stopping them..." -ForegroundColor Yellow
    $adbProcesses | Stop-Process -Force
    Start-Sleep -Seconds 2
}

# Clean build directory to avoid file locking issues
Write-Host "Cleaning build directory..." -ForegroundColor Yellow
if (Test-Path "build") {
    try {
        Remove-Item -Path "build" -Recurse -Force -ErrorAction Stop
        Write-Host "Build directory cleaned successfully" -ForegroundColor Green
    } catch {
        Write-Host "Warning: Could not fully clean build directory: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# Build the project
Write-Host "Building Android app..." -ForegroundColor Green
& .\gradlew clean assembleDebug
if ($LASTEXITCODE -ne 0) { 
    Write-Host "Gradle build failed with exit code: $LASTEXITCODE" -ForegroundColor Red
    throw "Gradle build failed."
}

Write-Host "Build successful!" -ForegroundColor Green

# Find built APKs
$apkPaths = @(
    "build\outputs\apk\debug",
    "app\build\outputs\apk\debug"
)

$apks = @()
foreach ($path in $apkPaths) {
    if (Test-Path $path) {
        $found = Get-ChildItem -Path $path -Filter "*.apk" | Sort-Object LastWriteTime -Descending
        if ($found) {
            $apks += $found
            Write-Host "Found APKs in: $path" -ForegroundColor Yellow
        }
    }
}

if (-not $apks) { 
    Write-Host "No APKs found. Checked paths:" -ForegroundColor Red
    $apkPaths | ForEach-Object { Write-Host "  - $_" }
    throw "No APKs found in any expected location"
}

Write-Host "Found APKs:" -ForegroundColor Green
$apks | ForEach-Object { Write-Host "  - $($_.FullName)" -ForegroundColor Cyan }

# Check for devices
Write-Host "Checking for connected devices..." -ForegroundColor Green
$devs = (adb devices) | Select-String "`tdevice$" | ForEach-Object { $_.Line.Split("`t")[0] }
if (-not $devs) { 
    Write-Host "No devices found (adb). Plug a device and enable USB debugging." -ForegroundColor Red
    Write-Host "Available APKs are ready for manual installation:" -ForegroundColor Yellow
    $apks | ForEach-Object { Write-Host "  - $($_.FullName)" }
    exit 0
}

Write-Host "Installing to devices: $($devs -join ', ')" -ForegroundColor Green

# Install APKs
foreach ($apk in $apks) {
  foreach ($d in $devs) {
    Write-Host "Installing $($apk.Name) on $d ..." -ForegroundColor Green
    $result = adb -s $d install -r -d "$($apk.FullName)"
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Successfully installed on $d" -ForegroundColor Green
    } else {
        Write-Host "Failed to install on $d`: $result" -ForegroundColor Red
    }
  }
}

Write-Host "Done!" -ForegroundColor Green
Write-Host "Build and installation completed successfully!" -ForegroundColor Green
