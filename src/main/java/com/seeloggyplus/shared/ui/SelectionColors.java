package com.seeloggyplus.shared.ui;

import javafx.scene.paint.Color;

public final class SelectionColors {

    public static final Color LIGHT_BACKGROUND = ThemePalette.color("-sl-selection-bg", AppTheme.Theme.LIGHT);
    public static final Color DARK_BACKGROUND = ThemePalette.color("-sl-selection-bg", AppTheme.Theme.DARK);

    private SelectionColors() {
    }

    public static Color background(boolean dark) {
        return dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
    }

    public static Color background(AppTheme.Theme theme) {
        return ThemePalette.color("-sl-selection-bg", theme);
    }

    public static Color background(AppTheme.Theme theme, boolean active) {
        return ThemePalette.color(active ? "-sl-selection-bg" : "-sl-selection-inactive-bg", theme);
    }

    public static boolean active(javafx.scene.Node node) {
        if (node.getScene() == null) return false;
        for (var owner = node.getScene().getFocusOwner(); owner != null; owner = owner.getParent()) {
            if (owner == node) return true;
        }
        return false;
    }
}
