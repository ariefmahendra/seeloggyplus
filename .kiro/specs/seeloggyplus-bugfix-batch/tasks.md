# Implementation Plan

## Overview

Fix 6 bugs in SeeLoggyPlus following the exploratory bugfix workflow:
explore (write tests before fix), preserve (write preservation tests), implement, validate.
All fixes follow minimal-diff principles — no new abstractions, no new dependencies.

## Tasks

- [x] 1. Write bug condition exploration tests (BEFORE implementing any fix)
  - **Property 1: Bug Condition** - Status Ikon Stale, Auto-Sort Missing, DEV Version, Download Hang/Corrupt, No Last Dir
  - **CRITICAL**: These tests MUST FAIL on unfixed code — failure confirms the bugs exist
  - **DO NOT attempt to fix the tests or the code when they fail**
  - **GOAL**: Surface counterexamples that demonstrate each bug exists
  - **Scoped PBT Approach**: Each test is scoped to the concrete failing case per bug condition
  - Write one exploration test per bug:
    - **Bug 1 & 3**: Mock `SSHSessionManager` returning an active session for server X. Call `loadServers()` (simulating dialog open) then again via Refresh button action. Assert `statusColumn` cell for server X shows ✓. On unfixed code: cell shows ✗ because `serverTable.refresh()` is never called after `filterServers()`.
    - **Bug 2**: Load a directory with mixed files and folders (varied `modified` timestamps). Assert first non-`..` rows are directories sorted modified descending, followed by files sorted modified descending. On unfixed code: order is filesystem-order (random/alphabetic, no grouping).
    - **Bug 4**: Run `./gradlew fatJar` (no prior `processResources`). Open the JAR and assert `version.properties` contains the version from `gradle.properties` (not "DEV" or absent). On unfixed code: property is "DEV" or file is missing.
    - **Bug 5**: Mock SFTP session allowing max 1 concurrent channel. Call `downloadFileConcurrent()` with `threadCount=2` on a ≥5 MB file. Assert the call returns within 40 seconds. On unfixed code: call hangs indefinitely.
    - **Bug 6**: Simulate navigation to `/tmp/test-dir`, close dialog, reopen for same location. Assert `currentPath` starts at `/tmp/test-dir`. On unfixed code: `currentPath` = home directory.
  - Run each test on UNFIXED code
  - **EXPECTED OUTCOME**: Each test FAILS (this is correct — it proves the bugs exist)
  - Document counterexamples found per bug to confirm root cause before fixing
  - Mark task complete when all tests are written, run, and failures are documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.10, 1.11, 1.12, 1.13, 1.14, 1.15_

