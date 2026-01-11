package com.seeloggyplus.test;

import com.seeloggyplus.model.LogEntry;
import com.seeloggyplus.model.ParsingConfig;
import com.seeloggyplus.service.impl.LogParserService;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.regex.Pattern;
import java.util.ArrayList;

public class TestLogParser {

    @Test
    public void testUnparsedLinesAreNotMerged() throws IOException {
        // Create a sample log file
        String content = "Line 1\nLine 2\nLine 3"; // No timestamp, should be unparsed
        Path tempFile = Files.createTempFile("test_log", ".txt");
        Files.deleteIfExists(tempFile); // Ensure clean start
        Files.writeString(tempFile, content);

        LogParserService parser = new LogParserService();
        ParsingConfig config = new ParsingConfig();
        // Manually setup config since we can't depend on full app context
        // Just empty config should result in unparsed lines if pipeline handles it,
        // or we can set a dummy pattern that doesn't match.
        config.setRegexPattern("(?<timestamp>^TIMESTAMP_ISO8601)");
        config.validatePattern();

        List<LogEntry> entries = parser.parseFileParallel(tempFile.toFile(), config, null);

        System.out.println("DEBUG: Parsed entries size: " + entries.size());
        for (int i = 0; i < entries.size(); i++) {
            System.out.println("DEBUG: Entry " + i + ": " + entries.get(i).getRawLog());
        }

        // Should be 3 entries
        assertEquals(3, entries.size(), "Should have 3 unparsed log entries");
        assertEquals("Line 1", entries.get(0).getRawLog());
        assertEquals("Line 2", entries.get(1).getRawLog());
        assertEquals("Line 3", entries.get(2).getRawLog());

        Files.delete(tempFile);
    }
}
