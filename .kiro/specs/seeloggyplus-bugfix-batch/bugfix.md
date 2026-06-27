# Bugfix Requirements Document

## Introduction

Dokumen ini mencakup 6 bug yang ditemukan di SeeLoggyPlus, sebuah JavaFX log viewer dengan fitur SSH. Bug-bug ini berdampak pada responsivitas UI (status ikon koneksi tidak ter-update), UX file manager (sorting tidak otomatis, direktori terakhir tidak diingat), kejelasan fungsi tombol refresh, keandalan build distribusi (versi tampil "DEV"), dan stabilitas download multi-thread (hang + file corrupt). Semua fix mengikuti prinsip minimal diff dan reuse pattern yang sudah ada.

---

## Bug Analysis

### Current Behavior (Defect)

#### Bug 1: Status Ikon Koneksi Tidak Reaktif di Server Management

1.1 WHEN dialog Server Management dibuka ulang setelah ada sesi aktif yang dibuat melalui File Manager THEN the system menampilkan ikon ✓ atau ✗ yang tidak mencerminkan keadaan sesi aktual pada saat reload selesai

1.2 WHEN data server selesai dimuat dari database ke tabel THEN the system tidak memaksa evaluasi ulang cell ikon di kolom status, sehingga ikon yang tampil tetap dari evaluasi sebelumnya dan tidak sinkron dengan set sesi aktif terkini

#### Bug 2: Sorting File Manager Tidak Auto-Sort by Modified Date, Dikelompokkan by Tipe

1.3 WHEN file manager selesai me-load daftar file ke tabel THEN the system menampilkan file tanpa urutan default yang ditentukan karena tidak ada sort order yang diterapkan secara programatik saat data dimuat

1.4 WHEN user belum mengklik header kolom apapun THEN the system menampilkan file dan direktori tercampur tanpa pengelompokan berdasarkan tipe (direktori vs file)

#### Bug 3: Tombol Refresh di Server Management Tidak Update Status Ikon

1.5 WHEN user menekan tombol Refresh di dialog Server Management THEN the system me-reload data dari database, tetapi ikon status yang tampil tidak diperbarui dan tetap mencerminkan evaluasi sebelumnya

1.6 WHEN daftar server diisi ulang ke tabel setelah reload THEN the system tidak memaksa evaluasi ulang cell ikon di kolom status, sehingga perubahan sesi aktif yang terjadi sejak render terakhir tidak terlihat di UI

#### Bug 4: Versi Tampil "DEV" Setelah Build Distribusi

1.7 WHEN user menjalankan `./gradlew fatJar` secara langsung tanpa menjalankan `processResources` terlebih dahulu THEN the system menghasilkan JAR yang tidak berisi `version.properties` dengan versi yang benar karena task `fatJar` tidak memiliki dependensi eksplisit pada `generateVersionProperties`

1.8 WHEN aplikasi dijalankan dari JAR distribusi yang dihasilkan oleh kondisi 1.7 THEN the system menampilkan string "DEV" sebagai versi di title bar dan dialog About

1.9 WHEN versi aplikasi perlu ditampilkan THEN the system membaca `version.properties` melalui static initializer yang identik di dua kelas berbeda (`Main.java` dan `AboutDialogController.java`), sehingga terdapat duplikasi kode yang melanggar prinsip DRY

#### Bug 5: Error Download Multi-Thread (threadCount > 1)

1.10 WHEN `downloadFileConcurrent()` dipanggil dengan `threadCount > 1` dan server SSH membatasi jumlah concurrent channels THEN the system berusaha membuka satu `ChannelSftp` baru per thread tanpa timeout koneksi, menyebabkan thread ke-2 dst dapat hang tanpa batas waktu atau throw exception

1.11 WHEN salah satu download thread hang atau gagal THEN the system menunggu semua thread selesai tanpa batas waktu, sehingga seluruh operasi download dapat hang selamanya

1.12 WHEN satu atau lebih download thread gagal THEN the system meninggalkan file output yang sudah dialokasi penuh di disk dalam kondisi tidak lengkap tanpa membersihkannya

