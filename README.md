# SeeLoggyPlus — A Fast, Modern & Intelligent Log Viewer

SeeLoggyPlus is a high-performance, JavaFX-based desktop log viewer designed for developers and system administrators. It provides a fast, secure, and convenient way to read, search, tail, and analyze large log files from both **local** and **remote (SSH)** sources.

---

## ✨ Key Features

*   **Universal Log Access**: Open log files from your local machine or connect to a remote server via SSH to browse, download, and tail files directly.
*   **Unified File Manager**: One browser for local and remote files. The Locations panel groups servers into nested folders, supports favorites, and lets you drag servers to group, ungroup, or reorder them.
*   **Find in Files (SSH)**: Search a keyword across every file under a remote folder, preview the matches with context, then Open & Jump or Tail the file.
*   **Live Tail Mode**: Stream logs in real-time with Smart Follow. Scrolling up pauses auto-follow; scrolling back to the bottom resumes it automatically.
*   **Smart Search & Filtering**: Instantly filter logs with text, case-sensitive, and regex searches. A Search Result Panel lists every match with click-to-jump, and F3 / Shift+F3 navigate between matches.
*   **Embedded Prettifiers**: Automatically format and syntax-highlight JSON and XML content found within your log entries.
*   **Recent Log Files**: Entries are grouped per server (or Local), searchable, and the file you reopen moves back to the top of its group with its last Open/Tail mode preserved.
*   **High Performance**: Canvas-based rendering, background indexing, and multi-threaded downloads keep the UI responsive even with multi-gigabyte log files.
*   **Secure SSH**: Password or private-key authentication (credentials stored encrypted), with host-key verification through `known_hosts` — unknown keys must be trusted by the user, changed keys are always refused.
*   **Themes & Preferences**: Graphite (default), Light, and Dark themes; configurable font, tail window size, memory limit, download options, and more.
*   **Self-Update**: The app checks for updates, can download them automatically in the background, and shows a "What's New" dialog after upgrading.

---

## 🚀 Getting Started

### Prerequisites

*   **Java 21** or newer to build and run from source.
*   The portable package **without** a bundled JRE also needs Java 21+ on the target machine. The package **with** a bundled JRE does not.

### Running from Source

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/ariefmahendra/seeloggyplus.git
    cd seeloggyplus
    ```

2.  **Run the application in development mode** (includes CSS hot reload):
    ```bash
    # On Windows
    .\gradlew.bat --no-daemon runDev

    # On Linux/macOS
    ./gradlew --no-daemon runDev
    ```

### Running the Tests

The full suite runs headless (Monocle) and is safe to run locally:

```bash
# On Windows
.\gradlew.bat --no-daemon test

