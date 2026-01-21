#!/bin/bash
# =============================================
# Quick Email Test via API (curl)
# =============================================
# Usage: ./test-email-curl.sh

API_URL="${API_URL:-http://localhost:8080}"
TO_EMAIL="${TO_EMAIL:-benariet@bgu.ac.il}"

echo "========================================="
echo "Email API Test (curl)"
echo "========================================="
echo ""

curl -X POST "$API_URL/api/v1/mail/send" \
  -H "Content-Type: application/json" \
  -d "{
    \"to\": \"$TO_EMAIL\",
    \"subject\": \"API Test Email - $(date '+%Y-%m-%d %H:%M:%S')\",
    \"htmlContent\": \"<html><body><h2>SemScan API Email Test</h2><p>This is a test email sent via the SemScan API endpoint.</p><p><strong>Timestamp:</strong> $(date '+%Y-%m-%d %H:%M:%S')</p><p>If you receive this email, the API email service is working correctly!</p></body></html>\"
  }"

echo ""
echo "========================================="
