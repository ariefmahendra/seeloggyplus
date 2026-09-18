package com.seeloggyplus.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
public class ServerManagementDialogTest {

    private Parent root;
    private ServerManagementDialogController controller;

    @Start
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerManagementDialog.fxml"));
        root = loader.load();
        controller = loader.getController();
    }

    @Test
    @DisplayName("ServerManagementDialog FXML should load successfully and contain cloneServerButton")
    public void testServerManagementDialogFxmlLoadsWithCloneButton() throws Exception {
        assertNotNull(root);
        assertNotNull(controller);

        Field cloneButtonField = ServerManagementDialogController.class.getDeclaredField("cloneServerButton");
        cloneButtonField.setAccessible(true);
        Button cloneButton = (Button) cloneButtonField.get(controller);

        assertNotNull(cloneButton, "cloneServerButton must be injected from FXML");
        assertEquals("Clone", cloneButton.getText());
        assertTrue(cloneButton.isDisable(), "Clone button should initially be disabled without selection");
    }

    @Test
    @DisplayName("serverTable should have ContextMenu configured with clone option")
    public void testServerTableHasContextMenuWithClone() throws Exception {
        Field tableField = ServerManagementDialogController.class.getDeclaredField("serverTable");
        tableField.setAccessible(true);
        TableView<?> table = (TableView<?>) tableField.get(controller);

        assertNotNull(table);
        assertNotNull(table.getContextMenu(), "serverTable should have a context menu configured");
        assertTrue(table.getContextMenu().getItems().stream()
                .anyMatch(item -> "Clone...".equals(item.getText())), "Context menu must have 'Clone...' item");
        assertFalse(table.getContextMenu().getItems().stream()
                .anyMatch(item -> "Test Connection".equals(item.getText())), "Context menu must NOT have 'Test Connection'");
    }

    @Test
    @DisplayName("serverTable should NOT contain status column")
    public void testServerTableDoesNotHaveStatusColumn() throws Exception {
        Field tableField = ServerManagementDialogController.class.getDeclaredField("serverTable");
        tableField.setAccessible(true);
        TableView<?> table = (TableView<?>) tableField.get(controller);

        assertNotNull(table);
        boolean hasStatusColumn = table.getColumns().stream()
                .anyMatch(col -> "Status".equalsIgnoreCase(col.getText()));
        assertFalse(hasStatusColumn, "serverTable must NOT contain Status column");
    }

    @Test
    @DisplayName("ServerManagementDialogController should NOT have statusColumn or testConnectionButton fields")
    public void testNoStatusColumnOrTestConnectionButtonFields() {
        assertThrows(NoSuchFieldException.class, () -> 
                ServerManagementDialogController.class.getDeclaredField("statusColumn"));
        assertThrows(NoSuchFieldException.class, () -> 
                ServerManagementDialogController.class.getDeclaredField("testConnectionButton"));
    }

    @Test
    @DisplayName("loadServers should populate serverTable with servers from database")
    public void testLoadServersPopulatesTable() throws Exception {
        com.seeloggyplus.service.ServerManagementService service = new com.seeloggyplus.service.impl.ServerManagementServiceImpl();
        com.seeloggyplus.model.SSHServerModel testServer = new com.seeloggyplus.model.SSHServerModel();
        testServer.setName("Test Populate Server");
        testServer.setHost("10.0.0.1");
        testServer.setPort(22);
        testServer.setUsername("user");
        service.saveServer(testServer);

        try {
            java.lang.reflect.Method loadMethod = ServerManagementDialogController.class.getDeclaredMethod("loadServers", String.class);
            loadMethod.setAccessible(true);
            loadMethod.invoke(controller, testServer.getId());

            // Wait for background Task and FX thread
            org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
            Thread.sleep(400);
            org.testfx.util.WaitForAsyncUtils.waitForFxEvents();

            Field tableField = ServerManagementDialogController.class.getDeclaredField("serverTable");
            tableField.setAccessible(true);
            @SuppressWarnings("unchecked")
            TableView<com.seeloggyplus.model.SSHServerModel> table = (TableView<com.seeloggyplus.model.SSHServerModel>) tableField.get(controller);

            assertFalse(table.getItems().isEmpty(), "serverTable items must not be empty after loadServers()");
            assertTrue(table.getItems().stream().anyMatch(s -> testServer.getId().equals(s.getId())), 
                    "serverTable must contain the inserted server");
        } finally {
            service.deleteServer(testServer.getId());
        }
    }
}
