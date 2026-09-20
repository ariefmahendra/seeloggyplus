package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A focused input field must show a single, clean border. Modena's default
 * focus style stacks a focus ring + border + inner background, which renders as
 * an ugly double border. This guards against that regression in both themes.
 */
@ExtendWith(ApplicationExtension.class)
class InputFocusBorderTest {

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
    void focusedInputHasSingleBorder() {
        onFxThread(() -> {
            TextField textField = new TextField("hello");
            PasswordField passwordField = new PasswordField();
            passwordField.setText("secret");
            TextArea textArea = new TextArea("multi\nline");

            VBox root = new VBox(6, textField, passwordField, textArea);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();

            assertAllFocused(textField, "TextField", false);
            assertAllFocused(passwordField, "PasswordField", false);
            assertAllFocused(textArea, "TextArea", false);

            AppTheme.setDark(true);
            root.applyCss();
            root.layout();
            assertAllFocused(textField, "TextField", true);
            assertAllFocused(passwordField, "PasswordField", true);
            assertAllFocused(textArea, "TextArea", true);

            AppTheme.setDark(false);
        });
    }

    private void assertAllFocused(Control field, String name, boolean dark) {
        field.requestFocus();
        javafx.scene.Parent root = field.getScene().getRoot();
        root.applyCss();
        root.layout();

        Background background = ((Region) field).getBackground();
        assertNotNull(background, name + " must have a background");

        long visibleFills = background.getFills().stream()
                .map(f -> f.getFill())
                .filter(p -> p instanceof Color c && c.getOpacity() > 0.01)
                .count();

        assertTrue(visibleFills <= 2,
                name + " focused state must not render a double border "
                        + "(" + (dark ? "dark" : "light") + " mode), visible fills=" + visibleFills);
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
