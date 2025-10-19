# SeeLoggyPlus - Project Structure

## 📁 Directory Structure

```
seeloggyplus/
├── src/
│   ├── main/
│   │   ├── java/com/seeloggyplus/
│   │   │   ├── Main.java                           # Application entry point
│   │   │   │
│   │   │   ├── controller/                         # UI Controllers
│   │   │   │   ├── MainController.java             # Main window controller
│   │   │   │   ├── ParsingConfigController.java    # Parsing config dialog
│   │   │   │   └── RemoteFileDialogController.java # SSH remote file browser
│   │   │   │
│   │   │   ├── model/                              # Data Models
│   │   │   │   ├── LogEntry.java                   # Log entry representation
│   │   │   │   ├── ParsingConfig.java              # Regex parsing configuration
│   │   │   │   └── RecentFile.java                 # Recent file history
│   │   │   │
│   │   │   ├── service/                            # Business Logic Services
│   │   │   │   ├── LogParserService.java           # High-performance log parsing
│   │   │   │   ├── SSHService.java                 # SSH remote file access
│   │   │   │   ├── JsonPrettifyService.java        # JSON formatting
│   │   │   │   └── XmlPrettifyService.java         # XML formatting
│   │   │   │
│   │   │   └── util/                               # Utilities
│   │   │       └── PreferencesManager.java         # Application settings manager
│   │   │
│   │   └── resources/
│   │       ├── fxml/                               # JavaFX FXML Layouts
│   │       │   ├── MainView.fxml                   # Main application layout
│   │       │   ├── ParsingConfigDialog.fxml        # Parsing config dialog
│   │       │   └── RemoteFileDialog.fxml           # Remote file browser
│   │       │
│   │       ├── css/                                # Stylesheets
│   │       │   └── style.css                       # Main application stylesheet
│   │       │
│   │       ├── images/                             # Images & Icons
│   │       │   └── (icon files)
│   │       │
│   │       └── logback.xml                         # Logging configuration
│   │
│   └── test/
│       └── java/                                   # Unit Tests
│           └── (test files)
│
├── gradle/                                         # Gradle Wrapper
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── build.gradle                                    # Gradle build configuration
├── settings.gradle                                 # Gradle settings
├── gradle.properties                               # Gradle properties
├── gradlew                                         # Gradle wrapper script (Unix)
├── gradlew.bat                                     # Gradle wrapper script (Windows)
│
├── .gitignore                                      # Git ignore rules
├── README.md                                       # Main documentation
├── QUICKSTART.md                                   # Quick start guide
└── PROJECT_STRUCTURE.md                            # This file
```

## 🏗️ Architecture Overview

### MVC Pattern

```
┌─────────────────────────────────────────────────────────┐
│                        VIEW (FXML)                       │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐ │
│  │  MainView   │  │ ParsingConfig│  │ RemoteFileDialog│ │
│  └─────────────┘  └──────────────┘  └────────────────┘ │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│                    CONTROLLER                            │
│  ┌──────────────────────────────────────────────────┐  │
│  │ MainController                                    │  │
│  │ - Manages UI interactions                         │  │
│  │ - Coordinates between View and Services          │  │
│  │ - Handles user events                            │  │
│  └──────────────────────────────────────────────────┘  │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│                      MODEL                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │  LogEntry    │  │ ParsingConfig│  │ RecentFile   │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│                    SERVICES                              │
│  ┌────────────────┐  ┌────────────┐  ┌──────────────┐ │
│  │ LogParserService│  │ SSHService │  │ Prettify     │ │
│  │                 │  │            │  │ Services     │ │
│  └────────────────┘  └────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────┘
```

## 📦 Component Descriptions

### 1. Main Application (`Main.java`)
- **Purpose**: Application entry point
- **Responsibilities**:
  - Initialize JavaFX application
  - Load main view
  - Setup window preferences
  - Handle application lifecycle

### 2. Controllers

