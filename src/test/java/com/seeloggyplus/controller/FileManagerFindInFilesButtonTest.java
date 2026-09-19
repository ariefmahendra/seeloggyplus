package com.seeloggyplus.controller;

import com.seeloggyplus.model.SSHServerModel;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the "Find in Files" button state: available only on remote server locations.
 */
@ExtendWith(ApplicationExtension.class)
public class FileManagerFindInFilesButtonTest {

    private UnifiedFileManagerDialogController controller;
    private Button findInFilesButton;

    @Start
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(root));
        stage.show();

        Field field = UnifiedFileManagerDialogController.class.getDeclaredField("findInFilesButton");
        field.setAccessible(true);
        findInFilesButton = (Button) field.get(controller);
    }

    private void setLocation(Object location) throws Exception {
        Field field = UnifiedFileManagerDialogController.class.getDeclaredField("currentLocation");
        field.setAccessible(true);
        field.set(controller, location);
        Method update = UnifiedFileManagerDialogController.class.getDeclaredMethod("updateFindInFilesState");
        update.setAccessible(true);
        update.invoke(controller);
    }

    private Object remoteLocation() throws Exception {
        Class<?> locationClass = Class.forName(
                "com.seeloggyplus.controller.UnifiedFileManagerDialogController$LocationItem");
        Constructor<?> constructor = locationClass.getDeclaredConstructor(
                String.class, FontAwesomeIcon.class, SSHServerModel.class);
        constructor.setAccessible(true);
        SSHServerModel server = new SSHServerModel("Prod", "10.0.0.1", 22, "user");
        server.setId("srv-location");
        return constructor.newInstance("Prod", FontAwesomeIcon.SERVER, server);
    }

    /**
     * Cancels the async directory load triggered by FXML initialization so it cannot
     * write preferences or update UI mid-test (keeps test data clean and deterministic).
     */
    private void cancelPendingLoadTask() throws Exception {
        Field taskField = UnifiedFileManagerDialogController.class.getDeclaredField("currentLoadTask");
        taskField.setAccessible(true);
        javafx.concurrent.Task<?> task = (javafx.concurrent.Task<?>) taskField.get(controller);
        if (task == null) return;
        WaitForAsyncUtils.asyncFx(() -> task.cancel(true)).get();
        long deadline = System.currentTimeMillis() + 3000;
        while (Boolean.TRUE.equals(WaitForAsyncUtils.asyncFx(task::isRunning).get())
                && System.currentTimeMillis() < deadline) {
            WaitForAsyncUtils.waitForFxEvents();
            Thread.sleep(25);
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void buttonIsDisabledForLocalAndEnabledForRemoteLocation() throws Exception {
        cancelPendingLoadTask();

        Platform.runLater(() -> {
            try {
                setLocation(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(findInFilesButton.isDisabled(), "Local location must disable Find in Files");

        Platform.runLater(() -> {
            try {
                setLocation(remoteLocation());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(findInFilesButton.isDisabled(), "Remote server location must enable Find in Files");

        Platform.runLater(() -> {
            try {
                setLocation(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(findInFilesButton.isDisabled(), "Clearing the location must disable Find in Files");
    }
}
