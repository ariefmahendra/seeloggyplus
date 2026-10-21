package com.seeloggyplus.shared.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogEntryTest {
    @Test void rawEntriesShareAnEmptyFieldMapUntilMutation() throws Exception {
        var field = LogEntry.class.getDeclaredField("parsedFields");
        field.setAccessible(true);
        var first = new LogEntry(1, "raw");
        var second = new LogEntry(2, "other");
        assertSame(field.get(first), field.get(second));
        first.setLevel("ERROR");
        assertEquals("ERROR", first.getField("level"));
        assertNull(second.getField("level"));
        var copy = first.getParsedFields();
        copy.clear();
        assertEquals("ERROR", first.getField("level"));
    }


    @Test
    void unparsedEntryKeepsRawLogAsMessageAndField() {
        LogEntry entry = new LogEntry(3, "raw line");

        assertEquals(3, entry.getLineNumber());
        assertEquals(3, entry.getEndLineNumber());
        assertEquals("raw line", entry.getRawLog());
        assertEquals("raw line", entry.getMessage());
        assertEquals("", entry.getLevel());
        assertEquals("", entry.getThread());
        assertEquals("", entry.getLogger());
        assertTrue(entry.getParsedFields().isEmpty());
        assertFalse(entry.isParsed());
        assertNull(entry.getTimestamp());
    }

    @Test
    void rangedEntryExposesStartAndEndLines() {
        LogEntry entry = new LogEntry(5, 9, "stack trace");

        assertEquals(5, entry.getLineNumber());
        assertEquals(9, entry.getEndLineNumber());
        assertEquals("stack trace", entry.getField("unparsed"));
    }

    @Test
    void matcherConstructorExtractsNamedGroups() {
        Pattern pattern = Pattern.compile("(?<level>\\w+)\\s+(?<message>.*)");
        Matcher matcher = pattern.matcher("INFO hello world");
        assertTrue(matcher.matches());

        LogEntry entry = new LogEntry(7, "INFO hello world", matcher, List.of("level", "message"));

        assertTrue(entry.isParsed());
        assertEquals("INFO", entry.getLevel());
        assertEquals("hello world", entry.getMessage());
        assertEquals("", entry.getThread());
        assertEquals("", entry.getLogger());
    }

    @Test
    void mapConstructorCopiesTheSourceMap() {
        Map<String, String> fields = new HashMap<>();
        fields.put("level", "WARN");
        fields.put("message", "disk full");

        LogEntry entry = new LogEntry(1, "WARN disk full", fields);
        fields.clear();

        assertEquals("WARN", entry.getLevel());
        assertEquals("disk full", entry.getMessage());
    }

    @Test
    void parsedFieldsAccessorIsDefensiveCopy() {
        LogEntry entry = new LogEntry(1, "line", Map.of("level", "INFO"));

        Map<String, String> copy = entry.getParsedFields();
        copy.put("level", "CHANGED");

        assertEquals("INFO", entry.getField("level"));
        assertNull(entry.getField("missing"));
    }

    @Test
    void settersSynchronizeParsedFields() {
        LogEntry entry = new LogEntry(1, "line");
        LocalDateTime timestamp = LocalDateTime.of(2026, 10, 12, 10, 20, 30);

        entry.setTimestamp(timestamp);
        entry.setLevel("ERROR");
        entry.setMessage("boom");
        entry.setThread("worker-1");
        entry.setLogger("com.example.Service");

        assertSame(timestamp, entry.getTimestamp());
        assertEquals(timestamp.toString(), entry.getField("timestamp"));
        assertEquals("ERROR", entry.getField("level"));
        assertEquals("boom", entry.getField("message"));
        assertEquals("worker-1", entry.getField("thread"));
        assertEquals("com.example.Service", entry.getField("logger"));
    }

    @Test
    void setTimestampNullDoesNotOverwriteStoredValue() {
        LogEntry entry = new LogEntry(1, "line");
        entry.setTimestamp(null);

        assertNull(entry.getTimestamp());
        assertNull(entry.getField("timestamp"));
    }

    @Test
    void addFieldAndAddFieldsMergeIntoParsedFields() {
        LogEntry entry = new LogEntry(1, "line");

        entry.addField("thread", "t1");
        entry.addFields(Map.of("logger", "L", "level", "INFO"));

        assertEquals("t1", entry.getField("thread"));
        assertEquals("L", entry.getField("logger"));
        assertEquals("INFO", entry.getField("level"));
    }

    @Test
    void matchesHonoursCaseSensitivityAndEmptyQueries() {
        LogEntry entry = new LogEntry(1, "Connection Refused");

        assertTrue(entry.matches(null, false));
        assertTrue(entry.matches("", true));
        assertTrue(entry.matches("connection", false));
        assertFalse(entry.matches("connection", true));
        assertTrue(entry.matches("Refused", true));
        assertFalse(entry.matches("timeout", false));
    }

    @Test
    void matchesRegexUsesFullMatchAndNeverThrows() {
        LogEntry entry = new LogEntry(1, "abc123");

        assertTrue(entry.matchesRegex(null));
        assertTrue(entry.matchesRegex(""));
        assertTrue(entry.matchesRegex("abc\\d+"));
        assertFalse(entry.matchesRegex("b.*"));
        assertFalse(entry.matchesRegex("("));
    }

    @Test
    void toStringReturnsTheRawLog() {
        assertEquals("payload", new LogEntry(1, "payload").toString());
    }

    @Test
    void equalityUsesLineNumberAndRawLog() {
        LogEntry a = new LogEntry(1, "same");
        LogEntry b = new LogEntry(1, "same");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new LogEntry(2, "same"));
        assertNotEquals(a, new LogEntry(1, "other"));
        assertNotEquals(a, null);
        assertNotEquals(a, "same");
    }
}
