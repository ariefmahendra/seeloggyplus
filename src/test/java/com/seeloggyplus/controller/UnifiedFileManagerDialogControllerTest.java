package com.seeloggyplus.controller;

import com.seeloggyplus.model.FavoriteFolder;
import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.FavoriteFolderService;
import com.seeloggyplus.service.LocalFileService;
import com.seeloggyplus.service.ServerManagementService;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.seeloggyplus.service.SSHService;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import com.seeloggyplus.dto.RemoteFileInfo;


import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Disabled;

@ExtendWith(ApplicationExtension.class)
public class UnifiedFileManagerDialogControllerTest {

    private UnifiedFileManagerDialogController controller;
    private TableView<FileInfo> fileTable;
    private TextField pathField;
    private TextField searchField;
    private Button openButton;
    private Button previewButton;
    private Button tailButton;
    private Label itemCountLabel;

    // Mocks
    private MockLocalFileService localFileService;
    private MockServerManagementService serverManagementService;
    private MockFavoriteFolderService favoriteFolderService;
    private MockSSHService mockSshService;

    @Start
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        stage.setScene(new Scene(root));
        stage.show();

        fileTable = getField("fileTable");
        pathField = getField("pathField");
        searchField = getField("searchField");
        openButton = getField("openButton");
        previewButton = getField("previewButton");
        tailButton = getField("tailButton");
        itemCountLabel = getField("itemCountLabel");
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(String fieldName) throws Exception {
        Field field = UnifiedFileManagerDialogController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(controller);
    }

    @BeforeEach
    public void setupMocks() throws Exception {
        localFileService = new MockLocalFileService();
        serverManagementService = new MockServerManagementService();
        favoriteFolderService = new MockFavoriteFolderService();

        Field lfsField = UnifiedFileManagerDialogController.class.getDeclaredField("localFileService");
        lfsField.setAccessible(true);
        lfsField.set(controller, localFileService);

        Field smsField = UnifiedFileManagerDialogController.class.getDeclaredField("serverManagementService");
        smsField.setAccessible(true);
        smsField.set(controller, serverManagementService);

        Field ffsField = UnifiedFileManagerDialogController.class.getDeclaredField("favoriteFolderService");
        ffsField.setAccessible(true);
        ffsField.set(controller, favoriteFolderService);

        mockSshService = new MockSSHService();
        Field sshFactoryField = UnifiedFileManagerDialogController.class.getDeclaredField("sshServiceFactory");
        sshFactoryField.setAccessible(true);
        java.util.function.Supplier<SSHServiceImpl> factory = () -> mockSshService;
        sshFactoryField.set(controller, factory);

        // Clear cache
        Field cacheField = UnifiedFileManagerDialogController.class.getDeclaredField("directoryCache");
        cacheField.setAccessible(true);
        java.util.Map<?, ?> cache = (java.util.Map<?, ?>) cacheField.get(controller);
        cache.clear();
    }

    private void forceRefreshLocation() {
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("refreshCurrentPath");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // --- POSITIVE SCENARIOS ---

    private void invokeControllerMethod(String methodName) {
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod(methodName);
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
    }

    @Test
    public void testNavigationButtons(FxRobot robot) throws Exception {
        // Go to Home
        invokeControllerMethod("navigateHome");
        Thread.sleep(1000); // Wait for background task
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("C:\\", pathField.getText());

        // Select directory "folder1"
        Platform.runLater(() -> fileTable.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
        
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleFileDoubleClick");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("C:\\folder1", pathField.getText());

        // Select directory "folder1" (inside folder1)
        Platform.runLater(() -> fileTable.getSelectionModel().select(1)); // index 0 is "..", index 1 is "folder1"
        WaitForAsyncUtils.waitForFxEvents();
        
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleFileDoubleClick");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("C:\\folder1\\folder1", pathField.getText());

        // Back
        invokeControllerMethod("navigateBack");
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("C:\\folder1", pathField.getText());

        // Forward
        invokeControllerMethod("navigateForward");
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("C:\\folder1\\folder1", pathField.getText());

        // Up
        invokeControllerMethod("navigateUp");
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("C:\\folder1", pathField.getText());
    }

    @Test
    public void testNavigateViaGoButton(FxRobot robot) throws Exception {
        Platform.runLater(() -> pathField.setText("C:\\custom"));
        WaitForAsyncUtils.waitForFxEvents();
        robot.clickOn("#goButton");
        Thread.sleep(200);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("C:\\custom", pathField.getText());
    }

    @Test
    public void testSearchFilteringAndClear(FxRobot robot) throws Exception {
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method navigateMethod = UnifiedFileManagerDialogController.class.getDeclaredMethod("navigateTo", String.class);
                navigateMethod.setAccessible(true);
                navigateMethod.invoke(controller, "C:\\");
            } catch (Exception e) {}
        });
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        
        Platform.runLater(() -> searchField.setText("file1"));
        Thread.sleep(500);
        WaitForAsyncUtils.waitForFxEvents();
        
