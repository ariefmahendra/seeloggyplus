# Desktop feedback implementation

Development base: `dev` (the repository's development branch).

1. Recent Files: a tree grouped by source (one expandable node per server, `Local` for local files) with the files as children. Rows show filename, full path and size — the old Open/Tail label was removed. Search matches all words across source, filename and path. Uses the joined server name; no per-cell database reads. The grouping is display-only, so it survives without extra queries.
2. Windows: `SeeLoggyPlus.vbs` starts the packaged application hidden, using the existing launcher configuration and version pointer. `launcher.bat --console` provides diagnostics.
3. Preferences / General: File double-click selects Open/Download (default) or Tail. Applies to files in File Management and Enter; folders always navigate. Recent entries continue to restore their recorded mode.
4. Server Management: pure CRUD dialog (add/edit/clone/delete, search, favorites and grouping removed; no drag-to-reorder rows).
5. Favorite folders: explicit add/remove-current-folder button and empty-state guidance. Favorites remain scoped to the selected location. Removing a favorite refreshes its cache immediately.
6. Server groups live in File Management: the Locations panel is a WinSCP/MobaXterm-style tree — Local Drive first, then group folders, then ungrouped servers. New group creates a persistent (even empty) folder; Rename/Delete are on the folder context menu (delete ungroups its members, never deletes servers); Move to group is on the server context menu; dragging a server onto a folder moves it, onto Local/root ungroups it, onto a server joins that server's group. Groups and their order are stored in the additive `server_groups` table.
7. Graphite: chrome and selected-row icons use the appropriate contrast tokens; icon tests exercise all three themes. Dropdown popups (ComboBox/ChoiceBox) are themed surface+text tokens so they render light in Graphite/Light and dark in Dark. All buttons (including dialog/plain buttons) follow the theme surface/text/border tokens — no default JavaFX look — and the location-tree drop highlight draws a background bar without changing the cell size.
8. Back/Forward traverse folder visit history within the selected location. Up goes to its parent. Switching location clears history; Backspace invokes Back. Tooltips show the destination; Back/Forward also persist the current path.

Database upgrade adds favorite, sort_order, group_name and the `server_groups` table without replacing data. Migrations run transactionally only after a successful consistent SQLite backup. Failed backups stop the migration. New databases use the latest schema directly (schema version 3).
