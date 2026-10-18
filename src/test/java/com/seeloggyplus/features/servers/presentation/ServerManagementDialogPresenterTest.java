package com.seeloggyplus.features.servers.presentation;

import com.seeloggyplus.features.servers.application.ServerManagementService;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.DialogPresenter;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class ServerManagementDialogPresenterTest {

    private Parent root;
    private Stage stage;
    private ServerManagementDialogController controller;
    private final FakePresenter presenter = new FakePresenter();

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerManagementDialog.fxml"));
        root = loader.load();
        controller = loader.getController();
        controller.setDialogPresenter(presenter);
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("confirming the delete dialog removes the server and reloads the list")
    void confirmingDeleteRemovesTheServer() throws Exception {
        FakeServerService service = new FakeServerService(List.of(server("Prod", "prod.example.com")));
        installService(service);
        populate(service.servers);
        selectFirstRow();
        presenter.dialogResult = Optional.of(ButtonType.OK);

        onFx(() -> button("deleteServerButton").fire());

        assertEquals(List.of("id-Prod"), service.deleted);
        assertEquals(1, presenter.alerts.size());
        assertEquals("Delete Server", presenter.alerts.get(0).getTitle());
        awaitFx(() -> service.getAllCalls.get() >= 1);
        assertEquals(0, serverTable().getItems().size());
    }

    @Test
    @DisplayName("cancelling the delete dialog keeps the server")
    void cancellingDeleteKeepsTheServer() throws Exception {
        FakeServerService service = new FakeServerService(List.of(server("Prod", "prod.example.com")));
        installService(service);
        populate(service.servers);
        selectFirstRow();
        presenter.dialogResult = Optional.empty();

        onFx(() -> button("deleteServerButton").fire());

        assertTrue(service.deleted.isEmpty());
        assertEquals(1, presenter.alerts.size());
        assertEquals(0, service.getAllCalls.get());
    }

    @Test
    @DisplayName("delete never asks when no row is selected")
    void deleteWithoutSelectionDoesNothing() throws Exception {
        FakeServerService service = new FakeServerService(List.of(server("Prod", "prod.example.com")));
        installService(service);
        populate(service.servers);

        onFx(() -> button("deleteServerButton").fire());

        assertTrue(presenter.alerts.isEmpty());
        assertTrue(service.deleted.isEmpty());
    }

    @Test
    @DisplayName("the add dialog saves through the real editor when the presenter drives it")
    void addDialogSavesThroughTheEditor() throws Exception {
        FakeServerService service = new FakeServerService(new ArrayList<>());
        installService(service);
        presenter.stageAction = stage -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            ((TextField) stage.getScene().lookup("#nameField")).setText("New Server");
            ((TextField) stage.getScene().lookup("#hostField")).setText("new.example.com");
            ((TextField) stage.getScene().lookup("#usernameField")).setText("newuser");
            ((Button) stage.getScene().lookup("#saveButton")).fire();
        };

        onFx(() -> button("addServerButton").fire());

        assertEquals(List.of("Add SSH Server"), presenter.stageTitles);
        assertEquals(1, service.saved.size());
        assertEquals("New Server", service.saved.get(0).getName());
        assertEquals("new.example.com", service.saved.get(0).getHost());
        awaitFx(() -> service.getAllCalls.get() >= 1);
    }

    @Test
    @DisplayName("the edit dialog saves the selected server in place")
    void editDialogSavesTheSelectedServer() throws Exception {
        FakeServerService service = new FakeServerService(List.of(server("Prod", "prod.example.com")));
        installService(service);
        populate(service.servers);
        selectFirstRow();
        presenter.stageAction = stage -> ((Button) stage.getScene().lookup("#saveButton")).fire();

        onFx(() -> button("editServerButton").fire());

        assertEquals(List.of("Edit SSH Server"), presenter.stageTitles);
        assertEquals(1, service.saved.size());
        assertEquals("id-Prod", service.saved.get(0).getId());
        awaitFx(() -> service.getAllCalls.get() >= 1);
    }

    @Test
    @DisplayName("the clone dialog saves a copy of the selected server")
    void cloneDialogSavesACopy() throws Exception {
        FakeServerService service = new FakeServerService(List.of(server("Prod", "prod.example.com")));
        installService(service);
        populate(service.servers);
        selectFirstRow();
        presenter.stageAction = stage -> ((Button) stage.getScene().lookup("#saveButton")).fire();

        onFx(() -> button("cloneServerButton").fire());

        assertEquals(1, service.saved.size());
        assertEquals("prod.example.com", service.saved.get(0).getHost());
        awaitFx(() -> service.getAllCalls.get() >= 1);
    }

    @Test
    @DisplayName("a failed reload reports the error through the presenter")
    void loadFailureShowsAnErrorAlert() throws Exception {
        FakeServerService service = new FakeServerService(new ArrayList<>());
        service.failLoads = true;
        installService(service);

        onFx(() -> button("refreshButton").fire());

        awaitFx(() -> !presenter.alerts.isEmpty());
        assertEquals("Load Error", presenter.alerts.get(0).getHeaderText());
        assertEquals("Error", presenter.alerts.get(0).getTitle());
    }

    private void installService(ServerManagementService service) throws Exception {
        Field field = ServerManagementDialogController.class.getDeclaredField("serverService");
        field.setAccessible(true);
        field.set(controller, service);
    }

    private void selectFirstRow() {
        onFx(() -> serverTable().getSelectionModel().select(0));
    }

    private void populate(List<SSHServerModel> servers) throws Exception {
        onFx(() -> {
            try {
                ObservableList<SSHServerModel> all = field("allServers");
                all.setAll(servers);
                Method filter = ServerManagementDialogController.class.getDeclaredMethod("filterServers", String.class);
                filter.setAccessible(true);
                filter.invoke(controller, (String) null);
            } catch (Exception e) {
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
    private TableView<SSHServerModel> serverTable() {
        return (TableView<SSHServerModel>) root.lookup("#serverTable");
    }

    private Button button(String id) {
        return (Button) root.lookup("#" + id);
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) throws Exception {
        Field field = ServerManagementDialogController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(controller);
    }

    private SSHServerModel server(String name, String host) {
        SSHServerModel server = new SSHServerModel(name, host, 22, "deploy");
        server.setId("id-" + name);
        return server;
    }

    private static final class FakePresenter implements DialogPresenter {
        final List<String> stageTitles = new ArrayList<>();
        final List<Alert> alerts = new ArrayList<>();
        Consumer<Stage> stageAction = stage -> { };
        Optional<ButtonType> dialogResult = Optional.empty();

        @Override
        public void present(Stage dialog) {
            stageTitles.add(dialog.getTitle());
            stageAction.accept(dialog);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> present(Dialog<T> dialog) {
            if (dialog instanceof Alert alert) {
                alerts.add(alert);
            }
            return (Optional<T>) dialogResult;
        }
    }

    private static final class FakeServerService implements ServerManagementService {
        private final List<SSHServerModel> servers = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();
        private final List<SSHServerModel> saved = new ArrayList<>();
        private final AtomicInteger getAllCalls = new AtomicInteger();
        private volatile boolean failLoads = false;

        FakeServerService(List<SSHServerModel> servers) {
            this.servers.addAll(servers);
        }

        @Override
        public List<SSHServerModel> getAllServers() {
            getAllCalls.incrementAndGet();
            if (failLoads) {
                throw new IllegalStateException("database unavailable");
            }
            return List.copyOf(servers);
        }

        @Override
        public SSHServerModel getServerById(String id) {
            return servers.stream().filter(s -> id.equals(s.getId())).findFirst().orElse(null);
        }

        @Override
        public void saveServer(SSHServerModel server) {
            saved.add(server);
            servers.removeIf(s -> s.getId().equals(server.getId()));
            servers.add(server);
        }

        @Override
        public void deleteServer(String id) {
            deleted.add(id);
            servers.removeIf(s -> s.getId().equals(id));
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
