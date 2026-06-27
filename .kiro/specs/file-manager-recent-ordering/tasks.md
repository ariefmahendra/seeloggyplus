# Implementation Plan: File Manager Recent Ordering

## Overview

Add ~30 lines to `UnifiedFileManagerDialogController` to persist and restore sort column/direction per location using the existing `PreferenceService`. No new classes, no new dependencies.

## Tasks

- [x] 1. Implement sort persistence in UnifiedFileManagerDialogController
  - [x] 1.1 Add `sortKey()`, `saveSortOrdering()`, and `restoreSortOrdering()` private methods
    - `sortKey()` returns `"file_manager_sort_local"` or `"file_manager_sort_{serverName}"` mirroring `lastPathKey()`
    - `saveSortOrdering()` encodes `fileTable.getSortOrder()` as `"columnFxId:ASCENDING"` or `"columnFxId:DESCENDING"` and calls `PreferenceService.saveOrUpdatePreferences()`
    - `restoreSortOrdering()` reads preference via `getPreferencesByCode()`, splits on `:`, finds column by fx:id, applies sort order; falls back to clearing sort order on any failure (blank, malformed, unknown column, exception)
    - Wrap save/restore in try-catch with `logger.warn(...)` — no user-facing error dialogs
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 4.1, 4.2_

  - [x] 1.2 Register sort-order listener and hook into `loadFiles()`
    - In `setupFileTable()`, add `fileTable.getSortOrder().addListener((ListChangeListener) -> saveSortOrdering())`
    - In `loadFiles()`, replace `fileTable.getSortOrder().clear()` with `restoreSortOrdering()`
    - _Requirements: 1.1, 2.1, 3.1, 3.2_

  - [x] 1.3 Write property tests (jqwik) for sort encoding/decoding logic
    - **Property 1: Sort ordering round-trip** — for any valid column id × {ASCENDING, DESCENDING}, `decode(encode(col, dir)) == (col, dir)`
    - **Property 2: Location key uniqueness** — for any two distinct non-blank location strings, `sortKey(a) != sortKey(b)`
    - **Property 3: Malformed preference fallback** — for any random/blank/no-colon/unknown-column string, decode returns fallback without exception
    - **Validates: Requirements 1.2, 2.2, 3.1, 4.1, 4.2**

  - [x] 1.4 Write unit tests for save/restore integration
    - Mock `PreferenceService`, simulate column click, verify `saveOrUpdatePreferences` called with correct key and encoded value
    - Set a preference, trigger `loadFiles`, verify `fileTable.getSortOrder()` matches
    - Verify fallback to default sort when no preference exists
    - Verify location isolation (local vs. remote with different saved sorts)
    - Verify no exception propagates when service throws on save
    - _Requirements: 1.1, 1.3, 2.1, 2.2, 2.3, 3.1, 3.2, 4.1, 4.2_

- [x] 2. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- The entire feature is ~30 lines in one file; no new classes, services, or FXML changes
- Property tests use jqwik (already on classpath)
- Encoding uses `columnFxId:DIRECTION` with `:` delimiter — column IDs are safe camelCase identifiers
- All error paths log and fall back silently — no user-facing failures

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["1.3", "1.4"] }
  ]
}
```
