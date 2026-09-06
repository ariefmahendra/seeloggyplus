package com.seeloggyplus.controller;

import com.seeloggyplus.model.FileInfo;
import com.seeloggyplus.model.Preference;
import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.service.PreferenceService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.ListView;
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

        // Inject locationListView
        Field locListField = UnifiedFileManagerDialogController.class.getDeclaredField("locationListView");
        locListField.setAccessible(true);
        ListView<Object> locationListView = new ListView<>();
        locListField.set(controller, locationListView);

        // Create LocationItem class and items
        Class<?> locItemClass = Class.forName("com.seeloggyplus.controller.UnifiedFileManagerDialogController$LocationItem");
        Object localItem = locItemClass.getConstructors()[0].newInstance("Local Drive", null, null);

        SSHServerModel server2 = new SSHServerModel();
        server2.setId("server2");
        server2.setName("Server 2");
        Object server2Item = locItemClass.getConstructors()[0].newInstance("Server 2", null, server2);

        locationListView.setItems(FXCollections.observableArrayList(localItem, server2Item));

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

        assertEquals(1, locationListView.getSelectionModel().getSelectedIndex());
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

