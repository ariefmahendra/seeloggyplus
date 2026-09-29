package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.scene.Scene;
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

    private void useTheme(AppTheme.Theme theme) {
        AppTheme.setTheme(AppTheme.Theme.DARK);
        AppTheme.setTheme(theme);
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
            if (node instanceof javafx.scene.control.ListCell<?> cell && cell.getTextFill() instanceof Color c) {
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
