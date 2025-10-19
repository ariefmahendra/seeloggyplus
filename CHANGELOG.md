# Changelog

All notable changes to SeeLoggyPlus will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2024-12-20

### Added
- **Recent Files Database Integration** - Recent files now stored in SQLite database
  - New table: `recent_files` for persistent file history
  - Automatic parsing and display when opening log files
  - File name appears in recent files list immediately after opening
  - Associated parsing configuration stored with each file
  - Support for both local and remote files in history
  - Ordered by last opened timestamp (most recent first)
  - Database CRUD operations through DatabaseService
- **Improved Panel Toggle Behavior** - Smart split pane adjustment when hiding/showing panels
  - Automatic divider position adjustment when hiding panels
  - Left panel: Divider moves to 0.0 when hidden, expanding center panel to full width
  - Bottom panel: Divider moves to 1.0 when hidden, expanding center panel to full height
  - Position restoration when showing panels back
  - Smooth transitions with Platform.runLater() for better UX
  - State persistence across application restarts
  - Menu checkbox items sync with panel visibility state
- **Enhanced DatabaseService** - Extended with recent files management
  - `saveRecentFile()` - Save or update recent file entry
  - `getAllRecentFiles(limit)` - Get recent files with limit
  - `deleteRecentFile(filePath)` - Remove specific file from history
  - `clearRecentFiles()` - Clear all recent files
  - `getParsingConfigId()` - Helper for foreign key resolution
  - Foreign key constraint to parsing_configs table
  - Automatic last_opened timestamp tracking

### Changed
- **PreferencesManager** - Migrated from JSON file to database for recent files
  - `loadRecentFiles()` now loads from database via DatabaseService
  - `addRecentFile()` saves to database and reloads list
  - `removeRecentFile()` deletes from database
  - `clearRecentFiles()` clears database table
  - `saveRecentFiles()` deprecated (no longer needed)
  - Old JSON backup files preserved but not used
- **MainController** - Enhanced panel toggle logic
  - `toggleLeftPanel()` now adjusts horizontal split pane divider
  - `toggleBottomPanel()` now adjusts vertical split pane divider
  - `restorePanelVisibility()` restores divider positions on startup
  - Stores divider positions before hiding panels
  - Calculates optimal divider positions when showing panels
  - Uses Platform.runLater() for smooth UI updates
- **Database Statistics** - Updated to include recent files count
  - `DatabaseStats.recentFileCount` added
  - Statistics query extended to count recent_files table
  - toString() method updated with new field

### Fixed
- **Panel State Persistence** - Menu checkboxes now properly reflect panel visibility on startup
- **Dead Space Issue** - Eliminated wasted space when panels are hidden
- **Divider Position Reset** - Dividers no longer reset to default after hide/show operations
- **Split Pane Layout** - Center panel now properly expands when side panels are hidden

### UI Improvements
- More responsive layout when toggling panels
- Smoother transitions between panel states
- Better space utilization when panels are hidden
- Visual consistency between menu items and panel state

### Documentation
- Added `RECENT_FILES_UPDATE.md` (293 lines) - Comprehensive guide for new features
- Updated database schema documentation
- Added migration notes from version 1.1.x
- Expanded troubleshooting section

### Technical Details
- Recent files table with UNIQUE constraint on file_path
- Foreign key constraint with ON DELETE SET NULL for parsing_config_id
- LocalDateTime stored as TEXT in ISO format
- Split pane divider positions calculated relative to container size
- Divider position preferences saved in double format (0.0-1.0 range)

## [1.1.1] - 2024-01-15

### Fixed
- **LocalDateTime Serialization Error** - Added custom TypeAdapter for Gson to handle Java 17+ LocalDateTime serialization
  - Fixed `JsonIOException: Failed making field 'java.time.LocalDateTime#date' accessible`
  - Recent files now load correctly without errors
  - Custom adapter handles null values properly
