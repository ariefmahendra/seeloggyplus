# SeeLoggyPlus Bugfix Batch — Design Document

## Overview

Dokumen ini merancang fix untuk 6 bug di SeeLoggyPlus yang sudah dianalisis di `bugfix.md`.
Setiap fix mengikuti prinsip **minimal diff**: ubah sesedikit mungkin, reuse pola yang sudah ada,
tidak tambah abstraksi baru.

Bug dikelompokkan berdasarkan lokasi fix:

| # | Bug | File yang diubah |
|---|-----|-----------------|
| 1 & 3 | Status ikon stale (open + refresh) | `ServerManagementDialogController.java` |
| 2 | Auto-sort file manager | `UnifiedFileManagerDialogController.java` |
| 4 | Versi "DEV" di fat JAR | `build.gradle` + `AboutDialogController.java` |
| 5 | Download multi-thread hang/corrupt | `SSHServiceImpl.java` |
| 6 | File manager tidak restore last directory | `UnifiedFileManagerDialogController.java` |

---

## Glossary

- **Bug_Condition (C)**: Kondisi yang men-trigger bug — input atau state yang menyebabkan perilaku salah.
- **Property (P)**: Perilaku yang diharapkan ketika C(input) = true setelah fix diterapkan.
- **Preservation**: Perilaku non-buggy yang tidak boleh berubah setelah fix.
- **F**: Fungsi/kode original (sebelum fix).
- **F'**: Fungsi/kode setelah fix.
- **`serverTable.refresh()`**: Method JavaFX `TableView` yang memaksa evaluasi ulang semua cell factory,
  termasuk `statusColumn` yang membaca `SSHSessionManager.getActiveSessionKeys()` secara real-time.
- **`filterServers()`**: Method di `ServerManagementDialogController` yang mengisi `filteredServers`
  dari `allServers` — dipanggil di `loadServers().onSucceeded`.
- **`SortedList`**: JavaFX wrapper di `UnifiedFileManagerDialogController` yang mengikat
  `fileTable.comparatorProperty()`. Default sort diatur via `fileTable.getSortOrder()`.
- **`downloadFileConcurrent()`**: Method di `SSHServiceImpl` yang membagi file ke N thread SFTP.
- **`CountDownLatch`**: Sinkronisasi thread — `latch.await(timeout)` mengembalikan `false` jika timeout.
- **`saveOrUpdatePreferences()`**: Upsert di `PreferenceServiceImpl` — pattern yang sudah dipakai
  untuk `window_x`, `window_y`, dll.
- **`generateVersionProperties`**: Gradle task yang menulis `version.properties` ke
  `$buildDir/resources/main/`. Sudah ada, tapi `fatJar` tidak `dependsOn` task ini.

---

## Bug Details

### Bug 1 & 3: Status Ikon Stale di Server Management

#### Bug Condition

Bug 1 terjadi saat dialog dibuka ulang. Bug 3 terjadi saat tombol Refresh ditekan.
Root cause identik: setelah `filterServers()` dipanggil dan `filteredServers` diisi ulang,
`serverTable.refresh()` tidak dipanggil, sehingga `statusColumn.cellFactory` tidak dievaluasi ulang.

```
FUNCTION isBugCondition_B1B3(event)
  INPUT: event — dialog dibuka ATAU tombol Refresh ditekan
  OUTPUT: boolean

  filterServers() dipanggil   // filteredServers diisi ulang
  RETURN serverTable.refresh() TIDAK dipanggil setelahnya
END FUNCTION
```

**Contoh konkret:**
- Buka File Manager → koneksi ke server A (session aktif). Tutup FM.
  Buka Server Management → ikon server A tetap ✗ (stale dari sebelum sesi dibuat).
- Tekan Refresh → data di-reload dari DB, ikon tetap tidak update karena `refresh()` tidak dipanggil.

#### Root Cause

