package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.service.LogEntrySource;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link LogEntrySource}.
 * <p>
 * Handles a static list of log entries, typically used for small datasets
 * or filtering results. Supports efficient sub-list retrieval and
 * stream-based filtering.
 */
@RequiredArgsConstructor
public class ListLogEntrySourceImpl implements LogEntrySource {

    private final List<LogEntry> allEntries;

    /**
     * Returns the total count of filtered/loaded entries.
     *
     * @return Total entry count
     */
    @Override
    public int getTotalEntries() {
        return allEntries.size();
    }

    /**
     * Retrieves a slice of entries from the in-memory list.
     * Safe against out-of-bounds indices.
     *
     * @param offset Start index
     * @param limit  Max items to return
     * @return List of entries
     */
    @Override
    public List<LogEntry> getEntries(int offset, int limit) {
        if (offset >= allEntries.size()) {
            return Collections.emptyList();
        }

        int fromIndex = Math.max(0, offset);
        int toIndex = Math.min(offset + limit, allEntries.size());

        if (fromIndex > toIndex) {
            return Collections.emptyList();
        }

        return allEntries.subList(fromIndex, toIndex);
    }

    /**
     * Filters the entries in memory and returns a new source.
     *
     * @param predicate Filtering condition
     * @return New ListLogEntrySourceImpl with filtered results
     */
    @Override
    public LogEntrySource filter(Predicate<LogEntry> predicate) {
        List<LogEntry> filteredList = allEntries.stream()
                .filter(predicate)
                .collect(Collectors.toList());
        return new ListLogEntrySourceImpl(filteredList);
    }
}
