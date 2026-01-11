package com.seeloggyplus.service.impl;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.service.SearchService;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of SearchService using Apache Lucene 9.8.0.
 * Executes full-text search and range queries against the index.
 */
public class LuceneSearchService implements SearchService {

    private static final Logger logger = LoggerFactory.getLogger(LuceneSearchService.class);
    private static final String INDEX_BASE_DIR = System.getProperty("java.io.tmpdir") + "/seeloggyplus/index/";

    private IndexReader reader;
    private IndexSearcher searcher;
    private QueryParser queryParser;
    private ScoreDoc lastSeenScoreDoc;
    private String currentRunId;
    private java.util.concurrent.ExecutorService executor;

    public void openIndex(String runId) throws IOException {
        Path indexPath = Paths.get(INDEX_BASE_DIR, runId);
        if (!indexPath.toFile().exists()) {
            throw new IOException("Index directory does not exist: " + indexPath);
        }

        if (reader != null) {
            reader.close();
        }

        // Close previous executor if exists
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        Directory dir = FSDirectory.open(indexPath);
        this.reader = DirectoryReader.open(dir);

        // Use WorkStealingPool for efficient parallel search across segments
        // This utilizes all available CPU cores
        this.executor = java.util.concurrent.Executors.newWorkStealingPool();
        this.searcher = new IndexSearcher(reader, executor);

        // "message" is the default field for full-text search
        this.queryParser = new QueryParser("message", new StandardAnalyzer());
        this.currentRunId = runId;

        logger.info("Opened Lucene Index for runId: {} with parallel search enabled", runId);
    }