Di `loadServers().setOnSucceeded`:
```java
allServers.clear();
allServers.addAll(task.getValue());
filterServers(searchField.getText());  // mengisi filteredServers
// ← MISSING: serverTable.refresh()
```

`filteredServers` di-replace elemennya, tapi cell factory untuk `statusColumn` tidak dipanggil ulang
karena JavaFX tidak menganggap ini sebagai "perubahan item yang sudah ada". `refresh()` memaksa
evaluasi ulang semua cell, yang akan membaca `SSHSessionManagerImpl.getInstance().getActiveSessionKeys()`
secara fresh.

### Fix Implementation

**File**: `ServerManagementDialogController.java`  
**Method**: `loadServers().task.setOnSucceeded` (satu lokasi, bukan dua)

```java
task.setOnSucceeded(e -> {
    allServers.clear();
    allServers.addAll(task.getValue());
    filterServers(searchField.getText());
    serverTable.refresh(); // ← tambahkan satu baris ini
    logger.info("Loaded {} servers", allServers.size());
});
```

Bug 1 dan Bug 3 keduanya di-fix oleh perubahan ini karena refresh button sudah memanggil
`loadServers()` (`refreshButton.setOnAction(e -> loadServers())`), dan dialog open memanggil
`loadServers()` dari `initialize()`.

---

### Bug 2: Sorting File Manager Tidak Auto-Sort by Modified Date, Dikelompokkan by Tipe

#### Bug Condition

Setelah `loadFiles()` selesai dan `allFiles.setAll(loadedFiles)`, `fileTable` tidak memiliki
sort order default, sehingga file tampil dalam urutan dari filesystem.

```
FUNCTION isBugCondition_B2(state)
  INPUT: state — file selesai di-load ke tabel
  OUTPUT: boolean

  RETURN fileTable.getSortOrder().isEmpty()
         OR sort order yang aktif bukan (direktori-dulu, Modified descending)
END FUNCTION
```

**Contoh konkret:**
- Navigasi ke `/home/user` → file muncul dalam urutan alfabetis atau urutan inode.
- Direktori dan file tercampur tanpa pengelompokan.

#### Root Cause

`setupFileTable()` membuat `SortedList` yang bind ke `fileTable.comparatorProperty()`, tapi tidak
pernah memanggil `fileTable.getSortOrder().setAll(modifiedColumn)` atau sejenisnya.

Default sort harus: direktori di atas file (berdasarkan `isDirectory()`), lalu dalam masing-masing
grup sort by `modifiedColumn` descending.

Karena `SortedList` bind ke `fileTable.comparatorProperty()`, kita tinggal set sort order di
`fileTable` dan reset setiap navigasi ke direktori baru.

### Fix Implementation

**File**: `UnifiedFileManagerDialogController.java`  
**Methods**: `setupFileTable()` + `loadFiles().setOnSucceeded`

Di `setupFileTable()`, setelah `fileTable.setItems(sortedData)`, set sort order awal:
```java
modifiedColumn.setSortType(TableColumn.SortType.DESCENDING);
fileTable.getSortOrder().setAll(iconColumn, modifiedColumn);
// iconColumn dipakai sebagai proxy tipe (direktori vs file) karena
// FileInfo.isDirectory() bisa kita expose lewat comparator iconColumn, ATAU
// kita tambahkan comparator custom di SortedList
```

Karena `iconColumn` bertipe `String` kosong, kita tidak bisa sort direktori-dulu lewat column itu.
Pendekatan paling minimal: override `SortedList` comparator saat load selesai, atau gunakan
`Comparator` default di `setupFileTable()`.

Pendekatan yang lebih bersih tanpa mengubah arsitektur: set `SortedList` dengan comparator tetap
untuk sort default, dan reset comparator tersebut setiap navigasi ke direktori baru.

