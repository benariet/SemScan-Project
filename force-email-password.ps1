# =============================================
# Nuclear Approach: Force Email Password via Environment Variable
# =============================================
# For Windows - instructions to run on Linux server
#
# SECURITY WARNING: Do NOT commit real passwords to version control!
# Replace "your-password-here" with actual password when running this script.

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "FORCING EMAIL PASSWORD UPDATE" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Run these commands on your Linux server:" -ForegroundColor Yellow
Write-Host ""

$commands = @"
# Step 1: Create override directory
sudo mkdir -p /etc/systemd/system/semscan-api.service.d

# Step 2: Create override.conf with environment variables
sudo tee /etc/systemd/system/semscan-api.service.d/override.conf > /dev/null << 'EOF'
[Service]
# FORCE email password via environment variable (highest priority)
Environment="SPRING_MAIL_PASSWORD=your-password-here"
Environment="SPRING_MAIL_USERNAME=your-email@bgu.ac.il"
Environment="SPRING_MAIL_FROM=SemScan_System_NoReply@bgu.ac.il"
EOF

# Step 3: Reload systemd
sudo systemctl daemon-reload

# Step 4: Restart service
sudo systemctl restart semscan-api

# Step 5: Verify
sudo journalctl -u semscan-api -n 50 | grep -i "SMTP Configuration\|EMAIL_CONFIG_LOADED"
"@

Write-Host $commands -ForegroundColor Green
Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "After running, check logs for:" -ForegroundColor Yellow
Write-Host "  Password: Ta***23! (should be Ta***23! not ta***r1)" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