- **SQLite getGeneratedKeys() Error** - Changed to use SQLite-specific `last_insert_rowid()` function
  - Fixed `SQLFeatureNotSupportedException: not implemented by SQLite JDBC driver`
  - SSH servers now save correctly to database
  - Auto-generated IDs properly retrieved

### Technical Details
- Modified `PreferencesManager.java` to register LocalDateTime TypeAdapter with Gson
- Modified `DatabaseService.insertSSHServer()` to use `SELECT last_insert_rowid()` instead of `getGeneratedKeys()`
- Both fixes ensure compatibility with Java 17+ and SQLite JDBC driver limitations

## [1.1.0] - 2024-01-15

### Added
- **SQLite Database Integration** - Replaced JSON file storage with SQLite database
  - New table: `ssh_servers` for storing SSH server configurations
  - New table: `parsing_configs` for storing log parsing patterns
  - Database auto-created at `~/.seeloggyplus/seeloggyplus.db`
- **SSH Server Management** - Save and manage remote server connections
  - Saved servers dropdown in Remote File Dialog
  - Save/Load/Delete buttons for server management
  - Optional password storage with checkbox
  - Last used tracking for server sorting
  - Support for both password and private key authentication
- **Enhanced Remote File Dialog UI**
  - ComboBox for quick server selection
  - Server configuration persistence
  - One-click connect to saved servers
  - Default path storage per server
- **Database Service** - Complete CRUD operations for SSH servers and parsing configs
  - `DatabaseService.java` with 540+ lines
  - Full transaction support
  - Prepared statements for security
  - Automatic migration from JSON to database

### Changed
- **Parsing Configuration Storage** - Now uses database instead of JSON files
  - Auto-migration from `parsing_configs.json` to database
  - JSON file kept as backup
  - Improved query performance with indexes
- **PreferencesManager** - Updated to use database for parsing configs
  - Backward compatible with existing JSON files
  - Seamless migration on first run

### UI Improvements
- **Parsing Config Dialog** - Increased size to 1000x800 (min 900x750)
  - Bottom action buttons now fully visible
  - Better spacing and layout
  - Compact tips section
- **Remote File Dialog** - Increased size to 900x700 (min 850x650)
  - File list table fully visible
  - Proper column widths
  - Better scrolling behavior
  - Tips section optimized

### Dependencies
- Added `org.xerial:sqlite-jdbc:3.44.1.0`

## [1.0.0] - 2024-01-15

### Added
- Initial release of SeeLoggyPlus
- High-performance log viewer with JavaFX 21
- Menu bar with File, View, Settings, and Help menus
- Main dashboard with three configurable panels:
  - Left panel: Recent files list
  - Center panel: Log table viewer with dynamic columns
  - Bottom panel: Log detail view with action buttons
- File access capabilities:
  - Local file browser for opening log files from disk
  - Remote file access via SSH with password or private key authentication
  - Recent files tracking with automatic history management
- Advanced parsing configuration system:
  - Regex pattern editor with named groups support
  - Pattern validation and testing
  - Preview parsed fields before saving
  - Multiple parsing configurations management
  - Default configurations for common log formats (Standard, Apache, JSON)
  - Automatic column generation from named groups
- Search functionality:
  - Text-based search (case-sensitive and case-insensitive)
  - Regex-based search with pattern matching
  - Real-time filtering of log entries
  - Search result count display
- JSON and XML prettification:
  - Auto-detect JSON/XML in log messages
  - Format and indent for better readability
  - Minify capability
  - Extract embedded JSON/XML from text
- Performance optimizations:
  - Parallel parsing for large files using multi-threading
  - Batch processing (1000 lines per batch)
  - Configurable buffer size (default 32KB)
  - Progress indicator for file loading
  - Virtual scrolling in table view
  - Lazy loading for optimal memory usage
- Panel management:
  - Show/hide left panel (Recent Files)
  - Show/hide bottom panel (Log Detail)
  - Resizable split panes with position persistence
  - Panel state saved between sessions
