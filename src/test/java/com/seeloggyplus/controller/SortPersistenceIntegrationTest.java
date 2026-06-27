package com.seeloggyplus.controller;

import com.seeloggyplus.model.FavoriteFolder;
import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.model.Preference;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.FavoriteFolderService;
import com.seeloggyplus.service.LocalFileService;
import com.seeloggyplus.service.PreferenceService;
import com.seeloggyplus.service.ServerManagementService;
import com.seeloggyplus.service.impl.SSHServiceImpl;
import com.seeloggyplus.dto.RemoteFileInfo;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for sort persistence save/restore integration.
 * Validates: Requirements 1.1, 1.3, 2.1, 2.2, 2.3, 3.1, 3.2, 4.1, 4.2
 */
@ExtendWith(ApplicationExtension.class)
public class SortPersistenceIntegrationTest {

    private UnifiedFileManagerDialogController controller;
    private TableView<FileInfo> fileTable;
    private InMemoryPreferenceService mockPrefs;

    @Start
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(root));
        stage.show();

        fileTable = getField("fileTable");
    }

    @BeforeEach
    public void setup() throws Exception {
        mockPrefs = new InMemoryPreferenceService();
        setField(controller, "preferenceService", mockPrefs);
        setField(controller, "localFileService", new StubLocalFileService());
        setField(controller, "serverManagementService", new StubServerManagementService());
        setField(controller, "favoriteFolderService", new StubFavoriteFolderService());
        setField(controller, "sshServiceFactory", (java.util.function.Supplier<SSHServiceImpl>) StubSSHService::new);

        // Clear directory cache
        Map<?, ?> cache = getField("directoryCache");
        cache.clear();
    }

    // -------------------------------------------------------------------------
    // Test: save on column sort change
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("saveSortOrdering() persists correct key and encoded value on sort change")
    void saveOnColumnSortChange() throws Exception {
        // Set local location (no server)
        setLocalLocation();

        // Simulate sort by nameColumn ASCENDING
        Platform.runLater(() -> {
            TableColumn<FileInfo, ?> nameCol = findColumn("nameColumn");
            assertNotNull(nameCol, "nameColumn must exist");
            nameCol.setSortType(TableColumn.SortType.ASCENDING);
            fileTable.getSortOrder().setAll(nameCol);
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Verify preference was saved
        String saved = mockPrefs.store.get("file_manager_sort_local");
        assertEquals("nameColumn:ASCENDING", saved);
    }

    @Test
    @DisplayName("saveSortOrdering() persists DESCENDING correctly")
    void saveDescending() throws Exception {
        setLocalLocation();

        Platform.runLater(() -> {
            TableColumn<FileInfo, ?> modCol = findColumn("modifiedColumn");
            assertNotNull(modCol);
            modCol.setSortType(TableColumn.SortType.DESCENDING);
            fileTable.getSortOrder().setAll(modCol);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("modifiedColumn:DESCENDING", mockPrefs.store.get("file_manager_sort_local"));
    }

    // -------------------------------------------------------------------------
    // Test: restore on loadFiles
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("restoreSortOrdering() applies saved preference to fileTable sort order")
    void restoreAppliesSavedPreference() throws Exception {
        setLocalLocation();
        mockPrefs.store.put("file_manager_sort_local", "sizeColumn:DESCENDING");

        // Invoke restoreSortOrdering
        runOnFxAndWait(() -> invoke("restoreSortOrdering"));

        // Verify sort order
        assertEquals(1, fileTable.getSortOrder().size());
        TableColumn<FileInfo, ?> col = fileTable.getSortOrder().get(0);
        assertEquals("sizeColumn", col.getId());
        assertEquals(TableColumn.SortType.DESCENDING, col.getSortType());
    }

    // -------------------------------------------------------------------------
    // Test: fallback when no preference exists
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("restoreSortOrdering() falls back to empty sort order when no preference exists")
    void fallbackWhenNoPreference() throws Exception {
        setLocalLocation();
        // No preference stored — store is empty

        // Pre-set a sort order so we can verify it gets cleared
        Platform.runLater(() -> {
            TableColumn<FileInfo, ?> nameCol = findColumn("nameColumn");
            fileTable.getSortOrder().setAll(nameCol);
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Now restore — should clear
        // ponytail: suppress the listener from saving during this test
        mockPrefs.store.clear();
        runOnFxAndWait(() -> invoke("restoreSortOrdering"));

        assertTrue(fileTable.getSortOrder().isEmpty(), "Sort order should be cleared when no preference");
    }

    @Test
    @DisplayName("restoreSortOrdering() falls back on malformed preference (no colon)")
    void fallbackOnMalformedNoColon() throws Exception {
        setLocalLocation();
        mockPrefs.store.put("file_manager_sort_local", "nameColumnASCENDING");

        runOnFxAndWait(() -> invoke("restoreSortOrdering"));

        assertTrue(fileTable.getSortOrder().isEmpty());
    }

    @Test
    @DisplayName("restoreSortOrdering() falls back on unknown column id")
    void fallbackOnUnknownColumn() throws Exception {
        setLocalLocation();
        mockPrefs.store.put("file_manager_sort_local", "bogusColumn:ASCENDING");

        runOnFxAndWait(() -> invoke("restoreSortOrdering"));

        assertTrue(fileTable.getSortOrder().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Test: location isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Local and remote locations use different sort keys")
    void locationIsolation() throws Exception {
        // Save local sort
        setLocalLocation();
        Platform.runLater(() -> {
            TableColumn<FileInfo, ?> nameCol = findColumn("nameColumn");
            nameCol.setSortType(TableColumn.SortType.ASCENDING);
            fileTable.getSortOrder().setAll(nameCol);
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Save remote sort (different column & direction)
        setRemoteLocation("prod-web");
        Platform.runLater(() -> {
            TableColumn<FileInfo, ?> sizeCol = findColumn("sizeColumn");
            sizeCol.setSortType(TableColumn.SortType.DESCENDING);
            fileTable.getSortOrder().setAll(sizeCol);
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Verify both stored independently
        assertEquals("nameColumn:ASCENDING", mockPrefs.store.get("file_manager_sort_local"));
        assertEquals("sizeColumn:DESCENDING", mockPrefs.store.get("file_manager_sort_prod-web"));

        // Restore local — should get nameColumn ASCENDING
        setLocalLocation();
        runOnFxAndWait(() -> invoke("restoreSortOrdering"));
        assertEquals(1, fileTable.getSortOrder().size());
        assertEquals("nameColumn", fileTable.getSortOrder().get(0).getId());
        assertEquals(TableColumn.SortType.ASCENDING, fileTable.getSortOrder().get(0).getSortType());

        // Restore remote — should get sizeColumn DESCENDING
        setRemoteLocation("prod-web");
        runOnFxAndWait(() -> invoke("restoreSortOrdering"));
        assertEquals(1, fileTable.getSortOrder().size());
        assertEquals("sizeColumn", fileTable.getSortOrder().get(0).getId());
        assertEquals(TableColumn.SortType.DESCENDING, fileTable.getSortOrder().get(0).getSortType());
    }

    // -------------------------------------------------------------------------
    // Test: error resilience — no exception propagates on save failure
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("No exception propagates when PreferenceService throws on save")
    void noExceptionOnSaveFailure() throws Exception {
        setLocalLocation();
        mockPrefs.throwOnSave = true;

        // Should not throw
        assertDoesNotThrow(() -> {
            Platform.runLater(() -> {
                TableColumn<FileInfo, ?> nameCol = findColumn("nameColumn");
                nameCol.setSortType(TableColumn.SortType.ASCENDING);
                fileTable.getSortOrder().setAll(nameCol);
            });
            WaitForAsyncUtils.waitForFxEvents();
        });
    }

    @Test
    @DisplayName("No exception propagates when PreferenceService throws on restore")
    void noExceptionOnRestoreFailure() throws Exception {
        setLocalLocation();
        mockPrefs.throwOnGet = true;

        assertDoesNotThrow(() -> runOnFxAndWait(() -> invoke("restoreSortOrdering")));
        // Should fall back to empty sort order
        assertTrue(fileTable.getSortOrder().isEmpty());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @SuppressWarnings("unchecked")
    private <T> T getField(String name) throws Exception {
        Field f = UnifiedFileManagerDialogController.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(controller);
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private void invoke(String methodName) {
        try {
            Method m = UnifiedFileManagerDialogController.class.getDeclaredMethod(methodName);
            m.setAccessible(true);
            m.invoke(controller);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void runOnFxAndWait(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { action.run(); } finally { latch.countDown(); }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "FX action timed out");
        WaitForAsyncUtils.waitForFxEvents();
    }

    @SuppressWarnings("unchecked")
    private TableColumn<FileInfo, ?> findColumn(String fxId) {
        return fileTable.getColumns().stream()
            .filter(c -> fxId.equals(c.getId()))
            .findFirst().orElse(null);
    }

    private void setLocalLocation() throws Exception {
        Object localItem = createLocationItem("Local", null);
        Field f = UnifiedFileManagerDialogController.class.getDeclaredField("currentLocation");
        f.setAccessible(true);
        f.set(controller, localItem);
    }

    private void setRemoteLocation(String serverName) throws Exception {
        SSHServerModel server = new SSHServerModel();
        server.setName(serverName);
        Object remoteItem = createLocationItem(serverName, server);
        Field f = UnifiedFileManagerDialogController.class.getDeclaredField("currentLocation");
        f.setAccessible(true);
        f.set(controller, remoteItem);
    }

    private Object createLocationItem(String name, SSHServerModel server) throws Exception {
        Class<?> cls = Class.forName(
            "com.seeloggyplus.controller.UnifiedFileManagerDialogController$LocationItem");
        var ctor = cls.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return ctor.newInstance(name, null, server);
    }

    // =========================================================================
    // Stubs
    // =========================================================================

    /** In-memory PreferenceService with optional throw behavior */
    static class InMemoryPreferenceService implements PreferenceService {
        final Map<String, String> store = new HashMap<>();
        boolean throwOnSave = false;
        boolean throwOnGet = false;

        @Override public void savePreferences(Preference p) {
            if (throwOnSave) throw new RuntimeException("Mock save error");
            store.put(p.getCode(), p.getValue());
        }
        @Override public void updatePreferences(Preference p) {
            if (throwOnSave) throw new RuntimeException("Mock save error");
            store.put(p.getCode(), p.getValue());
        }
        @Override public Optional<String> getPreferencesByCode(String code) {
            if (throwOnGet) throw new RuntimeException("Mock get error");
            return Optional.ofNullable(store.get(code));
        }
        @Override public List<Preference> getListPreferences() { return List.of(); }
        @Override public void saveOrUpdatePreferences(Preference p) {
            if (throwOnSave) throw new RuntimeException("Mock save error");
            store.put(p.getCode(), p.getValue());
        }
    }

    static class StubLocalFileService implements LocalFileService {
        @Override public String getHomeDirectory() { return "C:\\"; }
        @Override public List<FileInfo> listFiles(String path) {
            List<FileInfo> files = new ArrayList<>();
            FileInfo d = new FileInfo(); d.setName("folder1"); d.setDirectory(true); d.setPath(path + "\\folder1");
            FileInfo f = new FileInfo(); f.setName("file.txt"); f.setDirectory(false); f.setPath(path + "\\file.txt"); f.setSize(100L);
            files.add(d); files.add(f);
            return files;
        }
    }

    static class StubServerManagementService implements ServerManagementService {
        @Override public void saveServer(SSHServerModel s) {}
        @Override public void deleteServer(String id) {}
        @Override public void updateServerLastUsed(String id) {}
        @Override public List<SSHServerModel> getAllServers() { return List.of(); }
        @Override public SSHServerModel getServerById(String id) { return null; }
    }

    static class StubFavoriteFolderService implements FavoriteFolderService {
        @Override public void addFavorite(String name, String path, String locationId) {}
        @Override public void removeFavorite(int id) {}
        @Override public List<FavoriteFolder> getFavoritesForLocation(String locationId) { return List.of(); }
        @Override public boolean isFavorite(String path, String locationId) { return false; }
    }

    static class StubSSHService extends SSHServiceImpl {
        @Override public boolean connect(String h, int p, String u, String pw) { return false; }
        @Override public boolean connect(String h, int p, String u, String pw, long ttl) { return false; }
        @Override public void disconnect() {}
        @Override public boolean isConnected() { return false; }
        @Override public List<RemoteFileInfo> listFiles(String path) { return List.of(); }
    }
}
