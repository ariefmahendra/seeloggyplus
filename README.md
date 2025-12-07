# SeeLoggyPlus — A Fast, Modern & Intelligent Log Viewer

SeeLoggyPlus is a high-performance, JavaFX-based desktop log viewer designed for developers and system administrators. It provides a fast, secure, and convenient way to read, search, tail, and analyze large log files from both **local** and **remote (SSH)** sources.

---

## ✨ Key Features

*   **Universal Log Access**: Open log files from your local machine or connect to a remote server via SSH to browse and tail files directly.
*   **Live Tail Mode**: Stream logs in real-time with an intelligent tailing feature that pauses on manual scroll and resumes when you're ready.
*   **Dynamic Regex Parsing**: Use named capture groups in your regular expressions (`(?<name>...)`) to automatically transform raw log lines into structured, filterable table columns.
*   **Smart Search & Filtering**: Instantly filter logs with text, case-sensitive, and regex searches without reloading the entire file. Filter by log level and timestamp ranges.
*   **Embedded Prettifiers**: Automatically format and syntax-highlight JSON and XML content found within your log entries for improved readability.
*   **High Performance**: Optimized with multi-threaded parsing and a virtualized UI to handle log files ranging from a few megabytes to several gigabytes with ease.
*   **Modern & Flexible UI**: A clean, intuitive interface with panels that can be resized or collapsed to focus on what matters.

---

## 🚀 Getting Started

### Prerequisites

*   **Java 17** or newer.

### Running from Source

1.  **Clone the repository:**
    ```bash
    git clone https://gitlab.com/ariefmahendra/seeloggyplus.git
    cd seeloggyplus
    ```

2.  **Run the application:**
    The Gradle wrapper included in the repository will handle all dependencies.
    ```bash
    # On Linux/macOS
    ./gradlew run

    # On Windows
    .\gradlew.bat run
    ```

### Building the Portable Distribution

To build the standalone, portable distribution that includes the application and its runtime:

1.  **Run the `createPortableDist` task:**
    ```bash
    # On Linux/macOS
    ./gradlew createPortableDist

    # On Windows
    .\gradlew.bat createPortableDist
    ```

2.  **Find the artifact:**
    The portable `.zip` file will be available in the `build/distributions/` directory. You can extract and run it on any supported OS with Java 17+.

---

## 🛠️ Usage

### Opening Log Files

*   **Local Files**: Navigate to `File > Open...` (`Ctrl+O`) and select a log file. You will be prompted to choose a parsing configuration.
*   **Remote Files (SSH)**: Navigate to `Settings > Server Management...` to add an SSH server configuration. Then, go to `File > Open...` and select the "Remote" tab to browse and open files on the configured server.

### Live Tailing

Click the **Tail** button (the eye icon) in the toolbar to enter live tail mode. New log entries will be streamed in real-time. The tailing will automatically pause if you scroll up and can be resumed by clicking the "Resume Tail" button that appears.

### Log Parsing with Regex

The power of SeeLoggyPlus lies in its ability to parse any log format using regex with named capture groups.

1.  Go to **Settings > Parsing Configuration...**.
2.  Create a new configuration and define a regex pattern. Use `(?<column_name>...)` to define a capture group. Each named group will become a column in the log view table.

**Example:** For a log line like `2025-12-07 20:05:15 INFO com.seeloggyplus.Main - Application started.`, you could use:
```regex
(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) (?<level>\\w+) (?<logger>[\\w.]+) - (?<message>.*)
```
This will create `timestamp`, `level`, `logger`, and `message` columns.

---

## 🤝 Contributing

Contributions are welcome and greatly appreciated!

1.  Fork the repository on Github.
2.  Create a new branch for your feature or bug fix.
3.  Commit your changes.
4.  Submit a Merge Request.

For bugs and feature requests, please open an issue on the **GitLab Issues** page for this repository.

---

## 📜 License

© 2025 — SeeLoggyPlus

This software is free to use for personal and professional purposes. Please refer to the license file for more details.