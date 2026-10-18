package com.seeloggyplus.shared.ui;

import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

public final class RoundedPanelSupport {
    private RoundedPanelSupport() { }

    public static void apply(Region panel) {
        if (panel == null) return;
        if (!panel.getStyleClass().contains("workspace-island")) panel.getStyleClass().add("workspace-island");
        Rectangle clip = new Rectangle();
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        clip.widthProperty().bind(panel.widthProperty());
        clip.heightProperty().bind(panel.heightProperty());
        panel.setClip(clip);
    }
}
