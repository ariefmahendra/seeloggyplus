# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] - 2026-09-22

### Added
- **All-light theme**: a third, fully light theme (light menu bar / toolbar / status bar
  and light surfaces) alongside the default graphite and dark themes, selectable from
  Preferences → Theme.

### Changed
- Interactive states (hover / selected / pressed / focus) now use dedicated accent
  tokens, so the menu bar, toolbar, status bar and icons consistently follow the active
  theme. In the light theme the accent is blue.
- Toolbar buttons no longer change size when focused or clicked (the focus-ring space is
  reserved); only the colour changes.

### Fixed
- **Release build**: packaged apps no longer report version `DEV`. `version.properties`
  is generated into a declared, cache-correct resource directory and is always included.
- **Recent files**: the last open mode (normal/OPEN vs. live tail) is remembered and the
  entry reopens the same way, instead of a previously tailed file switching to download.
- **Remote files**: opening a file that is already streaming now reuses its tab instead
  of downloading again, which previously failed with an SSH channel error.
- Removed remaining hard-coded component colours and completed per-theme token coverage.

### Tests
- Added `AppVersionTest`, `RecentFileModeRepositoryTest`, `RecentFileOpenModeTest`,
  `LightThemeTest`, `ThemeTokensTest`, `ThemeComponentParityTest`.
- Updated `ThemeSwitchingTest` for the new theme selector.

## [0.4.3] - 2026-09-21

### Fixed
- **Log viewer**: Up/Down (and PageUp/PageDown/Home/End) keys now move the log viewer
  even when focus is not on the canvas. Arrow navigation is installed as a scene-level
  key filter and still respects text inputs, lists/tables and the detail editor.

### Tests
- Added `LogViewerArrowKeyNavigationTest`.

## [0.4.2] - 2026-09-21

### Fixed
- **Log viewer**: the vertical scrollbar step (up/down) buttons are visible again and
  clicking them moves exactly one row; the scrollbar is slightly wider for easier clicking.
- **Main view**: horizontal scroll / Shift+wheel over the tab header no longer skips tabs —
  it is throttled (threshold + cooldown) so one gesture moves one tab.

### Tests
- Added `CanvasScrollStepTest`; hardened `TabScrollSwitchTest`.

## [0.4.1] - 2026-09-21

### Fixed
- **Find in Files**: wider, easier-to-grab scrollbars; the selected preview line now uses
  the shared selection colour with readable, theme-matched text in both light and dark modes.
- **Log viewer**: restored the scrollbar step buttons so a row can be stepped by click.
- **Main view**: horizontal scroll over the tab header moves the selected tab; toolbar
  toggle icons (Tail, Smart Follow) keep the theme colour in every state.

### Tests
- Added `ScrollBarDefectTest`, `PreviewSelectionThemeTest`, `TabScrollSwitchTest`,
  `ToolbarToggleIconThemeTest`.

## [0.4.0] - 2026-09-20

### Added
- Token-based design system (`theme.css`, `theme-dark.css`) installed at scene level so the
  main window, every dialog and all popups stay consistent.
- Full **dark mode** with a live toggle in Preferences (persisted as `app_theme`).
- Enterprise, data-dense graphite visual language (dark chrome, neutral surfaces, compact density).

### Changed
- Redesigned log canvas readability: neutral body text, subtle severity accents, row tints.
- Unified text-selection colour across canvas, search panel, detail panel and text controls.
- Consistent tabs, tables, forms, menus, context menus, tooltips and empty states.
- JSON/XML prettify syntax highlighting is now themed in both light and dark modes.

### Fixed
- WCAG-oriented contrast for labels, input text/placeholders, icons (all states) and status indicators.
- Single focus ring on all controls (removed layered/double borders).
- Inactive selected rows keep their theme background so text and icons remain visible.

### Tests
- Added `AppThemeTest`, `DetailPanelThemeTest`, `DialogThemeTest`, `FocusBorderLayerTest`,
  `HighlightThemeTest`, `IconContrastTest`, `IconThemeTest`, `InputContrastTest`,
  `InputFocusBorderTest`, `LabelContrastTest`, `SelectedRowInactiveContrastTest`,
  `SelectionColorConsistencyTest`, `TabFocusIndicatorTest`, `ThemeSwitchingTest`,
  `CanvasThemeTest`, `SearchPanelThemeTest`, `DatabaseConfigTest`.
- Test data isolation via `seeloggyplus.dataDir`, so the suite no longer touches local
  recent files or preferences.

## [0.3.0] - 2026-09-19

### Added
- Multi-platform packages (Windows/Linux, with and without bundled JRE) and merged update manifest.
- Improved update UI.

[Unreleased]: https://github.com/ariefmahendra/seeloggyplus/compare/0.5.0...HEAD
[0.5.0]: https://github.com/ariefmahendra/seeloggyplus/compare/0.4.3...0.5.0
[0.4.3]: https://github.com/ariefmahendra/seeloggyplus/compare/0.4.2...0.4.3
[0.4.2]: https://github.com/ariefmahendra/seeloggyplus/compare/0.4.1...0.4.2
[0.4.1]: https://github.com/ariefmahendra/seeloggyplus/compare/0.4.0...0.4.1
[0.4.0]: https://github.com/ariefmahendra/seeloggyplus/compare/0.3.0...0.4.0
[0.3.0]: https://github.com/ariefmahendra/seeloggyplus/releases/tag/0.3.0
