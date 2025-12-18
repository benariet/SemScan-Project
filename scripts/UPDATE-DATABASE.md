# Database Update Guide

## Do You Need This?

**You ONLY need this if:**
- You have an **existing database** that was created **before** the `app_config` table was added
- Your database is missing the `app_config` table

**You DON'T need this if:**
- You're creating a **fresh database** from `database-schema-ddl.sql` (it's already included)
- Your database already has the `app_config` table

## What to Run

If you need to update an existing database:
```bash
mysql -u your_username -p semscan_db < migration-app-config.sql
```

Or if you're using MySQL Workbench or another client, just execute the contents of `migration-app-config.sql`.

## Why This is Needed (for existing databases)

If your database was created before `app_config` was added, you need to:
1. **Create `app_config` table** - Stores all application configuration settings
2. **Create indexes safely** - Uses MySQL-compatible syntax to check if indexes exist before creating them (prevents errors on re-run)
3. **Insert default config values** - Sets up initial configuration for email, network, and mobile app settings
4. **Idempotent** - Safe to run multiple times (won't create duplicates or fail if already exists)

## What It Does

1. Creates `app_config` table with columns for:
   - Configuration keys and values
   - Config types (STRING, INTEGER, BOOLEAN, JSON)
   - Target system (MOBILE, API, BOTH)
   - Categories and descriptions

2. Creates indexes on:
   - `target_system` - For filtering by system type
   - `category` - For grouping configs
   - `config_key` - For fast lookups

3. Inserts default configuration values for:
   - Server URLs
   - Email settings (from name, reply-to, BCC list)
   - Network timeouts
   - Attendance windows
   - Waiting list approval windows
   - UI settings (toast durations)
   - Export settings

## When to Run

- **First time setup** - After creating the database schema
- **After pulling new code** - If new config keys were added
- **When config structure changes** - To ensure your database has the latest structure

## Notes

- Uses `INSERT IGNORE` and `ON DUPLICATE KEY UPDATE` - Won't break if run multiple times
- Index creation checks `information_schema` first - MySQL-compatible (doesn't use `CREATE INDEX IF NOT EXISTS`)
- All default values can be changed later via the `app_config` table
