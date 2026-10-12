package com.seeloggyplus.features.files.presentation;

import com.seeloggyplus.features.files.application.FavoriteFolderService;
import com.seeloggyplus.features.files.application.LocalFileService;
import com.seeloggyplus.features.files.domain.FavoriteFolder;
import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.shared.dto.RemoteFileInfo;
import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.servers.ServerCatalog;
import com.seeloggyplus.shared.settings.PreferenceService;
import com.seeloggyplus.shared.ssh.SSHServiceFactory;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(ApplicationExtension.class)
class UnifiedFileManagerRemoteRestoreTest {

    private UnifiedFileManagerDialogController controller;

    @Start
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();
    }

    @Test
    void productionWiringOrderRestoresRemoteLastLocation(FxRobot robot) throws Exception {
        inject("localFileService", new NoOpLocalFileService());
        inject("favoriteFolderService", new NoOpFavoriteFolderService());

        SSHServerModel server = new SSHServerModel("prod", "prod.example.com", 22, "deploy");
        server.setId("srv-prod");
        server.setPassword("secret");
        server.setDefaultPath("/var/log");

        ServerCatalog catalog = new FakeCatalog(List.of(server));
        SavedLocationPreference preferences = new SavedLocationPreference(server.getName());
        SSHServiceFactory factory = ConnectedSshService::new;

        robot.interact(() -> {
            controller.setServerCatalog(catalog);
            controller.setPreferenceService(preferences);
        });
        robot.interact(() -> controller.setSshServiceFactory(factory));

        assertNotNull(controller.getActiveServer(),
                "remote last location must be restored once all dependencies are injected");
        assertEquals("prod", controller.getActiveServer().getName());
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = UnifiedFileManagerDialogController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(controller, value);
    }

    static class NoOpLocalFileService implements LocalFileService {
        @Override
        public String getHomeDirectory() {
            return System.getProperty("user.home");
        }

        @Override
        public List<FileInfo> listFiles(String directoryPath) throws IOException {
            return List.of();
        }
    }

    static class NoOpFavoriteFolderService implements FavoriteFolderService {
        @Override
        public void addFavorite(String name, String path, String locationId) {
        }

        @Override
        public void removeFavorite(int id) {
        }

        @Override
        public List<FavoriteFolder> getFavoritesForLocation(String locationId) {
            return List.of();
        }

        @Override
        public boolean isFavorite(String path, String locationId) {
            return false;
        }
    }

    static class SavedLocationPreference implements PreferenceService {
        private final Map<String, String> store = new HashMap<>();

        SavedLocationPreference(String lastLocation) {
            store.put("file_manager_last_location", lastLocation);
        }

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
        public List<Preference> getListPreferences() {
            return List.of();
        }
    }

    static class FakeCatalog implements ServerCatalog {
        private final List<SSHServerModel> servers;

        FakeCatalog(List<SSHServerModel> servers) {
            this.servers = servers;
        }

        @Override
        public List<SSHServerModel> getAllServers() {
            return servers;
        }

        @Override
        public SSHServerModel getServerById(String id) {
            return servers.stream().filter(server -> id != null && id.equals(server.getId())).findFirst().orElse(null);
        }

        @Override
        public void saveServer(SSHServerModel server) {
        }

        @Override
        public void reorderServers(List<String> orderedIds) {
        }

        @Override
        public List<String> getGroupNames() {
            return List.of();
        }

        @Override
        public void createGroup(String name) {
        }

        @Override
        public void renameGroup(String oldName, String newName) {
        }

        @Override
        public void deleteGroup(String name) {
        }

        @Override
        public void updateServerLastUsed(String serverId) {
        }
    }

    static class ConnectedSshService extends SSHServiceImpl {
        @Override
        public boolean connect(String host, int port, String username, String password) {
            return true;
        }

        @Override
        public boolean connect(String host, int port, String username, String password, long ttlMillis) {
            return true;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public List<RemoteFileInfo> listFiles(String remotePath) throws IOException {
            return List.of();
        }
    }
}
