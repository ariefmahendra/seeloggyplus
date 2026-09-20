package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Text inside input fields must be contrasty and match the active theme:
 * dark text on light fields in light mode, light text on dark fields in dark
 * mode (WCAG AA >= 4.5:1).
 */
@ExtendWith(ApplicationExtension.class)
class InputContrastTest {

    private static final double MIN_CONTRAST = 4.5;

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
    void inputTextContrastsAndMatchesTheme() {
        onFxThread(() -> {
            TextField textField = new TextField("hello");
            PasswordField passwordField = new PasswordField();
            passwordField.setText("secret");
            TextArea textArea = new TextArea("multi\nline");
            Spinner<Integer> spinner = new Spinner<>(1, 10, 5);
            spinner.setEditable(true);

            Region[] fields = {textField, passwordField, textArea, spinner.getEditor()};
            String[] names = {"TextField", "PasswordField", "TextArea", "SpinnerEditor"};

            VBox root = new VBox(6, textField, passwordField, textArea, spinner);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();

            assertAllFields(fields, names, false);
            Color lightText = textFill(textField);

            AppTheme.setDark(true);
            root.applyCss();
            root.layout();
            assertAllFields(fields, names, true);
            Color darkText = textFill(textField);

            assertNotEquals(lightText, darkText, "input text colour must change with the theme");
            assertTrue(relativeLuminance(lightText) < 0.5, "light mode input text must be dark");
            assertTrue(relativeLuminance(darkText) > 0.5, "dark mode input text must be light");

            AppTheme.setDark(false);
        });
    }

    @Test
    void inputPromptTextContrastsInBothThemes() {
        onFxThread(() -> {
            TextField textField = new TextField();
            textField.setPromptText("SSH username");
            PasswordField passwordField = new PasswordField();
            passwordField.setPromptText("SSH password");
            TextArea textArea = new TextArea();
            textArea.setPromptText("notes");

            VBox root = new VBox(6, textField, passwordField, textArea);
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();

            assertPrompt(textField, "SSH username", "TextField", false);
            assertPrompt(passwordField, "SSH password", "PasswordField", false);
            assertPrompt(textArea, "notes", "TextArea", false);

            AppTheme.setDark(true);
            root.applyCss();
            root.layout();
            assertPrompt(textField, "SSH username", "TextField", true);
            assertPrompt(passwordField, "SSH password", "PasswordField", true);
            assertPrompt(textArea, "notes", "TextArea", true);

            AppTheme.setDark(false);
        });
    }

    private static void assertPrompt(Region field, String prompt, String name, boolean dark) {
        javafx.scene.text.Text promptNode = null;
        for (Node node : field.lookupAll(".text")) {
            if (node instanceof javafx.scene.text.Text text && prompt.equals(text.getText())) {
                promptNode = text;
                break;
            }
        }
        assertNotNull(promptNode, name + " prompt text node must exist");
        Color fg = promptNode.getFill() instanceof Color c ? c : null;
        assertNotNull(fg, name + " prompt fill must be a colour");

        // Prompt text sits on the inner background (the last opaque fill), not on
        // the outer border/shadow layers.
        Color bg = innerBackground(field, dark);
        assertNotNull(bg, name + " inner background must be resolvable");
        double ratio = contrastRatio(fg, bg);
        assertTrue(ratio >= MIN_CONTRAST,
                String.format("%s prompt text contrast too low in %s mode: %.2f:1 (fg=%s bg=%s)",
                        name, dark ? "dark" : "light", ratio, fg, bg));
    }

    private static Color innerBackground(Region field, boolean dark) {
        Background background = field.getBackground();
        if (background != null) {
            var fills = background.getFills();
            for (int i = fills.size() - 1; i >= 0; i--) {
                Paint paint = fills.get(i).getFill();
                if (paint instanceof Color color && color.getOpacity() > 0.5) {
                    return color;
                }
            }
        }
        return dark ? Color.web("#262b30") : Color.WHITE;
    }

    private void assertAllFields(Region[] fields, String[] names, boolean dark) {
        for (int i = 0; i < fields.length; i++) {
            Region field = fields[i];
            String name = names[i];

            Color fg = textFill(field);
            assertNotNull(fg, name + " text fill must be defined");

            if (dark) {
                assertTrue(relativeLuminance(fg) > 0.5, name + " must have light text in dark mode");
            } else {
                assertTrue(relativeLuminance(fg) < 0.5, name + " must have dark text in light mode");
            }

            List<Color> backgrounds = backgroundColors(field);
            assertFalse(backgrounds.isEmpty(), name + " background must be resolvable");
            for (Color bg : backgrounds) {
                double ratio = contrastRatio(fg, bg);
                assertTrue(ratio >= MIN_CONTRAST,
                        String.format("%s text/bg contrast too low in %s mode: %.2f:1 (fg=%s bg=%s)",
                                name, dark ? "dark" : "light", ratio, fg, bg));
            }
        }
    }

    private static Color textFill(Region field) {
        Node node = field.lookup(".text");
        if (node instanceof Text text && text.getFill() instanceof Color color) {
            return color;
        }
        return null;
    }

    private static List<Color> backgroundColors(Region field) {
        List<Color> result = new ArrayList<>();
        Background background = field.getBackground();
        if (background == null) {
            return result;
        }
        for (var fill : background.getFills()) {
            Paint paint = fill.getFill();
            if (paint instanceof Color color) {
                result.add(color);
            } else if (paint instanceof LinearGradient gradient) {
                for (Stop stop : gradient.getStops()) {
                    result.add(stop.getColor());
                }
            }
        }
        return result;
    }

    private static double contrastRatio(Color a, Color b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    private static double relativeLuminance(Color color) {
        return 0.2126 * channel(color.getRed())
                + 0.7152 * channel(color.getGreen())
                + 0.0722 * channel(color.getBlue());
    }

    private static double channel(double value) {
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
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
