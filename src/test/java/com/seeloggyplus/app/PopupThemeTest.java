package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.Background;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
 * Regression for the dropdown (ComboBox/ChoiceBox popup) colours: in the light
 * Graphite and Light themes the popup surface must stay light with dark text,
 * instead of falling back to a dark Modena/default rendering.
 */
@ExtendWith(ApplicationExtension.class)
class PopupThemeTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @AfterEach
    void resetTheme() {
        onFxThread(() -> AppTheme.setDark(false));
    }

    @Test
    @DisplayName("Graphite dropdown popups are light with readable dark text")
    void graphiteDropdownIsLight() {
        onFxThread(() -> {
            useTheme(AppTheme.Theme.GRAPHITE);
            ListView<String> list = buildPopup();

            Color surface = popupSurface(list);
            assertNotNull(surface);
            assertTrue(luminance(surface) > 0.7,
                    "Graphite dropdown background must be light, was " + surface);

            Color text = firstCellTextFill(list);
            assertNotNull(text);
            assertTrue(luminance(text) < 0.4,
                    "Graphite dropdown text must stay dark on the light surface, was " + text);
        });
    }

    @Test
    @DisplayName("Dark theme still renders a dark dropdown surface")
    void darkDropdownStaysDark() {
        onFxThread(() -> {
            useTheme(AppTheme.Theme.DARK);
            ListView<String> list = buildPopup();

            Color surface = popupSurface(list);
            assertNotNull(surface);
            assertTrue(luminance(surface) < 0.3,
                    "Dark theme dropdown background must stay dark, was " + surface);
        });
    }

    @Test
    @DisplayName("Light theme dropdown is light too")
    void lightDropdownIsLight() {
        onFxThread(() -> {
            useTheme(AppTheme.Theme.LIGHT);
            ListView<String> list = buildPopup();

            Color surface = popupSurface(list);
            assertNotNull(surface);
            assertTrue(luminance(surface) > 0.7,
                    "Light theme dropdown background must be light, was " + surface);
        });
    }

    @Test
    @DisplayName("closed dropdown fields render as themed fields with readable text")
    void closedDropdownFieldsAreReadable() {
        ComboBox<String> combo = new ComboBox<>();
        onFxThread(() -> {
            combo.getItems().addAll("Open/Download", "Tail");
            combo.getSelectionModel().selectFirst();
            VBox root = new VBox(combo);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.setWidth(400);
            stage.setHeight(200);
            stage.show();
        });

        for (AppTheme.Theme theme : AppTheme.Theme.values()) {
            onFxThread(() -> {
                useTheme(theme);
                combo.getParent().applyCss();
                combo.getParent().layout();
                WaitForAsyncUtils.waitForFxEvents();
            });

            Color surface = firstFill(combo.getBackground());
            assertNotNull(surface, theme + ": dropdown field must have a themed surface");
            ListCell<?> buttonCell = (ListCell<?>) combo.lookup(".list-cell");
            assertNotNull(buttonCell, theme + ": the dropdown must render its value cell");
            Color text = (Color) buttonCell.getTextFill();
            assertNotNull(text, theme + ": dropdown text must be themed");
            assertTrue(contrast(text, surface) >= 4.5,
                    theme + ": dropdown text must contrast with its field surface");

            if (theme == AppTheme.Theme.DARK) {
                assertTrue(luminance(surface) < 0.3, "Dark dropdown must stay dark");
            } else {
                assertTrue(luminance(surface) > 0.7,
                        theme + ": Graphite/Light dropdown fields must be light");
            }
        }
    }

    @Test
    @DisplayName("dropdown selection is a neutral light highlight, not dark/blue, in Graphite and Light")
    void selectedItemUsesNeutralHighlight() {
        for (AppTheme.Theme theme : new AppTheme.Theme[]{AppTheme.Theme.GRAPHITE, AppTheme.Theme.LIGHT}) {
            AtomicReference<ListView<String>> listRef =
                    new AtomicReference<>();
            AtomicReference<ListCell<?>> cellRef =
                    new AtomicReference<>();
            onFxThread(() -> {
                useTheme(theme);
                ListView<String> list = buildPopup();
                listRef.set(list);
                cellRef.set(firstCell(list));
            });
            ListView<String> list = listRef.get();
            ListCell<?> cell = cellRef.get();
            assertNotNull(cell, theme + ": the popup must render cells");

            onFxThread(() -> {
                cell.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), true);
                list.getParent().applyCss();
                list.getParent().layout();
                WaitForAsyncUtils.waitForFxEvents();
            });

            Color selected = firstFill(cell.getBackground());
            assertNotNull(selected, theme + ": selected popup item must have a background");
            assertTrue(luminance(selected) > 0.7,
                    theme + ": selected dropdown item must stay light, was " + selected);
            double maxChannel = Math.max(selected.getRed(), Math.max(selected.getGreen(), selected.getBlue()));
            double minChannel = Math.min(selected.getRed(), Math.min(selected.getGreen(), selected.getBlue()));
            assertTrue(maxChannel - minChannel < 0.1,
                    theme + ": selected dropdown item must be neutral (not blue), was " + selected);

            Color text = (Color) cell.getTextFill();
            assertNotNull(text);
            assertTrue(contrast(text, selected) >= 4.5,
                    theme + ": selected dropdown text must stay readable");
        }
    }

    private void useTheme(AppTheme.Theme theme) {
        AppTheme.setTheme(AppTheme.Theme.DARK);
        AppTheme.setTheme(theme);
    }

    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    private static Color firstFill(Background background) {
        if (background == null || background.getFills().isEmpty()) {
            return null;
        }
        return background.getFills().get(0).getFill() instanceof Color c ? c : null;
    }

    private static ListCell<?> firstCell(ListView<String> list) {
        for (var node : list.lookupAll(".list-cell")) {
            if (node instanceof ListCell<?> cell) {
                return cell;
            }
        }
        return null;
    }

    private ListView<String> buildPopup() {
        StackPane popupRoot = new StackPane();
        popupRoot.getStyleClass().add("combo-box-popup");
        ListView<String> list = new ListView<>();
        list.setCellFactory(TextFieldListCell.forListView());
        list.getItems().addAll("Open/Download", "Tail");
        popupRoot.getChildren().add(list);

        VBox root = new VBox(popupRoot);
        Scene scene = AppTheme.scene(root);
        stage.setScene(scene);
        stage.setWidth(400);
        stage.setHeight(300);
        stage.show();

        root.applyCss();
        root.layout();
        WaitForAsyncUtils.waitForFxEvents();
        return list;
    }

    private static Color popupSurface(ListView<String> list) {
        Background background = list.getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return null;
        }
        return background.getFills().get(0).getFill() instanceof Color c ? c : null;
    }

    private static Color firstCellTextFill(ListView<String> list) {
        for (var node : list.lookupAll(".list-cell")) {
            if (node instanceof ListCell<?> cell && cell.getTextFill() instanceof Color c) {
                return c;
            }
        }
        return null;
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
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
