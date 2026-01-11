package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.service.LogEntrySource;
import com.seeloggyplus.service.LogParser;
import com.seeloggyplus.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Implementation of {@link LogEntrySource} that retrieves entries from a Lucene
 * index.
 * <p>
 * Uses {@link SearchService} to query the index and {@link LogParser} to
 * re-parse raw logs into structured {@link LogEntry} objects on demand.
 * optimizing memory usage by not storing parsed objects in the index (except
 * for searchable fields).
 * <p>
 * This class is stateful (maintains cursor for pagination optimization) and is
 * intended
 * to be used by a single UI component (e.g., TableView) on the JavaFX
 * Application Thread.
 */
public class LuceneLogEntrySource implements LogEntrySource {

    private static final Logger logger = LoggerFactory.getLogger(LuceneLogEntrySource.class);

    private final SearchService searchService;
    private final String queryStr;
    private final long fromTimestamp;
    private final long toTimestamp;
    private final LogParser logParserService;
    private final ParsingConfig parsingConfig;

    // Optimization: Cursor for "searchAfter" pagination
    private int lastEndOffset = -1;
    private Object lastEndToken = null;

    /**
     * Constructs a new LuceneLogEntrySource.
     *
     * @param searchService    The search service backend.
     * @param queryStr         The Lucene query string (optional, defaults to
     *                         empty).
     * @param fromTimestamp    Start timestamp (inclusive, epoch millis).
     * @param toTimestamp      End timestamp (inclusive, epoch millis).
     * @param logParserService Parser service for extracting fields from raw logs.
     * @param parsingConfig    Configuration for parsing raw logs.
     */
    public LuceneLogEntrySource(SearchService searchService, String queryStr, long fromTimestamp, long toTimestamp,
            LogParser logParserService, ParsingConfig parsingConfig) {
        this.searchService = searchService;
        this.queryStr = queryStr != null ? queryStr : "";
        this.fromTimestamp = fromTimestamp;
        this.toTimestamp = toTimestamp;
        this.logParserService = logParserService;
        this.parsingConfig = parsingConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getTotalEntries() {
        try {
            return (int) searchService.getTotalHits(queryStr, fromTimestamp, toTimestamp);
        } catch (Exception e) {
            logger.error("Error getting total hits for query: {}", queryStr, e);
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LogEntry> getEntries(int offset, int limit) {
        List<LogEntry> rawEntries;

        try {
            // Optimization: use cursor if we are continuing strictly sequentially from the
            // last known position
            if (offset == lastEndOffset && lastEndToken != null) {
                rawEntries = searchService.searchPageAfter(queryStr, fromTimestamp, toTimestamp, lastEndToken, limit);
            } else {
                rawEntries = searchService.searchPage(queryStr, fromTimestamp, toTimestamp, offset, limit);
            }

            // Update cursor state for next call
            lastEndOffset = offset + rawEntries.size();
            lastEndToken = searchService.getLastSearchAfterToken(); // Assuming SearchService exposes this or stateful

            if (rawEntries.isEmpty()) {
                return rawEntries;
            }

            // If parser is missing, return raw entries
            if (logParserService == null || parsingConfig == null) {
                return rawEntries;
            }

            // Re-parse entries to populate columns (lazy parsing)
            List<LogEntry> parsedEntries = new ArrayList<>(rawEntries.size());
            for (LogEntry raw : rawEntries) {
                if (raw.getRawLog() != null) {
                    try {
                        // parseLine returns a new LogEntry with parsed fields
                        LogEntry parsed = logParserService.parseLine(raw.getRawLog(), raw.getLineNumber(),
                                parsingConfig);
                        parsedEntries.add(parsed);
                    } catch (Exception e) {
                        logger.warn("Failed to re-parse log entry at line {}", raw.getLineNumber(), e);
                        parsedEntries.add(raw); // Fallback to raw
                    }
                } else {
                    parsedEntries.add(raw);
                }
            }
            return parsedEntries;

        } catch (Exception e) {
            logger.error("Error retrieving entries offset={} limit={}", offset, limit, e);
            return new ArrayList<>();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LogEntrySource filter(Predicate<LogEntry> predicate) {
        // Lucene source relies on Lucene Query Language, not Java Predicates.
        throw new UnsupportedOperationException(
                "LuceneLogEntrySource does not support predicate-based filtering. Create a new instance with an updated Lucene query string.");
    }
}
