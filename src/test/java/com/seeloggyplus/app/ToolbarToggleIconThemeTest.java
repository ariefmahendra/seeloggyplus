package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
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

@ExtendWith(ApplicationExtension.class)
class ToolbarToggleIconThemeTest {

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
                tail.setDisable(false);
                follow.setDisable(false);

                assertIconColour(tail, false, mode);
                assertIconColour(follow, false, mode);
                assertIconColour(tail, true, mode);
                assertIconColour(follow, true, mode);
                follow.setDisable(true);
                assertIconColour(follow, true, mode);
            }

            AppTheme.setDark(false);
        });
    }

    private void assertIconColour(ToggleButton button, boolean selected, String mode) {
        try {
            Field guard = MainController.class.getDeclaredField("isProgrammaticUpdate");
            guard.setAccessible(true);
            guard.setBoolean(controller, true);
            try { button.setSelected(selected); } finally { guard.setBoolean(controller, false); }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        assertEquals(selected, button.isSelected());
        root.applyCss();
        root.layout();
        WaitForAsyncUtils.waitForFxEvents();

        FontAwesomeIconView icon = (FontAwesomeIconView) button.getGraphic();
        assertNotNull(icon, "toolbar toggle must have an icon");
        Color expected = selected && !button.isDisabled()
                ? Color.web(mode.equals("dark") ? "#71a1fe" : "#2f5eb9")
                : Color.web(mode.equals("dark") ? "#d1d3d9" : "#192330");
        assertEquals(expected, icon.getFill(),
                "toolbar toggle icon must use blue only when active and enabled (" + mode
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
