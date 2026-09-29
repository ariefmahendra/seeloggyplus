package com.seeloggyplus.controller;

import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.ServerManagementService;
import com.seeloggyplus.service.impl.ServerManagementServiceImpl;
import com.seeloggyplus.util.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI-level coverage for the WinSCP-style grouping in File Management:
 * the location tree renders group folders with their servers, and the
 * New/Rename/Delete/Move operations update both the tree and the database.
 */
@ExtendWith(ApplicationExtension.class)
class FileManagerGroupingTest {

    private Stage stage;
    private UnifiedFileManagerDialogController controller;
    private TreeView<UnifiedFileManagerDialogController.LocationItem> locationTree;
    private ServerManagementService serverService;
    private final List<SSHServerModel> createdServers = new ArrayList<>();
    private final List<String> createdGroups = new ArrayList<>();

    @Start
    @SuppressWarnings("unchecked")
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();

        Field treeField = UnifiedFileManagerDialogController.class.getDeclaredField("locationTree");
        treeField.setAccessible(true);
        locationTree = (TreeView<UnifiedFileManagerDialogController.LocationItem>) treeField.get(controller);
        serverService = new ServerManagementServiceImpl();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @AfterEach
    void tearDown() {
        for (String group : createdGroups) {
            try {
                serverService.deleteGroup(group);
            } catch (Exception ignored) {
            }
        }
        for (SSHServerModel server : createdServers) {
            try {
                serverService.deleteServer(server.getId());
            } catch (Exception ignored) {
            }
        }
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private SSHServerModel createServer(String name, String group) {
        SSHServerModel server = new SSHServerModel(name, "127.0.0.1", 22, "user");
        server.setGroupName(group);
        serverService.saveServer(server);
        createdServers.add(server);
        return server;
    }

    private void createGroup(String name) {
        runFx(() -> controller.createGroup(name));
        createdGroups.add(name);
    }

    private static void runFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    private TreeItem<UnifiedFileManagerDialogController.LocationItem> groupNode(String name) {
        for (TreeItem<UnifiedFileManagerDialogController.LocationItem> child : locationTree.getRoot().getChildren()) {
            var value = child.getValue();
            if (value != null && value.isGroup() && name.equals(value.getGroupName())) {
                return child;
            }
        }
        return null;
    }

    private TreeItem<UnifiedFileManagerDialogController.LocationItem> serverNode(String id) {
        for (TreeItem<UnifiedFileManagerDialogController.LocationItem> folder : locationTree.getRoot().getChildren()) {
            for (TreeItem<UnifiedFileManagerDialogController.LocationItem> child : folder.getChildren()) {
                var value = child.getValue();
                if (value != null && value.isServer() && id.equals(value.getServer().getId())) {
                    return child;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("the location tree always starts with Local Drive")
    void localDriveIsAlwaysFirst() {
        TreeItem<UnifiedFileManagerDialogController.LocationItem> first =
                locationTree.getRoot().getChildren().get(0);
        assertTrue(first.getValue().isLocal());
        assertFalse(locationTree.isShowRoot());
    }

    @Test
    @DisplayName("New group creates a persistent, expanded folder")
    void newGroupCreatesPersistentFolder() {
        String name = unique("Group");
        createGroup(name);

        TreeItem<UnifiedFileManagerDialogController.LocationItem> node = groupNode(name);
        assertNotNull(node, "the new group must appear in the location tree");
        assertTrue(node.isExpanded(), "new groups should be expanded so servers are visible");
        assertTrue(serverService.getGroupNames().contains(name),
                "the group must be persisted so it survives a restart");
    }

    @Test
    @DisplayName("servers render inside their group folder")
    void serverRendersInsideGroup() {
        String group = unique("RenderGroup");
        SSHServerModel server = createServer(unique("server"), group);
        createGroup(group);

        TreeItem<UnifiedFileManagerDialogController.LocationItem> node = groupNode(group);
        assertNotNull(node);
        assertEquals(1, node.getChildren().size());
        assertEquals(server.getId(), node.getChildren().get(0).getValue().getServer().getId());
        assertNull(serverNodeByRoot(server.getId()), "grouped servers must not also sit at the root");
    }

    private TreeItem<UnifiedFileManagerDialogController.LocationItem> serverNodeByRoot(String id) {
        for (TreeItem<UnifiedFileManagerDialogController.LocationItem> child : locationTree.getRoot().getChildren()) {
            var value = child.getValue();
            if (value != null && value.isServer() && id.equals(value.getServer().getId())) {
                return child;
            }
        }
        return null;
    }

    @Test
    @DisplayName("Move to group moves the server node and persists the membership")
    void moveServerToGroupUpdatesTreeAndDatabase() {
        String group = unique("MoveGroup");
        SSHServerModel server = createServer(unique("server"), null);
        createGroup(group);

        runFx(() -> controller.moveServerToGroup(server.getId(), group));

        TreeItem<UnifiedFileManagerDialogController.LocationItem> node = serverNode(server.getId());
        assertNotNull(node, "the moved server must be inside a folder now");
        assertEquals(group, node.getParent().getValue().getGroupName());
        assertEquals(group, serverService.getServerById(server.getId()).getGroupName());
    }

    @Test
    @DisplayName("moving a server back to ungrouped places it at the tree root")
    void moveServerBackToUngrouped() {
        String group = unique("BackGroup");
        SSHServerModel server = createServer(unique("server"), group);
        createGroup(group);

        assertEquals(group, serverNode(server.getId()).getParent().getValue().getGroupName());

        runFx(() -> controller.moveServerToGroup(server.getId(), null));

        assertNotNull(serverNodeByRoot(server.getId()),
                "an ungrouped server must be visible directly under the root");
        assertNull(serverService.getServerById(server.getId()).getGroupName());
    }

    @Test
    @DisplayName("Rename group updates the folder label and its members")
    void renameGroupUpdatesTreeAndMembers() {
        String before = unique("Before");
        String after = unique("After");
        SSHServerModel server = createServer(unique("server"), before);
        createGroup(before);

        runFx(() -> controller.renameGroup(before, after));
        createdGroups.add(after);

        assertNull(groupNode(before));
        TreeItem<UnifiedFileManagerDialogController.LocationItem> renamed = groupNode(after);
        assertNotNull(renamed);
        assertEquals(server.getId(), renamed.getChildren().get(0).getValue().getServer().getId());
        assertEquals(after, serverService.getServerById(server.getId()).getGroupName());
    }

    @Test
    @DisplayName("Delete group ungroups its members and removes the folder")
    void deleteGroupKeepsServers() {
        String group = unique("DeleteGroup");
        SSHServerModel server = createServer(unique("server"), group);
        createGroup(group);

        runFx(() -> controller.deleteGroup(group));

        assertNull(groupNode(group));
        assertNotNull(serverNodeByRoot(server.getId()), "the server must stay, now ungrouped");
        assertNull(serverService.getServerById(server.getId()).getGroupName());
        assertFalse(serverService.getGroupNames().contains(group));
    }

    @Test
    @DisplayName("drop target rules: folder joins, server inherits, Local/root ungroup")
    void dropTargetRules() {
        String group = unique("DropGroup");
        SSHServerModel grouped = createServer(unique("server"), group);
        createGroup(group);

        assertEquals(group, UnifiedFileManagerDialogController.groupForDropTarget(
                UnifiedFileManagerDialogController.LocationItem.of(grouped)));
        assertEquals(group, UnifiedFileManagerDialogController.groupForDropTarget(
                UnifiedFileManagerDialogController.LocationItem.group(group)));
        assertNull(UnifiedFileManagerDialogController.groupForDropTarget(
                UnifiedFileManagerDialogController.LocationItem.local()));
        assertNull(UnifiedFileManagerDialogController.groupForDropTarget(
                UnifiedFileManagerDialogController.LocationItem.root()));
    }
}
