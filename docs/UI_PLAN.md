# SeeLoggyPlus — UI / Design Plan

Dokumen ini adalah acuan arah desain (visual language) aplikasi.
Semua perubahan styling mengikuti dokumen ini. Sumber kebenaran warna ada di
`src/main/resources/style/theme.css` (design tokens).

---

## Status

| Fase | Nama | Status |
|------|------|--------|
| 1 | Fondasi design token (`theme.css` + refactor token) | ✅ Selesai |
| 2 | Kunci visual language | ✅ Selesai (lihat di bawah) |
| 3 | Theme pass (terapkan token terpilih) | ✅ Selesai |
| 4 | Konsistensi komponen | ✅ Selesai |
| 5 | Readability log | ✅ Selesai |
| 6 | Polish & aksesibilitas | ✅ Selesai |
| 7 | Dark mode (dipilih) | ✅ Selesai |
| 7b | Density toggle / high-contrast (opsional) | ⏳ Kandidat |

---

## Keputusan Visual Language

- **Style:** Enterprise Data-dense (ala Splunk / Grafana)
- **Accent:** Graphite / neutral gelap (near-monochrome)
- **Density:** Compact
- **Chrome (menubar/toolbar/status bar):** ✅ **Gelap graphite** `#2b3138`
  - Area data tetap terang; chrome gelap untuk kontras tegas (khas pro tool)
  - Teks/ikon chrome menggunakan `-sl-chrome-text` (`#d7dbe0`) dan
    `-sl-chrome-text-muted` (`#8b939c`); hover/pressed chrome di `#3a424b` / `#4a535e`

### Prinsip
Warna hanya dipakai untuk **makna** (severity, status), bukan dekorasi.
Chrome netral graphite; data yang berbicara. Tidak ada aksen biru dekoratif.

---

## 1. Palette

### Netral (graphite / near-monochrome)

| Token | Nilai | Peran |
|-------|-------|-------|
| `-sl-chrome` | `#2b3138` | Menubar / toolbar / status bar (chrome gelap) |
| `-sl-chrome-hover` | `#3a424b` | Hover kontrol chrome |
| `-sl-chrome-pressed` | `#4a535e` | Pressed kontrol chrome |
| `-sl-chrome-text` | `#d7dbe0` | Teks/ikon chrome |
| `-sl-chrome-text-muted` | `#8b939c` | Teks sekunder chrome |
| `-sl-bg` | `#f1f3f5` | Latar area kerja |
| `-sl-surface` | `#ffffff` | Tabel, list, dialog |
| `-sl-surface-alt` | `#f8f9fa` | Zebra / surface alternatif |
| `-sl-surface-sunken` | `#e9ecef` | Header tabel, area detail, chrome terang |
| `-sl-border` | `#ced4da` | Pemisah, grid |
| `-sl-border-soft` | `#e9ecef` | Garis halus |
| `-sl-text` | `#1f2329` | Teks utama |
| `-sl-text-secondary` | `#495057` | Teks sekunder |
| `-sl-text-muted` | `#6c757d` | Label, hint |
| `-sl-text-faint` | `#adb5bd` | Placeholder, ikon nonaktif |

### Accent (interaktif)

| Token | Nilai | Peran |
|-------|-------|-------|
| `-sl-brand` | `#343a40` | Tombol primary, tab aktif, baris terpilih |
| `-sl-brand-hover` | `#212529` | Hover |
| `-sl-brand-pressed` | `#111418` | Pressed |
| `-sl-on-brand` | `#ffffff` | Teks di atas accent |
| `-sl-focus-color` | `#343a40` | Focus ring |

### Semantic (desaturated, hanya untuk status)

| Token | Nilai | Peran |
|-------|-------|-------|
| `-sl-success` | `#2f855a` | Sukses / OK |
| `-sl-warning` | `#b7791f` | Peringatan |
| `-sl-danger` | `#c0392b` | Error / destruktif |
| `-sl-info` | `#2b6cb0` | Informasi |

### Log level (Fase 5 — subtle accent, bukan badge solid)

| Level | Warna teks | Accent bar kiri |
|-------|-----------|-----------------|
| FATAL | `#7f1d1d` | `#b91c1c` |
| ERROR | `#b91c1c` | `#dc2626` |
| WARN  | `#92400e` | `#d97706` |
| INFO  | `#1d4ed8` | `#3b82f6` |
| DEBUG | `#4b5563` | `#9ca3af` |
| TRACE | `#6b7280` | `#d1d5db` |

