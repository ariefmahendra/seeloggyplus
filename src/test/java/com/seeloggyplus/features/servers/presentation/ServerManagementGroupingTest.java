package com.seeloggyplus.features.servers.presentation;

import com.seeloggyplus.features.files.presentation.UnifiedFileManagerDialogController;
import com.seeloggyplus.features.servers.infrastructure.ServerManagementServiceImpl;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.DialogPresenter;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class ServerManagementGroupingTest {
    private Parent root;
    private Stage stage;
    private ServerManagementServiceImpl service;
    private SSHServerModel production;
    private SSHServerModel loose;
    private String group;
    private Object answer;
    private java.util.function.Consumer<Stage> stageAction = dialog -> { };
    private Alert lastError;

    @Start void start(Stage stage) throws Exception {
        this.stage = stage;
        group = "Sync-" + UUID.randomUUID();
        service = new ServerManagementServiceImpl();
        service.createGroup(group + "/Backend");
        service.createGroup(group + "/Empty");
        production = new SSHServerModel("Production", "prod.example.com", 22, "deploy");
        production.setGroupName(group + "/Backend");
        service.saveServer(production);
        loose = new SSHServerModel("Loose", "loose.example.com", 22, "deploy");
        service.saveServer(loose);
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerManagementDialog.fxml"));
        root = loader.load();
        ServerManagementDialogController controller = loader.getController();
        controller.setDialogPresenter(new DialogPresenter() {
            @Override public void present(Stage dialog) { stageAction.accept(dialog); }
            @Override @SuppressWarnings("unchecked") public <T> Optional<T> present(Dialog<T> dialog) {
                if (dialog instanceof Alert alert && alert.getAlertType() == Alert.AlertType.ERROR) {
                    lastError = alert;
                    return Optional.empty();
                }
                return Optional.ofNullable((T) answer);
            }
        });
        stage.setScene(AppTheme.scene(root)); stage.show();
    }

    @AfterEach void clean() throws Exception {
        for (SSHServerModel server : service.getAllServers()) {
            if (server.getName() != null && server.getName().startsWith(group)) service.deleteServer(server.getId());
        }
        service.deleteServer(production.getId());
        service.deleteServer(loose.getId());
        for (String path : service.getGroupNames()) {
            if (path.startsWith(group)) service.deleteGroup(path);
        }
        WaitForAsyncUtils.asyncFx(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT)).get();
    }

    @Test void sharedNestedAndEmptyGroupsFilterServersTogetherWithSearch() throws Exception {
        assertNotNull(root.lookup("#groupTree"), "server management needs the shared group tree");
        await(() -> find(tree().getRoot(), group + "/Empty") != null);
        onFx(() -> tree().getSelectionModel().select(find(tree().getRoot(), group)));
        assertEquals(List.of("Production"), visibleNames());
        onFx(() -> ((TextField) root.lookup("#searchField")).setText("loose"));
        assertTrue(visibleNames().isEmpty());
        onFx(() -> {
            ((TextField) root.lookup("#searchField")).clear();
            tree().getSelectionModel().select(find(tree().getRoot(), group + "/Empty"));
        });
        assertTrue(visibleNames().isEmpty());
        onFx(() -> tree().getSelectionModel().select(find(tree().getRoot(), "Ungrouped")));
        assertTrue(visibleNames().contains("Loose"));
        assertFalse(visibleNames().contains("Production"));
    }

    @Test void creatingNestedGroupsInManagementIsVisibleInFileManager() throws Exception {
        await(() -> find(tree().getRoot(), group) != null);
        onFx(() -> {
            tree().getSelectionModel().select(find(tree().getRoot(), group));
            answer = "New team";
            button("newGroupButton").fire();
        });
        await(() -> find(tree().getRoot(), group + "/New team") != null && !button("newGroupButton").isDisabled());
        assertTrue(new ServerManagementServiceImpl().getGroupNames().contains(group + "/New team"));
        assertFileManagerGroup(group + "/New team");
    }

    @Test void renameAndDeleteApplyToNestedGroupsWithoutDeletingServers() throws Exception {
        await(() -> find(tree().getRoot(), group) != null);
        onFx(() -> {
            tree().getSelectionModel().select(find(tree().getRoot(), group));
            answer = group + "-Renamed";
            button("renameGroupButton").fire();
        });
        String renamed = group + "-Renamed";
        await(() -> find(tree().getRoot(), renamed + "/Backend") != null && !button("newGroupButton").isDisabled());
        assertEquals(renamed + "/Backend", service.getServerById(production.getId()).getGroupName());
        assertFileManagerGroup(renamed + "/Backend");
        onFx(() -> { answer = ButtonType.OK; button("deleteGroupButton").fire(); });
        await(() -> find(tree().getRoot(), renamed) == null && !button("newGroupButton").isDisabled());
        assertNotNull(service.getServerById(production.getId()));
        assertNull(service.getServerById(production.getId()).getGroupName());
    }

    @Test void cancelledGroupDeletionLeavesSharedGroupsIntact() throws Exception {
        await(() -> find(tree().getRoot(), group) != null);
        onFx(() -> {
            tree().getSelectionModel().select(find(tree().getRoot(), group));
            answer = null; button("deleteGroupButton").fire();
        });
        assertTrue(service.getGroupNames().contains(group + "/Backend"));
        assertEquals(group + "/Backend", service.getServerById(production.getId()).getGroupName());
    }

    @Test void movingSelectedServerUpdatesTheSharedCatalog() throws Exception {
        await(() -> table().getItems().stream().anyMatch(s -> s.getId().equals(loose.getId())));
        onFx(() -> {
            table().getSelectionModel().select(table().getItems().stream().filter(s -> s.getId().equals(loose.getId())).findFirst().orElseThrow());
            answer = group + "/Backend"; button("moveToGroupButton").fire();
        });
        await(() -> table().getItems().stream().anyMatch(s -> s.getId().equals(loose.getId()) && (group + "/Backend").equals(s.getGroupName()))
                && !button("newGroupButton").isDisabled());
        assertEquals(group + "/Backend", new ServerManagementServiceImpl().getServerById(loose.getId()).getGroupName());
        onFx(() -> { answer = "(Ungrouped)"; button("moveToGroupButton").fire(); });
        await(() -> table().getItems().stream().anyMatch(server -> server.getId().equals(loose.getId()) && server.getGroupName() == null)
                && !button("newGroupButton").isDisabled());
        assertNull(service.getServerById(loose.getId()).getGroupName());
    }

    @Test void addingServerInsideSelectedGroupKeepsItVisible() throws Exception {
        await(() -> find(tree().getRoot(), group + "/Backend") != null);
        onFx(() -> {
            tree().getSelectionModel().select(find(tree().getRoot(), group + "/Backend"));
            stageAction = dialog -> {
                dialog.getScene().getRoot().applyCss();
                dialog.getScene().getRoot().layout();
                ((TextField) dialog.getScene().lookup("#nameField")).setText(group + "-Added");
                ((TextField) dialog.getScene().lookup("#hostField")).setText("new.example.com");
                ((TextField) dialog.getScene().lookup("#usernameField")).setText("deploy");
                ((Button) dialog.getScene().lookup("#saveButton")).fire();
            };
            button("addServerButton").fire();
        });
        await(() -> table().getItems().stream().anyMatch(server -> (group + "-Added").equals(server.getName())));
        SSHServerModel saved = service.getAllServers().stream().filter(server -> (group + "-Added").equals(server.getName())).findFirst().orElseThrow();
        assertEquals(group + "/Backend", saved.getGroupName());
    }

    @Test void clonedServerKeepsItsSourceGroup() throws Exception {
        await(() -> find(tree().getRoot(), group + "/Backend") != null);
        onFx(() -> {
            tree().getSelectionModel().select(find(tree().getRoot(), group + "/Backend"));
            table().getSelectionModel().select(0);
            stageAction = dialog -> {
                dialog.getScene().getRoot().applyCss(); dialog.getScene().getRoot().layout();
                ((TextField) dialog.getScene().lookup("#nameField")).setText(group + "-Clone");
                ((Button) dialog.getScene().lookup("#saveButton")).fire();
            };
            button("cloneServerButton").fire();
        });
        await(() -> table().getItems().stream().anyMatch(server -> (group + "-Clone").equals(server.getName())));
        SSHServerModel saved = service.getAllServers().stream().filter(server -> (group + "-Clone").equals(server.getName())).findFirst().orElseThrow();
        assertEquals(group + "/Backend", saved.getGroupName());
    }

    @Test void failedRenameKeepsGroupsAndRestoresControls() throws Exception {
        await(() -> find(tree().getRoot(), group + "/Empty") != null);
        onFx(() -> {
            tree().getSelectionModel().select(find(tree().getRoot(), group + "/Empty"));
            answer = "Backend"; button("renameGroupButton").fire();
        });
        await(() -> lastError != null && !button("newGroupButton").isDisabled());
        assertTrue(service.getGroupNames().contains(group + "/Empty"));
        assertTrue(service.getGroupNames().contains(group + "/Backend"));
        assertEquals(group + "/Backend", service.getServerById(production.getId()).getGroupName());
    }

    @Test void refreshReadsGroupsCreatedByFileManager() throws Exception {
        await(() -> find(tree().getRoot(), group) != null);
        onFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
                loader.load();
                UnifiedFileManagerDialogController files = loader.getController();
                files.setServerCatalog(new ServerManagementServiceImpl());
                files.createGroup(group + "/From files");
            } catch (Exception error) { throw new AssertionError(error); }
            button("refreshButton").fire();
        });
        await(() -> find(tree().getRoot(), group + "/From files") != null);
    }

    @Test void groupControlsStayReadableAndUsableInAllThemes() throws Exception {
        await(() -> find(tree().getRoot(), group) != null);
        onFx(() -> {
            tree().getSelectionModel().select(find(tree().getRoot(), group));
            table().getSelectionModel().select(0);
            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme); root.applyCss(); root.layout();
                for (String id : List.of("newGroupButton", "renameGroupButton", "deleteGroupButton", "moveToGroupButton")) {
                    Button button = button(id);
                    for (boolean hover : new boolean[] { false, true }) {
                        button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), hover);
                        root.applyCss(); root.layout();
                        Color bg = com.seeloggyplus.shared.ui.RenderedColors.background(button);
                        assertTrue(contrast((Color) button.getTextFill(), bg) >= 4.5, theme + id);
                        assertTrue(button.getHeight() >= 24, id);
                        assertEquals(28, button.getWidth(), 1, id);
                        assertNotNull(button.getTooltip());
                        var icon = (de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView) button.getGraphic();
                        assertTrue(contrast((Color) icon.getFill(), bg) >= 3.0, theme + id + " icon");
                    }
                    button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), false);
                }
                assertTrue(tree().getWidth() >= 180);
                for (var node : tree().lookupAll(".tree-cell")) {
                    TreeCell<?> cell = (TreeCell<?>) node;
                    if (cell.isEmpty()) continue;
                    Color bg = (Color) cell.getBackground().getFills().getFirst().getFill();
                    if (bg.getOpacity() == 0) bg = (Color) tree().getBackground().getFills().getFirst().getFill();
                    assertTrue(contrast((Color) cell.getTextFill(), bg) >= 4.5, theme + " group text");
                    if (cell.getGraphic() instanceof de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView icon) {
                        assertTrue(contrast((Color) icon.getFill(), bg) >= 3.0, theme + " group icon");
                    }
                }
            }
        });
    }

    private void assertFileManagerGroup(String expected) throws Exception {
        onFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
                Parent filesRoot = loader.load();
                UnifiedFileManagerDialogController files = loader.getController();
                files.setServerCatalog(new ServerManagementServiceImpl());
                TreeView<?> locations = (TreeView<?>) loader.getNamespace().get("locationTree");
                assertTrue(hasLocationGroup(locations.getRoot(), expected));
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }
    private boolean hasLocationGroup(TreeItem<?> node, String path) {
        if (node.getValue() instanceof UnifiedFileManagerDialogController.LocationItem item && path.equals(item.getGroupName())) return true;
        return node.getChildren().stream().anyMatch(child -> hasLocationGroup(child, path));
    }
    @SuppressWarnings("unchecked") private TreeView<Object> tree() { return (TreeView<Object>) root.lookup("#groupTree"); }
    @SuppressWarnings("unchecked") private TableView<SSHServerModel> table() { return (TableView<SSHServerModel>) root.lookup("#serverTable"); }
    private Button button(String id) { Button result = (Button) root.lookup("#" + id); assertNotNull(result, id); return result; }
    private TreeItem<Object> find(TreeItem<Object> node, String value) {
        if (node == null) return null;
        if (value.equals(String.valueOf(node.getValue()))) return node;
        for (var child : node.getChildren()) { var found = find(child, value); if (found != null) return found; }
        return null;
    }
    private List<String> visibleNames() throws Exception { return WaitForAsyncUtils.asyncFx(() -> table().getItems().stream().map(SSHServerModel::getName).toList()).get(); }
    private void onFx(Runnable action) throws Exception { WaitForAsyncUtils.asyncFx(action).get(); WaitForAsyncUtils.waitForFxEvents(); }
    private void await(BooleanSupplier condition) throws Exception {
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(condition::getAsBoolean).get());
    }
    private static double contrast(Color a, Color b) { double x = luminance(a), y = luminance(b); return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05); }
    private static double luminance(Color c) { return 0.2126*linear(c.getRed())+0.7152*linear(c.getGreen())+0.0722*linear(c.getBlue()); }
    private static double linear(double v) { return v <= 0.04045 ? v/12.92 : Math.pow((v+0.055)/1.055,2.4); }
}
