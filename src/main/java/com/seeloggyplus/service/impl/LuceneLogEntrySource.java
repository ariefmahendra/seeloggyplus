package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.service.LogEntrySource;
import com.seeloggyplus.service.SearchService;

import java.util.List;
import java.util.function.Predicate;

/**
 * Adapter for sourcing log entries from Lucene SearchService.
 * Supports lazy loading via searchPage().
 */
public class LuceneLogEntrySource implements LogEntrySource {

    private final SearchService searchService;
    private final String queryStr;
    private final long fromTimestamp;
    private final long toTimestamp;

    private final com.seeloggyplus.service.impl.LogParserService logParserService;
    private final com.seeloggyplus.model.ParsingConfig parsingConfig;

    public LuceneLogEntrySource(SearchService searchService, String queryStr, long fromTimestamp, long toTimestamp,
            com.seeloggyplus.service.impl.LogParserService logParserService,
            com.seeloggyplus.model.ParsingConfig parsingConfig) {
        this.searchService = searchService;
        this.queryStr = queryStr != null ? queryStr : "";
        this.fromTimestamp = fromTimestamp;
        this.toTimestamp = toTimestamp;
        this.logParserService = logParserService;
        this.parsingConfig = parsingConfig;
    }

    private int lastEndOffset = -1;
    private Object lastEndToken = null;

    @Override
    public int getTotalEntries() {
        return (int) searchService.getTotalHits(queryStr, fromTimestamp, toTimestamp);
    }

    @Override
    public List<LogEntry> getEntries(int offset, int limit) {
        List<LogEntry> rawEntries;

        // Optimisation: use cursor if we are continuing from last known position
        if (offset == lastEndOffset && lastEndToken != null) {
            rawEntries = searchService.searchPageAfter(queryStr, fromTimestamp, toTimestamp, lastEndToken, limit);
        } else {
            rawEntries = searchService.searchPage(queryStr, fromTimestamp, toTimestamp, offset, limit);
        }

        // Update cursor state for next call
        lastEndOffset = offset + rawEntries.size();
        lastEndToken = searchService.getLastSearchAfterToken();

        if (rawEntries.isEmpty() || logParserService == null || parsingConfig == null) {
            return rawEntries;
        }

        // Re-parse entries to populate columns
        // We assume raw entries have correct lineNumber and rawLog
        List<LogEntry> parsedEntries = new java.util.ArrayList<>(rawEntries.size());
        for (LogEntry raw : rawEntries) {
            if (raw.getRawLog() != null) {
                // parseLine returns a new LogEntry with parsed fields
                parsedEntries.add(logParserService.parseLine(raw.getRawLog(), raw.getLineNumber(), parsingConfig));
            } else {
                parsedEntries.add(raw);
            }
        }
        return parsedEntries;
    }

    @Override
    public LogEntrySource filter(Predicate<LogEntry> predicate) {
        // Lucene source cannot use Java Predicate directly.
        // Logic in consumers (MainController) must handle creating a new
        // LuceneLogEntrySource with updated query.
        throw new UnsupportedOperationException(
                "LuceneLogEntrySource does not support predicate-based filtering. create a new instance with updated query instead.");
    }
}
