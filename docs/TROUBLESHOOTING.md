# Troubleshooting Guide - SeeLoggyPlus

**Version**: 1.2.0  
**Last Updated**: December 20, 2024

---

## 🐛 Common Issues

### Issue #1: File Tidak Muncul Setelah Dipilih

**Gejala:**
- Memilih file log dari File → Open Local File
- File dialog menutup
- Tidak ada log yang muncul di tabel
- Progress bar mungkin muncul sebentar lalu hilang

**Kemungkinan Penyebab:**

#### 1. Tidak Ada Parsing Configuration
**Solusi:**
```
1. Buka menu: Settings → Parsing Configuration
2. Cek apakah ada parsing config yang tersedia
3. Jika kosong, klik "Add" untuk membuat config baru
4. Gunakan salah satu contoh pattern di bawah
```

**Pattern untuk Log Standard:**
```regex
(?<timestamp>\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})\s+(?<level>\w+)\s+(?<message>.*)
```

**Pattern untuk Log Sederhana (Fallback):**
```regex
(?<line>.*)
```

#### 2. Pattern Tidak Cocok dengan Format Log
**Solusi:**
```
1. Buka file log di text editor
2. Lihat format baris pertama
3. Buat pattern yang sesuai dengan format tersebut
4. Test pattern di Parsing Configuration dialog
```

**Contoh Format Log:**
```
Format: 2024-12-20 10:15:23 INFO Application started
Pattern: (?<timestamp>\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})\s+(?<level>\w+)\s+(?<message>.*)

Format: [INFO] 2024-12-20 10:15:23 - Application started
Pattern: \[(?<level>\w+)\]\s+(?<timestamp>[\d\-:\s]+)\s+-\s+(?<message>.*)

Format: INFO  [main] Application started
Pattern: (?<level>\w+)\s+\[(?<thread>[^\]]+)\]\s+(?<message>.*)
```

#### 3. Database Parsing Config Kosong
**Solusi Manual:**
```sql
-- Buka database
sqlite3 ~/.seeloggyplus/seeloggyplus.db

-- Cek parsing configs
SELECT * FROM parsing_configs;

-- Jika kosong, tambahkan default config
INSERT INTO parsing_configs (name, description, regex_pattern, is_default, created_at)
VALUES (
  'Default',
  'Default log parsing configuration',
  '(?<timestamp>\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})\s+(?<level>\w+)\s+(?<message>.*)',
  1,
  datetime('now')
);
```

#### 4. Error Saat Parsing (Cek Log)
**Lokasi Log:**
```
Windows: C:\Users\YourName\.seeloggyplus\logs\
Linux/Mac: ~/.seeloggyplus/logs/
```

**File Log:**
- `seeloggyplus.log` - Main log
- `error.log` - Error log only

**Cari Error:**
```bash
# Windows (PowerShell)
Get-Content "~\.seeloggyplus\logs\seeloggyplus.log" | Select-String -Pattern "ERROR|WARN" | Select-Object -Last 20

# Linux/Mac
tail -100 ~/.seeloggyplus/logs/seeloggyplus.log | grep -E "ERROR|WARN"
```

---

## 🔍 Debugging Steps

### Step 1: Cek Console/Log Output
Jalankan aplikasi dari terminal untuk melihat log:
```bash
cd seeloggyplus
./gradlew run
```

Perhatikan output saat membuka file. Cari pesan seperti:
- "No default parsing config found"
- "Failed to validate default parsing config"
- "Config has 0 named groups"
- "Parsing completed, got 0 entries"

### Step 2: Test dengan File Sample
Gunakan file test.log yang sudah disediakan:
```bash
# File ada di: seeloggyplus/test.log
# Format: 2024-12-20 10:15:23 INFO Message text
```

Buka file ini dan lihat apakah muncul di tabel.

