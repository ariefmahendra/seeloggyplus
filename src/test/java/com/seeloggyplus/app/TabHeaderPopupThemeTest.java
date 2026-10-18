package com.seeloggyplus.app;

import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class TabHeaderPopupThemeTest {
    private Parent root;
    private TabPane tabs;
    private Tab first;

    @Start void start(Stage stage) throws Exception {
        var loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        root = loader.load();
        tabs = (TabPane) loader.getNamespace().get("logTabPane");
        var create = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
        create.setAccessible(true);
        first = (Tab) create.invoke(loader.getController(), new LogSession("application.log", LogSession.SessionType.LOCAL));
        tabs.getTabs().add(first);
        for (int i = 1; i <= 24; i++) tabs.getTabs().add(new Tab("service-" + i + ".log"));
        tabs.setVisible(true); tabs.setManaged(true);
        var empty = (javafx.scene.Node) loader.getNamespace().get("emptyStatePane");
        empty.setVisible(false); empty.setManaged(false);
        stage.setScene(AppTheme.scene(root)); stage.setWidth(900); stage.setHeight(650); stage.show();
    }

    @AfterEach void reset() throws Exception {
        fx(() -> {
            for (var window : java.util.List.copyOf(Window.getWindows())) if (window instanceof ContextMenu menu) menu.hide();
            AppTheme.setTheme(AppTheme.Theme.LIGHT);
        });
    }

    @Test void overflowPopupIsRoundedAndStillSelectsTabs(FxRobot robot) throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            fx(() -> { AppTheme.setTheme(theme); root.applyCss(); root.layout(); });
            var arrow = WaitForAsyncUtils.asyncFx(() -> tabs.lookup(".tab-down-button")).get();
            assertNotNull(arrow);
            robot.clickOn(arrow);
            fx(() -> {
                var menu = shownMenu();
                assertEquals(tabs.getTabs().size(), menu.getItems().size());
                checkAppearance(menu, theme);
                menu.getItems().get(12).fire();
                assertEquals(tabs.getTabs().get(12), tabs.getSelectionModel().getSelectedItem());
                menu.hide();
            });
        }
    }

    @Test void overflowButtonUsesCompactVerticalDotsInEveryTheme() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            fx(() -> {
                AppTheme.setTheme(theme); root.applyCss(); root.layout();
                var button = (Region) tabs.lookup(".tab-down-button");
                var dots = (Region) button.lookup(".arrow");
                assertEquals(28, button.getWidth(), .5);
                assertEquals(28, button.getHeight(), .5);
                assertTrue(dots.getShape() instanceof javafx.scene.shape.SVGPath);
                assertTrue(dots.getHeight() >= dots.getWidth() * 4);
                assertTrue(contrast(RenderedColors.background(dots), RenderedColors.background(button)) >= 3, theme.name());
                button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), true);
                root.applyCss();
                Color hover = RenderedColors.background(button);
                button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("pressed"), true);
                root.applyCss();
                assertNotEquals(hover, RenderedColors.background(button), theme + " overflow pressed feedback");
                button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), false);
                button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("pressed"), false);
                root.applyCss();
                snapshot(button, theme, "overflow-button");
            });
        }
    }

    @Test void tabContextMenuUsesTheSameRoundedAppearance() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            fx(() -> {
                AppTheme.setTheme(theme);
                first.getContextMenu().show(tabs, javafx.geometry.Side.BOTTOM, 0, 0);
            });
            fx(() -> {
                checkAppearance(first.getContextMenu(), theme);
                assertTrue(first.getContextMenu().getItems().stream().anyMatch(item -> "Close Tab".equals(item.getText())));
                first.getContextMenu().hide();
            });
        }
    }

    private void checkAppearance(ContextMenu menu, AppTheme.Theme theme) {
        var popup = (Region) menu.getSkin().getNode();
        assertNotNull(popup);
        popup.applyCss(); popup.layout();
        assertTrue(popup.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius() >= 10);
        assertTrue(popup.getPadding().getLeft() >= 6);
        var item = (Region) popup.lookup(".menu-item");
        item.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), true);
        popup.applyCss();
        assertTrue(item.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius() >= 6);
        var label = (Label) item.lookup(".label");
        assertTrue(contrast((Color) label.getTextFill(), RenderedColors.background(item)) >= 4.5, theme.name());
        snapshot(popup, theme, menu == first.getContextMenu() ? "context" : "overflow");
        item.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), false);
    }

    @Test void contextMenuOutsideMainViewUsesThemeSelectionColors() throws Exception {
        fx(() -> root.getStyleClass().remove("main-view"));
        for (var theme : AppTheme.Theme.values()) {
            fx(() -> {
                AppTheme.setTheme(theme);
                var menu = first.getContextMenu();
                menu.show(tabs, javafx.geometry.Side.BOTTOM, 0, 0);
                var popup = (Region) menu.getSkin().getNode();
                var item = (Region) popup.lookup(".menu-item");
                item.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), true);
                popup.applyCss();
                assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-selection-bg", theme), item.getBackground().getFills().getFirst().getFill());
                var label = (Label) item.lookup(".label");
                assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-selection-text", theme), label.getTextFill());
                assertTrue(contrast((Color)label.getTextFill(), RenderedColors.background(item)) >= 4.5);
                menu.hide();
            });
        }
    }

    @Test void openContextMenuUpdatesSelectionWhenThemeChanges() throws Exception {
        fx(() -> first.getContextMenu().show(tabs, javafx.geometry.Side.BOTTOM, 0, 0));
        for (var theme : AppTheme.Theme.values()) {
            fx(() -> {
                AppTheme.setTheme(theme);
                var popup = (Region)first.getContextMenu().getSkin().getNode();
                var item = (Region)popup.lookup(".menu-item");
                item.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), true);
                popup.applyCss();
                assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-selection-bg", theme), item.getBackground().getFills().getFirst().getFill());
                var label = (Label)item.lookup(".label");
                assertTrue(contrast((Color)label.getTextFill(), RenderedColors.background(item)) >= 4.5);
            });
        }
    }

    private ContextMenu shownMenu() {
        return Window.getWindows().stream().filter(window -> window instanceof ContextMenu && window.isShowing())
                .map(window -> (ContextMenu) window).findFirst().orElseThrow();
    }
    private void fx(Runnable action) throws Exception {
        WaitForAsyncUtils.asyncFx(action).get(); WaitForAsyncUtils.waitForFxEvents();
    }
    private static double contrast(Color a, Color b) {
        return (Math.max(luminance(a), luminance(b)) + .05) / (Math.min(luminance(a), luminance(b)) + .05);
    }
    private static double luminance(Color c) {
        return .2126 * linear(c.getRed()) + .7152 * linear(c.getGreen()) + .0722 * linear(c.getBlue());
    }
    private static double linear(double v) {
        return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
    }
    private void snapshot(Region popup, AppTheme.Theme theme, String kind) {
        try {
            var rendered = popup.snapshot(null, null);
            int width = (int) rendered.getWidth(), height = (int) rendered.getHeight();
            int[] pixels = new int[width * height];
            rendered.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
            var png = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            png.setRGB(0, 0, width, height, pixels, 0, width);
            var path = java.nio.file.Path.of("build", "tab-popup-review", kind + "-" + theme.name().toLowerCase() + ".png");
            java.nio.file.Files.createDirectories(path.getParent());
            javax.imageio.ImageIO.write(png, "png", path.toFile());
        } catch (Exception error) { throw new AssertionError(error); }
    }
}

