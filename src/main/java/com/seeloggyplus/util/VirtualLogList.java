package com.seeloggyplus.util;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.service.LogEntrySource;
import java.util.AbstractList;

/**
 * A virtual list backed by LogEntrySource.
 * Allows ListView to bind to 1 million+ entries without loading them all into
 * heap memory as objects if not needed.
 */
public class VirtualLogList extends AbstractList<LogEntry> {

    private final LogEntrySource source;
    private final IntArrayList filteredIndexes;

    public VirtualLogList(LogEntrySource source, IntArrayList filteredIndexes) {
        this.source = source;
        this.filteredIndexes = filteredIndexes;
    }

    @Override
    public LogEntry get(int index) {
        return source.getByFilteredIndex(index, filteredIndexes);
    }

    @Override
    public int size() {
        return source.getFilteredSize(filteredIndexes);
    }
}
