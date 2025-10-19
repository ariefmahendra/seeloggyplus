# SeeLoggyPlus - High-Performance Log Viewer

**Version 1.2.0** - December 2024

SeeLoggyPlus adalah aplikasi log viewer berbasis JavaFX yang dirancang untuk kinerja tinggi dan kemampuan parsing yang canggih.

## 🎉 What's New in 1.2.0

- **Recent Files in Database**: Log files sekarang otomatis di-parsing dan ditampilkan di dashboard setelah dibuka. File name langsung muncul di Recent Files panel!
- **Smart Panel Toggle**: Hide/show panel sekarang otomatis menyesuaikan split pane - tidak ada lagi dead space!

📖 [Read Full Update Details](docs/WHATS_NEW_1.2.0.md) | [Changelog](CHANGELOG.md) | [📚 All Documentation](docs/INDEX.md)

## Fitur Utama

### 1. **Menu Bar Lengkap**
- **File Menu**: Buka file log lokal atau remote via SSH
- **View Menu**: Toggle visibility panel (Recent Files, Detail Panel)
- **Settings Menu**: Konfigurasi parsing dengan regex pattern
- **Help Menu**: Dokumentasi dan informasi aplikasi

### 2. **Dashboard Log Viewer**
- **Panel Kiri**: Daftar file log yang baru dibuka (Recent Files)
- **Panel Kanan Atas**: Tabel log viewer dengan kolom dinamis
- **Panel Bawah**: Detail log dengan fitur prettify JSON/XML
- Semua panel dapat di-show/hide sesuai kebutuhan

### 3. **Akses File Multi-Source**
- **Local Drive**: Buka file log dari komputer lokal
- **SSH Remote**: Koneksi ke server remote via SSH untuk membaca log
- Support autentikasi password dan private key

### 4. **Parsing Configuration**
- Gunakan regex pattern dengan named groups: `(?<groupName>pattern)`
- Named groups otomatis menjadi kolom header di tabel log
- Test parsing dengan sample log untuk validasi pattern
- Preview hasil parsing sebelum disimpan
- Template default untuk format log umum (Standard, Apache, JSON)

### 5. **Performance Optimization**
- Parallel parsing untuk file besar
- Lazy loading dan virtual scrolling
- Buffer size yang dapat dikonfigurasi
- Progress indicator untuk operasi file besar

### 6. **Fitur Pencarian**
- **Text Search**: Pencarian teks biasa (case-sensitive/insensitive)
- **Regex Search**: Pencarian menggunakan regular expression
- Real-time filtering pada tabel log

### 7. **JSON/XML Prettification**
- Otomatis deteksi JSON atau XML dalam log
- Format dan indent JSON/XML untuk readability
- Copy hasil prettify ke clipboard

## Teknologi yang Digunakan

- **JavaFX 21**: Framework UI modern untuk Java
- **Gradle**: Build automation dan dependency management
- **JSch**: Library untuk koneksi SSH
- **Gson & Jackson**: Parsing dan formatting JSON
- **RichTextFX**: Advanced text editor untuk detail view
- **ControlsFX**: UI components tambahan
- **Logback**: Logging framework

## Struktur Project

```
seeloggyplus/
├── src/
│   ├── main/
│   │   ├── java/com/seeloggyplus/
│   │   │   ├── Main.java                    # Entry point aplikasi
│   │   │   ├── controller/                  # Controllers untuk UI
│   │   │   │   ├── MainController.java
│   │   │   │   ├── ParsingConfigController.java
│   │   │   │   └── ...
│   │   │   ├── model/                       # Data models
│   │   │   │   ├── LogEntry.java
│   │   │   │   ├── ParsingConfig.java
│   │   │   │   └── RecentFile.java
│   │   │   ├── service/                     # Business logic services
│   │   │   │   ├── LogParserService.java
│   │   │   │   ├── SSHService.java
│   │   │   │   ├── JsonPrettifyService.java
│   │   │   │   └── XmlPrettifyService.java
│   │   │   └── util/                        # Utilities
│   │   │       └── PreferencesManager.java
│   │   └── resources/
│   │       ├── fxml/                        # FXML layouts
│   │       │   ├── MainView.fxml
│   │       │   └── ParsingConfigDialog.fxml
│   │       ├── css/                         # Stylesheets
│   │       │   └── style.css
│   │       └── images/                      # Icons & images
│   └── test/
│       └── java/                            # Unit tests
├── build.gradle                             # Gradle build configuration
├── settings.gradle
├── gradle.properties
└── README.md
```

## Instalasi dan Setup

### Prasyarat

- Java Development Kit (JDK) 17 atau lebih tinggi
- Gradle 8.0+ (atau gunakan Gradle Wrapper yang disediakan)

### Clone Repository

```bash
git clone <repository-url>
cd seeloggyplus
```

### Build Project

```bash
# Windows
gradlew.bat build

# Linux/Mac
./gradlew build
```

