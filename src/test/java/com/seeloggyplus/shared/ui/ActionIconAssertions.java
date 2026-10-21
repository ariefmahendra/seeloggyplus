package com.seeloggyplus.shared.ui;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.css.PseudoClass;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ToggleButton;
import javafx.scene.paint.Color;

import static org.junit.jupiter.api.Assertions.*;

public final class ActionIconAssertions {
    private ActionIconAssertions() {
    }

    public static void verify(Parent root, String id, String glyph) {
        var button = assertInstanceOf(ButtonBase.class, root.lookup("#" + id));
        var icon = assertInstanceOf(FontAwesomeIconView.class, button.getGraphic());
        assertEquals(glyph, icon.getGlyphName(), id);
        assertNotNull(button.getAccessibleText(), id + " needs an accessible name");
        assertFalse(button.getAccessibleText().isBlank(), id);
        assertNotNull(button.getTooltip(), id + " needs a tooltip");
        assertFalse(button.getTooltip().getText().isBlank(), id);
        var originalTheme = AppTheme.getTheme();
        boolean disabled = button.isDisable();
        boolean selected = button instanceof ToggleButton toggle && toggle.isSelected();
        try {
            button.setDisable(false);
            for (var theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                for (String state : new String[]{"normal", "hover", "pressed", "selected"}) {
                    var pseudo = PseudoClass.getPseudoClass(state);
                    if (button instanceof ToggleButton toggle) toggle.setSelected(state.equals("selected"));
                    if (!state.equals("normal") && !state.equals("selected")) button.pseudoClassStateChanged(pseudo, true);
                    try {
                        root.applyCss();
                        root.layout();
                        assertTrue(icon.getFont().getSize() >= 12, id);
                        assertTrue(button.getHeight() > 0 && button.getHeight() <= 36, id);
                        var bounds = button.sceneToLocal(icon.localToScene(icon.getBoundsInLocal()));
                        assertTrue(bounds.getMinX() >= -0.5 && bounds.getMaxX() <= button.getWidth() + 0.5, id + " horizontal clipping");
                        assertTrue(bounds.getMinY() >= -0.5 && bounds.getMaxY() <= button.getHeight() + 0.5, id + " vertical clipping");
                        Color foreground = (Color) icon.getFill();
                        Color background = RenderedColors.background(icon);
                        double a = luminance(foreground), b = luminance(background);
                        assertTrue((Math.max(a, b) + .05) / (Math.min(a, b) + .05) >= 3, id + " contrast " + theme + " " + state);
                    } finally {
                        button.pseudoClassStateChanged(pseudo, false);
                    }
                }
            }
        } finally {
            if (button instanceof ToggleButton toggle) toggle.setSelected(selected);
            button.setDisable(disabled);
            AppTheme.setTheme(originalTheme);
        }
    }

    private static double luminance(Color c) {
        return .2126 * linear(c.getRed()) + .7152 * linear(c.getGreen()) + .0722 * linear(c.getBlue());
    }

    private static double linear(double v) {
        return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
    }
}
