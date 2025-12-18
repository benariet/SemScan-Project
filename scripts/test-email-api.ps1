# =============================================
# Email Test via API Endpoint
# =============================================
# This script tests email sending through the SemScan API
# Usage: .\test-email-api.ps1

param(
    [string]$ApiUrl = "http://132.72.50.53:8080",
    [string]$ToEmail = "benariet@bgu.ac.il",
    [string]$Subject = "API Test Email - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
)

# Disable colors to avoid errors
$Host.UI.RawUI.ForegroundColor = "White"

Write-Output "========================================="
Write-Output "Email API Test Script"
Write-Output "========================================="
Write-Output ""

$endpoint = "$ApiUrl/api/v1/mail/send"

$body = @{
    to = $ToEmail
    subject = $Subject
    htmlContent = @"
        <html>
            <body>
                <h2>SemScan API Email Test</h2>
                <p>This is a test email sent via the SemScan API endpoint.</p>
                <p><strong>Timestamp:</strong> $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')</p>
                <p>If you receive this email, the API email service is working correctly!</p>
            </body>
        </html>
"@
} | ConvertTo-Json

Write-Output "Sending POST request to: $endpoint"
Write-Output "Recipient: $ToEmail"
Write-Output ""

try {
    $response = Invoke-RestMethod -Uri $endpoint -Method Post -Body $body -ContentType "application/json" -ErrorAction Stop
    
    if ($response.success -eq $true) {
        Write-Output "SUCCESS: Email sent successfully via API!"
        Write-Output "  Response: $($response.message)"
        Write-Output "  Check inbox: $ToEmail"
    } else {
        Write-Output "ERROR: Email sending failed"
        Write-Output "  Response: $($response | ConvertTo-Json)"
        exit 1
    }
} catch {
    Write-Output "ERROR: API Request failed: $_"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Output "  Response Body: $responseBody"
    }
    exit 1
}

Write-Output ""
Write-Output "========================================="
Write-Output "Test completed!"
Write-Output "========================================="