### Run Aplikasi

```bash
# Windows
gradlew.bat run

# Linux/Mac
./gradlew run
```

### Create Executable JAR

```bash
# Windows
gradlew.bat fatJar

# Linux/Mac
./gradlew fatJar
```

JAR file akan tersedia di `build/libs/seeloggyplus-all-1.0.0.jar`

## Cara Penggunaan

### 1. Membuka File Log

**Lokal:**
1. Klik menu `File > Open File...` atau tekan `Ctrl+O`
2. Pilih file log yang ingin dibuka
3. File akan diparsing sesuai konfigurasi default

**Remote (SSH):**
1. Klik menu `File > Open Remote File...` atau tekan `Ctrl+R`
2. Masukkan informasi koneksi:
   - Host: IP atau hostname server
   - Port: Port SSH (default 22)
   - Username: Username SSH
   - Password atau Private Key
3. Browse dan pilih file log di server
4. File akan didownload dan diparsing

### 2. Konfigurasi Parsing

1. Klik menu `Settings > Parsing Configuration...` atau tekan `Ctrl+P`
2. Klik `Add` untuk membuat konfigurasi baru
3. Masukkan informasi:
   - **Name**: Nama konfigurasi
   - **Description**: Deskripsi (opsional)
   - **Regex Pattern**: Pattern dengan named groups
4. Lihat detected named groups di panel kanan bawah
5. Test pattern dengan sample log:
   - Paste sample log di "Sample Log Line"
   - Klik `Test Pattern`
   - Lihat preview hasil parsing
6. Klik `Save` untuk menyimpan

**Contoh Pattern:**

```regex
# Standard Java Log
(?<timestamp>\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2},\d{3})\s+(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\s+\[(?<thread>[^\]]+)\]\s+(?<logger>[^\s]+)\s+-\s+(?<message>.*)

# Apache Access Log
(?<ip>[\d.]+)\s+(?<identity>\S+)\s+(?<user>\S+)\s+\[(?<timestamp>[^\]]+)\]\s+"(?<method>\S+)\s+(?<path>\S+)\s+(?<protocol>\S+)"\s+(?<status>\d+)\s+(?<size>\S+)\s+"(?<referer>[^"]*)"\s+"(?<useragent>[^"]*)"

# JSON Log
\{.*"timestamp"\s*:\s*"(?<timestamp>[^"]+)".*"level"\s*:\s*"(?<level>[^"]+)".*"message"\s*:\s*"(?<message>[^"]+)".*\}
```

### 3. Pencarian Log

1. Masukkan kata kunci di search field
2. Pilih opsi:
   - **Regex**: Gunakan regex pattern untuk pencarian
   - **Case Sensitive**: Pencarian case-sensitive
3. Klik `Search` atau tekan Enter
4. Hasil akan difilter di tabel
5. Klik `Clear` untuk menghapus filter

### 4. Melihat Detail Log

1. Klik baris log di tabel
2. Detail log akan muncul di panel bawah
3. Gunakan tombol action:
   - **Prettify JSON**: Format JSON dalam log
   - **Prettify XML**: Format XML dalam log
   - **Copy**: Copy detail ke clipboard
   - **Clear**: Bersihkan panel detail

### 5. Mengatur Tampilan

1. Klik menu `View`
2. Toggle visibility panel:
   - `Show Recent Files Panel`: Show/hide panel kiri
   - `Show Detail Panel`: Show/hide panel bawah
3. Drag divider untuk resize panel

## Konfigurasi Regex Pattern - Best Practices

### Named Groups Syntax

```regex
(?<groupName>pattern)
```

### Tips Penting

1. **Gunakan nama group yang descriptive:**
   - ✅ Good: `timestamp`, `level`, `message`, `thread`, `logger`
   - ❌ Bad: `g1`, `g2`, `field1`

2. **Test pattern sebelum save:**
   - Gunakan fitur Test Parsing dengan sample log real
   - Pastikan semua named groups terdeteksi
   - Verifikasi hasil parsing sudah sesuai

3. **Named groups menjadi kolom tabel:**
   - Setiap named group akan menjadi kolom di tabel log
   - Urutan kolom mengikuti urutan group di pattern

4. **Pattern common untuk log level:**
   ```regex
   (TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)
   ```

5. **Pattern untuk timestamp:**
   ```regex
   # ISO 8601
   \d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}:\d{2}(?:[.,]\d{3})?(?:Z|[+-]\d{2}:\d{2})?
   
   # Log4j style
   \d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}[.,]\d{3}
   ```

## Performance Tips

### Untuk File Besar (> 100 MB)

1. **Enable Parallel Parsing:**
   - Settings > Preferences > Enable Parallel Parsing
   - Gunakan multiple CPU cores untuk parsing

2. **Increase Buffer Size:**
   - Settings > Preferences > Buffer Size
   - Default: 32KB, untuk file besar gunakan 64KB atau 128KB