**Implementasi**: Di `loadFiles().setOnSucceeded`, setelah `allFiles.setAll(loadedFiles)`:
```java
// Reset ke default sort: direktori dulu, lalu modified descending, lalu nama ascending
modifiedColumn.setSortType(TableColumn.SortType.DESCENDING);
fileTable.getSortOrder().setAll(modifiedColumn); // trigger sort
// Direktori-dulu: gunakan custom comparator di SortedList (sudah ada sortedData)
```

Karena `SortedList.comparatorProperty()` sudah bound ke `fileTable.comparatorProperty()`,
kita cukup set `fileTable.getSortOrder()`. Untuk direktori-dulu, kita perlu kolom yang bisa
di-sort by `isDirectory()`. Solusi: buat `typeColumn` (sudah ada di FXML) memiliki comparator
yang memprioritaskan direktori, lalu sort by typeColumn, lalu modifiedColumn.

Atau — lebih minimal — di `setupFileTable()` set custom comparator di `SortedList` sebagai fallback:

```java
// Di setupFileTable(), sebelum bind:
sortedData.setComparator(Comparator
    .comparing((FileInfo f) -> f.getName().equals("..") ? 0 : (f.isDirectory() ? 1 : 2))
    .thenComparing(f -> f.getModified() != null ? f.getModified() : java.time.LocalDateTime.MIN,
                   Comparator.reverseOrder())
    .thenComparing(f -> f.getName().toLowerCase()));
sortedData.comparatorProperty().bind(fileTable.comparatorProperty());
```

Masalah: `bind()` akan override comparator yang kita set. Solusi: gunakan pattern
"comparator dari table, atau default kalau null":

```java
// ponytail: SortedList dengan fallback ke default comparator saat table comparator null
Comparator<FileInfo> defaultSort = Comparator
    .comparing((FileInfo f) -> f.getName().equals("..") ? 0 : (f.isDirectory() ? 1 : 2))
    .thenComparing(f -> f.getModified() != null ? f.getModified() : java.time.LocalDateTime.MIN,
                   Comparator.reverseOrder())
    .thenComparing(f -> f.getName().toLowerCase());

sortedData.comparatorProperty().bind(
    fileTable.comparatorProperty().map(c -> c != null ? c : defaultSort)
);
```

Dan saat navigasi ke direktori baru (di `loadFiles().setOnSucceeded`), reset sort override:
```java
fileTable.getSortOrder().clear(); // kembali ke defaultSort
```

---

### Bug 4: Versi "DEV" di Fat JAR

#### Bug Condition

```
FUNCTION isBugCondition_B4(buildCommand)
  INPUT: buildCommand
  OUTPUT: boolean

  RETURN buildCommand = "./gradlew fatJar"
         AND fatJar task TIDAK memiliki dependsOn generateVersionProperties
END FUNCTION
```

**Contoh konkret:**
- `./gradlew fatJar` → JAR berisi `version.properties` dengan nilai "DEV" atau tidak ada file ini.
- App dijalankan → title bar: "SeeLoggyPlus - Log Viewer vDEV".

#### Root Cause

1. **`fatJar` tanpa dependensi**: Task `fatJar` di `build.gradle` hanya `dependsOn jar` (implisit
   via `with jar`), tapi tidak `dependsOn generateVersionProperties`. Task `generateVersionProperties`
   menulis ke `$buildDir/resources/main/`, yang kemudian diinclude oleh `jar` — tapi hanya jika
   `processResources` (yang `dependsOn generateVersionProperties`) sudah jalan duluan.

2. **DRY violation**: `AboutDialogController` memiliki static initializer identik dengan `Main.java`
   untuk membaca `version.properties`. Ini redundan: `Main.VERSION` sudah tersedia sebagai static field.

### Fix Implementation

**File 1**: `build.gradle`

```groovy
tasks.register('fatJar', Jar) {
    // ...existing config...
    dependsOn processResources  // ← tambahkan ini
    // atau lebih spesifik: dependsOn generateVersionProperties
}
```

