package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class MainViewIslandLayoutTest {
    private Parent root;
    private Stage stage;
    private MainController controller;

    @Start void start(Stage stage) throws Exception {
        this.stage = stage;
        stage.setX(40); stage.setY(40);
        var loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.setWidth(1024); stage.setHeight(700); stage.show();
        var reset=MainController.class.getDeclaredMethod("resetWorkbenchLayout"); reset.setAccessible(true); reset.invoke(controller);
        var position=MainController.class.getDeclaredMethod("setResultsPosition",String.class); position.setAccessible(true); position.invoke(controller,"right");
        for (String id : new String[] {"toggleLeftPanelButton", "toggleBottomPanelButton"}) {
            var button = (ToggleButton) root.lookup("#" + id);
            if (!button.isSelected()) button.fire();
        }
    }

    @AfterEach void reset() throws Exception {
        fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT));
    }

    @Test void roundedCornersRevealWorkspaceAtEverySizeAndTheme() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            for (double[] size : new double[][] {{600,400},{1024,700},{1600,900}}) {
                fx(() -> {
                    AppTheme.setTheme(theme);
                    stage.setWidth(size[0]); stage.setHeight(size[1]);
                });
                fx(() -> {
                    root.applyCss(); root.layout();
                    var workspace = (Region) root.lookup("#centerRoot");
                    Color backdrop = RenderedColors.background(workspace);
                    assertEquals(4, workspace.getPadding().getLeft());
                    for (String id : new String[] {"leftPanel", "logContainer", "bottomPanel"}) {
                        var panel = (Region) root.lookup("#" + id);
                        assertNotNull(panel, id);
                        var clip = assertInstanceOf(Rectangle.class, panel.getClip(), id);
                        assertEquals(8, clip.getArcWidth());
                        assertEquals(8, clip.getArcHeight());
                        assertEquals(panel.getWidth(), clip.getWidth(), .1);
                        assertEquals(panel.getHeight(), clip.getHeight(), .1);
                        assertNotEquals(backdrop, RenderedColors.background(panel));
                        var point = root.sceneToLocal(panel.localToScene(0, 0));
                        var pixels = root.snapshot(null, null).getPixelReader();
                        assertEquals(backdrop, pixels.getColor((int) point.getX(), (int) point.getY()), id + " clipped corner");
                    }
                    assertTrue(region("logIsland").getHeight() >= 120);
                    assertTrue(region("bottomPanel").getHeight() >= 80);
                    assertTrue(region("leftPanel").getWidth() >= 200);
                    if (size[0] == 1024) snapshot(theme);
                });
            }
        }
    }

    @Test void draggingDetailDividerToTopKeepsTheEntireEmptyStateVisible(FxRobot robot) throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            for (double[] size : new double[][]{{600,400},{1024,700}}) {
                fx(() -> {
                    AppTheme.setTheme(theme);
                    stage.setWidth(size[0]); stage.setHeight(size[1]);
                    ((SplitPane) root.lookup("#verticalSplitPane")).setDividerPositions(.7);
                });
                var divider = WaitForAsyncUtils.asyncFx(() -> {
                    var split = root.lookup("#verticalSplitPane");
                    return split.lookupAll(".split-pane-divider").stream()
                            .filter(node -> node.getParent() == split).findFirst().orElseThrow();
                }).get();
                robot.moveTo(divider).press(MouseButton.PRIMARY).moveBy(0, -1000).release(MouseButton.PRIMARY);
                fx(() -> {
                    root.applyCss(); root.layout();
                    var pane = region("emptyStatePane");
                    var container = region("logIsland");
                    var visibleBounds = container.localToScene(container.getBoundsInLocal());
                    for (String selector : new String[]{".empty-state-icon", ".empty-state-title",
                            ".empty-state-subtitle", ".empty-state-button"}) {
                        var node = pane.lookup(selector);
                        var bounds = node.localToScene(node.getBoundsInLocal());
                        assertTrue(bounds.getMinY() >= visibleBounds.getMinY(), theme + " top " + selector);
                        assertTrue(bounds.getMaxY() <= visibleBounds.getMaxY(), theme + " bottom " + selector);
                    }
                });
            }
        }
        fx(() -> {
            var tabs = (javafx.scene.control.TabPane) root.lookup("#logTabPane");
            tabs.getTabs().add(new javafx.scene.control.Tab("Test", new javafx.scene.layout.StackPane()));
            root.applyCss(); root.layout();
            assertFalse(region("emptyStatePane").isManaged());
            assertEquals(120, region("logIsland").getMinHeight(), .01,
                    "An open log must retain the compact divider minimum");
        });
    }

    @Test void resultsAndLogViewerHaveIndependentRoundedCornersAndResizableGap(FxRobot robot) throws Exception {
        var resultField = MainController.class.getDeclaredField("searchResultPanel");
        resultField.setAccessible(true);
        var results = (com.seeloggyplus.features.search.presentation.SearchResultPanel) resultField.get(controller);
        var splitField = MainController.class.getDeclaredField("searchSplitPane");
        splitField.setAccessible(true);
        var searchSplit = (SplitPane) splitField.get(controller);
        var matches = new com.seeloggyplus.shared.util.IntArrayList();
        matches.add(0); matches.add(1);
        var show = MainController.class.getDeclaredMethod("showSearchResultPanel", com.seeloggyplus.shared.util.IntArrayList.class, java.util.regex.Pattern.class);
        show.setAccessible(true);
        var hide = MainController.class.getDeclaredMethod("hideSearchResultPanel");
        hide.setAccessible(true);
        for (var theme : AppTheme.Theme.values()) {
            for (double[] size : new double[][] {{600,400},{1024,700},{1600,900}}) {
                fx(() -> {
                    AppTheme.setTheme(theme); stage.setWidth(size[0]); stage.setHeight(size[1]);
                    invoke(show, matches, null);
                });
                fx(() -> {
                    root.applyCss(); root.layout();
                    var backdrop = RenderedColors.background(region("centerRoot"));
                    assertNull(region("logIsland").getClip());
                    assertEquals(backdrop, RenderedColors.background(region("logIsland")));
                    var pixels = root.snapshot(null, null).getPixelReader();
                    for (Region panel : new Region[]{region("logContainer"), results}) {
                        var clip = assertInstanceOf(Rectangle.class, panel.getClip());
                        assertEquals(8, clip.getArcWidth());
                        assertEquals(panel.getWidth(), clip.getWidth(), .1);
                        assertEquals(panel.getHeight(), clip.getHeight(), .1);
                        assertTrue(panel.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius() >= 4);
                        assertNotEquals(backdrop, RenderedColors.background(panel));
                        for (double x : new double[]{0,panel.getWidth()-1}) {
                            for (double y : new double[]{0,panel.getHeight()-1}) {
                                var point = root.sceneToLocal(panel.localToScene(x,y));
                                var corner=pixels.getColor((int)point.getX(), (int)point.getY());
                                assertTrue(Math.abs(Math.round(backdrop.getRed()*255)-Math.round(corner.getRed()*255))<=1,theme+" corner red raster rounding");
                                assertTrue(Math.abs(Math.round(backdrop.getGreen()*255)-Math.round(corner.getGreen()*255))<=1,theme+" corner green raster rounding");
                                assertTrue(Math.abs(Math.round(backdrop.getBlue()*255)-Math.round(corner.getBlue()*255))<=1,theme+" corner blue raster rounding");
                            }
                        }
                    }
                    var divider = (Region) searchSplit.lookup(".split-pane-divider");
                    assertEquals(6, divider.getWidth(), .1);
                    var gapPoint = root.sceneToLocal(divider.localToScene(1, divider.getHeight()/2));
                    assertEquals(backdrop, pixels.getColor((int)gapPoint.getX(), (int)gapPoint.getY()));
                    var header = (javafx.scene.control.Label) results.lookup(".search-results-header");
                    double fg = luminance((Color) header.getTextFill());
                    double bg = luminance(RenderedColors.background(header));
                    assertTrue((Math.max(fg,bg)+.05)/(Math.min(fg,bg)+.05) >= 4.5, theme + " result header contrast");
                    assertTrue(results.getWidth() >= 120);
                    if (size[0] == 1024) snapshot(theme);
                });
                fx(() -> invoke(hide));
                fx(() -> {
                    root.applyCss(); root.layout();
                    assertEquals(region("logIsland").getWidth(), region("logContainer").getWidth(), .1);
                    assertNotNull(region("logContainer").getClip());
                });
            }
        }
        fx(() -> invoke(show, matches, null));
        var divider = WaitForAsyncUtils.asyncFx(() -> searchSplit.lookup(".split-pane-divider")).get();
        double before = WaitForAsyncUtils.asyncFx(results::getWidth).get();
        robot.moveTo(divider).press(MouseButton.PRIMARY).moveBy(-70,0).release(MouseButton.PRIMARY);
        fx(() -> assertTrue(results.getWidth() > before + 20));
        robot.moveTo(divider).press(MouseButton.PRIMARY).moveBy(2000,0).release(MouseButton.PRIMARY);
        fx(() -> assertTrue(results.getWidth() >= 120));
    }

    private void invoke(java.lang.reflect.Method method, Object... args) {
        try { method.invoke(controller, args); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private double luminance(Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
    }

    private double linear(double value) {
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    @Test void draggingDetailDividerResizesBothPanelsAndRespectsMinimums(FxRobot robot) throws Exception {
        fx(() -> { root.applyCss(); root.layout(); });
        double before = WaitForAsyncUtils.asyncFx(() -> region("logIsland").getHeight()).get();
        var divider = WaitForAsyncUtils.asyncFx(() -> split("verticalSplitPane").lookupAll(".split-pane-divider").stream()
                .filter(node -> node.getParent() == split("verticalSplitPane") && node.isVisible()).findFirst().orElseThrow()).get();
        robot.moveTo(divider).press(MouseButton.PRIMARY).moveBy(0, -70).release(MouseButton.PRIMARY);
        fx(() -> {
            assertTrue(region("logIsland").getHeight() < before - 20);
            assertTrue(region("bottomPanel").getHeight() >= 80);
            assertEquals(6, divider.getBoundsInLocal().getHeight(), .1,
                    "divider=" + divider + " parent=" + divider.getParent() + " layout=" + divider.getLayoutBounds()
                            + " padding=" + ((Region) divider).getPadding() + " pseudo=" + split("verticalSplitPane").getPseudoClassStates()
                            + " current=" + split("verticalSplitPane").lookupAll(".split-pane-divider"));
        });
        robot.moveTo(divider).press(MouseButton.PRIMARY).moveBy(0, -1000).release(MouseButton.PRIMARY);
        fx(() -> assertTrue(region("logIsland").getHeight() >= 120));
        robot.moveTo(divider).press(MouseButton.PRIMARY).moveBy(0, 1000).release(MouseButton.PRIMARY);
        fx(() -> assertTrue(region("bottomPanel").getHeight() >= 80));
    }

    @Test void hiddenPanelsReleaseSpaceAndRestoreDividerPositions() throws Exception {
        for (String[] ids : new String[][] {{"verticalSplitPane","toggleBottomPanelButton"},{"horizontalSplitPane","toggleLeftPanelButton"}}) {
            fx(() -> split(ids[0]).setDividerPositions(.55));
            double position = WaitForAsyncUtils.asyncFx(() -> split(ids[0]).getDividerPositions()[0]).get();
            fx(() -> ((ToggleButton) root.lookup("#" + ids[1])).fire());
            fx(() -> {
                assertEquals(1, split(ids[0]).getItems().size());
                assertTrue(split(ids[0]).lookupAll(".split-pane-divider").stream()
                        .filter(node -> node.getParent() == split(ids[0])).noneMatch(javafx.scene.Node::isVisible));
            });
            fx(() -> ((ToggleButton) root.lookup("#" + ids[1])).fire());
            fx(() -> {
                assertEquals(2, split(ids[0]).getItems().size());
                assertEquals(position, split(ids[0]).getDividerPositions()[0], .02);
            });
        }
    }

    private Region region(String id) { return (Region) root.lookup("#" + id); }
    private SplitPane split(String id) { return (SplitPane) root.lookup("#" + id); }
    private void fx(Runnable action) throws Exception {
        WaitForAsyncUtils.asyncFx(action).get();
        WaitForAsyncUtils.waitForFxEvents();
    }
    private void snapshot(AppTheme.Theme theme) {
        try {
            var rendered = root.snapshot(null, null);
            int width = (int) rendered.getWidth(), height = (int) rendered.getHeight();
            int[] pixels = new int[width * height];
            rendered.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
            var png = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            png.setRGB(0, 0, width, height, pixels, 0, width);
            var path = java.nio.file.Path.of("build", "islands-rounded", "main-" + theme.name().toLowerCase() + ".png");
            java.nio.file.Files.createDirectories(path.getParent());
            javax.imageio.ImageIO.write(png, "png", path.toFile());
        } catch (Exception error) { throw new AssertionError(error); }
    }
}
