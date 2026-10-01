package com.seeloggyplus.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseNotesTest {

    @Test
    @DisplayName("markdown is cleaned up for the plain-text dialog")
    void plainTextCleanup() {
        String markdown = """
                # SeeLoggyPlus 0.7.0

                ## Features
                - **Private-key auth** with [release notes](https://example.com/notes)
                - `code` stays readable
                """;

        String text = ReleaseNotes.toPlainText(markdown);

        assertFalse(text.contains("#"), "headings markers must be removed");
        assertFalse(text.contains("**"), "bold markers must be removed");
        assertFalse(text.contains("](http"), "links must be reduced to their label");
        assertFalse(text.contains("`"), "inline code markers must be removed");
        assertTrue(text.contains("SeeLoggyPlus 0.7.0"));
        assertTrue(text.contains("Private-key auth with release notes"));
        assertTrue(text.contains("code stays readable"));
    }

    @Test
    @DisplayName("blank or missing notes result in empty output")
    void blankInputs() {
        assertEquals("", ReleaseNotes.toPlainText(null));
        assertEquals("", ReleaseNotes.toPlainText("   "));
        assertTrue(ReleaseNotes.load(null).isEmpty());
        assertTrue(ReleaseNotes.load(new ByteArrayInputStream("  \n".getBytes(StandardCharsets.UTF_8))).isEmpty());
    }

    @Test
    @DisplayName("load reads the embedded notes")
    void loadReadsStream() {
        assertTrue(ReleaseNotes.load(
                new ByteArrayInputStream("# Notes\nhello".getBytes(StandardCharsets.UTF_8))).isPresent());
    }

    @Test
    @DisplayName("What's New shows once per real version")
    void shouldShowRules() {
        assertTrue(ReleaseNotes.shouldShow("0.7.0", null));
        assertTrue(ReleaseNotes.shouldShow("0.7.0", "0.6.3"));
        assertFalse(ReleaseNotes.shouldShow("0.7.0", "0.7.0"));
        assertFalse(ReleaseNotes.shouldShow("DEV", null), "development builds must not pop the dialog");
        assertFalse(ReleaseNotes.shouldShow(null, null));
        assertFalse(ReleaseNotes.shouldShow("  ", null));
    }
}
