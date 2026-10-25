package com.seeloggyplus.shared.ui;

import com.seeloggyplus.shared.ui.AppTheme.Theme;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class ThemePalette {

    private static final Map<Theme, Map<String, Color>> CACHE = new EnumMap<>(Theme.class);

    private ThemePalette() {
    }

    public static Color color(String token, Theme theme) {
        Map<String, Color> palette = palette(theme == null ? Theme.LIGHT : theme);
        Color color = palette.get(token);
        if (color == null) {
            throw new IllegalArgumentException("Unknown theme token: " + token);
        }
        return color;
    }

    private static synchronized Map<String, Color> palette(Theme theme) {
        Map<String, Color> cached = CACHE.get(theme);
        if (cached != null) {
            return cached;
        }
        Map<String, Color> palette = new HashMap<>();
        merge(read(theme.stylesheet()), palette);
        Map<String, Color> immutable = Map.copyOf(palette);
        CACHE.put(theme, immutable);
        return immutable;
    }

    private static String read(String resource) {
        try (InputStream input = ThemePalette.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing theme stylesheet: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void merge(String css, Map<String, Color> target) {
        String block = rootBlock(css);
        if (block == null) {
            throw new IllegalStateException("Theme stylesheet has no root token block");
        }
        int index = 0;
        while ((index = block.indexOf("-sl-", index)) >= 0) {
            int end = block.indexOf(';', index);
            if (end < 0) {
                break;
            }
            int colon = block.indexOf(':', index);
            if (colon < 0 || colon > end) {
                index = end + 1;
                continue;
            }
            target.put(block.substring(index, colon).trim(), Color.web(block.substring(colon + 1, end).trim()));
            index = end + 1;
        }
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
}
