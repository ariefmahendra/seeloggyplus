# Developer Guide - Version 1.2.0

**Target Audience**: Developers working on SeeLoggyPlus  
**Version**: 1.2.0  
**Last Updated**: December 20, 2024

---

## 🎯 Quick Start for Developers

### Build and Run
```bash
# Clone and navigate
cd seeloggyplus

# Build
./gradlew clean build

# Run
./gradlew run

# Create distribution
./gradlew installDist
```

### Development Environment
- **Java**: OpenJDK 17+
- **JavaFX**: 21
- **Gradle**: 8.5
- **IDE**: IntelliJ IDEA recommended
- **Database**: SQLite 3.44+

---

## 🗂️ Architecture Overview

### Key Components

```
seeloggyplus/
├── src/main/java/com/seeloggyplus/
│   ├── controller/
│   │   ├── MainController.java          ← UI logic, panel toggle
│   │   ├── ParsingConfigController.java
│   │   └── RemoteFileDialogController.java
│   ├── model/
│   │   ├── LogEntry.java
│   │   ├── ParsingConfig.java
│   │   ├── RecentFile.java              ← Recent files model
│   │   └── SSHServer.java
│   ├── service/
│   │   ├── DatabaseService.java         ← Database CRUD (recent files)
│   │   ├── LogParserService.java        ← Log parsing logic
│   │   ├── SSHService.java
│   │   ├── JsonPrettifyService.java
│   │   └── XmlPrettifyService.java
│   └── util/
│       └── PreferencesManager.java      ← Preferences & recent files
├── src/main/resources/
│   ├── fxml/                            ← UI layouts
│   └── css/                             ← Styling
└── docs/                                ← Documentation
```

---

## 🔧 Recent Files Implementation

### Database Layer (DatabaseService.java)

**Recent Files Table Schema:**
```sql
CREATE TABLE recent_files (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_path TEXT NOT NULL UNIQUE,
    file_name TEXT NOT NULL,
    file_size INTEGER NOT NULL DEFAULT 0,
    is_remote INTEGER NOT NULL DEFAULT 0,
    remote_host TEXT,
    remote_port INTEGER,
    remote_user TEXT,
    remote_path TEXT,
    parsing_config_id INTEGER,
    last_opened TEXT NOT NULL,
    FOREIGN KEY(parsing_config_id) REFERENCES parsing_configs(id) ON DELETE SET NULL
);
```

**Key Methods:**
```java
// Save or update recent file
public void saveRecentFile(RecentFile recentFile) throws SQLException

// Get recent files (ordered by last_opened DESC)
public List<RecentFile> getAllRecentFiles(int limit) throws SQLException

// Delete specific file
public void deleteRecentFile(String filePath) throws SQLException

// Clear all recent files
public void clearRecentFiles() throws SQLException

// Helper: Get parsing config ID by name
private Integer getParsingConfigId(String name) throws SQLException
```

**Usage Example:**
```java
// Save recent file
RecentFile recentFile = new RecentFile(
    "/path/to/file.log", 
    "file.log", 
    1024000
);
recentFile.setParsingConfig(parsingConfig);
databaseService.saveRecentFile(recentFile);

// Load recent files
List<RecentFile> recentFiles = databaseService.getAllRecentFiles(20);

// Clear all
databaseService.clearRecentFiles();
```

---

### Service Layer (PreferencesManager.java)

**Key Methods:**
```java
// Load from database
private void loadRecentFiles()

// Add to database
public void addRecentFile(RecentFile file)

// Remove from database
public void removeRecentFile(RecentFile file)

// Clear database
public void clearRecentFiles()
```

**Usage Example:**
```java
PreferencesManager prefs = PreferencesManager.getInstance();

// Add recent file (automatically saves to DB)
RecentFile file = new RecentFile(path, name, size);
prefs.addRecentFile(file);

// Get all recent files
List<RecentFile> files = prefs.getRecentFiles();

// Clear
prefs.clearRecentFiles();
```

---

### UI Layer (MainController.java)

