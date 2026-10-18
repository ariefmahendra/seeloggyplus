package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
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
class SettingsLayoutTest {
    private Stage stage;
    private Parent root;
    @Start void start(Stage stage) throws Exception {
        this.stage = stage;
        root = new FXMLLoader(getClass().getResource("/fxml/PreferencesDialog.fxml")).load();
        stage.setScene(AppTheme.scene(root));
        stage.show();
    }

    @Test void categoriesRetainEditsAndFooterStaysVisibleAtBothSizes() throws Exception {
        try {
            fx(() -> ((Spinner<Integer>) root.lookup("#appMaxMemorySpinner")).getValueFactory().setValue(7));
            for (var theme : AppTheme.Theme.values()) {
                for (double[] size : new double[][] { {660,460}, {600,420} }) {
                    for (int index = 0; index < 3; index++) {
                        final int pageIndex = index;
                        fx(() -> {
                            AppTheme.setTheme(theme);
                            stage.setWidth(size[0]); stage.setHeight(size[1]);
                            var categories = (ListView<?>) root.lookup("#settingsCategories");
                            assertEquals(java.util.List.of("General", "Log Viewer", "SSH"), categories.getItems());
                            categories.getSelectionModel().select(pageIndex);
                        });
                        fx(() -> {
                            root.applyCss(); root.layout();
                            var surfaceColor=com.seeloggyplus.shared.ui.RenderedColors.background(root);
                            for(String selector:new String[]{".settings-sidebar",".settings-footer",".settings-scroll > .viewport"}) {
                                var panel=(Region)root.lookup(selector);
                                if(panel.getClip()!=null) {
                                    var clip=assertInstanceOf(javafx.scene.shape.Rectangle.class,panel.getClip());
                                    assertEquals(0,clip.getArcWidth(),selector); assertEquals(0,clip.getArcHeight(),selector);
                                }
                                assertEquals(surfaceColor,com.seeloggyplus.shared.ui.RenderedColors.background(panel),selector);
                                for(var fill:panel.getBackground().getFills()) assertEquals(0,fill.getRadii().getTopLeftHorizontalRadius(),selector);
                            }
                            var activePage = (ScrollPane) root.lookup("#" + new String[] { "generalPage", "logViewerPage", "sshPage" }[pageIndex]);
                            activePage.setVvalue(0);
                            if (size[0] == 660) snapshot(theme, pageIndex);
                            for (var node : activePage.getContent().lookupAll(".spinner")) {
                                var spinner = (Spinner<?>) node;
                                var editor = spinner.getEditor();
                                var background = com.seeloggyplus.shared.ui.RenderedColors.background(editor);
                                var text = (javafx.scene.text.Text) editor.lookup(".text");
                                assertTrue(contrast((Color) text.getFill(), background) >= 4.5);
                                for (String selector : new String[] { ".increment-arrow", ".decrement-arrow" }) {
                                    var arrow = (Region) spinner.lookup(selector);
                                    var arrowFill = (Color) arrow.getBackground().getFills().getFirst().getFill();
                                    assertTrue(contrast(arrowFill, background) >= 3);
                                }
                            }
                            int visible = 0;
                            for (String id : new String[] { "generalPage", "logViewerPage", "sshPage" }) {
                                var page = (ScrollPane) root.lookup("#" + id);
                                if (page.isVisible()) {
                                    visible++;
                                    assertTrue(page.isManaged());
                                    assertTrue(page.isFitToWidth());
                                    assertEquals(ScrollPane.ScrollBarPolicy.NEVER, page.getHbarPolicy());
                                    assertTrue(page.getViewportBounds().getWidth() > 400, "Content width: " + page.getViewportBounds());
                                } else assertFalse(page.isManaged());
                            }
                            assertEquals(1, visible);
                            var footer = (Region) root.lookup(".settings-footer");
                            assertTrue(footer.getHeight() <= 44);
                            for (String id : new String[] { "saveButton", "cancelButton" }) {
                                var button = (Button) root.lookup("#" + id);
                                var bounds = button.localToScene(button.getBoundsInLocal());
                                assertTrue(bounds.getMaxY() <= stage.getScene().getHeight());
                                assertTrue(bounds.getMinY() >= 0);
                                assertEquals(28, button.getHeight(), 1);
                            }
                            Color surface = (Color) ((Region) root).getBackground().getFills().getFirst().getFill();
                            for (var node : root.lookupAll(".settings-description")) {
                                var label = (Label) node;
                                assertTrue(label.isWrapText());
                                assertTrue(contrast((Color) label.getTextFill(), surface) >= 4.5);
                            }
                            for (var node : root.lookup("#settingsCategories").lookupAll(".list-cell")) {
                                var cell = (ListCell<?>) node;
                                if (cell.isEmpty()) continue;
                                var fill = cell.getBackground().getFills().getFirst();
                                Color background = (Color) fill.getFill();
                                assertTrue(fill.getInsets().getTop() >= 2 && fill.getInsets().getBottom() >= 2);
                                assertTrue(contrast((Color) cell.getTextFill(), background) >= 4.5);
                            }
                        });
                    }
                }
            }
            fx(() -> assertEquals(7, ((Spinner<?>) root.lookup("#appMaxMemorySpinner")).getValue()));
        } finally { fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)); }
    }
    @Test void downloadDirectoryUsesThemeSurfaceAndReadableText() throws Exception {
        try {
            for (var theme : AppTheme.Theme.values()) fx(() -> {
                AppTheme.setTheme(theme);
                ((ListView<?>) root.lookup("#settingsCategories")).getSelectionModel().select(2);
                root.applyCss(); root.layout();
                var field = (TextField) root.lookup("#sshDownloadDirField");
                var background = com.seeloggyplus.shared.ui.RenderedColors.background(field);
                assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-input-bg", theme), background);
                assertEquals(28, field.getHeight(), .1);
                assertTrue(field.getWidth() >= 180);
                assertEquals(5, field.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius(), .1);
                var prompt = (javafx.scene.text.Text) field.lookup(".text");
                assertTrue(contrast((Color) prompt.getFill(), background) >= 4.5);
                field.setText("D:\\logs\\downloads");
                root.applyCss(); root.layout();
                assertTrue(contrast((Color) ((javafx.scene.text.Text) field.lookup(".text")).getFill(), background) >= 4.5);
                field.requestFocus(); root.applyCss();
                assertEquals(background, com.seeloggyplus.shared.ui.RenderedColors.background(field));
                assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-focus-ring", theme), field.getBorder().getStrokes().getFirst().getTopStroke());
                field.clear();
            });
        } finally { fx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)); }
    }
    private void fx(Runnable action) throws Exception {
        WaitForAsyncUtils.asyncFx(action).get();
        WaitForAsyncUtils.waitForFxEvents();
    }
    private void snapshot(AppTheme.Theme theme, int index) {
        try {
            var image = root.snapshot(null, null);
            int width = (int) image.getWidth(), height = (int) image.getHeight();
            int[] pixels = new int[width * height];
            image.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
            var png = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            png.setRGB(0, 0, width, height, pixels, 0, width);
            var path = java.nio.file.Path.of("build", "test-snapshots", "settings-" + theme.name().toLowerCase() + "-" + index + ".png");
            java.nio.file.Files.createDirectories(path.getParent());
            javax.imageio.ImageIO.write(png, "png", path.toFile());
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private static double contrast(Color a, Color b) { return (Math.max(luminance(a),luminance(b))+0.05)/(Math.min(luminance(a),luminance(b))+0.05); }
    private static double luminance(Color c) { return 0.2126*linear(c.getRed())+0.7152*linear(c.getGreen())+0.0722*linear(c.getBlue()); }
    private static double linear(double v) { return v <= 0.04045 ? v/12.92 : Math.pow((v+0.055)/1.055,2.4); }
}