#### MainController
- **Purpose**: Main application window controller
- **Key Features**:
  - Menu bar handling (File, View, Settings, Help)
  - Log table management
  - Search functionality (text & regex)
  - Panel visibility toggling
  - Recent files management
  - Detail view with JSON/XML prettify

#### ParsingConfigController
- **Purpose**: Parsing configuration dialog
- **Key Features**:
  - Create/Edit/Delete regex patterns
  - Named group extraction
  - Pattern validation
  - Test parsing with sample logs
  - Preview parsed fields
  - Save/Load configurations

#### RemoteFileDialogController
- **Purpose**: Remote file access dialog
- **Key Features**:
  - SSH connection management
  - Remote directory browsing
  - File selection (local & remote)
  - Authentication (password & private key)

### 3. Models

#### LogEntry
- **Purpose**: Represent a single log line
- **Properties**:
  - `lineNumber`: Line position in file
  - `rawLog`: Original log text
  - `parsedFields`: Map of extracted fields
  - Common fields: timestamp, level, message, thread, logger
- **Methods**:
  - Pattern matching
  - Search filtering

#### ParsingConfig
- **Purpose**: Regex pattern configuration
- **Properties**:
  - `name`: Configuration name
  - `description`: Description
  - `regexPattern`: Regex with named groups
  - `groupNames`: Extracted group names
  - `isValid`: Validation status
- **Methods**:
  - Pattern validation
  - Named group extraction
  - Log parsing
  - Pattern testing

#### RecentFile
- **Purpose**: Track recently opened files
- **Properties**:
  - Local file: path, name, size
  - Remote file: host, port, user, path
  - `lastOpened`: Timestamp
  - Associated parsing config

### 4. Services

#### LogParserService
- **Purpose**: High-performance log file parsing
- **Features**:
  - Sequential parsing for small files
  - Parallel parsing for large files (multi-threaded)
  - Progress callback support
  - Batch processing (1000 lines/batch)
  - Pattern-based parsing with regex
  - Search functionality (text & regex)

#### SSHService
- **Purpose**: Remote file access via SSH
- **Features**:
  - SSH connection (password & key auth)
  - Remote file reading
  - Directory listing
  - File size checking
  - Command execution
  - SFTP support

#### JsonPrettifyService
- **Purpose**: JSON formatting
- **Features**:
  - Prettify JSON with indentation
  - Minify JSON
  - Extract JSON from log messages
  - Validation

#### XmlPrettifyService
- **Purpose**: XML formatting
- **Features**:
  - Prettify XML with indentation
  - Minify XML
  - Extract XML from log messages
  - Validation

### 5. Utilities

#### PreferencesManager
- **Purpose**: Application settings management
- **Features**:
  - Window size/position persistence
  - Panel visibility settings
  - Parsing configurations storage
  - Recent files history
  - User preferences
  - Export/Import settings

## 🔄 Data Flow

### Opening a Log File

```
User clicks "Open File"
        ↓
MainController.handleOpenFile()
        ↓
FileChooser selects file
        ↓
MainController.openLogFile(file)
        ↓
LogParserService.parseFile(file, config)
        ↓
┌─────────────────────────────────┐
│  Parallel Processing (threads)  │
│  - Split into batches           │
│  - Parse each batch             │
│  - Extract named groups         │
│  - Create LogEntry objects      │
└─────────────────────────────────┘
        ↓
Return List<LogEntry>
        ↓
Update ObservableList
        ↓
TableView auto-updates
        ↓
Add to Recent Files
        ↓
Save preferences
```

### Search Flow

```
User enters search text
        ↓
MainController.performSearch()
        ↓
Check if regex mode
        ↓
┌─────────────┬──────────────┐
│  Text Mode  │  Regex Mode  │
└─────────────┴──────────────┘
        ↓              ↓
String.contains() Pattern.matcher()
        ↓              ↓
Filter LogEntries with Predicate
        ↓
FilteredList updates
        ↓
TableView shows filtered results
        ↓
Update status with count
```

### Parsing Configuration Flow

