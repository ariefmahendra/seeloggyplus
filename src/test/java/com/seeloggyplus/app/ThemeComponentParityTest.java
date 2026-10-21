package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import javafx.scene.layout.Pane;

@ExtendWith(ApplicationExtension.class)
class ThemeComponentParityTest {

    private static final PseudoClass HOVER = PseudoClass.getPseudoClass("hover");
    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");

    private record Palette(Color chrome, Color accent, Color accentHover, Color accentText, Color soft, Color text) {
    }

    private static final Palette LIGHT =
            new Palette(Color.web("#eef0f3"), Color.web("#3871e1"), Color.web("#2f5eb9"),
                    Color.web("#ffffff"), Color.web("#f7f8f9"), Color.web("#192330"));
    private static final Palette DARK =
            new Palette(Color.web("#20242b"), Color.web("#3871e1"), Color.web("#2f5eb9"),
                    Color.web("#ffffff"), Color.web("#202630"), Color.web("#d1d3d9"));

    private Stage stage;
    private Parent root;
    private MainController controller;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.setWidth(1100);
        stage.setHeight(720);
        stage.show();
        apply();
    }

    @AfterEach
    void resetTheme() {
        onFx(() -> AppTheme.setDark(false));
    }

    @Test
    @DisplayName("Chrome (menu bar / toolbar / status bar) follows every theme")
    void chromeFollowsTheme() {
        onFx(() -> {
            Region menuBar = (Region) root.lookup(".app-menubar");
            Region toolbar = (Region) root.lookup(".app-toolbar");
            Region statusBar = (Region) root.lookup(".status-bar");
            assertNotNull(menuBar);
            assertNotNull(toolbar);
            assertNotNull(statusBar);

            forAllThemes((theme, palette) -> {
                assertEquals(palette.chrome(), backgroundFill(menuBar),
                        "menu bar background must use the theme chrome [" + theme + "]");
                assertEquals(palette.chrome(), backgroundFill(toolbar),
                        "toolbar background must use the theme chrome [" + theme + "]");
                assertEquals(palette.chrome(), backgroundFill(statusBar),
                        "status bar background must use the theme chrome [" + theme + "]");
            });
        });
    }

    @Test
    @DisplayName("Toolbar interactive states use soft theme surfaces")
    void toolbarInteractiveStatesFollowTheme() {
        onFx(() -> {
            FontAwesomeIconView icon = new FontAwesomeIconView();
            ToggleButton toggle = new ToggleButton();
            toggle.setGraphic(icon);
            HBox toolbar = new HBox(toggle);
            toolbar.getStyleClass().add("app-toolbar");
            ((Pane) root).getChildren().add(toolbar);
            apply();

            forAllThemes((theme, palette) -> {
                toggle.setSelected(true);
                apply();
                assertEquals(Color.web(theme == AppTheme.Theme.DARK ? "#33353b" : "#e4e5e8"), backgroundFill(toggle),
                        "selected toolbar toggle must use a neutral gray surface [" + theme + "]");
                assertEquals(Color.web(theme == AppTheme.Theme.DARK ? "#71a1fe" : "#2f5eb9"), icon.getFill(),
                        "selected toolbar icon must use readable blue [" + theme + "]");

                toggle.setSelected(false);
                toggle.pseudoClassStateChanged(HOVER, true);
                apply();
                assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-button-hover", theme), backgroundFill(toggle),
                        "hovered toolbar toggle must use the soft surface [" + theme + "]");
                assertEquals(Color.web(theme == AppTheme.Theme.DARK ? "#71a1fe" : "#2f5eb9"), icon.getFill(),
                        "hovered toolbar icon must use readable blue [" + theme + "]");
                toggle.pseudoClassStateChanged(HOVER, false);
                apply();
            });

            ((Pane) root).getChildren().remove(toolbar);
        });
    }

    @Test
    @DisplayName("Keyboard toolbar focus uses a thin theme ring and gray interaction surface")
    void toolbarFocusRingFollowsTheme() {
        onFx(() -> {
            Button refresh = (Button) root.lookup("#searchButton");
            assertNotNull(refresh, "toolbar must contain the search button");
            refresh.setDisable(false);

            forAllThemes((theme, palette) -> {
                refresh.pseudoClassStateChanged(FOCUSED, true);
                refresh.pseudoClassStateChanged(PseudoClass.getPseudoClass("keyboard-focus"), true);
                apply();
                assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-focus-ring-panel", theme), borderStroke(refresh));
                assertEquals(1, refresh.getBorder().getStrokes().getFirst().getWidths().getTop());
                assertEquals(Color.web(theme == AppTheme.Theme.DARK ? "#33353b" : "#e4e5e8"), backgroundFill(refresh));
                assertEquals(palette.text(), refresh.getTextFill());
                refresh.pseudoClassStateChanged(FOCUSED, false);
                refresh.pseudoClassStateChanged(PseudoClass.getPseudoClass("keyboard-focus"), false);
                apply();
            });
        });
    }

    @Test
    @DisplayName("Focusing / clicking a toolbar button must not resize it")
    void focusDoesNotResizeToolbarButton() {
        onFx(() -> {
            Button refresh = (Button) root.lookup("#searchButton");
            assertNotNull(refresh, "toolbar must contain the search button");
            refresh.setDisable(false);
            apply();

            double width = refresh.getWidth();
            double height = refresh.getHeight();
            assertTrue(width > 0 && height > 0, "button must have a laid-out size");

            refresh.pseudoClassStateChanged(FOCUSED, true);
            apply();
            assertEquals(width, refresh.getWidth(), 0.01,
                    "focusing a toolbar button must not change its width (reserved focus ring)");
            assertEquals(height, refresh.getHeight(), 0.01,
                    "focusing a toolbar button must not change its height (reserved focus ring)");

            refresh.pseudoClassStateChanged(FOCUSED, false);
            apply();
            assertEquals(width, refresh.getWidth(), 0.01,
                    "losing focus must not resize the toolbar button");
        });
    }

    @Test
    @DisplayName("Primary buttons blend at rest and remain readable on hover")
    void primaryButtonsFollowBrand() {
        onFx(() -> {
            Scene scene = root.getScene();
            Button primary = new Button("Primary");
            primary.getStyleClass().add("btn-primary");
            StackPane holder = new StackPane(primary);
            Parent stageRoot = scene.getRoot();
            ((Pane) stageRoot).getChildren().add(holder);
            apply();

            forAllThemes((theme, palette) -> {
                apply();
                assertEquals(Color.TRANSPARENT, backgroundFill(primary),
                        "primary button must blend at rest [" + theme + "]");
                assertEquals(palette.text(), primary.getTextFill());
                primary.pseudoClassStateChanged(HOVER, true);
                apply();
                assertNotEquals(Color.TRANSPARENT, backgroundFill(primary));
                assertEquals(palette.text(), primary.getTextFill());
                primary.pseudoClassStateChanged(HOVER, false);
                apply();
            });
            ((Pane) stageRoot).getChildren().remove(holder);
        });
    }

    @Test
    @DisplayName("Menu bar active state uses a soft highlight with readable chrome text")
    void menuBarActiveStateFollowsTheme() {
        onFx(() -> {
            MenuBar bar = new MenuBar();
            bar.getStyleClass().add("app-menubar");
            Menu menu = new Menu("File");
            menu.getItems().add(new MenuItem("Open"));
            bar.getMenus().add(menu);

            StackPane holder = new StackPane(bar);
            ((Pane) root).getChildren().add(holder);
            apply();

            Node menuNode = bar.lookup(".menu");
            assertNotNull(menuNode, "menu bar must render .menu nodes");

            forAllThemes((theme, palette) -> {
                menuNode.pseudoClassStateChanged(HOVER, true);
                apply();
                assertEquals(com.seeloggyplus.shared.ui.ThemePalette.color("-sl-button-hover", theme), backgroundFill((Region) menuNode),
                        "active menu item background must use a soft highlight [" + theme + "]");
                Label label = (Label) menuNode.lookup(".label");
                assertNotNull(label, "menu item must have a label");
                assertEquals(palette.text(), label.getTextFill(),
                        "active menu label must use readable chrome text [" + theme + "]");
                menuNode.pseudoClassStateChanged(HOVER, false);
                apply();
            });

            ((Pane) root).getChildren().remove(holder);
        });
    }

    @FunctionalInterface
    private interface ThemeCheck {
        void accept(AppTheme.Theme theme, Palette palette);
    }

    private void forAllThemes(ThemeCheck check) {
        runTheme(AppTheme.Theme.LIGHT, LIGHT, check);
        runTheme(AppTheme.Theme.DARK, DARK, check);
        AppTheme.setTheme(AppTheme.Theme.LIGHT);
        apply();
    }

    private void runTheme(AppTheme.Theme theme, Palette palette, ThemeCheck check) {
        AppTheme.setTheme(theme);
        apply();
        check.accept(theme, palette);
    }

    private void apply() {
        root.applyCss();
        root.layout();
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Color backgroundFill(Region region) {
        Background bg = region.getBackground();
        if (bg == null || bg.getFills().isEmpty()) {
            return null;
        }
        Paint paint = bg.getFills().get(0).getFill();
        return paint instanceof Color color ? color : null;
    }

    private static Color borderStroke(Region region) {
        if (region.getBorder() == null || region.getBorder().getStrokes().isEmpty()) {
            return null;
        }
        Paint stroke = region.getBorder().getStrokes().get(0).getTopStroke();
        return stroke instanceof Color color ? color : null;
    }

    private static void onFx(Runnable action) {
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
