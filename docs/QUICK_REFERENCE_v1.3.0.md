# Quick Reference Guide - Version 1.3.0

## 🆕 New Features

### 1. Smart Unparsed Entry Placement
Unparsed log entries sekarang ditampilkan secara intelligent:

#### Automatic Column Detection:
- **Priority 1**: Kolom `message` (jika ada)
- **Priority 2**: Kolom mirip message (`msg`, `messages`, `text`, `content`, dll)
- **Priority 3**: Kolom pertama (kecuali `level`)

#### Contoh:
```
Pattern: (?<timestamp>.*) (?<level>INFO|ERROR) (?<message>.*)
Result: Unparsed → kolom "message" ✓

Pattern: (?<timestamp>.*) [(?<level>INFO)] (?<msg>.*)
Result: Unparsed → kolom "msg" ✓

Pattern: (?<level>INFO|ERROR) (?<thread>.*) (?<logger>.*)
Result: Unparsed → kolom "thread" (skip level) ✓
```

### 2. Auto-Update Parsing Configuration
Main view sekarang auto-update ketika Anda mengubah parsing configuration!

#### How It Works:
1. Buka file dengan Config A
2. Buka **Settings > Parsing Configuration**
3. Edit Config A (ubah regex pattern)
4. Klik **Save** atau **Apply**
5. ✨ **Dialog muncul**: "Re-parse file now?"
   - **Re-parse Now**: File langsung di-parse ulang dengan config baru
   - **Later**: Update config saja, parse ulang nanti

#### Benefits:
- ✅ No need to close/reopen file
- ✅ Instant feedback on config changes
- ✅ User control (can defer re-parsing)

## 📋 User Workflows

### Workflow 1: Update Config for Current File
```
1. File terbuka: application.log (Config: Java Standard)
2. Menu: Settings > Parsing Configuration
3. Select: "Java Standard"
4. Edit: Tambah named group (?<user>.*?)
5. Click: "Save"
6. Dialog: "Re-parse application.log now?"
7. Click: "Re-parse Now"
8. ✨ Result: Tabel update, kolom "user" muncul
```

### Workflow 2: Create New Config
```
1. Menu: Settings > Parsing Configuration
2. Click: "Add"
3. Input: Name, Pattern, etc.
4. Click: "Save"
5. ✨ Result: Config saved, no re-parse (no file loaded)
```

### Workflow 3: Delete Current Config
```
1. File terbuka: app.log (Config: Custom Pattern)
2. Menu: Settings > Parsing Configuration
3. Select: "Custom Pattern"
4. Click: "Delete"
5. Confirm deletion
6. Click: "Save"
7. ✨ Dialog: "Config deleted. Reload file with new config."
```

### Workflow 4: Test Pattern Changes
```
1. File terbuka: server.log
2. Menu: Settings > Parsing Configuration
3. Edit pattern
4. Click: "Apply" (not Save - dialog stays open)
5. Dialog: "Re-parse now?"
6. Click: "Re-parse Now"
7. ✨ Check main view - see results
8. If good: Click "Save"
9. If bad: Edit more, repeat
```

## 🎯 Best Practices

### Pattern Design:
1. **Always include message-like column**
   ```regex
   ✅ Good: (?<timestamp>.*) (?<level>.*) (?<message>.*)
   ⚠️ Avoid: (?<level>.*) (?<value1>.*) (?<value2>.*)
   ```

2. **Put level column after timestamp**
   ```regex
   ✅ Good: (?<timestamp>.*) (?<level>INFO) (?<msg>.*)
   ⚠️ Avoid: (?<level>INFO) (?<timestamp>.*) (?<msg>.*)
   ```

3. **Use descriptive group names**
   ```regex
   ✅ Good: (?<error_message>.*) (?<stack_trace>.*)
   ⚠️ Bad: (?<field1>.*) (?<field2>.*)
   ```

### Configuration Management:
1. **Test before saving**
   - Use "Test Parsing" feature
   - Check sample logs
   - Verify all groups captured

2. **Use Apply for experiments**
   - Dialog stays open
   - Can see results immediately
   - Easy to iterate

