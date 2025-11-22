package com.seeloggyplus.service;

import com.seeloggyplus.model.LogEntry;

import java.util.List;

/**
 * Interface for a source of LogEntry objects, supporting lazy loading.
 */
public interface LogEntrySource {
    /**
     * Returns the total number of log entries available.
     * @return The total count of log entries.
     */
    int getTotalEntries();

    /**
     * Retrieves a sub-list of log entries from the source.
     * @param offset The starting index (inclusive) of the entries to retrieve.
     * @param limit The maximum number of entries to retrieve.
     * @return A list of LogEntry objects.
     */
    List<LogEntry> getEntries(int offset, int limit);

    /**
     * Filters the log entries based on a predicate and returns a new LogEntrySource.
     * @param predicate The predicate to apply for filtering.
     * @return A new LogEntrySource containing only the filtered entries.
     */
    LogEntrySource filter(java.util.function.Predicate<LogEntry> predicate);
}