`dependsOn processResources` lebih aman karena `processResources` sudah `dependsOn generateVersionProperties`
dan memastikan semua resources (bukan hanya version.properties) sudah di-process sebelum JAR dibuat.

**File 2**: `AboutDialogController.java`

Hapus static initializer dan field `VERSION`, ganti referensi ke `Main.VERSION`:

```java
// HAPUS:
// private static final String VERSION;
// static { ... baca version.properties ... }

// UBAH di initialize():
versionLabel.setText("Version " + Main.VERSION);
```

---

### Bug 5: Error Download Multi-Thread (threadCount > 1)

#### Bug Condition

```
FUNCTION isBugCondition_B5(download)
  INPUT: download — panggilan downloadFileConcurrent() dengan threadCount > 1, fileSize >= 5MB
  OUTPUT: boolean

  RETURN channel.connect() dipanggil TANPA timeout
         OR latch.await() dipanggil TANPA timeout
END FUNCTION
```

**Contoh konkret:**
- Server SSH membatasi max 2 concurrent channels. Download dengan `threadCount=4` →
  thread 3 & 4 hang di `channel.connect()` tanpa batas waktu → seluruh operasi hang selamanya.
- Salah satu thread gagal → `hasError = true` → download dianggap gagal → file output
  (sudah dialokasi penuh dengan `setLength(fileSize)`) dibiarkan di disk dalam kondisi corrupt.

#### Root Cause

Di `downloadFileConcurrent()`:

```java
// Bug A: no timeout
channel.connect(); // ← harus channel.connect(30_000)

// Bug B: no timeout on latch
latch.await(); // ← harus latch.await(timeoutSec, TimeUnit.SECONDS)

// Bug C: no cleanup on failure
// setelah latch: tidak ada penghapusan file jika hasError = true
```

### Fix Implementation

**File**: `SSHServiceImpl.java`  
**Method**: `downloadFileConcurrent()`

**Perubahan A — connect timeout**:
```java
channel.connect(30_000); // 30 detik timeout
```

**Perubahan B — latch timeout**:
```java
// Hitung timeout: (fileSize / 100KB) + 60s, clamped ke [60, 3600]
long timeoutSec = Math.max(60, Math.min(3600, (fileSize / 102_400) + 60));
boolean completed = latch.await(timeoutSec, TimeUnit.SECONDS);
if (!completed) {
    hasError.set(true);
    executor.shutdownNow();
    executor.awaitTermination(5, TimeUnit.SECONDS);
}
```

**Perubahan C — cleanup on failure**:
```java
executor.shutdown();
boolean success = !hasError.get();
if (!success) {
    try {
        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(localPath));
    } catch (java.nio.file.NoSuchFileException ignored) {
        // already gone, fine
    } catch (IOException e2) {
        logger.warn("Failed to delete partial download file: {}", localPath, e2);
    }
}
return success;
```

**Perubahan D — exception handling per thread** (latch tetap decrement):
```java
} catch (Exception e) {
    logger.error("Error in download thread {}: {}", threadId, e.getMessage());
    hasError.set(true);
} finally {
    if (channel != null)
        channel.disconnect();
    latch.countDown(); // sudah ada, pastikan tidak double-decrement
}
```

Latch saat ini sudah di `finally`, jadi perubahan D hanya memastikan `channel.disconnect()`
dipanggil sebelum `countDown` — sudah benar di kode existing.

---

### Bug 6: UnifiedFileManager Tidak Restore Last Directory

#### Bug Condition

```
FUNCTION isBugCondition_B6(open)
  INPUT: open — dialog File Manager dibuka untuk lokasi Local atau Remote
  OUTPUT: boolean

  RETURN preferenceService.getPreferencesByCode(lastPathKey).isEmpty()
         // karena belum pernah disimpan — tidak ada saveOrUpdatePreferences()
         // dipanggil saat navigasi atau saat dialog ditutup
END FUNCTION
```

