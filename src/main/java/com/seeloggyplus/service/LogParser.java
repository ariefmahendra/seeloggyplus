package com.seeloggyplus.service;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.ParsingConfig;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interface for log parsing operations.
 * <p>
 * Defines the contract for parsing log files, supporting both in-memory list
 * generation
 * and streaming to a search index. Follows Dependency Inversion Principle.
 */
public interface LogParser {

    /**
     * Callback interface for monitoring parsing progress.
     */
    interface ProgressCallback {
        /**
         * Report progress updates.
         *
         * @param progress       Percentage complete (0.0 to 1.0).
         * @param bytesProcessed Number of bytes processed so far.
         * @param totalBytes     Total bytes to process.
         */
        void onProgress(double progress, long bytesProcessed, long totalBytes);

        /**
         * Called when parsing is complete.
         *
         * @param totalEntries Total entries successfully parsed.
         */
        void onComplete(long totalEntries);
    }

    /**
     * DTO for communicating parsing configuration validation results.
     */
    @Getter
    @Setter
    class TestResult {
        private boolean success;
        private String message;
        private Map<String, String> parsedFields;
        private List<String> groupNames;

        public TestResult() {
            this.parsedFields = new HashMap<>();
            this.groupNames = new ArrayList<>();
        }
    }

    /**
     * Parses a file using parallel processing for performance.
     * <p>
     * Optimal for large files. Chunks the file and processes it using multiple
     * threads.
     *
     * @param file     The source file to parse.
     * @param config   The configuration defining regex patterns and formats.
     * @param callback Optional callback for progress updates.
     * @return List of parsed LogEntry objects.
     * @throws IOException If file access fails.
     */
    List<LogEntry> parseFileParallel(File file, ParsingConfig config, ProgressCallback callback) throws IOException;

    /**
     * Parses a single line string into a LogEntry.
     *
     * @param line       Raw text line.
     * @param lineNumber Line number (for tracking).
     * @param config     Parsing configuration.
     * @return Parsed LogEntry object.
     */
    LogEntry parseLine(String line, long lineNumber, ParsingConfig config);

    /**
     * Validates a parsing configuration against a sample log line.
     *
     * @param sampleLog ONE or more lines of sample log text.
     * @param config    The configuration to test.
     * @return TestResult containing status and extracted fields.
     */
    TestResult testParsing(String sampleLog, ParsingConfig config);

    /**
     * Performs a high-performance search across a list of entries.
     *
     * @param entries       The dataset to search.
     * @param searchText    The query string.
     * @param isRegex       True to interpret searchText as RegEx.
     * @param caseSensitive True to enforce case sensitivity.
     * @return Filtered list of matching entries.
     */
    List<LogEntry> search(List<LogEntry> entries, String searchText, boolean isRegex, boolean caseSensitive);
}
