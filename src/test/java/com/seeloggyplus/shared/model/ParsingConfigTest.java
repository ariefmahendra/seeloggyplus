package com.seeloggyplus.shared.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsingConfigTest {

    @Test
    void validNamedPatternIsAcceptedAndGroupsAreExtracted() {
        ParsingConfig config = new ParsingConfig("App", "(?<timestamp>\\d{4}) (?<level>\\w+) (?<message>.*)");

        assertTrue(config.isValid());
        assertNull(config.getValidationError());
        assertNotNull(config.getCompiledPattern());
        assertEquals(java.util.List.of("timestamp", "level", "message"), config.getGroupNames());
    }

    @Test
    void emptyPatternIsRejectedWithHint() {
        ParsingConfig config = new ParsingConfig("Empty", "   ");

        assertFalse(config.isValid());
        assertEquals("Regex pattern cannot be empty", config.getValidationError());
        assertTrue(config.getGroupNames().isEmpty());
        assertNull(config.getCompiledPattern());
    }

    @Test
    void patternWithoutNamedGroupsIsRejected() {
        ParsingConfig config = new ParsingConfig("Plain", "\\d+\\s+\\w+");

        assertFalse(config.isValid());
        assertTrue(config.getValidationError().contains("named group"));
    }

    @Test
    void syntacticallyInvalidPatternIsRejected() {
        ParsingConfig config = new ParsingConfig("Broken", "(unclosed");

        assertFalse(config.isValid());
        assertTrue(config.getValidationError().startsWith("Invalid regex pattern"));
        assertNull(config.getCompiledPattern());
    }

    @Test
    void compiledPatternIsLazilyRebuiltWhenCleared() {
        ParsingConfig config = new ParsingConfig();
        config.setRegexPattern("(?<message>.*)");

        assertNotNull(config.getCompiledPattern());
        assertSame(config.getCompiledPattern(), config.getCompiledPattern());
    }

    @Test
    void lazyCompilationReturnsNullForInvalidPattern() {
        ParsingConfig config = new ParsingConfig();
        config.setRegexPattern("(");

        assertNull(config.getCompiledPattern());
    }

    @Test
    void timestampFormatIsDetectedForIsoWithMillis() {
        ParsingConfig config = new ParsingConfig("Iso",
                "(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) (?<message>.*)");

        assertEquals("yyyy-MM-dd HH:mm:ss.SSS", config.autoDetectTimestampFormat());
    }

    @Test
    void timestampFormatIsDetectedForDayFirstDates() {
        ParsingConfig config = new ParsingConfig("DayFirst",
                "(?<timestamp>\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2}:\\d{2}) (?<message>.*)");

        assertEquals("dd-MM-yyyy HH:mm:ss", config.autoDetectTimestampFormat());
    }

    @Test
    void timestampFormatFallsBackToFullPrecision() {
        ParsingConfig config = new ParsingConfig("Loose", "(?<timestamp>.*) (?<message>.*)");

        assertEquals("yyyy-MM-dd HH:mm:ss.SSS", config.autoDetectTimestampFormat());
    }

    @Test
    void timestampFormatIsNullWithoutTimestampGroupOrPattern() {
        assertNull(new ParsingConfig("NoStamp", "(?<message>.*)").autoDetectTimestampFormat());
        assertNull(new ParsingConfig("Empty", "").autoDetectTimestampFormat());
    }

    @Test
    void copyKeepsContentButNeverStaysDefault() {
        ParsingConfig source = ParsingConfig.createRawConfig();
        source.setTimestampFormat("yyyy-MM-dd");

        ParsingConfig copy = source.copy();

        assertEquals(source.getName(), copy.getName());
        assertEquals(source.getDescription(), copy.getDescription());
        assertEquals(source.getRegexPattern(), copy.getRegexPattern());
        assertEquals("yyyy-MM-dd", copy.getTimestampFormat());
        assertFalse(copy.isDefault());
        assertTrue(copy.isValid());
    }

    @Test
    void toStringMarksDefaultConfigs() {
        assertEquals("Raw Text (Default)", ParsingConfig.createRawConfig().toString());
        assertEquals("Plain", new ParsingConfig("Plain", "(?<message>.*)").toString());
    }

    @Test
    void equalityUsesNameAndPattern() {
        ParsingConfig a = new ParsingConfig("Same", "(?<message>.*)");
        ParsingConfig b = new ParsingConfig("Same", "(?<message>.*)");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new ParsingConfig("Other", "(?<message>.*)"));
        assertNotEquals(a, new ParsingConfig("Same", "(?<msg>.*)"));
    }

    @Test
    void rawConfigIsValidAndTagged() {
        ParsingConfig raw = ParsingConfig.createRawConfig();

        assertEquals(ParsingConfig.RAW_CONFIG_ID, raw.getId());
        assertEquals("Raw Text", raw.getName());
        assertTrue(raw.isDefault());
        assertTrue(raw.isValid());
        assertNull(raw.getTimestampFormat());
        assertEquals(java.util.List.of("message"), raw.getGroupNames());
    }
}
