package com.seeloggyplus.app;

import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.session.TabSessionManager;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class AdaptiveLogTabsTest {

    private MainController controller;
    private Parent root;
    private TabPane pane;
    private Stage stage;
    private Map<Tab, LogSession> sessions;

    @Start
    @SuppressWarnings("unchecked")
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        root = loader.load();
        controller = loader.getController();
        Field tabPane = MainController.class.getDeclaredField("logTabPane");
        tabPane.setAccessible(true);
        pane = (TabPane) tabPane.get(controller);
        Field map = MainController.class.getDeclaredField("tabSessionManager");
        map.setAccessible(true);
        sessions = ((TabSessionManager) map.get(controller)).sessions();
        stage.setScene(AppTheme.scene(root));
        stage.setWidth(1200);
        stage.setHeight(720);
        stage.show();
    }

    @AfterEach
    void cleanup(FxRobot robot) {
        robot.interact(() -> {
            new ArrayList<>(sessions.values()).forEach(LogSession::close);
            sessions.clear();
            AppTheme.setTheme(AppTheme.Theme.GRAPHITE);
        });
    }

    @Test
    void headersShrinkProportionallyAsMoreTabsAreOpened(FxRobot robot) {
        robot.interact(() -> {
            addTabs(2);
            layout();
            double twoTabs = header(0).getWidth();
            addTabs(4);
            layout();
            double sixTabs = header(0).getWidth();
            assertTrue(sixTabs < twoTabs * 0.85, "Opening more tabs should visibly reduce header widths");
            assertEqualWidthsAndFit();

            addTabs(2);
            layout();
            assertTrue(header(0).getWidth() < sixTabs, "Widths should continue adjusting as the count grows");
            assertEqualWidthsAndFit();
        });
    }

    @Test
    void closingTabsMakesTheRemainingHeadersWider(FxRobot robot) {
        robot.interact(() -> {
            addTabs(8);
            layout();
            double before = header(0).getWidth();
            List<Tab> closing = new ArrayList<>(pane.getTabs().subList(4, 8));
            for (Tab tab : closing) {
                invoke("closeSession", new Class<?>[]{LogSession.class}, sessions.get(tab));
            }
            layout();
            assertTrue(header(0).getWidth() > before * 1.25, "Closing tabs should recover useful label space");
            assertEqualWidthsAndFit();
        });
    }

    @Test
    void resizingTheWindowRecalculatesHeaderWidths(FxRobot robot) throws Exception {
        AtomicReference<Double> originalPaneWidth = new AtomicReference<>();
        AtomicReference<Double> originalTabWidth = new AtomicReference<>();
        robot.interact(() -> {
            addTabs(6);
            layout();
            originalPaneWidth.set(pane.getWidth());
            originalTabWidth.set(header(0).getWidth());
            stage.setWidth(850);
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() -> {
            layout();
            return pane.getWidth() < originalPaneWidth.get() - 100;
        }).get());
        robot.interact(() -> {
            layout();
            assertTrue(header(0).getWidth() < originalTabWidth.get(), "Resize should update headers without reopening tabs");
            assertEqualWidthsAndFit();
        });
    }

    @Test
    void crowdedTabsRetainCloseButtonsAndFullNamesForNavigation(FxRobot robot) {
        robot.interact(() -> {
            addTabs(30);
            pane.getSelectionModel().selectLast();
            layout();
            assertEquals(TabPane.TabDragPolicy.REORDER, pane.getTabDragPolicy());
            assertEquals(TabPane.TabClosingPolicy.ALL_TABS, pane.getTabClosingPolicy());
            for (int i = 0; i < pane.getTabs().size(); i++) {
                Region header = header(i);
                assertTrue(header.getWidth() >= 72, "Crowded headers need a readable minimum size");
                assertTrue(header.getWidth() <= 110, "Many tabs should reach a compact width instead of staying wide");
                Node close = header.lookup(".tab-close-button");
                assertNotNull(close);
                assertTrue(close.isVisible());
                // A shaped Region's ink bounds are smaller than its allocated button area.
                assertTrue(close.getLayoutBounds().getWidth() >= 12, "The close target must retain its allocated size");
                assertTrue(close.localToScene(close.getBoundsInLocal()).getMaxX()
                        <= header.localToScene(header.getBoundsInLocal()).getMaxX() + 1,
                        "The close button must remain inside the compressed header");
                Tab tab = pane.getTabs().get(i);
                assertEquals(sessions.get(tab).getTitle(), tab.getText(), "Overflow navigation must keep the full title");
                assertEquals(sessions.get(tab).getRemotePath(), tab.getTooltip().getText());
            }
            Region selected = header(pane.getTabs().size() - 1);
            assertTrue(selected.localToScene(selected.getBoundsInLocal()).getMaxX()
                    <= pane.localToScene(pane.getBoundsInLocal()).getMaxX() + 1,
                    "The selected tab must remain reachable when the native overflow menu is needed");
        });
    }

    @Test
    void compressedLabelsKeepFontSizeEllipsisAndContrastInEveryTheme(FxRobot robot) {
        robot.interact(() -> {
            addTabs(8);
            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                layout();
                for (int i = 0; i < pane.getTabs().size(); i++) {
                    Label label = (Label) header(i).lookup(".tab-label");
                    assertNotNull(label);
                    assertEquals(OverrunStyle.ELLIPSIS, label.getTextOverrun());
                    assertTrue(label.getFont().getSize() >= 12, "Reduce header width, not the text's readable size");
                    assertTrue(contrast((Color) label.getTextFill(), background(label)) >= 4.5,
                            "Tab labels must meet 4.5:1 text contrast in " + theme);
                }
            }
        });
    }

    private void addTabs(int count) {
        int offset = pane.getTabs().size();
        for (int i = offset; i < offset + count; i++) {
            LogSession session = new LogSession("production-application-service-long-log-file-" + i + ".log",
                    LogSession.SessionType.REMOTE);
            session.setRemotePath("/var/log/" + session.getTitle());
            Tab tab = (Tab) invoke("createTabForSession", new Class<?>[]{LogSession.class}, session);
            tab.setId("adaptive-log-tab-" + i);
            sessions.put(tab, session);
            pane.getTabs().add(tab);
        }
    }

    private Region header(int index) {
        return assertInstanceOf(Region.class, pane.lookup("#adaptive-log-tab-" + index));
    }

    private void layout() {
        root.applyCss();
        root.layout();
        pane.applyCss();
        pane.layout();
    }

    private void assertEqualWidthsAndFit() {
        double width = header(0).getWidth();
        double total = 0;
        for (int i = 0; i < pane.getTabs().size(); i++) {
            assertEquals(width, header(i).getWidth(), 1, "Available space should be shared proportionally");
            total += header(i).getWidth();
        }
        assertTrue(total < pane.getWidth(), "Tabs should fit before the readable minimum requires overflow");
    }

    private Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = MainController.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(controller, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
        throw new AssertionError("Missing tab label background");
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