    @Override
    public List<LogEntry> search(String queryStr, long fromTimestamp, long toTimestamp, int limit) {
        if (searcher == null)
            return Collections.emptyList();

        try {
            Query finalQuery = buildQuery(queryStr, fromTimestamp, toTimestamp);

            // Sort by line_number ascending to preserve log order
            Sort sortByLineNumber = new Sort(new SortField("line_number_sort", SortField.Type.LONG));
            TopDocs results = searcher.search(finalQuery, limit, sortByLineNumber);

            return mapHitsToEntries(results.scoreDocs);
        } catch (Exception e) {
            logger.error("Search failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<LogEntry> searchPage(String queryStr, long fromTimestamp, long toTimestamp, int offset, int limit) {
        if (searcher == null)
            return Collections.emptyList();

        try {
            Query finalQuery = buildQuery(queryStr, fromTimestamp, toTimestamp);

            TopDocs results = null;
            Sort sortByLineNumber = null;

            // Optimisation: Bidirectional Search
            // If offset is past the halfway mark, search backwards from the end (Reverse
            // Sort)
            // This turns "Last Page" access from O(N) to O(1)
            long totalHits = searcher.count(finalQuery);
            boolean reverseSearch = false;

            if (offset > totalHits / 2) {
                reverseSearch = true;
                // Reverse Sort
                sortByLineNumber = new Sort(new SortField("line_number_sort", SortField.Type.LONG, true));

                // Calculate reverse offset
                // Example: Total 100, Limit 10.
                // Request Offset 90 (Page 10).
                // Forward: Skip 90, take 10.
                // Reverse: Skip 0, take 10.
                // ReverseOffset = Total - (Offset + Limit)
                // If Total=100, Offset=95, Limit=10 -> Range [95-104].
                // Available [0-99]. Overlap [95-99] (5 items).
                // ReverseOffset = 100 - (95 + 10) = -5?
                // Handle partial pages at end logic:

                long effectiveLimit = limit;
                long endPos = offset + limit;
                if (endPos > totalHits) {
                    endPos = totalHits;
                    effectiveLimit = endPos - offset;
                }

                long reverseOffset = totalHits - endPos;

                if (reverseOffset < 0)
                    reverseOffset = 0; // Should not happen with math above

                // Use reverse search
                // Note: searchAfter logic for reverse search needs careful handling or disable
                // it
                // For now, simple skip logic for reverse search (since reverse offset is small)

                if (reverseOffset > 0) {
                    TopDocs prevDocs = searcher.search(finalQuery, (int) reverseOffset, sortByLineNumber);
                    if (prevDocs.scoreDocs.length > 0) {
                        ScoreDoc after = prevDocs.scoreDocs[prevDocs.scoreDocs.length - 1];
                        results = searcher.searchAfter(after, finalQuery, (int) effectiveLimit, sortByLineNumber);
                    } else {
                        results = searcher.search(finalQuery, (int) effectiveLimit, sortByLineNumber);
                    }
                } else {
                    results = searcher.search(finalQuery, (int) effectiveLimit, sortByLineNumber);
                }

            } else {
                // Normal Forward Search
                sortByLineNumber = new Sort(new SortField("line_number_sort", SortField.Type.LONG));

                TopDocs resultsForward;
                // For large offsets, use searchAfter for efficient deep pagination
                ScoreDoc afterDoc = null;

                if (offset > 0) {
                    TopDocs prevDocs = searcher.search(finalQuery, offset, sortByLineNumber);
                    if (prevDocs.scoreDocs.length > 0) {
                        afterDoc = prevDocs.scoreDocs[prevDocs.scoreDocs.length - 1];
                    }
                }

                if (afterDoc != null) {
                    resultsForward = searcher.searchAfter(afterDoc, finalQuery, limit, sortByLineNumber);
                } else {
                    resultsForward = searcher.search(finalQuery, limit, sortByLineNumber);
                }
                results = resultsForward;
            }

            // Capture the last doc for next page optimization
            if (results.scoreDocs.length > 0) {
                this.lastSeenScoreDoc = results.scoreDocs[results.scoreDocs.length - 1];
            } else {
                this.lastSeenScoreDoc = null;
            }

            List<LogEntry> entries = mapHitsToEntries(results.scoreDocs);

            // If reverse search was used, reverse the list to restore original order
            if (reverseSearch) {
                Collections.reverse(entries);
            }

            return entries;
        } catch (Exception e) {
            logger.error("Search Page failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Object getLastSearchAfterToken() {
        return lastSeenScoreDoc;
    }

    @Override
    public List<LogEntry> searchPageAfter(String queryStr, long fromTimestamp, long toTimestamp, Object afterToken,
            int limit) {
        if (searcher == null)
            return Collections.emptyList();

        try {
            Query finalQuery = buildQuery(queryStr, fromTimestamp, toTimestamp);
            Sort sortByLineNumber = new Sort(new SortField("line_number_sort", SortField.Type.LONG)); // Corrected field
                                                                                                      // name

            ScoreDoc afterDoc = (ScoreDoc) afterToken;
            TopDocs results = searcher.searchAfter(afterDoc, finalQuery, limit, sortByLineNumber);

            if (results.scoreDocs.length > 0) {
                this.lastSeenScoreDoc = results.scoreDocs[results.scoreDocs.length - 1];
            } else {
                this.lastSeenScoreDoc = null;
            }

            return mapHitsToEntries(results.scoreDocs);
        } catch (Exception e) {
            logger.error("SearchAfter failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public long getTotalHits(String queryStr, long fromTimestamp, long toTimestamp) {
        if (searcher == null)
            return 0;
        try {
            Query finalQuery = buildQuery(queryStr, fromTimestamp, toTimestamp);
            return searcher.count(finalQuery);
        } catch (Exception e) {
            logger.error("Count failed", e);
            return 0;
        }
    }

    private Query buildQuery(String queryStr, long from, long to)
            throws org.apache.lucene.queryparser.classic.ParseException {
        BooleanQuery.Builder booleanBuilder = new BooleanQuery.Builder();

        String remainingQuery = queryStr != null ? queryStr : "";

        // 1. Extract and handle level filter separately (StringField needs exact match)
        java.util.regex.Pattern levelPattern = java.util.regex.Pattern.compile("level:(\\w+|\"[^\"]*\")");
        java.util.regex.Matcher levelMatcher = levelPattern.matcher(remainingQuery);

        while (levelMatcher.find()) {
            String levelValue = levelMatcher.group(1).replace("\"", "");
            if (!levelValue.isEmpty()) {
                // Use TermQuery for exact match on StringField
                booleanBuilder.add(new TermQuery(new Term("level", levelValue)), BooleanClause.Occur.MUST);
                logger.debug("Added level filter: {}", levelValue);
            } else {
                // Empty level means UNPARSED - match empty string
                booleanBuilder.add(new TermQuery(new Term("level", "")), BooleanClause.Occur.MUST);
                logger.debug("Added UNPARSED level filter (empty)");
            }
        }

        // Remove level clauses from remaining query
        remainingQuery = levelMatcher.replaceAll("").trim();

        // Handle -level:"" (hide unparsed)
        if (remainingQuery.contains("-level:\"\"")) {
            booleanBuilder.add(new TermQuery(new Term("level", "")), BooleanClause.Occur.MUST_NOT);
            remainingQuery = remainingQuery.replace("-level:\"\"", "").trim();
            logger.debug("Added hide unparsed filter");
        }

        // Clean up AND connectors left over
        remainingQuery = remainingQuery.replaceAll("\\s+AND\\s+", " ").trim();
        remainingQuery = remainingQuery.replaceAll("^AND\\s+", "").trim();
        remainingQuery = remainingQuery.replaceAll("\\s+AND$", "").trim();

        // 2. Text Query (what remains after level extraction)
        if (!remainingQuery.isEmpty()) {
            Query textQuery = queryParser.parse(remainingQuery);
            booleanBuilder.add(textQuery, BooleanClause.Occur.MUST);
        }

        // If no clauses added, match all
        BooleanQuery built = booleanBuilder.build();
        if (built.clauses().isEmpty()) {
            booleanBuilder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
            built = booleanBuilder.build();
        }

        // 3. Date Range Filter
        if (from > 0 || to < Long.MAX_VALUE) {
            BooleanQuery.Builder withDateRange = new BooleanQuery.Builder();
            withDateRange.add(built, BooleanClause.Occur.MUST);
            Query rangeQuery = LongPoint.newRangeQuery("timestamp", from, to);
            withDateRange.add(rangeQuery, BooleanClause.Occur.FILTER);
            return withDateRange.build();
        }

        return built;
    }

    private List<LogEntry> mapHitsToEntries(ScoreDoc[] hits) throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        StoredFields fieldReader = reader.storedFields();

        for (ScoreDoc hit : hits) {
            Document doc = fieldReader.document(hit.doc);

            long lineNumber = 0;
            if (doc.getField("line_number") != null) {
                lineNumber = doc.getField("line_number").numericValue().longValue();
            }

            String rawLog = doc.get("raw_log");
            String level = doc.get("level");
            String message = doc.get("message");

            // Reconstruct parsed fields map
            java.util.Map<String, String> fields = new java.util.HashMap<>();
            if (level != null)
                fields.put("level", level);
            if (message != null)
                fields.put("message", message);
            // Add raw log as 'unparsed' fallback if needed, or consistent with LogEntry
            // logic
            if (rawLog != null)
                fields.put("unparsed", rawLog);

            // Create LogEntry as 'Parsed'
            LogEntry entry = new LogEntry(lineNumber, rawLog, fields);

            // Rehydrate Timestamp
            if (doc.getField("timestamp_store") != null) {
                long epochMillis = doc.getField("timestamp_store").numericValue().longValue();
                if (epochMillis > 0) {
                    java.time.LocalDateTime dt = java.time.Instant.ofEpochMilli(epochMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime();
                    entry.setTimestamp(dt);
                }
            }

            entries.add(entry);
        }
        return entries;
    }

    @Override
    public long getOffsetForLineNumber(long lineNumber) {
        if (searcher == null)
            return -1;
        try {
            // Count how many documents have a line number strictly less than target
            Query rangeQuery = LongPoint.newRangeQuery("line_number_range", 0, lineNumber - 1);
            return searcher.count(rangeQuery);
        } catch (IOException e) {
            logger.error("Failed to count offset for line number {}", lineNumber, e);
            return -1;
        }
    }

    @Override
    public void close() {
        if (reader != null) {
            try {
                reader.close();
                logger.info("Closed Lucene IndexReader");
            } catch (IOException e) {
                logger.warn("Failed to close reader", e);
            }
        }
        if (executor != null) {
            executor.shutdown();
        }
    }
}
