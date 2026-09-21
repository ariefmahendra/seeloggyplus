package com.seeloggyplus.controller;

import com.seeloggyplus.util.AppTheme;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ScrollEvent;
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
 * Defect fix: horizontal scroll (or Shift+wheel) over the tab header moves the
 * selected tab left/right on the main view.
 */
@ExtendWith(ApplicationExtension.class)
class TabScrollSwitchTest {

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
        stage.setWidth(1000);
        stage.setHeight(640);
        stage.show();
    }

    @AfterEach
    void resetTheme() {
        onFxThread(() -> AppTheme.setDark(false));
    }

    @Test
    void horizontalScrollOnHeaderSwitchesTabs() {
        onFxThread(() -> {
            TabPane pane = logTabPane();
            pane.getTabs().setAll(new Tab("One"), new Tab("Two"), new Tab("Three"));
            pane.getSelectionModel().select(0);
            root.applyCss();
            root.layout();

            Node header = pane.lookup(".tab-header-area");
            assertNotNull(header, "tab header area must be available");

            Event.fireEvent(header, scrollEvent(40, 0, false));
            assertEquals(1, pane.getSelectionModel().getSelectedIndex(),
                    "scroll right must move to the next tab");

            Event.fireEvent(header, scrollEvent(-40, 0, false));
            assertEquals(0, pane.getSelectionModel().getSelectedIndex(),
                    "scroll left must move back to the previous tab");

            Event.fireEvent(header, scrollEvent(0, 40, true));
            assertEquals(1, pane.getSelectionModel().getSelectedIndex(),
                    "Shift+wheel over the header must move to the next tab");
        });
    }

    private TabPane logTabPane() {
        try {
            Field field = MainController.class.getDeclaredField("logTabPane");
            field.setAccessible(true);
            return (TabPane) field.get(controller);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static ScrollEvent scrollEvent(double deltaX, double deltaY, boolean shiftDown) {
        return new ScrollEvent(ScrollEvent.SCROLL,
                0, 0, 0, 0,
                shiftDown, false, false, false,
                false, false,
                deltaX, deltaY, deltaX, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0, null);
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
