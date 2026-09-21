package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Defect fix: toolbar toggle icons (Tail, Smart Follow) must keep the theme
 * colour in every state — the previous programmatic fill sometimes lingered and
 * looked off-colour.
 */
@ExtendWith(ApplicationExtension.class)
class ToolbarToggleIconThemeTest {

    private static final Color CHROME_TEXT = Color.web("#d7dbe0");

    private Parent root;
    private MainController controller;
    private Stage stage;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        root = loader.load();
        controller = loader.getController();
        Scene scene = AppTheme.scene(root);
        stage.setScene(scene);
        stage.setWidth(1100);
        stage.setHeight(720);
        stage.show();
    }

    @AfterEach
    void resetTheme() {
        onFxThread(() -> AppTheme.setDark(false));
    }

    @Test
    void toolbarToggleIconsUseThemeColourInAllStates() {
        onFxThread(() -> {
            ToggleButton tail = toggle("tailButton");
            ToggleButton follow = toggle("followTailButton");

            for (boolean dark : new boolean[] {false, true}) {
                AppTheme.setDark(dark);
                String mode = dark ? "dark" : "light";

                assertIconColour(tail, false, mode);
                assertIconColour(follow, false, mode);
                assertIconColour(tail, true, mode);
                assertIconColour(follow, true, mode);
            }

            AppTheme.setDark(false);
        });
    }

    private void assertIconColour(ToggleButton button, boolean selected, String mode) {
        button.setSelected(selected);
        root.applyCss();
        root.layout();
        WaitForAsyncUtils.waitForFxEvents();

        FontAwesomeIconView icon = (FontAwesomeIconView) button.getGraphic();
        assertNotNull(icon, "toolbar toggle must have an icon");
        assertEquals(CHROME_TEXT, icon.getFill(),
                "toolbar toggle icon must use the chrome text colour (" + mode
                        + ", selected=" + selected + "), was " + icon.getFill());
    }

    private ToggleButton toggle(String fieldName) {
        try {
            Field field = MainController.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (ToggleButton) field.get(controller);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
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
