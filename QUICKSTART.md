# SeeLoggyPlus - Quick Start Guide

## 🚀 Menjalankan Aplikasi

### Prasyarat
- Java JDK 17 atau lebih tinggi
- Gradle 8.0+ (sudah include Gradle Wrapper)

### Cara Menjalankan

#### Option 1: Menggunakan Gradle (Recommended)

```bash
# Windows
gradlew.bat run

# Linux/Mac
./gradlew run
```

#### Option 2: Build JAR dan Run

```bash
# Build JAR
gradlew.bat fatJar

# Run JAR
java -jar build/libs/seeloggyplus-all-1.0.0.jar
```

#### Option 3: Menggunakan IDE

1. Import project ke IntelliJ IDEA atau Eclipse
2. Refresh Gradle dependencies
3. Run `Main.java` sebagai Java Application

---

## 📖 Panduan Penggunaan Cepat

### 1. Membuka Log File Pertama Kali

1. Launch aplikasi
2. Klik **File > Open File...** atau tekan `Ctrl+O`
3. Pilih file log (contoh: `application.log`, `server.log`)
4. File akan otomatis diparsing dengan konfigurasi default

### 2. Membuat Pattern Parsing Custom

**Contoh: Standard Java Application Log**

Format log:
```
2024-01-15 10:30:45.123 INFO  [main] com.example.App - Application started
```

Regex pattern:
```regex
(?<timestamp>\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(?<level>\w+)\s+\[(?<thread>[^\]]+)\]\s+(?<logger>\S+)\s+-\s+(?<message>.*)
```

Langkah-langkah:
1. Klik **Settings > Parsing Configuration**
2. Klik **Add**
3. Masukkan:
   - Name: `Java Application Log`
   - Pattern: (paste pattern di atas)
4. Paste sample log di "Sample Log Line"
5. Klik **Test Pattern** - lihat preview
6. Klik **Save**

### 3. Mencari Log Entry

**Text Search:**
```
1. Ketik keyword di search box: "ERROR"
2. Klik Search
```

**Regex Search:**
```
1. Centang checkbox "Regex"
2. Ketik pattern: "ERROR.*database"
3. Klik Search
```

**Case Sensitive:**
```
1. Centang "Case Sensitive"
2. Search akan membedakan huruf besar/kecil
```

### 4. Melihat Detail & Prettify

1. Klik row di tabel log
2. Detail muncul di panel bawah
3. Jika ada JSON/XML:
   - Klik **Prettify JSON** atau **Prettify XML**
   - Format akan otomatis rapih
4. Klik **Copy** untuk copy ke clipboard

### 5. Remote File via SSH

1. Klik **File > Open Remote File...**
2. Tab **Remote (SSH)**:
   - Host: `192.168.1.100` atau `server.example.com`
   - Port: `22`
   - Username: `admin`
   - Password: `yourpassword`
3. Klik **Connect**
4. Browse directory di remote server
5. Double-click directory untuk navigate
6. Pilih file log
7. Klik **OK**

---

## 🎯 Use Cases & Contoh

### Use Case 1: Debugging Application Error

**Skenario:** Aplikasi crash, perlu cari error stack trace

```
1. Open log file
2. Search box: ketik "Exception"
3. Centang "Regex"
4. Pattern: "(Exception|Error|Throwable)"
5. Klik Search
6. Browse hasil - klik row untuk detail
7. Prettify jika ada JSON payload
8. Copy stack trace jika perlu
```

### Use Case 2: Monitoring Web Server Access

**Skenario:** Analisa access log Apache/Nginx

Pattern untuk Apache Access Log:
```regex
(?<ip>[\d.]+)\s+(?<identity>\S+)\s+(?<user>\S+)\s+\[(?<timestamp>[^\]]+)\]\s+"(?<method>\S+)\s+(?<path>\S+)\s+(?<protocol>\S+)"\s+(?<status>\d+)\s+(?<size>\S+)\s+"(?<referer>[^"]*)"\s+"(?<useragent>[^"]*)"
```

Sample log:
```
127.0.0.1 - - [15/Jan/2024:10:30:45 +0000] "GET /api/users HTTP/1.1" 200 1234 "-" "Mozilla/5.0"
```

**Cara:**
1. Buat parsing config baru dengan pattern di atas
2. Set sebagai default
3. Open access.log
4. Tabel akan show kolom: ip, timestamp, method, path, status, dll
5. Search status "404" untuk cari broken links
6. Search path "login" untuk monitor auth attempts

### Use Case 3: JSON Structured Logs

**Skenario:** Modern app dengan JSON logging

Sample log:
```json
{"timestamp":"2024-01-15T10:30:45.123Z","level":"ERROR","service":"auth-service","message":"Login failed","user":"john@example.com","ip":"192.168.1.50"}
```

Pattern:
```regex
\{.*"timestamp"\s*:\s*"(?<timestamp>[^"]+)".*"level"\s*:\s*"(?<level>[^"]+)".*"service"\s*:\s*"(?<service>[^"]+)".*"message"\s*:\s*"(?<message>[^"]+)".*\}
```