```
User opens Parsing Config Dialog
        ↓
Load saved configurations
        ↓
User creates/edits pattern
        ↓
ParsingConfigController validates
        ↓
Extract named groups from pattern
        ↓
User tests with sample log
        ↓
LogParserService.testParsing()
        ↓
Show preview of parsed fields
        ↓
User saves configuration
        ↓
PreferencesManager.saveParsingConfigs()
        ↓
Write to ~/.seeloggyplus/parsing_configs.json
```

## 🎨 UI Component Hierarchy

```
Stage (Window)
└── Scene
    └── BorderPane (mainBorderPane)
        ├── Top
        │   ├── MenuBar
        │   └── ToolBar
        │
        ├── Center
        │   └── SplitPane (horizontal)
        │       ├── Left: VBox (Recent Files Panel)
        │       │   ├── Label
        │       │   ├── ListView<RecentFile>
        │       │   └── Button (Clear)
        │       │
        │       └── Right: SplitPane (vertical)
        │           ├── Top: VBox (Log Table Panel)
        │           │   └── TableView<LogEntry>
        │           │
        │           └── Bottom: VBox (Detail Panel)
        │               ├── Label (title)
        │               ├── CodeArea (detail text)
        │               └── HBox (action buttons)
        │
        └── Bottom
            └── HBox (Status Bar)
                ├── Label (status)
                └── ProgressBar
```

## 🔧 Configuration Files

### Application Data Location

- **Windows**: `C:\Users\[username]\.seeloggyplus\`
- **Linux/Mac**: `~/.seeloggyplus/`

### Stored Files

```
.seeloggyplus/
├── parsing_configs.json          # Saved regex patterns
├── recent_files.json             # Recent file history
└── logs/
    ├── seeloggyplus.log          # Application logs
    └── seeloggyplus-error.log    # Error logs
```

### Java Preferences

Stored in OS-specific location:
- Window size, position, maximized state
- Panel visibility and sizes
- UI preferences (theme, font)
- Performance settings

## 🚀 Performance Optimizations

### 1. Parallel Processing
- Multi-threaded parsing for large files
- Batch processing (1000 lines/batch)
- Thread pool with CPU core count

### 2. Lazy Loading
- Virtual scrolling in TableView
- On-demand detail rendering
- Filtered list wrapping

### 3. Efficient Memory Usage
- Stream processing for file reading
- Large buffer size (32KB default)
- Garbage collection friendly

### 4. UI Responsiveness
- Background tasks for file operations
- Progress indicators
- Non-blocking UI updates

## 📚 Dependencies

### Core Dependencies
- **JavaFX 21**: UI framework
- **Gradle 8.5**: Build system

### Libraries
- **JSch 0.1.55**: SSH connectivity
- **Gson 2.10.1**: JSON processing
- **Jackson 2.15.2**: JSON processing (alternative)
- **Logback 1.4.11**: Logging
- **Commons IO 2.13.0**: File utilities
- **ControlsFX 11.1.2**: Advanced UI components
- **RichTextFX 0.11.2**: Code editor component

## 🧪 Testing Strategy

### Unit Tests
- Model classes validation
- Service logic testing
- Pattern matching tests

### Integration Tests
- File parsing end-to-end
- SSH connection tests
- UI component interactions

### Performance Tests
- Large file handling (> 1GB)
- Concurrent parsing
- Memory usage profiling

## 🔐 Security Considerations

### SSH Connections
- No hardcoded credentials
- Private key support
- Secure password handling
- Host key verification

### File Access
- Sandboxed file operations
- Input validation
- Path traversal prevention

### Regex Patterns
- Pattern compilation error handling
- Timeout for complex patterns
- Input sanitization

## 📊 Metrics & Logging

### Application Logs
- INFO: Normal operations
- WARN: Non-critical issues
- ERROR: Failures and exceptions
- DEBUG: Detailed debugging info

### Performance Metrics
- File parsing time
- Memory usage
- Thread pool statistics
- Search performance

---

**Last Updated**: 2024
**Version**: 1.0.0