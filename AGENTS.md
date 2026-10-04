---
description: Binding operating rules for AI coding agents (OpenCode etc.) working in this repository.
applies_to: "**"
---

# SeeLoggyPlus — Agent Rules

JavaFX 21 desktop log viewer for local and SSH files. Single Gradle project.
Entrypoints: `com.seeloggyplus.Main` (JavaFX `Application`) and
`com.seeloggyplus.Launcher` (fat jar / `runDev`). Full release checklist:
`docs/RELEASING.md`.

Rule strength: **MUST** = never skip; **SHOULD** = default, deviate only with a
clear reason; **NEVER** = stop and choose another path.

## 1. Commands

MUST run all Gradle commands through the wrapper with `--no-daemon` on Windows
(or from an agent shell):

| Goal | Command |
| --- | --- |
| Release build (includes checks) | `.\gradlew.bat --no-daemon build` |
| Feature compilation | `.\gradlew.bat --no-daemon classes testClasses` |
| Build artifacts without running tests | `.\gradlew.bat --no-daemon assemble` |
| Full tests (release or explicit request; always headless) | `.\gradlew.bat --no-daemon test` |
| Selected tests | `.\gradlew.bat --no-daemon test --tests "com.seeloggyplus.controller.FooTest"` (repeat `--tests`) |
| Dev app (+ hot reload, sets `seeloggyplus.dev`) | `.\gradlew.bat --no-daemon runDev` |
| Packages/manifests | `fatJar`, `packagePortableNoJre`, `packagePortableWithJre`, `packagePlatform`, `generatePlatformManifest`, `mergeManifests`, `buildUpdateArtifacts` |

MUST keep tests headless (Monocle config in `build.gradle` `test`); MUST NOT make
them windowed or optional.

### 1.1 Known failure modes

- Symptom: shell hangs or `processResources` fails with `Failed to clean up stale outputs`.
  Cause: the Gradle daemon holds `build/resources/main` locks.
  Recovery: `.\gradlew.bat --stop` → `powershell -ExecutionPolicy Bypass -File scripts/stop-app.ps1` → retry.
  MUST NOT treat this as a code failure.
- Symptom: `Select-String ... | Select-Object -First` output looks hung.
  Recovery: use `Get-Content -Tail` or `ForEach-Object { $_.Line }`.

## 2. Feature workflow (MUST follow in order)

1. Write the feature/bug tests FIRST (tests exist before or with the code change).
2. Iterate running only the related test classes (see Selected tests in §1). MUST NOT
   run the full suite during routine feature work. Repeat tests only after a change,
   failure, or unresolved concern justifies it.
3. Once related tests and required smoke checks pass, report the feature ready for
   user validation. Do not automatically expand validation to the full regression
   suite, including after a feature is completed or committed.
4. Run the full suite ONLY when the user explicitly requests it or requests release
   preparation. For a release, run `test`, then `build` (both `--no-daemon`) and
   follow §10. During feature work, MUST NOT invoke `build` or `check` in a way that
   implicitly runs the full suite; use `classes`/`testClasses`, `assemble`, or the
   required packaging task instead.
5. MUST finish the reply with the TEST SUMMARY block printed by the `testSummary` task
   (Total/Passed/Failed/Skipped/Result + failing test names). State which related
   classes were run; a selected-test summary must not be presented as a full-suite
   result. Remove neither the task nor its `finalizedBy 'testSummary'` wiring.
6. UI/theme changes MUST include contrast/size tests covering the affected components.
   Choose the relevant classes, such as `ButtonThemeTest`, `PopupThemeTest`,
   `TreeDropIndicatorTest`, `FileManagerLeftPanelLayoutTest`, `IconContrastTest`,
   and `LabelContrastTest`; do not automatically run unrelated UI regression classes.
7. MUST keep `SelfUpdateEndToEndTest` green; run it for changes covered by §9.
8. MUST NOT make tests pass by disabling/deleting tests, lowering contrast thresholds,
   or encoding the bug into assertions.

## 3. Smoke test (MUST after UI/startup-affecting changes)

1. Launch detached (never foreground):
   `Start-Process cmd.exe -ArgumentList '/c','gradlew.bat --no-daemon runDev > run-dev.log 2>&1' -WindowStyle Hidden`
2. Always stop: `powershell -ExecutionPolicy Bypass -File scripts/stop-app.ps1`
   (the `runDev` process is `com.seeloggyplus.Main`; do not match only `Launcher`).
3. Delete the log file afterwards; logs must not survive at the repo root.

## 4. Test-JVM isolation (WHY tests behave differently)

- `build.gradle` sets `seeloggyplus.dataDir=build/test-data`,
  `seeloggyplus.launcherProperties=build/test-data/...`, and
  `seeloggyplus.disableUpdateCheck=true`.
- Consequence: under tests, `MainController` NEVER opens update / "What's New" dialogs
  and never touches the developer's `.data`. Any new startup popup MUST stay behind
  `seeloggyplus.disableUpdateCheck` or a similarly injectable guard, or the suite breaks.

