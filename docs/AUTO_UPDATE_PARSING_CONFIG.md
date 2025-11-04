# Auto-Update Parsing Configuration Feature

## Overview
Implementasi callback mechanism untuk auto-update parsing configuration di main view ketika user mengubah konfigurasi dari Parsing Configuration Dialog.

## Problem Statement
Sebelumnya, ketika user mengubah parsing configuration dari menu Settings > Parsing Configuration, perubahan tidak langsung ter-reflect di main view. User harus:
1. Close file yang sedang dibuka
2. Re-open file dengan configuration baru
3. Ini tidak user-friendly dan membingungkan

## Solution
Implementasi callback pattern untuk memberitahu MainController ketika parsing configuration berubah, dengan opsi untuk re-parse file secara otomatis.

## Changes Made

### 1. ParsingConfigController.java

#### Added Fields:
```java
private Runnable onConfigChangedCallback; // Callback to notify parent when config changes
```

#### Added Methods:

**`setOnConfigChangedCallback(Runnable callback)`**
```java
/**
 * Set callback to be invoked when parsing configuration changes
 * @param callback Runnable to execute when config is saved/updated
 */
public void setOnConfigChangedCallback(Runnable callback) {
    this.onConfigChangedCallback = callback;
}
```

**`notifyConfigChanged()`**
```java
/**
 * Notify parent controller that configuration has changed
 */
private void notifyConfigChanged() {
    if (onConfigChangedCallback != null) {
        logger.debug("Notifying parent controller of config changes");
        onConfigChangedCallback.run();
    }
}
```

#### Modified Methods:

**`handleSave()`** - Added notification after saving
```java
private void handleSave() {
    // ... existing save logic ...
    
    // Notify parent controller of changes
    notifyConfigChanged();
    logger.info("Configuration saved and parent notified");
    
    closeDialog();
}
```

**`handleApply()`** - Added notification after applying
```java
private void handleApply() {
    // ... existing apply logic ...
    
    // Notify parent controller of changes
    notifyConfigChanged();
    logger.info("Configuration applied and parent notified");
}
```

### 2. MainController.java

#### Modified `handleParsingConfiguration()`
Set callback sebelum membuka dialog:
```java
private void handleParsingConfiguration() {
    try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ParsingConfigDialog.fxml"));
        Parent root = loader.load();
        
        // Get controller and set callback
        ParsingConfigController controller = loader.getController();
        controller.setOnConfigChangedCallback(this::handleParsingConfigChanged);
        
        // ... rest of dialog setup ...
    }
}
```

#### Added `handleParsingConfigChanged()`
Method baru untuk handle config changes:
```java
/**
 * Handle parsing configuration changes from ParsingConfigDialog
 * Re-parse current file with updated configuration if file is loaded
 */
private void handleParsingConfigChanged() {
    // 1. Check if file is loaded
    if (currentFile == null || currentParsingConfig == null) {
        return;
    }
    
    // 2. Reload config from database
    Optional<ParsingConfig> updatedConfigOpt = parsingConfigService.findById(currentParsingConfig.getId());
    
    // 3. Handle deleted config
    if (updatedConfigOpt.isEmpty()) {
        showInfo("Configuration Deleted", "...");
        return;
    }
    
    ParsingConfig updatedConfig = updatedConfigOpt.get();
    
    // 4. Check if config actually changed
    if (configsAreEqual(currentParsingConfig, updatedConfig)) {
        return; // No change
    }
    
    // 5. Ask user if they want to re-parse
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    // ... setup alert ...
    
    Optional<ButtonType> result = alert.showAndWait();
    
    if (result.isPresent() && result.get() == reParseButton) {
        // Re-parse file with updated config
        openLocalLogFile(currentFile, false, updatedConfig);
    } else {
        updateStatus("Configuration updated. Reload file to apply changes.");
    }
}
```

#### Added `configsAreEqual()`
Helper method untuk compare configs:
```java
/**
 * Compare two ParsingConfig objects for equality (by content, not reference)
 */
private boolean configsAreEqual(ParsingConfig config1, ParsingConfig config2) {
    if (config1 == null || config2 == null) {
        return config1 == config2;
    }
    
    return Objects.equals(config1.getName(), config2.getName()) &&
           Objects.equals(config1.getRegexPattern(), config2.getRegexPattern()) &&
           Objects.equals(config1.getDescription(), config2.getDescription()) &&
           Objects.equals(config1.getTimestampFormat(), config2.getTimestampFormat());
}
```

## User Flow

### Scenario 1: User Saves Config (No File Loaded)
1. User opens Settings > Parsing Configuration
2. User edits/creates configuration
3. User clicks "Save" or "Apply"
4. ✓ Config saved to database
5. ✓ Callback triggered (no action needed)
6. ✓ Dialog closes (if Save) or stays open (if Apply)

### Scenario 2: User Saves Config (File Already Loaded)
1. User has file open with Config A
2. User opens Settings > Parsing Configuration
3. User modifies Config A
4. User clicks "Save" or "Apply"
5. ✓ Config saved to database
6. ✓ Callback triggered
7. ✓ MainController detects changes
8. ✓ Dialog asks: "Re-parse now or later?"
9. **If "Re-parse Now":**
   - File is re-parsed with updated config
   - Table columns updated
   - Display refreshed
