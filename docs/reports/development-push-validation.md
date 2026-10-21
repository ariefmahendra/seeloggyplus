# Validasi push dev — 21 Oktober 2026

Versi aplikasi tetap 0.8.0. Perubahan pengembangan dicatat sebagai Unreleased; tidak ada tag atau rilis baru.

Validasi akhir: 325/325 tes lulus pada 39 kelas terkait, seluruhnya headless, tanpa failed/skipped. Ini bukan full suite atau validasi rilis. Gradle dijalankan melalui wrapper dengan --no-daemon.

Dokumentasi seeloggyplus-doc: Astro check tanpa error/warning/hint, build produksi dan validasi link berhasil, 103/103 tes Playwright lulus. Halaman pratinjau tersedia dalam bahasa Inggris dan Indonesia, ditandai belum dirilis, dengan screenshot build pengembangan. Browser diperiksa pada 390 dan 1440 px, Light/Dark. Panduan stabil dan unduhan tetap 0.8.0.

Smoke aplikasi telah memverifikasi startup sukses setelah perubahan splash/tema. Aplikasi dihentikan melalui scripts/stop-app.ps1 dan log root dihapus. Laporan performance-audit.md tetap mencatat workload yang lebih lambat dan target optimasi yang belum tercapai.

## Kelas aplikasi yang dijalankan

- `com.seeloggyplus.app.ButtonColorStatesTest`
- `com.seeloggyplus.app.ButtonThemeTest`
- `com.seeloggyplus.app.CompactActionButtonsTest`
- `com.seeloggyplus.app.DocumentationScreenshotGeneratorTest`
- `com.seeloggyplus.app.FileManagerLeftPanelLayoutTest`
- `com.seeloggyplus.app.IconContrastTest`
- `com.seeloggyplus.app.LabelContrastTest`
- `com.seeloggyplus.app.LandingScreenshotGeneratorTest`
- `com.seeloggyplus.app.MainControllerHelperLogicTest`
- `com.seeloggyplus.app.MainControllerReachableFlowsTest`
- `com.seeloggyplus.app.MainViewCompactLayoutTest`
- `com.seeloggyplus.app.MainViewFxmlLoadTest`
- `com.seeloggyplus.app.MainViewIconsTest`
- `com.seeloggyplus.app.MainViewIslandLayoutTest`
- `com.seeloggyplus.app.PreferencesDialogControllerTest`
- `com.seeloggyplus.app.SearchFeatureE2ETest`
- `com.seeloggyplus.app.SearchPreviewDialogThemeTest`
- `com.seeloggyplus.app.SearchResultsRegressionTest`
- `com.seeloggyplus.app.ThemeComponentParityTest`
- `com.seeloggyplus.app.WorkbenchCaptureTest`
- `com.seeloggyplus.app.WorkbenchChromeRevisionTest`
- `com.seeloggyplus.app.WorkbenchLayoutTest`
- `com.seeloggyplus.features.detail.presentation.DetailPanelThemeTest`
- `com.seeloggyplus.features.detail.presentation.LogDetailPaneAsyncCoverageTest`
- `com.seeloggyplus.features.detail.presentation.LogDetailPaneControllerTest`
- `com.seeloggyplus.features.files.presentation.FilePathBreadcrumbTest`
- `com.seeloggyplus.features.logs.infrastructure.io.SpoolingTailHistoryTest`
- `com.seeloggyplus.features.search.presentation.SearchPanelThemeTest`
- `com.seeloggyplus.features.search.presentation.SearchResultPanelBehaviorTest`
- `com.seeloggyplus.features.servers.presentation.ServerManagementCompactLayoutTest`
- `com.seeloggyplus.shared.model.LogEntryTest`
- `com.seeloggyplus.shared.ui.canvas.CanvasLogViewerInteractionTest`
- `com.seeloggyplus.shared.ui.canvas.CanvasSelectionRenderingRegressionTest`
- `com.seeloggyplus.shared.ui.canvas.CanvasThemeTest`
- `com.seeloggyplus.shared.util.IntArrayListTransferTest`
- `com.seeloggyplus.shared.util.LineOffsetIndexAllocationTest`
- `com.seeloggyplus.shared.util.ReaderAllocationTest`
- `com.seeloggyplus.shared.util.StartupSplashStatusTest`
- `com.seeloggyplus.shared.util.ThemeTokensTest`
