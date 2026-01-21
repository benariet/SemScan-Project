# Kill Stuck Transaction Script
Write-Host "=== Killing Stuck Transaction ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Found stuck transaction:" -ForegroundColor Yellow
Write-Host "  Thread ID: 28" -ForegroundColor White
Write-Host "  Transaction ID: 40426" -ForegroundColor White
Write-Host "  Started: 2025-11-12 11:41:35 (running for hours!)" -ForegroundColor Red
Write-Host "  Tables Locked: 4" -ForegroundColor White
Write-Host "  Rows Locked: 14" -ForegroundColor White
Write-Host ""
Write-Host "This transaction is blocking all slot registrations." -ForegroundColor Yellow
Write-Host ""
Write-Host "To fix, run this SQL command in your MySQL client:" -ForegroundColor Cyan
Write-Host "  KILL 28;" -ForegroundColor Green
Write-Host ""
Write-Host "Or use the SQL file:" -ForegroundColor Cyan
Write-Host "  mysql -u root -p semscan_db < kill-stuck-transaction.sql" -ForegroundColor Green
Write-Host ""
Write-Host "After killing the thread, test registration again:" -ForegroundColor Yellow
Write-Host "  .\test-slot-registration.ps1" -ForegroundColor White