---

## 2. Tipografi

- **UI:** `Segoe UI` **12px** (control height ~26px).
- **Data / log & angka status:** monospace (`Consolas`) **12px**, tabular.
- **Label mikro** (header tabel, section): **uppercase 11px**, letter-spacing 0.4px, warna muted.

---

## 3. Density (Compact)

- Baris tabel ≈ **24px**, cell padding `2 8`.
- Toolbar icon button **26–28px**.
- Spacing scale: **4 / 8 / 12**.
- Radius: **3px** (kontrol), **4px** (panel/card). Shadow hanya untuk menu/overlay.

---

## 4. Perlakuan Komponen

- **Tab:** kotak, aktif = underline / border-top graphite, bukan fill penuh.
- **Tombol:** default abu netral; primary graphite; destructive = outline merah.
- **Log-level:** accent bar kiri 2–3px + teks berwarna (atau pill uppercase kecil), bukan badge solid.
- **Tabel:** header uppercase muted + grid tipis; hover `#eef1f4`; selection `#dfe3e8`.
- **Status bar:** netral, angka monospace, tinggi compact.
- **Focus ring:** graphite.

---

## 5. Detail Fase

### Fase 3 — Theme pass
- Tulis ulang nilai token di `theme.css`: palette graphite + semantic desaturated.
- Base font 12px; radius 3/4; header tabel uppercase.
- Berlaku otomatis ke seluruh dialog (theme.css sudah di-load global).
- ✅ Diterapkan: chrome gelap via class `app-menubar` / `app-toolbar` / `status-bar`
  (scoped ke main window; toolbar dialog tetap terang karena bersifat aksi in-content),
  badge log-level jadi tint halus, header tabel uppercase di FXML
  (Server Management, File Manager, Remote Log Search).

### Fase 4 — Konsistensi komponen
- Tab kotak (underline aktif), hierarki tombol (primary/secondary/destructive), toolbar compact, form control, focus ring graphite.
- ✅ Diterapkan: tab rectangular dengan accent bar atas graphite + label muted→tegas
  saat aktif; indikator tab (`tab-live-indicator`, `tab-badge-unread`) akhirnya
  terdefinisi; tombol `.btn-secondary` (dipakai di UpdateDialog) membentuk hierarki;
  radius form control seragam; toolbar & split-pane divider konsisten; tooltip
  bertema chrome gelap; placeholder jadi satu komponen `.empty-state-*`.
- ✅ Inline style warna hardcoded di `MainController` dihapus (menimpa CSS):
  background toggle aktif `#2196F3`, toggle prettify, status link hover, dan
  memory bar. Kini warna sepenuhnya dari token/CSS
  (`.status-link`, `.status-memory-bar.memory-ok/warn/high`, `.muted-italic`).
- ✅ Semua dialog kini memuat `components.css` juga (sebelumnya hanya `theme.css`),
  sehingga tab, header tabel, empty-state, form control, dll. konsisten.
- ✅ Chrome gelap diterapkan ke toolbar dialog (File Manager, Server Management)
  via `.app-toolbar`.
- ✅ Popup menu/context-menu: stylesheet di-install di level **Scene**
  (util `AppTheme.scene(root)`), karena popup tidak mewarisi stylesheet dari root
  node. Ditambah styling `.context-menu`/`.menu-item`.
- ✅ `help/style.css` (dibuka di browser) diselaraskan ke palet graphite.

### Fase 5 — Readability log
- Level accent (bukan badge solid), zebra halus, monospace, density 24px.
- ✅ Diterapkan di `CanvasLogViewer` (viewer utama berbasis Canvas, jadi CSS
  `highlight.css` tidak bisa menata isinya):
  - Teks body selalu gelap (`#1f2329`); **hanya token level** yang diwarnai
    (`ERROR #b91c1c`, `WARN #b7791f`, `INFO #2b6cb0`, `DEBUG/TRACE #6c757d`).
  - **Accent bar kiri** 5px di gutter untuk FATAL/ERROR/WARN.
  - **Tint baris** halus untuk FATAL/ERROR (`#fdecec`) dan WARN (`#fdf8e8`).
  - Palet desaturated selaras `theme.css`; selection/scrollbar/line number
    disesuaikan. Font tetap monospace (Consolas).
  - Zebra dilewati (opsional) agar tidak menambah noise pada log padat.