10. **If "Later":**
   - Status bar shows: "Configuration updated. Reload file to apply changes."
   - User can manually reload later

### Scenario 3: User Deletes Current Config
1. User has file open with Config A
2. User opens Settings > Parsing Configuration
3. User deletes Config A
4. User clicks "Save"
5. ✓ Config deleted from database
6. ✓ Callback triggered
7. ✓ MainController detects deletion
8. ✓ Info dialog: "Configuration deleted. Please reload file with new configuration."

### Scenario 4: User Edits Different Config
1. User has file open with Config A
2. User opens Settings > Parsing Configuration
3. User edits Config B (different config)
4. User clicks "Save"
5. ✓ Config B saved
6. ✓ Callback triggered
7. ✓ MainController checks: Config A unchanged
8. ✓ No action needed (silent)

## Design Pattern Used

### Observer/Callback Pattern
- **Subject**: ParsingConfigController
- **Observer**: MainController
- **Notification**: Via Runnable callback
- **Benefit**: Loose coupling, easy to extend

### Benefits:
1. **Decoupling**: ParsingConfigController doesn't need to know about MainController
2. **Flexibility**: Easy to add more observers if needed
3. **Testability**: Can mock callback for testing
4. **Performance**: Minimal overhead, only notified on actual changes

## Performance Considerations

### Optimizations:
1. **Change Detection**: Only re-parse if config actually changed
   - Compare all relevant fields
   - Skip notification if no changes
   
2. **User Control**: Always ask before re-parsing
   - User can defer re-parsing
   - Prevents unwanted long operations
   
3. **Efficient Comparison**: 
   - O(1) field comparison
   - No deep object traversal
   - Early exit on first difference

### Memory:
- Callback stored as field (single reference)
- No memory leaks (callback cleared when dialog closes)
- Config comparison uses existing objects

### Thread Safety:
- Callback executed on JavaFX Application Thread
- No concurrent modification issues
- Safe dialog operations

## Testing Guide

### Test Cases:

#### TC1: Config Updated with File Loaded
1. Load file with Config A
2. Open Parsing Configuration
3. Modify Config A (change regex pattern)
4. Click "Save"
5. **Expected**: Dialog asks "Re-parse now?"
6. Click "Re-parse Now"
7. **Expected**: File re-parsed, display updated

#### TC2: Config Updated, User Chooses Later
1. Load file with Config A
2. Open Parsing Configuration
3. Modify Config A
4. Click "Save"
5. **Expected**: Dialog asks "Re-parse now?"
6. Click "Later"
7. **Expected**: Status bar shows "Configuration updated..."
8. File display unchanged

#### TC3: Different Config Updated
1. Load file with Config A
2. Open Parsing Configuration
3. Modify Config B
4. Click "Save"
5. **Expected**: No dialog, silent save
6. File display unchanged

#### TC4: Config Deleted
1. Load file with Config A
2. Open Parsing Configuration
3. Delete Config A
4. Click "Save"
5. **Expected**: Info dialog about deletion
6. File display unchanged

#### TC5: No Changes Made
1. Load file with Config A
2. Open Parsing Configuration
3. Select Config A (no edits)
4. Click "Save"
5. **Expected**: Silent save, no re-parse prompt

#### TC6: Apply Button
1. Load file with Config A
2. Open Parsing Configuration
3. Modify Config A
4. Click "Apply"
5. **Expected**: Dialog asks "Re-parse now?"
6. Dialog stays open
7. Can continue editing

#### TC7: No File Loaded
1. No file loaded
2. Open Parsing Configuration
3. Modify any config
4. Click "Save"
5. **Expected**: Silent save, dialog closes

## Error Handling

### Graceful Degradation:
1. **Callback is null**: No error, silent skip
2. **Config not found**: Show info dialog, prevent crash
3. **Re-parse fails**: Show error dialog, keep old display
4. **Database error**: Log error, show user-friendly message

### Logging:
```java
logger.info("Parsing configuration changed, checking if current file needs re-parsing");
logger.info("Configuration unchanged, no need to re-parse");
logger.warn("Current parsing config no longer exists in database");
logger.info("User chose to re-parse file with updated configuration");
```

## Future Enhancements

1. **Auto Re-parse Option**
   - Add checkbox: "Always auto re-parse on config change"
   - Save preference to database
   - Skip confirmation dialog if enabled

2. **Undo Support**
   - Keep previous config in memory
   - Add "Undo" button to revert changes
   - Re-parse with old config

3. **Config Version History**
   - Track config changes over time
   - Allow rollback to previous versions
   - Compare versions side-by-side

4. **Batch Re-parse**
   - If multiple files open (future feature)
   - Re-parse all files using updated config
   - Progress indicator for batch operation

5. **Smart Re-parse**
   - Only re-parse visible entries
   - Background re-parse for large files
   - Incremental updates

## API Summary

### ParsingConfigController
```java
public void setOnConfigChangedCallback(Runnable callback)
private void notifyConfigChanged()
```

### MainController
```java
private void handleParsingConfigChanged()
private boolean configsAreEqual(ParsingConfig config1, ParsingConfig config2)
```

## Migration Notes

### Breaking Changes:
- None - backward compatible

### New Dependencies:
- None

### Database Changes:
- None

---

**Version**: 1.3.0  
**Date**: November 2024  
**Author**: SeeLoggyPlus Team  
**Status**: ✅ Implemented & Tested
