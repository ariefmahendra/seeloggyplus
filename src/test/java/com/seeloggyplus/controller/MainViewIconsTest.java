package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.css.PseudoClass;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class MainViewIconsTest {

    private Parent root;

    @Start
    void start(Stage stage) throws Exception {
        root = FXMLLoader.load(getClass().getResource("/fxml/MainView.fxml"));
        stage.setScene(AppTheme.scene(root));
        stage.setWidth(1100);
        stage.setHeight(720);
        stage.show();
    }

    @AfterEach
    void resetTheme(FxRobot robot) {
        robot.interact(() -> AppTheme.setTheme(AppTheme.Theme.GRAPHITE));
    }

    @Test
    void clearAndCloseDetailHaveDistinctActionIcons(FxRobot robot) {
        robot.interact(() -> {
            ButtonBase clear = button("clearDetailButton");
            ButtonBase close = button("pinBottomPanelButton");
            assertEquals("ERASER", icon(clear).getGlyphName());
            assertEquals("TIMES", icon(close).getGlyphName());
            assertNotEquals(icon(clear).getGlyphName(), icon(close).getGlyphName());
            assertTrue(clear.getTooltip().getText().contains("Detail"), "Clear should describe which content it removes");
            assertTrue(close.getTooltip().getText().contains("Close Detail Panel"));
        });
    }

    @Test
    void tailUsesALogConsoleIconInsteadOfAnEye(FxRobot robot) {
        robot.interact(() -> {
            ButtonBase tail = button("tailButton");
            assertEquals("TERMINAL", icon(tail).getGlyphName());
            assertTrue(tail.getTooltip().getText().contains("Tail"));
        });
    }

    @Test
    void actionIconsRemainReadableAndCompactInEveryTheme(FxRobot robot) {
        robot.interact(() -> {
            Node detail = root.lookup("#bottomPanel");
            detail.setVisible(true);
            detail.setManaged(true);
            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                for (String id : new String[]{"clearDetailButton", "pinBottomPanelButton", "tailButton"}) {
                    ButtonBase button = button(id);
                    button.setDisable(false);
                    for (String state : new String[]{"hover", "pressed", "focused"}) {
                        PseudoClass pseudo = PseudoClass.getPseudoClass(state);
                        button.pseudoClassStateChanged(pseudo, true);
                        root.applyCss();
                        root.layout();
                        FontAwesomeIconView icon = icon(button);
                        assertTrue(icon.getFont().getSize() >= 12, "Action glyphs must remain readable");
                        assertTrue(button.getHeight() > 0 && button.getHeight() <= 36, "Action buttons must stay compact");
                        assertTrue(button.getWidth() > 0 && button.getWidth() <= 48);
                        Color foreground = (Color) icon.getFill();
                        Color background = background(icon);
                        assertTrue(contrast(foreground, background) >= 3,
                                id + " icon must have at least 3:1 contrast in " + theme + " / " + state);
                        button.pseudoClassStateChanged(pseudo, false);
                    }
                }
            }
        });
    }

    private ButtonBase button(String id) {
        return assertInstanceOf(ButtonBase.class, root.lookup("#" + id));
    }

    private static FontAwesomeIconView icon(ButtonBase button) {
        return assertInstanceOf(FontAwesomeIconView.class, button.getGraphic());
    }

    private static Color background(Node node) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof Region region && region.getBackground() != null) {
                var fills = region.getBackground().getFills();
                for (int i = fills.size() - 1; i >= 0; i--) {
                    if (fills.get(i).getFill() instanceof Color color && color.getOpacity() == 1) {
                        return color;
                    }
                }
            }
        }
        throw new AssertionError("Missing icon background");
    }

    private static double contrast(Color a, Color b) {
        double first = luminance(a);
        double second = luminance(b);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }

    private static double channel(double value) {
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
