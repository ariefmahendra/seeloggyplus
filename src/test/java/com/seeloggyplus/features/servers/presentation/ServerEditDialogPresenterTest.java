package com.seeloggyplus.features.servers.presentation;

import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.features.servers.application.ServerManagementService;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.DialogPresenter;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class ServerEditDialogPresenterTest {

    private Parent root;
    private Stage stage;
    private ServerEditDialogController controller;
    private final FakePresenter presenter = new FakePresenter();
    private final FakeServerService service = new FakeServerService();

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerEditDialog.fxml"));
        root = loader.load();
        controller = loader.getController();
        controller.setDialogPresenter(presenter);
        controller.setServerService(service);
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("saving key auth without a key path warns and keeps the dialog open")
    void savingKeyAuthWithoutAKeyWarns() {
        onFx(() -> {
            textField("hostField").setText("prod.example.com");
            textField("portField").setText("22");
            textField("usernameField").setText("deploy");
            choice("authTypeChoice").getSelectionModel().select("Private key");
            button("saveButton").fire();
        });

        assertEquals(1, presenter.dialogs.size());
        Alert alert = (Alert) presenter.dialogs.get(0);
        assertEquals("Private Key Required", alert.getTitle());
        assertFalse(controller.isSaved());
        assertTrue(service.saved.isEmpty());
        assertTrue(stage.isShowing(), "the dialog must stay open after the warning");
    }

    @Test
    @DisplayName("testing key auth without a key path warns before touching SSH")
    void testingKeyAuthWithoutAKeyWarns() {
        AtomicInteger factoryCalls = new AtomicInteger();
        controller.setSshServiceFactory(() -> {
            factoryCalls.incrementAndGet();
            return new SSHServiceImpl();
        });

        onFx(() -> {
            textField("hostField").setText("prod.example.com");
            textField("portField").setText("22");
            textField("usernameField").setText("deploy");
            choice("authTypeChoice").getSelectionModel().select("Private key");
            button("testButton").fire();
        });

        assertEquals(1, presenter.dialogs.size());
        Alert alert = (Alert) presenter.dialogs.get(0);
        assertEquals("Private Key Required", alert.getTitle());
        assertEquals(0, factoryCalls.get(), "the connection test must not run without a key");
    }

    @Test
    @DisplayName("a successful connection test reports success through the presenter")
    void successfulConnectionTestShowsTheSuccessAlert() {
        controller.setSshServiceFactory(() -> new SSHServiceImpl() {
            @Override
            public boolean connect(String host, int port, String username, String password) {
                return true;
            }
        });

        onFx(() -> {
            textField("hostField").setText("prod.example.com");
            textField("portField").setText("22");
            textField("usernameField").setText("deploy");
            textField("passwordField").setText("secret");
            button("testButton").fire();
        });

        awaitFx(() -> !presenter.dialogs.isEmpty());
        Alert alert = (Alert) presenter.dialogs.get(0);
        assertEquals("Test Result", alert.getTitle());
        assertEquals("Connection Successful", alert.getHeaderText());
    }

    @Test
    @DisplayName("a failed connection test reports the feedback alert through the presenter")
    void failedConnectionTestShowsTheFeedbackAlert() {
        controller.setSshServiceFactory(() -> new SSHServiceImpl() {
            @Override
            public boolean connect(String host, int port, String username, String password) {
                return false;
            }

            @Override
            public String getLastConnectError() {
                return "Authentication failed";
            }
        });

        onFx(() -> {
            textField("hostField").setText("prod.example.com");
            textField("portField").setText("22");
            textField("usernameField").setText("deploy");
            textField("passwordField").setText("secret");
            button("testButton").fire();
        });

        awaitFx(() -> !presenter.dialogs.isEmpty());
        Alert alert = (Alert) presenter.dialogs.get(0);
        assertEquals("Connection problem", alert.getTitle());
        assertEquals("Unable to sign in to server", alert.getHeaderText());
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

    private TextField textField(String id) {
        return (TextField) root.lookup("#" + id);
    }

    private Button button(String id) {
        return (Button) root.lookup("#" + id);
    }

    @SuppressWarnings("unchecked")
    private ChoiceBox<String> choice(String id) {
        return (ChoiceBox<String>) root.lookup("#" + id);
    }

    private static final class FakePresenter implements DialogPresenter {
        final List<Dialog<?>> dialogs = new ArrayList<>();

        @Override
        public void present(Stage dialog) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> present(Dialog<T> dialog) {
            dialogs.add(dialog);
            return (Optional<T>) Optional.empty();
        }
    }

    private static final class FakeServerService implements ServerManagementService {
        private final List<SSHServerModel> saved = new ArrayList<>();

        @Override
        public void saveServer(SSHServerModel server) {
            saved.add(server);
        }

        @Override
        public List<SSHServerModel> getAllServers() {
            return List.of();
        }

        @Override
        public SSHServerModel getServerById(String id) {
            return null;
        }

        @Override
        public void deleteServer(String id) {
        }

        @Override
        public void reorderServers(List<String> orderedIds) {
        }

        @Override
        public List<String> getGroupNames() {
            return List.of();
        }

        @Override
        public void createGroup(String name) {
        }

        @Override
        public void renameGroup(String oldName, String newName) {
        }

        @Override
        public void deleteGroup(String name) {
        }

        @Override
        public void updateServerLastUsed(String serverId) {
        }
    }
}
