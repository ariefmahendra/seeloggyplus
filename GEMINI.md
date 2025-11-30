# Project Context: SeeLoggyPlus
Ini adalah aplikasi desktop log viewer menggunakan JavaFX.

## Code Style & Conventions
1. **Lombok:** Selalu gunakan anotasi Lombok (`@Getter`, `@Setter`, `@Slf4j`, dll) untuk mengurangi boilerplate code.
2. **JavaFX:**
   - Pastikan update UI dilakukan di dalam `Platform.runLater()` jika dipanggil dari background thread.
   - Gunakan file FXML untuk layout, jangan membuat UI murni lewat Java code kecuali mendesak.
3. **Logging:** Gunakan `logger.info()`, `logger.warn()`, atau `logger.error()`. JANGAN gunakan `System.out.println()`.
4. **Concurrency:** Gunakan `Task<T>` atau `Service<T>` dari JavaFX untuk operasi berat agar UI tidak freeze.

## Specific Implementation Details
- **Parsing:** Parsing configuration selalu di-handle oleh `ParsingConfigService`.
- **SSH:** Koneksi remote menggunakan `SSHServiceImpl`. Pastikan session ditutup atau dikelola dengan baik.

## Behavior
- Jika memodifikasi file UI (`.fxml` atau Controller), pastikan logic binding-nya tidak lepas.
- Berikan komentar singkat pada logic yang kompleks dengan bahasa inggris.
- selalu gunakan bahasa inggris yang tepat dan profesional
- anda hanya diperbolehkan untuk mengedit, menghapus, menambah, dan membaca filenya.
- anda tidak diperbolehkan untuk menjalankan gradle dan semacamnya, jika ada perintah untuk melakukan hal tersebut silahkan perintahkan ke saya
- anda dilarang untuk mengakses gradle