3. **Backup important patterns**
   - Duplicate before editing
   - Keep original as backup

## 🔍 Troubleshooting

### Q: Unparsed entries appear in wrong column
**A**: Check your pattern:
- Ensure you have `message` or `msg` named group
- Verify group names are lowercase
- Test pattern with sample log

### Q: Re-parse dialog doesn't appear
**A**: Possible reasons:
- Config wasn't actually changed
- No file is currently loaded
- You edited different config (not the one in use)

### Q: Re-parse takes too long
**A**: Options:
1. Click "Later" to defer
2. Close file first, then save config
3. Use smaller sample file for testing

### Q: Config deleted but file still shows old data
**A**: Expected behavior:
- Old data remains until you reload
- Status bar shows: "Config deleted..."
- Action: Load file with new config

## ⚡ Performance Tips

### For Large Files:
1. **Test pattern on small sample first**
   - Don't test on 1GB file immediately
   - Use first 1000 lines for testing
   
2. **Use Apply button for testing**
   - See results without closing dialog
   - Faster iteration
   
3. **Optimize regex patterns**
   - Avoid greedy quantifiers where possible
   - Use specific patterns over `.*`
   
4. **Defer re-parsing if needed**
   - Click "Later" when busy
   - Re-parse during break time

### For Better UX:
1. **Name configs clearly**
   - "Apache Access Log 2024"
   - "Java App - Custom Format"
   - Not: "Config 1", "Test"

2. **Add descriptions**
   - Explain what pattern captures
   - Note any special requirements
   - Help future you remember

3. **Test with edge cases**
   - Empty lines
   - Multi-line entries
   - Special characters

## 📊 Column Display Logic

### Detection Priority (Detailed):

1. **Exact Match** (highest priority):
   - `message`
   - `msg`
   - `messages`

2. **Partial Match** (medium priority):
   - Contains `message`: `error_message`, `log_message`
   - Contains `msg`: `user_msg`, `err_msg`
   - Contains `text`: `message_text`, `log_text`
   - Contains `content`: `message_content`

3. **Level Avoidance** (special case):
   - If first column is `level`
   - Use second column instead
   - Prevents confusing "UNPARSED" badge in level column

4. **Default Fallback**:
   - Use first column
   - Only if no message-like columns found

### Examples:

```
Pattern: (?<time>.*) (?<lvl>.*) (?<msg>.*)
Unparsed → "msg" (exact match)

Pattern: (?<timestamp>.*) (?<log_message>.*)
Unparsed → "log_message" (partial match: contains "message")

Pattern: (?<datetime>.*) (?<error_msg>.*)
Unparsed → "error_msg" (partial match: contains "msg")

Pattern: (?<level>.*) (?<thread>.*) (?<logger>.*)
Unparsed → "thread" (level avoidance)

Pattern: (?<timestamp>.*) (?<value>.*)
Unparsed → "timestamp" (default fallback)
```

## 🚀 Quick Tips

### Keyboard Shortcuts:
- `Ctrl+O`: Open File
- `Ctrl+P`: Parsing Configuration
- `Ctrl+F`: Focus Search
- `Ctrl+R`: Open Remote File

### UI Tips:
- **Double-click** log entry: Jump to original position (clears filters)
- **Drag dividers**: Resize panels
- **Right-click column**: Sort options
- **Ctrl+Scroll**: Zoom text (detail panel)

### Power User Features:
1. **Auto-fit columns**: Click "Auto Fit" button
2. **Hide unparsed**: Checkbox to hide unparsed entries
3. **Level filter**: Filter by log level
4. **Date range**: Filter by timestamp
5. **Regex search**: Advanced search with regex

## 📖 Related Documentation

- [Full Feature Documentation](UNPARSED_ENTRY_IMPROVEMENT.md)
- [Auto-Update Config Details](AUTO_UPDATE_PARSING_CONFIG.md)
- [Developer Guide](DEVELOPER_GUIDE_1.2.0.md)
- [Project Structure](PROJECT_STRUCTURE.md)

---

**Version**: 1.3.0  
**Last Updated**: November 2024  
**Status**: ✅ Production Ready
