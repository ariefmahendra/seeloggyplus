package com.seeloggyplus.shared.util;

import com.seeloggyplus.features.update.domain.ReleaseNotes;
import com.seeloggyplus.features.update.domain.VersionComparator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedUtilCoverageTest {

    @Test
    @DisplayName("JsonPrettify returns blank input untouched")
    void jsonPrettifyBlankInput() {
        assertNull(JsonPrettify.prettify(null));
        assertEquals("   ", JsonPrettify.prettify("   "));
    }

    @Test
    @DisplayName("JsonPrettify minify handles blank and malformed input")
    void jsonMinifyEdgeCases() {
        assertNull(JsonPrettify.minify(null));
        assertEquals("  ", JsonPrettify.minify("  "));
        assertEquals("{broken", JsonPrettify.minify("{broken"));
    }

    @Test
    @DisplayName("JsonPrettify extracts regions and reports containment")
    void jsonRegionExtraction() {
        assertEquals("{\"a\":1}", JsonPrettify.extractJson("prefix {\"a\":1} suffix"));
        assertNull(JsonPrettify.extractJson(null));
        assertNull(JsonPrettify.extractJson(""));
        assertNull(JsonPrettify.extractJson("no json here"));

        assertTrue(JsonPrettify.containsJson("x {\"a\":1}"));
        assertFalse(JsonPrettify.containsJson("plain text"));
        assertFalse(JsonPrettify.containsJson(null));
    }

    @Test
    @DisplayName("JsonPrettify validation error distinguishes empty, valid and invalid input")
    void jsonValidationErrors() {
        assertEquals("JSON string is empty", JsonPrettify.getValidationError(null));
        assertEquals("JSON string is empty", JsonPrettify.getValidationError("   "));
        assertNull(JsonPrettify.getValidationError("{\"a\":1}"));
        assertNotNull(JsonPrettify.getValidationError("{oops}"));
    }

    @Test
    @DisplayName("JsonPrettify leaves null log lines alone")
    void jsonPrettifyFromLogNull() {
        assertNull(JsonPrettify.prettifyFromLog(null));
        assertEquals("  ", JsonPrettify.prettifyFromLog("  "));
    }

    @Test
    @DisplayName("XmlPrettify returns blank input untouched")
    void xmlPrettifyBlankInput() {
        assertNull(XmlPrettify.prettify(null));
        assertEquals("   ", XmlPrettify.prettify("   "));
        assertNull(XmlPrettify.prettifyFromLog(null));
    }

    @Test
    @DisplayName("XmlPrettify minify handles blank and malformed input")
    void xmlMinifyEdgeCases() {
        assertNull(XmlPrettify.minify(null));
        assertEquals("  ", XmlPrettify.minify("  "));
        assertEquals("<broken", XmlPrettify.minify("<broken"));
    }

    @Test
    @DisplayName("XmlPrettify extracts a plain XML region from surrounding text")
    void xmlRegionExtraction() {
        assertEquals("<root><a>1</a></root>", XmlPrettify.extractXml("prefix <root><a>1</a></root> suffix"));
        assertNull(XmlPrettify.extractXml(null));
        assertNull(XmlPrettify.extractXml(""));
        assertNull(XmlPrettify.extractXml("no xml here"));

        assertTrue(XmlPrettify.containsXml("x <a>1</a>"));
        assertFalse(XmlPrettify.containsXml("plain text"));
        assertFalse(XmlPrettify.containsXml(null));
    }

    @Test
    @DisplayName("XmlPrettify extracts regions that start with an XML declaration")
    void xmlRegionWithDeclaration() {
        String declaration = "<?xml version=\"1.0\"?>";
        assertEquals(declaration + "<root><a>1</a></root>",
                XmlPrettify.extractXml(declaration + "<root><a>1</a></root>"));
        assertEquals(declaration + "<r><x>1</x></r>",
                XmlPrettify.extractXml("2026 [INFO] " + declaration + "<r><x>1</x></r> done"));
    }

    @Test
    @DisplayName("XmlPrettify extracts self-closing tags and skips mismatched nesting")
    void xmlSelfClosingAndMismatch() {
        assertEquals("<br/>", XmlPrettify.extractXml("text <br/> tail"));
        assertNull(XmlPrettify.extractXml("<a><b></a></b>"));
    }

    @Test
    @DisplayName("XmlPrettify validation error distinguishes empty, valid and invalid input")
    void xmlValidationErrors() {
        assertEquals("XML string is empty", XmlPrettify.getValidationError(null));
        assertEquals("XML string is empty", XmlPrettify.getValidationError("   "));
        assertNull(XmlPrettify.getValidationError("<a>1</a>"));
        assertNotNull(XmlPrettify.getValidationError("<a>"));
    }

    @Test
    @DisplayName("FileUtils formats byte counts for every unit")
    void fileUtilsFormatsSizes() {
        assertEquals("512 B", FileUtils.formatFileSize(512L));
        assertEquals("2.00 KB", FileUtils.formatFileSize(2048L));
        assertEquals("3.00 MB", FileUtils.formatFileSize(3L * 1024 * 1024));
        assertEquals("2.00 GB", FileUtils.formatFileSize(2L * 1024 * 1024 * 1024));
    }

    @Test
    @DisplayName("FileUtils parses numeric strings and keeps unparsable ones")
    void fileUtilsParsesStrings() {
        assertEquals("2.00 KB", FileUtils.formatFileSize("2048"));
        assertEquals("0 B", FileUtils.formatFileSize((String) null));
        assertEquals("0 B", FileUtils.formatFileSize(""));
        assertEquals("not-a-size", FileUtils.formatFileSize("not-a-size"));
    }

    @Test
    @DisplayName("VersionComparator handles prerelease identifiers of different lengths")
    void versionComparatorPrereleaseLengths() {
        assertTrue(VersionComparator.compare("1.0.0-alpha", "1.0.0-alpha.1") < 0);
        assertTrue(VersionComparator.compare("1.0.0-alpha.1", "1.0.0-alpha") > 0);
        assertTrue(VersionComparator.compare("1.0.0-x.", "1.0.0-x.y") < 0);
    }

    @Test
    @DisplayName("ReleaseNotes loads the packaged notes and tolerates a failing stream")
    void releaseNotesLoadEdgeCases() throws Exception {
        assertTrue(ReleaseNotes.load().isPresent());

        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        java.lang.reflect.Method load = ReleaseNotes.class.getDeclaredMethod("load", InputStream.class);
        load.setAccessible(true);
        assertTrue(((java.util.Optional<?>) load.invoke(null, failing)).isEmpty());
    }

    @Test
    @DisplayName("ReleaseNotes load reads a stream with real content")
    void releaseNotesLoadContent() throws Exception {
        java.lang.reflect.Method load = ReleaseNotes.class.getDeclaredMethod("load", InputStream.class);
        load.setAccessible(true);
        assertEquals("hello", ((java.util.Optional<?>) load.invoke(null, new java.io.ByteArrayInputStream(
                "hello".getBytes(StandardCharsets.UTF_8)))).orElseThrow());
    }
}
