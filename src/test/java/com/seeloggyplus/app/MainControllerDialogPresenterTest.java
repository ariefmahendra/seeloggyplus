package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.DialogPresenter;
import com.seeloggyplus.shared.util.RemoteLogPaths;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class MainControllerDialogPresenterTest {

    private Parent root;
    private MainController controller;
    private final FakePresenter presenter = new FakePresenter();

    @Start
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        root = loader.load();
        controller = loader.getController();
        controller.setDialogPresenter(presenter);
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("showError presents a themed error alert through the seam")
    void showErrorPresentsTheAlert() {
        invoke("showError", new Class<?>[]{String.class, String.class}, "Tail Info", "boom");

        awaitFx(() -> !presenter.dialogs.isEmpty());
        Alert alert = (Alert) presenter.dialogs.get(0);
        assertEquals("Error", alert.getTitle());
        assertEquals("Tail Info", alert.getHeaderText());
        assertEquals("boom", alert.getContentText());
    }

    @Test
    @DisplayName("showInfo presents an information alert through the seam")
    void showInfoPresentsTheAlert() {
        invoke("showInfo", new Class<?>[]{String.class, String.class}, "Tail Info", "done");

        awaitFx(() -> !presenter.dialogs.isEmpty());
        Alert alert = (Alert) presenter.dialogs.get(0);
        assertEquals("Information", alert.getTitle());
        assertEquals("Tail Info", alert.getHeaderText());
    }

    @Test
    @DisplayName("a Windows-style remote path error carries the path in its details")
    void remotePathErrorCarriesThePath() {
        invoke("showRemotePathError", new Class<?>[]{String.class}, "C:\\logs\\app.log");

        awaitFx(() -> !presenter.dialogs.isEmpty());
        Alert alert = (Alert) presenter.dialogs.get(0);
        assertEquals("Unable to monitor log", alert.getTitle());
        assertEquals(RemoteLogPaths.LOCAL_LOCATION_MESSAGE, alert.getContentText());
        TextArea details = (TextArea) alert.getDialogPane().getExpandableContent();
        assertTrue(details.getText().contains("C:\\logs\\app.log"));
    }

    @Test
    @DisplayName("a blank remote path reports the missing location instead")
    void blankRemotePathReportsMissingLocation() {
        invoke("showRemotePathError", new Class<?>[]{String.class}, "  ");

        awaitFx(() -> !presenter.dialogs.isEmpty());
        Alert alert = (Alert) presenter.dialogs.get(0);
        assertEquals(RemoteLogPaths.MISSING_LOCATION_MESSAGE, alert.getContentText());
        TextArea details = (TextArea) alert.getDialogPane().getExpandableContent();
        assertTrue(details.getText().contains("Not saved"));
    }

    @Test
    @DisplayName("Go to Line consumes the typed number through the seam")
    void goToLineConsumesTheTypedNumber() throws Exception {
        ToggleButton follow = field("followTailButton");
        onFx(() -> follow.setSelected(true));
        setField("canvasLogViewer", new com.seeloggyplus.shared.ui.canvas.CanvasLogViewer());
        presenter.result = Optional.of("42");

        onFx(controller::handleGoToLine);

        assertEquals("Go to Line", ((TextInputDialog) presenter.lastDialog()).getTitle());
        onFx(() -> assertFalse(follow.isSelected()));
    }

    @Test
    @DisplayName("a controller outside a window still presents dialogs through the seam")
    void controllerWithoutAWindowStillPresentsDialogs() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        loader.load();
        MainController detached = loader.getController();
        FakePresenter detachedPresenter = new FakePresenter();
        detached.setDialogPresenter(detachedPresenter);

        Method showError = MainController.class.getDeclaredMethod("showError", String.class, String.class);
        showError.setAccessible(true);
        onFx(() -> {
            try {
                showError.invoke(detached, "Title", "message");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });

        awaitFx(() -> !detachedPresenter.dialogs.isEmpty());
        assertEquals("Title", ((Alert) detachedPresenter.dialogs.get(0)).getHeaderText());
    }

    private void setField(String name, Object value) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private void invoke(String name, Class<?>[] types, Object... args) {
        onFx(() -> {
            try {
                Method method = MainController.class.getDeclaredMethod(name, types);
                method.setAccessible(true);
                method.invoke(controller, args);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    private void onFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get();
            WaitForAsyncUtils.waitForFxEvents();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void awaitFx(BooleanSupplier condition) {
        try {
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> {
                try {
                    return WaitForAsyncUtils.asyncFx(condition::getAsBoolean).get(2, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return false;
                }
            });
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("condition was not met within 5 seconds", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) {
        try {
            Field field = MainController.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(controller);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class FakePresenter implements DialogPresenter {
        private final List<Dialog<?>> dialogs = new ArrayList<>();
        private Optional<String> result = Optional.empty();

        Dialog<?> lastDialog() {
            return dialogs.get(dialogs.size() - 1);
        }

        @Override
        public void present(Stage dialog) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> present(Dialog<T> dialog) {
            dialogs.add(dialog);
            return (Optional<T>) result;
        }
    }
}
