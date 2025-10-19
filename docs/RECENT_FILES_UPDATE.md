# Recent Files Database Integration & Improved Panel Toggling

## Version 1.2.0 - Update Summary

This document describes the new features and improvements implemented in version 1.2.0 of SeeLoggyPlus.

---

## 🎯 New Features

### 1. Recent Files Database Integration

Recent files are now stored in SQLite database instead of JSON files, providing better performance, data integrity, and integration with the existing database infrastructure.

#### Key Changes:

- **Database Table**: Added `recent_files` table to store file history
- **Automatic Parsing**: When opening a log file, it's automatically parsed and entries are displayed in the dashboard
- **Persistent Storage**: Recent files are saved to database immediately after opening
- **Parsing Config Association**: Each recent file stores its associated parsing configuration

#### Database Schema:

```sql
CREATE TABLE IF NOT EXISTS recent_files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_path TEXT NOT NULL,
    file_name TEXT NOT NULL,
    file_size INTEGER NOT NULL DEFAULT 0,
    is_remote INTEGER NOT NULL DEFAULT 0,
    remote_host TEXT,
    remote_port INTEGER,
    remote_user TEXT,
    remote_path TEXT,
    parsing_config_id INTEGER,
    last_opened TEXT NOT NULL,
    UNIQUE(file_path),
    FOREIGN KEY(parsing_config_id) REFERENCES parsing_configs(id) ON DELETE SET NULL
)
```

#### Benefits:

1. **Better Performance**: Database queries are faster than JSON file I/O
2. **Data Integrity**: Foreign key constraints ensure parsing config references are valid
3. **Unified Storage**: All configuration data in one place
4. **Automatic Cleanup**: Easy to manage old entries with SQL queries
5. **Scalability**: Can handle thousands of recent files efficiently

---

### 2. Improved Panel Toggle Behavior

The hide/show panel functionality has been significantly improved to provide a better user experience.

#### Before:
- Panels would hide/show but split pane dividers remained in the same position
- Dead space remained visible when panels were hidden
- User had to manually adjust dividers after showing/hiding panels

#### After:
- **Automatic Divider Adjustment**: When hiding a panel, the split pane divider automatically adjusts to give maximum space to the visible content
- **Position Restoration**: When showing a panel, the previous divider position is restored
- **Smooth Transitions**: Panel visibility changes are smooth and natural
- **State Persistence**: Divider positions are saved and restored across application restarts

#### Implementation Details:

**Left Panel Toggle:**
- Hiding: Divider moves to 0.0 (far left), expanding center panel to full width
- Showing: Divider restores to saved position (default: 20% of window width)

**Bottom Panel Toggle:**
- Hiding: Divider moves to 1.0 (bottom), expanding center panel to full height
- Showing: Divider restores to saved position (default: 75% of window height)

---

## 🔧 Technical Implementation

### DatabaseService Updates

Added new methods for recent files management:

```java
// Save or update recent file
public void saveRecentFile(RecentFile recentFile) throws SQLException

// Get all recent files (ordered by last opened)
public List<RecentFile> getAllRecentFiles(int limit) throws SQLException

// Delete specific recent file
public void deleteRecentFile(String filePath) throws SQLException

// Clear all recent files
public void clearRecentFiles() throws SQLException
```

### PreferencesManager Updates

Modified to use DatabaseService instead of JSON file storage:

```java
// Load recent files from database
private void loadRecentFiles()

// Add recent file (saves to database)
public void addRecentFile(RecentFile file)

// Remove recent file (deletes from database)
public void removeRecentFile(RecentFile file)

// Clear all recent files (clears database table)
public void clearRecentFiles()
```

### MainController Updates

Enhanced panel toggle methods:

```java
// Toggle left panel with divider adjustment
private void toggleLeftPanel()

// Toggle bottom panel with divider adjustment
private void toggleBottomPanel()

// Restore panel visibility and positions on startup
private void restorePanelVisibility()
```

---

## 📊 Database Statistics

The `DatabaseStats` class now includes recent files count:

```java
public static class DatabaseStats {
    public int sshServerCount;
    public int parsingConfigCount;
    public int recentFileCount;
}
```

---

## 🚀 Usage

### Opening and Parsing Log Files

