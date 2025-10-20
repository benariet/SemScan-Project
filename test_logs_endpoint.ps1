# Test script for app logs endpoint
# This script tests the /api/v1/logs endpoint to ensure logs are being saved

$baseUrl = "http://localhost:8080"
$apiKey = "presenter-001-api-key-12345"

# Test log entry
$logData = @{
    logs = @(
        @{
            timestamp = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
            level = "INFO"
            tag = "TestApp"
            message = "Test log entry from PowerShell script"
            userId = "test-user-001"
            userRole = "STUDENT"
            deviceInfo = "Windows PowerShell Test"
            appVersion = "1.0.0"
        }
    )
} | ConvertTo-Json -Depth 3

Write-Host "Testing app logs endpoint..."
Write-Host "URL: $baseUrl/api/v1/logs"
Write-Host "API Key: $apiKey"
Write-Host "Log Data: $logData"

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/v1/logs" -Method POST -Headers @{
        "Content-Type" = "application/json"
        "x-api-key" = $apiKey
    } -Body $logData
    
    Write-Host "Response received:"
    $response | ConvertTo-Json -Depth 3
}
catch {
    Write-Host "Error occurred:"
    Write-Host $_.Exception.Message
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "Response body: $responseBody"
    }
}

Write-Host "`nNow testing to retrieve logs..."
try {
    $logsResponse = Invoke-RestMethod -Uri "$baseUrl/api/v1/logs/recent?limit=10" -Method GET -Headers @{
        "x-api-key" = $apiKey
    }
    
    Write-Host "Recent logs retrieved:"
    $logsResponse | ConvertTo-Json -Depth 3
}
catch {
    Write-Host "Error retrieving logs:"
    Write-Host $_.Exception.Message
}
