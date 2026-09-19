package com.seeloggyplus.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a single-line rich-text row that highlights every occurrence of a search pattern.
 * <p>
 * Mirrors the canvas log viewer approach: the match gets a yellow background mark while the
 * text stays readable. An {@link HBox} is used instead of {@link javafx.scene.text.TextFlow}
 * so the row never wraps, keeping cell heights stable (the caller clips overflow).
 */
public final class FxTextHighlighter {

    public static final String HIGHLIGHT_STYLE_CLASS = "match-highlight";
    public static final String NORMAL_STYLE_CLASS = "match-run";

    private FxTextHighlighter() {
    }

    /**
     * Appends {@code text} to {@code target}, wrapping each pattern match in a
     * yellow-background run. Zero-length matches are skipped safely.
     */
    public static void apply(HBox target, String text, Pattern pattern) {
        if (target == null || text == null) {
            return;
        }
        if (pattern == null) {
            target.getChildren().add(run(text, false));
            return;
        }
        Matcher matcher = pattern.matcher(text);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() == matcher.end()) {
                continue;
            }
            if (matcher.start() > last) {
                target.getChildren().add(run(text.substring(last, matcher.start()), false));
            }
            target.getChildren().add(run(matcher.group(), true));
            last = matcher.end();
        }
        if (last < text.length()) {
            target.getChildren().add(run(text.substring(last), false));
        }
    }

    private static Label run(String value, boolean highlight) {
        Label label = new Label(value);
        label.getStyleClass().add(highlight ? HIGHLIGHT_STYLE_CLASS : NORMAL_STYLE_CLASS);
        label.setPadding(highlight ? new Insets(0, 1, 0, 1) : Insets.EMPTY);
        label.setMinWidth(0);
        return label;
    }

    public static HBox build(String text, Pattern pattern) {
        HBox box = new HBox();
        box.setSpacing(0);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setFillHeight(false);
        // Keep runs at their natural width; the cell clips overflow so rows stay single-line.
        box.setMinWidth(Region.USE_PREF_SIZE);
        apply(box, text, pattern);
        return box;
    }
}