**Contoh konkret:**
- User navigasi ke `/var/log/nginx`. Tutup dialog. Buka ulang → kembali ke home directory.
- User navigasi ke `/opt/app/logs` di server "prod". Tutup. Buka ulang → kembali ke `server.defaultPath`.

#### Root Cause

`handleLocationSelected()` memanggil `navigateTo(localFileService.getHomeDirectory())` (local) atau
`navigateTo(server.getDefaultPath())` (remote) tanpa membaca preferensi last-path. Dan `navigateTo()`
atau `closeDialog()` tidak pernah memanggil `saveOrUpdatePreferences()`.

`PreferenceService.saveOrUpdatePreferences()` sudah ada dan digunakan oleh `Main.java` untuk
window position — pattern yang sama bisa langsung dipakai.

### Fix Implementation

**File**: `UnifiedFileManagerDialogController.java`

**Tambah field**:
```java
private PreferenceService preferenceService;
```

**Di `initialize()`**:
```java
preferenceService = new PreferenceServiceImpl();
```

**Helper untuk preference key** (satu baris):
```java
private String lastPathKey() {
    return currentLocation == null || currentLocation.server == null
        ? "file_manager_last_path_local"
        : "file_manager_last_path_" + currentLocation.server.getName();
}
```

**Di `navigateTo()`**, simpan path ke preferensi setelah update `currentPath`:
```java
currentPath = normalizedNewPath;
pathField.setText(currentPath);
// Simpan last path (kecuali navigasi ke "..")
if (!currentPath.equals("..") && preferenceService != null) {
    preferenceService.saveOrUpdatePreferences(new Preference(lastPathKey(), currentPath));
}
loadFiles(currentPath);
```

**Di `handleLocationSelected()`**, baca last-path saat memilih lokasi:
```java
if (location.server == null) {
    String lastPath = preferenceService.getPreferencesByCode(lastPathKey())
        .filter(p -> !p.isBlank())
        .orElse(localFileService.getHomeDirectory());
    navigateTo(lastPath);
} else {
    activeSshService = sshServiceFactory.get();
    connectToRemote(location.server); // navigateTo dipanggil di dalam setelah connect
}
```

Untuk remote, last-path dibaca di `connectToRemote().setOnSucceeded`, menggantikan
`navigateTo(server.getDefaultPath())`:
```java
String lastPath = preferenceService.getPreferencesByCode(lastPathKey())
    .filter(p -> !p.isBlank())
    .orElse(server.getDefaultPath() != null ? server.getDefaultPath() : "/");
navigateTo(lastPath);
```

Catatan: `lastPathKey()` untuk remote menggunakan `server.getName()`, bukan server object langsung,
sesuai requirements 2.13–2.15. Jika server dihapus/rename, key lama diabaikan secara alami (3.13).

---

## Correctness Properties

Property 1: Bug 1 & 3 — Status Ikon Selalu Fresh Saat Load Selesai

_For any_ reload event (dialog dibuka atau tombol Refresh ditekan) di mana `loadServers()` berhasil
selesai, fungsi yang sudah difix SHALL memanggil `serverTable.refresh()` setelah `filterServers()`,
sehingga setiap cell di `statusColumn` dievaluasi ulang berdasarkan `SSHSessionManager.getActiveSessionKeys()`
pada saat load selesai.

**Validates: Requirements 2.1, 2.2, 2.5, 2.6**

---

Property 2: Bug 2 — Default Sort Diterapkan Setelah Setiap Load

_For any_ load direktori (startup, navigasi, refresh), setelah `allFiles.setAll()` selesai, tabel
SHALL menampilkan entri `..` di atas (jika ada), diikuti direktori (diurutkan modified descending,
tie-break nama ascending case-insensitive), diikuti file (urutan yang sama). Saat user belum pernah
mengklik header kolom, sort ini SHALL aktif secara default.

**Validates: Requirements 2.3, 2.4**

---

Property 3: Bug 4 — Fat JAR Berisi version.properties yang Benar