#### Bug 6: UnifiedFileManager Tidak Restore Last Directory

1.13 WHEN dialog File Manager dibuka untuk lokasi Local THEN the system selalu navigate ke home directory sistem, mengabaikan direktori terakhir yang dikunjungi pada sesi sebelumnya

1.14 WHEN dialog File Manager dibuka untuk lokasi Server (remote) THEN the system selalu navigate ke default path yang dikonfigurasi di data server, mengabaikan direktori terakhir yang dikunjungi per server pada sesi sebelumnya

1.15 WHEN user menutup File Manager setelah bernavigasi ke suatu direktori THEN the system tidak menyimpan path tersebut ke penyimpanan preferensi, sehingga tidak ada data yang bisa di-restore saat dialog dibuka kembali

---

### Expected Behavior (Correct)

#### Bug 1: Status Ikon Koneksi Tidak Reaktif di Server Management

2.1 WHEN dialog Server Management dibuka (termasuk dibuka ulang setelah ada sesi aktif yang dibuat melalui File Manager) atau setelah tombol Refresh ditekan dan data selesai dimuat THEN the system SHALL menampilkan ikon ✓ untuk setiap server yang memiliki sesi aktif dan ikon ✗ untuk server yang tidak memiliki sesi aktif, berdasarkan set sesi aktif pada saat reload selesai

2.2 WHEN data server selesai dimuat ke tabel THEN the system SHALL memaksa evaluasi ulang semua cell di kolom status sehingga ikon yang ditampilkan mencerminkan keadaan sesi aktif pada saat reload selesai, bukan keadaan dari render sebelumnya

#### Bug 2: Sorting File Manager Tidak Auto-Sort by Modified Date, Dikelompokkan by Tipe

2.3 WHEN file manager selesai memuat daftar file (saat startup, navigasi ke direktori baru, atau refresh) THEN the system SHALL menampilkan daftar dengan semua direktori di atas semua file, dan di dalam masing-masing kelompok diurutkan berdasarkan last-write timestamp (kolom Modified) secara descending; tie-breaking dalam satu kelompok menggunakan nama file ascending case-insensitive

2.4 WHEN user selesai bernavigasi ke direktori baru setelah sebelumnya menerapkan manual sort override THEN the system SHALL me-reset sort ke default (direktori-dulu lalu Modified descending) untuk direktori yang baru dimuat tersebut

#### Bug 3: Tombol Refresh di Server Management Tidak Update Status Ikon

2.5 WHEN user menekan tombol Refresh dan data selesai dimuat THEN the system SHALL menampilkan ikon ✓ untuk setiap server yang memiliki sesi aktif dan ikon ✗ untuk server yang tidak, berdasarkan set sesi aktif pada saat reload selesai

2.6 WHEN tombol Refresh ditekan atau dialog Add/Edit Server ditutup dengan aksi Save THEN the system SHALL memuat ulang data server dari database dan setelahnya memaksa evaluasi ulang semua cell di kolom status

#### Bug 4: Versi Tampil "DEV" Setelah Build Distribusi

2.7 WHEN `./gradlew fatJar` dijalankan (baik langsung maupun sebagai bagian dari build chain) THEN the system SHALL menghasilkan JAR yang berisi file `version.properties` dengan nilai property `version` yang identik dengan nilai `version` di `gradle.properties`, karena task `fatJar` memiliki dependensi eksplisit pada `generateVersionProperties`

2.8 WHEN aplikasi dijalankan dari JAR distribusi yang dihasilkan oleh kondisi 2.7 THEN the system SHALL menampilkan string versi yang identik dengan nilai property `version` di `gradle.properties` pada title bar dan dialog About

2.9 WHEN versi aplikasi ditampilkan di dialog About THEN the system SHALL membaca nilai versi dari `Main.VERSION` (bukan melalui static initializer duplikat di `AboutDialogController`), sehingga `AboutDialogController` tidak memiliki logika pembacaan `version.properties` tersendiri

#### Bug 5: Error Download Multi-Thread (threadCount > 1)

