# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.6.4] - 2026-10-01

Patch release that keeps a newly installed self-update active across subsequent
launches from an older root launcher.

### Fixed
- **Self-update startup health**: a newly activated version now confirms itself as
  healthy before rollback is evaluated. Previously, the first successful start could
  move `current` back to the previous release before writing the health marker, so the
  next run from the existing root `launcher.bat` reopened the old version.
- **Legacy launcher continuity**: the unchanged, version-aware 0.5.1 root launcher now
  continues to resolve `current` to the latest staged JAR after the update restarts.

### Tests
- Extended `UpdateBootstrapperTest` and `SelfUpdateEndToEndTest` to cover first-start
  confirmation, preservation of the active pointer and rollback when a different
  unhealthy version is active.
- Verified the complete 0.5.1 upgrade path with artifacts built from the 0.5.1 tag:
  HTTP download, checksum, stage, activate, first start, and a second launch through
  the unchanged 0.5.1 BAT all selected the updated JAR.
- All 496 headless tests pass, and the production startup smoke test succeeds.

## [0.6.3] - 2026-09-30

Patch release that makes self-update work again from older versions.

### Fixed
- **Self-update from 0.5.x**: 0.5.1 rejected the 0.6.2 package because it contained
  `SeeLoggyPlus.vbs`, a file the old installer's content whitelist does not know. The
  portable packages no longer ship that file; the app recreates the silent Windows
  launcher in the installation root on startup when it is missing, so updates from
  older releases stage and activate normally again.

### Tests
- Added `SelfUpdateEndToEndTest`: a realistic portable package goes through the real
  download (injected stream) → stage → activate → health → rollback flow, a failed
  download leaves the installation untouched, and the package entries are pinned
  against the older 0.5.x installer whitelist.
- Added `SilentLauncherInstallerTest`; `WindowsLauncherTest` now guards that the
  silent launcher is never part of a package.

## [0.6.2] - 2026-09-30

**First published build of the 0.6 line.** The 0.6.0 and 0.6.1 release workflows failed
in the CI `test` job before publishing, so 0.6.2 ships the complete 0.6 feature set.

### Added
- **SSH private-key authentication**: per-server auth method (password or private key),
  key file picker and optional encrypted passphrase. Modern keys (ed25519, ecdsa,
  rsa-sha2) are supported.
- **Host-key verification (trust-on-first-use)**: an app-managed `known_hosts` file, a
  fingerprint confirmation dialog, and a hard refusal when a saved host key changes
  (possible MITM).
- **Server groups in File Management**: WinSCP/MobaXterm-style Locations tree with
  persistent nested groups, New/Rename/Delete, Move to group and drag & drop (dragging
  never connects; click or Enter opens a location).
- **Recent Files tree**: entries grouped per server with expandable nodes; search
  matches server, file name and path across all words.
- **Windows silent launcher** (`SeeLoggyPlus.vbs`); `launcher.bat --console` for
  diagnostics.
- **Double-click preference** (Open/Download or Tail) in Preferences  General.

### Changed
- Server Management is CRUD-only (favorite, grouping and drag-to-reorder moved out).
- Buttons and dropdowns consistently follow the theme tokens, with readable
  focus/hover/pressed states and neutral dropdown selection.
- File Management's left panel is compact (icon action bar, collapsible Favorite
  folders) and the footer no longer reports cache internals.
- Reloading an active remote tail reconnects off the UI thread.

### Fixed
- **Release pipeline**: the CI `test` job failed for both 0.6.0 and 0.6.1 because
  `UnifiedFileManagerDialogControllerTest.testServerConnectionFailure` asserted the
  path is not `/` after falling back to local — wrong on Linux, where the local home
  is `/`. The test now asserts the active location is local and the path equals the
  local home.
- Connect failures show the real reason (missing key file, auth failure) instead of a
  generic message; the Server editor form scrolls and keeps its action buttons visible.
- Database upgrades v3 (`server_groups`) and v4 (auth columns) are additive,
  transactional and automatically backed up.

### Tests
- 472 headless tests green, including migrations, auth persistence/encryption,
  known-hosts trust-on-first-use, connect flow, theme contrast, grouping and reload
  responsiveness.

## [0.6.1] - 2026-09-30

### Fixed
- **Release pipeline**: the 0.6.0 CI run failed before publishing a release, so
  0.6.1 is the first published build of the 0.6 line with the same feature set.
  Failing tests are now listed on the workflow run summary (visible without
  admin access to logs), and two UI assertions were hardened so they are stable
  across CI machines:
  - the remote-tail reload test now blocks the fake connect on a latch instead
    of using wall-clock sleeps;
  - the Browse button size assertions use platform-tolerant tolerances;
  - the Gradle `testSummary` now lists the failing test names and messages.

## [0.6.0] - 2026-09-30

### Added
- **SSH private-key authentication**: per-server auth method (password or private key),
  key file picker and optional encrypted passphrase. Modern keys (ed25519, ecdsa,
  rsa-sha2) are supported.
