# AGENTS.md — Aturan Project SeeLoggyPlus

Panduan wajib untuk siapa pun (manusia atau agent) yang bekerja di repository ini.

## 1. Testing (WAJIB)

- **Selalu jalankan unit test** untuk setiap fitur baru atau perbaikan bug. Tidak ada perubahan kode tanpa test yang relevan.
- **Testing selalu mode headless.** Konfigurasi Monocle/headless sudah dipasang permanen di `build.gradle` (blok `test`). Jangan menghapus, menonaktifkan, atau memindahkannya ke balik flag opsional.
- Perintah test: `.\gradlew.bat test` (Windows) atau `./gradlew test` (Linux/macOS).
- Saat dijalankan dari terminal/agent di Windows, jalankan Gradle dengan `--no-daemon` (contoh: `.\gradlew.bat --no-daemon test`) agar proses shell tidak menggantung menunggu handle daemon.
- **Selalu sertakan summary hasil test** di akhir pekerjaan. Task `testSummary` otomatis mencetak blok berikut setelah test selesai (berjalan walaupun test gagal):

  ```
  ==================== TEST SUMMARY ====================
    Total   : <n>
    Passed  : <n>
    Failed  : <n>
    Skipped : <n>
    Result  : PASS|FAIL
  ======================================================
  ```

  Jangan menghapus task `testSummary` atau perintah `finalizedBy 'testSummary'` pada task `test`.
- Jika ada test yang gagal, perbaiki sampai hijau sebelum menyatakan pekerjaan selesai. Jangan menonaktifkan/menghapus test untuk "menghijaukan" build.

## 2. Build & Run (WAJIB)

- Pastikan `.\gradlew.bat build` berhasil sebelum menutup pekerjaan.
- Smoke test aplikasi memakai `.\gradlew.bat runDev` (mode dev + hot reload) atau `launcher.bat --console` untuk melihat log console.
- Jangan commit bila build/test masih gagal. Jangan commit kecuali diminta secara eksplisit.

### 2a. Proses aplikasi & smoke test (WAJIB)

- Jalankan smoke test **detached** dan dengan `--no-daemon` (daemon Gradle menahan file lock di `build/resources/main` walau aplikasi sudah ditutup), jangan foreground:
  `Start-Process cmd.exe -ArgumentList '/c','gradlew.bat --no-daemon runDev > run-dev.log 2>&1' -WindowStyle Hidden`
- **Selalu hentikan aplikasi setelah smoke test**:
  `powershell -ExecutionPolicy Bypass -File scripts/stop-app.ps1`
  Instance `runDev` berjalan sebagai `com.seeloggyplus.Main` (bukan hanya `Launcher`), jadi jangan hanya mencocokkan nama Launcher.
- Jika `processResources` gagal dengan `Failed to clean up stale outputs` (file lock khas Windows), urutannya: `.\gradlew.bat --stop` → `scripts/stop-app.ps1` → ulangi perintah build/test. Jangan menganggap ini kegagalan kode.
- Saat memeriksa output test, jangan memakai `Select-Object -First` pada pipeline `Select-String` (membuat shell seperti menggantung); pakai `Get-Content -Tail` atau `ForEach-Object { $_.Line }`.

## 3. Performa Aplikasi Desktop

- Prioritaskan performa: hindari query database per-cell/per-keystroke, hindari operasi berat di JavaFX Application Thread, dan gunakan cache/background `Task` seperti pola yang sudah ada.
- Untuk operasi I/O (file/SSH), jalankan di luar UI thread.

## 4. Database & Migrasi

- Migrasi **additive-only** (hanya tambah kolom/tabel), **idempotent**, **transaksional**, dan **selalu backup** sebelum migrasi. Jangan rename/drop/ubah tipe kolom.
- Setiap perubahan schema wajib menambah/memperbarui test di `DatabaseMigrationTest` (skema lama → migrasi → data utuh, idempoten, rollback saat gagal).
- Fresh install memakai schema terbaru di `createTables()`; DB lama di-upgrade oleh `DatabaseMigrator`.

## 5. Tema & Kontras

- Semua warna wajib memakai token (`-sl-*`); token harus identik di ketiga file tema (Graphite/Light/Dark) — dijaga `ThemeTokensTest`.
- Ikon/teks di atas chrome harus memakai `-sl-chrome-text`/`-sl-accent-text`. Test kontras (`IconContrastTest`, `LabelContrastTest`) tidak boleh diturunkan ambangnya.

## 6. Stack

- Java 21, JavaFX 21 via Gradle wrapper, SQLite (xerial), JUnit 5 + TestFX, Lombok.
- Jalankan perintah Gradle lewat wrapper (`gradlew`), bukan Gradle global.
