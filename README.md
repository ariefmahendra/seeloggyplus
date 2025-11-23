# SeeLoggyPlus — Fast, Modern & Intelligent Log Viewer

SeeLoggyPlus adalah aplikasi **log viewer desktop berbasis JavaFX** untuk membaca, mencari, tailing, dan menganalisis log dengan ukuran besar — baik dari **lokal** maupun **remote (SSH server)** — dengan cepat, aman, dan nyaman.

---

## ✨ Fitur Utama

* ✅ Buka log **lokal & remote (SSH)**
* ✅ **Live Tail Mode** — streaming log real-time
* ✅ **Regex Parsing dengan Named Groups → Auto Table Columns**
* ✅ **Recent Files Panel** untuk akses cepat
* ✅ **Smart Search** — text, case-sensitive, & regex
* ✅ **Prettify JSON & XML** di panel detail
* ✅ UI fleksibel — panel dapat disembunyikan
* ✅ Optimized untuk log berukuran ratusan MB
* ✅ Tidak perlu plugin atau konfigurasi tambahan

---

## 📥 Instalasi

### Persyaratan

* **Java 17+**
* **Gradle 8+** *(opsional, sudah tersedia Gradle Wrapper)*
* Koneksi SSH *(hanya jika membuka remote log)*

### Clone Repository

```bash
git clone https://gitlab.com/ariefmahendra/seeloggyplus.git
cd seeloggyplus
```

### Menjalankan Aplikasi

```bash
./gradlew run
```

### Build Executable JAR

```bash
./gradlew fatJar
```

JAR akan tersedia pada:

```
build/libs/
```

---

## 🚀 Cara Menggunakan SeeLoggyPlus

### 1️⃣ Membuka Log Lokal

1. Klik menu **File → Open File**
2. Pilih file `.log`, `.txt`, atau format teks lainnya
3. Pilih parsing configuration
4. Log langsung tampil dalam tabel

### 2️⃣ Membuka Log Remote via SSH

1. Klik **File → Open Remote File**
2. Masukkan host, port, username, password
3. Browse file di server
4. Klik **Open** atau **Tail**

### 3️⃣ Live Tail Mode (Real-Time)

* Klik tombol **Tail**
* Baris baru otomatis tampil saat log berubah
* Scroll manual → tail pause otomatis
* Klik **Resume Tail** untuk melanjutkan

### 4️⃣ Mencari Log

* Ketik di search bar
* Pilih:

    * Case-sensitive
    * Regex mode
* Filtering instan tanpa reload file

### 5️⃣ Parsing Log dengan Regex

* Buka **Settings → Parsing Configuration**
* Gunakan named groups, contoh:

```regex
(?<timestamp>.+?) (?<level>INFO|WARN|ERROR) (?<message>.*)
```

Hasilnya otomatis menjadi kolom tabel log.

### 6️⃣ Melihat Detail Log

* Klik baris log → panel detail muncul
* Bisa:

    * ✅ Copy
    * ✅ Prettify JSON/XML
    * ✅ Clear panel

---

## 🧭 Navigasi & UI

* **Recent Files Panel** — klik untuk membuka kembali
* **Split View UI** — drag untuk resize
* **Panel Toggle** — hide/show via menu View
* **Keyboard Shortcuts**

    * `Ctrl+O` — Open File
    * `Ctrl+R` — Open Remote File
    * `Ctrl+F` — Search
    * `Ctrl+P` — Parsing Config

---

## 📡 Remote Log Support

* SSH authentication:

    * Username + Password
    * Private Key
* Bisa browsing folder server
* Tail mode **tanpa mendownload seluruh file**
* Tidak menyimpan credential secara permanen

---

## ⚡ Performa

* Multi-threaded parsing
* Efficient memory usage
* Virtualized table view untuk scrolling cepat
* Stabil untuk file **100MB – 5GB+**

---

## 🛠 Teknologi

* JavaFX 21
* SQLite
* JSch SSH Client
* Gradle
* SLF4J + Logback

---

## 🧩 Sistem Operasi yang Didukung

✅ Windows 10/11
✅ Linux (Ubuntu, Fedora, Arch, dll.)
✅ macOS (Intel & Apple Silicon)

---

## ❓ Troubleshooting Cepat

| Masalah                 | Solusi                                   |
| ----------------------- | ---------------------------------------- |
| Aplikasi tidak berjalan | Pastikan Java 17+ terinstal              |
| SSH gagal connect       | Periksa host, port, firewall, credential |
| Regex tidak match       | Test melalui menu Parsing Configuration  |

---

## 🤝 Kontribusi

Kontribusi sangat diterima 🎉

1. Fork repository
2. Buat branch baru
3. Commit perubahan
4. Kirim Pull Request

Bugs & improvement request → buka **GitHub Issues**

---

## 📜 Lisensi

© 2025 — SeeLoggyPlus
Bebas digunakan untuk kebutuhan personal & profesional.

---

## ❤️ Terima Kasih

Terima kasih telah menggunakan SeeLoggyPlus!
Semoga log analysis Anda menjadi lebih cepat, jelas, dan nyaman 🔍🚀
