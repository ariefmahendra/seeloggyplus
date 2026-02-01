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
     * Retrieves a single entry by its global zero-based index.
     *
     * @param index The index of the entry.
     * @return The LogEntry at the specified index.
     */
    LogEntry getEntry(int index);

    /**
     * Helper to get the effective size based on whether filtering is active.
     */
    default int getFilteredSize(com.seeloggyplus.util.IntArrayList filteredIndexes) {
        return filteredIndexes == null ? getTotalEntries() : filteredIndexes.size();
    }

    /**
     * Helper to get a LogEntry by its filtered index.
     * Maps the filtered index to the real underlying index.
     */
    default LogEntry getByFilteredIndex(int filteredIndex, com.seeloggyplus.util.IntArrayList filteredIndexes) {
        int realIndex = (filteredIndexes == null) ? filteredIndex : filteredIndexes.get(filteredIndex);
        return getEntry(realIndex);
    }

    LogEntrySource filter(Predicate<LogEntry> predicate);
}
