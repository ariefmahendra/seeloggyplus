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
    private Parent root;
    private Stage stage;
    private final java.util.concurrent.atomic.AtomicInteger factoryCalls = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.List<javafx.scene.control.Dialog<?>> dialogs = new java.util.ArrayList<>();
    private final ConnectedSshService ssh = new ConnectedSshService();
    private SSHServerModel server;
    private SavedLocationPreference preferences;
    private final String localPath = java.nio.file.Path.of(System.getProperty("user.home"), "last-local").toString();

    @Start
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
        this.stage = stage;
        root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();
    }

    @Test
    void startupRestoresLocalFolderWithoutConnectingToLastServer() throws Exception {
        configure(false, false);
        await(() -> localPath.equals(((javafx.scene.control.TextField) root.lookup("#pathField")).getText()));
        assertEquals(0, factoryCalls.get());
        assertEquals(0, ssh.connectCalls.get());
        org.junit.jupiter.api.Assertions.assertNull(controller.getActiveServer());
        org.junit.jupiter.api.Assertions.assertTrue(dialogs.isEmpty());
        onFx(() -> {
            var hint = (javafx.scene.control.Label) root.lookup("#lastServerLabel");
            assertNotNull(hint);
            org.junit.jupiter.api.Assertions.assertTrue(hint.getText().contains("prod"));
            org.junit.jupiter.api.Assertions.assertTrue(hint.getTooltip().getText().contains("Not connected"));
            org.junit.jupiter.api.Assertions.assertTrue(root.lookup("#lastServerBox").isVisible());
        });
    }

    @Test
    void explicitConnectRestoresRemoteFolderAndPreservesHistoryWhenReturningLocal() throws Exception {
        configure(true, false);
        onFx(() -> ((javafx.scene.control.Button) root.lookup("#connectLastServerButton")).fire());
        await(() -> "/remembered/logs".equals(((javafx.scene.control.TextField) root.lookup("#pathField")).getText())
                && "srv-prod".equals(preferences.getPreferencesByCode("file_manager_last_server_id").orElse(null))
                && ssh.listedPaths.contains("/remembered/logs"));
        assertEquals(1, factoryCalls.get());
        assertEquals(1, ssh.connectCalls.get());
        assertEquals("prod", controller.getActiveServer().getName());
        onFx(() -> {
            org.junit.jupiter.api.Assertions.assertFalse(root.lookup("#lastServerBox").isVisible());
            controller.handleLocationClick(UnifiedFileManagerDialogController.LocationItem.local());
        });
        await(() -> controller.getActiveServer() == null && localPath.equals(((javafx.scene.control.TextField) root.lookup("#pathField")).getText()));
        assertEquals("srv-prod", preferences.getPreferencesByCode("file_manager_last_server_id").orElse(null));
        onFx(() -> org.junit.jupiter.api.Assertions.assertTrue(root.lookup("#lastServerBox").isVisible()));
    }

    @Test
    void reconnectingToCachedServerUpdatesRememberedServer() throws Exception {
        configure(true, false);
        SSHServerModel other = new SSHServerModel("other", "other.example.com", 22, "deploy");
        other.setId("srv-other"); other.setPassword("secret"); other.setDefaultPath("/other/logs");
        onFx(() -> {
            controller.setServerCatalog(new FakeCatalog(List.of(server, other)));
            ((javafx.scene.control.Button) root.lookup("#connectLastServerButton")).fire();
        });
        await(() -> ssh.listedPaths.contains("/remembered/logs")
                && !((javafx.scene.control.ProgressIndicator) root.lookup("#progressIndicator")).isVisible());
        onFx(() -> controller.handleLocationClick(UnifiedFileManagerDialogController.LocationItem.of(other)));
        await(() -> "srv-other".equals(preferences.getPreferencesByCode("file_manager_last_server_id").orElse(null)));
        onFx(() -> controller.handleLocationClick(UnifiedFileManagerDialogController.LocationItem.of(server)));
        await(() -> controller.getActiveServer() != null && "srv-prod".equals(controller.getActiveServer().getId())
                && "srv-prod".equals(preferences.getPreferencesByCode("file_manager_last_server_id").orElse(null)));
        assertEquals("srv-prod", preferences.getPreferencesByCode("file_manager_last_server_id").orElse(null));
    }

    @Test
    void missingPasswordIsRequestedOnlyAfterExplicitConnect() throws Exception {
        configure(false, false);
        org.junit.jupiter.api.Assertions.assertTrue(dialogs.isEmpty());
        onFx(() -> ((javafx.scene.control.Button) root.lookup("#connectLastServerButton")).fire());
        await(() -> controller.getActiveServer() == null);
        assertEquals(1, dialogs.size());
        assertEquals(0, ssh.connectCalls.get());
    }

    @Test
    void failedExplicitConnectionReturnsLocalAndKeepsRetryAvailable() throws Exception {
        configure(true, false);
        ssh.connectResult = false;
        onFx(() -> ((javafx.scene.control.Button) root.lookup("#connectLastServerButton")).fire());
        await(() -> controller.getActiveServer() == null && dialogs.size() == 1
                && localPath.equals(((javafx.scene.control.TextField) root.lookup("#pathField")).getText()));
        assertEquals("srv-prod", preferences.getPreferencesByCode("file_manager_last_server_id").orElse(null));
        onFx(() -> {
            org.junit.jupiter.api.Assertions.assertFalse(((javafx.scene.control.Button) root.lookup("#connectLastServerButton")).isDisabled());
            ssh.connectResult = true;
            ((javafx.scene.control.Button) root.lookup("#connectLastServerButton")).fire();
        });
        await(() -> controller.getActiveServer() != null && ssh.listedPaths.contains("/remembered/logs"));
        assertEquals(2, ssh.connectCalls.get());
    }

    @Test
    void preferencesInjectedBeforeCatalogStillRestoreLocalWithoutConnecting() throws Exception {
        configure(true, true);
        await(() -> localPath.equals(((javafx.scene.control.TextField) root.lookup("#pathField")).getText()));
        org.junit.jupiter.api.Assertions.assertNull(controller.getActiveServer());
        assertEquals(0, factoryCalls.get());
        onFx(() -> org.junit.jupiter.api.Assertions.assertTrue(root.lookup("#lastServerBox").isVisible()));
    }

    @Test
    void deletedRememberedServerIsIgnoredWithoutDialogs() throws Exception {
        configure(true, false);
        onFx(() -> controller.setServerCatalog(new FakeCatalog(List.of())));
        assertEquals(0, factoryCalls.get());
        org.junit.jupiter.api.Assertions.assertNull(controller.getActiveServer());
        org.junit.jupiter.api.Assertions.assertTrue(dialogs.isEmpty());
        onFx(() -> org.junit.jupiter.api.Assertions.assertFalse(root.lookup("#lastServerBox").isVisible()));
    }

    @Test
    void rememberedServerUsesStableIdAfterRenameAndHintRemainsReadableAcrossThemes() throws Exception {
        configure(true, false);
        server.setName("renamed prod");
        onFx(() -> controller.setServerCatalog(new FakeCatalog(List.of(server))));
        onFx(() -> {
            try {
                for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                    AppTheme.setTheme(theme); root.applyCss(); root.layout();
                    var hint = (javafx.scene.control.Label) root.lookup("#lastServerLabel");
                    var connect = (javafx.scene.control.Button) root.lookup("#connectLastServerButton");
                    org.junit.jupiter.api.Assertions.assertTrue(hint.getText().contains("renamed prod"));
                    javafx.scene.paint.Color bg = (javafx.scene.paint.Color) ((javafx.scene.layout.Region) root).getBackground().getFills().getFirst().getFill();
                    org.junit.jupiter.api.Assertions.assertTrue(contrast((javafx.scene.paint.Color) hint.getTextFill(), bg) >= 4.5, theme.name());
                    javafx.scene.paint.Color buttonBg = com.seeloggyplus.shared.ui.RenderedColors.background(connect);
                    org.junit.jupiter.api.Assertions.assertTrue(contrast((javafx.scene.paint.Color) connect.getTextFill(), buttonBg) >= 4.5, theme.name());
                    org.junit.jupiter.api.Assertions.assertTrue(connect.getHeight() >= 24);
                    org.junit.jupiter.api.Assertions.assertTrue(connect.getWidth() >= 60);
                }
            } finally { AppTheme.setTheme(AppTheme.Theme.LIGHT); }
        });
    }

    private void configure(boolean savedPassword, boolean preferencesFirst) throws Exception {
        inject("localFileService", new NoOpLocalFileService());
        inject("favoriteFolderService", new NoOpFavoriteFolderService());
        server = new SSHServerModel("prod", "prod.example.com", 22, "deploy");
        server.setId("srv-prod");
        if (savedPassword) server.setPassword("secret");
        server.setDefaultPath("/var/log");
        preferences = new SavedLocationPreference("prod");
        preferences.store.put("file_manager_last_path_local", localPath);
        preferences.store.put("file_manager_last_path_prod", "/remembered/logs");
        onFx(() -> {
            controller.setDialogPresenter(new com.seeloggyplus.shared.ui.DialogPresenter() {
                @Override public void present(Stage dialog) { }
                @Override public <T> Optional<T> present(javafx.scene.control.Dialog<T> dialog) {
                    dialogs.add(dialog);
                    return Optional.empty();
                }
            });
            if (preferencesFirst) controller.setPreferenceService(preferences);
            controller.setServerCatalog(new FakeCatalog(List.of(server)));
            if (!preferencesFirst) controller.setPreferenceService(preferences);
            controller.setSshServiceFactory(() -> { factoryCalls.incrementAndGet(); return ssh; });
        });
    }

    private void onFx(Runnable action) throws Exception {
        org.testfx.util.WaitForAsyncUtils.asyncFx(action).get();
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
    }

    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        org.testfx.util.WaitForAsyncUtils.waitFor(5, java.util.concurrent.TimeUnit.SECONDS,
                () -> org.testfx.util.WaitForAsyncUtils.asyncFx(condition::getAsBoolean).get());
    }

    private static double contrast(javafx.scene.paint.Color a, javafx.scene.paint.Color b) {
        double x = luminance(a), y = luminance(b); return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);
    }
    private static double luminance(javafx.scene.paint.Color c) {
        return 0.2126*linear(c.getRed())+0.7152*linear(c.getGreen())+0.0722*linear(c.getBlue());
    }
    private static double linear(double v) { return v <= 0.04045 ? v/12.92 : Math.pow((v+0.055)/1.055,2.4); }

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
        volatile boolean connected;
        volatile boolean connectResult = true;
        final java.util.concurrent.atomic.AtomicInteger connectCalls = new java.util.concurrent.atomic.AtomicInteger();
        final List<String> listedPaths = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public boolean connect(String host, int port, String username, String password) {
            connectCalls.incrementAndGet(); connected = connectResult; return connectResult;
        }
        @Override public boolean connect(String host, int port, String username, String password, long ttlMillis) {
            return connect(host, port, username, password);
        }
        @Override public boolean isConnected() { return connected; }
        @Override public void disconnect() { connected = false; }
        @Override public List<RemoteFileInfo> listFiles(String remotePath) throws IOException {
            listedPaths.add(remotePath); return List.of();
        }
    }
}
