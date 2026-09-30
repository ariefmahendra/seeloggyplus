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

### 1a. Alur testing per fitur (WAJIB untuk agent/opencode)

1. **Tulis/update unit test dulu** untuk fitur atau bug yang sedang dikerjakan; jangan menunggu sampai semua pekerjaan selesai.
2. **Jalankan hanya test yang terkait fitur itu** selama iterasi (cepat), dengan filter `--tests`, contoh:
   `.\gradlew.bat --no-daemon test --tests "com.seeloggyplus.controller.NamaControllerTest" --tests "com.seeloggyplus.service.NamaServiceTest"`
   - Pilih kelas test yang berhubungan langsung dengan file yang diubah (controller/service/repository/UI cell).
   - Jangan menjalankan seluruh suite berulang-ulang selagi fitur masih dikerjakan.
3. **Setelah test fitur hijau dan perubahan dianggap final**, baru jalankan seluruh suite satu kali:
   `.\gradlew.bat --no-daemon test` lalu `.\gradlew.bat --no-daemon build`.
4. Selalu tampilkan blok TEST SUMMARY dari langkah terakhir (fitur atau suite penuh) di akhir jawaban.
5. Untuk perubahan UI/tema, sertakan test kontras/ukuran yang relevan (mis. `ButtonThemeTest`, `PopupThemeTest`, `TreeDropIndicatorTest`, `FileManagerLeftPanelLayoutTest`) sebagai bagian dari test fitur, bukan hanya suite penuh.
6. Jangan pernah mengubah test agar mengikuti bug (mis. melonggarkan ambang kontras atau menghapus assertion) demi build hijau.

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

## 7. Kebersihan file & dead code (WAJIB)

- Setiap refactor/perbaikan wajib **menghapus file yang tidak dipakai lagi**: kelas/service/repository/UI component mati, FXML/CSS/script tak terpakai, dan test yang menguji kode yang sudah dihapus.
- Sebelum menghapus, buktikan tidak ada referensi dengan mencari nama kelas/API di `src/` (termasuk FXML/CSS/test). Setelah menghapus, jalankan test terkait (§1a) lalu suite penuh.
- Jangan tinggalkan artefak lokal di root repo: log test/build/run-dev, hasil diagnosa sementara (`*.log`, `focus-report.txt`, dsb.). Hapus atau pastikan sudah masuk `.gitignore`.
- File lokal yang tidak boleh di-commit: dokumen kerja/feedback pribadi (`docs/UI_PLAN.md`, `docs/FEEDBACK_*.md`), `.jqwik-database`, `bin/`, `build/`, `logs/`, `.data/`, `launcher.properties` (lihat `.gitignore`). Dokumen seperti `FEEDBACK_IMPLEMENTATION.md` cukup disimpan lokal sebagai catatan; jangan dibawa ke git.
- Saat commit: periksa `git status`; hanya file relevan yang boleh ikut. Jangan pernah commit log/artefak build atau data user.

## 8. Rilis (WAJIB)

- Proses rilis **wajib mengikuti `docs/RELEASING.md`** (checklist lengkap ada di sana).
- Ringkasan alur: suite test hijau (§1a) → `.\gradlew.bat --no-daemon build` → update `CHANGELOG.md` → bump versi di `gradle.properties` → commit `chore(release): X.Y.Z` di `dev` → merge `--no-ff` ke `main` → push `main` + `dev` → tag `X.Y.Z` dan push tag (memicu workflow Release).
- Semver: MAJOR = breaking, MINOR = fitur, PATCH = bugfix. Pesan commit memakai Conventional Commits (`feat|fix|test|docs|refactor|perf|build|ci|chore`).
- Jangan merilis saat working tree kotor, test gagal, atau build gagal. Semua perubahan wajib sudah di-commit dan hijau.
- Release notes per versi disimpan di `docs/release-notes/<versi>.md`; dokumen ini bagian dari proses rilis dan **boleh** di-commit (berbeda dengan dokumen kerja/feedback pribadi di §7).