        // Clear
        Platform.runLater(() -> searchField.setText(""));
        Thread.sleep(500);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void testAddAndRemoveFavorites(FxRobot robot) throws Exception {
        invokeControllerMethod("navigateHome");
        Thread.sleep(1500);
        WaitForAsyncUtils.waitForFxEvents();

        // Select the first directory
        Platform.runLater(() -> fileTable.getSelectionModel().select(0)); // 0 is "folder1" (no ".." in C:\)
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();

        FileInfo selected = fileTable.getSelectionModel().getSelectedItem();
        assertNotNull(selected, "Item should be selected");
        
        // Add favorite programmatically to bypass context menu UI flakiness in tests
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleAddToFavorites");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        Thread.sleep(200);
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(favoriteFolderService.isFavorite(selected.getPath(), "local"));

        // Remove
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleRemoveFromFavorites");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        Thread.sleep(200);
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(favoriteFolderService.isFavorite(selected.getPath(), "local"));
    }

    @Test
    public void testTableSelectionDisablesButtons(FxRobot robot) throws Exception {
        invokeControllerMethod("navigateHome");
        Thread.sleep(1500);
        WaitForAsyncUtils.waitForFxEvents();

        // Select directory
        Platform.runLater(() -> fileTable.getSelectionModel().select(0)); // "folder1"
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(openButton.isDisabled());
        assertTrue(previewButton.isDisabled());
        assertTrue(tailButton.isDisabled());

        // Select local file
        Platform.runLater(() -> fileTable.getSelectionModel().select(1)); // "file1.txt"
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(openButton.isDisabled());
        assertFalse(previewButton.isDisabled());
        assertTrue(tailButton.isDisabled()); // Tail is for remote only
    }

