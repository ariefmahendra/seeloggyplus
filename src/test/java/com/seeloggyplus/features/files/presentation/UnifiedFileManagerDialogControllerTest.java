package com.seeloggyplus.features.files.presentation;

import com.seeloggyplus.shared.ui.AppTheme;

import com.seeloggyplus.features.files.domain.FavoriteFolder;
import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.features.files.application.FavoriteFolderService;
import com.seeloggyplus.features.files.application.LocalFileService;
import com.seeloggyplus.shared.servers.ServerCatalog;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.shared.ssh.SSHServiceFactory;
import com.seeloggyplus.shared.dto.RemoteFileInfo;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.settings.PreferenceService;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.concurrent.Task;

@ExtendWith(ApplicationExtension.class)
public class UnifiedFileManagerDialogControllerTest {

    private UnifiedFileManagerDialogController controller;
    private Stage stage;
    private TableView<FileInfo> fileTable;
    private TextField pathField;
    private TextField searchField;
    private Button openButton;
    private Button previewButton;
    private Button tailButton;
    private Label itemCountLabel;

    private MockLocalFileService localFileService;
    private MockServerManagementService serverManagementService;
    private MockFavoriteFolderService favoriteFolderService;
    private MockSSHService mockSshService;

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final String SEP = IS_WINDOWS ? "\\" : "/";
    private static final String HOME = IS_WINDOWS ? "C:\\" : "/";
    private static final String FOLDER1 = HOME + "folder1";
    private static final String FOLDER1_INNER = FOLDER1 + SEP + "folder1";
    private static final String CUSTOM_DIR = HOME + "custom";
    private static final String CACHE_DIR = HOME + "CacheDir";
    private static final String STALE_DIR = HOME + "StaleDir";

    private static String join(String base, String name) {
        if (base == null || base.isEmpty()) {
            return name;
        }
        char last = base.charAt(base.length() - 1);
        return (last == '/' || last == '\\') ? base + name : base + SEP + name;
    }

    @Start
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        stage.setScene(AppTheme.scene(root));
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

        Field ffsField = UnifiedFileManagerDialogController.class.getDeclaredField("favoriteFolderService");
        ffsField.setAccessible(true);
        ffsField.set(controller, favoriteFolderService);

        mockSshService = new MockSSHService();
        Field sshFactoryField = UnifiedFileManagerDialogController.class.getDeclaredField("sshServiceFactory");
        sshFactoryField.setAccessible(true);
        SSHServiceFactory factory = () -> mockSshService;
        sshFactoryField.set(controller, factory);

        WaitForAsyncUtils.asyncFx(() -> {
            controller.setServerCatalog(serverManagementService);
            controller.setPreferenceService(new InMemoryPreferenceService());
        }).get();

        Field cacheField = UnifiedFileManagerDialogController.class.getDeclaredField("directoryCache");
        cacheField.setAccessible(true);
        Map<?, ?> cache = (Map<?, ?>) cacheField.get(controller);
        cache.clear();

