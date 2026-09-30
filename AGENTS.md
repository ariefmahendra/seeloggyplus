# AGENTS.md — SeeLoggyPlus Project Rules

Binding guide for anyone (human or agent) working in this repository.

## 1. Testing (MANDATORY)

- **Always run unit tests** for every new feature or bug fix. No code change without relevant tests.
- **Testing is always headless.** The Monocle/headless configuration is permanently in `build.gradle` (the `test` block). Do not remove it, disable it, or move it behind an optional flag.
- Test command: `.\gradlew.bat test` (Windows) or `./gradlew test` (Linux/macOS).
- When run from a Windows terminal/agent, pass `--no-daemon` (e.g. `.\gradlew.bat --no-daemon test`) so the shell does not hang waiting for daemon handles.
- **Always include the test summary** at the end of the work. The `testSummary` task prints the following block after tests finish (it also runs when tests fail):

  ```
  ==================== TEST SUMMARY ====================
    Total   : <n>
    Passed  : <n>
    Failed  : <n>
    Skipped : <n>
    Result  : PASS|FAIL
  ======================================================
  ```

  Do not remove the `testSummary` task or the `finalizedBy 'testSummary'` wiring on the `test` task. The task also prints the names and messages of failing tests immediately after the block.
- Fix failing tests before declaring work done. Never disable/delete tests to "green" the build.

### 1a. Per-feature testing flow (MANDATORY for agents/opencode)

1. **Write/update unit tests first** for the feature or bug being worked on; do not wait until all work is done.
2. **Run only the tests related to that feature** while iterating (fast), using the `--tests` filter, e.g.:
   `.\gradlew.bat --no-daemon test --tests "com.seeloggyplus.controller.NamaControllerTest" --tests "com.seeloggyplus.service.NamaServiceTest"`
   - Pick the test classes that directly cover the changed files (controller/service/repository/UI cell).
   - Do not run the entire suite repeatedly while a feature is still in progress.
3. **Once the feature tests are green and the changes are final**, run the full suite once:
   `.\gradlew.bat --no-daemon test` then `.\gradlew.bat --no-daemon build`.
4. Always show the TEST SUMMARY block from the last run (feature or full suite) at the end of the answer.
5. For UI/theme changes, include the relevant contrast/size tests (e.g. `ButtonThemeTest`, `PopupThemeTest`, `TreeDropIndicatorTest`, `FileManagerLeftPanelLayoutTest`) as part of the feature tests, not only in the full suite.
6. Never change a test to match a bug (for example loosening contrast thresholds or removing assertions) just to make the build green.
7. **Self-update E2E is mandatory**: `SelfUpdateEndToEndTest` must exist and stay green; extend it whenever the update/packaging code or the portable package layout changes (see §9).

## 2. Build & Run (MANDATORY)

- Ensure `.\gradlew.bat build` succeeds before closing the work.
- Smoke test the application with `.\gradlew.bat runDev` (dev mode + hot reload) or `launcher.bat --console` to watch the console log.
- Do not commit while build/tests fail. Do not commit unless explicitly asked.

### 2a. Application process & smoke test (MANDATORY)

- Run the smoke test **detached** and with `--no-daemon` (the Gradle daemon keeps a file lock on `build/resources/main` even after the app is closed), never in the foreground:
  `Start-Process cmd.exe -ArgumentList '/c','gradlew.bat --no-daemon runDev > run-dev.log 2>&1' -WindowStyle Hidden`
- **Always stop the application after the smoke test**:
  `powershell -ExecutionPolicy Bypass -File scripts/stop-app.ps1`
  The `runDev` instance runs as `com.seeloggyplus.Main` (not only `Launcher`), so do not match on the Launcher name alone.
- If `processResources` fails with `Failed to clean up stale outputs` (a typical Windows file lock), do: `.\gradlew.bat --stop` → `scripts/stop-app.ps1` → retry the build/test command. Do not treat this as a code failure.
- When inspecting test output, do not use `Select-Object -First` on a `Select-String` pipeline (it makes the shell look like it is hanging); use `Get-Content -Tail` or `ForEach-Object { $_.Line }`.

## 3. Desktop Application Performance

- Prioritize performance: avoid per-cell/per-keystroke database queries, avoid heavy work on the JavaFX Application Thread, and use caches/background `Task`s as the existing patterns do.
- Run file/SSH I/O outside the UI thread.

## 4. Database & Migrations

- Migrations are **additive-only** (add columns/tables only), **idempotent**, **transactional**, and **always backed up** before migrating. Never rename/drop a column or change its type.
- Every schema change must add/update a test in `DatabaseMigrationTest` (old schema → migrate → data intact, idempotent, rollback on failure).
- Fresh installs use the latest schema in `createTables()`; old databases are upgraded by `DatabaseMigrator`.

## 5. Theme & Contrast

- All colours must use tokens (`-sl-*`); tokens must be identical across the three theme files (Graphite/Light/Dark) — enforced by `ThemeTokensTest`.
- Icons/text on chrome must use `-sl-chrome-text`/`-sl-accent-text`. Contrast test thresholds (`IconContrastTest`, `LabelContrastTest`) must never be lowered.

