package com.seeloggyplus.shared.ui;

import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

public final class RoundedPanelSupport {
    private RoundedPanelSupport() { }

    public static void apply(Region panel) {
        apply(panel, 10);
    }

    public static void apply(Region panel, double radius) {
        if (panel == null) return;
        if (!panel.getStyleClass().contains("workspace-island")) panel.getStyleClass().add("workspace-island");
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(panel.widthProperty());
        clip.heightProperty().bind(panel.heightProperty());
        panel.setClip(clip);
    }
}
