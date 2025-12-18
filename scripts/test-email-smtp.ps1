# =============================================
# SMTP Email Test Script for Office 365
# =============================================
# This script tests SMTP connection and authentication
# Usage: .\test-email-smtp.ps1

param(
    [string]$ToEmail = "benariet@bgu.ac.il",
    [string]$FromEmail = "SemScan_System_NoReply@bgu.ac.il",
    [string]$SmtpHost = "outlook.office365.com",
    [int]$SmtpPort = 587,
    [string]$Username = "benariet@bgu.ac.il",
    [string]$Password = "Taltal123!"
)

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "SMTP Email Test Script" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: Basic TCP Connection
$hostPort = "$SmtpHost`:$SmtpPort"
Write-Host "[Test 1] Testing TCP connection to $hostPort..." -ForegroundColor Yellow
try {
    $tcpClient = New-Object System.Net.Sockets.TcpClient
    $connect = $tcpClient.BeginConnect($SmtpHost, $SmtpPort, $null, $null)
    $wait = $connect.AsyncWaitHandle.WaitOne(5000, $false)
    
    if ($wait) {
        $tcpClient.EndConnect($connect)
        Write-Host "[OK] TCP connection successful" -ForegroundColor Green
        $tcpClient.Close()
    } else {
        Write-Host "[FAIL] TCP connection timeout" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "[FAIL] TCP connection failed: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Test 2: SMTP Connection and Authentication
Write-Host "[Test 2] Testing SMTP authentication..." -ForegroundColor Yellow
try {
    # Create SMTP client
    $smtp = New-Object System.Net.Mail.SmtpClient($SmtpHost, $SmtpPort)
    $smtp.EnableSsl = $true
    $smtp.Timeout = 30000
    $smtp.Credentials = New-Object System.Net.NetworkCredential($Username, $Password)
    
    # Create test message
    $message = New-Object System.Net.Mail.MailMessage
    $message.From = New-Object System.Net.Mail.MailAddress($FromEmail)
    $message.To.Add($ToEmail)
    $message.Subject = "SMTP Test - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    $message.Body = "This is a test email from SemScan API SMTP test script.`n`nIf you receive this, SMTP configuration is working correctly!"
    $message.IsBodyHtml = $false
    
    # Send email
    Write-Host "  Sending test email to $ToEmail..." -ForegroundColor Gray
    $smtp.Send($message)
    
    Write-Host "[OK] Email sent successfully!" -ForegroundColor Green
    Write-Host "  Check inbox: $ToEmail" -ForegroundColor Gray
    
    $message.Dispose()
    $smtp.Dispose()
    
} catch [System.Net.Mail.SmtpException] {
    Write-Host "[FAIL] SMTP Error: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.InnerException) {
        Write-Host "  Inner Exception: $($_.Exception.InnerException.Message)" -ForegroundColor Red
    }
    exit 1
} catch {
    Write-Host "[FAIL] Error: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "All tests passed!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