_For any_ eksekusi `./gradlew fatJar` (langsung atau via chain), JAR yang dihasilkan SHALL berisi
`version.properties` dengan nilai `version` identik dengan `gradle.properties`. `AboutDialogController`
SHALL membaca versi dari `Main.VERSION`, bukan dari static initializer tersendiri.

**Validates: Requirements 2.7, 2.8, 2.9**

---

Property 4: Bug 5 — Download Multi-Thread Tidak Hang, File Bersih Saat Gagal

_For any_ panggilan `downloadFileConcurrent()` dengan `threadCount > 1`:
- Setiap `channel.connect()` SHALL memiliki timeout 30 detik.
- `latch.await()` SHALL memiliki timeout yang dihitung dari ukuran file, clamped ke [60, 3600] detik.
- Jika `hasError = true` atau latch timeout, file output SHALL dihapus dari disk.

**Validates: Requirements 2.10, 2.11, 2.12, 2.13, 2.14**

---

Property 5: Bug 6 — Last Directory Di-restore Saat Dialog Dibuka

_For any_ pembukaan dialog File Manager setelah user pernah bernavigasi ke suatu direktori,
dialog SHALL navigate ke direktori terakhir yang dikunjungi (dari preferensi). Jika preferensi
tidak ada atau kosong, SHALL fallback ke home directory (local) atau `server.defaultPath` (remote).
Navigasi ke direktori baru SHALL menyimpan path ke preferensi menggunakan `saveOrUpdatePreferences()`.

**Validates: Requirements 2.13, 2.14, 2.15**

---

Property 6: Preservation — Semua Perilaku Non-Buggy Tidak Berubah

_For any_ input di mana tidak satu pun dari bug condition di atas terpenuhi (filter/search,
klik kolom untuk manual sort, mouse click pada button, single-thread download, navigasi Home),
kode yang sudah difix SHALL menghasilkan perilaku yang identik dengan kode original.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10, 3.11, 3.12, 3.13, 3.14**

---

## Expected Behavior

Lihat `bugfix.md` bagian **Expected Behavior (Correct)** — Requirements 2.1 hingga 2.15.
Ringkasan per bug:

- **Bug 1 & 3**: Setelah load/refresh selesai, ikon ✓/✗ mencerminkan state sesi aktif *saat itu*.
- **Bug 2**: Daftar file selalu muncul dengan direktori di atas, diurutkan by modified descending.
- **Bug 4**: `./gradlew fatJar` menghasilkan JAR dengan `version.properties` yang benar; `AboutDialogController` tidak duplikasi logika baca versi.
- **Bug 5**: Download multi-thread tidak hang; file output dihapus jika download gagal.
- **Bug 6**: Dialog File Manager membuka direktori terakhir yang dikunjungi; navigasi menyimpan path ke preferensi.

### Preservation Requirements

**Unchanged Behaviors:**
- Filter/search di Server Management tetap bekerja saat reload (3.1, 3.2, 3.5, 3.6).
- Manual sort override di file manager tetap bekerja setelah user klik header kolom (3.3, 3.4).
- `"DEV"` tetap muncul saat `version.properties` tidak ada di classpath / run dari IDE (3.7).
- `./gradlew build` tetap mengikutsertakan `generateVersionProperties` via `processResources` (3.8).
- Single-thread download (`threadCount=1` atau file < 5MB) tidak terpengaruh (3.9, 3.10, 3.11).
- Tombol Home di File Manager tetap navigate ke home/default path, tidak mengubah preferensi (3.12).
- Preferensi last-path untuk server yang sudah dihapus/rename diabaikan secara natural (3.13, 3.14).

## Hypothesized Root Cause