- **Host-key verification (trust-on-first-use)**: an app-managed `known_hosts` file,
  a confirmation dialog showing the key fingerprint, and a hard refusal when a saved
  host key changes (possible MITM).
- **Server groups in File Management**: the Locations panel is a WinSCP/MobaXterm-style
  tree with persistent groups that can contain other groups (nested paths), New/Rename/
  Delete actions on the folder menu, Move to group on the server menu, and drag & drop
  with a size-stable drop indicator.
- **Recent Files tree**: entries are grouped per server and the group can be expanded or
  collapsed; search matches server, file name and path across all words.
- **Windows silent launcher** (`SeeLoggyPlus.vbs`) so the packaged app starts without a
  terminal window; `launcher.bat --console` remains for diagnostics.
- **File double-click preference** (Open/Download or Tail) in Preferences  General.

### Changed
- Server Management is now CRUD-only: favorite, grouping and drag-to-reorder controls
  were removed (grouping lives in File Management).
- Locations open on click/Enter only; selecting a server (e.g. starting a drag) never
  connects by itself.
- Buttons and dropdown fields follow the theme tokens: readable focus/hover/pressed
  states, no default JavaFX look, neutral dropdown selection (light in Graphite/Light),
  and no white outline around dark-filled controls.
- File Management's left panel is compact (icon action bar, collapsible Favorite folders)
  and the footer no longer reports cache internals.
- Reloading an active remote tail reconnects off the UI thread, so the window no longer
  freezes while the SSH handshake runs.

### Fixed
- Connect failures now show the real reason (missing key file, auth failure) instead of a
  generic "check credentials" message.
- The Server editor form scrolls and keeps Test Connection / Cancel / Save visible in
  every auth mode; the Browse button is compact and proportional to its field.
- Database schema upgrades: v3 adds the `server_groups` table (backfilled from existing
  group names) and v4 adds the auth columns, both additive and transactional with an
  automatic backup.

### Tests
- Added `DatabaseMigrationTest`, `ServerAuthPersistenceTest`, `SshKnownHostsTest`,
  `SshAuthConfigTest`, `SshConnectFlowTest`, `SshServiceImplErrorTest`,
  `ServerEditDialogAuthTest`, `RemoteReloadResponsivenessTest`,
  `ServerManagementCleanupTest`, `ServerGroupDropTest`, `TreeDropIndicatorTest`,
  `FileManagerGroupingTest`, `FileManagerLeftPanelLayoutTest`, `RecentFilesTreeTest`,
  `RecentFileReadabilityTest`, `DropdownConsistencyTest`, `PopupThemeTest`,
  `ButtonThemeTest`, `FindInFilesFocusTest`, `FeedbackUiSmokeTest`,
  `WindowsLauncherTest`, `FileDoubleClickPreferenceTest`.
- 472 headless tests green before release.

## [0.5.1] - 2026-09-22

### Fixed
- **Self-update**: downloads are staged in the application data folder
  (`<data>/updates`) that already exists, instead of the system temp directory.
- **Self-update**: the installer locates `seeloggyplus.jar` from the known package
  layout instead of recursively walking the extracted files.
- **Self-update**: the installation root is resolved correctly for the blue/green
  `versions/<version>` layout, so subsequent updates stage and activate in the right
  place.

### Tests
- Added `UpdateLayoutTest`; extended `UpdateInstallerTest` (no recursive jar search)
  and `UpdateDialogControllerTest` (staging directory lives in the app data folder).

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

[Unreleased]: https://github.com/ariefmahendra/seeloggyplus/compare/0.6.4...HEAD
[0.6.4]: https://github.com/ariefmahendra/seeloggyplus/compare/0.6.3...0.6.4
[0.6.3]: https://github.com/ariefmahendra/seeloggyplus/compare/0.6.2...0.6.3
[0.6.2]: https://github.com/ariefmahendra/seeloggyplus/compare/0.6.1...0.6.2
[0.6.1]: https://github.com/ariefmahendra/seeloggyplus/compare/0.6.0...0.6.1
[0.6.0]: https://github.com/ariefmahendra/seeloggyplus/compare/0.5.1...0.6.0
[0.5.1]: https://github.com/ariefmahendra/seeloggyplus/compare/0.5.0...0.5.1
[0.5.0]: https://github.com/ariefmahendra/seeloggyplus/compare/0.4.3...0.5.0
[0.4.3]: https://github.com/ariefmahendra/seeloggyplus/compare/0.4.2...0.4.3
[0.4.2]: https://github.com/ariefmahendra/seeloggyplus/compare/0.4.1...0.4.2
[0.4.1]: https://github.com/ariefmahendra/seeloggyplus/compare/0.4.0...0.4.1
[0.4.0]: https://github.com/ariefmahendra/seeloggyplus/compare/0.3.0...0.4.0
[0.3.0]: https://github.com/ariefmahendra/seeloggyplus/releases/tag/0.3.0
