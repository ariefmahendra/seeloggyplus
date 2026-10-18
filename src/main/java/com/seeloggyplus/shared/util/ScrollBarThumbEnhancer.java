package com.seeloggyplus.shared.util;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollBar;

public final class ScrollBarThumbEnhancer {

    static final double MIN_VISIBLE_PROPORTION = 0.12;

    private ScrollBarThumbEnhancer() {
    }

    public static double requiredVisibleAmount(double max, double visibleAmount, double minProportion) {
        if (max <= 0 || visibleAmount <= 0 || minProportion <= 0) {
            return visibleAmount;
        }
        double total = max + visibleAmount;
        double minimum = total * minProportion;
        return Math.max(visibleAmount, minimum);
    }

    public static void enhance(Control control) {
        if (control == null) {
            return;
        }
        for (Node node : control.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) {
                double required = requiredVisibleAmount(bar.getMax(), bar.getVisibleAmount(), MIN_VISIBLE_PROPORTION);
                if (required > bar.getVisibleAmount()) {
                    bar.setVisibleAmount(required);
                }
            }
        }
    }
}
