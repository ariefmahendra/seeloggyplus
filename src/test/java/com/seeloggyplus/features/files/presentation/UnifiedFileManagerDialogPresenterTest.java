package com.seeloggyplus.features.files.presentation;

import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.servers.ServerCatalog;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.DialogPresenter;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class UnifiedFileManagerDialogPresenterTest {

    private Parent root;
    private UnifiedFileManagerDialogController controller;
    private final RecordingCatalog catalog = new RecordingCatalog();
    private final FakePresenter presenter = new FakePresenter();

    @Start
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
        root = loader.load();
        controller = loader.getController();
        controller.setServerCatalog(catalog);
        controller.setDialogPresenter(presenter);
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("New group creates the trimmed name inside the selected parent")
    void newGroupPromptCreatesTheTrimmedGroup() {
        presenter.responder = dialog -> Optional.of("  Team  ");

        invoke("promptNewGroup", new Class<?>[]{String.class}, "Parent");

        assertEquals(List.of("Parent/Team"), catalog.created);
        assertInstanceOf(TextInputDialog.class, presenter.lastDialog());
    }

    @Test
    @DisplayName("New group ignores blank and cancelled input")
    void newGroupPromptIgnoresBlankInput() {
        presenter.responder = dialog -> Optional.of("   ");
        invoke("promptNewGroup", new Class<?>[]{String.class}, (String) null);
        presenter.responder = dialog -> Optional.empty();
        invoke("promptNewGroup", new Class<?>[]{String.class}, (String) null);

        assertTrue(catalog.created.isEmpty());
    }

    @Test
    @DisplayName("Rename group moves the group under the same parent and ignores the old name")
    void renameGroupPromptRenamesWithinTheParent() {
        presenter.responder = dialog -> Optional.of("Renamed");
        invoke("promptRenameGroup", new Class<?>[]{String.class}, "Parent/Old");
        assertEquals(List.of("Parent/Old->Parent/Renamed"), catalog.renamed);

        presenter.responder = dialog -> Optional.of("Renamed");
        invoke("promptRenameGroup", new Class<?>[]{String.class}, "Parent/Renamed");
        assertEquals(1, catalog.renamed.size());
    }

    @Test
    @DisplayName("Delete group asks for confirmation before deleting")
    void deleteGroupPromptDeletesOnlyOnConfirmation() {
        presenter.responder = dialog -> Optional.of(ButtonType.OK);
        invoke("promptDeleteGroup", new Class<?>[]{String.class}, "Gone");
        assertEquals(List.of("Gone"), catalog.deleted);
        assertInstanceOf(Alert.class, presenter.lastDialog());

        presenter.responder = dialog -> Optional.empty();
        invoke("promptDeleteGroup", new Class<?>[]{String.class}, "Kept");
        assertEquals(List.of("Gone"), catalog.deleted);
    }

    @Test
    @DisplayName("Move to group saves the chosen group and skips the current one")
    void moveServerToGroupMovesTheChosenGroup() {
        SSHServerModel server = new SSHServerModel("Alpha", "alpha.example.com", 22, "user");
        server.setId("srv-a");
        onFx(() -> {
            catalog.servers.add(server);
            controller.rebuildLocationTree();
        });

        presenter.responder = dialog -> Optional.of("prod");
        invoke("promptMoveServerToGroup", new Class<?>[]{SSHServerModel.class}, server);
        assertEquals(List.of("srv-a:prod"), catalog.moves());
        assertInstanceOf(ChoiceDialog.class, presenter.lastDialog());

        presenter.responder = dialog -> Optional.of("prod");
        invoke("promptMoveServerToGroup", new Class<?>[]{SSHServerModel.class}, server);
        assertEquals(1, catalog.moves().size());

        presenter.responder = dialog -> Optional.of("(Ungrouped)");
        invoke("promptMoveServerToGroup", new Class<?>[]{SSHServerModel.class}, server);
        assertEquals(List.of("srv-a:prod", "srv-a:null"), catalog.moves());
    }

    @Test
    @DisplayName("Error dialogs are presented through the seam")
    void errorDialogsArePresentedThroughThePresenter() {
        invoke("showError", new Class<?>[]{String.class, String.class}, "Group Error", "boom");

        awaitFx(() -> presenter.dialogs.stream().anyMatch(d -> d instanceof Alert));
        Alert alert = (Alert) presenter.dialogs.stream()
                .filter(d -> d instanceof Alert).findFirst().orElseThrow();
        assertEquals("Group Error", alert.getTitle());
        assertEquals("boom", alert.getContentText());
    }

    @Test
    @DisplayName("Connection failures use the feedback alert through the seam")
    void connectionErrorsUseTheFeedbackAlert() {
        SSHServerModel server = new SSHServerModel("Alpha", "alpha.example.com", 22, "user");
        server.setId("srv-a");
        invoke("showConnectionError", new Class<?>[]{SSHServerModel.class, String.class}, server,
                "Authentication failed");

        awaitFx(() -> presenter.dialogs.stream().anyMatch(d -> d instanceof Alert));
        Alert alert = (Alert) presenter.dialogs.stream()
                .filter(d -> d instanceof Alert).findFirst().orElseThrow();
        assertEquals("Connection problem", alert.getTitle());
        assertEquals("Unable to sign in to server", alert.getHeaderText());
        assertTrue(alert.getContentText().contains("rejected the login"));
        assertTrue(((javafx.scene.control.TextArea) alert.getDialogPane().getExpandableContent())
                .getText().contains("Authentication failed"));
    }

    private void invoke(String name, Class<?>[] types, Object... args) {
        onFx(() -> {
            try {
                Method method = UnifiedFileManagerDialogController.class.getDeclaredMethod(name, types);
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

    private static final class FakePresenter implements DialogPresenter {
        final List<Dialog<?>> dialogs = new ArrayList<>();
        Function<Dialog<?>, Optional<?>> responder = dialog -> Optional.empty();

        @Override
        public void present(Stage dialog) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> present(Dialog<T> dialog) {
            dialogs.add(dialog);
            return (Optional<T>) responder.apply(dialog);
        }

        Dialog<?> lastDialog() {
            return dialogs.get(dialogs.size() - 1);
        }
    }

    private static final class RecordingCatalog implements ServerCatalog {
        final List<SSHServerModel> servers = new ArrayList<>();
        final List<String> groups = new ArrayList<>();
        final List<String> created = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();
        final List<String> renamed = new ArrayList<>();
        final List<String> saves = new ArrayList<>();

        List<String> moves() {
            return saves;
        }

        @Override
        public List<SSHServerModel> getAllServers() {
            return List.copyOf(servers);
        }

        @Override
        public SSHServerModel getServerById(String id) {
            return servers.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public void saveServer(SSHServerModel server) {
            saves.add(server.getId() + ":" + server.getGroupName());
        }

        @Override
        public void reorderServers(List<String> orderedIds) {
        }

        @Override
        public List<String> getGroupNames() {
            return List.copyOf(groups);
        }

        @Override
        public void createGroup(String name) {
            created.add(name);
            groups.add(name);
        }

        @Override
        public void renameGroup(String oldName, String newName) {
            renamed.add(oldName + "->" + newName);
        }

        @Override
        public void deleteGroup(String name) {
            deleted.add(name);
        }

        @Override
        public void updateServerLastUsed(String serverId) {
        }
    }
}
