package com.seeloggyplus.features.servers.presentation;

import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class ServerManagementCompactLayoutTest {
    private Stage stage;
    private Parent root;
    private ServerManagementDialogController controller;
    @Start void start(Stage stage) throws Exception {
        this.stage = stage;
        var loader = new FXMLLoader(getClass().getResource("/fxml/ServerManagementDialog.fxml"));
        root = loader.load(); controller = loader.getController();
        stage.setScene(AppTheme.scene(root)); stage.show();
    }
    @AfterEach void resetTheme() throws Exception { fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)); }
    @Test void dividerRespectsSidebarLimitsAndPreservesUserWidth() throws Exception {
        fx(() -> { stage.setWidth(1000); stage.setHeight(640); });
        fx(() -> {
            var split = (SplitPane) root.lookup("#serverSplitPane");
            split.setDividerPositions(.95); root.layout();
            assertTrue(((Region) root.lookup("#groupSidebar")).getWidth() <= 320);
            split.setDividerPositions(.01); root.layout();
            assertTrue(((Region) root.lookup("#groupSidebar")).getWidth() >= 160);
            split.setDividerPositions(.26); root.layout();
        });
        var width = new java.util.concurrent.atomic.AtomicReference<Double>();
        fx(() -> { width.set(((Region) root.lookup("#groupSidebar")).getWidth()); stage.setWidth(1600); });
        fx(() -> { root.layout(); assertEquals(width.get(), ((Region) root.lookup("#groupSidebar")).getWidth(), 1); });
    }
    @Test void columnVisibilitySurvivesReopening() throws Exception {
        fx(() -> {
            var preferences = new com.seeloggyplus.features.settings.infrastructure.PreferenceServiceImpl();
            preferences.saveOrUpdatePreferences(new com.seeloggyplus.shared.model.Preference("server_management_visible_columns", ""));
            try {
                controller.setPreferenceService(preferences);
                var table = (TableView<?>) root.lookup("#serverTable");
                table.getColumns().stream().filter(c -> "defaultPathColumn".equals(c.getId())).findFirst().orElseThrow().setVisible(true);
                var loader = new FXMLLoader(getClass().getResource("/fxml/ServerManagementDialog.fxml"));
                loader.load();
                ((ServerManagementDialogController) loader.getController()).setPreferenceService(preferences);
                var reopened = (TableView<?>) loader.getNamespace().get("serverTable");
                assertTrue(reopened.getColumns().stream().filter(c -> "defaultPathColumn".equals(c.getId())).findFirst().orElseThrow().isVisible());
            } catch (Exception error) { throw new AssertionError(error); }
            finally { preferences.saveOrUpdatePreferences(new com.seeloggyplus.shared.model.Preference("server_management_visible_columns", "")); }
        });
    }
    @Test void configureWindowUsesCompactResizableBounds() throws Exception {
        fx(() -> {
            controller.configureWindow(stage);
            assertTrue(stage.isResizable());
            assertEquals(800, stage.getMinWidth()); assertEquals(520, stage.getMinHeight());
            assertEquals(1000, stage.getWidth()); assertEquals(640, stage.getHeight());
        });
    }
    @Test void compactLayoutFitsAllWindowSizesAndThemes() throws Exception {
        for (var theme : AppTheme.Theme.values()) {
            for (double[] size : new double[][] {{800,520},{1000,640},{1600,900}}) {
                fx(() -> { AppTheme.setTheme(theme); stage.setWidth(size[0]); stage.setHeight(size[1]); });
                fx(() -> {
                    root.applyCss(); root.layout();
                    var table = (TableView<?>) root.lookup("#serverTable");
                    assertEquals(28, table.getFixedCellSize());
                    assertTrue(table.isTableMenuButtonVisible());
                    assertEquals(java.util.List.of("nameColumn","hostColumn","portColumn","usernameColumn"), table.getVisibleLeafColumns().stream().map(TableColumn::getId).toList());
                    assertTrue(table.getVisibleLeafColumns().stream().mapToDouble(TableColumn::getWidth).sum() <= table.getWidth());
                    for (var node : table.lookupAll(".scroll-bar")) {
                        var bar = (ScrollBar) node;
                        if (bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL) assertFalse(bar.isVisible());
                    }
                    var sidebar = (Region) root.lookup("#groupSidebar");
                    var groupHeader = (Region) root.lookup(".server-group-header");
                    var toolbar = (Region) root.lookup("#serverToolbar");
                    var footer = (Region) root.lookup("#serverFooter");
                    var details = (Region) root.lookup("#detailsPanel");
                    assertEquals(200, sidebar.getWidth(), 2);
                    var sidebarBg = com.seeloggyplus.shared.ui.RenderedColors.background(sidebar);
                    assertEquals(sidebarBg, com.seeloggyplus.shared.ui.RenderedColors.background(toolbar));
                    assertEquals(sidebarBg, com.seeloggyplus.shared.ui.RenderedColors.background(groupHeader));
                    assertEquals(sidebarBg, com.seeloggyplus.shared.ui.RenderedColors.background(footer));
                    assertEquals(sidebarBg, com.seeloggyplus.shared.ui.RenderedColors.background(details));
                    assertTrue(((javafx.scene.layout.HBox) groupHeader).getSpacing() >= 4);
                    for (var node : root.lookup("#groupTree").lookupAll(".tree-cell")) {
                        var cell = (TreeCell<?>) node;
                        if (cell.isEmpty()) continue;
                        var fill = cell.getBackground().getFills().getFirst();
                        assertTrue(fill.getInsets().getTop() >= 2 && fill.getInsets().getBottom() >= 2);
                    }
                    for (String id : new String[] {"serverToolbar", "serverFooter"}) {
                        var bar = (Region) root.lookup("#" + id);
                        assertTrue(bar.getHeight() <= 40);
                        for (var node : bar.getChildrenUnmodifiable()) {
                            assertTrue(node.localToScene(node.getBoundsInLocal()).getMaxX() <= stage.getScene().getWidth() + 1);
                        }
                    }
                    var status = (Label) root.lookup("#serverCountLabel");
                    assertContrast((javafx.scene.paint.Color) status.getTextFill(), com.seeloggyplus.shared.ui.RenderedColors.background(status));
                    var close = (Button) root.lookup("#closeButton");
                    assertContrast((javafx.scene.paint.Color) close.getTextFill(), com.seeloggyplus.shared.ui.RenderedColors.background(close));
                    for (String id : new String[] {"editServerButton","cloneServerButton","moveToGroupButton","deleteServerButton","clearSearchButton","refreshButton"}) {
                        var button = (Button) root.lookup("#" + id);
                        assertTrue(button.getText() == null || button.getText().isBlank());
                        assertNotNull(button.getTooltip()); assertNotNull(button.getAccessibleText());
                        assertEquals(28, button.getWidth(), 1);
                        if (!button.isDisabled()) {
                            var icon = (de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView) button.getGraphic();
                            assertContrast((javafx.scene.paint.Color) icon.getFill(), com.seeloggyplus.shared.ui.RenderedColors.background(button));
                        }
                    }
                });
            }
        }
    }
    @Test void detailsCollapseWhenSelectionClearsAndFilterCountUpdates() throws Exception {
        Thread.sleep(700);
        fx(() -> {
            try {
                var field = ServerManagementDialogController.class.getDeclaredField("allServers"); field.setAccessible(true);
                var servers = (javafx.collections.ObservableList<SSHServerModel>) field.get(controller);
                servers.setAll(new SSHServerModel("Production", "prod.example.com", 22, "deploy"), new SSHServerModel("Dev", "dev.example.com", 2222, "dev"));
                var search = (TextField) root.lookup("#searchField"); search.setText("prod");
                assertEquals("1 of 2 servers", ((Label) root.lookup("#serverCountLabel")).getText());
                ((Button) root.lookup("#clearSearchButton")).fire();
                assertEquals("2 servers", ((Label) root.lookup("#serverCountLabel")).getText());
                assertTrue(root.lookup("#clearSearchButton").isDisabled());
                var table = (TableView<?>) root.lookup("#serverTable");
                var toggle = (ToggleButton) root.lookup("#detailsButton");
                var details = root.lookup("#detailsPanel");
                assertFalse(details.isManaged()); assertTrue(toggle.isDisabled());
                table.getSelectionModel().select(0); toggle.fire();
                assertTrue(details.isVisible()); assertTrue(details.isManaged());
                assertEquals("deploy@prod.example.com:22", ((Label) root.lookup("#detailConnectionLabel")).getText());
                table.getSelectionModel().select(1);
                assertEquals("Dev", ((Label) root.lookup("#detailNameLabel")).getText());
                table.getSelectionModel().clearSelection();
                assertFalse(details.isManaged()); assertFalse(details.isVisible()); assertFalse(toggle.isSelected());
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }
    @Test void longValuesHaveTooltipsAndGenerateThemePreviews() throws Exception {
        Thread.sleep(700);
        for (var theme : AppTheme.Theme.values()) {
            fx(() -> { AppTheme.setTheme(theme); stage.setWidth(1000); stage.setHeight(640); });
            fx(() -> {
                try {
                    var field = ServerManagementDialogController.class.getDeclaredField("allServers"); field.setAccessible(true);
                    var servers = (javafx.collections.ObservableList<SSHServerModel>) field.get(controller);
                    var model = new SSHServerModel("Production application server with a very long name", "production-application.internal.example.com", 2222, "deploy");
                    model.setDefaultPath("/srv/production/application/logs/archived/2026/long-default-path"); model.setGroupName("Production/Applications");
                    servers.setAll(model);
                    for (int i = 0; i < 40; i++) servers.add(new SSHServerModel("Service " + i, "service-" + i + ".internal", 22, "admin"));
                    var rebuild = ServerManagementDialogController.class.getDeclaredMethod("rebuildGroups"); rebuild.setAccessible(true); rebuild.invoke(controller);
                    var search = (TextField) root.lookup("#searchField"); search.setText(" "); search.clear();
                    var table = (TableView<?>) root.lookup("#serverTable"); table.getSelectionModel().select(0);
                    root.applyCss(); root.layout();
                    for (var node : table.lookupAll(".table-cell")) {
                        var cell = (TableCell<?, ?>) node;
                        if (cell.getItem() instanceof String value && value.length() > 30) assertEquals(value, cell.getTooltip().getText());
                    }
                    double width = stage.getWidth(), height = stage.getHeight();
                    snapshot(theme, "closed");
                    ((ToggleButton) root.lookup("#detailsButton")).fire(); root.layout();
                    assertEquals(width, stage.getWidth()); assertEquals(height, stage.getHeight());
                    var path = (Label) root.lookup("#detailPathLabel"); assertEquals(model.getDefaultPath(), path.getTooltip().getText());
                    assertContrast((javafx.scene.paint.Color) path.getTextFill(), com.seeloggyplus.shared.ui.RenderedColors.background(path));
                    snapshot(theme, "open");
                    ((ToggleButton) root.lookup("#detailsButton")).fire();
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
        }
    }
    private void snapshot(AppTheme.Theme theme, String state) {
        try {
            var image = root.snapshot(null, null);
            int width = (int) image.getWidth(), height = (int) image.getHeight();
            int[] pixels = new int[width * height];
            image.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
            var png = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            png.setRGB(0, 0, width, height, pixels, 0, width);
            var path = java.nio.file.Path.of("build", "test-snapshots", "server-management-" + theme.name().toLowerCase() + "-" + state + ".png");
            java.nio.file.Files.createDirectories(path.getParent()); javax.imageio.ImageIO.write(png, "png", path.toFile());
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private static void assertContrast(javafx.scene.paint.Color a, javafx.scene.paint.Color b) {
        assertTrue((Math.max(luminance(a), luminance(b)) + .05) / (Math.min(luminance(a), luminance(b)) + .05) >= 4.5, "contrast " + a + " on " + b);
    }
    private static double luminance(javafx.scene.paint.Color c) { return .2126 * linear(c.getRed()) + .7152 * linear(c.getGreen()) + .0722 * linear(c.getBlue()); }
    private static double linear(double v) { return v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4); }
    private void fx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(); WaitForAsyncUtils.waitForFxEvents(); }
}
