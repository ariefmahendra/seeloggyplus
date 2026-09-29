package com.seeloggyplus.controller;

import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.model.Preference;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.PreferenceService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileManagerStatePersistenceTest {

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    static class InMemoryPreferenceService implements PreferenceService {
        private final Map<String, String> store = new HashMap<>();

        @Override
        public Optional<String> getPreferencesByCode(String code) {
            return Optional.ofNullable(store.get(code));
        }

        @Override
        public void savePreferences(Preference preference) {
            store.put(preference.getCode(), preference.getValue());
        }

        @Override
        public void updatePreferences(Preference preference) {
            store.put(preference.getCode(), preference.getValue());
        }

        @Override
        public void saveOrUpdatePreferences(Preference preference) {
            store.put(preference.getCode(), preference.getValue());
        }

        @Override
        public java.util.List<Preference> getListPreferences() {
            return java.util.Collections.emptyList();
        }
    }

    @Test
    @DisplayName("Should save and restore last opened location and file")
    void testSaveAndRestoreLastLocationAndFile() throws Exception {
        UnifiedFileManagerDialogController controller = new UnifiedFileManagerDialogController();
        InMemoryPreferenceService prefService = new InMemoryPreferenceService();

        // Inject preferenceService
        Field prefField = UnifiedFileManagerDialogController.class.getDeclaredField("preferenceService");
        prefField.setAccessible(true);
        prefField.set(controller, prefService);

        // Inject locationTree (local node + server node)
        Field locTreeField = UnifiedFileManagerDialogController.class.getDeclaredField("locationTree");
        locTreeField.setAccessible(true);
        TreeView<UnifiedFileManagerDialogController.LocationItem> locationTree = new TreeView<>();
        locationTree.setShowRoot(false);

        var localItem = UnifiedFileManagerDialogController.LocationItem.local();
        SSHServerModel server2 = new SSHServerModel();
        server2.setId("server2");
        server2.setName("Server 2");
        var server2Item = UnifiedFileManagerDialogController.LocationItem.of(server2);

        TreeItem<UnifiedFileManagerDialogController.LocationItem> root = new TreeItem<>();
        root.setExpanded(true);
        root.getChildren().add(new TreeItem<>(localItem));
        root.getChildren().add(new TreeItem<>(server2Item));
        locationTree.setRoot(root);
        locTreeField.set(controller, locationTree);

        // Inject currentLocation
        Field curLocField = UnifiedFileManagerDialogController.class.getDeclaredField("currentLocation");
        curLocField.setAccessible(true);
        curLocField.set(controller, server2Item);

        // Test saving file
        FileInfo fileA = new FileInfo();
        fileA.setName("file_a.log");
        fileA.setPath("/var/log/a/file_a.log");
        fileA.setDirectory(false);

        Method saveMethod = UnifiedFileManagerDialogController.class.getDeclaredMethod("saveLastOpenedFile", FileInfo.class);
        saveMethod.setAccessible(true);
        saveMethod.invoke(controller, fileA);

        // Verify preferences were saved
        assertEquals("Server 2", prefService.getPreferencesByCode("file_manager_last_location").orElse(null));
        assertEquals("file_a.log", prefService.getPreferencesByCode("file_manager_last_file_Server 2").orElse(null));

        // Test restoreLastLocation selects Server 2 (index 1)
        Method restoreLocMethod = UnifiedFileManagerDialogController.class.getDeclaredMethod("restoreLastLocation");
        restoreLocMethod.setAccessible(true);
        restoreLocMethod.invoke(controller);

        assertEquals(1, locationTree.getSelectionModel().getSelectedIndex());
        assertEquals("Server 2",
                locationTree.getSelectionModel().getSelectedItem().getValue().getServer().getName());
    }

    @Test
    @DisplayName("Should select matching file when restoring file selection")
    void testRestoreFileSelection() throws Exception {
        UnifiedFileManagerDialogController controller = new UnifiedFileManagerDialogController();
        InMemoryPreferenceService prefService = new InMemoryPreferenceService();
        Field pathField = UnifiedFileManagerDialogController.class.getDeclaredField("currentPath");
        pathField.setAccessible(true);
        pathField.set(controller, "C:\\test");

        prefService.saveOrUpdatePreferences(new Preference("file_manager_last_file_local", "target.log"));
        prefService.saveOrUpdatePreferences(new Preference("file_manager_last_file_dir_local", "C:\\test"));

        Field prefField = UnifiedFileManagerDialogController.class.getDeclaredField("preferenceService");
        prefField.setAccessible(true);
        prefField.set(controller, prefService);

        Field tableField = UnifiedFileManagerDialogController.class.getDeclaredField("fileTable");
        tableField.setAccessible(true);
        TableView<FileInfo> table = new TableView<>();
        tableField.set(controller, table);

        FileInfo f1 = new FileInfo();
        f1.setName("other.log");
        f1.setDirectory(false);

        FileInfo target = new FileInfo();
        target.setName("target.log");
        target.setDirectory(false);

        table.setItems(FXCollections.observableArrayList(f1, target));

        Method restoreMethod = UnifiedFileManagerDialogController.class.getDeclaredMethod("restoreFileSelection");
        restoreMethod.setAccessible(true);
        restoreMethod.invoke(controller);

        assertEquals(target, table.getSelectionModel().getSelectedItem());
    }
}

