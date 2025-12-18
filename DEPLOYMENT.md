# SemScan API Deployment Guide

## Security Best Practices

### ⚠️ NEVER Commit Passwords to Git

**Passwords and secrets should NEVER be:**
- Hardcoded in source code
- Committed to git repositories
- Included in JAR files
- Stored in version control

### ✅ Use Environment Variables Instead

Spring Boot configuration priority (highest to lowest):
1. **Environment Variables** ← USE THIS for secrets
2. Command line arguments
3. External config files (`/opt/semscan-api/config/application-global.properties`)
4. JAR-internal config files (for defaults only)

## Email Configuration

### Local Development

Set environment variables in your IDE run configuration or `.env` file:

```bash
SPRING_MAIL_USERNAME=your-email@bgu.ac.il
SPRING_MAIL_PASSWORD=your-password
SPRING_MAIL_FROM=SemScan_System_NoReply@bgu.ac.il
```

### Production Deployment

#### Option 1: Systemd Service (Recommended)

Edit the systemd service override file:

```bash
sudo mkdir -p /etc/systemd/system/semscan-api.service.d
sudo nano /etc/systemd/system/semscan-api.service.d/override.conf
```

Add:

```ini
[Service]
Environment="SPRING_MAIL_USERNAME=benariet@bgu.ac.il"
Environment="SPRING_MAIL_PASSWORD=your-actual-password"
Environment="SPRING_MAIL_FROM=SemScan_System_NoReply@bgu.ac.il"
```

Then reload and restart:

```bash
sudo systemctl daemon-reload
sudo systemctl restart semscan-api
```

#### Option 2: External Config File

Create `/opt/semscan-api/config/application-global.properties`:

```properties
spring.mail.username=benariet@bgu.ac.il
spring.mail.password=your-actual-password
spring.mail.from=SemScan_System_NoReply@bgu.ac.il
```

**Note:** This file should be protected:
```bash
sudo chmod 600 /opt/semscan-api/config/application-global.properties
sudo chown semscan-api:semscan-api /opt/semscan-api/config/application-global.properties
```

## Database Configuration

Same principles apply - use environment variables:

```ini
[Service]
Environment="SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/semscan_db?useSSL=false&serverTimezone=UTC"
Environment="SPRING_DATASOURCE_USERNAME=semscan_admin"
Environment="SPRING_DATASOURCE_PASSWORD=your-db-password"
```

## Verifying Configuration

After deployment, check that the correct values are loaded:

```bash
# Check systemd environment variables
sudo systemctl show semscan-api | grep -i "SPRING_MAIL"

# Check application logs
sudo journalctl -u semscan-api -n 100 | grep -i "SMTP Configuration\|EMAIL_CONFIG_LOADED"
```

The logs should show the masked password. If you see `CHANGE_ME_PASSWORD`, the environment variable wasn't set correctly.

## Changing Passwords

When passwords change:

1. **Update systemd service** (if using Option 1):
   ```bash
   sudo systemctl edit semscan-api
   # Update Environment="SPRING_MAIL_PASSWORD=..."
   sudo systemctl daemon-reload
   sudo systemctl restart semscan-api
   ```

2. **Update external config file** (if using Option 2):
   ```bash
   sudo nano /opt/semscan-api/config/application-global.properties
   # Update spring.mail.password=...
   sudo systemctl restart semscan-api
   ```

3. **Verify** the new password is loaded:
   ```bash
   sudo journalctl -u semscan-api -n 50 | grep "SMTP Configuration"
   ```

## Build Process

When building the JAR:

1. **Source code** uses placeholders: `${SPRING_MAIL_PASSWORD:CHANGE_ME_PASSWORD}`
2. **JAR file** contains only placeholders (safe to commit)
3. **Production** overrides via environment variables (never in JAR)

This ensures:
- ✅ No passwords in git history
- ✅ No passwords in JAR files
- ✅ Easy password updates without rebuilding
- ✅ Different passwords per environment

## Troubleshooting

### Password Not Updating

If the password isn't updating after changes:

1. **Check environment variable priority:**
   ```bash
   sudo systemctl show semscan-api | grep SPRING_MAIL_PASSWORD
   ```

2. **Check if external config exists:**
   ```bash
   ls -la /opt/semscan-api/config/application-global.properties
   ```

3. **Check application logs for loaded config:**
   ```bash
   sudo journalctl -u semscan-api | grep "EMAIL_CONFIG_LOADED"
   ```

4. **Restart service:**
   ```bash
   sudo systemctl restart semscan-api
   ```

### Environment Variable Not Loading

If environment variables aren't being picked up:

1. **Verify systemd override file exists:**
   ```bash
   cat /etc/systemd/system/semscan-api.service.d/override.conf
   ```

2. **Reload systemd:**
   ```bash
   sudo systemctl daemon-reload
   ```

3. **Check service status:**
   ```bash
   sudo systemctl status semscan-api
   ```
