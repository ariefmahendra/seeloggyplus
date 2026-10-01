package com.seeloggyplus.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Loads the release notes that the build embeds for the running version
 * ({@code /release-notes.md}) and converts them to readable plain text for the
 * "What's New" dialog.
 */
public final class ReleaseNotes {

    static final String RESOURCE = "/release-notes.md";

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\([^)]+\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s*");
    private static final Pattern BLANK_RUN = Pattern.compile("\\n{3,}");

    private ReleaseNotes() {
    }

    /** Raw markdown of the embedded notes, or empty when the build had none. */
    public static Optional<String> load() {
        return load(ReleaseNotes.class.getResourceAsStream(RESOURCE));
    }

    static Optional<String> load(InputStream stream) {
        if (stream == null) {
            return Optional.empty();
        }
        try (stream) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Minimal markdown cleanup for display in a plain text area. */
    public static String toPlainText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String text = LINK.matcher(markdown).replaceAll("$1");
        text = BOLD.matcher(text).replaceAll("$1");
        text = HEADING.matcher(text).replaceAll("");
        text = text.replace("`", "");
        text = BLANK_RUN.matcher(text).replaceAll("\n\n");
        return text.trim();
    }

    /** True when the "What's New" dialog should be shown for {@code currentVersion}. */
    public static boolean shouldShow(String currentVersion, String seenVersion) {
        if (currentVersion == null || currentVersion.isBlank() || "DEV".equalsIgnoreCase(currentVersion)) {
            return false;
        }
        return !currentVersion.equals(seenVersion);
    }
}
