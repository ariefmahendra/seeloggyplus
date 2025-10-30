package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.service.LogEntrySource;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * An implementation of LogEntrySource that wraps a List<LogEntry>.
 * This is suitable when all log entries are already in memory.
 */
public class ListLogEntrySourceImpl implements LogEntrySource {
    private final List<LogEntry> allEntries;

    public ListLogEntrySourceImpl(List<LogEntry> allEntries) {
        this.allEntries = allEntries;
    }

    @Override
    public int getTotalEntries() {
        return allEntries.size();
    }

    @Override
    public List<LogEntry> getEntries(int offset, int limit) {
        int fromIndex = Math.min(offset, allEntries.size());
        int toIndex = Math.min(offset + limit, allEntries.size());
        if (fromIndex > toIndex) {
            return List.of(); // Return empty list if range is invalid
        }
        return allEntries.subList(fromIndex, toIndex);
    }

    @Override
    public LogEntrySource filter(Predicate<LogEntry> predicate) {
        List<LogEntry> filteredList = allEntries.stream()
                .filter(predicate)
                .collect(Collectors.toList());
        return new ListLogEntrySourceImpl(filteredList);
    }
}