- [x] 2. Write preservation property tests (BEFORE implementing any fix)
  - **Property 2: Preservation** - All Non-Buggy Behaviors Unchanged
  - **IMPORTANT**: Follow observation-first methodology — observe unfixed code output, then encode it
  - Observe and write property-based tests for the following non-buggy behaviors:
    - **Bug 1 & 3 preservation**: Filter/search in Server Management still filters correctly after reload (requirements 3.1, 3.2, 3.5, 3.6). Generate random search strings and server lists — assert filtered result matches expected subset both before and after fix.
    - **Bug 2 preservation**: After user clicks a column header (manual sort override), the sort stays active and is not reset until navigation to a new directory (requirement 3.3). Also: search/filter does not reset active sort (3.4).
    - **Bug 4 preservation**: When `version.properties` is absent from classpath (IDE run), version reads as "DEV" (3.7). `./gradlew build` still works without change (3.8).
    - **Bug 5 preservation**: For `threadCount==1` or file < 5 MB, the single-thread `downloadFile()` path is used unchanged (3.9, 3.10, 3.11). Generate random `(threadCount=1, fileSize)` pairs — assert behaviour identical to unfixed code.
    - **Bug 6 preservation**: Home button always navigates to home/default path and does NOT update the last-path preference (3.12). `preferenceService.getPreferencesByCode(lastPathKey)` returning empty causes fallback without error (3.13, 3.14).
  - Run all preservation tests on UNFIXED code
  - **EXPECTED OUTCOME**: All tests PASS (this confirms the baseline to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10, 3.11, 3.12, 3.13, 3.14_

- [x] 3. Fix Bug 1 & 3 — Status ikon stale di Server Management

  - [x] 3.1 Add `serverTable.refresh()` in `loadServers().setOnSucceeded`
    - File: `ServerManagementDialogController.java`
    - In `task.setOnSucceeded`, after `filterServers(searchField.getText())`, add one line: `serverTable.refresh();`
    - This single line fixes both Bug 1 (dialog open) and Bug 3 (Refresh button) because both go through `loadServers()`
    - _Bug_Condition: isBugCondition_B1B3 — serverTable.refresh() not called after filterServers()_
    - _Expected_Behavior: statusColumn cells re-evaluated against SSHSessionManager.getActiveSessionKeys() at load-complete time_
    - _Preservation: filter/search still works (3.1, 3.2, 3.5, 3.6); no new methods or abstractions_
    - _Requirements: 2.1, 2.2, 2.5, 2.6_

  - [x] 3.2 Verify Bug 1 & 3 exploration test now passes
    - **Property 1: Expected Behavior** - Status ikon fresh setelah reload
    - **IMPORTANT**: Re-run the SAME test from task 1 (Bug 1 & 3 case) — do NOT write a new test
    - **EXPECTED OUTCOME**: Test PASSES (confirms `serverTable.refresh()` fix works)
    - _Requirements: 2.1, 2.2, 2.5, 2.6_

  - [x] 3.3 Verify preservation tests for Bug 1 & 3 still pass
    - **Property 2: Preservation** - Filter/search behaviour unchanged
    - **IMPORTANT**: Re-run the SAME preservation tests from task 2 (Bug 1 & 3 cases)
    - **EXPECTED OUTCOME**: Tests PASS (no regressions to filter, reload, or search)

- [x] 4. Fix Bug 2 — File manager auto-sort

  - [x] 4.1 Bind `sortedData` with fallback default comparator in `setupFileTable()`
    - File: `UnifiedFileManagerDialogController.java`
    - In `setupFileTable()`, replace the plain `sortedData.comparatorProperty().bind(...)` with:
      ```java
      // ponytail: fallback comparator — directories first, then modified desc, tie-break name asc
      Comparator<FileInfo> defaultSort = Comparator
          .comparing((FileInfo f) -> f.getName().equals("..") ? 0 : (f.isDirectory() ? 1 : 2))
          .thenComparing(f -> f.getModified() != null ? f.getModified() : java.time.LocalDateTime.MIN,
                         Comparator.reverseOrder())
          .thenComparing(f -> f.getName().toLowerCase());
      sortedData.comparatorProperty().bind(
          fileTable.comparatorProperty().map(c -> c != null ? c : defaultSort)
      );
      ```
    - In `loadFiles().setOnSucceeded`, after `allFiles.setAll(loadedFiles)`, reset to default sort:
      ```java
      fileTable.getSortOrder().clear(); // returns to defaultSort via the bound comparator
      ```
    - _Bug_Condition: isBugCondition_B2 — no default sort order applied after load_
    - _Expected_Behavior: directories first then files, each group modified descending, name ascending tie-break (2.3, 2.4)_
    - _Preservation: manual column-header sort still overrides default; search does not reset sort (3.3, 3.4)_
    - _Requirements: 2.3, 2.4_

  - [x] 4.2 Verify Bug 2 exploration test now passes
    - **Property 1: Expected Behavior** - Default sort applied after every load
    - **IMPORTANT**: Re-run the SAME test from task 1 (Bug 2 case)
    - **EXPECTED OUTCOME**: Test PASSES
    - _Requirements: 2.3, 2.4_

  - [x] 4.3 Verify preservation tests for Bug 2 still pass
    - **Property 2: Preservation** - Manual sort override and search filter unchanged
    - **IMPORTANT**: Re-run the SAME preservation tests from task 2 (Bug 2 cases)
    - **EXPECTED OUTCOME**: Tests PASS

- [x] 5. Fix Bug 4 — Versi "DEV" di fat JAR

  - [x] 5.1 Add `dependsOn processResources` to `fatJar` task in `build.gradle`
    - File: `build.gradle`
    - Inside the `fatJar` task registration, add: `dependsOn processResources`
    - `processResources` already `dependsOn generateVersionProperties`, so this is sufficient
    - _Bug_Condition: isBugCondition_B4 — fatJar runs without generateVersionProperties having run_
    - _Expected_Behavior: JAR contains version.properties with correct version (2.7, 2.8)_
    - _Preservation: ./gradlew build still works via existing processResources chain (3.8)_
    - _Requirements: 2.7, 2.8_

  - [x] 5.2 Remove duplicate static initializer from `AboutDialogController`
    - File: `AboutDialogController.java`
    - Delete the private static `VERSION` field and its static initializer block
    - Replace any reference to `VERSION` in `initialize()` with `Main.VERSION`
    - _Bug_Condition: isBugCondition_B4 (DRY violation — redundant version reader, 1.9)_
    - _Expected_Behavior: version shown in About dialog reads from Main.VERSION (2.9)_
    - _Preservation: fallback "DEV" still works when version.properties absent from classpath (3.7) — Main.VERSION already handles this_
    - _Requirements: 2.9_

  - [x] 5.3 Verify Bug 4 exploration test now passes
    - **Property 1: Expected Behavior** - Fat JAR contains correct version.properties
    - **IMPORTANT**: Re-run the SAME test from task 1 (Bug 4 case)
    - **EXPECTED OUTCOME**: Test PASSES
    - _Requirements: 2.7, 2.8, 2.9_

  - [x] 5.4 Verify preservation tests for Bug 4 still pass
    - **Property 2: Preservation** - DEV fallback and normal build unchanged
    - **IMPORTANT**: Re-run the SAME preservation tests from task 2 (Bug 4 cases)
    - **EXPECTED OUTCOME**: Tests PASS

- [x] 6. Fix Bug 5 — Download multi-thread hang/corrupt

  - [x] 6.1 Add connect timeout, latch timeout, and cleanup in `downloadFileConcurrent()`
    - File: `SSHServiceImpl.java`, method `downloadFileConcurrent()`
    - **Change A** — connect timeout: `channel.connect()` → `channel.connect(30_000)`
    - **Change B** — latch timeout: replace `latch.await()` with:
      ```java
      long timeoutSec = Math.max(60, Math.min(3600, (fileSize / 102_400) + 60));
      boolean completed = latch.await(timeoutSec, TimeUnit.SECONDS);
      if (!completed) {
          hasError.set(true);
          executor.shutdownNow();
          executor.awaitTermination(5, TimeUnit.SECONDS);
      }
      ```
    - **Change C** — cleanup on failure: after `executor.shutdown()`, add:
      ```java
      if (hasError.get()) {
          try {
              java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(localPath));
          } catch (java.nio.file.NoSuchFileException ignored) {
          } catch (IOException e2) {
              logger.warn("Failed to delete partial download file: {}", localPath, e2);
          }
      }
      ```
    - Verify `channel.disconnect()` and `latch.countDown()` are already in the `finally` block (they are — no change needed per design)
    - _Bug_Condition: isBugCondition_B5 — connect or latch.await without timeout, no cleanup on failure_
    - _Expected_Behavior: connect has 30s timeout; latch timeout in [60,3600]s; partial file deleted on failure (2.10, 2.11, 2.12, 2.13, 2.14)_
    - _Preservation: threadCount==1 / file<5MB path uses downloadFile() — not touched (3.9, 3.10, 3.11)_
    - _Requirements: 2.10, 2.11, 2.12, 2.13, 2.14_

  - [x] 6.2 Verify Bug 5 exploration test now passes
    - **Property 1: Expected Behavior** - Download returns within timeout; partial file deleted on failure
    - **IMPORTANT**: Re-run the SAME test from task 1 (Bug 5 case)
    - **EXPECTED OUTCOME**: Test PASSES (call returns in < 40s; no leftover partial file)
    - _Requirements: 2.10, 2.11, 2.12, 2.13, 2.14_

  - [x] 6.3 Verify preservation tests for Bug 5 still pass
    - **Property 2: Preservation** - Single-thread download behaviour unchanged
    - **IMPORTANT**: Re-run the SAME preservation tests from task 2 (Bug 5 cases)
    - **EXPECTED OUTCOME**: Tests PASS

- [x] 7. Fix Bug 6 — File manager does not restore last directory

  - [x] 7.1 Add `preferenceService`, `lastPathKey()`, save on navigate, restore on open
    - File: `UnifiedFileManagerDialogController.java`
    - Add field: `private PreferenceService preferenceService;`
    - In `initialize()`: `preferenceService = new PreferenceServiceImpl();`
    - Add helper (one method):
      ```java
      private String lastPathKey() {
          return currentLocation == null || currentLocation.server == null
              ? "file_manager_last_path_local"
              : "file_manager_last_path_" + currentLocation.server.getName();
      }
      ```
    - In `navigateTo()`, after `currentPath = normalizedNewPath; pathField.setText(currentPath);`, save preference (skip for `..` navigation edge-case is handled naturally by the path already being normalized):
      ```java
      if (preferenceService != null) {
          preferenceService.saveOrUpdatePreferences(new Preference(lastPathKey(), currentPath));
      }
      ```
    - In `handleLocationSelected()` local branch, replace `navigateTo(localFileService.getHomeDirectory())` with:
      ```java
      String lastPath = preferenceService.getPreferencesByCode(lastPathKey())
          .filter(p -> !p.isBlank())
          .orElse(localFileService.getHomeDirectory());
      navigateTo(lastPath);
      ```
    - In `connectToRemote().setOnSucceeded`, replace `navigateTo(server.getDefaultPath())` with:
      ```java
      String lastPath = preferenceService.getPreferencesByCode(lastPathKey())
          .filter(p -> !p.isBlank())
          .orElse(server.getDefaultPath() != null ? server.getDefaultPath() : "/");
      navigateTo(lastPath);
      ```
    - _Bug_Condition: isBugCondition_B6 — no last-path preference saved/read on open_
    - _Expected_Behavior: navigate saves path; open reads saved path with home/defaultPath fallback (2.13, 2.14, 2.15)_
    - _Preservation: Home button still navigates to home/default and does NOT save preference (3.12); missing pref = silent fallback (3.13, 3.14)_
    - _Requirements: 2.13, 2.14, 2.15_

  - [x] 7.2 Verify Bug 6 exploration test now passes
    - **Property 1: Expected Behavior** - Dialog opens at last visited directory
    - **IMPORTANT**: Re-run the SAME test from task 1 (Bug 6 case)
    - **EXPECTED OUTCOME**: Test PASSES (`currentPath` = last navigated path, not home)
    - _Requirements: 2.13, 2.14, 2.15_

  - [x] 7.3 Verify preservation tests for Bug 6 still pass
    - **Property 2: Preservation** - Home button and fallback behaviour unchanged
    - **IMPORTANT**: Re-run the SAME preservation tests from task 2 (Bug 6 cases)
    - **EXPECTED OUTCOME**: Tests PASS

- [x] 8. Checkpoint — Ensure all tests pass
  - Re-run the full test suite (exploration tests + preservation tests)
  - All 5 bug condition exploration tests SHALL now PASS (bugs fixed)
  - All preservation property tests SHALL still PASS (no regressions)
  - Run `./gradlew clean fatJar` and verify JAR version matches `gradle.properties`
  - Ensure all tests pass; ask the user if questions arise

## Notes

- Tasks 1 and 2 MUST be completed (tests written and run on unfixed code) before any fix task begins.
- Bugs 1 & 3 share a single fix (one `serverTable.refresh()` call) — tasks 3–3.3 cover both.
- Bug 6 fix lands in the same file as Bug 2 fix (`UnifiedFileManagerDialogController.java`) — apply both changes carefully to avoid conflicts.
- The `ponytail:` comment in task 4.1 marks the fallback-comparator pattern as intentional; the upgrade path is a dedicated sort column if requirements change.
- Home-button preservation (3.12) is guaranteed because the Home button calls `navigateTo(homeDir)` directly — `navigateTo()` will save that path. If the requirement is that Home must NOT update the preference, add a boolean `skipSave` guard and mark it with a `ponytail:` comment.

## Task Dependency Graph

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1", "2"] },
    { "wave": 2, "tasks": ["3.1", "4.1", "5.1", "5.2", "6.1", "7.1"] },
    { "wave": 3, "tasks": ["3.2", "3.3", "4.2", "4.3", "5.3", "5.4", "6.2", "6.3", "7.2", "7.3"] },
    { "wave": 4, "tasks": ["8"] }
  ]
}
```
