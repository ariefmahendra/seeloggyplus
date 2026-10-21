# Warna state tombol

21 Oktober 2026.

Ikon tombol menjadi biru saat hover, pressed/armed, selected, menu terbuka, atau fokus keyboard. Latar interaksi menggunakan abu-abu netral sesuai tema Light/Dark. Teks label tetap memakai warna teks tema; teks aksi destruktif tetap merah dengan kontras yang memadai. Tombol disabled memakai ikon redup. Pola yang sama berlaku pada ikon outline panel bawah, panah breadcrumb, dan hover ikon tutup tab.

Kontras ikon minimal 3:1 dan teks minimal 4,5:1 dipertahankan. Pengujian memeriksa kestabilan ukuran tombol, state disabled, pergantian tema, toolbar, dialog, menu, dan panel detail. Screenshot headless empat ukuran pada kedua tema tersedia di `build/workbench-review/`.

Validasi terakhir: 69/69 tes headless lulus pada 14 kelas terpilih berikut. Ini bukan hasil full suite.

- `com.seeloggyplus.app.ButtonColorStatesTest`
- `com.seeloggyplus.app.ButtonThemeTest`
- `com.seeloggyplus.app.CompactActionButtonsTest`
- `com.seeloggyplus.app.IconContrastTest`
- `com.seeloggyplus.app.LabelContrastTest`
- `com.seeloggyplus.app.PopupThemeTest`
- `com.seeloggyplus.app.SearchPreviewDialogThemeTest`
- `com.seeloggyplus.app.ThemeComponentParityTest`
- `com.seeloggyplus.app.WorkbenchCaptureTest`
- `com.seeloggyplus.app.WorkbenchChromeRevisionTest`
- `com.seeloggyplus.features.detail.presentation.DetailPanelThemeTest`
- `com.seeloggyplus.features.files.presentation.FilePathBreadcrumbTest`
- `com.seeloggyplus.features.search.presentation.SearchPanelThemeTest`
- `com.seeloggyplus.shared.util.ThemeTokensTest`

Smoke native dijalankan detached menggunakan `runDev`: aplikasi berhasil dibuka, tanpa kesalahan resolusi CSS. Aplikasi dihentikan melalui `scripts/stop-app.ps1`, dan `run-dev.log` di root dihapus.
