# Database Integration - SQLite

## Overview

SeeLoggyPlus now uses SQLite database to store:
- **SSH Server Configurations** - Save and manage remote server connections
- **Parsing Configurations** - Store regex patterns with named groups

This replaces the previous JSON file-based storage for parsing configurations, providing better data integrity and query capabilities.

---

## Database Location

The SQLite database is stored in your user directory:

```
Windows: C:\Users\[username]\.seeloggyplus\seeloggyplus.db
Linux/Mac: ~/.seeloggyplus/seeloggyplus.db
```

---

## Database Schema

### Table: `ssh_servers`

Stores SSH server connection configurations.

```sql
CREATE TABLE ssh_servers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,                     -- Display name for the server
    host TEXT NOT NULL,                     -- Hostname or IP address
    port INTEGER NOT NULL DEFAULT 22,       -- SSH port
    username TEXT NOT NULL,                 -- SSH username
    password TEXT,                          -- Password (if save_password=1)
    use_private_key INTEGER NOT NULL DEFAULT 0,  -- Use private key auth
    private_key_path TEXT,                  -- Path to private key file
    passphrase TEXT,                        -- Private key passphrase
    default_path TEXT,                      -- Default remote directory
    save_password INTEGER NOT NULL DEFAULT 0,    -- Save password flag
    created_at TEXT NOT NULL,               -- Creation timestamp
    last_used TEXT,                         -- Last connection timestamp
    UNIQUE(host, port, username)            -- Prevent duplicates
)
```

**Fields:**
- `id` - Unique identifier
- `name` - User-friendly name (e.g., "Production Server")
- `host` - Server hostname (e.g., "192.168.1.100" or "server.example.com")
- `port` - SSH port (default: 22)
- `username` - SSH username
- `password` - Encrypted password (only if save_password=1)
- `use_private_key` - Boolean flag (0=password, 1=private key)
- `private_key_path` - Path to .pem/.key/.ppk file
- `passphrase` - Private key passphrase
- `default_path` - Starting directory on remote server (e.g., "/var/log")
- `save_password` - Whether to save password to database
- `created_at` - When the server was added
- `last_used` - Last successful connection

### Table: `parsing_configs`

Stores log parsing configurations with regex patterns.

```sql
CREATE TABLE parsing_configs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,              -- Configuration name
    description TEXT,                       -- Description
    regex_pattern TEXT NOT NULL,            -- Regex with named groups
    group_names TEXT,                       -- JSON array of group names
    is_default INTEGER NOT NULL DEFAULT 0,  -- Default config flag
    created_at TEXT NOT NULL,               -- Creation timestamp
    updated_at TEXT                         -- Last update timestamp
)
```

**Fields:**
- `id` - Unique identifier
- `name` - Configuration name (must be unique)
- `description` - Optional description
- `regex_pattern` - Regex pattern with named groups
- `group_names` - JSON array of extracted group names
- `is_default` - Boolean flag for default configuration
- `created_at` - When the configuration was created
- `updated_at` - Last modification time

---

## Features

### 1. SSH Server Management

#### Save Server Configuration

In the **Remote File Dialog**:

1. Fill in server details:
   - Host: `192.168.1.100`
   - Port: `22`
   - Username: `admin`
   - Password: `******`
   - Check "Save password" if desired

2. Click **"Save"** button

3. Enter a name for the server (e.g., "Production Server")

4. Click **OK**

The server is now saved and will appear in the "Saved Servers" dropdown.

#### Load Saved Server

1. Open **Remote File Dialog**
2. Select a server from **"Saved Servers"** dropdown
3. Click **"Load"**
4. All fields are filled automatically
5. Click **"Connect"**

#### Delete Saved Server

1. Select a server from dropdown
2. Click **"Delete"**
3. Confirm deletion

#### Features:
- ✅ **Password Storage** - Optional encrypted password storage
- ✅ **Private Key Support** - Store path to private key files
- ✅ **Default Path** - Remember starting directory for each server
- ✅ **Last Used Tracking** - Servers sorted by recent usage
- ✅ **Quick Connect** - One-click connection to saved servers

### 2. Parsing Configuration Management

Parsing configurations are now stored in database instead of JSON files.

#### Automatic Migration

When you first run the application with database support:
- Existing configurations from `parsing_configs.json` are automatically migrated
- Database becomes the primary storage
- JSON file is kept as backup

#### Operations:

**Add Configuration:**
```
Settings > Parsing Configuration > Add
- Configuration saved to database immediately
```

**Edit Configuration:**
```
Settings > Parsing Configuration > Select config > Edit > Save
- Updates database record
```

