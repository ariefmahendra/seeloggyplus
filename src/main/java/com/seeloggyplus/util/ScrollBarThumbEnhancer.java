package com.seeloggyplus.util;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollBar;

/**
 * Makes virtualized controls' scrollbar thumbs easier to grab, mirroring the canvas
 * log viewer rule: the thumb must represent at least 12% of the scrollable content.
 */
public final class ScrollBarThumbEnhancer {

    static final double MIN_VISIBLE_PROPORTION = 0.12;

    private ScrollBarThumbEnhancer() {
    }

    /**
     * @param max          scrollbar max (content length - visible amount)
     * @param visibleAmount current visible amount
     * @return a visible amount that yields a thumb of at least {@value #MIN_VISIBLE_PROPORTION}
     *         of the track, or the original value if it is already large enough
     */
    public static double requiredVisibleAmount(double max, double visibleAmount, double minProportion) {
        if (max <= 0 || visibleAmount <= 0 || minProportion <= 0) {
            return visibleAmount;
        }
        double total = max + visibleAmount;
        double minimum = total * minProportion;
        return Math.max(visibleAmount, minimum);
    }

    /** Applies the minimum-thumb rule to every vertical scrollbar of the control. */
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