| Bug | Root Cause | Confidence |
|-----|-----------|-----------|
| 1 & 3 | `loadServers().setOnSucceeded` tidak memanggil `serverTable.refresh()` setelah `filterServers()` — JavaFX tidak re-evaluate cell factory untuk item yang sudah ada di list | Tinggi — confirmed dari kode |
| 2 | `setupFileTable()` tidak menetapkan default sort order; `SortedList` comparator hanya bind ke table comparator yang awalnya null | Tinggi — confirmed dari kode |
| 4a | `fatJar` tidak `dependsOn processResources` (atau `generateVersionProperties`), sehingga `version.properties` belum ada di `buildDir` saat `fatJar` berjalan langsung | Tinggi — confirmed dari `build.gradle` |
| 4b | `AboutDialogController` menduplikasi static initializer `Main.java` alih-alih membaca `Main.VERSION` | Tinggi — confirmed dari kode |
| 5a | `channel.connect()` dipanggil tanpa timeout — jika server membatasi concurrent channels, thread ke-N+1 hang indefinitely | Tinggi — confirmed dari kode |
| 5b | `latch.await()` tanpa timeout — satu thread hang = seluruh download hang | Tinggi — confirmed dari kode |
| 5c | Tidak ada cleanup file output ketika `hasError = true` — file sudah di-`setLength(fileSize)` jadi terisi nol, bukan file valid | Tinggi — confirmed dari kode |
| 6 | `handleLocationSelected()` dan `connectToRemote().setOnSucceeded` tidak membaca preferensi last-path; `navigateTo()` tidak menyimpan path ke `PreferenceService` | Tinggi — confirmed dari kode; `PreferenceService` + `saveOrUpdatePreferences()` sudah tersedia |

## Fix Implementation

### Changes Required

**File 1**: `src/main/java/com/seeloggyplus/controller/ServerManagementDialogController.java`
- Tambah `serverTable.refresh()` di `loadServers().setOnSucceeded` setelah `filterServers()`.
- **1 baris tambahan**.

**File 2**: `src/main/java/com/seeloggyplus/controller/UnifiedFileManagerDialogController.java`
- `setupFileTable()`: bind `sortedData.comparatorProperty()` dengan fallback ke default comparator
  (direktori-dulu, modified descending, nama ascending).
- `loadFiles().setOnSucceeded`: tambah `fileTable.getSortOrder().clear()` untuk reset ke default sort
  setiap navigasi ke direktori baru.
- Tambah field `preferenceService`, inisialisasi di `initialize()`.
- Tambah helper `lastPathKey()`.
- `navigateTo()`: simpan path ke preferensi setelah update `currentPath`.
- `handleLocationSelected()` (local branch): baca preferensi last-path, fallback ke home.
- `connectToRemote().setOnSucceeded`: baca preferensi last-path, fallback ke `server.defaultPath`.

**File 3**: `build.gradle`
- Tambah `dependsOn processResources` ke task `fatJar`.
- **1 baris tambahan**.

**File 4**: `src/main/java/com/seeloggyplus/controller/AboutDialogController.java`
- Hapus static initializer + field `VERSION`.
- Ganti `VERSION` di `initialize()` dengan `Main.VERSION`.

**File 5**: `src/main/java/com/seeloggyplus/service/impl/SSHServiceImpl.java` — method `downloadFileConcurrent()`:
- `channel.connect()` → `channel.connect(30_000)`.
- `latch.await()` → `latch.await(timeoutSec, TimeUnit.SECONDS)` dengan hitung timeout + shutdown executor.
- Tambah cleanup: hapus file output jika `hasError = true` atau latch timeout.

---

## Testing Strategy

### Validation Approach

Testing mengikuti dua fase: (1) jalankan test eksplorasi di kode *unfixed* untuk konfirmasi root cause,
(2) jalankan fix-checking dan preservation-checking di kode *fixed*.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexample yang membuktikan bug *sebelum* fix diterapkan.

**Bug 1 & 3**:
- Test: buat mock `SSHSessionManager` yang mengembalikan session aktif untuk server X.
  Panggil `loadServers()` (atau tombol Refresh). Assert bahwa cell di `statusColumn` untuk server X
  menampilkan ikon ✓.
