package com.seeloggyplus.ui;

import javafx.scene.paint.Color;

/**
 * Single source of truth for text-selection highlight colours so that every
 * text surface (canvas log, search result panel, RichTextFX detail panel and
 * JavaFX text controls) looks consistent.
 * <p>
 * The CSS equivalent lives in {@code theme.css} ({@code -sl-selection-bg},
 * {@code -fx-highlight-fill}) and {@code richtext.css} ({@code .selection});
 * tests assert they stay in sync.
 */
public final class SelectionColors {

    public static final Color LIGHT_BACKGROUND = Color.web("#343a40", 0.18);
    public static final Color DARK_BACKGROUND = Color.web("#6ea8fe", 0.28);

    private SelectionColors() {
    }

    public static Color background(boolean dark) {
        return dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
    }
}