# On Linux/macOS
./gradlew --no-daemon test
```

### Building the Portable Distribution

The portable zip contains the application jar, the launcher scripts (`launcher.bat`, `launcher.sh`), `launcher.properties`, and the offline help files.

```bash
# On Windows
.\gradlew.bat --no-daemon packagePortableNoJre      # smaller; requires Java 21+ on the target machine
.\gradlew.bat --no-daemon packagePortableWithJre    # bundles a custom JRE; build on the target OS
.\gradlew.bat --no-daemon packagePlatform           # builds both
```

The archives are written to `build/distributions/` (for example `seeloggyplus-Portable-win.zip` and `seeloggyplus-Portable-win-jre.zip`).

### Running the Packaged Application

*   **Windows**
    1.  Run `launcher.bat` once. On first start the application creates `SeeLoggyPlus.vbs` next to it (the VBS is intentionally not shipped in the package).
    2.  After that, double-click `SeeLoggyPlus.vbs` to start the app without a console window.
    3.  For diagnostics, run `launcher.bat --console` to keep a console attached.
*   **Linux/macOS**: run `./launcher.sh`.

Java is selected in this order: `java.home` in `launcher.properties` → the bundled `jre/` folder → `JAVA_HOME` → `java` on `PATH`. The maximum heap size is configured with `max.memory.gb` in `launcher.properties` (default: 4 GB) or through **Settings → Preferences → General → Performance**.

---

## 🛠️ Usage

### Opening Log Files

*   **Local and remote files**: go to **File → Open...** (`Ctrl+O`). This opens the **Unified File Manager**, which has two parts:
    *   **Locations** (left): `Local Drive` plus every configured SSH server, grouped into folders.
    *   **File table** (right): double-click a folder to enter it; double-click a file to Open/Download or Tail it (configurable in **Preferences → General → File double-click**). Enter does the same as double-click; folders always open.
*   **Recent files**: click any entry in the **Recent Log Files** panel to reopen it in the same mode (Open or Tail) it was last used.
*   **Remote servers**: add them first in **Settings → Server Management...** (`Ctrl+M`). Use **Add Server**, then choose password or private-key authentication, and test the connection with **Test Connection**. Unknown host keys must be trusted via the fingerprint dialog on first connect.

### Organizing Servers (Locations)

*   **New group**: right-click anywhere in the Locations panel and choose **New group...**. Groups can contain other groups (for example `Production/Database`).
*   **Move a server into a group**: drag it onto the group folder. Dropping it onto another server joins that server's group.
*   **Reorder servers**: drag a server between two rows; a line above or below the row shows where it will land. The order is saved.
*   **Remove from a group**: drop the server onto `Local Drive` or the empty area of the panel.
*   **Rename/Delete a group**: right-click the group folder. Deleting a group never deletes servers — they simply become ungrouped.
*   Dragging a server never connects to it; a server is only opened with a click or Enter.

### Live Tailing

1.  Open a file and click the **Tail** button (terminal icon) in the toolbar.
2.  New lines stream in as they are written. **Smart Follow** keeps the view pinned to the newest lines.
3.  Scrolling up pauses Smart Follow; scroll back to the bottom (or toggle **Smart Follow**) to resume.
4.  While a tab is tailing, a green dot marks the tab and unread lines are counted in its title.
5.  The number of lines loaded on tail start is configured with **Tail Window Size** in **Preferences → Log Viewer**.

### Search & Filtering

*   Type into the **Include (Search)** field in the toolbar. Results update as you type (300 ms debounce) and pressing `Enter` applies immediately.
*   Toggle **Regex** and **Case Sensitive** to change matching.
*   When a search is active, the **Search Result Panel** appears on the right; click a result to jump to that line.
*   Use `F3` / `Shift+F3` to move between matches, and `Escape` in the search field to clear the filter.

### Detail Panel

Click a log line to see its full content in the bottom panel. The panel toolbar offers:

*   **Auto-Prettify JSON / XML** toggles (also available in **Preferences → Log Viewer**),
*   **Copy** to copy the shown content,
*   **Clear** to empty the panel,
*   **Close** to hide the panel (`Ctrl+J` to show it again).

### Keyboard Shortcuts

| Shortcut | Action |
| --- | --- |
| `Ctrl+O` | Open the Unified File Manager |
| `Ctrl+W` | Close the current tab |
| `Ctrl+Tab` / `Ctrl+Shift+Tab` | Next / previous tab |
| `Ctrl+G` | Go To Line... |
| `Ctrl+F` | Focus the search field |
| `Ctrl+R` | Reload the current file or tail session |
| `F3` / `Shift+F3` | Next / previous search match |
| `Ctrl+B` | Show/hide the Recent Log Files panel |
| `Ctrl+J` | Show/hide the Detail panel |
| `Ctrl+M` | Server Management |
| `F1` | Open Help Contents |
| `Alt+F4` | Exit |

### Preferences

**Settings → Preferences...** has three tabs:

*   **General**: font size and family, theme (Graphite/Light/Dark), file double-click action, and maximum memory (restart required).
*   **Log Viewer**: tail window size, preview line limit, and auto-prettify JSON/XML.
*   **SSH**: download threads, connection timeout, and the download directory (with **Open Folder** and **Clean Downloads** helpers).

### Updates

Use **Help → Check for Updates...** for a manual check, or leave the automatic options enabled:

*   **Check for Updates Automatically** — checks in the background and shows a status-bar indicator when a new version exists.
*   **Download Updates Automatically** — downloads and stages the update in the background; the indicator then offers **Restart to update**.
*   After upgrading, a **What's New** dialog shows the release notes of the new version.

---

## 🤝 Contributing

Contributions are welcome and greatly appreciated!

1.  Fork the repository on GitHub.
2.  Create a new branch for your feature or bug fix.
3.  Commit your changes.
4.  Submit a Pull Request.

Before submitting, run the test suite (`.\gradlew.bat --no-daemon test`) and keep it green.

For bugs and feature requests, please open an issue on the **GitHub Issues** page for this repository, or contact **mahend.arief@gmail.com**.

---

## 📜 License

© 2025–2026 SeeLoggyPlus

This software is free to use for personal and professional purposes.
