# Requirements Document

## Introduction

The File Manager dialog currently resets column sort ordering to a default (directories first, then by modified date descending) every time files are loaded. This feature adds persistence of the user's last-used sort column and direction per location (local drive or each remote server), so the preferred ordering is restored when reopening the dialog for that location.

## Glossary

- **File_Manager**: The `UnifiedFileManagerDialogController` dialog that provides local and remote file browsing
- **Sort_Ordering**: A combination of the active sort column identifier and sort direction (ascending or descending)
- **Location**: Either the local drive or a specific remote SSH server, identified by the same key pattern used for last-path persistence
- **Preference_Service**: The existing `PreferenceService` used to persist key-value settings via `saveOrUpdatePreferences()` and `getPreferencesByCode()`
- **Default_Sort**: The fallback comparator (directories first, then modified descending, then name ascending) used when no column sort is active

## Requirements

### Requirement 1: Persist Sort Ordering on Column Click

**User Story:** As a user, I want the File Manager to remember which column I last sorted by (and in which direction), so that I do not have to re-sort every time I open the dialog.

#### Acceptance Criteria

1. WHEN the user clicks a column header to change the sort ordering, THE File_Manager SHALL save the active column identifier and sort direction to the Preference_Service using a location-specific key
2. THE File_Manager SHALL store Sort_Ordering as a single preference value encoding both the column identifier and the sort direction
3. IF the Preference_Service fails to save the Sort_Ordering, THEN THE File_Manager SHALL log the error and continue operation without interrupting the user

### Requirement 2: Restore Sort Ordering on Location Load

**User Story:** As a user, I want the File Manager to restore my last-used sort ordering when I open or switch to a location, so that my preferred view is maintained automatically.

#### Acceptance Criteria

1. WHEN a location is selected and files are loaded, THE File_Manager SHALL retrieve the saved Sort_Ordering for that location from the Preference_Service
2. WHEN a saved Sort_Ordering exists for the current location, THE File_Manager SHALL apply the saved column and direction to the file table sort order instead of clearing to the Default_Sort
3. WHEN no saved Sort_Ordering exists for the current location, THE File_Manager SHALL use the Default_Sort comparator

### Requirement 3: Location-Specific Isolation

**User Story:** As a user, I want each location (local and each remote server) to have its own remembered sort ordering, so that sorting on one server does not affect another.

#### Acceptance Criteria

1. THE File_Manager SHALL use a unique preference key per location for Sort_Ordering, following the same naming convention as the existing last-path preference keys
2. WHEN switching between locations, THE File_Manager SHALL restore the Sort_Ordering specific to the newly selected location independently of other locations

### Requirement 4: Handle Column Removal and Edge Cases

**User Story:** As a user, I want the File Manager to gracefully handle situations where a saved sort column is no longer valid, so that the dialog never shows a broken or empty state.

#### Acceptance Criteria

1. IF the saved Sort_Ordering references a column identifier that does not exist in the current table configuration, THEN THE File_Manager SHALL fall back to the Default_Sort
2. IF the saved Sort_Ordering value is blank or malformed, THEN THE File_Manager SHALL fall back to the Default_Sort
