package com.seeloggyplus.features.detail.presentation;

import com.seeloggyplus.app.MainController;

import com.seeloggyplus.features.detail.presentation.LogDetailPaneController;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.RenderedColors;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import com.seeloggyplus.shared.util.SyntaxHighlighter;
import java.net.URL;

@ExtendWith(ApplicationExtension.class)
class DetailPanelThemeTest {

    private Parent mainRoot;
    private MainController controller;
    private Stage stage;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        mainRoot = loader.load();
        controller = loader.getController();
    }

    @AfterEach
    void resetTheme() {
        onFxThread(() -> AppTheme.setDark(false));
    }

    @Test
    void detailAreaHasRichtextStylesheet() {
        onFxThread(() -> {
            StyleClassedTextArea area = mainDetailArea();
            assertNotNull(area, "detailCodeArea must be created during initialization");
            assertTrue(area.getStyleClass().contains("detail-area"),
                    "detail area must carry the detail-area style class");
            assertTrue(area.getStylesheets().stream().anyMatch(url -> url.contains("richtext.css")),
                    "detail area must load richtext.css");
        });
    }

    @Test
    void detailAreaBackgroundFollowsTheme() {
        onFxThread(() -> {
            StyleClassedTextArea area = themedArea();
            rootLayout(area);

            assertEquals(Color.web("#ffffff"), backgroundFill(area),
                    "light theme detail background must be the light surface");

            AppTheme.setDark(true);
            area.applyCss();
            assertEquals(Color.web("#191a1c"), backgroundFill(area),
                    "dark theme detail background must be the dark surface");

            AppTheme.setDark(false);
        });
    }

    @Test
    void defaultDetailTextUsesThemeFill() {
        onFxThread(() -> {
            StyleClassedTextArea area = themedArea();
            String line = "plain unstyled log line";

            area.replaceText(line);
            area.setStyleSpans(0, SyntaxHighlighter.computeLogHighlighting(line));
            rootLayout(area);

            Text lightText = firstTextNode(area);
            assertNotNull(lightText, "detail area must render text nodes");
            assertEquals(Color.web("#080808"), lightText.getFill(),
                    "light theme default detail text must be dark");

            AppTheme.setDark(true);
            area.applyCss();
            Text darkText = firstTextNode(area);
            assertNotNull(darkText);
            assertEquals(Color.web("#bcbec4"), darkText.getFill(),
                    "dark theme default detail text must be light (not black)");

            AppTheme.setDark(false);
        });
    }

    @Test
    void prettifyToggleIconsFollowTheTheme() {
        onFxThread(() -> {
            stage.setScene(AppTheme.scene(mainRoot));
            stage.show();

            ToggleButton json = prettifyToggle("prettifyJsonButton");
            FontAwesomeIconView icon = (FontAwesomeIconView) json.getGraphic();
            assertNotNull(icon, "prettify toggle must have an icon graphic");

            for (var theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                json.setSelected(false);
                mainRoot.applyCss();
                Color restingBackground = RenderedColors.background(json);
                for (boolean selected : new boolean[] {false, true}) {
                    json.setSelected(selected);
                    mainRoot.applyCss();
                    Color expected = Color.web(theme == AppTheme.Theme.DARK ? "#d1d3d9" : "#000000");
                    assertEquals(expected, icon.getFill(), "prettify icon theme " + theme);
                    Color background = RenderedColors.background(json);
                    double foregroundLight = luminance((Color) icon.getFill());
                    double backgroundLight = luminance(background);
                    assertTrue((Math.max(foregroundLight, backgroundLight) + .05)
                            / (Math.min(foregroundLight, backgroundLight) + .05) >= 3,
                            "prettify icon contrast " + theme + " selected=" + selected);
                    if (selected) assertNotEquals(restingBackground, background, "selected format must remain visible");
                }
            }
        });
    }

    private static double luminance(Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
    }

    private static double linear(double value) {
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    @Test
    void selectedRowsHighlightIsUniformAcrossAllRows() {
        onFxThread(() -> {
            stage.setWidth(1200);
            stage.setHeight(700);
            stage.setScene(AppTheme.scene(mainRoot));
            stage.show();
            ((javafx.scene.control.SplitPane) mainRoot.lookup("#verticalSplitPane")).setDividerPositions(.45);
            mainRoot.applyCss();
            mainRoot.layout();

            StyleClassedTextArea area = mainDetailArea();
            area.replaceText(String.join("\n",
                    "{",
                    "  \"level\" : \"ERROR\",",
                    "  \"pid\" : 2463,",
                    "  \"thread\" : \"http-nio-8080-exec-6\",",
                    "  \"service\" : \"notification-gateway\",",
                    "}"));
            area.selectRange(0, area.getText().indexOf("}", 1));
            rootLayout(area);

            List<Bounds> rows = selectionRowBounds(area);
            assertTrue(rows.size() >= 2, "a multi-line selection must render one highlight per row");
            double top = rows.get(0).getMinY();
            double bottom = rows.get(rows.size() - 1).getMaxY() - 1;

            WritableImage image = area.snapshot(null, null);
            assertTrue(bottom < image.getHeight(), "all selected fixture rows must be visible before sampling");
            Color expected = image.getPixelReader().getColor((int) image.getWidth() - 25, (int) top);
            assertNotEquals(Color.WHITE, expected,
                    "the sampled column must sit inside the selection highlight");

            for (int y = (int) top; y <= bottom; y++) {
                Color pixel = image.getPixelReader().getColor((int) image.getWidth() - 25, y);
                assertEquals(expected, pixel,
                        "every selected row must share one highlight colour, row " + y + " differs");
            }
        });
    }

    private static List<Bounds> selectionRowBounds(Parent parent) {
        List<Bounds> rows = new ArrayList<>();
        for (Node node : parent.lookupAll(".selection")) {
            if (node instanceof Shape shape) {
                rows.add(parent.sceneToLocal(shape.localToScene(shape.getBoundsInLocal())));
            }
        }
        rows.sort(Comparator.comparingDouble(Bounds::getMinY));
        return rows;
    }

    private ToggleButton prettifyToggle(String name) {
        try {
            Field controllerField = MainController.class.getDeclaredField("detailPaneController");
            controllerField.setAccessible(true);
            LogDetailPaneController detailPane = (LogDetailPaneController) controllerField.get(controller);
            Field field = LogDetailPaneController.class.getDeclaredField(name);
            field.setAccessible(true);
            return (ToggleButton) field.get(detailPane);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private StyleClassedTextArea themedArea() {
        StyleClassedTextArea area = new StyleClassedTextArea();
        area.setEditable(false);
        area.getStyleClass().add("detail-area");
        area.getStylesheets().add(requireRichtextCss());
        StackPane root = new StackPane(area);
        root.setId("detail-theme-root");
        Scene scene = AppTheme.scene(root);
        stage.setScene(scene);
        stage.show();
        root.applyCss();
        root.layout();
        return area;
    }

    private String requireRichtextCss() {
        URL url = getClass().getResource("/style/richtext.css");
        assertNotNull(url, "richtext.css must be on the classpath");
        return url.toExternalForm();
    }

    private void rootLayout(Node node) {
        if (node.getScene() != null) {
            Parent root = node.getScene().getRoot();
            root.applyCss();
            root.layout();
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Text firstTextNode(Parent parent) {
        for (Node node : parent.lookupAll(".text")) {
            if (node instanceof Text text && text.getText() != null && !text.getText().isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private StyleClassedTextArea mainDetailArea() {
        try {
            Field f = MainController.class.getDeclaredField("detailPaneController");
            f.setAccessible(true);
            LogDetailPaneController detailPane = (LogDetailPaneController) f.get(controller);
            return detailPane.getCodeArea();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Color backgroundFill(Region region) {
        Background bg = region.getBackground();
        if (bg == null || bg.getFills().isEmpty()) {
            return null;
        }
        Paint paint = bg.getFills().get(0).getFill();
        return paint instanceof Color color ? color : null;
    }

    private static void onFxThread(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }
}