2.10 WHEN setiap thread download membuka channel SFTP baru THEN the system SHALL memanggil connect dengan timeout 30 detik; IF connect gagal atau timeout THEN the system SHALL menutup channel, menandai download sebagai gagal, dan mendekrement latch tepat satu kali sebelum thread keluar

2.11 WHEN semua thread download telah dikirim ke executor THEN the system SHALL menunggu semua thread selesai dengan batas waktu `(fileSize / 102400) + 60` detik, dibulatkan ke atas dan dikunci dalam rentang [60, 3600] detik; IF batas waktu terlampaui THEN the system SHALL memperlakukan kondisi tersebut setara dengan `hasError == true` dan melanjutkan ke langkah cleanup

2.12 WHEN download dinyatakan gagal (hasError adalah true atau latch timeout) THEN the system SHALL menghapus file output dari disk; IF penghapusan gagal karena file tidak ditemukan THEN the system SHALL menganggap kondisi tersebut berhasil; IF penghapusan gagal karena sebab lain THEN the system SHALL mencatat log warning dan melanjutkan tanpa melempar exception ke pemanggil

2.13 WHEN thread download mengalami exception saat membuka atau menggunakan channel SFTP THEN the system SHALL menutup channel tersebut, menset hasError ke true, dan mendekrement latch tepat satu kali; IF latch telah mencapai nol sebelum thread ini selesai THEN the system SHALL tetap melanjutkan tanpa mendekrement ulang

2.14 WHEN `latch.await()` dengan batas waktu mengembalikan false (timeout) THEN the system SHALL mengirim sinyal interrupt ke semua thread yang masih aktif di executor dan menunggu hingga 5 detik untuk thread-thread tersebut berhenti sebelum melanjutkan ke langkah cleanup

#### Bug 6: UnifiedFileManager Tidak Restore Last Directory

2.13 WHEN user bernavigasi ke suatu direktori di File Manager (baik dengan double-click, mengetik path, atau klik favorites) THEN the system SHALL menyimpan path tersebut ke preferensi dengan key `file_manager_last_path_local` untuk lokasi Local atau `file_manager_last_path_<serverName>` untuk lokasi Remote, menggunakan mekanisme `saveOrUpdatePreferences` yang sudah tersedia

2.14 WHEN dialog File Manager dibuka untuk lokasi Local THEN the system SHALL membaca nilai preferensi `file_manager_last_path_local` dan navigate ke path tersebut; IF preferensi tidak ada atau kosong THEN the system SHALL navigate ke home directory sistem sebagai fallback

2.15 WHEN dialog File Manager dibuka untuk lokasi Server (remote) THEN the system SHALL membaca nilai preferensi `file_manager_last_path_<serverName>` dan navigate ke path tersebut setelah koneksi berhasil; IF preferensi tidak ada atau kosong THEN the system SHALL navigate ke default path yang dikonfigurasi di data server sebagai fallback

---

### Unchanged Behavior (Regression Prevention)

#### Bug 1: Status Ikon Koneksi Tidak Reaktif di Server Management

3.1 WHEN user mengetik teks di search field THEN the system SHALL CONTINUE TO memfilter daftar server yang ditampilkan berdasarkan teks tersebut tanpa mengubah data server yang tersimpan di database

3.2 WHEN user menyimpan perubahan di dialog Add/Edit Server THEN the system SHALL CONTINUE TO memuat ulang daftar server dari database setelah dialog ditutup

#### Bug 2: Sorting File Manager Tidak Auto-Sort by Modified Date, Dikelompokkan by Tipe

3.3 WHEN user mengklik header kolom di tabel file THEN the system SHALL CONTINUE TO mengurutkan daftar berdasarkan kolom tersebut: klik pertama pada kolom = ascending, klik kedua pada kolom yang sama = descending; perilaku ini berlaku untuk sesi direktori aktif

3.4 WHEN user mengetik di search field File Manager THEN the system SHALL CONTINUE TO memfilter daftar file berdasarkan nama tanpa mereset sort order yang sedang aktif

#### Bug 3: Tombol Refresh di Server Management Tidak Update Status Ikon