- Expected failure (unfixed): cell masih menampilkan ✗ karena `refresh()` tidak dipanggil.

**Bug 2**:
- Test: load direktori dengan campuran file dan folder. Assert bahwa baris pertama (selain `..`)
  adalah direktori, dan direktori diurutkan by modified descending.
- Expected failure (unfixed): urutan acak / alfabetis tanpa pengelompokan.

**Bug 4**:
- Test: jalankan `./gradlew fatJar` tanpa `processResources`. Buka JAR, assert `version.properties`
  berisi versi yang benar (bukan "DEV").
- Expected failure (unfixed): `version.properties` tidak ada atau berisi "DEV".

**Bug 5**:
- Test: mock SSH session yang hanya mengizinkan 1 concurrent channel. Panggil
  `downloadFileConcurrent()` dengan `threadCount=2`. Assert bahwa operasi selesai dalam < 35 detik
  (tidak hang).
- Expected failure (unfixed): test hang tanpa batas waktu.

**Bug 6**:
- Test: simulate navigasi ke `/tmp/test-dir`, tutup dialog, buka ulang. Assert bahwa
  `currentPath` dimulai dari `/tmp/test-dir`, bukan home directory.
- Expected failure (unfixed): `currentPath` = home directory.

### Fix Checking

```
FOR ALL input WHERE isBugCondition(input) DO
  result := fixedFunction(input)
  ASSERT property(result)   // sesuai Property 1–5 di atas
END FOR
```

### Preservation Checking

```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT F(input) = F'(input)
END FOR
```

PBT direkomendasikan untuk preservation checking karena men-generate banyak kombinasi input
secara otomatis, terutama untuk:
- Bug 5: random `threadCount=1` + berbagai ukuran file — perilaku single-thread tidak boleh berubah.
- Bug 6: navigasi ke path yang sama berulang kali — preferensi harus idempoten.
- Bug 2: klik header kolom setelah navigasi — manual sort override harus tetap bekerja.

### Unit Tests

- Bug 1 & 3: unit test `loadServers()` dengan mock `serverService` dan mock `SSHSessionManager`.
  Assert `serverTable.refresh()` dipanggil (via spy atau observer).
- Bug 2: unit test `setupFileTable()` dengan list FileInfo campuran. Assert urutan output.
- Bug 4: unit test `AboutDialogController.initialize()` — assert `versionLabel.getText()`
  mengandung `Main.VERSION` (bukan hasil baca ulang properties).
- Bug 5: unit test `downloadFileConcurrent()` dengan mock SFTP channel yang delay connect 35 detik.
  Assert method return dalam < 40 detik dan file output dihapus.
- Bug 6: unit test `navigateTo()` — assert `preferenceService.saveOrUpdatePreferences()` dipanggil
  dengan key dan path yang benar.

### Property-Based Tests

- Generate random list FileInfo (campuran direktori/file, random modified timestamps).
  Property: setelah default sort, semua direktori mendahului semua file; dalam masing-masing grup,
  modified timestamps non-increasing.
- Generate random `fileSize` dan `threadCount`. Property: timeout yang dihitung selalu dalam [60, 3600].
- Generate random preference key/value pairs. Property: `saveOrUpdatePreferences()` diikuti
  `getPreferencesByCode()` selalu mengembalikan nilai yang tersimpan.

### Integration Tests

- End-to-end: buka Server Management setelah membuat sesi via File Manager → ikon ✓ tampil benar.
- End-to-end: navigasi ke direktori di File Manager → tutup → buka ulang → path yang sama tampil.
- Build: `./gradlew clean fatJar` → jalankan JAR → versi di title bar sesuai `gradle.properties`.
- Download: download file > 5MB dengan `threadCount=2` di server yang mendukung concurrent SFTP →
  file output identik byte-per-byte dengan `scp` reference download.
