package com.seeloggyplus.controller;

import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.impl.ServerManagementServiceImpl;
import com.seeloggyplus.util.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Server Management is now a pure CRUD/reorder dialog: grouping and favourite
 * management live in File Management, so the old controls must be gone.
 */
@ExtendWith(ApplicationExtension.class)
class ServerManagementCleanupTest {

    private Parent root;
    private ServerManagementDialogController controller;

    @Start
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerManagementDialog.fxml"));
        root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("Server Management no longer offers favorite or grouping controls")
    void favoriteAndGroupControlsAreGone() {
        assertNull(root.lookup("#groupTree"), "the group tree must be removed from Server Management");
        assertNull(root.lookup("#groupButton"), "Move to group must be removed from Server Management");
        assertNull(root.lookup("#favoriteButton"), "favorite toggle must be removed from Server Management");
        assertNull(root.lookup("#favoritesOnly"), "Favorites only filter must be removed from Server Management");
    }

    @Test
    @DisplayName("server rows are not draggable for reordering")
    void rowsAreNotDraggable() throws Exception {
        Field tableField = ServerManagementDialogController.class.getDeclaredField("serverTable");
        tableField.setAccessible(true);
        TableView<?> table = (TableView<?>) tableField.get(controller);
        assertNotNull(table.getRowFactory(), "the row factory must still support double-click to edit");

        @SuppressWarnings({"unchecked", "rawtypes"})
        TableRow<?> row = table.getRowFactory().call((TableView) table);
        assertNull(row.getOnDragDetected(), "rows must not start a reorder drag");
        assertNull(row.getOnDragOver(), "rows must not accept a reorder drag");
        assertNull(row.getOnDragDropped(), "rows must not handle reorder drops");
    }

    @Test
    @DisplayName("the drag-to-reorder hint is gone")
    void noReorderHint() {
        for (javafx.scene.Node node : root.lookupAll(".label")) {
            if (node instanceof javafx.scene.control.Label label && label.getText() != null) {
                assertFalse(label.getText().toLowerCase().contains("drag rows"),
                        "the reorder hint must be removed");
            }
        }
    }

    @Test
    @DisplayName("server names never show the old favorite star")
    void nameColumnHasNoFavoriteStar() throws Exception {
        ServerManagementServiceImpl service = new ServerManagementServiceImpl();
        SSHServerModel server = new SSHServerModel("NoStar-" + UUID.randomUUID().toString().substring(0, 8),
                "127.0.0.1", 22, "user");
        server.setFavorite(true);
        service.saveServer(server);
        try {
            Field nameField = ServerManagementDialogController.class.getDeclaredField("nameColumn");
            nameField.setAccessible(true);
            @SuppressWarnings("unchecked")
            TableColumn<SSHServerModel, String> nameColumn =
                    (TableColumn<SSHServerModel, String>) nameField.get(controller);

            String shown = nameColumn.getCellData(server);
            assertNotNull(shown);
            assertFalse(shown.contains("★"), "favorite stars must not be rendered in Server Management");
            assertTrue(shown.contains(server.getName()));
        } finally {
            service.deleteServer(server.getId());
        }
    }
}
