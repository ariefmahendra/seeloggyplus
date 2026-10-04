package com.seeloggyplus.controller;

import com.seeloggyplus.dto.RecentFilesDto;
import com.seeloggyplus.model.LogFile;
import com.seeloggyplus.ui.cell.RecentFileTreeCell;
import com.seeloggyplus.util.AppTheme;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for the Recent Files tree: entries are grouped per server (or Local),
 * groups expand/collapse, search filters across server + file name, and the rows
 * no longer show the old "Open/Tail" label.
 */
@ExtendWith(ApplicationExtension.class)
class RecentFilesTreeTest {

    private MainController controller;
    private TreeView<RecentFileTreeCell.RecentNode> recentFilesTreeView;
    private ObservableList<RecentFilesDto> allRecentFiles;
    private TextField filterField;

    @Start
    @SuppressWarnings("unchecked")
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();

        Field treeField = MainController.class.getDeclaredField("recentFilesTreeView");
        treeField.setAccessible(true);
        recentFilesTreeView = (TreeView<RecentFileTreeCell.RecentNode>) treeField.get(controller);

        Field allField = MainController.class.getDeclaredField("allRecentFiles");
        allField.setAccessible(true);
        allRecentFiles = (ObservableList<RecentFilesDto>) allField.get(controller);

        Field filterFieldRef = MainController.class.getDeclaredField("recentFilesFilterField");
        filterFieldRef.setAccessible(true);
        filterField = (TextField) filterFieldRef.get(controller);
    }

    private RecentFilesDto remote(String serverName, String serverId, String name, String path) {
        LogFile file = new LogFile();
        file.setName(name);
        file.setFilePath(path);
        file.setRemote(true);
        file.setSshServerID(serverId);
        return new RecentFilesDto(file, null, serverName, "TAIL");
    }

    private RecentFilesDto local(String name, String path) {
        LogFile file = new LogFile();
        file.setName(name);
        file.setFilePath(path);
        file.setRemote(false);
        return new RecentFilesDto(file, null, null, "OPEN");
    }

    private static void runFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    private TreeItem<RecentFileTreeCell.RecentNode> serverNode(String name) {
        for (TreeItem<RecentFileTreeCell.RecentNode> child : recentFilesTreeView.getRoot().getChildren()) {
            if (child.getValue().isServer() && name.equals(child.getValue().getServerName())) {
                return child;
            }
        }
        return null;
    }

    @Test
    @DisplayName("recent files are grouped per server and per Local source")
    void entriesAreGroupedByServer() {
        runFx(() -> {
            allRecentFiles.setAll(
                    remote("Production", "id-p", "application.log", "/srv/prod/application.log"),
                    remote("Production", "id-p", "error.log", "/srv/prod/error.log"),
                    remote("Staging", "id-s", "application.log", "/srv/stage/application.log"),
                    local("local.log", "C:\\logs\\local.log"));
            controller.rebuildRecentTree();
        });

        TreeItem<RecentFileTreeCell.RecentNode> production = serverNode("Production");
        TreeItem<RecentFileTreeCell.RecentNode> staging = serverNode("Staging");
        TreeItem<RecentFileTreeCell.RecentNode> localNode = serverNode("Local");

        assertNotNull(production, "same-named files from one server must collapse under it");
        assertEquals(2, production.getChildren().size());
        assertNotNull(staging);
        assertEquals(1, staging.getChildren().size());
        assertNotNull(localNode, "local files group under Local");
        assertEquals(3, recentFilesTreeView.getRoot().getChildren().size(), "2 servers + Local");
    }

    @Test
    @DisplayName("group headers and tooltips show only source names, regardless of the number of logs")
    void groupHeadersDoNotShowLogCounts() {
        class TestCell extends RecentFileTreeCell {
            TestCell() { super(() -> null); }
            void render(TreeItem<RecentNode> group) {
                updateTreeItem(group);
                updateItem(group.getValue(), false);
            }
        }
        runFx(() -> {
            allRecentFiles.setAll(
                    remote("Production", "id-p", "application.log", "/srv/application.log"),
                    remote("Production", "id-p", "error.log", "/srv/error.log"),
                    remote("Production (Copy)", "id-copy", "copy.log", "/srv/copy.log"),
                    local("local.log", "C:\\logs\\local.log"));
            controller.rebuildRecentTree();
            TestCell cell = new TestCell();
            for (TreeItem<RecentFileTreeCell.RecentNode> group : recentFilesTreeView.getRoot().getChildren()) {
                String source = group.getValue().getServerName();
                cell.render(group);
                assertEquals(source, cell.getText(), "The header should display the full source name without a count");
                assertEquals(source, cell.getTooltip().getText(), "Hovering should not add the removed count");
                group.getChildren().clear();
                cell.render(group);
                assertEquals(source, cell.getText(), "Changes to the number of logs must not change the header");
                assertEquals(source, cell.getTooltip().getText());
            }
        });
    }

    @Test
    @DisplayName("server groups are expanded by default and can be collapsed")
    void groupsExpandAndCollapse() {
        runFx(() -> {
            allRecentFiles.setAll(
                    remote("Production", "id-p", "a.log", "/srv/a.log"),
                    remote("Production", "id-p", "b.log", "/srv/b.log"));
            controller.rebuildRecentTree();
        });

        TreeItem<RecentFileTreeCell.RecentNode> production = serverNode("Production");
        assertTrue(production.isExpanded(), "groups start expanded so files are visible");
        production.setExpanded(false);
        assertFalse(production.isExpanded(), "groups must be collapsible");
        production.setExpanded(true);
        assertEquals(2, production.getChildren().size());
    }

    @Test
    @DisplayName("search matches combined words across server name, file name and path")
    void searchMatchesAcrossServerAndFile() {
        runFx(() -> {
            allRecentFiles.setAll(
                    remote("Production", "id-p", "application.log", "/srv/prod/application.log"),
                    remote("Staging", "id-s", "application.log", "/srv/stage/application.log"));
            controller.rebuildRecentTree();
            filterField.setText("production application.log");
        });

        assertNotNull(serverNode("Production"), "combined search must keep the matching server");
        assertNull(serverNode("Staging"), "the other server must be filtered out");
        assertEquals(1, serverNode("Production").getChildren().size());

        runFx(() -> filterField.setText("/srv/stage"));
        assertNull(serverNode("Production"));
        assertNotNull(serverNode("Staging"), "path search must match the Staging entry");
    }

    @Test
    @DisplayName("file rows no longer render the old Tail/Open label")
    void rowsDoNotShowOpenMode() {
        RecentFilesDto dto = remote("Production", "id-p", "application.log", "/srv/application.log");
        class TestCell extends RecentFileTreeCell {
            TestCell() { super(() -> null); }
            void render(RecentFilesDto item) {
                updateItem(RecentFileTreeCell.RecentNode.file(item), false);
            }
        }
        javafx.scene.layout.VBox[] contentRef = new javafx.scene.layout.VBox[1];
        runFx(() -> {
            TestCell cell = new TestCell();
            cell.render(dto);
            contentRef[0] = (javafx.scene.layout.VBox) cell.getGraphic();
        });

        var content = contentRef[0];
        String text = content.getChildren().stream()
                .filter(node -> node instanceof Label)
                .map(node -> ((Label) node).getText())
                .reduce("", (all, value) -> all + " " + value);
        assertFalse(text.toLowerCase().contains("tail"));
        assertFalse(text.toLowerCase().contains("open"));
        assertTrue(text.contains("application.log"));
    }
}