### Step 3: Verifikasi Database
```bash
cd ~/.seeloggyplus
sqlite3 seeloggyplus.db

# Cek parsing configs
SELECT id, name, regex_pattern, is_default FROM parsing_configs;

# Cek recent files
SELECT id, file_name, last_opened FROM recent_files;

# Exit
.quit
```

### Step 4: Reset Configuration
Jika semua cara di atas gagal, reset configuration:
```bash
# Backup dulu
cp -r ~/.seeloggyplus ~/.seeloggyplus.backup

# Hapus database (akan dibuat ulang)
rm ~/.seeloggyplus/seeloggyplus.db

# Jalankan aplikasi lagi
cd seeloggyplus
./gradlew run

# Buat parsing config baru melalui UI
```

---

## 📝 Quick Fix Checklist

Saat file tidak muncul, coba ini secara berurutan:

- [ ] **Cek Parsing Configuration**
  ```
  Settings → Parsing Configuration
  Pastikan ada minimal 1 config
  Pastikan ada yang di-set sebagai default (⭐)
  ```

- [ ] **Test dengan File Sample**
  ```
  Buka test.log yang ada di folder project
  Format sudah pasti cocok dengan pattern default
  ```

- [ ] **Lihat Console Output**
  ```
  Jalankan: ./gradlew run
  Perhatikan error messages
  ```

- [ ] **Cek Database**
  ```
  sqlite3 ~/.seeloggyplus/seeloggyplus.db
  SELECT * FROM parsing_configs;
  ```

- [ ] **Buat Config Manual**
  ```
  Settings → Parsing Configuration → Add
  Name: Simple
  Pattern: (?<line>.*)
  Click Save
  ```

- [ ] **Restart Aplikasi**
  ```
  Close aplikasi
  ./gradlew run
  Try open file lagi
  ```

---

## 🔧 Advanced Debugging

### Enable Debug Logging

Edit `src/main/resources/logback.xml`:
```xml
<logger name="com.seeloggyplus" level="DEBUG"/>
```

Rebuild dan run:
```bash
./gradlew clean build
./gradlew run
```

Log akan lebih detail, contoh output yang normal:
```
INFO  MainController - Updating table columns with config: Default
INFO  MainController - Config has 3 named groups: [timestamp, level, message]
INFO  MainController - Parsing completed, got 50 entries
INFO  MainController - Set 50 entries to allLogEntries
INFO  MainController - Updated table columns for config: Default
INFO  MainController - Created 4 columns total (including line number)
INFO  MainController - Table refresh called, visible items: 50
INFO  MainController - Loaded 50 log entries from test.log, table now shows 50 items
```

Output yang bermasalah:
```
WARN  MainController - No default parsing config found, creating default
ERROR MainController - Failed to validate default parsing config: Pattern must contain at least one named group
INFO  MainController - Config has 0 named groups: []
INFO  MainController - Parsing completed, got 0 entries
```

### Check JavaFX TableView State

Tambahkan breakpoint atau logging di `updateTableColumns`:
```java
logger.debug("TableView columns: {}", logTableView.getColumns().size());
logger.debug("TableView items: {}", logTableView.getItems().size());
logger.debug("allLogEntries size: {}", allLogEntries.size());
logger.debug("filteredLogEntries size: {}", filteredLogEntries.size());
```

### Test Parsing Manually

Buat test sederhana:
```java
ParsingConfig config = new ParsingConfig(
    "Test",
    "Test config",
    "(?<timestamp>\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(?<level>\\w+)\\s+(?<message>.*)"
);

System.out.println("Config valid: " + config.isValid());
System.out.println("Group names: " + config.getGroupNames());
System.out.println("Pattern: " + config.getRegexPattern());

// Test parsing a line
Pattern pattern = Pattern.compile(config.getRegexPattern());
String testLine = "2024-12-20 10:15:23 INFO Application started";
Matcher matcher = pattern.matcher(testLine);

if (matcher.matches()) {
    System.out.println("Match found!");
    for (String group : config.getGroupNames()) {
        System.out.println(group + ": " + matcher.group(group));
    }
} else {
    System.out.println("No match!");
}
```

