package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused controls must show a SINGLE focus ring — not a layered/double border.
 * Modena stacks a faint outer halo plus the focus ring; we disable the halo
 * ({@code -fx-faint-focus-color: transparent}) and this guards the regression by
 * sampling the pixels just outside each control while it is focused.
 */
@ExtendWith(ApplicationExtension.class)
class FocusBorderLayerTest {

    /** One distinct colour outside the bounds is the (single) focus ring. */
    private static final int MAX_OUTER_LAYERS = 1;

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
    void focusedControlsHaveSingleBorderLayer() {
        onFxThread(() -> {
            Button button = new Button("Button");
            ToggleButton toggle = new ToggleButton("Toggle");
            ComboBox<String> combo = new ComboBox<>();
            combo.getItems().addAll("a", "b");
            combo.setValue("a");
            ChoiceBox<String> choice = new ChoiceBox<>();
            choice.getItems().addAll("a", "b");
            choice.setValue("a");
            Spinner<Integer> spinner = new Spinner<>(1, 10, 5);
            CheckBox check = new CheckBox("Check");
            RadioButton radio = new RadioButton("Radio");
            ListView<String> list = new ListView<>();
            list.getItems().addAll("one", "two");
            list.setPrefHeight(80);

            Node[] components = {button, toggle, combo, choice, spinner, check, radio, list};
            String[] names = {"Button", "ToggleButton", "ComboBox", "ChoiceBox", "Spinner", "CheckBox", "RadioButton", "ListView"};

            VBox root = new VBox(18, components);
            root.setPadding(new javafx.geometry.Insets(30));
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.setWidth(460);
            stage.setHeight(640);
            stage.show();

            for (boolean dark : new boolean[] {false, true}) {
                AppTheme.setDark(dark);
                root.applyCss();
                root.layout();
                String mode = dark ? "dark" : "light";

                for (int i = 0; i < components.length; i++) {
                    components[i].requestFocus();
                    root.applyCss();
                    root.layout();
                    WaitForAsyncUtils.waitForFxEvents();

                    WritableImage image = scene.snapshot(null);
                    int outerLayers = countOuterLayers(image, components[i]);
                    assertTrue(outerLayers <= MAX_OUTER_LAYERS,
                            names[i] + " [" + mode + "] focused control must not have a layered border "
                                    + "(distinct layers outside bounds = " + outerLayers + ")");
                }
            }

            AppTheme.setDark(false);
        });
    }

    private static int countOuterLayers(WritableImage image, Node node) {
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        int y = (int) Math.round(bounds.getMinY() + bounds.getHeight() / 2);
        int edge = (int) Math.round(bounds.getMinX());
        int from = Math.max(0, edge - 8);
        PixelReader reader = image.getPixelReader();

        Set<Integer> colours = new LinkedHashSet<>();
        for (int x = from; x < edge && x < image.getWidth(); x++) {
            if (y < 0 || y >= image.getHeight()) {
                continue;
            }
            colours.add(quantize(reader.getColor(x, y)));
        }
        return colours.size();
    }

    private static int quantize(Color color) {
        int r = (int) Math.round(color.getRed() * 255) / 24;
        int g = (int) Math.round(color.getGreen() * 255) / 24;
        int b = (int) Math.round(color.getBlue() * 255) / 24;
        return (r << 16) | (g << 8) | b;
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
