package com.seeloggyplus.shared.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FxTextHighlighter {

    public static final String HIGHLIGHT_STYLE_CLASS = "match-highlight";
    public static final String NORMAL_STYLE_CLASS = "match-run";

    private FxTextHighlighter() {
    }

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

        box.setMinWidth(Region.USE_PREF_SIZE);
        apply(box, text, pattern);
        return box;
    }
}
