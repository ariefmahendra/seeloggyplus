package com.seeloggyplus.app;

import com.seeloggyplus.features.files.presentation.UnifiedFileManagerDialogController;
import com.seeloggyplus.features.settings.infrastructure.PreferenceServiceImpl;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
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
class FileManagerCompactLayoutTest {
    private Stage stage;
    private Parent root;
    private UnifiedFileManagerDialogController controller;
    private final PreferenceServiceImpl preferences = new PreferenceServiceImpl();
    @Start void start(Stage stage) throws Exception {
        this.stage = stage;
        preferences.saveOrUpdatePreferences(new Preference("file_manager_visible_columns", ""));
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
        root = loader.load(); controller = loader.getController();
        stage.setScene(AppTheme.scene(root)); stage.setX(0); stage.setY(0); stage.show();
        var field = UnifiedFileManagerDialogController.class.getDeclaredField("allFiles");
        field.setAccessible(true);
        var files = (javafx.collections.ObservableList<com.seeloggyplus.shared.model.FileInfo>) field.get(controller);
        files.setAll(
                new com.seeloggyplus.shared.model.FileInfo("..", "C:/Users/demo", 0, true, 0, com.seeloggyplus.shared.model.FileInfo.SourceType.LOCAL),
                new com.seeloggyplus.shared.model.FileInfo("archive", "C:/Users/demo/Downloads/archive", 0, true, 1729160000000L, com.seeloggyplus.shared.model.FileInfo.SourceType.LOCAL),
                new com.seeloggyplus.shared.model.FileInfo("application-production-access-log-with-a-long-file-name.log", "C:/Users/demo/Downloads/application.log", 327680, false, 1729160000000L, com.seeloggyplus.shared.model.FileInfo.SourceType.LOCAL),
                new com.seeloggyplus.shared.model.FileInfo("config.json", "C:/Users/demo/Downloads/config.json", 2048, false, 1729160000000L, com.seeloggyplus.shared.model.FileInfo.SourceType.LOCAL));
        for (int index = 0; index < 40; index++) {
            files.add(new com.seeloggyplus.shared.model.FileInfo(String.format("service-%02d.log", index),
                    "C:/Users/demo/Downloads/service-" + index + ".log", 8192L * (index + 1), false,
                    1729160000000L, com.seeloggyplus.shared.model.FileInfo.SourceType.LOCAL));
        }
        ((TextField) loader.getNamespace().get("pathField")).setText("C:\\Users\\demo\\Downloads");
        ((Label) root.lookup("#itemCountLabel")).setText(files.size() + " items");
    }
    @AfterEach void reset() throws Exception {
        fx(() -> {
            preferences.saveOrUpdatePreferences(new Preference("file_manager_visible_columns", ""));
            AppTheme.setTheme(AppTheme.Theme.LIGHT);
        });
    }
    @Test void compactLayoutFitsAndSidebarKeepsItsWidth() throws Exception {
        var sidebarWidth = new java.util.concurrent.atomic.AtomicReference<Double>();
        for (var theme : AppTheme.Theme.values()) {
            for (double[] size : new double[][] {{800,520},{1000,640},{1600,900}}) {
                fx(() -> { AppTheme.setTheme(theme); stage.setWidth(size[0]); stage.setHeight(size[1]); });
                fx(() -> {
                    root.applyCss(); root.layout();
                    var toolbar = (Region) root.lookup("#pathToolbar");
                    assertTrue(toolbar.getHeight() <= 40, "toolbar height " + toolbar.getHeight());
                    var breadcrumb = (Region) root.lookup(".file-path-breadcrumb");
                    assertTrue(breadcrumb.getWidth() >= 180, "breadcrumb width " + breadcrumb.getWidth());
                    var sidebar = (Region) root.lookup("#locationsContainer");
                    assertTrue(sidebar.getWidth() >= 160 && sidebar.getWidth() <= 320);
                    if (sidebarWidth.get() == null) sidebarWidth.set(sidebar.getWidth());
                    else assertEquals(sidebarWidth.get(), sidebar.getWidth(), 1, "sidebar must not grow with the window");
                    var sidebarBg = RenderedColors.background(sidebar);
                    assertEquals(sidebarBg, RenderedColors.background(toolbar));
                    for (var node : root.lookup("#locationTree").lookupAll(".tree-cell")) {
                        var cell = (TreeCell<?>) node;
                        if (cell.isEmpty()) continue;
                        var fill = cell.getBackground().getFills().getFirst();
                        assertTrue(fill.getInsets().getTop() >= 2 && fill.getInsets().getBottom() >= 2);
                    }
                    for (var node : breadcrumb.lookupAll(".path-segment")) {
                        var segment = (Button) node;
                        assertTrue(contrast((Color) segment.getTextFill(), RenderedColors.background(segment)) >= 4.5);
                    }
                    for (String id : new String[] {"backButton","forwardButton","upButton","homeButton","refreshButton","findInFilesButton"}) {
                        var button = (Button) root.lookup("#" + id);
                        assertTrue(button.getText() == null || button.getText().isBlank());
                        assertNotNull(button.getTooltip());
                        assertTrue(button.getWidth() <= 32);
                    }
                    var footer = (Region) root.lookup(".file-manager-footer");
                    assertNotNull(footer);
                    assertTrue(footer.getHeight() <= 44);
                    double lastY = -1;
                    for (String id : new String[] {"cancelButton","previewButton","tailButton","openButton"}) {
                        var button = (Button) root.lookup("#" + id);
                        var bounds = button.localToScene(button.getBoundsInLocal());
                        assertTrue(bounds.getMaxX() <= stage.getScene().getWidth());
                        assertTrue(bounds.getMaxY() <= stage.getScene().getHeight());
                        if (lastY >= 0) assertEquals(lastY, bounds.getMinY(), 0.1);
                        lastY = bounds.getMinY();
                    }
                    var table = (TableView<?>) root.lookup("#fileTable");
                    assertEquals(28, table.getFixedCellSize());
                    assertTrue(table.isTableMenuButtonVisible());
                    assertEquals(java.util.List.of("nameColumn","sizeColumn","modifiedColumn"),
                            table.getVisibleLeafColumns().stream().map(TableColumn::getId).toList());
                    assertTrue(table.getVisibleLeafColumns().stream().mapToDouble(TableColumn::getWidth).sum() <= table.getWidth());
                    boolean foundLongName = false;
                    for (var node : table.lookupAll(".table-cell")) {
                        var cell = (TableCell<?, ?>) node;
                        if (cell.getTableColumn() != null && "nameColumn".equals(cell.getTableColumn().getId())
                                && cell.getItem() instanceof String name && name.startsWith("application-production")) {
                            foundLongName = true;
                            assertNotNull(cell.getGraphic());
                            assertEquals(name, cell.getTooltip().getText());
                        }
                    }
                    assertTrue(foundLongName);
                    for (var node : table.lookupAll(".scroll-bar")) {
                        var bar = (ScrollBar) node;
                        if (bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL) assertFalse(bar.isVisible(), "default columns must fit without horizontal scrolling");
                    }
                    if (size[0] == 1000) snapshot(theme);
                    var status = (Label) root.lookup("#statusLabel");
                    assertTrue(contrast((Color) status.getTextFill(), RenderedColors.background(status)) >= 4.5);
                    var progress = (ProgressIndicator) root.lookup("#progressIndicator");
                    progress.setVisible(true); assertTrue(progress.isManaged());
                    progress.setVisible(false); assertFalse(progress.isManaged());
                });
            }
        }
    }
    @Test void loadingIndicatorStaysCompactAndHasNoInputFrameInEitherTheme() throws Exception {
        fx(() -> {
            var progress = (ProgressIndicator) root.lookup("#progressIndicator");
            progress.setVisible(true);
            for (var theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                root.applyCss(); root.layout();
                assertEquals(16, progress.getWidth(), .01);
                assertEquals(16, progress.getHeight(), .01);
                assertTrue(progress.isIndeterminate());
                assertTrue(progress.isManaged());
                Region spinner = (Region) progress.lookup(".spinner");
                assertNotNull(spinner);
                assertTrue(spinner.getBackground() == null || spinner.getBackground().getFills().stream()
                        .allMatch(fill -> ((Color) fill.getFill()).getOpacity() == 0), "Loading must not inherit an input background");
                assertTrue(spinner.getBorder() == null || spinner.getBorder().getStrokes().stream()
                        .allMatch(stroke -> stroke.getWidths().getTop() == 0), "Loading must not inherit an input border");
                var segments = progress.lookupAll(".segment");
                assertFalse(segments.isEmpty());
                for (var node : segments) {
                    Color fill = (Color) ((Region) node).getBackground().getFills().getFirst().getFill();
                    assertEquals(Color.web("#3871e1"), fill);
                    assertTrue(contrast(fill, RenderedColors.background(progress)) >= 3);
                }
            }
            progress.setVisible(false);
            assertFalse(progress.isManaged());
        });
    }