### Fase 6 — Polish & aksesibilitas
- Verifikasi kontras WCAG, keyboard nav, high-contrast.
- ✅ Kontras (rasio WCAG diukur): `-sl-chrome-text-muted` `#8b939c`→`#a0a8b0`
  (4.2→5.45:1), `-sl-text-faint` `#adb5bd`→`#828a91` (2.07→3.5:1, ikon),
  `-sl-warning`/`-sl-syntax-warn` `#b7791f`→`#92400e` (3.6→7.1:1),
  warna WARN canvas disamakan. Token accent terang untuk indikator di chrome gelap:
  `-sl-chrome-success/-warning/-danger` (`#4ade80/#fbbf24/#fca5a5`) dipakai memory gauge.
  `-sl-live` `#4caf50`→`#2f855a` (3.8:1 di header tab).
- ✅ Focus ring: outline terang untuk tombol/toggle di chrome gelap
  (`.app-toolbar`, `.status-bar`); focus color graphite tetap di surface terang.
- ✅ Keyboard: mnemonik menu `_File/_View/_Settings/_Help` (Alt+F/V/S/H).
- ⏳ High-contrast theme: ditunda (butuh toggle di Preferences + set token terpisah);
  dicatat sebagai kandidat Fase 7.

### Fase 7 — Dark mode
- ✅ `style/theme-dark.css` meng-override token di `.root` (dimuat setelah
  `theme.css`/`components.css`) + `-fx-base/-fx-background/-fx-control-inner-background`
  agar kontrol default Modena ikut gelap. Termasuk override syntax richtext &
  level log.
- ✅ `AppTheme` sekarang men-swap stylesheet di level Scene dan menandai root
  dengan class `theme-dark`; popup (menu/context menu) ikut gelap karena mewarisi
  stylesheet Scene. Semua Scene dibuat via `AppTheme.scene(root)`.
- ✅ Switching tahan-error: tiap window diproses terpisah (satu window bermasalah
  tidak membatalkan sisanya). Saat kembali ke light, SEMUA stylesheet yang
  mengandung `theme-dark` dihapus.
- ✅ Regresi dev (`runDev`): dulu saat balik ke light hanya canvas yang berubah
  karena salinan temp `theme-dark` dari DevHotReloader bernama
  `seeloggy-hotreload-XXXX.css` (tak mengandung `theme-dark.css`) sehingga tidak
  terhapus. Kini file temp menyertakan nama sumber
  (`seeloggy-hotreload-theme-dark-XXXX.css`) dan `AppTheme` menghapus URL yang
  mengandung `theme-dark`.
- ✅ `CanvasLogViewer` (yang mengabaikan CSS) mendeteksi class `theme-dark` pada
  scene root dan men-swap paletnya otomatis.
- ✅ Toggle "Dark mode" di `Preferences` (General), disimpan sebagai
  preferensi `app_theme` dan diterapkan langsung (live).
- ✅ `SearchResultPanel` (canvas) ikut men-swap palet saat tema berubah; header
  memakai class `.search-results-header`. `SearchNavigator` memakai
  `.search-navigator-status` (bukan inline style) agar terbaca di chrome gelap.
- ✅ Detail panel (RichTextFX) memakai token `richtext.css`; background/
  syntax mengikuti tema (terverifikasi otomatis).
- ✅ Prettify JSON/XML: segmen tanpa highlight kini diberi class `default`
  (bukan tanpa class) sehingga base text memakai `-sl-syntax-default`. Rule
  default lama (`.styled-text-area .text`) ternyata menimpa warna syntax
  (spesifisitas lebih tinggi) — dihapus. Semua path detail (raw & prettify)
  kini selalu mengeset style spans.
- ✅ Test: `HighlightThemeTest` memverifikasi warna `json-*` dan `xml-*`
  benar di mode light dan dark.
- ✅ Selection teks konsisten di seluruh UI: satu sumber `SelectionColors`
  (light `rgba(52,58,64,.18)`, dark `rgba(110,168,254,.28)`), dipakai canvas log,
  search result panel, dan `.selection` RichTextFX detail panel; kontrol teks
  (TextField/TextArea) memakai `-fx-highlight-fill` dari token `-sl-selection-bg`
  yang sama.
