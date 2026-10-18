package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class StatusBarMemoryBarTest {

    private Parent root;
    private MainController controller;
    private Stage stage;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("the status bar memory bar renders with a visible height")
    void memoryBarRendersWithAVisibleHeight() {
        Platform.runLater(() -> {
            root.applyCss();
            root.layout();

            ProgressBar bar = (ProgressBar) field("memoryBar");
            assertNotNull(bar);
            assertTrue(bar.getHeight() >= 8,
                    "The memory progress bar must be visible, was height " + bar.getHeight());
            Node fill = bar.lookup(".bar");
            assertNotNull(fill, "the progress bar must render its fill node");
            assertTrue(fill.getLayoutBounds().getHeight() > 0,
                    "the progress fill must have a visible height, was " + fill.getLayoutBounds().getHeight());
            assertTrue(bar.getStyleClass().stream().anyMatch(styleClass -> styleClass.startsWith("memory-")),
                    "the memory state style must still be applied");
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Object field(String name) {
        try {
            Field field = MainController.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(controller);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
