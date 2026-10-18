package com.seeloggyplus.shared.ui;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;

public final class RenderedColors {
    private RenderedColors() { }
    public static Color background(Node node) {
        if (node == null) return Color.WHITE;
        Color result = node.getParent() == null
                ? (node.getScene() != null && node.getScene().getFill() instanceof Color color ? color : Color.WHITE)
                : background(node.getParent());
        if (node instanceof Region region && region.getBackground() != null) {
            for (var fill : region.getBackground().getFills()) {
                var paint = fill.getFill();
                Color color = paint instanceof Color value ? value
                        : paint instanceof LinearGradient gradient ? gradient.getStops().getFirst().getColor() : null;
                if (color != null) result = over(color, result);
            }
        }
        return result;
    }
    public static Color over(Color front, Color back) {
        double alpha = front.getOpacity();
        return new Color(front.getRed()*alpha + back.getRed()*(1-alpha),
                front.getGreen()*alpha + back.getGreen()*(1-alpha),
                front.getBlue()*alpha + back.getBlue()*(1-alpha), 1);
    }
}