- ✅ About dialog: teks pakai class token (`.about-version`, `.about-copyright`,
  `.about-description`) — sebelumnya `textFill` hardcoded.
- ✅ Update dialog: banner gelap mengikuti chrome (`linear-gradient(-sl-chrome,
  -sl-chrome-hover)`) dan tombol primary/default jadi graphite lebih gelap
  (`#3a424b`) di dark, agar tone-nya match.
- ✅ Tab header: focus-indicator dimatikan sehingga tidak muncul border saat
  fokus berpindah (mis. setelah klik baris log di canvas / klik tab Preferences).
- ✅ Label: `.empty-state-subtitle`, header tabel, `.muted-italic`, dan label
  info Preferences memakai `-sl-text-secondary` (light `#495057` / dark `#b8c0c8`)
  agar ≥ 4.5:1; `textFill="#888888"` inline dihapus.
- ✅ Ikon file/favorit memakai **tone tema** (bukan warna jenuh): folder/log
  `-sl-text-secondary`, file `-sl-text-muted`, bintang favorit `-sl-warning`,
  sehingga menyatu di light maupun dark.
- ✅ Navigasi tombol panah: route UP/DOWN/PAGE/HOME/END dipindah ke **level Scene**
  agar log viewer tetap bergerak walau fokus bukan di canvas (tetap menghormati
  input teks, list/table, dan detail RichTextFX).
- ✅ Tombol step scrollbar (atas/bawah & kiri/kanan) diaktifkan lagi agar bisa
  memindah satu baris dengan klik; scrollbar Find in Files diperlebar (16px,
  thumb min 44px).
- ✅ Seleksi baris preview Find in Files memakai `-sl-selection-bg` + teks tema.
- ✅ Scroll horizontal (atau Shift+wheel) pada header tab memindah tab terpilih.
- ✅ Ikon toggle toolbar: `applyCss()` setelah pewarnaan programatik agar warna
  tema selalu konsisten.
- ✅ Baris/item terpilih saat kontrol tidak fokus: `-fx-selection-bar-non-focused`
  dibuat gelap (`#2f353c`) + `-fx-selection-bar-text` terang di dark, agar ikon
  dan teks yang memakai tone tema tetap terlihat.
- ✅ Placeholder (`-fx-prompt-text-fill`) di-set ke `-sl-text-secondary` pada
  `.text-input` (Modena menurunkan warna terlalu gelap di dark sehingga
  placeholder tak terlihat di Add/Edit Server, search, dsb).
- ✅ Fokus: `-fx-faint-focus-color: transparent` menghapus halo luar Modena,
  menyisakan **satu** focus ring untuk semua komponen (button, combo, spinner,
  checkbox, list, dst) — tidak ada lagi border berlayer.
- ✅ Input field: state fokus memakai satu border bersih
  (`-sl-brand` + `-fx-control-inner-background`), menggantikan 3-layer Modena
  (focus ring + border + inner) yang tampak seperti border ganda.
- ✅ Ikon: default `.glyph-icon`/`FontAwesomeIconView` memakai `-sl-text`
  sehingga kontras di kedua mode. Ikon berwarna (bintang favorit, ikon file)
  dipindah dari `setFill` ke class (`favorite-star`, `file-icon-*`) dengan
  spesifisitas lebih tinggi agar tidak tertimpa.
- Catatan: stylesheet per-node dihapus dari FXML agar tidak bentrok dengan
  swap di Scene; tema sepenuhnya di-install lewat `AppTheme`.

### Fase 7b — Opsional (belum)
- Density toggle (compact/comfortable) dan high-contrast theme.

---

## 5b. Test Otomatis

- `AppThemeTest` — instalasi stylesheet, toggle `theme-dark` + class root,
  scene yang dibuat saat dark, penghapusan semua salinan `theme-dark`
  (termasuk salinan bernama hot-reload) saat balik ke light.
- `ThemeSwitchingTest` — end-to-end lewat checkbox Preferences: dark→light→dark
  berulang, memastikan SEMUA window (main + Preferences) ikut berubah, dan
  salinan dark dari hot-reload ikut terhapus saat balik ke light.
- `DetailPanelThemeTest` — detail panel memuat `richtext.css` dan background
  mengikuti surface terang/gelap.
