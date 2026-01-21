# Fix Database Locks Script
# This script helps identify and kill problematic database connections

Write-Host "=== Database Connection Analysis ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "Found problematic connection:" -ForegroundColor Yellow
Write-Host "  Process ID: 28" -ForegroundColor White
Write-Host "  User: root" -ForegroundColor White
Write-Host "  State: Sleep" -ForegroundColor White
Write-Host "  Time: 2012 seconds (~33 minutes)" -ForegroundColor Red
Write-Host ""

Write-Host "This long-sleeping connection may be holding locks." -ForegroundColor Yellow
Write-Host ""

$confirm = Read-Host "Kill process ID 28? (y/n)"
if ($confirm -eq "y" -or $confirm -eq "Y") {
    Write-Host ""
    Write-Host "To kill the connection, run this SQL command in your MySQL client:" -ForegroundColor Cyan
    Write-Host "  KILL 28;" -ForegroundColor Green
    Write-Host ""
    Write-Host "Or if you have mysql command line access:" -ForegroundColor Cyan
    Write-Host "  mysql -u root -p -e 'KILL 28;' semscan_db" -ForegroundColor Green
    Write-Host ""
    Write-Host "After killing the connection, test the registration again." -ForegroundColor Yellow
} else {
    Write-Host "Skipped killing connection." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Other Recommendations ===" -ForegroundColor Cyan
Write-Host "1. Check for uncommitted transactions:" -ForegroundColor White
Write-Host "   SELECT * FROM information_schema.innodb_trx;" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Check for table locks:" -ForegroundColor White
Write-Host "   SHOW OPEN TABLES WHERE In_use > 0;" -ForegroundColor Gray
Write-Host ""
Write-Host "3. Check for lock waits:" -ForegroundColor White
Write-Host "   SELECT * FROM information_schema.innodb_lock_waits;" -ForegroundColor Gray
Write-Host ""
Write-Host "4. After fixing, test registration again:" -ForegroundColor White
Write-Host "   .\test-slot-registration.ps1" -ForegroundColor Gray