3.5 WHEN user menekan tombol Refresh THEN the system SHALL CONTINUE TO memuat ulang data server dari database (bukan hanya memperbarui tampilan secara visual)

3.6 WHEN user menekan tombol Refresh THEN the system SHALL CONTINUE TO mempertahankan teks pencarian yang sedang aktif di search field selama proses reload

#### Bug 4: Versi Tampil "DEV" Setelah Build Distribusi

3.7 WHEN aplikasi dijalankan dari IDE atau lingkungan di mana `version.properties` tidak tersedia di classpath THEN the system SHALL CONTINUE TO menampilkan string "DEV" sebagai nilai versi fallback

3.8 WHEN `./gradlew build` atau `./gradlew run` dijalankan THEN the system SHALL CONTINUE TO mengikutsertakan `generateVersionProperties` sebagai bagian dari proses build melalui dependensi `processResources` yang sudah ada

#### Bug 5: Error Download Multi-Thread (threadCount > 1)

3.9 WHEN `threadCount == 1` atau ukuran file di bawah 5 MB THEN the system SHALL CONTINUE TO menggunakan `downloadFile()` single-thread tanpa perubahan perilaku

3.10 WHEN download multi-thread berhasil penuh (semua thread selesai tanpa error) THEN the system SHALL CONTINUE TO menghasilkan file output yang lengkap dan melaporkan progress ke `progressCallback` setidaknya satu kali per chunk yang berhasil diselesaikan

3.11 WHEN server SSH tidak mendukung subsistem SFTP (channel dengan tipe "sftp" ditolak atau tidak dikenali oleh server) THEN the system SHALL CONTINUE TO jatuh ke metode download berbasis exec tanpa mencoba concurrent download

#### Bug 6: UnifiedFileManager Tidak Restore Last Directory

3.12 WHEN user mengklik tombol Home di File Manager THEN the system SHALL CONTINUE TO navigate ke home directory sistem (untuk Local) atau default path server (untuk Remote) tanpa mengubah nilai preferensi last-path yang tersimpan

3.13 WHEN server dihapus atau diganti nama di Server Management THEN the system SHALL CONTINUE TO berperilaku normal; nilai preferensi last-path untuk nama server lama yang tidak lagi ada akan diabaikan secara alami saat dialog dibuka untuk server tersebut

3.14 WHEN `PreferenceService` mengembalikan nilai kosong atau tidak menemukan key last-path untuk lokasi yang diminta THEN the system SHALL CONTINUE TO menggunakan path fallback default (home directory untuk Local, default path untuk Remote) tanpa error atau pesan kesalahan kepada user

---

## Bug Condition Summary

```pascal
// Bug 1 & 3 — Status Ikon Stale
FUNCTION isBugCondition_B1(context)
  INPUT: context (dialog dibuka / refresh ditekan)
  RETURN serverTable.refresh() tidak dipanggil setelah filterServers() selesai
END FUNCTION

// Bug 2 — Auto-Sort
FUNCTION isBugCondition_B2(state)
  INPUT: state (file selesai di-load ke tabel)
  RETURN tidak ada TableColumn sort default yang diterapkan programatik saat load
END FUNCTION

// Bug 4 — Versi DEV
FUNCTION isBugCondition_B4(buildCommand)
  INPUT: buildCommand
  RETURN buildCommand = "./gradlew fatJar" AND fatJar.dependsOn tidak include generateVersionProperties
END FUNCTION

// Bug 5 — Download Hang / Corrupt
FUNCTION isBugCondition_B5(download)
  INPUT: download (threadCount > 1, file >= 5MB)
  RETURN channel.connect() tanpa timeout OR latch.await() tanpa timeout
END FUNCTION

// Bug 6 — No Last Dir
FUNCTION isBugCondition_B6(open)
  INPUT: open (dialog file manager dibuka)
  RETURN tidak ada last-path preference tersimpan untuk lokasi ini
END FUNCTION
```

```pascal
// Preservation Goal — berlaku untuk semua bug
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X) = F'(X)  // Perilaku non-buggy tidak berubah
END FOR
```