3. **Batasi Kolom:**
   - Gunakan regex pattern yang hanya extract field yang diperlukan
   - Semakin sedikit named groups, semakin cepat parsing

4. **Filter Early:**
   - Gunakan search untuk filter log yang relevan
   - Jangan scroll semua data jika tidak perlu

## Troubleshooting

### Aplikasi Tidak Bisa Start

```bash
# Check Java version
java -version

# Harus Java 17 atau lebih tinggi
```

### Pattern Tidak Match

1. Test pattern di online regex tester (regex101.com)
2. Pastikan escape special characters: `\`, `(`, `)`, `[`, `]`, `{`, `}`, `.`, `*`, `+`, `?`
3. Gunakan raw string untuk path: `\\` untuk backslash
4. Cek sample log apakah sesuai dengan pattern

### SSH Connection Failed

1. Verify server dapat diakses: `ping <hostname>`
2. Cek SSH port: `telnet <hostname> 22`
3. Test SSH credential: `ssh user@hostname`
4. Pastikan firewall tidak block koneksi

### Out of Memory Error

```bash
# Increase JVM heap size di gradle.properties
org.gradle.jvmargs=-Xmx4096m

# Atau saat run:
java -Xmx4g -jar seeloggyplus-all-1.0.0.jar
```

## Roadmap & Future Features

- [ ] Export filtered logs ke file
- [ ] Bookmark log entries
- [ ] Multi-tab untuk multiple files
- [ ] Log syntax highlighting
- [ ] Custom color schemes per log level
- [ ] Log statistics dan analytics
- [ ] Real-time log tailing (follow mode)
- [ ] Plugin system untuk custom parsers
- [ ] Database storage untuk very large files
- [ ] Log correlation dan threading

## Kontribusi

Kontribusi sangat diterima! Silakan:

1. Fork repository
2. Create feature branch: `git checkout -b feature/AmazingFeature`
3. Commit changes: `git commit -m 'Add some AmazingFeature'`
4. Push to branch: `git push origin feature/AmazingFeature`
5. Open Pull Request

## Lisensi

Copyright © 2024 SeeLoggyPlus

## Kontak & Support

Untuk pertanyaan, bug reports, atau feature requests, silakan buka issue di repository ini.

## 📚 Dokumentasi Lengkap

Semua dokumentasi telah dipindahkan ke folder `docs/` untuk organisasi yang lebih baik:

### 📖 Quick Access

- **[📚 Documentation Index](docs/INDEX.md)** - Daftar lengkap semua dokumentasi
- **[🇮🇩 Implementasi Selesai](docs/IMPLEMENTASI_SELESAI.md)** - Ringkasan implementasi v1.2.0 (Bahasa Indonesia)
- **[🎉 What's New 1.2.0](docs/WHATS_NEW_1.2.0.md)** - User-friendly changelog
- **[📋 Quick Start Guide](QUICKSTART.md)** - Panduan cepat memulai

### 🔧 For Developers

- **[👨‍💻 Developer Guide](docs/DEVELOPER_GUIDE_1.2.0.md)** - Complete developer guide
- **[🗂️ Project Structure](docs/PROJECT_STRUCTURE.md)** - Project structure overview
- **[💾 Database Integration](docs/DATABASE_INTEGRATION.md)** - Database documentation
- **[🧪 Feature Tests](docs/FEATURE_TEST_1.2.0.md)** - Test cases and verification

### 📝 Release Documentation

- **[📦 Version 1.2.0 Update](docs/VERSION_1.2.0_UPDATE.md)** - Detailed release notes
- **[✨ Recent Files Update](docs/RECENT_FILES_UPDATE.md)** - Feature guide for recent files
- **[📊 Implementation Summary](docs/IMPLEMENTATION_SUMMARY_1.2.0.md)** - Technical details
- **[📜 Changelog](CHANGELOG.md)** - Complete version history

### 🗂️ Documentation Structure

```
docs/
├── INDEX.md                           # Documentation index
├── IMPLEMENTASI_SELESAI.md           # 🇮🇩 Implementation summary
├── WHATS_NEW_1.2.0.md                # User-friendly changelog
├── VERSION_1.2.0_UPDATE.md           # Release notes
├── RECENT_FILES_UPDATE.md            # Feature guide
├── IMPLEMENTATION_SUMMARY_1.2.0.md   # Technical summary
├── DEVELOPER_GUIDE_1.2.0.md          # Developer guide
├── FEATURE_TEST_1.2.0.md             # Test cases
├── DATABASE_INTEGRATION.md           # Database docs
├── PROJECT_STRUCTURE.md              # Project overview
├── UPDATE_SUMMARY.md                 # v1.1.0 features
├── BUGFIXES.md                       # v1.1.1 fixes
└── SUCCESS.md                        # Historical
```

**Total Documentation**: ~5,250+ lines across 13 files

---

**Happy Log Viewing! 🔍📊**