    @Test void breadcrumbArrowsStayCenteredInBothThemes() throws Exception {
        for (var theme : AppTheme.Theme.values()) fx(() -> {
            AppTheme.setTheme(theme);
            root.applyCss(); root.layout();
            var arrows = root.lookupAll(".path-children");
            assertFalse(arrows.isEmpty());
            for (var node : arrows) {
                var button = (MenuButton) node;
                if (!button.isVisible()) continue;
                for (double angle : new double[] {0, 90}) {
                    button.getGraphic().setRotate(angle);
                    var bounds = button.sceneToLocal(button.getGraphic().localToScene(button.getGraphic().getBoundsInLocal()));
                    assertEquals(button.getWidth() / 2, bounds.getCenterX(), .6, "horizontal center");
                    assertEquals(button.getHeight() / 2, bounds.getCenterY(), .6, "vertical center");
                }
                button.getGraphic().setRotate(0);
            }
        });
    }

    @Test void columnVisibilitySurvivesReopening() throws Exception {
        fx(() -> {
            controller.setPreferenceService(preferences);
            var table = (TableView<?>) root.lookup("#fileTable");
            table.getColumns().stream().filter(c -> c.getId().equals("typeColumn")).findFirst().orElseThrow().setVisible(true);
            assertTrue(preferences.getPreferencesByCode("file_manager_visible_columns").orElseThrow().contains("typeColumn"));
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
                Parent reopened = loader.load();
                ((UnifiedFileManagerDialogController) loader.getController()).setPreferenceService(preferences);
                var restored = (TableView<?>) loader.getNamespace().get("fileTable");
                assertTrue(restored.getColumns().stream().filter(c -> c.getId().equals("typeColumn")).findFirst().orElseThrow().isVisible());
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }
    @Test void fileManagerOpensAsResizableNormalWindow() throws Exception {
        fx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
                Parent mainRoot = loader.load();
                stage.setScene(AppTheme.scene(mainRoot));
                var seen = new java.util.concurrent.atomic.AtomicBoolean();
                var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
                javafx.collections.ListChangeListener<javafx.stage.Window> listener = change -> {
                    while (change.next()) {
                        for (var window : change.getAddedSubList()) {
                            if (window instanceof Stage dialog && "Open File".equals(dialog.getTitle())) {
                                seen.set(true);
                                javafx.application.Platform.runLater(() -> {
                                    try {
                                        assertFalse(dialog.isMaximized());
                                        assertTrue(dialog.isResizable());
                                        assertEquals(1000, dialog.getWidth(), 1);
                                        assertEquals(640, dialog.getHeight(), 1);
                                        assertEquals(800, dialog.getMinWidth(), 1);
                                        assertEquals(520, dialog.getMinHeight(), 1);
                                    } catch (Throwable error) { failure.set(error); }
                                    finally { dialog.hide(); }
                                });
                            }
                        }
                    }
                };
                javafx.stage.Window.getWindows().addListener(listener);
                try {
                    ((MainController) loader.getController()).handleOpen();
                    assertTrue(seen.get());
                    if (failure.get() != null) throw new AssertionError(failure.get());
                } finally { javafx.stage.Window.getWindows().removeListener(listener); }
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    private void snapshot(AppTheme.Theme theme) {
        try {
            var image = root.snapshot(null, null);
            int width = (int) image.getWidth(), height = (int) image.getHeight();
            int[] pixels = new int[width * height];
            image.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
            var png = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            png.setRGB(0, 0, width, height, pixels, 0, width);
            var path = java.nio.file.Path.of("build", "test-snapshots", "file-manager-compact-" + theme.name().toLowerCase() + ".png");
            java.nio.file.Files.createDirectories(path.getParent());
            javax.imageio.ImageIO.write(png, "png", path.toFile());
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(); WaitForAsyncUtils.waitForFxEvents(); }
    private static double contrast(Color a, Color b) { return (Math.max(luminance(a),luminance(b))+0.05)/(Math.min(luminance(a),luminance(b))+0.05); }
    private static double luminance(Color c) { return .2126*linear(c.getRed())+.7152*linear(c.getGreen())+.0722*linear(c.getBlue()); }
    private static double linear(double v) { return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4); }
}