**Opening a Log File:**
```java
private void openLogFile(File file) {
    // 1. Parse file
    logParserService.parseFile(file, currentParsingConfig, callback);
    
    // 2. Display in table
    allLogEntries.setAll(entries);
    
    // 3. Save to recent files
    RecentFile recentFile = new RecentFile(
        file.getAbsolutePath(),
        file.getName(),
        file.length()
    );
    recentFile.setParsingConfig(currentParsingConfig);
    prefsManager.addRecentFile(recentFile);
    
    // 4. Refresh UI
    refreshRecentFilesList();
}
```

**Refreshing Recent Files List:**
```java
private void refreshRecentFilesList() {
    recentFilesListView.setItems(
        FXCollections.observableArrayList(
            prefsManager.getRecentFiles()
        )
    );
}
```

---

## 🎨 Panel Toggle Implementation

### Left Panel Toggle

**Method:**
```java
private void toggleLeftPanel() {
    boolean visible = showLeftPanelMenuItem.isSelected();
    
    if (!visible) {
        // Save current position
        double[] positions = horizontalSplitPane.getDividerPositions();
        if (positions.length > 0) {
            prefsManager.setLeftPanelWidth(
                positions[0] * horizontalSplitPane.getWidth()
            );
        }
        
        // Hide panel
        leftPanel.setVisible(false);
        leftPanel.setManaged(false);
        
        // Move divider to left (expand center)
        Platform.runLater(() -> {
            horizontalSplitPane.setDividerPositions(0.0);
        });
    } else {
        // Show panel
        leftPanel.setVisible(true);
        leftPanel.setManaged(true);
        
        // Restore position
        Platform.runLater(() -> {
            double savedWidth = prefsManager.getLeftPanelWidth();
            double totalWidth = horizontalSplitPane.getWidth();
            double position = (totalWidth > 0) 
                ? savedWidth / totalWidth 
                : 0.2;
            horizontalSplitPane.setDividerPositions(position);
        });
    }
    
    prefsManager.setLeftPanelVisible(visible);
}
```

**Key Concepts:**
- `setVisible(false)` - Hides panel visually
- `setManaged(false)` - Removes from layout calculations
- `Platform.runLater()` - Ensures layout is complete before adjustment
- `getDividerPositions()` - Returns positions as ratio (0.0-1.0)
- `setDividerPositions(0.0)` - Moves divider to far left
- `setDividerPositions(1.0)` - Moves divider to far right/bottom

### Bottom Panel Toggle

**Method:**
```java
private void toggleBottomPanel() {
    boolean visible = showBottomPanelMenuItem.isSelected();
    
    if (!visible) {
        // Save position
        double[] positions = verticalSplitPane.getDividerPositions();
        if (positions.length > 0) {
            prefsManager.setBottomPanelHeight(
                positions[0] * verticalSplitPane.getHeight()
            );
        }
        
        // Hide
        bottomPanel.setVisible(false);
        bottomPanel.setManaged(false);
        
        // Move divider to bottom
        Platform.runLater(() -> {
            verticalSplitPane.setDividerPositions(1.0);
        });
    } else {
        // Show and restore
        bottomPanel.setVisible(true);
        bottomPanel.setManaged(true);
        
        Platform.runLater(() -> {
            double savedHeight = prefsManager.getBottomPanelHeight();
            double totalHeight = verticalSplitPane.getHeight();
            double position = (totalHeight > 0)
                ? (totalHeight - savedHeight) / totalHeight
                : 0.75;
            verticalSplitPane.setDividerPositions(position);
        });
    }
    
    prefsManager.setBottomPanelVisible(visible);
}
```

---

## 🧪 Testing

### Manual Testing Checklist

**Recent Files:**
- [ ] Open log file → appears in recent list
- [ ] Click recent file → reopens correctly
- [ ] Clear recent → list empties
- [ ] Database contains correct entries

**Panel Toggle:**
- [ ] Hide left panel → divider moves left, center expands
- [ ] Show left panel → divider restores, panel appears
- [ ] Hide bottom panel → divider moves down, center expands
- [ ] Show bottom panel → divider restores, panel appears
- [ ] Restart app → positions restored correctly