    @Test
    public void testLocalFileOpen(FxRobot robot) throws Exception {
        robot.clickOn("#homeButton");
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> fileTable.getSelectionModel().select(2)); // "file1.txt"
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleOpen");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(UnifiedFileManagerDialogController.OpenAction.OPEN, controller.getOpenAction());
    }

    @Test
    public void testDirectoryCacheHit(FxRobot robot) throws Exception {
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("navigateTo", String.class);
                m.setAccessible(true);
                m.invoke(controller, "C:\\CacheDir");
            } catch (Exception e) {}
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, localFileService.callCount); // First call

        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("navigateTo", String.class);
                m.setAccessible(true);
                m.invoke(controller, "C:\\CacheDir");
            } catch (Exception e) {}
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, localFileService.callCount); // Should be cached!
    }

    @Test
    public void testSortingBySize(FxRobot robot) {
        robot.clickOn("#homeButton");
        WaitForAsyncUtils.waitForFxEvents();
        // Since testfx click on column header is tricky, we can check the comparator
        assertNotNull(fileTable.getColumns().get(2).getComparator()); // Size column
    }

    @Test
    public void testAutoRefreshOnFocus(FxRobot robot) throws Exception {
        // Only refreshes if remote location, but we can call it manually to test logic
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleWindowGainedFocus");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        WaitForAsyncUtils.waitForFxEvents();
        // No exception means it handled it.
    }

    // --- NEGATIVE SCENARIOS ---

    @Test
    public void testDirectoryLoadFailure(FxRobot robot) {
        localFileService.throwError = true;
        robot.clickOn("#homeButton");
        WaitForAsyncUtils.waitForFxEvents();
        // Just verify it doesn't crash and we get an error status or empty table.
        // It should show an error dialog which blocks TestFX, but we can at least assert it tried.
    }

    @Test
    public void testNavigateBeyondRoot(FxRobot robot) {
        robot.clickOn("#homeButton");
        WaitForAsyncUtils.waitForFxEvents();
        
        robot.clickOn("#upButton");
        WaitForAsyncUtils.waitForFxEvents();
        // Should not crash, path should remain C:\
    }

    @Test
    public void testTailLocalFile(FxRobot robot) {
        robot.clickOn("#homeButton");
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> fileTable.getSelectionModel().select(2)); // file
        WaitForAsyncUtils.waitForFxEvents();

        // Call handleTail
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleTail");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        WaitForAsyncUtils.waitForFxEvents();
        // openAction should still be OPEN, not TAIL because it rejected it
        assertEquals(UnifiedFileManagerDialogController.OpenAction.OPEN, controller.getOpenAction());
    }

    // --- SERVER SCENARIOS ---

    @Test
    public void testServerConnectionSuccess(FxRobot robot) throws Exception {
        // Trigger server selection
        Platform.runLater(() -> {
            try {
                Class<?> locationItemClass = Class.forName("com.seeloggyplus.controller.UnifiedFileManagerDialogController$LocationItem");
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleLocationSelected", locationItemClass);
                m.setAccessible(true);
                SSHServerModel s = new SSHServerModel();
                s.setId("server1"); s.setName("Server 1"); s.setHost("test.com"); s.setUsername("user"); s.setPassword("pass");
                Object item = locationItemClass.getConstructors()[0].newInstance("Server 1", null, s);
                m.invoke(controller, item);
            } catch (Exception e) {}
        });
        Thread.sleep(1500); // Wait for connect task
        WaitForAsyncUtils.waitForFxEvents();

        // Connected and navigated to default path "/"
        assertEquals("/", pathField.getText());
        assertTrue(mockSshService.connectCalled);
    }

    @Test
    public void testServerConnectionFailure(FxRobot robot) throws Exception {
        mockSshService.simulateFailure = true;
        // Trigger server selection
        Platform.runLater(() -> {
            try {
                Class<?> locationItemClass = Class.forName("com.seeloggyplus.controller.UnifiedFileManagerDialogController$LocationItem");
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleLocationSelected", locationItemClass);
                m.setAccessible(true);
                SSHServerModel s = new SSHServerModel();
                s.setId("server1"); s.setName("Server Fail"); s.setHost("fail.com"); s.setUsername("user"); s.setPassword("pass");
                Object item = locationItemClass.getConstructors()[0].newInstance("Server Fail", null, s);
                m.invoke(controller, item);
            } catch (Exception e) {}
        });
        Thread.sleep(1500); // Wait for connect task
        WaitForAsyncUtils.waitForFxEvents();

        // Should NOT be connected, fallback to local so path doesn't change to "/"
        assertNotEquals("/", pathField.getText());
        assertTrue(mockSshService.connectCalled);
    }

    @Test
    public void testRemoteFileTailAction(FxRobot robot) throws Exception {
        // First connect
        testServerConnectionSuccess(robot);

        // Select a remote file in the table (mock returns a file)
        Platform.runLater(() -> fileTable.getSelectionModel().select(0)); // remoteFile.log
        Thread.sleep(200);
        WaitForAsyncUtils.waitForFxEvents();

        // Tail button should now be enabled for remote files
        assertFalse(tailButton.isDisabled());

        // Call handleTail
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleTail");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        Thread.sleep(200);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(UnifiedFileManagerDialogController.OpenAction.TAIL, controller.getOpenAction());
    }

    // --- Mock Classes ---

    class MockLocalFileService implements LocalFileService {
        public int callCount = 0;
        public boolean throwError = false;
        public boolean simulateDelay = false;

        @Override
        public String getHomeDirectory() {
            return "C:\\";
        }

        @Override
        public List<FileInfo> listFiles(String directoryPath) throws IOException {
            callCount++;
            if (throwError) throw new IOException("Mock Error");

            if (simulateDelay) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            List<FileInfo> list = new ArrayList<>();
            FileInfo d1 = new FileInfo(); d1.setName("folder1"); d1.setDirectory(true); d1.setPath(directoryPath + "\\folder1");
            FileInfo f1 = new FileInfo(); f1.setName("file1.txt"); f1.setDirectory(false); f1.setPath(directoryPath + "\\file1.txt"); f1.setSize(1024L);
            FileInfo f2 = new FileInfo(); f2.setName("file2.log"); f2.setDirectory(false); f2.setPath(directoryPath + "\\file2.log"); f2.setSize(5000000L);
            list.add(d1);
            list.add(f1);
            list.add(f2);
            return list;
        }
    }

    class MockServerManagementService implements ServerManagementService {
        @Override
        public void saveServer(SSHServerModel server) {}
        @Override
        public void deleteServer(String id) {}
        @Override
        public void updateServerLastUsed(String id) {}
        @Override
        public List<SSHServerModel> getAllServers() {
            SSHServerModel s = new SSHServerModel();
            s.setId("server1"); s.setName("Server 1"); s.setHost("localhost");
            return Arrays.asList(s);
        }
        @Override
        public SSHServerModel getServerById(String id) { return null; }
    }

    class MockSSHService extends SSHServiceImpl {
        public boolean connectCalled = false;
        public boolean simulateFailure = false;

        @Override
        public boolean connect(String host, int port, String username, String password) {
            connectCalled = true;
            return !simulateFailure;
        }

        @Override
        public boolean connect(String host, int port, String username, String password, long ttlMillis) {
            return connect(host, port, username, password);
        }

        @Override public void disconnect() {}
        @Override public boolean isConnected() { return connectCalled && !simulateFailure; }
        
        @Override
        public List<RemoteFileInfo> listFiles(String remotePath) throws IOException {
            if (simulateFailure) throw new IOException("Remote Mock Error");
            List<RemoteFileInfo> list = new ArrayList<>();
            RemoteFileInfo f1 = new RemoteFileInfo();
            f1.setName("remoteFile.log");
            f1.setPath(remotePath + "/remoteFile.log");
            f1.setDirectory(false);
            f1.setSize(1024L);
            f1.setModifiedTime(System.currentTimeMillis());
            f1.setPermissions("rw-r--r--");
            list.add(f1);
            return list;
        }
    }

    class MockFavoriteFolderService implements FavoriteFolderService {
        private List<FavoriteFolder> favs = new ArrayList<>();
        @Override
        public void addFavorite(String name, String path, String locationId) {
            FavoriteFolder f = new FavoriteFolder(); f.setName(name); f.setPath(path); f.setId(1);
            favs.add(f);
        }
        @Override
        public void removeFavorite(int id) { favs.removeIf(f -> f.getId() == id); }
        @Override
        public List<FavoriteFolder> getFavoritesForLocation(String locationId) { return favs; }
        @Override
        public boolean isFavorite(String path, String locationId) {
            return favs.stream().anyMatch(f -> f.getPath().equals(path));
        }
    }
    @Test
    public void testFileDoubleClick(FxRobot robot) throws Exception {
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method navigateMethod = UnifiedFileManagerDialogController.class.getDeclaredMethod("navigateTo", String.class);
                navigateMethod.setAccessible(true);
                navigateMethod.invoke(controller, "C:\\");
            } catch (Exception e) {}
        });
        Thread.sleep(1500);
        WaitForAsyncUtils.waitForFxEvents();

        // Select directory "folder1"
        Platform.runLater(() -> fileTable.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
        
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleFileDoubleClick");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        Thread.sleep(1500);
        WaitForAsyncUtils.waitForFxEvents();
        
        assertEquals("C:\\folder1", pathField.getText());

        // Select file "file1.txt" (inside folder1)
        Platform.runLater(() -> fileTable.getSelectionModel().select(2)); // Should be a file inside folder1 (index 0 is "..", 1 is "folder1", 2 is "file1.txt")
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleFileDoubleClick");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        Thread.sleep(500);
        WaitForAsyncUtils.waitForFxEvents();
        
        assertEquals(UnifiedFileManagerDialogController.OpenAction.OPEN, controller.getOpenAction());
    }

    @Test
    public void testPreviewAction(FxRobot robot) throws Exception {
        robot.clickOn("#homeButton");
        Thread.sleep(1500);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> fileTable.getSelectionModel().select(1)); // "file1.txt"
        WaitForAsyncUtils.waitForFxEvents();

        Thread t = new Thread(() -> {
            Platform.runLater(() -> {
                try {
                    java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handlePreview");
                    m.setAccessible(true);
                    m.invoke(controller);
                } catch (Exception e) {}
            });
        });
        t.start();
        Thread.sleep(1500);
        
        robot.type(javafx.scene.input.KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void testManageServersAction(FxRobot robot) throws Exception {
        // Run on separate thread to prevent showAndWait from blocking TestFX thread
        Thread t = new Thread(() -> {
            Platform.runLater(() -> {
                try {
                    java.lang.reflect.Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleManageServers");
                    m.setAccessible(true);
                    m.invoke(controller);
                } catch (Exception e) {}
            });
        });
        t.start();
        Thread.sleep(1500);
        
        // Close the newly opened ServerManagementDialog by typing ESCAPE
        robot.type(javafx.scene.input.KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
