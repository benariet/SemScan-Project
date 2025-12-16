#!/bin/bash
# =============================================
# Nuclear Approach: Force Email Password via Environment Variable
# =============================================
# This script updates the systemd service to use environment variables
# Environment variables have HIGHEST priority and override JAR-internal config

echo "========================================="
echo "FORCING EMAIL PASSWORD UPDATE"
echo "========================================="
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then 
    echo "ERROR: This script must be run as root (use sudo)"
    exit 1
fi

SERVICE_FILE="/etc/systemd/system/semscan-api.service"
OVERRIDE_DIR="/etc/systemd/system/semscan-api.service.d"
OVERRIDE_FILE="$OVERRIDE_DIR/override.conf"

# New password
NEW_PASSWORD="Taltal123!"
NEW_USERNAME="benariet@bgu.ac.il"
NEW_FROM="SemScan_System_NoReply@bgu.ac.il"

echo "Step 1: Creating override directory..."
mkdir -p "$OVERRIDE_DIR"

echo "Step 2: Creating/updating override.conf with environment variables..."
cat > "$OVERRIDE_FILE" << EOF
[Service]
# FORCE email password via environment variable (highest priority)
Environment="SPRING_MAIL_PASSWORD=$NEW_PASSWORD"
Environment="SPRING_MAIL_USERNAME=$NEW_USERNAME"
Environment="SPRING_MAIL_FROM=$NEW_FROM"
EOF

echo "Step 3: Reloading systemd daemon..."
systemctl daemon-reload

echo "Step 4: Restarting semscan-api service..."
systemctl restart semscan-api

echo ""
echo "========================================="
echo "DONE! Service restarted with new password"
echo "========================================="
echo ""
echo "Verify the password in logs:"
echo "  sudo journalctl -u semscan-api -n 50 | grep 'SMTP Configuration'"
echo ""
echo "You should see: Password: Ta***23! (not ta***r1)"
echo ""
