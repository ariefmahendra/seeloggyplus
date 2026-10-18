package com.seeloggyplus.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ThemeTokensTest {

    private static final String THEME = "/style/theme.css";
    private static final String THEME_DARK = "/style/theme-dark.css";
    private static final String THEME_LIGHT = "/style/theme-light.css";
    private static final String COMPONENTS = "/style/components.css";
    private static final String RICHTEXT = "/style/richtext.css";

    @Test
    @DisplayName("Every theme defines the same set of design tokens")
    void allThemesDefineTheSameTokens() throws Exception {
        Set<String> base = rootTokens(THEME);
        Set<String> dark = rootTokens(THEME_DARK);
        Set<String> light = rootTokens(THEME_LIGHT);

        assertTrue(base.isEmpty(), "theme.css must contain shared rules, not a third palette");
        assertFalse(light.isEmpty());
        assertEquals(light, dark,"Light and Dark must define the same complete token set");
        var required=new LinkedHashSet<String>();
        var references=Pattern.compile("-sl-[a-z0-9-]+").matcher(read(THEME)+read(COMPONENTS)+read(RICHTEXT));
        while(references.find()) required.add(references.group());
        assertTrue(light.containsAll(required),"Missing theme tokens: "+missing(required,light));
    }

    @Test
    @DisplayName("Light theme uses an official Islands blue accent for interactive states")
    void lightThemeUsesBlueAccent() throws Exception {
        String light = normalize(read(THEME_LIGHT));
        assertEquals("#3871e1", tokenValue(light, "-sl-accent"),
                "light theme accent must be official Islands blue");
        assertEquals("#3871e1", tokenValue(light, "-sl-brand"),
                "light theme focus and selection must share the muted accent");
        assertEquals("#ffffff", tokenValue(light, "-sl-accent-text"),
                "light theme accent text must be white");
    }

    @Test
    @DisplayName("Base + component CSS use tokens, not hard-coded colours")
    void componentCssHasNoHardCodedColours() throws Exception {
        String theme = removeRootBlock(read(THEME));
        assertNoRawColours(theme, THEME);
        assertNoRawColours(read(COMPONENTS), COMPONENTS);
        assertNoRawColours(read(RICHTEXT), RICHTEXT);
    }

    private static void assertNoRawColours(String css, String name) {
        Pattern raw = Pattern.compile("#[0-9a-fA-F]{3,8}|rgba?\\s*\\(", Pattern.CASE_INSENSITIVE);
        List<String> offenders = new ArrayList<>();

        String withoutComments = css.replaceAll("(?s)/\\*.*?\\*/", "");
        String[] lines = withoutComments.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher m = raw.matcher(line);
            while (m.find()) {
                String match = m.group().toLowerCase();

                boolean allowedShadow = match.startsWith("rgba") && line.contains("rgba(0, 0, 0");
                if (!allowedShadow) {
                    offenders.add(name + ":" + (i + 1) + " -> " + line.trim());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "Component colours must come from -sl-* tokens so every theme adapts. "
                        + "Hard-coded colours found:\n" + String.join("\n", offenders));
    }

    private static Set<String> rootTokens(String resource) throws Exception {
        String css = read(resource);
        String block = rootBlock(css);
        assertNotNull(block, resource + " must contain a .root block");
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(-sl-[a-z0-9-]+)\\s*:").matcher(block);
        while (m.find()) {
            tokens.add(m.group(1));
        }
        return tokens;
    }

    private static String tokenValue(String normalizedCss, String token) {
        Matcher m = Pattern.compile(Pattern.quote(token) + ":([^;]+);").matcher(normalizedCss);
        return m.find() ? m.group(1) : null;
    }

    private static String rootBlock(String css) {
        int start = css.indexOf(".root");
        if (start < 0) {
            return null;
        }
        int open = css.indexOf('{', start);
        if (open < 0) {
            return null;
        }
        int depth = 0;
        for (int i = open; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return css.substring(open + 1, i);
                }
            }
        }
        return null;
    }

    private static String removeRootBlock(String css) {
        String block = rootBlock(css);
        return block == null ? css : css.replace(block, "");
    }

    private static String missing(Set<String> expected, Set<String> actual) {
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        return String.join(", ", missing);
    }

    private static String normalize(String css) {
        return css.replaceAll("\\s+", "").toLowerCase();
    }

    private static String read(String resource) throws Exception {
        try (InputStream in = ThemeTokensTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing resource: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