## 5. Architecture

- Packages: `controller` (FXML controllers), `service`+`service.impl`,
  `repository`+`repository.impl`, `model`, `ui` (`canvas`, `search`, `cell`),
  `update` (self-update), `config` (DB + migrator), `util`.
- Data dir = system property `seeloggyplus.dataDir` (default `.data`):
  SQLite `seeloggyplus.db`, `.keystore`, `known_hosts` (SSH trust-on-first-use),
  `updates/` staging.
- Install layout is blue/green: root holds `current` (text) and
  `versions/<version>/seeloggyplus.jar`; `UpdateLayout.installationRoot()` folds
  `versions/<v>` back up to the root. MUST NOT assume the running jar sits in the
  install root.
- SSH uses the `com.github.mwiede:jsch` fork (ed25519/ecdsa/rsa-sha2). Host keys verify
  against `.data/known_hosts`: unknown → user must trust via fingerprint dialog;
  changed → always refused.
- Performance MUST-haves: no per-cell/per-keystroke DB queries, no heavy work on the
  JavaFX Application Thread, file/SSH I/O off the UI thread; use the existing caches
  and background `Task` patterns.

## 6. Database migrations (MUST)

- Additive-only (add columns/tables), idempotent, transactional, always backed up.
  Bump `DatabaseMigrator.SCHEMA_VERSION`. MUST NOT rename/drop/retype columns.
- Every schema change MUST add a `DatabaseMigrationTest` case: old schema → migrate →
  data intact, idempotent re-run, rollback on failure, backup created.
- Fresh installs get the latest schema from `DatabaseConfig.createTables()`;
  old DBs are upgraded by `DatabaseMigrator`.

## 7. Theme & contrast (MUST)

- Colours only via `-sl-*` tokens; the token set must match across
  Graphite/Light/Dark (`ThemeTokensTest`).
- Chrome icons/text use `-sl-chrome-text` / `-sl-accent-text`.
- MUST NOT lower thresholds in `IconContrastTest` / `LabelContrastTest`.

## 8. Dead code & commits (MUST)

- The change that removes usage MUST also delete now-unused files
  (classes/services/FXML/CSS/scripts/tests). Prove no references first, then run the
  related tests. Full-suite validation follows §2 (release or explicit request only).
- NEVER commit: `docs/UI_PLAN.md`, `docs/FEEDBACK_*.md`, `.jqwik-database`, `bin/`,
  `build/`, `logs/`, `.data/`, `launcher.properties`.
- Check `git status` before committing: relevant files only; no logs, build artifacts,
  or user data. MUST NOT commit unless explicitly asked.

## 9. Self-update checklist (MUST when touching)

Applies when changing the `update` package, packaging tasks, launchers, or the install
layout. Self-update breakage strands users on old versions.

1. `com.seeloggyplus.update.SelfUpdateEndToEndTest` stays green: real package through
   download → stage → activate → health → rollback; a failed download leaves the
   installation untouched.
2. Package entries must stay installable by OLD installers: the E2E pins them to the
   0.5.x whitelist — adding a file to the portable package MUST fail that test.
3. New launcher/helper files are created at runtime (`SilentLauncherInstaller.ensure`),
   NEVER added to the zip.
4. Keep green: `UpdateInstallerTest` (zip-slip/whitelist/checksum/wrapper stripping),
   `UpdateDownloaderTest`, `UpdateCoordinatorTest`, `UpdateBootstrapperTest`,
   `SilentLauncherInstallerTest`.
5. If a release changes packaging: verify the published `update-manifest.json` matches
   the tag and that an older version stages/activates it.
6. NEVER relax the whitelist or checksum validation to make a package install.

## 10. Release (MUST)

Full checklist: `docs/RELEASING.md`. Sequence:

1. Full suite + `build` green, working tree clean (MUST NOT release otherwise).
2. Update `CHANGELOG.md` and write `docs/release-notes/<version>.md`.
3. Bump `version` in `gradle.properties`; commit `chore(release): X.Y.Z` on `dev`.
4. Merge `--no-ff` into `main`; push `main` + `dev`; tag `X.Y.Z` and push the tag
   (triggers the Release workflow). Semver; Conventional Commits
   (`feat|fix|test|docs|refactor|perf|build|ci|chore`).
5. Release description: the GitHub Release body is `docs/release-notes/<version>.md`
   copied verbatim by the workflow; the `CHANGELOG.md` `[X.Y.Z]` section is the
   fallback. Both stay in sync, end-user ready. Notes structure:
   `# SeeLoggyPlus <v>` → `## Overview` → Features/Fixed/Improvements → `## Tests` →
   `## Upgrade notes` → `**Full changelog:**` compare link to the previous version.
6. Published description needs fixing: edit `CHANGELOG.md` + the notes file, push, then
   `gh release edit X.Y.Z --notes-file docs/release-notes/X.Y.Z.md` (or the GitHub API
   via `git credential fill`).
