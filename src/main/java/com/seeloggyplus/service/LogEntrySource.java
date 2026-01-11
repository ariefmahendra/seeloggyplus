package com.seeloggyplus.service;

import com.seeloggyplus.model.LogEntry;

import java.util.List;
import java.util.function.Predicate;

/**
 * Interface defining a source of log entries.
 * <p>
 * This interface abstracts the underlying data source (e.g., in-memory list,
 * lucene index, file stream) to allow unified access for the Log Viewer (table
 * view).
 * It supports pagination (offset/limit) and filtering.
 */
public interface LogEntrySource {

    /**
     * Returns the total number of log entries available in this source.
     * This is used by pagination controls to determine the total page count.
     *
     * @return The total count of accessible log entries.
     */
    int getTotalEntries();

    /**
     * Retrieves a page (sub-list) of log entries.
     * This method supports lazy loading for UI virtualization.
     *
     * @param offset The zero-based starting index.
     * @param limit  The maximum number of entries to return.
     * @return A list of LogEntry objects. Returns empty list if offset is out of
     *         bounds.
     */
    List<LogEntry> getEntries(int offset, int limit);

    /**
     * Creates a NEW LogEntrySource filtered by the given predicate.
     * The original source remains unchanged (immutability preferred).
     *
     * @param predicate The condition to test each log entry.
     * @return A new LogEntrySource containing only matching entries.
     */
    LogEntrySource filter(Predicate<LogEntry> predicate);
}