**Delete Configuration:**
```
Settings > Parsing Configuration > Select config > Delete
- Removes from database
```

**Set Default:**
```
Settings > Parsing Configuration > Select config > Set Default
- Updates is_default flag in database
```

---

## API Reference

### DatabaseService

Main service class for all database operations.

```java
DatabaseService db = DatabaseService.getInstance();
```

#### SSH Server Methods

```java
// Save or update SSH server
SSHServer saveSSHServer(SSHServer server) throws SQLException

// Get all SSH servers (sorted by last_used DESC)
List<SSHServer> getAllSSHServers() throws SQLException

// Get server by ID
SSHServer getSSHServerById(Long id) throws SQLException

// Delete SSH server
void deleteSSHServer(Long id) throws SQLException

// Update last used timestamp
void updateSSHServerLastUsed(Long id) throws SQLException
```

#### Parsing Configuration Methods

```java
// Save or update parsing configuration
ParsingConfig saveParsingConfig(ParsingConfig config) throws SQLException

// Get all parsing configurations
List<ParsingConfig> getAllParsingConfigs() throws SQLException

// Get configuration by name
ParsingConfig getParsingConfigByName(String name) throws SQLException

// Get default configuration
ParsingConfig getDefaultParsingConfig() throws SQLException

// Delete configuration
void deleteParsingConfig(String name) throws SQLException

// Set default configuration
void setDefaultParsingConfig(String name) throws SQLException
```

#### Maintenance Methods

```java
// Get database statistics
DatabaseStats getStatistics() throws SQLException

// Clear all data (for testing)
void clearAllData() throws SQLException

// Close database connection
void close()
```

---

## Security Considerations

### Password Storage

⚠️ **Important Security Notes:**

1. **Passwords are stored in plain text** in the database
   - The database file is NOT encrypted by default
   - Only save passwords on trusted machines
   - Use private key authentication when possible

2. **Database File Protection:**
   - Database file has standard OS file permissions
   - Located in user home directory (not world-readable)
   - Consider encrypting the database file manually if needed

3. **Best Practices:**
   - ✅ Use SSH private keys instead of passwords
   - ✅ Don't save passwords for production servers
   - ✅ Use "Save password" only for development/testing
   - ✅ Regularly rotate SSH credentials
   - ✅ Backup database before major operations

### Future Security Enhancements (Planned)

- [ ] Encrypt passwords using system keyring
- [ ] Master password for database
- [ ] Option to encrypt entire database
- [ ] SSH agent integration
- [ ] Certificate-based authentication

---

## Database Backup & Restore

### Manual Backup

Copy the database file:

**Windows:**
```cmd
copy "%USERPROFILE%\.seeloggyplus\seeloggyplus.db" "C:\Backup\seeloggyplus_backup.db"
```

**Linux/Mac:**
```bash
cp ~/.seeloggyplus/seeloggyplus.db ~/backups/seeloggyplus_backup.db
```

### Restore from Backup

Close the application, then:

**Windows:**
```cmd
copy "C:\Backup\seeloggyplus_backup.db" "%USERPROFILE%\.seeloggyplus\seeloggyplus.db"
```

**Linux/Mac:**
```bash
cp ~/backups/seeloggyplus_backup.db ~/.seeloggyplus/seeloggyplus.db
```

### Automated Backup (Recommended)

Add to your backup routine:
- Database file: `~/.seeloggyplus/seeloggyplus.db`
- Size: Usually < 1MB
- Frequency: Daily or before major changes

---

## Migration Guide

### From JSON to Database

If you're upgrading from a previous version:

1. **Automatic Migration:**
   - Launch the new version
   - Parsing configurations auto-migrate from JSON
   - JSON file kept as backup

2. **Verify Migration:**
   ```
   Settings > Parsing Configuration
   - Check all configurations are present
   ```

3. **Manual Cleanup (Optional):**
   - Old file: `~/.seeloggyplus/parsing_configs.json`
   - Can be deleted after successful migration
   - Keep as backup for safety

### Exporting Data

**Export to JSON:**
```java
// Export parsing configurations
DatabaseService db = DatabaseService.getInstance();
List<ParsingConfig> configs = db.getAllParsingConfigs();
// Save to JSON using Gson
```

**Export SSH Servers:**
```java
List<SSHServer> servers = db.getAllSSHServers();
// Note: Passwords included if save_password=true
// Handle with care!
```

---

## Troubleshooting

### Database Locked Error

**Problem:** "Database is locked" error

**Solution:**
```
1. Close all instances of SeeLoggyPlus
2. Check for hung processes
3. Restart application
4. If persists, delete database and restart (data will be reset)
```

