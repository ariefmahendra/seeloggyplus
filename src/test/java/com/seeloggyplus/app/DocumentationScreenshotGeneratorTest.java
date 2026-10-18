package com.seeloggyplus.app;

import com.seeloggyplus.features.files.application.FavoriteFolderService;
import com.seeloggyplus.features.files.application.LocalFileService;
import com.seeloggyplus.features.files.domain.FavoriteFolder;
import com.seeloggyplus.features.files.presentation.UnifiedFileManagerDialogController;
import com.seeloggyplus.features.recent.application.RecentFileService;
import com.seeloggyplus.features.recent.domain.RecentFile;
import com.seeloggyplus.features.recent.domain.RecentFilesDto;
import com.seeloggyplus.features.servers.infrastructure.ServerManagementServiceImpl;
import com.seeloggyplus.features.settings.infrastructure.PreferenceServiceImpl;
import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.shared.dto.RemoteFileInfo;
import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.servers.ServerCatalog;
import com.seeloggyplus.shared.settings.PreferenceService;
import com.seeloggyplus.shared.ssh.SSHServiceFactory;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class DocumentationScreenshotGeneratorTest {

    private static final int WINDOW_WIDTH = 1024;
    private static final int WINDOW_HEIGHT = 576;
    private static final double SCALE = 2880.0 / WINDOW_WIDTH;

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @Test
    void captureMainWindowAndInterfaceOverview(FxRobot robot) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        MainController controller = showMainView(robot, rootRef);

        applySampleRecentFiles(robot, rootRef.get(), controller);
        Path logs = sampleLogDirectory();
        openLocalFile(robot, controller, logs.resolve("nginx-access.log").toFile());
        openLocalFile(robot, controller, logs.resolve("payment-engine.log").toFile());
        settle(robot);

        capture(robot, rootRef.get(), "main-window.png");
        robot.interact(stage::hide);
    }

    @Test
    void captureTailMode(FxRobot robot) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        MainController controller = showMainView(robot, rootRef);

        applySampleRecentFiles(robot, rootRef.get(), controller);
        Path logs = sampleLogDirectory();
        openLocalFile(robot, controller, logs.resolve("nginx-access.log").toFile());
        openLocalFile(robot, controller, logs.resolve("payment-engine.log").toFile());
        settle(robot);

        robot.interact(() -> {
            Node tail = rootRef.get().lookup("#tailButton");
            if (tail instanceof javafx.scene.control.ToggleButton toggle) {
                toggle.fire();
            }
            Node pane = rootRef.get().lookup("#logTabPane");
            if (pane instanceof javafx.scene.control.TabPane tabPane) {
                Node content = tabPane.getSelectionModel().getSelectedItem().getContent();
                if (content instanceof com.seeloggyplus.shared.ui.canvas.CanvasLogViewer viewer) {
                    viewer.setFollowTail(true);
                    viewer.scrollToBottom();
                }
            }
        });
        settle(robot);
        capture(robot, rootRef.get(), "tail-mode.png");
        robot.interact(stage::hide);
    }

    @Test
    void captureMainViewColorsInEveryTheme(FxRobot robot) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        MainController controller = showMainView(robot, rootRef);
        openLocalFile(robot, controller, sampleLogDirectory().resolve("payment-engine.log").toFile());
        for (var theme : AppTheme.Theme.values()) {
            robot.interact(() -> AppTheme.setTheme(theme));
            settle(robot);
            capture(robot, rootRef.get(), "main-colors-" + theme.name().toLowerCase() + ".png");
        }
        robot.interact(() -> { stage.hide(); AppTheme.setTheme(AppTheme.Theme.DARK); });
    }

    @Test
    void captureSoftLightAccentStates(FxRobot robot) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        MainController controller = showMainView(robot, rootRef);
        openLocalFile(robot, controller, sampleLogDirectory().resolve("payment-engine.log").toFile());
        robot.interact(() -> {
            AppTheme.setTheme(AppTheme.Theme.LIGHT);
            var tree = (javafx.scene.control.TreeView<?>) rootRef.get().lookup("#recentFilesTreeView");
            tree.getSelectionModel().select(1);
            rootRef.get().lookup("#searchField").requestFocus();
        });
        settle(robot);
        capture(robot, rootRef.get(), "light-soft-accent.png");
        robot.interact(() -> { stage.hide(); AppTheme.setTheme(AppTheme.Theme.DARK); });
    }

    @Test
    void captureIslandsDialogs(FxRobot robot) throws Exception {
        for (var theme : new AppTheme.Theme[] {AppTheme.Theme.LIGHT, AppTheme.Theme.DARK}) {
            robot.interact(() -> {
                try {
                    AppTheme.setTheme(theme);
                    Parent root = FXMLLoader.load(getClass().getResource("/fxml/PreferencesDialog.fxml"));
                    stage.setScene(AppTheme.scene(root));
                    stage.sizeToScene();
                    stage.show();
                    var themes = (javafx.scene.control.ComboBox<?>) root.lookup("#themeComboBox");
                    themes.getSelectionModel().select(themes.getItems().indexOf(theme == AppTheme.Theme.LIGHT ? "Light" : "Dark"));
                } catch (Exception error) { throw new AssertionError(error); }
            });
            settle(robot);
            capture(robot, stage.getScene().getRoot(), "islands-preferences-" + theme.name().toLowerCase() + ".png");
            robot.interact(stage::hide);
            captureLocalFileBrowser(robot);
            robot.interact(() -> {
                stage.show();
                AppTheme.setTheme(theme);
                assertTrue(stage.getScene().getRoot().getStyleClass().contains(theme.styleClass()));
            });
            settle(robot);
            capture(robot, stage.getScene().getRoot(), "islands-files-" + theme.name().toLowerCase() + ".png");
            robot.interact(stage::hide);
        }
        robot.interact(() -> AppTheme.setTheme(AppTheme.Theme.DARK));
    }

    @Test
    void captureInterfaceOverview(FxRobot robot) throws Exception {
        new PreferenceServiceImpl().saveOrUpdatePreferences(new Preference("main_auto_prettify_json", "true"));

        AtomicReference<Parent> rootRef = new AtomicReference<>();
        MainController controller = showMainView(robot, rootRef);

        applySampleRecentFiles(robot, rootRef.get(), controller);
        Path logs = sampleLogDirectory();
        openLocalFile(robot, controller, logs.resolve("nginx-access.log").toFile());
        openLocalFile(robot, controller, logs.resolve("payment-engine.log").toFile());
        settle(robot);

        robot.interact(() -> {
            TextField field = lookup(rootRef.get(), "searchField");
            if (field != null) {
                field.setText("ERROR");
            }
        });
        runSearch(robot, controller);
        showPayloadInDetailPanel(robot, controller, logs.resolve("payment-engine.log"));
        settle(robot);
        capture(robot, rootRef.get(), "interface-overview.png");
        robot.interact(stage::hide);
    }

    private void showPayloadInDetailPanel(FxRobot robot, MainController controller, Path logFile) {
        robot.interact(() -> {
            try {
                List<String> lines = Files.readAllLines(logFile);
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains("\"trace_id\"")) {
                        Method show = MainController.class.getDeclaredMethod("displayLogDetailFromCanvas",
                                long.class, String.class);
                        show.setAccessible(true);
                        show.invoke(controller, (long) i, lines.get(i));
                        return;
                    }
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void captureSearchScreenshots(FxRobot robot) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        MainController controller = showMainView(robot, rootRef);

        applySampleRecentFiles(robot, rootRef.get(), controller);
        Path logs = sampleLogDirectory();
        openLocalFile(robot, controller, logs.resolve("nginx-access.log").toFile());
        openLocalFile(robot, controller, logs.resolve("payment-engine.log").toFile());
        settle(robot);

        robot.interact(() -> {
            TextField field = lookup(rootRef.get(), "searchField");
            if (field != null) {
                field.setText("ERROR");
            }
        });
        runSearch(robot, controller);
        settle(robot);
        capture(robot, rootRef.get(), "search.png");

        AtomicReference<Node> panelRef = new AtomicReference<>();
        robot.interact(() -> {
            try {
                Field field = MainController.class.getDeclaredField("searchResultPanel");
                field.setAccessible(true);
                panelRef.set((Node) field.get(controller));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        assertNotNull(panelRef.get(), "the search result panel must exist after a search");
        capture(robot, panelRef.get(), "search-result-panel.png");
        robot.interact(stage::hide);
    }

    @Test
    void captureDetailPanel(FxRobot robot) throws Exception {
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        MainController controller = showMainView(robot, rootRef);

        Path logs = sampleLogDirectory();
        openLocalFile(robot, controller, logs.resolve("payment-engine.log").toFile());
        settle(robot);

        robot.interact(() -> {
            try {
                Field field = MainController.class.getDeclaredField("detailPaneController");
                field.setAccessible(true);
                Object paneController = field.get(controller);
                Method setAutoPrettify = paneController.getClass()
                        .getDeclaredMethod("setAutoPrettify", boolean.class, boolean.class);
                setAutoPrettify.setAccessible(true);
                setAutoPrettify.invoke(paneController, true, false);

                Method show = paneController.getClass()
                        .getDeclaredMethod("show", long.class, String.class);
                show.setAccessible(true);
                show.invoke(paneController, 42L, jsonPayload());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            rootRef.get().applyCss();
            rootRef.get().layout();
        });
        WaitForAsyncUtils.waitForFxEvents();
        AtomicReference<Parent> paneRef = new AtomicReference<>();
        robot.interact(() -> {
            try {
                Field field = MainController.class.getDeclaredField("detailPaneController");
                field.setAccessible(true);
                Object paneController = field.get(controller);
                Method getRoot = paneController.getClass().getDeclaredMethod("getRoot");
                getRoot.setAccessible(true);
                paneRef.set((Parent) getRoot.invoke(paneController));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        capture(robot, paneRef.get(), "detail-panel.png");
        robot.interact(stage::hide);
    }

    @Test
    void capturePreferencesDialog(FxRobot robot) throws Exception {
        PreferenceService pref = new PreferenceServiceImpl();
        pref.saveOrUpdatePreferences(new Preference("app_theme", "dark"));
        pref.saveOrUpdatePreferences(new Preference("app_max_memory_gb", "8"));
        pref.saveOrUpdatePreferences(new Preference("file_double_click_action", "TAIL"));
        showDialog(robot, "PreferencesDialog", "preferences.png");
    }

    @Test
    void capturePreferencesCategories(FxRobot robot) throws Exception {
        new PreferenceServiceImpl().saveOrUpdatePreferences(new Preference("app_theme", "dark"));
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Parent> wrapperRef = new AtomicReference<>();
        robot.interact(() -> {
            try {
                AppTheme.setTheme(AppTheme.Theme.DARK);
                Parent root = FXMLLoader.load(getClass().getResource("/fxml/PreferencesDialog.fxml"));
                rootRef.set(root);
                StackPane wrapper = new StackPane(root);
                wrapper.setStyle("-fx-background-color: #26282c;");
                wrapperRef.set(wrapper);
                stage.setScene(AppTheme.scene(wrapper));
                stage.setWidth(WINDOW_WIDTH);
                stage.setHeight(WINDOW_HEIGHT);
                stage.show();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        settle(robot);
        selectSettingsCategory(robot, rootRef.get(), "Log Viewer");
        capture(robot, wrapperRef.get(), "preferences-log-viewer.png");
        selectSettingsCategory(robot, rootRef.get(), "SSH");
        capture(robot, wrapperRef.get(), "preferences-ssh.png");
        robot.interact(stage::hide);
    }

    @Test
    void captureServerEditDialog(FxRobot robot) throws Exception {
        new PreferenceServiceImpl().saveOrUpdatePreferences(new Preference("app_theme", "dark"));
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        robot.interact(() -> {
            try {
                AppTheme.setTheme(AppTheme.Theme.DARK);
                Parent root = FXMLLoader.load(getClass().getResource("/fxml/ServerEditDialog.fxml"));
                rootRef.set(root);
                stage.setScene(AppTheme.scene(root));
                stage.sizeToScene();
                stage.show();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        robot.interact(() -> {
            setText(rootRef.get(), "nameField", "prod-web-01");
            setText(rootRef.get(), "hostField", "prod-web-01.example.com");
            setText(rootRef.get(), "portField", "22");
            setText(rootRef.get(), "usernameField", "deploy");
            setText(rootRef.get(), "defaultPathField", "/var/log");
        });
        settle(robot);
        capture(robot, rootRef.get(), "server-edit.png");
        robot.interact(stage::hide);
    }

    @Test
    void captureFindInFiles(FxRobot robot) throws Exception {
        new PreferenceServiceImpl().saveOrUpdatePreferences(new Preference("app_theme", "dark"));
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Object> controllerRef = new AtomicReference<>();
        robot.interact(() -> {
            try {
                AppTheme.setTheme(AppTheme.Theme.DARK);
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RemoteLogSearchDialog.fxml"));
                Parent root = loader.load();
                rootRef.set(root);
                controllerRef.set(loader.getController());
                stage.setScene(AppTheme.scene(root));
                stage.setWidth(WINDOW_WIDTH);
                stage.setHeight(WINDOW_HEIGHT);
                stage.show();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });

        SSHServerModel server = new SSHServerModel("prod-web-01", "prod-web-01.example.com", 22, "deploy");
        server.setDefaultPath("/var/log");
        robot.interact(() -> {
            try {
                Method setContext = controllerRef.get().getClass().getDeclaredMethod("setContext",
                        com.seeloggyplus.shared.ssh.SSHService.class, SSHServerModel.class, String.class);
                setContext.setAccessible(true);
                setContext.invoke(controllerRef.get(), new SearchStubSshService(), server, "/var/log");
                setText(rootRef.get(), "keywordField", "ERROR");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        robot.interact(() -> {
            Node search = rootRef.get().lookup("#searchButton");
            if (search instanceof javafx.scene.control.Button button) {
                button.fire();
            }
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.asyncFx(() -> resultRowCount(rootRef.get()) > 0).get());
        robot.interact(() -> {
            TableView<?> table = (TableView<?>) rootRef.get().lookup("#resultTable");
            table.getSelectionModel().select(2);
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() -> {
            javafx.scene.control.ListView<?> preview = (javafx.scene.control.ListView<?>) rootRef.get()
                    .lookup("#previewList");
            return preview != null && preview.getItems() != null && preview.getItems().size() > 0;
        }).get());
        settle(robot);
        capture(robot, rootRef.get(), "find-in-files.png");
        robot.interact(stage::hide);
    }

    private void selectSettingsCategory(FxRobot robot, Parent root, String text) {
        robot.interact(() -> {
            var categories = (javafx.scene.control.ListView<?>) root.lookup("#settingsCategories");
            int index = categories.getItems().indexOf(text);
            if (index < 0) throw new AssertionError("Missing settings category " + text);
            categories.getSelectionModel().select(index);
            root.applyCss();
            root.layout();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void setText(Parent root, String id, String value) {
        Node node = root.lookup("#" + id);
        if (node instanceof TextField field) {
            field.setText(value);
        }
    }

    @Test
    void captureServerManagementDialog(FxRobot robot) throws Exception {
        ServerManagementServiceImpl service = new ServerManagementServiceImpl();
        List<SSHServerModel> samples = sampleServers();
        try {
            samples.forEach(service::saveServer);
            AtomicReference<Parent> rootRef = new AtomicReference<>();
            AtomicReference<Parent> wrapperRef = new AtomicReference<>();
            robot.interact(() -> {
                try {
                    AppTheme.setTheme(AppTheme.Theme.DARK);
                    Parent dialog = FXMLLoader.load(getClass().getResource("/fxml/ServerManagementDialog.fxml"));
                    rootRef.set(dialog);
                    StackPane wrapper = new StackPane(dialog);
                    wrapper.setStyle("-fx-background-color: #26282c;");
                    wrapperRef.set(wrapper);
                    stage.setScene(AppTheme.scene(wrapper));
                    stage.setWidth(WINDOW_WIDTH);
                    stage.setHeight(WINDOW_HEIGHT);
                    stage.show();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });
            settle(robot);
            robot.interact(() -> {
                TableView<?> table = (TableView<?>) rootRef.get().lookup("#serverTable");
                if (table != null && !table.getItems().isEmpty()) {
                    table.getSelectionModel().select(0);
                }
                Node detailsButton = rootRef.get().lookup("#detailsButton");
                if (detailsButton instanceof javafx.scene.control.ToggleButton tb) {
                    tb.setSelected(true);
                }
            });
            settle(robot);
            capture(robot, wrapperRef.get(), "server-management.png");
            robot.interact(stage::hide);
        } finally {
            samples.forEach(server -> service.deleteServer(server.getId()));
        }
    }

    @Test
    void captureLocalFileBrowser(FxRobot robot) throws Exception {
        UnifiedFileManagerDialogController controller = loadFileManager(robot);
        robot.interact(() -> {
            try {
                inject(controller, "localFileService", new SampleLocalFileService());
                inject(controller, "favoriteFolderService", new NoOpFavoriteFolderService());
                controller.setServerCatalog(new StaticCatalog(sampleServers()));
                controller.setPreferenceService(new StaticPreference("local"));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        robot.interact(() -> controller.setSshServiceFactory(ConnectedSshService::new));
        settle(robot);
        robot.interact(() -> {
            TableView<?> table = (TableView<?>) stage.getScene().getRoot().lookup("#fileTable");
            if (table != null && table.getItems().size() > 2) {
                table.getSelectionModel().select(2);
            }
            Parent root = stage.getScene().getRoot();
            root.applyCss();
            root.layout();
        });
        capture(robot, stage.getScene().getRoot(), "open-local-file.png");
        robot.interact(stage::hide);
    }

    @Test
    void captureRemoteFileBrowser(FxRobot robot) throws Exception {
        UnifiedFileManagerDialogController controller = loadFileManager(robot);
        SSHServerModel server = new SSHServerModel("prod-web-01", "prod-web-01.example.com", 22, "deploy");
        server.setId("srv-prod-web-01");
        server.setPassword("secret");
        server.setDefaultPath("/var/log");
        server.setGroupName("Production");

        robot.interact(() -> {
            try {
                inject(controller, "localFileService", new SampleLocalFileService());
                inject(controller, "favoriteFolderService", new NoOpFavoriteFolderService());
                controller.setServerCatalog(new StaticCatalog(List.of(server)));
                controller.setPreferenceService(new StaticPreference(server.getName()));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        robot.interact(() -> controller.setSshServiceFactory(ConnectedSshService::new));
        robot.interact(() -> {
            var connect = (javafx.scene.control.Button) stage.getScene().getRoot().lookup("#connectLastServerButton");
            assertFalse(connect.isDisabled(), "the remembered server must be available to connect");
            connect.fire();
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.asyncFx(() -> fileRowCount(stage.getScene().getRoot()) > 0).get());
        assertNotNull(controller.getActiveServer(), "the selected remote server must be connected");
        settle(robot);
        robot.interact(() -> {
            Parent root = stage.getScene().getRoot();
            root.applyCss();
            root.layout();
        });
        capture(robot, stage.getScene().getRoot(), "remote-file-browser.png");
        robot.interact(stage::hide);
    }

    private MainController showMainView(FxRobot robot, AtomicReference<Parent> rootRef) {
        new PreferenceServiceImpl().saveOrUpdatePreferences(new Preference("app_theme", "dark"));
        AtomicReference<MainController> controllerRef = new AtomicReference<>();
        robot.interact(() -> {
            try {
                AppTheme.setTheme(AppTheme.Theme.DARK);
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
                Parent root = loader.load();
                rootRef.set(root);
                controllerRef.set(loader.getController());
                stage.setScene(AppTheme.scene(root));
                stage.setWidth(WINDOW_WIDTH);
                stage.setHeight(WINDOW_HEIGHT);
                stage.show();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        return controllerRef.get();
    }

    private void openLocalFile(FxRobot robot, MainController controller, File file) {
        robot.interact(() -> {
            try {
                Method open = MainController.class.getDeclaredMethod("openLocalLogFile", File.class, boolean.class);
                open.setAccessible(true);
                open.invoke(controller, file, false);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    private void runSearch(FxRobot robot, MainController controller) throws Exception {
        robot.interact(() -> {
            try {
                Method search = MainController.class.getDeclaredMethod("performSearch");
                search.setAccessible(true);
                search.invoke(controller);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.asyncFx(() -> {
                    Node navigator = null;
                    try {
                        Field field = MainController.class.getDeclaredField("searchNavigator");
                        field.setAccessible(true);
                        navigator = (Node) field.get(controller);
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError(e);
                    }
                    return navigator != null && navigator.isVisible();
                }).get());
    }

    private void settle(FxRobot robot) {
        robot.interact(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private UnifiedFileManagerDialogController loadFileManager(FxRobot robot) throws Exception {
        new PreferenceServiceImpl().saveOrUpdatePreferences(new Preference("app_theme", "dark"));
        AtomicReference<UnifiedFileManagerDialogController> controllerRef = new AtomicReference<>();
        robot.interact(() -> {
            try {
                AppTheme.setTheme(AppTheme.Theme.DARK);
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
                Parent root = loader.load();
                controllerRef.set(loader.getController());
                stage.setScene(AppTheme.scene(root));
                stage.setWidth(WINDOW_WIDTH);
                stage.setHeight(WINDOW_HEIGHT);
                stage.show();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        return controllerRef.get();
    }

    private void showDialog(FxRobot robot, String fxmlName, String fileName) throws Exception {
        new PreferenceServiceImpl().saveOrUpdatePreferences(new Preference("app_theme", "dark"));
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        robot.interact(() -> {
            try {
                AppTheme.setTheme(AppTheme.Theme.DARK);
                Parent dialog = FXMLLoader.load(getClass().getResource("/fxml/" + fxmlName + ".fxml"));
                StackPane wrapper = new StackPane(dialog);
                wrapper.setStyle("-fx-background-color: #26282c;");
                rootRef.set(wrapper);
                stage.setScene(AppTheme.scene(wrapper));
                stage.setWidth(WINDOW_WIDTH);
                stage.setHeight(WINDOW_HEIGHT);
                stage.show();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        settle(robot);
        capture(robot, rootRef.get(), fileName);
        robot.interact(stage::hide);
    }

    private void capture(FxRobot robot, Node node, String fileName) throws Exception {
        AtomicReference<WritableImage> imageRef = new AtomicReference<>();
        robot.interact(() -> {
            node.applyCss();
            if (node instanceof Parent parent) {
                for (int pass = 0; pass < 2; pass++) {
                    parent.layout();
                    redrawCanvases(parent);
                }
            }
            SnapshotParameters params = new SnapshotParameters();
            params.setTransform(Transform.scale(SCALE, SCALE));
            imageRef.set(node.snapshot(params, null));
        });
        WaitForAsyncUtils.waitForFxEvents();

        WritableImage image = imageRef.get();
        assertNotNull(image, fileName + " must be rendered");
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage png = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            image.getPixelReader().getPixels(0, y, width, 1, PixelFormat.getIntArgbInstance(), row, 0, width);
            png.setRGB(0, y, width, 1, row, 0, width);
        }
        Path target = outputDirectory().resolve(fileName);
        Files.createDirectories(target.getParent());
        ImageIO.write(png, "png", target.toFile());
    }

    private Path outputDirectory() {
        String configured = System.getProperty("seeloggyplus.documentationScreenshots");
        return configured != null && !configured.isBlank()
                ? Path.of(configured)
                : Path.of("build", "documentation-screenshots");
    }

    private Path sampleLogDirectory() throws IOException {
        Path logs = outputDirectory().resolve("fixtures");
        Files.createDirectories(logs);
        if (!Files.exists(logs.resolve("payment-engine.log"))) {
            writeLog(logs.resolve("payment-engine.log"), 160);
            writeLog(logs.resolve("nginx-access.log"), 90);
        }
        return logs;
    }

    private static void writeLog(Path path, int lines) throws IOException {
        List<String> content = new ArrayList<>();
        String[] levels = { "INFO", "DEBUG", "INFO", "WARN", "INFO", "DEBUG", "ERROR", "INFO", "TRACE", "INFO" };
        String[] messages = {
                "Handling request POST /v1/payments/authorize",
                "Payment authorization received for order ORD-88213",
                "Loading configuration from /etc/payment-engine/app.yml",
                "Cache hit for tenant TNT-4410 (ttl 300s)",
                "Retrying webhook delivery for order ORD-88213 attempt 2/3",
                "Upstream gateway latency 812 ms exceeds the 800 ms budget",
                "Payment authorization failed for order ORD-88213: upstream timeout",
                "Emitted audit event payment.authorized id EVT-90231",
                "Connection pool in-use 18/50, waiting 0 ms",
                "Metrics flushed to collector (12 series)",
        };
        for (int i = 0; i < lines; i++) {
            String level = levels[i % levels.length];
            String logger = "c.a.p." + (i % 3 == 0 ? "Gateway" : i % 3 == 1 ? "PaymentService" : "Telemetry");
            content.add(String.format("2026-10-17 13:%02d:%02d,%03d %-5s [http-nio-8080-exec-%d] %s - %s",
                    2 + (i / 60), i % 60, (i * 7) % 1000, level, 1 + (i % 8), logger, messages[i % messages.length]));
            if (i % 37 == 36) {
                content.add("java.lang.IllegalStateException: upstream timeout");
                content.add("\tat com.acme.payment.GatewayClient.post(GatewayClient.java:184)");
                content.add("\tat com.acme.payment.PaymentService.authorize(PaymentService.java:96)");
            }
            if (i % 41 == 40) {
                content.add("2026-10-17 13:04:12,556 INFO  [http-nio-8080-exec-6] c.a.p.Telemetry - Payload "
                        + jsonPayload());
            }
        }
        Files.write(path, content);
    }

    private static String jsonPayload() {
        return "{\"timestamp\":\"2026-10-17T13:04:12.556+0200\",\"level\":\"ERROR\","
                + "\"service\":\"notification-gateway\",\"pid\":2463,"
                + "\"thread\":\"http-nio-8080-exec-6\",\"trace_id\":\"89c1f4d0a2b\",\"span_id\":\"19e9\","
                + "\"client_ip\":\"10.74.254.88\",\"user_id\":\"USR-114702\","
                + "\"http\":{\"method\":\"POST\",\"path\":\"/v1/notify\",\"status\":502},"
                + "\"error\":{\"type\":\"UpstreamTimeout\",\"retryable\":true}}";
    }

    private static List<SSHServerModel> sampleServers() {
        SSHServerModel production = new SSHServerModel("prod-web-01", "prod-web-01.example.com", 22, "deploy");
        production.setId("srv-prod-web-01");
        production.setDefaultPath("/var/log");
        production.setGroupName("Production");
        production.setFavorite(true);

        SSHServerModel database = new SSHServerModel("prod-db-01", "prod-db-01.example.com", 22, "dba");
        database.setId("srv-prod-db-01");
        database.setDefaultPath("/var/log/postgresql");
        database.setGroupName("Production");

        SSHServerModel staging = new SSHServerModel("staging-web", "10.20.30.41", 22, "qa");
        staging.setId("srv-staging-web");
        staging.setDefaultPath("/srv/app/logs");
        staging.setGroupName("Staging");

        SSHServerModel bastion = new SSHServerModel("bastion", "bastion.example.com", 2222, "ops");
        bastion.setId("srv-bastion");
        bastion.setDefaultPath("/var/log");

        return List.of(production, database, staging, bastion);
    }

    private static int fileRowCount(Parent root) {
        TableView<?> table = (TableView<?>) root.lookup("#fileTable");
        return table == null || table.getItems() == null ? 0 : table.getItems().size();
    }

    private static int resultRowCount(Parent root) {
        TableView<?> table = (TableView<?>) root.lookup("#resultTable");
        return table == null || table.getItems() == null ? 0 : table.getItems().size();
    }

    private static void redrawCanvases(Node node) {
        if (node instanceof com.seeloggyplus.shared.ui.canvas.CanvasLogViewer viewer) {
            viewer.redraw();
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                redrawCanvases(child);
            }
        }
    }

    private static <T> T lookup(Parent root, String id) {
        return (T) root.lookup("#" + id);
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void applySampleRecentFiles(FxRobot robot, Parent root, MainController controller) throws Exception {
        Object panel = readField(controller, "recentPanelController");
        inject(panel, "recentFileService", new StubRecentFileService(sampleRecentFiles()));
        Method refresh = panel.getClass().getMethod("refresh");
        robot.interact(() -> {
            try {
                refresh.invoke(panel);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.asyncFx(() -> {
                    TreeView<?> tree = (TreeView<?>) root.lookup("#recentFilesTreeView");
                    return tree != null && tree.getRoot() != null && !tree.getRoot().getChildren().isEmpty();
                }).get());
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static List<RecentFilesDto> sampleRecentFiles() {
        LogFile remoteNginx = recentFile("rf-1", "nginx-access.log", "/var/log/nginx/access.log", "4.10 MB", true);
        LogFile remotePayment = recentFile("rf-2", "payment-engine.log", "/var/log/payment-engine.log", "2.00 MB", true);
        LogFile localApp = recentFile("rf-3", "app-production.log", "C:\\logs\\app-production.log", "17.59 MB", false);
        LogFile localSystem = recentFile("rf-4", "system.log", "C:\\logs\\system.log", "64.00 KB", false);
        return List.of(
                new RecentFilesDto(remoteNginx, null, "prod-web-01", RecentFile.MODE_TAIL),
                new RecentFilesDto(remotePayment, null, "prod-web-01", RecentFile.MODE_OPEN),
                new RecentFilesDto(localApp, null, null, RecentFile.MODE_OPEN),
                new RecentFilesDto(localSystem, null, null, RecentFile.MODE_OPEN));
    }

    private static LogFile recentFile(String id, String name, String path, String size, boolean remote) {
        LogFile logFile = new LogFile();
        logFile.setId(id);
        logFile.setName(name);
        logFile.setFilePath(path);
        logFile.setSize(size);
        logFile.setModified("2026-10-17 13:04:12");
        logFile.setRemote(remote);
        logFile.setSshServerID(remote ? "srv-prod-web-01" : null);
        return logFile;
    }

    private static final class StubRecentFileService implements RecentFileService {

        private final List<RecentFilesDto> entries;

        StubRecentFileService(List<RecentFilesDto> entries) {
            this.entries = entries;
        }

        @Override
        public List<RecentFilesDto> findAll() {
            return entries;
        }

        @Override
        public void save(LogFile logFile, RecentFile recentFile) {
        }

        @Override
        public void deleteAll() {
        }

        @Override
        public RecentFilesDto findById(String id) {
            return null;
        }

        @Override
        public Optional<RecentFile> findByFileId(String fileId) {
            return Optional.empty();
        }

        @Override
        public void deleteById(String id) {
        }

        @Override
        public void deleteByFileId(String fileId) {
        }
    }

    private static final class SampleLocalFileService implements LocalFileService {

        @Override
        public String getHomeDirectory() {
            return "C:\\logs";
        }

        @Override
        public List<FileInfo> listFiles(String directoryPath) {
            long base = 1750000000000L;
            return List.of(
                    local("archive", "C:\\logs\\archive", 0, true, base),
                    local("backup", "C:\\logs\\backup", 0, true, base),
                    local("app-production.log", "C:\\logs\\app-production.log", 18_446_744, false, base),
                    local("nginx-access.log", "C:\\logs\\nginx-access.log", 4_294_967, false, base),
                    local("notification-gateway.log", "C:\\logs\\notification-gateway.log", 2_097_152, false, base),
                    local("payment-engine.log", "C:\\logs\\payment-engine.log", 1_048_576, false, base),
                    local("system.log", "C:\\logs\\system.log", 65_536, false, base));
        }

        private static FileInfo local(String name, String path, long size, boolean directory, long modified) {
            FileInfo info = new FileInfo(name, path, size, directory, modified, FileInfo.SourceType.LOCAL);
            info.setPermissions(directory ? "drwxr-xr-x" : "-rw-r--r--");
            info.setOwner(directory ? "root" : "deploy");
            return info;
        }
    }

    private static final class NoOpFavoriteFolderService implements FavoriteFolderService {

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

    private static final class StaticCatalog implements ServerCatalog {

        private final List<SSHServerModel> servers;

        StaticCatalog(List<SSHServerModel> servers) {
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
            return List.of("Production", "Staging");
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

    private static final class StaticPreference implements PreferenceService {

        private final String lastLocation;

        StaticPreference(String lastLocation) {
            this.lastLocation = lastLocation;
        }

        @Override
        public Optional<String> getPreferencesByCode(String code) {
            if ("file_manager_last_location".equals(code)) {
                return Optional.of(lastLocation);
            }
            return Optional.empty();
        }

        @Override
        public void savePreferences(Preference preference) {
        }

        @Override
        public void updatePreferences(Preference preference) {
        }

        @Override
        public void saveOrUpdatePreferences(Preference preference) {
        }

        @Override
        public List<Preference> getListPreferences() {
            return List.of();
        }
    }

    private static final class SearchStubSshService extends SSHServiceImpl {

        @Override
        public boolean connect(String host, int port, String username, String password) {
            return true;
        }

        @Override
        public boolean connect(String host, int port, String username, String password, long ttlMillis) {
            return true;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public String executeCommand(String command) {
            if (command != null && command.contains("grep")) {
                return String.join("\n",
                        "/var/log/nginx/error.log:1841:2026-10-17 13:02:15,132 [ERROR] upstream timed out while reading response header",
                        "/var/log/nginx/error.log:1902:2026-10-17 13:14:02,884 [ERROR] no live upstreams while connecting to upstream",
                        "/var/log/payment-engine.log:7:2026-10-17 13:02:06,842 ERROR [http-nio-8080-exec-7] c.a.p.Gateway - Payment authorization failed for order ORD-88213: upstream timeout",
                        "/var/log/payment-engine.log:17:2026-10-17 13:02:16,112 ERROR [http-nio-8080-exec-1] c.a.p.PaymentService - Payment authorization failed for order ORD-88213: upstream timeout",
                        "/var/log/payment-engine.log:27:2026-10-17 13:02:26,182 ERROR [http-nio-8080-exec-3] c.a.p.Telemetry - Payment authorization failed for order ORD-88213: upstream timeout",
                        "/var/log/notification-gateway.log:45:2026-10-17 13:04:12,556 ERROR [http-nio-8080-exec-6] c.a.n.Webhook - UpstreamTimeout after 800 ms",
                        "/var/log/postgresql/postgresql-16-main.log:3112:2026-10-17 13:05:44,201 [ERROR] could not receive data from client: Connection reset by peer");
            }
            if (command != null && command.contains("awk")) {
                return String.join("\n",
                        "5:2026-10-17 13:02:04,818 DEBUG [http-nio-8080-exec-5] c.a.p.Telemetry - Upstream gateway latency 780 ms",
                        "6:2026-10-17 13:02:05,035 DEBUG [http-nio-8080-exec-6] c.a.p.Telemetry - Upstream gateway latency 812 ms",
                        "7:2026-10-17 13:02:06,842 ERROR [http-nio-8080-exec-7] c.a.p.Gateway - Payment authorization failed for order ORD-88213: upstream timeout",
                        "8:2026-10-17 13:02:07,049 INFO  [http-nio-8080-exec-8] c.a.p.PaymentService - Emitted audit event payment.authorized id EVT-90231",
                        "9:2026-10-17 13:02:08,056 TRACE [http-nio-8080-exec-1] c.a.p.PaymentService - Connection pool in-use 18/50");
            }
            return "";
        }
    }

    private static final class ConnectedSshService extends SSHServiceImpl {

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
        public List<RemoteFileInfo> listFiles(String remotePath) {
            long base = 1750000000000L;
            List<RemoteFileInfo> entries = new ArrayList<>();
            entries.add(remote("nginx", "/var/log/nginx", true, 4096, base, "drwxr-xr-x", "root"));
            entries.add(remote("postgresql", "/var/log/postgresql", true, 4096, base, "drwxr-xr-x", "postgres"));
            entries.add(remote("syslog", "/var/log/syslog", false, 52_428_800, base, "-rw-r-----", "syslog"));
            entries.add(remote("auth.log", "/var/log/auth.log", false, 8_388_608, base, "-rw-r-----", "root"));
            entries.add(remote("kern.log", "/var/log/kern.log", false, 4_194_304, base, "-rw-r-----", "root"));
            entries.add(remote("payment-engine.log", "/var/log/payment-engine.log", false, 2_097_152, base,
                    "-rw-r--r--", "deploy"));
            return entries;
        }

        private static RemoteFileInfo remote(String name, String path, boolean directory, long size, long modified,
                String permissions, String owner) {
            RemoteFileInfo info = new RemoteFileInfo();
            info.setName(name);
            info.setPath(path);
            info.setDirectory(directory);
            info.setSize(size);
            info.setModifiedTime(modified);
            info.setPermissions(permissions);
            info.setOwner(owner);
            return info;
        }
    }
}
