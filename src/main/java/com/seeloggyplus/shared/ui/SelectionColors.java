package com.seeloggyplus.shared.ui;

import javafx.scene.paint.Color;

public final class SelectionColors {

    public static final Color LIGHT_BACKGROUND = Color.web("#343a40", 0.18);
    public static final Color DARK_BACKGROUND = Color.web("#6ea8fe", 0.28);

    private SelectionColors() {
    }

    public static Color background(boolean dark) {
        return dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
    }
}
