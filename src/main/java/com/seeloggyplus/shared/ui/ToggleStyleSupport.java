package com.seeloggyplus.shared.ui;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;

public final class ToggleStyleSupport {

    private ToggleStyleSupport() {
    }

    public static void configure(ToggleButton button) {
        if (button == null) {
            return;
        }
        updateIconColor(button, button.isSelected());
        button.selectedProperty().addListener((obs, oldVal, newVal) -> updateIconColor(button, newVal));
    }

    public static void updateIconColor(ToggleButton button, boolean selected) {
        if (button == null) {
            return;
        }
        Node graphic = button.getGraphic();
        if (graphic instanceof FontAwesomeIconView icon) {
            icon.setFill(selected ? Color.WHITE : Color.web("#333333"));
        } else if (graphic instanceof Shape shape) {
            shape.setFill(selected ? Color.WHITE : Color.web("#333333"));
        }
        if (button.getScene() != null) {
            button.applyCss();
        }
    }
}