**Database Queries:**
```sql
-- Check recent files
SELECT * FROM recent_files ORDER BY last_opened DESC;

-- Check with parsing config
SELECT rf.file_name, pc.name 
FROM recent_files rf 
LEFT JOIN parsing_configs pc ON rf.parsing_config_id = pc.id;

-- Count recent files
SELECT COUNT(*) FROM recent_files;

-- Clear test data
DELETE FROM recent_files;
```

---

## 🐛 Debugging

### Enable Debug Logging

Edit `src/main/resources/logback.xml`:
```xml
<logger name="com.seeloggyplus" level="DEBUG"/>
```

### Common Issues

**Issue: Recent file not appearing**
```java
// Check if file was saved
logger.debug("Saving recent file: {}", recentFile.getFileName());

// Check database
DatabaseStats stats = databaseService.getStatistics();
logger.debug("Recent files count: {}", stats.recentFileCount);

// Check list refresh
logger.debug("Refreshing list, current count: {}", 
    prefsManager.getRecentFiles().size());
```

**Issue: Divider not adjusting**
```java
// Check split pane size
logger.debug("Horizontal pane width: {}", 
    horizontalSplitPane.getWidth());
logger.debug("Current divider positions: {}", 
    Arrays.toString(horizontalSplitPane.getDividerPositions()));

// Check if Platform.runLater is needed
Platform.runLater(() -> {
    logger.debug("Setting divider after layout");
    horizontalSplitPane.setDividerPositions(0.2);
});
```

**Issue: Database error**
```java
try {
    databaseService.saveRecentFile(recentFile);
} catch (SQLException e) {
    logger.error("Database error", e);
    // Check:
    // 1. Database file permissions
    // 2. Schema is up to date
    // 3. Foreign key constraints
}
```

---

## 📦 Database Management

### Location
```
~/.seeloggyplus/seeloggyplus.db
```

### Backup
```bash
# Manual backup
cp ~/.seeloggyplus/seeloggyplus.db ~/.seeloggyplus/seeloggyplus.db.backup

# View schema
sqlite3 ~/.seeloggyplus/seeloggyplus.db .schema
```

### Inspect Data
```bash
sqlite3 ~/.seeloggyplus/seeloggyplus.db
```

```sql
-- List all tables
.tables

-- View recent files
SELECT * FROM recent_files;

-- View with parsing config
SELECT 
    rf.id,
    rf.file_name,
    rf.file_size,
    rf.last_opened,
    pc.name as parsing_config
FROM recent_files rf
LEFT JOIN parsing_configs pc ON rf.parsing_config_id = pc.id
ORDER BY rf.last_opened DESC;

-- Statistics
SELECT 
    COUNT(*) as total,
    SUM(file_size) as total_size,
    MAX(last_opened) as most_recent
FROM recent_files;
```

---

## 🚀 Adding New Features

### Adding a Recent Files Feature

**Example: Add "Favorite" flag to recent files**

1. **Update Database Schema** (DatabaseService.java):
```java
// In createTables()
String createRecentFilesTable = """
    CREATE TABLE IF NOT EXISTS recent_files (
        ...
        is_favorite INTEGER NOT NULL DEFAULT 0,
        ...
    )
""";
```

2. **Update Model** (RecentFile.java):
```java
public class RecentFile {
    private boolean favorite;
    
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
}
```

3. **Update Database Service** (DatabaseService.java):
```java
// Add method
public void setFavorite(String filePath, boolean favorite) throws SQLException {
    String sql = "UPDATE recent_files SET is_favorite = ? WHERE file_path = ?";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setInt(1, favorite ? 1 : 0);
        pstmt.setString(2, filePath);
        pstmt.executeUpdate();
    }
}

// Update mapper
private RecentFile mapResultSetToRecentFile(ResultSet rs) {
    // ...
    recentFile.setFavorite(rs.getInt("is_favorite") == 1);
    return recentFile;
}
```

