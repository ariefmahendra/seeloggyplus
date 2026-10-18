package com.seeloggyplus.app;

import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class LogTabPillThemeTest {
    private Parent root;
    private TabPane tabs;
    private Tab local;
    private Tab remote;

    @Start void start(Stage stage) throws Exception {
        var loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        root = loader.load();
        var create = MainController.class.getDeclaredMethod("createTabForSession", LogSession.class);
        create.setAccessible(true);
        local = (Tab) create.invoke(loader.getController(), new LogSession("application-production-long-file-name.log", LogSession.SessionType.LOCAL));
        var live = new LogSession("remote.log", LogSession.SessionType.REMOTE);
        live.setTailModeEnabled(true);
        remote = (Tab) create.invoke(loader.getController(), live);
        tabs = (TabPane) loader.getNamespace().get("logTabPane");
        tabs.getTabs().setAll(local, remote);
        tabs.setVisible(true); tabs.setManaged(true);
        var empty = (javafx.scene.Node) loader.getNamespace().get("emptyStatePane");
        empty.setVisible(false);
        empty.setManaged(false);
        stage.setScene(AppTheme.scene(root));
        stage.setWidth(1024); stage.setHeight(700); stage.show();
    }

    @AfterEach void reset() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)).get();
    }

    @Test void tabsHaveRoundedFramesAndReadableLabelsInEveryTheme() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            WaitForAsyncUtils.asyncFx(() -> {
                AppTheme.setTheme(theme);
                root.applyCss(); root.layout();
                var header = (Region) tabs.lookup(".tab-header-area");
                assertTrue(header.getPadding().getTop() >= 4);
                assertTrue(header.getHeight() <= 44);
                for (var node : tabs.lookupAll(".tab")) {
                    var pill = (Region) node;
                    var fill = pill.getBackground().getFills().getFirst();
                    assertTrue(fill.getRadii().getTopLeftHorizontalRadius() >= 8);
                    var border = pill.getBorder().getStrokes().getFirst();
                    assertEquals(1, border.getWidths().getTop());
                    assertEquals(1, border.getWidths().getBottom());
                    assertTrue(pill.getHeight() >= 26 && pill.getHeight() <= 32);
                    var label = (Label) pill.lookup(".tab-label");
                    assertTrue(contrast((Color) label.getTextFill(), RenderedColors.background(pill)) >= 4.5);
                    var close = (Region) pill.lookup(".tab-close-button");
                    assertTrue(contrast((Color) close.getBackground().getFills().getFirst().getFill(), RenderedColors.background(pill)) >= 3);
                    var icon = (FontAwesomeIconView) pill.lookup(".log-tab-icon");
                    assertTrue(contrast((Color) icon.getFill(), RenderedColors.background(pill)) >= 3);
                    pill.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), true);
                    Color idle = RenderedColors.background(pill);
                    root.applyCss();
                    if (pill.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("selected"))) {
                        Color hover = RenderedColors.background(pill);
                        assertNotEquals(idle, hover, theme + " selected tab hover");
                        pill.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("pressed"), true);
                        root.applyCss();
                        assertNotEquals(hover, RenderedColors.background(pill), theme + " selected tab pressed");
                        pill.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("pressed"), false);
                        root.applyCss();
                    }
                    assertTrue(contrast((Color) label.getTextFill(), RenderedColors.background(pill)) >= 4.5);
                    pill.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), false);
                }
                assertEquals("application-production-long-file-name.log", local.getText());
                assertEquals(TabPane.TabDragPolicy.REORDER, tabs.getTabDragPolicy());
                assertNotNull(local.getTooltip());
            }).get();
        }
    }

    @Test void localAndRemoteTabsKeepIconsAndLiveStatus() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> {
            root.applyCss(); root.layout();
            var fileIcon = assertInstanceOf(FontAwesomeIconView.class, local.getGraphic().lookup(".log-tab-icon"));
            var serverIcon = assertInstanceOf(FontAwesomeIconView.class, remote.getGraphic().lookup(".log-tab-icon"));
            assertEquals("FILE_TEXT", fileIcon.getGlyphName());
            assertEquals("SERVER", serverIcon.getGlyphName());
            assertFalse(local.getGraphic().lookup(".tab-live-indicator").isVisible());
            assertTrue(remote.getGraphic().lookup(".tab-live-indicator").isVisible());
            var iconBounds = serverIcon.localToScene(serverIcon.getBoundsInLocal());
            var dot = remote.getGraphic().lookup(".tab-live-indicator");
            var dotBounds = dot.localToScene(dot.getBoundsInLocal());
            assertTrue(iconBounds.intersects(dotBounds), "live status must overlap its file/server icon");
            assertTrue(dotBounds.getCenterX() >= iconBounds.getCenterX());
            assertTrue(dotBounds.getCenterY() <= iconBounds.getCenterY());
            assertTrue(remote.isClosable());
        }).get();
    }

    @Test void unreadBadgeRemainsReadableInEveryTheme() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            WaitForAsyncUtils.asyncFx(() -> {
                AppTheme.setTheme(theme);
                var badge = (Label) remote.getGraphic().lookup(".tab-badge-unread");
                badge.setText("12"); badge.setVisible(true); badge.setManaged(true);
                root.applyCss(); root.layout();
                assertTrue(contrast((Color) badge.getTextFill(), RenderedColors.background(badge)) >= 4.5, theme.name());
            }).get();
        }
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
}
