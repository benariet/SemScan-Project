# Test Second Session Attendance
# This script tests if the second session in a timeslot can be attended

param(
    [string]$SessionId = ""
)

Write-Host "=== Testing Second Session Attendance ===" -ForegroundColor Cyan
Write-Host ""

if ([string]::IsNullOrEmpty($SessionId)) {
    Write-Host "Usage: .\test-second-session-attendance.ps1 -SessionId <sessionId>" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "First, check what open sessions are available:" -ForegroundColor Yellow
    Write-Host "  Invoke-WebRequest -Uri 'http://localhost:8080/api/v1/sessions/open' -UseBasicParsing | Select-Object -ExpandProperty Content | ConvertFrom-Json | ConvertTo-Json -Depth 5" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Then test attendance submission with a session ID from the list." -ForegroundColor Yellow
    exit 1
}

Write-Host "Testing attendance submission for Session ID: $SessionId" -ForegroundColor White
Write-Host ""

# Step 1: Check if session is in open sessions list
Write-Host "Step 1: Checking if session is in open sessions list..." -ForegroundColor Yellow
try {
    $openSessionsResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/sessions/open" -UseBasicParsing
    $openSessions = $openSessionsResponse.Content | ConvertFrom-Json
    $sessionFound = $false
    $sessionStatus = ""
    
    foreach ($session in $openSessions) {
        if ($session.sessionId -eq [long]$SessionId) {
            $sessionFound = $true
            $sessionStatus = $session.status
            Write-Host "  Session found in open sessions list" -ForegroundColor Green
            Write-Host "    Status: $sessionStatus" -ForegroundColor White
            Write-Host "    Topic: $($session.topic)" -ForegroundColor White
            Write-Host "    Presenter: $($session.presenterName)" -ForegroundColor White
            break
        }
    }
    
    if (-not $sessionFound) {
        Write-Host "  WARNING: Session $SessionId NOT found in open sessions list!" -ForegroundColor Red
        Write-Host "    This indicates a backend bug - see BACKEND_FIX_MULTIPLE_PRESENTERS_SESSION.md" -ForegroundColor Yellow
        Write-Host "    Total open sessions returned: $($openSessions.Count)" -ForegroundColor White
        Write-Host "    Session IDs in list: $($openSessions.sessionId -join ', ')" -ForegroundColor White
    }
} catch {
    Write-Host "  ERROR: Could not fetch open sessions list" -ForegroundColor Red
    Write-Host "    $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host ""

# Step 2: Try to submit attendance
Write-Host "Step 2: Attempting to submit attendance..." -ForegroundColor Yellow
$body = @{
    sessionId = [long]$SessionId
    studentUsername = "teststudent"
    timestampMs = [long](Get-Date -UFormat %s) * 1000
} | ConvertTo-Json

try {
    $startTime = Get-Date
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/attendance" -Method POST -Body $body -ContentType "application/json" -UseBasicParsing -TimeoutSec 30
    $duration = ((Get-Date) - $startTime).TotalSeconds
    
    Write-Host "  SUCCESS (Status: $($response.StatusCode), Duration: $([math]::Round($duration, 2))s)" -ForegroundColor Green
    $response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 5
} catch {
    $duration = ((Get-Date) - $startTime).TotalSeconds
    $statusCode = $_.Exception.Response.StatusCode.value__
    $errorBody = $_.ErrorDetails.Message
    
    Write-Host "  FAILED (Status: $statusCode, Duration: $([math]::Round($duration, 2))s)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Error Response:" -ForegroundColor Red
    
    if ($errorBody) {
        try {
            $errorJson = $errorBody | ConvertFrom-Json
            Write-Host "  Message: $($errorJson.message)" -ForegroundColor Yellow
            Write-Host "  Error: $($errorJson.error)" -ForegroundColor Yellow
            
            if ($errorJson.error -match "Session is not open" -or $errorJson.message -match "Session is not open") {
                Write-Host ""
                Write-Host "  BACKEND ISSUE DETECTED!" -ForegroundColor Red
                Write-Host "  The backend is rejecting attendance for session $SessionId" -ForegroundColor Yellow
                Write-Host "  This confirms the backend attendance endpoint needs to be fixed:" -ForegroundColor Yellow
                Write-Host "    1. Check if backend queries by session_id (not slot_id)" -ForegroundColor White
                Write-Host "    2. Verify backend validates the SPECIFIC session ID" -ForegroundColor White
                Write-Host "    3. See BACKEND_FIX_MULTIPLE_PRESENTERS_SESSION.md for details" -ForegroundColor White
            }
        } catch {
            Write-Host "  $errorBody" -ForegroundColor Yellow
        }
    } else {
        Write-Host "  $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "If session was not in open sessions list OR attendance failed:" -ForegroundColor Yellow
Write-Host "  The backend needs to be fixed - see docs/BACKEND_FIX_MULTIPLE_PRESENTERS_SESSION.md" -ForegroundColor White