4. **Update UI** (MainController.java):
```java
// Add context menu
ContextMenu contextMenu = new ContextMenu();
MenuItem favoriteItem = new MenuItem("Toggle Favorite");
favoriteItem.setOnAction(e -> {
    RecentFile file = recentFilesListView.getSelectionModel().getSelectedItem();
    if (file != null) {
        file.setFavorite(!file.isFavorite());
        try {
            databaseService.setFavorite(file.getFilePath(), file.isFavorite());
            refreshRecentFilesList();
        } catch (SQLException ex) {
            logger.error("Failed to toggle favorite", ex);
        }
    }
});
contextMenu.getItems().add(favoriteItem);
recentFilesListView.setContextMenu(contextMenu);
```

---

## 📚 Resources

### Documentation
- [DATABASE_INTEGRATION.md](DATABASE_INTEGRATION.md) - Database guide
- [RECENT_FILES_UPDATE.md](RECENT_FILES_UPDATE.md) - Feature details
- [CHANGELOG.md](CHANGELOG.md) - Version history

### Code References
- **Recent Files**: DatabaseService.java (lines 526-760)
- **Panel Toggle**: MainController.java (lines 713-835)
- **Preferences**: PreferencesManager.java (lines 176-337)

### External Documentation
- [JavaFX Documentation](https://openjfx.io/javadoc/21/)
- [SQLite Documentation](https://www.sqlite.org/docs.html)
- [Gradle User Guide](https://docs.gradle.org/)

---

## 🔐 Best Practices

### Database Operations
```java
// ✅ Good: Use try-with-resources
try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
    pstmt.setString(1, value);
    pstmt.executeUpdate();
}

// ❌ Bad: Manual resource management
PreparedStatement pstmt = conn.prepareStatement(sql);
pstmt.executeUpdate();
pstmt.close(); // Might not execute on exception
```

### UI Updates
```java
// ✅ Good: Use Platform.runLater for UI updates
Platform.runLater(() -> {
    horizontalSplitPane.setDividerPositions(0.2);
});

// ❌ Bad: Direct UI update from background thread
horizontalSplitPane.setDividerPositions(0.2); // Might throw exception
```

### Error Handling
```java
// ✅ Good: Specific exception handling
try {
    databaseService.saveRecentFile(file);
} catch (SQLException e) {
    logger.error("Failed to save recent file: {}", file.getFileName(), e);
    showError("Database Error", "Could not save file to recent list");
}

// ❌ Bad: Swallowing exceptions
try {
    databaseService.saveRecentFile(file);
} catch (Exception e) {
    // Silent failure
}
```

---

## 🎓 Learning Path

### For New Developers

1. **Understand the Architecture**
   - Read PROJECT_STRUCTURE.md
   - Review main components

2. **Study Recent Files Implementation**
   - DatabaseService.java → Database layer
   - PreferencesManager.java → Service layer
   - MainController.java → UI layer

3. **Practice with Small Changes**
   - Add logging to existing methods
   - Modify SQL queries
   - Update UI labels

4. **Build a Feature**
   - Follow "Adding New Features" guide
   - Test thoroughly
   - Document changes

---

## 💡 Pro Tips

1. **Use SQLite Browser** for database inspection
2. **Enable DEBUG logging** during development
3. **Test with large files** (>100MB) for performance
4. **Use Platform.runLater()** for divider adjustments
5. **Always use try-with-resources** for database operations
6. **Check foreign key constraints** when modifying schema
7. **Backup database** before schema changes
8. **Write tests** for critical paths

---

## 🆘 Getting Help

### Common Questions

**Q: Where is the database file?**  
A: `~/.seeloggyplus/seeloggyplus.db`

**Q: How to reset the database?**  
A: Delete the database file, it will be recreated on next run

**Q: Why isn't the divider adjusting?**  
A: Wrap adjustment in `Platform.runLater()` to ensure layout is complete

**Q: How to add a new column to recent_files?**  
A: Modify `createTables()` in DatabaseService.java and update mappers

**Q: Recent files not loading?**  
A: Check logs for SQL errors, verify database permissions

---

**Happy Coding!** 🚀

---

*Last Updated: December 20, 2024*  
*Version: 1.2.0*  
*For questions, see project documentation or open an issue*