1. **Open File**: File → Open Local File (or Ctrl+O)
2. **Automatic Parsing**: The file is parsed using the default parsing configuration
3. **Dashboard Display**: Parsed log entries appear in the main table
4. **Recent Files**: File is automatically added to recent files list

### Viewing Recent Files

- Recent files appear in the left panel
- Click any recent file to reopen it
- Each entry shows:
  - File name
  - File size
  - Last opened date
  - Full path (tooltip)

### Clearing Recent Files

1. Click "Clear Recent" button in the left panel
2. Confirm the action
3. All recent files are removed from the database

### Panel Toggling

**Using Menu:**
- View → Show Left Panel (Ctrl+Shift+L)
- View → Show Bottom Panel (Ctrl+Shift+B)

**Behavior:**
- Panels hide/show smoothly with automatic divider adjustment
- Previous positions are restored when showing panels
- State persists across application restarts

---

## 🔄 Migration Notes

### From Version 1.1.x to 1.2.0

1. **Automatic Migration**: Recent files from JSON will be imported automatically (future enhancement)
2. **Database Schema**: New `recent_files` table is created automatically on first run
3. **No Data Loss**: Old JSON files are preserved but no longer used
4. **Backward Compatible**: Previous versions' data remains accessible

### Manual Migration (if needed)

If you have important recent files data from previous versions:

1. Locate `~/.seeloggyplus/recent_files.json`
2. The application will continue to work without migration
3. New files will be saved to database
4. Old entries can be manually re-opened to add them to database

---

## 🐛 Bug Fixes

1. **Panel State Persistence**: Fixed issue where panel visibility state wasn't properly restored on startup
2. **Divider Position Reset**: Fixed divider positions resetting to default after hide/show
3. **Dead Space**: Eliminated dead space when panels are hidden
4. **Menu Item Sync**: Menu checkboxes now properly reflect panel visibility state

---

## 🎨 UI Improvements

1. **Responsive Layout**: Split panes now properly adjust to content
2. **Smooth Transitions**: Panel visibility changes are more fluid
3. **Visual Consistency**: Menu items stay in sync with panel state
4. **Better Space Usage**: Hidden panels don't waste screen space

---

## 📝 Code Quality

1. **Database Abstraction**: All database operations centralized in DatabaseService
2. **Error Handling**: Proper SQL exception handling throughout
3. **Logging**: Comprehensive logging for debugging
4. **Documentation**: Inline comments and JavaDoc updated

---

## 🔮 Future Enhancements

Planned improvements for future versions:

1. **Search Recent Files**: Add search/filter functionality to recent files list
2. **Favorites**: Mark frequently used files as favorites
3. **File Groups**: Organize recent files into groups/categories
4. **Statistics**: Show file open count and patterns
5. **Export/Import**: Export recent files list for backup
6. **Automatic Cleanup**: Remove files that no longer exist on disk
7. **JSON Migration**: Automatic import of recent files from old JSON format

---

## 📚 Related Documentation

- [DATABASE_INTEGRATION.md](DATABASE_INTEGRATION.md) - Complete database documentation
- [CHANGELOG.md](CHANGELOG.md) - Version history
- [QUICKSTART.md](QUICKSTART.md) - Getting started guide
- [README.md](README.md) - Main documentation

---

## 🤝 Contributing

To contribute to these features:

1. Recent files logic: `DatabaseService.java` and `PreferencesManager.java`
2. Panel toggling: `MainController.java` (toggleLeftPanel/toggleBottomPanel methods)
3. UI definitions: `MainView.fxml`

---

## ❓ Troubleshooting

### Recent Files Not Appearing

1. Check database file exists: `~/.seeloggyplus/seeloggyplus.db`
2. Check logs for SQL errors
3. Try clearing and reopening files
4. Verify file permissions on database file

### Panel Toggle Not Working

1. Check if split pane has proper size (not zero width/height)
2. Verify scene is fully initialized before toggling
3. Check console for exceptions
4. Reset preferences: `~/.seeloggyplus/` folder

### Divider Position Not Restored

1. Ensure preferences are saved properly
2. Check if divider positions are within valid range (0.0-1.0)
3. Verify window has proper dimensions when restoring
4. Try resetting preferences to defaults

---

**Last Updated**: December 2024  
**Version**: 1.2.0  
**Status**: ✅ Implemented and Tested