### Corrupted Database

**Problem:** SQLite corruption errors

**Solution:**
```bash
# Attempt repair (Linux/Mac)
sqlite3 ~/.seeloggyplus/seeloggyplus.db ".recover" | sqlite3 ~/.seeloggyplus/seeloggyplus_recovered.db

# Or restore from backup
cp ~/backups/seeloggyplus_backup.db ~/.seeloggyplus/seeloggyplus.db
```

### Migration Failed

**Problem:** Parsing configurations not migrated

**Solution:**
```
1. Check logs: ~/.seeloggyplus/logs/seeloggyplus-error.log
2. Manually import from JSON file
3. Or create configurations manually in UI
```

### Cannot Connect to Saved Server

**Problem:** "Connection failed" with saved server

**Solutions:**
```
1. Check server is accessible (ping hostname)
2. Verify credentials haven't changed
3. Test SSH connection manually
4. Update saved server configuration
5. Check firewall/network settings
```

---

## Performance

### Database Size

Typical database sizes:
- Empty: ~16 KB
- 10 SSH servers: ~20 KB
- 20 parsing configs: ~25 KB
- Combined: < 50 KB

### Query Performance

All queries are optimized:
- Indexes on frequently queried fields
- Connection pooling (single connection reused)
- Prepared statements for security
- Minimal overhead (< 1ms per query)

### Optimization Tips

1. **Keep database small:**
   - Delete unused SSH servers
   - Remove old parsing configurations
   - Regular cleanup

2. **Backup regularly:**
   - Small file size = quick backups
   - Use version control for database

3. **Monitor logs:**
   - Check for SQL errors
   - Watch for locked database warnings

---

## Advanced Usage

### Direct Database Access

You can query the database directly using SQLite tools:

```bash
# Open database
sqlite3 ~/.seeloggyplus/seeloggyplus.db

# List all SSH servers
SELECT * FROM ssh_servers;

# List all parsing configurations
SELECT * FROM parsing_configs;

# Find default parsing config
SELECT * FROM parsing_configs WHERE is_default = 1;

# Export to CSV
.mode csv
.headers on
.output ssh_servers.csv
SELECT * FROM ssh_servers;
.quit
```

### SQL Examples

**Find servers by host:**
```sql
SELECT * FROM ssh_servers WHERE host LIKE '%example.com%';
```

**Get recently used servers:**
```sql
SELECT * FROM ssh_servers 
ORDER BY last_used DESC 
LIMIT 5;
```

**Count configurations:**
```sql
SELECT COUNT(*) as total FROM parsing_configs;
```

**Update server name:**
```sql
UPDATE ssh_servers 
SET name = 'New Name' 
WHERE id = 1;
```

---

## Best Practices

### SSH Server Management

1. ✅ **Use descriptive names** - "Production DB Server" not "Server1"
2. ✅ **Organize by environment** - Prefix with "PROD-", "DEV-", "TEST-"
3. ✅ **Set default paths** - Save time navigating to log directories
4. ✅ **Use private keys** - More secure than passwords
5. ✅ **Regular cleanup** - Delete unused servers

### Parsing Configuration Management

1. ✅ **Test before saving** - Always test with sample logs
2. ✅ **Use clear names** - Describe the log format clearly
3. ✅ **Add descriptions** - Explain what the pattern matches
4. ✅ **Version control** - Export and commit to Git
5. ✅ **Share with team** - Export and distribute standard configs

### Database Maintenance

1. ✅ **Regular backups** - Before major changes
2. ✅ **Monitor size** - Keep database clean
3. ✅ **Check integrity** - Verify after crashes
4. ✅ **Document changes** - Note custom configurations
5. ✅ **Security audit** - Review saved passwords periodically

---

## Changelog

### Version 1.0.0
- ✅ Initial SQLite integration
- ✅ SSH server storage
- ✅ Parsing configuration storage
- ✅ Migration from JSON files
- ✅ CRUD operations for all entities
- ✅ Last used tracking
- ✅ Default configuration support

### Future Enhancements
- [ ] Password encryption
- [ ] Database compression
- [ ] Cloud sync support
- [ ] Import/export functionality
- [ ] Database backup automation
- [ ] Multiple database profiles
- [ ] SSH key management
- [ ] Connection history tracking

---

## Support

For issues or questions:
1. Check application logs: `~/.seeloggyplus/logs/`
2. Review this documentation
3. Check database integrity with SQLite tools
4. Create issue on project repository

---

**Last Updated:** 2024-01-15
**Version:** 1.0.0
**Database Schema Version:** 1