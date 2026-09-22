package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
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

/**
 * Verifies that every themeable component follows the SELECTED theme.
 *
 * <p>This is the regression guard for the "light theme still shows graphite
 * active/focus colours" defect: the expected colours are derived from the theme
 * the app is currently on, for each of the three themes.
 */
@ExtendWith(ApplicationExtension.class)
class ThemeComponentParityTest {

    private static final PseudoClass HOVER = PseudoClass.getPseudoClass("hover");
    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");

    private record Palette(Color chrome, Color accent, Color accentHover, Color accentText, Color primary) {
    }

    private static final Palette GRAPHITE =
            new Palette(Color.web("#2b3138"), Color.web("#4a535e"), Color.web("#3a424b"),
                    Color.web("#d7dbe0"), Color.web("#343a40"));
    private static final Palette LIGHT =
            new Palette(Color.web("#e9ecef"), Color.web("#2563eb"), Color.web("#1d4ed8"),
                    Color.web("#ffffff"), Color.web("#2563eb"));
    private static final Palette DARK =
            new Palette(Color.web("#23282d"), Color.web("#434b53"), Color.web("#343b42"),
                    Color.web("#d7dbe0"), Color.web("#3a424b"));

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
    @DisplayName("Toolbar interactive states (selected / hover) use the theme accent")
    void toolbarInteractiveStatesFollowTheme() {
        onFx(() -> {
            // Dedicated (side-effect free) toggle so the real Tail button's listener
            // cannot reset the selection while we assert the colours.
            FontAwesomeIconView icon = new FontAwesomeIconView();
            ToggleButton toggle = new ToggleButton();
            toggle.setGraphic(icon);
            HBox toolbar = new HBox(toggle);
            toolbar.getStyleClass().add("app-toolbar");
            ((javafx.scene.layout.Pane) root).getChildren().add(toolbar);
            apply();

            forAllThemes((theme, palette) -> {
                // Selected state
                toggle.setSelected(true);
                apply();
                assertEquals(palette.accent(), backgroundFill(toggle),
                        "selected toolbar toggle must use the accent [" + theme + "]");
                assertEquals(palette.accentText(), icon.getFill(),
                        "selected toolbar icon must use the accent text [" + theme + "]");

                toggle.setSelected(false);
                toggle.pseudoClassStateChanged(HOVER, true);
                apply();
                assertEquals(palette.accentHover(), backgroundFill(toggle),
                        "hovered toolbar toggle must use the accent hover [" + theme + "]");
                assertEquals(palette.accentText(), icon.getFill(),
                        "hovered toolbar icon must use the accent text [" + theme + "]");
                toggle.pseudoClassStateChanged(HOVER, false);
                apply();
            });

            ((javafx.scene.layout.Pane) root).getChildren().remove(toolbar);
        });
    }

    @Test
    @DisplayName("Toolbar focus ring uses the theme accent")
    void toolbarFocusRingFollowsTheme() {
        onFx(() -> {
            Button refresh = (Button) root.lookup("#refreshButton");
            assertNotNull(refresh, "toolbar must contain the refresh button");

            forAllThemes((theme, palette) -> {
                refresh.pseudoClassStateChanged(FOCUSED, true);
                apply();
                assertEquals(palette.accent(), borderStroke(refresh),
                        "focused toolbar button border must use the accent [" + theme + "]");
                refresh.pseudoClassStateChanged(FOCUSED, false);
                apply();
            });
        });
    }

    @Test
    @DisplayName("Focusing / clicking a toolbar button must not resize it")
    void focusDoesNotResizeToolbarButton() {
        onFx(() -> {
            Button refresh = (Button) root.lookup("#refreshButton");
            assertNotNull(refresh, "toolbar must contain the refresh button");
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
    @DisplayName("Primary buttons use the theme brand colour")
    void primaryButtonsFollowBrand() {
        onFx(() -> {
            Scene scene = root.getScene();
            Button primary = new Button("Primary");
            primary.getStyleClass().add("btn-primary");
            StackPane holder = new StackPane(primary);
            Parent stageRoot = scene.getRoot();
            ((javafx.scene.layout.Pane) stageRoot).getChildren().add(holder);
            apply();

            forAllThemes((theme, palette) -> {
                apply();
                assertEquals(palette.primary(), backgroundFill(primary),
                        "primary button must use the theme primary colour [" + theme + "]");
            });
            ((javafx.scene.layout.Pane) stageRoot).getChildren().remove(holder);
        });
    }

    @Test
    @DisplayName("Menu bar active (hover/showing) state uses the theme accent")
    void menuBarActiveStateFollowsTheme() {
        onFx(() -> {
            MenuBar bar = new MenuBar();
            bar.getStyleClass().add("app-menubar");
            Menu menu = new Menu("File");
            menu.getItems().add(new MenuItem("Open"));
            bar.getMenus().add(menu);

            StackPane holder = new StackPane(bar);
            ((javafx.scene.layout.Pane) root).getChildren().add(holder);
            apply();

            Node menuNode = bar.lookup(".menu");
            assertNotNull(menuNode, "menu bar must render .menu nodes");

            forAllThemes((theme, palette) -> {
                menuNode.pseudoClassStateChanged(HOVER, true);
                apply();
                assertEquals(palette.accentHover(), backgroundFill((Region) menuNode),
                        "active menu item background must use the accent [" + theme + "]");
                Label label = (Label) menuNode.lookup(".label");
                assertNotNull(label, "menu item must have a label");
                assertEquals(palette.accentText(), label.getTextFill(),
                        "active menu label must use the accent text [" + theme + "]");
                menuNode.pseudoClassStateChanged(HOVER, false);
                apply();
            });

            ((javafx.scene.layout.Pane) root).getChildren().remove(holder);
        });
    }

    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface ThemeCheck {
        void accept(AppTheme.Theme theme, Palette palette);
    }

    private void forAllThemes(ThemeCheck check) {
        runTheme(AppTheme.Theme.GRAPHITE, GRAPHITE, check);
        runTheme(AppTheme.Theme.LIGHT, LIGHT, check);
        runTheme(AppTheme.Theme.DARK, DARK, check);
        AppTheme.setTheme(AppTheme.Theme.GRAPHITE);
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
