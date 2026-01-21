# Test Slot Registration Endpoint
param(
    [string]$Username = "talguest3",
    [long]$SlotId = 111,
    [string]$Topic = "Test Topic",
    [string]$Email = "test@example.com"
)

Write-Host "=== Testing Slot Registration Endpoint ===" -ForegroundColor Cyan
Write-Host "Username: $Username"
Write-Host "Slot ID: $SlotId"
Write-Host "Topic: $Topic"
Write-Host "Email: $Email"
Write-Host ""

$body = @{
    topic = $Topic
    presenterEmail = $Email
} | ConvertTo-Json

Write-Host "Sending POST request..." -ForegroundColor Yellow
$startTime = Get-Date

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/presenters/$Username/home/slots/$SlotId/register" -Method POST -Body $body -ContentType "application/json" -UseBasicParsing -TimeoutSec 60
    $duration = ((Get-Date) - $startTime).TotalSeconds
    Write-Host "SUCCESS (Status: $($response.StatusCode), Duration: $([math]::Round($duration, 2))s)" -ForegroundColor Green
    $response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 5
} catch {
    $duration = ((Get-Date) - $startTime).TotalSeconds
    $statusCode = $_.Exception.Response.StatusCode.value__
    $errorBody = $_.ErrorDetails.Message
    
    Write-Host "FAILED (Status: $statusCode, Duration: $([math]::Round($duration, 2))s)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Error Response:" -ForegroundColor Red
    
    if ($errorBody) {
        try {
            $errorJson = $errorBody | ConvertFrom-Json
            Write-Host "Message: $($errorJson.message)" -ForegroundColor Yellow
            Write-Host "Error: $($errorJson.error)" -ForegroundColor Yellow
            
            if ($errorJson.error -match "Lock wait timeout") {
                Write-Host ""
                Write-Host "DATABASE LOCK TIMEOUT DETECTED!" -ForegroundColor Red
                Write-Host "This indicates a database-level issue:" -ForegroundColor Yellow
                Write-Host "1. Check for long-running transactions"
                Write-Host "2. Check for deadlocks in database logs"
                Write-Host "3. Verify database connection pool settings"
                Write-Host "4. Restart the database if needed"
            }
        } catch {
            Write-Host $errorBody -ForegroundColor Yellow
        }
    } else {
        Write-Host $_.Exception.Message -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan
