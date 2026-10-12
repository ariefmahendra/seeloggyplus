package com.seeloggyplus.features.servers.presentation;

import com.seeloggyplus.features.servers.application.ServerManagementService;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class ServerManagementDialogBehaviorTest {

    private Parent root;
    private Stage stage;
    private ServerManagementDialogController controller;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerManagementDialog.fxml"));
        root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();
        Thread.sleep(600);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void searchFiltersServersAcrossAllDisplayedFields() throws Exception {
        populate(List.of(
                server("Prod DB", "db.internal", "deploy", "database", "/var/log/db"),
                server("Web", "web.example.com", "webuser", "frontend", "/srv/www"),
                server("Stage", "stage.example.com", "deploy", null, null)));

        onFx(() -> searchField().setText("database"));
        assertEquals(List.of("Prod DB"), visibleNames());

        onFx(() -> searchField().setText("WEB.EXAMPLE"));
        assertEquals(List.of("Web"), visibleNames());

        onFx(() -> searchField().setText("deploy"));
        assertEquals(List.of("Prod DB", "Stage"), visibleNames());

        onFx(() -> searchField().setText("/srv"));
        assertEquals(List.of("Web"), visibleNames());

        onFx(() -> searchField().setText("   "));
        assertEquals(3, visibleNames().size());
    }

    @Test
    void clearSearchButtonRestoresEveryServer() throws Exception {
        populate(List.of(
                server("Prod", "prod.example.com", "deploy", null, null),
                server("Dev", "dev.example.com", "deploy", null, null)));
        onFx(() -> searchField().setText("prod"));
        assertEquals(List.of("Prod"), visibleNames());

        onFx(() -> button("clearSearchButton").fire());

        assertEquals(2, visibleNames().size());
    }

    @Test
    void selectionUpdatesTheDetailsPanelAndButtonStates() throws Exception {
        populate(List.of(server("Prod", "prod.example.com", "deploy", null, "/var/log")));
        TableView<SSHServerModel> table = serverTable();

        assertTrue(button("editServerButton").isDisable());
        assertTrue(button("deleteServerButton").isDisable());
        assertEquals("-", label("detailNameLabel").getText());

        onFx(() -> table.getSelectionModel().select(0));

        assertEquals("Prod", label("detailNameLabel").getText());
        assertEquals("prod.example.com", label("detailHostLabel").getText());
        assertEquals("deploy", label("detailUsernameLabel").getText());
        assertEquals("22", label("detailPortLabel").getText());
        assertEquals("/var/log", label("detailPathLabel").getText());
        assertEquals("Never", label("detailLastUsedLabel").getText());
        assertFalse(button("editServerButton").isDisable());
        assertFalse(button("deleteServerButton").isDisable());

        onFx(() -> table.getSelectionModel().clearSelection());

        assertEquals("-", label("detailNameLabel").getText());
        assertTrue(button("editServerButton").isDisable());
    }

    @Test
    void columnsRenderFallbacksForMissingValues() throws Exception {
        SSHServerModel bare = new SSHServerModel();
        bare.setHost("host");
        bare.setUsername("user");
        bare.setPort(2222);

        assertEquals("-", cellValue("nameColumn", bare));
        assertEquals("host", cellValue("hostColumn", bare));
        assertEquals("2222", cellValue("portColumn", bare));
        assertEquals("/", cellValue("defaultPathColumn", bare));
        assertEquals("-", cellValue("lastUsedColumn", bare));

        SSHServerModel rich = server("Prod", "prod.example.com", "deploy", null, "/var/log");
        rich.setLastUsed(LocalDateTime.of(2026, 10, 13, 9, 30));
        assertEquals("2026-10-13 09:30", cellValue("lastUsedColumn", rich));
    }

    @Test
    void closeButtonClosesTheDialog() {
        assertTrue(stage.isShowing());

        onFx(() -> button("closeButton").fire());

        assertFalse(stage.isShowing());
        onFx(stage::show);
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

    private List<String> visibleNames() {
        return serverTable().getItems().stream().map(SSHServerModel::getName).toList();
    }

    private SSHServerModel server(String name, String host, String user, String group, String path) {
        SSHServerModel server = new SSHServerModel(name, host, 22, user);
        server.setId("id-" + name);
        server.setGroupName(group);
        server.setDefaultPath(path);
        return server;
    }

    private void onFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get();
            WaitForAsyncUtils.waitForFxEvents();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private TableView<SSHServerModel> serverTable() {
        return (TableView<SSHServerModel>) root.lookup("#serverTable");
    }

    private TextField searchField() {
        return (TextField) root.lookup("#searchField");
    }

    private Button button(String id) {
        return (Button) root.lookup("#" + id);
    }

    private Label label(String id) {
        return (Label) root.lookup("#" + id);
    }

    @SuppressWarnings("unchecked")
    private String cellValue(String columnId, SSHServerModel model) throws Exception {
        TableColumn<SSHServerModel, String> column = (TableColumn<SSHServerModel, String>) field(columnId);
        AtomicReference<String> value = new AtomicReference<>();
        onFx(() -> {
            ObservableValue<String> observable = column.getCellObservableValue(model);
            value.set(observable == null ? null : observable.getValue());
        });
        return value.get();
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) throws Exception {
        Field field = ServerManagementDialogController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(controller);
    }

    private static class FakeServerService implements ServerManagementService {
        private final List<SSHServerModel> servers;

        FakeServerService(List<SSHServerModel> servers) {
            this.servers = servers;
        }

        @Override
        public List<SSHServerModel> getAllServers() {
            return servers;
        }

        @Override
        public SSHServerModel getServerById(String id) {
            return servers.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public void saveServer(SSHServerModel server) {
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
