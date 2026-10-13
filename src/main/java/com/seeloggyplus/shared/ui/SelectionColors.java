package com.seeloggyplus.shared.ui;

import javafx.scene.paint.Color;

public final class SelectionColors {

    public static final Color LIGHT_BACKGROUND = ThemePalette.color("-sl-selection-bg", AppTheme.Theme.GRAPHITE);
    public static final Color DARK_BACKGROUND = ThemePalette.color("-sl-selection-bg", AppTheme.Theme.DARK);

    private SelectionColors() {
    }

    public static Color background(boolean dark) {
        return dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
    }
}