---

## 🚨 Error Messages & Solutions

### "No default parsing config found"
**Penyebab:** Database tidak memiliki parsing config default  
**Solusi:** Aplikasi akan otomatis membuat default config

### "Failed to validate default parsing config"
**Penyebab:** Pattern regex tidak valid  
**Solusi:** Aplikasi akan fallback ke pattern sederhana `(?<line>.*)`

### "Config has 0 named groups"
**Penyebab:** Pattern tidak menggunakan named groups `(?<name>...)`  
**Solusi:** Edit pattern dan tambahkan named groups

### "Parsing completed, got 0 entries"
**Penyebab:** Pattern tidak cocok dengan format log  
**Solusi:** Buka log file di text editor, lihat formatnya, sesuaikan pattern

### SQLException: table recent_files not found
**Penyebab:** Database schema belum diupdate  
**Solusi:** Hapus database file, akan dibuat ulang otomatis
```bash
rm ~/.seeloggyplus/seeloggyplus.db
```

---

## 💡 Best Practices

### 1. Selalu Buat Parsing Config Dulu
Sebelum membuka log file pertama kali:
1. Settings → Parsing Configuration
2. Add minimal 1 config
3. Set sebagai default (klik ⭐)

### 2. Test Pattern dengan Sample
Sebelum save config:
1. Paste sample log line di "Sample Log Line"
2. Click "Test Pattern"
3. Lihat preview hasil parsing
4. Pastikan semua group ter-extract dengan benar

### 3. Gunakan Pattern Sederhana Untuk Testing
Jika tidak yakin dengan format log:
```regex
(?<line>.*)
```
Pattern ini akan menangkap seluruh baris sebagai 1 field.

### 4. Simpan Pattern yang Sering Dipakai
Buat beberapa config untuk format log yang berbeda:
- Standard (timestamp, level, message)
- Apache Access Log
- JSON Log
- Custom format

---

## 📞 Masih Bermasalah?

### 1. Cek Issue yang Sudah Ada
GitHub Issues: [link]

### 2. Buat Issue Baru
Include informasi ini:
- OS dan Java version: `java -version`
- SeeLoggyPlus version: 1.2.0
- Sample log file (minimal 5 baris)
- Pattern yang digunakan
- Console output / log file
- Screenshot (jika ada)

### 3. Log Files
Attach log files dari:
```
~/.seeloggyplus/logs/seeloggyplus.log
~/.seeloggyplus/logs/error.log
```

---

## 🔄 Complete Reset Instructions

Jika semua cara gagal, reset lengkap:

```bash
# 1. Backup data
cp -r ~/.seeloggyplus ~/.seeloggyplus.backup.$(date +%Y%m%d)

# 2. Stop aplikasi jika running
# (Close window)

# 3. Hapus directory
rm -rf ~/.seeloggyplus

# 4. Rebuild aplikasi
cd seeloggyplus
./gradlew clean build

# 5. Jalankan
./gradlew run

# 6. Setup dari awal
# - Buat parsing config baru
# - Set sebagai default
# - Test dengan test.log
```

---

## ✅ Verification Steps

Setelah fix, verifikasi:

1. **Buka test.log**
   - File → Open Local File
   - Pilih seeloggyplus/test.log
   - Should see 50 log entries

2. **Cek Table**
   - Should have 4 columns: Line, timestamp, level, message
   - All 50 rows visible
   - Can scroll through

3. **Cek Recent Files**
   - test.log appears in left panel
   - Can click to reopen

4. **Cek Database**
   ```sql
   SELECT COUNT(*) FROM recent_files; -- Should be 1
   ```

---

**Last Updated**: December 20, 2024  
**Version**: 1.2.0  
**Status**: Active

---

*For more help, see [docs/README.md](README.md) or [docs/INDEX.md](INDEX.md)*