- User preferences management:
  - Window size and position persistence
  - Panel visibility and dimensions
  - Default parsing configuration
  - Maximum recent files setting
  - Font size customization
  - Theme selection (light)
  - Performance settings (parallel parsing, buffer size)
- SSH service features:
  - Connect to remote servers
  - Browse remote directories
  - List files with metadata (size, type, modified time)
  - Download files
  - Execute remote commands
  - Support for both password and private key authentication
- Modern UI design:
  - Clean and intuitive interface
  - Responsive layout
  - Custom CSS styling
  - Consistent color scheme
  - Status bar with progress indicator
  - Toolbar with quick actions
- Application logging:
  - Logback integration
  - Rotating log files (daily)
  - Separate error log file
  - Configurable log levels
  - Log retention (30 days default)
- Build system:
  - Gradle 8.5 build automation
  - JavaFX plugin integration
  - Fat JAR generation
  - Gradle Wrapper included
  - Multi-platform support (Windows, Linux, Mac)

### Dependencies
- JavaFX 21 - UI framework
- JSch 0.1.55 - SSH connectivity
- Gson 2.10.1 - JSON processing
- Jackson 2.15.2 - JSON processing (alternative)
- Logback 1.4.11 - Logging framework
- Commons IO 2.13.0 - File utilities
- Commons Lang3 3.13.0 - Language utilities
- ControlsFX 11.1.2 - Advanced UI components
- RichTextFX 0.11.2 - Rich text editor

### Documentation
- Comprehensive README.md with feature overview
- Quick Start Guide (QUICKSTART.md)
- Project Structure documentation (PROJECT_STRUCTURE.md)
- Inline code documentation with Javadoc
- Sample regex patterns for common log formats
- Troubleshooting guide

### Documentation
- Added `DATABASE_INTEGRATION.md` (561 lines) - Complete database guide
- Added `UPDATE_SUMMARY.md` (505 lines) - Feature comparison and migration guide
- Added `BUGFIXES.md` (315 lines) - Detailed bug fix documentation

## [Unreleased]

### Planned Features
- [ ] Export filtered logs to file (CSV, JSON, text)
- [ ] Bookmark important log entries
- [ ] Multi-tab support for multiple files
- [ ] Syntax highlighting for log levels
- [ ] Custom color schemes per log level
- [ ] Log statistics and analytics dashboard
- [ ] Real-time log tailing (follow mode)
- [ ] Plugin system for custom parsers
- [ ] Database storage for very large files
- [ ] Log correlation by request ID or trace ID
- [ ] Dark theme support
- [ ] Keyboard shortcuts configuration
- [ ] Column sorting and filtering
- [ ] Context menu for table rows
- [ ] Find and replace in detail view
- [ ] Regular expression library/snippets
- [ ] File comparison mode
- [ ] Timeline view for logs
- [ ] Chart visualization for log patterns
- [ ] Alert rules and notifications
- [ ] Compressed file support (zip, gzip)
- [ ] Automatic format detection
- [ ] Session management (save/restore workspace)
- [ ] Integration with log management systems
- [ ] REST API for remote control

### Known Issues
- None - All issues resolved in 1.1.1

### Improvements Under Consideration
- Optimize memory usage for extremely large files (>5GB)
- Add more default parsing patterns
- Improve SSH connection retry logic
- Add connection pooling for multiple remote files
- Cache parsed results for faster reopening
- Add undo/redo for search filters
- Improve error messages and user guidance
- Add context-sensitive help

---

## Release Notes Format

### Version Format
- MAJOR.MINOR.PATCH (e.g., 1.0.0)
- MAJOR: Breaking changes
- MINOR: New features, backwards compatible
- PATCH: Bug fixes, backwards compatible

### Change Categories
- **Added**: New features
- **Changed**: Changes in existing functionality
- **Deprecated**: Soon-to-be removed features
- **Removed**: Removed features
- **Fixed**: Bug fixes
- **Security**: Security fixes

---

**Note**: This is the initial release. Future updates will be documented in this file as they are released.

For bug reports and feature requests, please open an issue on the project repository.

Last Updated: 2024-01-15