- `SearchPanelThemeTest` — style class header/status & palet `SearchResultPanel`
  mengikuti tema.
- `CanvasThemeTest` — palet `CanvasLogViewer` mengikuti tema.
- `IconThemeTest` — ikon default kontras mengikuti tema; ikon berwarna tetap.
- `HighlightThemeTest` — warna syntax JSON & XML mengikuti light/dark.
- `SelectionColorConsistencyTest` — warna selection canvas/search/detail dan
  CSS vs konstanta Java konsisten di light & dark.
- `InputContrastTest` — teks input field (TextField/PasswordField/TextArea/
  Spinner editor) DAN **prompt/placeholder** kontras (>= 4.5:1) serta sesuai mode.
- `InputFocusBorderTest` — input field saat fokus hanya satu border (bukan
  border ganda Modena) di kedua mode.
- `IconContrastTest` — ikon kontras (>= 3:1) di setiap state (default, hover,
  pressed, focused, selected) untuk light & dark; plus ikon berwarna file/favorit.
- `LabelContrastTest` — SEMUA label di setiap dialog (termasuk label input field)
  kontras (>= 4.5:1) di light & dark.
- `LogViewerArrowKeyNavigationTest` — tombol panah keyboard (Up/Down) memindah
  baris log viewer lewat navigasi level Scene, meski fokus bukan di canvas.
- `ScrollBarDefectTest` — tombol step (atas/bawah) scrollbar terlihat & berfungsi;
  scrollbar Find in Files cukup lebar (>= 16px, thumb >= 40px).
- `PreviewSelectionThemeTest` — baris preview terpilih memakai warna seleksi
  bersama + teks tema (kontras >= 4.5:1) di light & dark.
- `TabScrollSwitchTest` — scroll horizontal / Shift+wheel pada header tab
  memindah tab terpilih.
- `ToolbarToggleIconThemeTest` — ikon toggle toolbar (Tail/Follow) memakai
  warna tema di semua state, light & dark.
- `SelectedRowInactiveContrastTest` — baris/item terpilih saat kontrol **tidak
  fokus** (TableView/ListView) tetap kontras: ikon >= 3:1 dan teks >= 4.5:1,
  light & dark.
- `FocusBorderLayerTest` — komponen saat fokus (Button, ToggleButton, ComboBox,
  ChoiceBox, Spinner, CheckBox, RadioButton, ListView) hanya punya SATU layer
  border (bukan border berlayer) di light & dark, diverifikasi lewat pixel-scan.
- Infra: test JVM `maxHeapSize = 2g` (banyak scene JavaFX); `RemoteLogSearchDialogControllerTest`
  menetapkan ukuran stage eksplisit agar preview area selalu punya tinggi.
- `DialogThemeTest` — About text, banner Update, dan tombol primary/default
  mengikuti tema gelap.
- `TabFocusIndicatorTest` — tab header tidak memunculkan focus border.
- `DatabaseConfigTest` — DB test terisolasi di `build/test-data`.
- `DetailPanelThemeTest#unstyledDetailTextUsesThemeDefaultFill` — teks tanpa
  style class tidak hitam di mode gelap.
- Catatan: scene pada test dibuat via `AppTheme.scene(root)` supaya token CSS
  ter-resolve (tanpa warning).
- Isolasi data test: `seeloggyplus.dataDir` (`build/test-data`) untuk DB/keystore
  dan `seeloggyplus.launcherProperties` untuk launcher config, sehingga test tidak
  lagi mengotori `.data` (recent files) maupun `launcher.properties` di repo.
- Semua dijalankan headless (`./gradlew test -PheadlessTest`).

## 6. Catatan Teknis

- JavaFX CSS hanya mendukung **looked-up colors** sebagai variabel (wajib berupa warna).
  Spacing/radius/type-scale adalah **konvensi terdokumentasi**, bukan token.
- `theme.css` + `components.css` di-load global pada semua FXML root (main view + dialog),
  dan juga di level Scene via `com.seeloggyplus.util.AppTheme.scene(root)` agar
  popup window (menu, context menu) ikut mewarisi tema. Setiap `new Scene(root)`
  sebaiknya diganti `AppTheme.scene(root)`.
- `highlight.css` saat ini **tidak di-load** di kode mana pun (orphan); ditokenkan
  untuk persiapan Fase 5.