## 6. Stack

- Java 21, JavaFX 21 via the Gradle wrapper, SQLite (xerial), JUnit 5 + TestFX, Lombok.
- Run Gradle commands through the wrapper (`gradlew`), not a global Gradle installation.

## 7. File hygiene & dead code (MANDATORY)

- Every refactor/fix must **delete files that are no longer used**: dead classes/services/repositories/UI components, unused FXML/CSS/scripts, and tests that cover deleted code.
- Before deleting, prove there are no references by searching the class/API name in `src/` (including FXML/CSS/tests). After deleting, run the related tests (§1a) and then the full suite.
- Do not leave local artifacts at the repository root: test/build/run-dev logs, temporary diagnostics (`*.log`, `focus-report.txt`, etc.). Delete them or make sure they are in `.gitignore`.
- Files that must never be committed: personal working/feedback documents (`docs/UI_PLAN.md`, `docs/FEEDBACK_*.md`), `.jqwik-database`, `bin/`, `build/`, `logs/`, `.data/`, `launcher.properties` (see `.gitignore`). Documents such as `FEEDBACK_IMPLEMENTATION.md` are kept locally as notes only; do not bring them into git.
- When committing: inspect `git status`; only relevant files may be included. Never commit logs/build artifacts or user data.

## 8. Release (MANDATORY)

- The release process **must follow `docs/RELEASING.md`** (the full checklist lives there).
- Flow summary: full test suite green (§1a) → `.\gradlew.bat --no-daemon build` → update `CHANGELOG.md` → bump the version in `gradle.properties` → commit `chore(release): X.Y.Z` on `dev` → merge `--no-ff` into `main` → push `main` + `dev` → tag `X.Y.Z` and push the tag (this triggers the Release workflow).
- Semver: MAJOR = breaking, MINOR = feature, PATCH = bug fix. Commit messages follow Conventional Commits (`feat|fix|test|docs|refactor|perf|build|ci|chore`).
- Do not release with a dirty working tree, failing tests, or a failing build. All changes must be committed and green.
- Per-version release notes live in `docs/release-notes/<version>.md`; this document is part of the release process and **may** be committed (unlike the personal working/feedback documents in §7).

### 8a. Standard release-description format (MANDATORY)

- The **GitHub Release body is taken verbatim** from the `[X.Y.Z]` section in `CHANGELOG.md` by the workflow. That section must therefore be complete and end-user ready, not a one-line internal note.
- Standard order inside the `[X.Y.Z]` section in `CHANGELOG.md`:
  1. a short opening paragraph (optional, e.g. "First published build of the X line.");
  2. `### Added`, then `### Changed`, then `### Fixed`, then `### Tests` (only the relevant sections, in this order);
  3. upgrade notes when needed.
  For the first release of a feature line, summarize the main features under `### Added` — do not list only the CI fix.
- `docs/release-notes/<version>.md` is mandatory and follows the standard structure:
  `# SeeLoggyPlus <version>` → `## Overview` → `## Features` / `## Fixed` / `## Improvements` (whichever apply) → `## Tests` → `## Upgrade notes` → a `**Full changelog:**` line with the compare link to the previous version.
- If a release body was already published with the wrong format: fix `CHANGELOG.md` + `docs/release-notes/<version>.md`, push, then edit the release:
  `gh release edit X.Y.Z --notes-file docs/release-notes/X.Y.Z.md` (requires `gh auth login`).
  Without `gh`, use `git credential fill` + the GitHub API `PATCH /repos/<owner>/<repo>/releases/<id>` with the release-notes file as the body.

## 9. Self-update checklist (MANDATORY for agents/opencode)

Self-update is a critical path: a broken package or installer can strand users on an old
version. Before declaring work done on anything touching packaging tasks, the `update`
package, the launcher scripts or the install layout, verify:

1. **E2E test green** — `com.seeloggyplus.update.SelfUpdateEndToEndTest` covers a
   realistic portable package (wrapper folder, jar, launchers, help) through
   download → stage → activate → health → rollback, and proves a failed download leaves
   the current installation untouched.
2. **Package compatibility** — portable packages may only contain entries that older
   released installers accept. The E2E test pins the package entries against the 0.5.x
   whitelist: adding a new file to the package must make that test fail.
3. **No new packaged helper files** — create launcher/helper files at runtime instead
   (see `SilentLauncherInstaller.ensure(...)`), never by adding them to the zip.
4. **Installer safety tests stay green** — `UpdateInstallerTest` (zip-slip, whitelist,
   checksum, wrapper stripping), `UpdateDownloaderTest` (resume/cancel/checksum),
   `UpdateCoordinatorTest`, `UpdateBootstrapperTest` and `SilentLauncherInstallerTest`.
5. **Release smoke** — when a release ships packaging/update changes, confirm the
   workflow publishes packages whose `update-manifest.json` matches the tagged version,
   and that an update from the previous version stages and activates (the E2E test, or
   `runDev -PupdateManifestUrl=...` for a manual check).
6. Never relax the whitelist or checksum validation just to make a package install.