        Platform.runLater(() -> {
            try {
                @SuppressWarnings("rawtypes")
                TreeView locationTree = getField("locationTree");
                if (locationTree != null && locationTree.getRoot() != null
                        && !locationTree.getRoot().getChildren().isEmpty()) {
                    Object local = locationTree.getRoot().getChildren().get(0);
                    if (locationTree.getSelectionModel().getSelectedItem() != local) {
                        locationTree.getSelectionModel().select(local);
                    }
                }
            } catch (Exception ignored) {}
        });
        Thread.sleep(500);
        WaitForAsyncUtils.waitForFxEvents();
        localFileService.callCount = 0;
    }

    private void forceRefreshLocation() {
        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("refreshCurrentPath");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void switchingLocationStartsWithEmptyHistory(FxRobot robot) throws Exception {
        var locationClass = UnifiedFileManagerDialogController.LocationItem.class;
        var oldLocation = UnifiedFileManagerDialogController.LocationItem.of(
                new SSHServerModel("Old", "host", 22, "user"));
        robot.interact(() -> {
            try {
                Field location = UnifiedFileManagerDialogController.class.getDeclaredField("currentLocation");
                location.setAccessible(true); location.set(controller, oldLocation);
                Field path = UnifiedFileManagerDialogController.class.getDeclaredField("currentPath");
                path.setAccessible(true); path.set(controller, "/remote-only/path");
                var method = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleLocationSelected",
                        locationClass);
                method.setAccessible(true);
                Field connectionTask = UnifiedFileManagerDialogController.class.getDeclaredField("currentConnectTask");
                connectionTask.setAccessible(true);
                var staleTask = new Task<Boolean>() { @Override protected Boolean call() { return true; } };
                connectionTask.set(controller, staleTask);
                method.invoke(controller, UnifiedFileManagerDialogController.LocationItem.local());
                assertNull(connectionTask.get(controller));
                assertTrue(staleTask.isCancelled());
                Stack<String> history = getField("backHistory");
                assertTrue(history.isEmpty(), "Remote paths must never enter local history");
                Button back = getField("backButton");
                assertTrue(back.isDisabled());
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            catch (Exception e) { throw new AssertionError(e); }
        });
    }

    @Test
    void currentFolderFavoriteCanBeAddedAndRemoved(FxRobot robot) throws Exception {
        robot.interact(() -> {
            try {
                var method = UnifiedFileManagerDialogController.class.getDeclaredMethod("toggleCurrentFavorite");
                method.setAccessible(true);
                method.invoke(controller);
                Set<String> cache = getField("favoritePathsCache");
                assertTrue(cache.contains(pathField.getText()));
                Button button = getField("favoriteCurrentButton");
                assertNotNull(button.getTooltip());
                assertTrue(button.getTooltip().getText().toLowerCase().contains("remove"),
                        "favorite button tooltip must reflect the saved state");
                method.invoke(controller);
                assertFalse(cache.contains(pathField.getText()));
                assertTrue(button.getTooltip().getText().toLowerCase().contains("favorite"),
                        "favorite button tooltip must return to the add state");
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }

    @Test
    void doubleClickTailOpensLocalFileInTailMode(FxRobot robot) throws Exception {
        robot.interact(() -> {
            try {
                Field action = UnifiedFileManagerDialogController.class.getDeclaredField("doubleClickAction");
                action.setAccessible(true); action.set(controller, UnifiedFileManagerDialogController.OpenAction.TAIL);
                FileInfo file = new FileInfo(); file.setName("tail.log"); file.setPath(join(HOME, "tail.log"));
                file.setDirectory(false); file.setSourceType(FileInfo.SourceType.LOCAL);
                ObservableList<FileInfo> files = getField("allFiles"); files.setAll(file);
                fileTable.getSelectionModel().select(file);
                var method = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleFileDoubleClick");
                method.setAccessible(true); method.invoke(controller);
                assertEquals(UnifiedFileManagerDialogController.OpenAction.TAIL, controller.getOpenAction());
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }

    private void invokeControllerMethod(String methodName) {
        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod(methodName);
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
    }

    @Test
    public void testNavigationButtons(FxRobot robot) throws Exception {
        invokeControllerMethod("navigateHome");
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(HOME, pathField.getText());

        Platform.runLater(() -> fileTable.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleFileDoubleClick");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(FOLDER1, pathField.getText());

        Platform.runLater(() -> fileTable.getSelectionModel().select(1));
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleFileDoubleClick");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(FOLDER1_INNER, pathField.getText());

        invokeControllerMethod("navigateBack");
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(FOLDER1, pathField.getText());

        invokeControllerMethod("navigateForward");
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(FOLDER1_INNER, pathField.getText());

        invokeControllerMethod("navigateUp");
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(FOLDER1, pathField.getText());
    }

    @Test
    public void testNavigateViaPathEnter(FxRobot robot) throws Exception {
        robot.interact(() -> {
            var breadcrumb = (FilePathBreadcrumb) stage.getScene().lookup(".file-path-breadcrumb");
            breadcrumb.edit();
            pathField.setText(CUSTOM_DIR);
            pathField.requestFocus();
        });
        robot.press(javafx.scene.input.KeyCode.ENTER).release(javafx.scene.input.KeyCode.ENTER);
        Thread.sleep(200);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(CUSTOM_DIR, pathField.getText());
    }

    @Test
    public void testSearchFilteringAndClear(FxRobot robot) throws Exception {
        Platform.runLater(() -> {
            try {
                Method navigateMethod = UnifiedFileManagerDialogController.class.getDeclaredMethod("navigateTo", String.class);
                navigateMethod.setAccessible(true);
                navigateMethod.invoke(controller, HOME);
            } catch (Exception e) {}
        });
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        int allFiles = fileTable.getItems().size();

        Platform.runLater(() -> searchField.setText("file1"));
        Thread.sleep(500);
        WaitForAsyncUtils.waitForFxEvents();
        int filtered = fileTable.getItems().size();

        Platform.runLater(() -> searchField.setText(""));
        Thread.sleep(500);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(filtered > 0 && filtered < allFiles);
        assertEquals(allFiles, fileTable.getItems().size());
    }

    @Test
    public void testAddAndRemoveFavorites(FxRobot robot) throws Exception {
        invokeControllerMethod("navigateHome");
        Thread.sleep(1500);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> fileTable.getSelectionModel().select(0));
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();

        FileInfo selected = fileTable.getSelectionModel().getSelectedItem();
        assertNotNull(selected, "Item should be selected");

        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleAddToFavorites");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        Thread.sleep(200);
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(favoriteFolderService.isFavorite(selected.getPath(), "local"));

        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleRemoveFromFavorites");
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

        Platform.runLater(() -> fileTable.getSelectionModel().select(0));
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(openButton.isDisabled());
        assertTrue(previewButton.isDisabled());
        assertTrue(tailButton.isDisabled());

        Platform.runLater(() -> fileTable.getSelectionModel().select(1));
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(openButton.isDisabled());
        assertFalse(previewButton.isDisabled());
        assertFalse(tailButton.isDisabled());
    }

    @Test
    public void testLocalFileOpen(FxRobot robot) throws Exception {
        robot.clickOn("#homeButton");
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> fileTable.getSelectionModel().select(2));
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleOpen");
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
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("navigateTo", String.class);
                m.setAccessible(true);
                m.invoke(controller, CACHE_DIR);
            } catch (Exception e) {}
        });
        Thread.sleep(1000);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, localFileService.callCount);

        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("navigateTo", String.class);
                m.setAccessible(true);
                m.invoke(controller, CACHE_DIR);
            } catch (Exception e) {}
        });
        Thread.sleep(200);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, localFileService.callCount);

        Label status = getField("statusLabel");
        assertNotNull(status);
        assertFalse(status.getText().toLowerCase(Locale.ROOT).contains("cache"),
                "the footer must not mention cache internals");
    }

    @Test
    public void testStaleWhileRevalidateRendersImmediately(FxRobot robot) throws Exception {
        Field cacheField = UnifiedFileManagerDialogController.class.getDeclaredField("directoryCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> cache = (Map<String, Object>) cacheField.get(controller);

        List<FileInfo> staleFiles = new ArrayList<>();
        FileInfo f = new FileInfo();
        f.setName("stale-file.log");
        f.setSize(1234L);
        staleFiles.add(f);

        Class<?> cacheEntryClass = Class.forName("com.seeloggyplus.features.files.presentation.UnifiedFileManagerDialogController$CacheEntry");
        Constructor<?> ctor = cacheEntryClass.getDeclaredConstructor(List.class);
        ctor.setAccessible(true);
        Object staleEntry = ctor.newInstance(staleFiles);

        Field tsField = cacheEntryClass.getDeclaredField("timestamp");
        tsField.setAccessible(true);
        tsField.set(staleEntry, System.currentTimeMillis() - (10 * 60 * 1000));

        cache.put("local:" + STALE_DIR, staleEntry);

        localFileService.simulateDelay = true;
        int initialCalls = localFileService.callCount;

        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("navigateTo", String.class);
                m.setAccessible(true);
                m.invoke(controller, STALE_DIR);
            } catch (Exception e) {}
        });

        Thread.sleep(50);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1.0, fileTable.getOpacity(), "Table must remain full opacity (not dimmed) when showing stale cache");
        assertFalse(fileTable.getItems().isEmpty(), "Stale files must be displayed immediately without waiting for network");
        assertEquals("stale-file.log", fileTable.getItems().get(0).getName());

        Thread.sleep(800);
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(localFileService.callCount > initialCalls, "Background revalidation must have run");
    }

    @Test
    public void testSortingBySize(FxRobot robot) {
        robot.clickOn("#homeButton");
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(fileTable.getColumns().get(2).getComparator());
    }

    @Test
    public void testAutoRefreshOnFocus(FxRobot robot) throws Exception {
        Field suppressed = UnifiedFileManagerDialogController.class.getDeclaredField("suppressAutoRefresh");
        suppressed.setAccessible(true);
        suppressed.setBoolean(controller, true);
        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleWindowGainedFocus");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(suppressed.getBoolean(controller));
    }

    @Test
    public void testDirectoryLoadFailure(FxRobot robot) throws Exception {
        int callsBefore = localFileService.callCount;
        localFileService.throwError = true;
        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("navigateTo", String.class);
                m.setAccessible(true);
                m.invoke(controller, HOME + SEP + "missing");
            } catch (Exception e) {}
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> localFileService.callCount > callsBefore);
        assertTrue(localFileService.callCount > callsBefore);
    }

    @Test
    public void testNavigateBeyondRoot(FxRobot robot) {
        robot.clickOn("#homeButton");
        WaitForAsyncUtils.waitForFxEvents();
        String homePath = pathField.getText();

        robot.clickOn("#upButton");
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(homePath, pathField.getText());
    }

    @Test
    public void testTailLocalFile(FxRobot robot) {
        robot.clickOn("#homeButton");
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> fileTable.getSelectionModel().select(2));
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleTail");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(UnifiedFileManagerDialogController.OpenAction.TAIL, controller.getOpenAction());
    }

    @Test
    public void testServerConnectionSuccess(FxRobot robot) throws Exception {
        Platform.runLater(() -> {
            try {
                Class<?> locationItemClass = Class.forName("com.seeloggyplus.features.files.presentation.UnifiedFileManagerDialogController$LocationItem");
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleLocationSelected", locationItemClass);
                m.setAccessible(true);
                SSHServerModel s = new SSHServerModel();
                s.setId("server1"); s.setName("Server 1"); s.setHost("test.com"); s.setUsername("user"); s.setPassword("pass");
                Object item = locationItemClass.getDeclaredMethod("of", SSHServerModel.class).invoke(null, s);
                m.invoke(controller, item);
            } catch (Exception e) {}
        });
        Thread.sleep(1500);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("/", pathField.getText());
        assertTrue(mockSshService.connectCalled);
    }

    @Test
    public void testServerConnectionFailure(FxRobot robot) throws Exception {
        mockSshService.simulateFailure = true;

        Platform.runLater(() -> {
            try {
                Class<?> locationItemClass = Class.forName("com.seeloggyplus.features.files.presentation.UnifiedFileManagerDialogController$LocationItem");
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleLocationSelected", locationItemClass);
                m.setAccessible(true);
                SSHServerModel s = new SSHServerModel();
                s.setId("server1"); s.setName("Server Fail"); s.setHost("fail.com"); s.setUsername("user"); s.setPassword("pass");
                Object item = locationItemClass.getDeclaredMethod("of", SSHServerModel.class).invoke(null, s);
                m.invoke(controller, item);
            } catch (Exception e) {}
        });
        Thread.sleep(1500);
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(mockSshService.connectCalled);
        assertNull(controller.getActiveServer(), "a failed connect must fall back to a local location");
        assertEquals(localFileService.getHomeDirectory(), pathField.getText(),
                "the path must show the local home after the fallback");
    }

    @Test
    public void testRemoteFileTailAction(FxRobot robot) throws Exception {
        testServerConnectionSuccess(robot);

        Platform.runLater(() -> fileTable.getSelectionModel().select(0));
        Thread.sleep(200);
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(tailButton.isDisabled());

        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleTail");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        Thread.sleep(200);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(UnifiedFileManagerDialogController.OpenAction.TAIL, controller.getOpenAction());
    }

    private FileInfo waitForTableItem(String name, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            WaitForAsyncUtils.waitForFxEvents();
            FileInfo found = fileTable.getItems().stream()
                    .filter(f -> name.equals(f.getName()))
                    .findFirst()
                    .orElse(null);
            if (found != null) {
                return found;
            }
            Thread.sleep(50);
        }
        return null;
    }

    @Test
    public void testOwnerColumnDisplay(FxRobot robot) throws Exception {
        invokeControllerMethod("navigateHome");
        Thread.sleep(300);
        WaitForAsyncUtils.waitForFxEvents();

        TableColumn<FileInfo, String> ownerCol = getField("ownerColumn");
        assertNotNull(ownerCol, "Owner column should exist");

        FileInfo file1 = waitForTableItem("file1.txt", 5000);
        assertNotNull(file1, "file1.txt should be present");
        assertEquals("localuser", file1.getOwner());
        assertEquals("localuser", ownerCol.getCellObservableValue(file1).getValue());

        Platform.runLater(() -> {
            try {
                Class<?> locationItemClass = Class.forName(
                        "com.seeloggyplus.features.files.presentation.UnifiedFileManagerDialogController$LocationItem");
                Method m = UnifiedFileManagerDialogController.class
                        .getDeclaredMethod("handleLocationSelected", locationItemClass);
                m.setAccessible(true);
                SSHServerModel s = new SSHServerModel();
                s.setId("server1");
                s.setName("Server 1");
                s.setHost("test.com");
                s.setUsername("user");
                s.setPassword("pass");
                s.setDefaultPath("/");
                Object item = locationItemClass.getDeclaredMethod("of", SSHServerModel.class).invoke(null, s);
                m.invoke(controller, item);
            } catch (Exception ignored) {}
        });

        FileInfo remoteFile = waitForTableItem("remoteFile.log", 8000);
        assertNotNull(remoteFile, "remoteFile.log should be present");
        assertEquals("remoteuser", remoteFile.getOwner());
        assertEquals("remoteuser", ownerCol.getCellObservableValue(remoteFile).getValue());
    }

    class MockLocalFileService implements LocalFileService {
        public int callCount = 0;
        public boolean throwError = false;
        public boolean simulateDelay = false;

        @Override
        public String getHomeDirectory() {
            return HOME;
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
            FileInfo d1 = new FileInfo(); d1.setName("folder1"); d1.setDirectory(true); d1.setPath(join(directoryPath, "folder1")); d1.setOwner("localuser");
            FileInfo f1 = new FileInfo(); f1.setName("file1.txt"); f1.setDirectory(false); f1.setPath(join(directoryPath, "file1.txt")); f1.setSize(1024L); f1.setOwner("localuser");
            FileInfo f2 = new FileInfo(); f2.setName("file2.log"); f2.setDirectory(false); f2.setPath(join(directoryPath, "file2.log")); f2.setSize(5000000L); f2.setOwner("localuser");
            list.add(d1);
            list.add(f1);
            list.add(f2);
            return list;
        }
    }

    class MockServerManagementService implements ServerCatalog {
        @Override public void reorderServers(List<String> ids) { throw new UnsupportedOperationException(); }
        @Override public List<String> getGroupNames() { return List.of(); }
        @Override public void createGroup(String name) {}
        @Override public void renameGroup(String oldName, String newName) {}
        @Override public void deleteGroup(String name) {}
        @Override
        public void saveServer(SSHServerModel server) {}
        @Override
        public void updateServerLastUsed(String id) {}
        @Override
        public List<SSHServerModel> getAllServers() {
            SSHServerModel s = new SSHServerModel();
            s.setId("server1"); s.setName("Server 1"); s.setHost("localhost");

            s.setPassword("dummy");
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
            f1.setOwner("remoteuser");
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
                Method navigateMethod = UnifiedFileManagerDialogController.class.getDeclaredMethod("navigateTo", String.class);
                navigateMethod.setAccessible(true);
                navigateMethod.invoke(controller, HOME);
            } catch (Exception e) {}
        });
        Thread.sleep(1500);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> fileTable.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleFileDoubleClick");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        Thread.sleep(1500);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(FOLDER1, pathField.getText());

        Platform.runLater(() -> fileTable.getSelectionModel().select(2));
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleFileDoubleClick");
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
        List<FileInfo> previews = new CopyOnWriteArrayList<>();
        controller.setHost(recordingHost(previews, null));
        robot.clickOn("#homeButton");
        Thread.sleep(1500);
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> fileTable.getSelectionModel().select(1));
        WaitForAsyncUtils.waitForFxEvents();

        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handlePreview");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !previews.isEmpty());
        assertFalse(previews.isEmpty());
    }

    @Test
    public void testManageServersAction(FxRobot robot) throws Exception {
        AtomicInteger manageCalls = new AtomicInteger();
        controller.setHost(recordingHost(null, manageCalls));
        Platform.runLater(() -> {
            try {
                Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod("handleManageServers");
                m.setAccessible(true);
                m.invoke(controller);
            } catch (Exception e) {}
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> manageCalls.get() > 0);
        assertEquals(1, manageCalls.get());
    }

    private static FileManagerHost recordingHost(List<FileInfo> previews, AtomicInteger manageCalls) {
        return new FileManagerHost() {
            @Override
            public FileManagerHost.FindInFilesResult openFindInFiles(SSHService sshService, SSHServerModel server,
                    String path, int maxTailWindow) {
                return null;
            }

            @Override
            public void openPreview(FileInfo file, SSHService sshService) {
                if (previews != null) previews.add(file);
            }

            @Override
            public void openServerManagement() {
                if (manageCalls != null) manageCalls.incrementAndGet();
            }
        };
    }

    private static class InMemoryPreferenceService implements PreferenceService {
        private final Map<String, String> store = new HashMap<>();

        @Override
        public void savePreferences(Preference preferences) {
            if (preferences != null) {
                store.put(preferences.getCode(), preferences.getValue());
            }
        }

        @Override
        public void updatePreferences(Preference preferences) {
            if (preferences != null) {
                store.put(preferences.getCode(), preferences.getValue());
            }
        }

        @Override
        public Optional<String> getPreferencesByCode(String code) {
            return Optional.ofNullable(store.get(code));
        }

        @Override
        public List<Preference> getListPreferences() {
            List<Preference> list = new ArrayList<>();
            store.forEach((k, v) -> list.add(new Preference(k, v)));
            return list;
        }

        @Override
        public void saveOrUpdatePreferences(Preference preferences) {
            if (preferences != null) {
                store.put(preferences.getCode(), preferences.getValue());
            }
        }
    }
}
