package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class StoreMenuPresentationTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @Test
    void storeUpdateExplanationFitsAndUsesReadableThemeColors() throws Exception {
        String previous = System.getProperty("seeloggyplus.distribution");
        System.setProperty("seeloggyplus.distribution", "store");
        try {
            WaitForAsyncUtils.asyncFx(() -> {
                for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                    try {
                        AppTheme.setTheme(theme);
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
                        Parent root = loader.load();
                        AppTheme.setTheme(theme);
                        stage.setScene(AppTheme.scene(root));
                        stage.show();
                        root.applyCss();
                        root.layout();
                        MenuBar bar = (MenuBar) loader.getNamespace().get("menuBar");
                        var help = bar.getMenus().stream().filter(menu -> menu.getText().equals("_Help")).findFirst().orElseThrow();
                        help.show();
                        MenuItem item = (MenuItem) loader.getNamespace().get("storeMenuItem");
                        var node = item.getStyleableNode();
                        assertNotNull(node);
                        node.applyCss();
                        var popupRoot = node.getScene().getRoot();
                        popupRoot.applyCss();
                        popupRoot.layout();
                        Label label = (Label) node.lookup(".label");
                        assertNotNull(label);
                        assertTrue(label.getWidth() >= label.prefWidth(-1) - 1, theme + ": Store update explanation is clipped");
                        assertTrue(node.getLayoutBounds().getHeight() >= 20, theme + ": menu item is too short");
                        Color background = null;
                        for (var ancestor = node.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
                            if (ancestor instanceof Region region && region.getBackground() != null) {
                                for (var fill : region.getBackground().getFills()) {
                                    if (fill.getFill() instanceof Color color && color.getOpacity() == 1) background = color;
                                }
                            }
                            if (background != null) break;
                        }
                        assertNotNull(background, "Menu must have a painted opaque background");
                        Color text = (Color) label.getTextFill();
                        double lighter = Math.max(luminance(text), luminance(background));
                        double darker = Math.min(luminance(text), luminance(background));
                        assertTrue((lighter + 0.05) / (darker + 0.05) >= 4.5, theme + ": menu text contrast is too low");
                        help.hide();
                        stage.hide();
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }
            }).get();
        } finally {
            WaitForAsyncUtils.asyncFx(() -> {
                stage.hide();
                AppTheme.setTheme(AppTheme.Theme.LIGHT);
            }).get();
            if (previous == null) System.clearProperty("seeloggyplus.distribution");
            else System.setProperty("seeloggyplus.distribution", previous);
        }
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(double component) {
        return component <= 0.04045 ? component / 12.92 : Math.pow((component + 0.055) / 1.055, 2.4);
    }
}
