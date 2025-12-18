# Security Best Practices for SemScan API

## ⚠️ CRITICAL: Never Commit Passwords

**Passwords and secrets should NEVER be:**
- ❌ Hardcoded in source code
- ❌ Committed to git repositories  
- ❌ Included in JAR files
- ❌ Stored in version control

## ✅ Solution: Use Environment Variables

### Why Environment Variables?

1. **Highest Priority**: Spring Boot loads environment variables first
2. **Not in Git**: Environment variables are never committed
3. **Easy Updates**: Change passwords without rebuilding JAR
4. **Environment-Specific**: Different passwords per environment

### How It Works

The source code uses placeholders:

```properties
spring.mail.password=${SPRING_MAIL_PASSWORD:CHANGE_ME_PASSWORD}
```

- `${SPRING_MAIL_PASSWORD:...}` means: "Use environment variable `SPRING_MAIL_PASSWORD`, or default to `CHANGE_ME_PASSWORD`"
- In production, set `SPRING_MAIL_PASSWORD` environment variable
- The default `CHANGE_ME_PASSWORD` will never work, forcing you to set it properly

## Quick Reference

### Local Development

Set in your IDE run configuration:
```
SPRING_MAIL_USERNAME=your-email@bgu.ac.il
SPRING_MAIL_PASSWORD=your-password
```

### Production

Set in systemd service:
```ini
[Service]
Environment="SPRING_MAIL_PASSWORD=actual-password"
```

See `DEPLOYMENT.md` for detailed instructions.

## What Changed

1. ✅ Source code now uses `${SPRING_MAIL_PASSWORD:CHANGE_ME_PASSWORD}` instead of hardcoded password
2. ✅ `.gitignore` updated to exclude secret files
3. ✅ `DEPLOYMENT.md` created with deployment instructions
4. ✅ Production uses environment variables (already configured)

## Future Password Changes

When passwords change:

1. **Update systemd service** (no code changes needed):
   ```bash
   sudo systemctl edit semscan-api
   # Update Environment="SPRING_MAIL_PASSWORD=..."
   sudo systemctl daemon-reload
   sudo systemctl restart semscan-api
   ```

2. **Verify** in logs:
   ```bash
   sudo journalctl -u semscan-api | grep "SMTP Configuration"
   ```

No need to rebuild JAR or change source code!
