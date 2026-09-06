package com.seeloggyplus.util;

import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SyntaxHighlighterTest {

    @Test
    @DisplayName("Should correctly identify JSON syntax elements")
    void testComputeJsonHighlighting() {
        String json = "{\n" +
                "  \"id\" : 101,\n" +
                "  \"name\" : \"Alice\",\n" +
                "  \"active\" : true,\n" +
                "  \"extra\" : null\n" +
                "}";

        StyleSpans<Collection<String>> spans = SyntaxHighlighter.computeJsonHighlighting(json);
        assertNotNull(spans);

        Set<String> detectedClasses = new HashSet<>();
        for (StyleSpan<Collection<String>> span : spans) {
            detectedClasses.addAll(span.getStyle());
        }

        assertTrue(detectedClasses.contains("json-key"));
        assertTrue(detectedClasses.contains("json-string"));
        assertTrue(detectedClasses.contains("json-number"));
        assertTrue(detectedClasses.contains("json-boolean"));
        assertTrue(detectedClasses.contains("json-null"));
        assertTrue(detectedClasses.contains("json-punctuation"));
    }

    @Test
    @DisplayName("Should correctly identify XML syntax elements")
    void testComputeXmlHighlighting() {
        String xml = "<?xml version=\"1.0\"?>\n" +
                "<!-- comment -->\n" +
                "<Order id=\"123\">\n" +
                "  <item>Phone</item>\n" +
                "</Order>";

        StyleSpans<Collection<String>> spans = SyntaxHighlighter.computeXmlHighlighting(xml);
        assertNotNull(spans);

        Set<String> detectedClasses = new HashSet<>();
        for (StyleSpan<Collection<String>> span : spans) {
            detectedClasses.addAll(span.getStyle());
        }

        assertTrue(detectedClasses.contains("xml-declaration"));
        assertTrue(detectedClasses.contains("xml-comment"));
        assertTrue(detectedClasses.contains("xml-tag"));
        assertTrue(detectedClasses.contains("xml-attribute"));
        assertTrue(detectedClasses.contains("xml-value"));
    }

    @Test
    @DisplayName("Should correctly identify standard log level keywords")
    void testComputeLogHighlighting() {
        String log = "2026-09-05 ERROR Database failed WARN Retry attempt INFO Success DEBUG details TRACE packet";
        StyleSpans<Collection<String>> spans = SyntaxHighlighter.computeLogHighlighting(log);
        assertNotNull(spans);

        Set<String> detectedClasses = new HashSet<>();
        for (StyleSpan<Collection<String>> span : spans) {
            detectedClasses.addAll(span.getStyle());
        }

        assertTrue(detectedClasses.contains("error"));
        assertTrue(detectedClasses.contains("warn"));
        assertTrue(detectedClasses.contains("info"));
        assertTrue(detectedClasses.contains("debug"));
        assertTrue(detectedClasses.contains("trace"));
    }

    @Test
    @DisplayName("Should handle empty and null strings safely")
    void testEmptyAndNullSafety() {
        assertDoesNotThrow(() -> {
            StyleSpans<Collection<String>> spansEmpty = SyntaxHighlighter.computeJsonHighlighting("");
            assertEquals(1, spansEmpty.getSpanCount());
            assertEquals(0, spansEmpty.length());

            StyleSpans<Collection<String>> spansNull = SyntaxHighlighter.computeJsonHighlighting(null);
            assertEquals(1, spansNull.getSpanCount());
            assertEquals(0, spansNull.length());

            StyleSpans<Collection<String>> xmlEmpty = SyntaxHighlighter.computeXmlHighlighting("");
            assertEquals(1, xmlEmpty.getSpanCount());
            assertEquals(0, xmlEmpty.length());

            StyleSpans<Collection<String>> logEmpty = SyntaxHighlighter.computeLogHighlighting("");
            assertEquals(1, logEmpty.getSpanCount());
            assertEquals(0, logEmpty.length());
        });
    }
}
