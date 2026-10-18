package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
class MainViewCompactLayoutTest {
    private Parent root;
    private Stage stage;

    @Start void start(Stage stage) throws Exception {
        this.stage = stage;
        root = FXMLLoader.load(getClass().getResource("/fxml/MainView.fxml"));
        stage.setScene(AppTheme.scene(root));
        stage.show();
    }

    @AfterEach void reset() throws Exception {
        WaitForAsyncUtils.asyncFx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)).get();
    }

    @Test void chromeLeavesSpaceForLogsAndEmptyStateFits() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            for (double width : new double[] {600, 1024}) {
                WaitForAsyncUtils.asyncFx(() -> {
                    AppTheme.setTheme(theme);
                    stage.getScene().getWindow().setWidth(width);
                    root.applyCss(); root.layout();
                    var toolbar = (Region) root.lookup(".app-toolbar");
                    var canvasBackground = RenderedColors.background(root.lookup(".empty-state-pane"));
                    assertTrue(contrast(RenderedColors.background(toolbar), canvasBackground) <= 1.25,
                            "Toolbar should blend with the log surface in " + theme);
                    var status = (Label) root.lookup("#statusLabel");
                    assertTrue(contrast((Color) status.getTextFill(), RenderedColors.background(status)) >= 4.5);
                    assertTrue(toolbar.getHeight() <= 38, "toolbar height " + toolbar.getHeight());
                    var search = (TextField) root.lookup("#searchField");
                    assertTrue(search.getHeight() <= 28, "search height " + search.getHeight());
                    assertTrue(((Region) root.lookup(".status-bar")).getHeight() <= 28);
                    var subtitle = (Label) root.lookup(".empty-state-subtitle");
                    assertTrue(subtitle.isWrapText());
                    assertTrue(subtitle.getWidth() <= root.lookup("#logContainer").getLayoutBounds().getWidth());
                    var title = (Label) root.lookup(".empty-state-title");
                    assertTrue(title.getFont().getSize() <= 16);
                    assertTrue(contrast((Color) title.getTextFill(), RenderedColors.background(title)) >= 4.5);
                }).get();
            }
        }
    }

    @Test void emptyStateHasBalancedCenteredContentAndCompactAction() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            for (double width : new double[]{600, 1024}) {
                WaitForAsyncUtils.asyncFx(() -> {
                    AppTheme.setTheme(theme);
                    stage.setWidth(width);
                    root.applyCss(); root.layout();
                    var pane = (Region) root.lookup("#emptyStatePane");
                    var content = (Region) pane.lookup(".empty-state-content");
                    assertNotNull(content, "Empty state needs a bounded content group");
                    assertTrue(content.getWidth() <= 320);
                    var contentBounds = content.localToScene(content.getBoundsInLocal());
                    var paneBounds = pane.localToScene(pane.getBoundsInLocal());
                    assertEquals((paneBounds.getMinX() + paneBounds.getMaxX()) / 2,
                            (contentBounds.getMinX() + contentBounds.getMaxX()) / 2, 1);
                    assertEquals((paneBounds.getMinY() + paneBounds.getMaxY()) / 2,
                            (contentBounds.getMinY() + contentBounds.getMaxY()) / 2, 1);
                    var subtitle = (Label) pane.lookup(".empty-state-subtitle");
                    assertEquals(javafx.geometry.Pos.CENTER, subtitle.getAlignment());
                    var button = (javafx.scene.control.Button) pane.lookup(".empty-state-button");
                    assertEquals(28, button.getHeight(), .01);
                    assertTrue(button.getWidth() >= 120);
                    assertTrue(contrast((Color) button.getTextFill(), RenderedColors.background(button)) >= 4.5);
                    var glyph = (javafx.scene.text.Text) pane.lookup(".empty-state-icon");
                    assertTrue(contrast((Color) glyph.getFill(), RenderedColors.background(pane)) >= 3);
                }).get();
            }
        }
    }

    @Test void panelsShareSoftSurfacesWithoutNestedFrames() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            WaitForAsyncUtils.asyncFx(() -> {
                AppTheme.setTheme(theme);
                root.lookup("#bottomPanel").setVisible(true);
                root.lookup("#bottomPanel").setManaged(true);
                root.applyCss(); root.layout();
                var surface = RenderedColors.background(root.lookup(".empty-state-pane"));
                for (String selector : new String[] {"#leftPanel", "#bottomPanel", "#recentFilesTreeView", ".detail-container",
                        "#horizontalSplitPane", "#verticalSplitPane"}) {
                    var panel = (Region) root.lookup(selector);
                    assertNotNull(panel, selector);
                    assertTrue(contrast(surface, RenderedColors.background(panel)) <= 1.25, selector + " surface " + theme);
                    assertTrue(panel.getBorder() == null || panel.getBorder().getStrokes().stream().allMatch(stroke ->
                                    stroke.getWidths().getTop() == 0 && stroke.getWidths().getBottom() == 0
                                            && stroke.getWidths().getLeft() == 0 && stroke.getWidths().getRight() == 0),
                            selector + " must not draw a nested frame");
                }
                for (var node : root.lookupAll(".split-pane-divider")) {
                    var divider = (Region) node;
                    assertTrue(contrast(surface, RenderedColors.background(divider)) <= 1.6);
                    assertTrue(Math.min(divider.getWidth(), divider.getHeight()) <= 6);
                }
                for (var node : root.lookupAll(".panel-title")) {
                    var label = (Label) node;
                    assertTrue(contrast((Color) label.getTextFill(), RenderedColors.background(label)) >= 4.5);
                }
                var filter = (TextField) root.lookup("#recentFilesFilterField");
                assertTrue(filter.getHeight() <= 28);
            }).get();
        }
    }

    private static double contrast(Color a, Color b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }

    @Test void statusHoverKeepsReadableText() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            WaitForAsyncUtils.asyncFx(() -> {
                AppTheme.setTheme(theme);
                var status = (Label) root.lookup("#statusLabel");
                status.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), true);
                root.applyCss();
                assertTrue(status.isUnderline());
                assertTrue(contrast((Color) status.getTextFill(), RenderedColors.background(status)) >= 4.5, theme.name());
                status.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), false);
            }).get();
        }
    }

    @Test void searchInputsShareRestingAndFocusedBorders() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            WaitForAsyncUtils.asyncFx(() -> {
                AppTheme.setTheme(theme);
                var search = (TextField) root.lookup("#searchField");
                var filter = (TextField) root.lookup("#recentFilesFilterField");
                for (boolean focused : new boolean[] {false, true}) {
                    for (var input : new TextField[] {search, filter}) {
                        input.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), focused);
                    }
                    root.applyCss();
                    var a = search.getBorder().getStrokes().getFirst();
                    var b = filter.getBorder().getStrokes().getFirst();
                    assertEquals(b.getTopStroke(), a.getTopStroke(), theme + " focused=" + focused);
                    assertEquals(1, a.getWidths().getTop());
                    assertEquals(b.getRadii(), a.getRadii());
                }
            }).get();
        }
    }

    @Test void activeToolbarButtonsUseSoftButDistinctSurfaces() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            WaitForAsyncUtils.asyncFx(() -> {
                AppTheme.setTheme(theme);
                var button = (javafx.scene.control.ToggleButton) root.lookup("#regexCheckBox");
                button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), false);
                button.setSelected(false);
                root.applyCss();
                Color resting = RenderedColors.background(button);
                button.setSelected(true);
                root.applyCss();
                Color active = RenderedColors.background(button);
                assertNotEquals(resting, active);
                assertTrue(contrast(resting, active) <= (theme == AppTheme.Theme.LIGHT ? 1.25 : 3), "active toolbar surface " + theme);
                assertTrue(contrast((Color) button.getTextFill(), active) >= 4.5);
                button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), true);
                root.applyCss();
                assertNotEquals(active, RenderedColors.background(button), theme + " selected hover must provide feedback");
                button.setSelected(false);
                root.applyCss();
                assertNotEquals(resting, RenderedColors.background(button));
                assertTrue(contrast((Color) button.getTextFill(), RenderedColors.background(button)) >= 4.5);
            }).get();
        }
    }
    private static double luminance(Color c) {
        return .2126 * linear(c.getRed()) + .7152 * linear(c.getGreen()) + .0722 * linear(c.getBlue());
    }
    private static double linear(double v) {
        return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
    }
}