**Cara:**
1. Create config "JSON Log Format"
2. Test dengan sample
3. Open log file
4. Select log entry
5. Klik **Prettify JSON** untuk format full JSON

---

## 💡 Tips & Tricks

### Performance Tips

**Large Files (> 100MB):**
- App akan show progress bar
- Gunakan search untuk filter early
- Parallel parsing otomatis aktif untuk multi-core CPU

**Very Large Files (> 1GB):**
- Pertimbangkan filter di server dulu dengan grep/awk
- Atau buka file partial dulu

### Regex Pattern Tips

**Named Groups Must:**
```regex
✅ (?<timestamp>\d{4}-\d{2}-\d{2})
❌ (\d{4}-\d{2}-\d{2})          # Missing name
❌ (?P<timestamp>\d{4}-\d{2}-\d{2})  # Python style, use (?<name>)
```

**Common Patterns:**

Timestamp ISO 8601:
```regex
(?<timestamp>\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}:\d{2}(?:[.,]\d{3})?(?:Z|[+-]\d{2}:\d{2})?)
```

Log Level:
```regex
(?<level>TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)
```

IP Address:
```regex
(?<ip>\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})
```

Thread Name:
```regex
\[(?<thread>[^\]]+)\]
```

### Keyboard Shortcuts

- `Ctrl+O` - Open File
- `Ctrl+R` - Open Remote File
- `Ctrl+P` - Parsing Configuration
- `Ctrl+F` - Focus Search (planned)
- `F5` - Refresh (planned)

### Panel Management

**Hide/Show Panels:**
- View > Show Recent Files Panel
- View > Show Detail Panel

**Resize Panels:**
- Drag divider between panels
- Position disimpan otomatis

---

## 🔧 Troubleshooting

### Aplikasi Tidak Start

**Problem:** Error "JavaFX components are missing"

**Solution:**
```bash
# Pastikan JavaFX included
java --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.fxml -jar app.jar

# Atau gunakan gradle run (recommended)
gradlew run
```

### Pattern Tidak Match

**Problem:** Test parsing gagal, pattern tidak match log

**Solution:**
1. Copy log line yang gagal
2. Test di https://regex101.com (pilih Java flavor)
3. Pastikan escape special characters: `\`, `(`, `)`, `[`, `]`, `{`, `}`, `.`, `*`, `+`, `?`
4. Untuk path Windows: `\\` untuk backslash
5. Cek whitespace - kadang ada tab vs space

### SSH Connection Failed

**Problem:** Cannot connect to remote server

**Solution:**
```bash
# Test koneksi manual
ssh username@hostname

# Check firewall
telnet hostname 22

# Verify credentials
# Try login manual dulu

# Check known_hosts
# App akan auto-accept host key
```

### Out of Memory

**Problem:** Java heap space error untuk file sangat besar

**Solution:**
```bash
# Increase heap size
java -Xmx4g -jar seeloggyplus-all-1.0.0.jar

# Atau edit gradle.properties
org.gradle.jvmargs=-Xmx4096m
```

---

## 📚 Resources

### Sample Log Patterns

Repository ini include sample patterns di:
- Settings > Parsing Configuration > "Default Log Format"
- Settings > Parsing Configuration > "Apache Access Log"
- Settings > Parsing Configuration > "JSON Log Format"

### Sample Log Files

Untuk testing, buat sample log:

**app.log:**
```
2024-01-15 10:30:45.123 INFO  [main] com.example.App - Application started
2024-01-15 10:30:46.456 DEBUG [worker-1] com.example.Service - Processing request
2024-01-15 10:30:47.789 ERROR [worker-1] com.example.Service - Database connection failed
2024-01-15 10:30:48.012 WARN  [main] com.example.App - Retrying connection
2024-01-15 10:30:49.345 INFO  [main] com.example.App - Connection restored
```

---

## 🎓 Advanced Features

### Multiple Configurations

Simpan berbagai parsing config untuk berbagai log format:
- Development logs
- Production logs  
- Access logs
- Error logs
- Custom application logs

Switch config sesuai file yang dibuka.

### Export & Import Config

**Export:**
- Settings > Preferences > Export Settings
- Save ke file `.json`

**Import:**
- Settings > Preferences > Import Settings
- Restore dari backup

### Bookmarks (Planned)

Feature untuk bookmark log entries penting - coming soon!

### Log Correlation (Planned)

Link related logs via request ID atau trace ID - coming soon!

---

## 📞 Support

Jika ada masalah atau pertanyaan:

1. Check documentation di `README.md`
2. Browse issues di repository
3. Create new issue dengan:
   - OS & Java version
   - Log sample (jika bisa)
   - Error message/screenshot
   - Steps to reproduce

---

**Happy Log Viewing! 🔍📊**

Last updated: 2024
Version: 1.0.0