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
import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.shared.ssh.SSHServiceFactory;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class UnifiedFileManagerDialogControllerHelperTest {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final String SEP = IS_WINDOWS ? "\\" : "/";
    private static final String HOME = IS_WINDOWS ? "C:\\" : "/";
    private static final String LOCAL_DIR = HOME + "helperdir";

    private UnifiedFileManagerDialogController wired;
    private FakeLocalFileService localFileService;
    private RecordingFavoriteService favoriteService;
    private InMemoryPreferenceService preferences;
    private FakeSshFactory sshFactory;
    private RecordingCatalog catalog;

    @Start
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
        Parent root = loader.load();
        wired = loader.getController();
        localFileService = new FakeLocalFileService();
        favoriteService = new RecordingFavoriteService();
        preferences = new InMemoryPreferenceService();
        sshFactory = new FakeSshFactory();
        catalog = new RecordingCatalog();
        setField(wired, "localFileService", localFileService);
        setField(wired, "favoriteFolderService", favoriteService);
        setField(wired, "preferenceService", preferences);
        setField(wired, "sshServiceFactory", sshFactory);
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.asyncFx(() -> wired.setServerCatalog(catalog)).get();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void parseFormattedSizeDecodesUnitsAndRejectsInvalidInput() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        assertEquals(1024L, invoke(controller, "parseFormattedSize", new Class<?>[]{String.class}, "1024 B"));
        assertEquals(1536L, invoke(controller, "parseFormattedSize", new Class<?>[]{String.class}, "1.5 KB"));
        assertEquals(2L * 1024 * 1024, invoke(controller, "parseFormattedSize", new Class<?>[]{String.class}, "2 MB"));
        assertEquals(1024L * 1024 * 1024, invoke(controller, "parseFormattedSize", new Class<?>[]{String.class}, "1 GB"));
        assertEquals(-1L, invoke(controller, "parseFormattedSize", new Class<?>[]{String.class}, (Object) null));
        assertEquals(-1L, invoke(controller, "parseFormattedSize", new Class<?>[]{String.class}, "-"));
        assertEquals(-1L, invoke(controller, "parseFormattedSize", new Class<?>[]{String.class}, "   "));
        assertEquals(-1L, invoke(controller, "parseFormattedSize", new Class<?>[]{String.class}, "5"));
        assertEquals(-1L, invoke(controller, "parseFormattedSize", new Class<?>[]{String.class}, "5 XB"));
        assertEquals(-1L, invoke(controller, "parseFormattedSize", new Class<?>[]{String.class}, "abc KB"));
    }

    @Test
    void isSameFileListComparesFileMetadata() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        List<FileInfo> base = List.of(
                file("a.log", "/a.log", 10L, 1000L, false),
                file("sub", "/sub", 0L, 2000L, true));
        List<FileInfo> same = List.of(
                file("a.log", "/a.log", 10L, 1000L, false),
                file("sub", "/sub", 0L, 2000L, true));
        assertTrue((Boolean) invoke(controller, "isSameFileList",
                new Class<?>[]{List.class, List.class}, base, same));
        assertFalse((Boolean) invoke(controller, "isSameFileList", new Class<?>[]{List.class, List.class},
                base, List.of(file("a.log", "/a.log", 11L, 1000L, false), base.get(1))));
        assertFalse((Boolean) invoke(controller, "isSameFileList", new Class<?>[]{List.class, List.class},
                base, List.of(file("b.log", "/a.log", 10L, 1000L, false), base.get(1))));
        assertFalse((Boolean) invoke(controller, "isSameFileList", new Class<?>[]{List.class, List.class},
                base, List.of(file("a.log", "/a.log", 10L, 1001L, false), base.get(1))));
        assertFalse((Boolean) invoke(controller, "isSameFileList", new Class<?>[]{List.class, List.class},
                base, List.of(file("a.log", "/a.log", 10L, 1000L, true), base.get(1))));
        assertFalse((Boolean) invoke(controller, "isSameFileList",
                new Class<?>[]{List.class, List.class}, base, null));
        assertFalse((Boolean) invoke(controller, "isSameFileList",
                new Class<?>[]{List.class, List.class}, base, List.of(base.get(0))));
    }

    @Test
    void isSameLocationComparesLocalAndServerIdentities() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        UnifiedFileManagerDialogController.LocationItem local =
                UnifiedFileManagerDialogController.LocationItem.local();
        SSHServerModel same = server("SameSrv", "same-1");
        UnifiedFileManagerDialogController.LocationItem serverA =
                UnifiedFileManagerDialogController.LocationItem.of(same);
        UnifiedFileManagerDialogController.LocationItem serverB =
                UnifiedFileManagerDialogController.LocationItem.of(server("OtherSrv", "same-2"));
        assertEquals(false, invoke(controller, "isSameLocation",
                new Class<?>[]{UnifiedFileManagerDialogController.LocationItem.class,
                        UnifiedFileManagerDialogController.LocationItem.class}, null, local));
        assertEquals(true, invoke(controller, "isSameLocation",
                new Class<?>[]{UnifiedFileManagerDialogController.LocationItem.class,
                        UnifiedFileManagerDialogController.LocationItem.class}, local,
                UnifiedFileManagerDialogController.LocationItem.local()));
        assertEquals(false, invoke(controller, "isSameLocation",
                new Class<?>[]{UnifiedFileManagerDialogController.LocationItem.class,
                        UnifiedFileManagerDialogController.LocationItem.class}, local, serverA));
        assertEquals(true, invoke(controller, "isSameLocation",
                new Class<?>[]{UnifiedFileManagerDialogController.LocationItem.class,
                        UnifiedFileManagerDialogController.LocationItem.class}, serverA,
                UnifiedFileManagerDialogController.LocationItem.of(same)));
        assertEquals(false, invoke(controller, "isSameLocation",
                new Class<?>[]{UnifiedFileManagerDialogController.LocationItem.class,
                        UnifiedFileManagerDialogController.LocationItem.class}, serverA, serverB));
    }

    @Test
    void locationItemRootHasDefaultLabelAndTooltip() {
        UnifiedFileManagerDialogController.LocationItem root =
                UnifiedFileManagerDialogController.LocationItem.root();
        assertTrue(root.isRoot());
        assertEquals("Locations", root.getLabel());
        assertNull(root.getTooltip());
    }

    @Test
    void parentPathHelpersResolveGroupAndServerParents() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        assertNull(invoke(controller, "parentPathForNewGroup",
                new Class<?>[]{UnifiedFileManagerDialogController.LocationItem.class}, (Object) null));
        assertEquals("a/b", invoke(controller, "parentPathForNewGroup",
                new Class<?>[]{UnifiedFileManagerDialogController.LocationItem.class},
                UnifiedFileManagerDialogController.LocationItem.group("a/b")));
        SSHServerModel grouped = server("GroupedSrv", "grp-1");
        grouped.setGroupName("grp");
        assertEquals("grp", invoke(controller, "parentPathForNewGroup",
                new Class<?>[]{UnifiedFileManagerDialogController.LocationItem.class},
                UnifiedFileManagerDialogController.LocationItem.of(grouped)));
        assertNull(invoke(controller, "parentPathForNewGroup",
                new Class<?>[]{UnifiedFileManagerDialogController.LocationItem.class},
                UnifiedFileManagerDialogController.LocationItem.local()));
    }

    @Test
    void getParentPathHandlesLocalAndRemotePaths() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        setField(controller, "currentLocation", UnifiedFileManagerDialogController.LocationItem.local());
        assertNull(invoke(controller, "getParentPath", new Class<?>[]{String.class}, (Object) null));
        assertEquals(LOCAL_DIR, invoke(controller, "getParentPath",
                new Class<?>[]{String.class}, LOCAL_DIR + SEP + "child"));
        setField(controller, "currentLocation",
                UnifiedFileManagerDialogController.LocationItem.of(server("ParentSrv", "pp-1")));
        assertNull(invoke(controller, "getParentPath", new Class<?>[]{String.class}, "/"));
        assertEquals("/", invoke(controller, "getParentPath", new Class<?>[]{String.class}, "/a"));
        assertEquals("/a", invoke(controller, "getParentPath", new Class<?>[]{String.class}, "/a/b"));
    }

    @Test
    void normalizePathStringNormalizesLocalAndRemotePaths() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        setField(controller, "currentLocation", UnifiedFileManagerDialogController.LocationItem.local());
        String raw = LOCAL_DIR + SEP + ".." + SEP + "normalized";
        assertEquals(Paths.get(raw).toAbsolutePath().normalize().toString(),
                invoke(controller, "normalizePathString", new Class<?>[]{String.class}, raw));
        assertEquals("bad\u0000path",
                invoke(controller, "normalizePathString", new Class<?>[]{String.class}, "bad\u0000path"));
        setField(controller, "currentLocation",
                UnifiedFileManagerDialogController.LocationItem.of(server("NormSrv", "nn-1")));
        assertEquals("a", invoke(controller, "normalizePathString", new Class<?>[]{String.class}, "a/"));
        assertEquals("/", invoke(controller, "normalizePathString", new Class<?>[]{String.class}, "/"));
        assertEquals("a", invoke(controller, "normalizePathString", new Class<?>[]{String.class}, "a"));
        assertNull(invoke(controller, "normalizePathString", new Class<?>[]{String.class}, (Object) null));
    }

    @Test
    void dropHelperContractCoversNonServerTargetsAndPseudoClasses() throws Exception {
        assertNull(UnifiedFileManagerDialogController.groupForDropTarget(null));
        assertNull(UnifiedFileManagerDialogController.groupForDropTarget(
                UnifiedFileManagerDialogController.LocationItem.local()));
        assertNull(UnifiedFileManagerDialogController.groupForDropTarget(
                UnifiedFileManagerDialogController.LocationItem.root()));
        Class<UnifiedFileManagerDialogController> type = UnifiedFileManagerDialogController.class;
        assertEquals("drop-before", invokeStatic(type, "pseudoClassFor",
                new Class<?>[]{UnifiedFileManagerDialogController.DropPlacement.class},
                UnifiedFileManagerDialogController.DropPlacement.BEFORE));
        assertEquals("drop-after", invokeStatic(type, "pseudoClassFor",
                new Class<?>[]{UnifiedFileManagerDialogController.DropPlacement.class},
                UnifiedFileManagerDialogController.DropPlacement.AFTER));
        assertEquals("drop-target", invokeStatic(type, "pseudoClassFor",
                new Class<?>[]{UnifiedFileManagerDialogController.DropPlacement.class},
                UnifiedFileManagerDialogController.DropPlacement.INTO_GROUP));
        assertEquals("drop-ungroup", invokeStatic(type, "pseudoClassFor",
                new Class<?>[]{UnifiedFileManagerDialogController.DropPlacement.class},
                UnifiedFileManagerDialogController.DropPlacement.UNGROUP));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ensureFolderNodeSkipsBlankSegments() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        TreeItem<UnifiedFileManagerDialogController.LocationItem> root =
                new TreeItem<>(UnifiedFileManagerDialogController.LocationItem.root());
        Map<String, TreeItem<UnifiedFileManagerDialogController.LocationItem>> folders = new HashMap<>();
        Object node = invoke(controller, "ensureFolderNode",
                new Class<?>[]{TreeItem.class, Map.class, String.class}, root, folders, "/a//b/");
        assertNotNull(node);
        assertEquals("a/b", ((TreeItem<UnifiedFileManagerDialogController.LocationItem>) node).getValue().getGroupName());
        assertEquals(Set.of("a", "a/b"), folders.keySet());
        assertEquals(1, root.getChildren().size());
        assertEquals("a", root.getChildren().get(0).getValue().getGroupName());
    }

    @Test
    void copySelectionToClipboardHandlesEmptyAndSelectedCells() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        FileInfo info = file("x.cfg", HOME + "x.cfg", 5L, 1000L, false);
        String copied = WaitForAsyncUtils.asyncFx(() -> {
            ClipboardContent sentinel = new ClipboardContent();
            sentinel.putString("sentinel");
            Clipboard.getSystemClipboard().setContent(sentinel);
            invoke(controller, "copySelectionToClipboard", new Class<?>[]{TableView.class}, new TableView<>());
            String afterEmpty = Clipboard.getSystemClipboard().getString();
            assertEquals("sentinel", afterEmpty);
            TableView<FileInfo> table = new TableView<>();
            TableColumn<FileInfo, String> name = new TableColumn<>("NAME");
            name.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getName()));
            TableColumn<FileInfo, String> type = new TableColumn<>("TYPE");
            type.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getTypeDescription()));
            table.getColumns().addAll(name, type);
            table.setItems(FXCollections.observableArrayList(info));
            table.getSelectionModel().setCellSelectionEnabled(true);
            table.getSelectionModel().select(0, type);
            invoke(controller, "copySelectionToClipboard", new Class<?>[]{TableView.class}, table);
            return Clipboard.getSystemClipboard().getString();
        }).get();
        assertEquals("CFG File\n", copied);
    }

    @Test
    @SuppressWarnings("unchecked")
    void locationTreeLookupsAndLocationIdHandleMissingNodes() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        TreeView<UnifiedFileManagerDialogController.LocationItem> tree = new TreeView<>();
        setField(controller, "locationTree", tree);
        onFx(() -> {
            assertNull(invoke(controller, "findLocalNode"));
            TreeItem<UnifiedFileManagerDialogController.LocationItem> root =
                    new TreeItem<>(UnifiedFileManagerDialogController.LocationItem.root());
            root.getChildren().add(new TreeItem<>(
                    UnifiedFileManagerDialogController.LocationItem.of(server("OnlyServer", "only-1"))));
            tree.setRoot(root);
            assertNull(invoke(controller, "findLocalNode"));
            invoke(controller, "selectLocalLocation");
            assertNull(tree.getSelectionModel().getSelectedItem());
            TreeItem<UnifiedFileManagerDialogController.LocationItem> local =
                    new TreeItem<>(UnifiedFileManagerDialogController.LocationItem.local());
            root.getChildren().add(0, local);
            assertSame(local, invoke(controller, "findLocalNode"));
            setField(controller, "currentLocation", null);
            assertSame(local, invoke(controller, "selectedLocationNode"));
            setField(controller, "currentLocation",
                    UnifiedFileManagerDialogController.LocationItem.of(server("OnlyServer", "only-1")));
            TreeItem<UnifiedFileManagerDialogController.LocationItem> selected =
                    (TreeItem<UnifiedFileManagerDialogController.LocationItem>) invoke(controller, "selectedLocationNode");
            assertNotNull(selected);
            assertEquals("only-1", selected.getValue().getServer().getId());
        });
        setField(controller, "currentLocation", null);
        assertEquals("", invoke(controller, "getLocationIdForCurrent"));
        setField(controller, "currentLocation", UnifiedFileManagerDialogController.LocationItem.local());
        assertEquals("local", invoke(controller, "getLocationIdForCurrent"));
        setField(controller, "currentLocation",
                UnifiedFileManagerDialogController.LocationItem.of(server("NameSrv", "name-1")));
        assertEquals("NameSrv", invoke(controller, "getLocationIdForCurrent"));
    }

    @Test
    void moveServerToGroupIgnoresUnknownServerAndFallsBackToCatalog() throws Exception {
        RecordingCatalog recordingCatalog = new RecordingCatalog();
        SSHServerModel known = server("CatalogOnly", "catalog-1");
        recordingCatalog.servers.add(known);
        UnifiedFileManagerDialogController controller = bare();
        setField(controller, "serverManagementService", recordingCatalog);
        TreeView<UnifiedFileManagerDialogController.LocationItem> tree = new TreeView<>();
        TreeItem<UnifiedFileManagerDialogController.LocationItem> root =
                new TreeItem<>(UnifiedFileManagerDialogController.LocationItem.root());
        root.getChildren().add(new TreeItem<>(UnifiedFileManagerDialogController.LocationItem.local()));
        tree.setRoot(root);
        setField(controller, "locationTree", tree);
        onFx(() -> controller.moveServerToGroup("missing-id", "g"));
        assertTrue(recordingCatalog.saved.isEmpty());
        onFx(() -> controller.moveServerToGroup(known.getId(), "g"));
        assertEquals("g", known.getGroupName());
        assertEquals(List.of(known.getId()), recordingCatalog.saved);
    }

    @Test
    void gettersAndSetHostExposeDialogState() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        RecordingHost host = new RecordingHost();
        controller.setHost(host);
        assertSame(host, getField(controller, "host"));
        assertNull(controller.getSelectedFile());
        assertEquals(0, controller.getPendingJumpLine());
        assertEquals(0, controller.getPendingTailWindowLines());
        assertEquals(-1, controller.getPendingTailJumpIndex());
        assertNull(controller.getSshService());
        assertNull(controller.getActiveServer());
    }

    @Test
    void dialogGuardsIgnoreInvalidSelectionAndLocation() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        UnifiedFileManagerDialogController.LocationItem serverItem =
                UnifiedFileManagerDialogController.LocationItem.of(server("GuardSrv", "guard-1"));
        setField(controller, "currentLocation", serverItem);
        onFx(() -> {
            controller.handleLocationClick(null);
            controller.handleLocationClick(UnifiedFileManagerDialogController.LocationItem.group("g"));
        });
        assertSame(serverItem, getField(controller, "currentLocation"));

        RecordingFavoriteService favorites = new RecordingFavoriteService();
        setField(controller, "favoriteFolderService", favorites);
        TableView<FileInfo> table = new TableView<>();
        FileInfo plain = file("plain.log", HOME + "plain.log", 5L, 1000L, false);
        FileInfo folder = file("folder", HOME + "folder", 0L, 1000L, true);
        table.setItems(FXCollections.observableArrayList(plain, folder));
        setField(controller, "fileTable", table);
        RecordingHost host = new RecordingHost();
        setField(controller, "host", host);
        onFx(() -> {
            table.getSelectionModel().clearSelection();
            invoke(controller, "handleFileDoubleClick");
            invoke(controller, "handleTail");
            invoke(controller, "handleAddToFavorites");
            invoke(controller, "handleRemoveFromFavorites");
            invoke(controller, "handlePreview");
            table.getSelectionModel().select(plain);
            invoke(controller, "handleAddToFavorites");
            invoke(controller, "handleRemoveFromFavorites");
            table.getSelectionModel().select(folder);
            invoke(controller, "handleTail");
            invoke(controller, "handlePreview");
        });
        assertTrue(favorites.added.isEmpty());
        assertTrue(favorites.removed.isEmpty());
        assertEquals(0, host.previews);
        assertEquals(UnifiedFileManagerDialogController.OpenAction.OPEN, controller.getOpenAction());
        assertTrue(controller.getSelectedFile() == null || !controller.getSelectedFile().isFile());

        UnifiedFileManagerDialogController noLocation = bare();
        assertNull(invokeForFailure(noLocation, "loadFavoritesForCurrentLocation", new Class<?>[0]));
    }

    @Test
    void navigationGuardsIgnoreMissingPath() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        TextField pathField = new TextField("keep");
        setField(controller, "pathField", pathField);
        onFx(() -> {
            invoke(controller, "navigateTo", new Class<?>[]{String.class}, (Object) null);
            invoke(controller, "navigateTo", new Class<?>[]{String.class}, "");
        });
        assertEquals("keep", pathField.getText());
        assertNull(getField(controller, "currentPath"));
        assertNull(invokeForFailure(controller, "navigateUp", new Class<?>[0]));
    }

    @Test
    void navigateUpUsesRemoteParents() throws Exception {
        RecordingSsh ssh = new RecordingSsh();
        sshFactory.next = ssh;
        SSHServerModel remote = server("UpSrv", "up-1");
        remote.setPassword("pw");
        onFx(() -> {
            setField(wired, "currentLocation", UnifiedFileManagerDialogController.LocationItem.of(remote));
            setField(wired, "currentPath", "/a/b");
            invoke(wired, "navigateUp");
        });
        TextField pathField = getField(wired, "pathField");
        assertEquals("/a", pathField.getText());
        onFx(() -> invoke(wired, "navigateUp"));
        assertEquals("/", pathField.getText());
        onFx(() -> invoke(wired, "navigateUp"));
        assertEquals("/", pathField.getText());
    }

    @Test
    void handleLocationSelectedCancelsPendingPrefetch() throws Exception {
        CompletableFuture<Object> pending = new CompletableFuture<>();
        onFx(() -> {
            setField(wired, "currentPrefetchFuture", pending);
            invoke(wired, "handleLocationSelected",
                    new Class<?>[]{UnifiedFileManagerDialogController.LocationItem.class},
                    UnifiedFileManagerDialogController.LocationItem.local());
        });
        assertTrue(pending.isCancelled());
        assertTrue(((Stack<?>) getField(wired, "backHistory")).isEmpty());
        assertTrue(((Stack<?>) getField(wired, "forwardHistory")).isEmpty());
    }

    @Test
    void refreshCurrentPathInvalidatesCacheAndCancelsPrefetch() throws Exception {
        String dir = LOCAL_DIR + SEP + "refresh";
        String key = "local:" + Paths.get(dir).toAbsolutePath().normalize();
        CountDownLatch block = new CountDownLatch(1);
        localFileService.blockOnList = block;
        CompletableFuture<Object> pending = new CompletableFuture<>();
        try {
            onFx(() -> {
                setField(wired, "currentLocation", UnifiedFileManagerDialogController.LocationItem.local());
                setField(wired, "currentPath", dir);
                putCacheEntry(wired, key, List.of());
                setField(wired, "currentPrefetchFuture", pending);
                invoke(wired, "refreshCurrentPath");
            });
            assertFalse(directoryCache(wired).containsKey(key));
            assertTrue(pending.isCancelled());
        } finally {
            block.countDown();
            localFileService.blockOnList = null;
        }
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> directoryCache(wired).containsKey(key));
    }

    @Test
    void loadFilesRemoteReconnectsAndListsThroughSsh() throws Exception {
        RecordingSsh ssh = new RecordingSsh();
        ssh.listings.put("/remote-load", List.of(remoteFile("remote-loaded.log", "/remote-load/remote-loaded.log")));
        sshFactory.next = ssh;
        sshFactory.created.set(0);
        SSHServerModel remote = server("ReconnectSrv", "reconnect-1");
        remote.setPassword("pw");
        onFx(() -> {
            setField(wired, "currentLocation", UnifiedFileManagerDialogController.LocationItem.of(remote));
            setField(wired, "activeSshService", null);
            invoke(wired, "navigateTo", new Class<?>[]{String.class}, "/remote-load");
        });
        FileInfo loaded = waitForTableItem(wired, "remote-loaded.log");
        assertNotNull(loaded);
        assertEquals(1, sshFactory.created.get());
        assertEquals(1, ssh.connectCount.get());
    }

    @Test
    void triggerSpeculativePrefetchSkipsLocalAndDisconnectedLocations() throws Exception {
        RecordingSsh offline = new RecordingSsh();
        offline.connected = false;
        directoryCache(wired).clear();
        onFx(() -> {
            setField(wired, "currentLocation", UnifiedFileManagerDialogController.LocationItem.local());
            invoke(wired, "triggerSpeculativePrefetch", new Class<?>[]{String.class, List.class}, HOME, List.of());
            setField(wired, "currentLocation",
                    UnifiedFileManagerDialogController.LocationItem.of(server("SkipPrefetch", "skip-1")));
            setField(wired, "activeSshService", null);
            invoke(wired, "triggerSpeculativePrefetch", new Class<?>[]{String.class, List.class}, "/x", List.of());
            setField(wired, "activeSshService", offline);
            invoke(wired, "triggerSpeculativePrefetch", new Class<?>[]{String.class, List.class}, "/x", List.of());
        });
        assertTrue(directoryCache(wired).isEmpty());
    }

    @Test
    void triggerSpeculativePrefetchCachesRemoteSubdirectories() throws Exception {
        String serverName = "PrefetchSrv";
        SSHServerModel remote = server(serverName, "prefetch-1");
        RecordingSsh ssh = new RecordingSsh();
        ssh.listings.put("/root/sub1", List.of(remoteFile("a.log", "/root/sub1/a.log")));
        ssh.failPaths.add("/root/sub3");
        List<FileInfo> files = List.of(
                file("sub1", "/root/sub1", 0L, 1000L, true),
                file("..", "/root", 0L, 1000L, true),
                file("sub2", "/root/sub2", 0L, 1000L, true),
                file("sub3", "/root/sub3", 0L, 1000L, true),
                file("plain.log", "/root/plain.log", 5L, 1000L, false));
        CompletableFuture<Object> pending = new CompletableFuture<>();
        onFx(() -> {
            setField(wired, "currentLocation", UnifiedFileManagerDialogController.LocationItem.of(remote));
            setField(wired, "activeSshService", ssh);
            setField(wired, "currentPrefetchFuture", pending);
            putCacheEntry(wired, serverName + ":/root/sub2", List.of());
            invoke(wired, "triggerSpeculativePrefetch", new Class<?>[]{String.class, List.class}, "/root", files);
        });
        assertTrue(pending.isCancelled());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> directoryCache(wired).containsKey(serverName + ":/root/sub1"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> ssh.listedPaths.contains("/root/sub3"));
        assertTrue(ssh.listedPaths.contains("/root/sub1"));
        assertTrue(ssh.listedPaths.contains("/root/sub3"));
        assertFalse(ssh.listedPaths.contains("/root/sub2"));
        assertFalse(ssh.listedPaths.contains("/root"));
    }

    @Test
    void ensureSshConnectedSkipsLocalAndConnectedSessions() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        assertNull(invokeForFailure(controller, "ensureSshConnected", new Class<?>[0]));
        setField(controller, "currentLocation", UnifiedFileManagerDialogController.LocationItem.local());
        assertNull(invokeForFailure(controller, "ensureSshConnected", new Class<?>[0]));
        RecordingSsh connected = new RecordingSsh();
        SSHServerModel remote = server("SkipConnect", "sc-1");
        remote.setPassword("pw");
        setField(controller, "currentLocation", UnifiedFileManagerDialogController.LocationItem.of(remote));
        setField(controller, "activeSshService", connected);
        assertNull(invokeForFailure(controller, "ensureSshConnected", new Class<?>[0]));
        assertEquals(0, connected.connectCount.get());
    }

    @Test
    void ensureSshConnectedReconnectsAndThrowsOnFailure() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        assertInstanceOf(IllegalStateException.class,
                invokeForFailure(controller, "createSshService", new Class<?>[0]));
        RecordingSsh ssh = new RecordingSsh();
        setField(controller, "sshServiceFactory", (SSHServiceFactory) () -> ssh);
        SSHServerModel remote = server("ReconnectNow", "rn-1");
        remote.setPassword("pw");
        setField(controller, "currentLocation", UnifiedFileManagerDialogController.LocationItem.of(remote));
        assertNull(invokeForFailure(controller, "ensureSshConnected", new Class<?>[0]));
        assertEquals(1, ssh.connectCount.get());
        assertSame(ssh, getField(controller, "activeSshService"));

        RecordingSsh refusing = new RecordingSsh();
        refusing.connected = false;
        UnifiedFileManagerDialogController failing = bare();
        setField(failing, "sshServiceFactory", (SSHServiceFactory) () -> refusing);
        setField(failing, "currentLocation",
                UnifiedFileManagerDialogController.LocationItem.of(server("NoPassword", "np-1")));
        assertInstanceOf(IOException.class, invokeForFailure(failing, "ensureSshConnected", new Class<?>[0]));
        SSHServerModel withPassword = server("RefusedConnect", "rc-1");
        withPassword.setPassword("pw");
        setField(failing, "currentLocation",
                UnifiedFileManagerDialogController.LocationItem.of(withPassword));
        assertInstanceOf(IOException.class, invokeForFailure(failing, "ensureSshConnected", new Class<?>[0]));
    }

    @Test
    void readTailWindowSizeReadsPreferenceAndFallsBack() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        InMemoryPreferenceService prefs = new InMemoryPreferenceService();
        setField(controller, "preferenceService", prefs);
        assertEquals(20000, invoke(controller, "readTailWindowSize", new Class<?>[0]));
        prefs.saveOrUpdatePreferences(new Preference("main_tail_window_size", "12345"));
        assertEquals(12345, invoke(controller, "readTailWindowSize", new Class<?>[0]));
        prefs.saveOrUpdatePreferences(new Preference("main_tail_window_size", "not-a-number"));
        assertEquals(20000, invoke(controller, "readTailWindowSize", new Class<?>[0]));
    }

    @Test
    void restoreFileSelectionSkipsMismatchedFolderAndSwallowsFailures() throws Exception {
        InMemoryPreferenceService prefs = new InMemoryPreferenceService();
        prefs.saveOrUpdatePreferences(new Preference("file_manager_last_file_local", "target.log"));

        UnifiedFileManagerDialogController matching = bare();
        prefs.saveOrUpdatePreferences(new Preference("file_manager_last_file_dir_local", LOCAL_DIR));
        setField(matching, "preferenceService", prefs);
        setField(matching, "currentPath", LOCAL_DIR);
        TableView<FileInfo> table = new TableView<>();
        FileInfo target = file("target.log", LOCAL_DIR + SEP + "target.log", 5L, 1000L, false);
        table.setItems(FXCollections.observableArrayList(target));
        setField(matching, "fileTable", table);
        onFx(() -> invoke(matching, "restoreFileSelection"));
        assertSame(target, table.getSelectionModel().getSelectedItem());

        UnifiedFileManagerDialogController mismatched = bare();
        prefs.saveOrUpdatePreferences(new Preference("file_manager_last_file_dir_local", HOME + "elsewhere"));
        setField(mismatched, "preferenceService", prefs);
        setField(mismatched, "currentPath", LOCAL_DIR);
        TableView<FileInfo> otherTable = new TableView<>();
        otherTable.setItems(FXCollections.observableArrayList(target));
        setField(mismatched, "fileTable", otherTable);
        onFx(() -> invoke(mismatched, "restoreFileSelection"));
        assertNull(otherTable.getSelectionModel().getSelectedItem());

        UnifiedFileManagerDialogController broken = bare();
        setField(broken, "preferenceService", prefs);
        assertNull(invokeForFailure(broken, "restoreFileSelection", new Class<?>[0]));
    }

    @Test
    void restoreLastLocationIgnoresUnknownSavedServer() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        InMemoryPreferenceService prefs = new InMemoryPreferenceService();
        prefs.saveOrUpdatePreferences(new Preference("file_manager_last_location", "missing-server"));
        setField(controller, "preferenceService", prefs);
        TreeView<UnifiedFileManagerDialogController.LocationItem> tree = new TreeView<>();
        TreeItem<UnifiedFileManagerDialogController.LocationItem> root =
                new TreeItem<>(UnifiedFileManagerDialogController.LocationItem.root());
        root.getChildren().add(new TreeItem<>(
                UnifiedFileManagerDialogController.LocationItem.of(server("Known", "known-1"))));
        tree.setRoot(root);
        setField(controller, "locationTree", tree);
        setField(controller, "locationTreeReady", true);
        assertNull(invokeForFailure(controller, "restoreLastLocation", new Class<?>[0]));
        assertNull(tree.getSelectionModel().getSelectedItem());
        assertNull(controller.getActiveServer());
    }

    @Test
    void restoreSortOrderingAppliesIdempotentlyAndClearsInvalidPreferences() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        InMemoryPreferenceService prefs = new InMemoryPreferenceService();
        setField(controller, "preferenceService", prefs);
        TableView<FileInfo> table = new TableView<>();
        TableColumn<FileInfo, String> size = new TableColumn<>("Size");
        size.setId("sizeColumn");
        TableColumn<FileInfo, String> name = new TableColumn<>("Name");
        name.setId("nameColumn");
        table.getColumns().addAll(size, name);
        setField(controller, "fileTable", table);

        prefs.saveOrUpdatePreferences(new Preference("file_manager_sort_local", "sizeColumn:DESCENDING"));
        onFx(() -> invoke(controller, "restoreSortOrdering"));
        assertEquals(List.of(size), table.getSortOrder());
        assertEquals(TableColumn.SortType.DESCENDING, size.getSortType());
        onFx(() -> invoke(controller, "restoreSortOrdering"));
        assertEquals(List.of(size), table.getSortOrder());

        prefs.saveOrUpdatePreferences(new Preference("file_manager_sort_local", ""));
        onFx(() -> invoke(controller, "restoreSortOrdering"));
        assertTrue(table.getSortOrder().isEmpty());

        prefs.saveOrUpdatePreferences(new Preference("file_manager_sort_local", "a:b:c"));
        onFx(() -> invoke(controller, "restoreSortOrdering"));
        assertTrue(table.getSortOrder().isEmpty());

        prefs.saveOrUpdatePreferences(new Preference("file_manager_sort_local", "missingColumn:ASCENDING"));
        onFx(() -> invoke(controller, "restoreSortOrdering"));
        assertTrue(table.getSortOrder().isEmpty());

        prefs.saveOrUpdatePreferences(new Preference("file_manager_sort_local", "sizeColumn:BOGUS"));
        onFx(() -> invoke(controller, "restoreSortOrdering"));
        assertTrue(table.getSortOrder().isEmpty());
    }

    @Test
    void handleFindInFilesCapturesResultOrIgnoresDefaultHost() throws Exception {
        RecordingSsh ssh = new RecordingSsh();
        SSHServerModel remote = server("FindSrv", "find-1");
        InMemoryPreferenceService prefs = new InMemoryPreferenceService();
        prefs.saveOrUpdatePreferences(new Preference("main_tail_window_size", "12345"));
        FileInfo found = file("found.log", "/var/found.log", 5L, 1000L, false);

        UnifiedFileManagerDialogController controller = bare();
        setField(controller, "currentLocation", UnifiedFileManagerDialogController.LocationItem.of(remote));
        setField(controller, "activeSshService", ssh);
        setField(controller, "preferenceService", prefs);
        RecordingHost host = new RecordingHost();
        host.result = new FileManagerHost.FindInFilesResult(found, 42, 500, 7, true, false);
        setField(controller, "host", host);
        assertNull(invokeForFailure(controller, "handleFindInFiles", new Class<?>[0]));
        assertEquals(12345, host.lastMaxTailWindow);
        assertSame(found, controller.getSelectedFile());
        assertEquals(42, controller.getPendingJumpLine());
        assertEquals(500, controller.getPendingTailWindowLines());
        assertEquals(7, controller.getPendingTailJumpIndex());
        assertEquals(UnifiedFileManagerDialogController.OpenAction.TAIL, controller.getOpenAction());
        assertSame(ssh, controller.getSshService());
        assertEquals(0, ssh.disconnectCount.get());

        UnifiedFileManagerDialogController openInstead = bare();
        setField(openInstead, "currentLocation", UnifiedFileManagerDialogController.LocationItem.of(remote));
        setField(openInstead, "activeSshService", ssh);
        setField(openInstead, "preferenceService", prefs);
        RecordingHost openHost = new RecordingHost();
        openHost.result = new FileManagerHost.FindInFilesResult(found, 1, 2, 3, true, true);
        setField(openInstead, "host", openHost);
        assertNull(invokeForFailure(openInstead, "handleFindInFiles", new Class<?>[0]));
        assertEquals(UnifiedFileManagerDialogController.OpenAction.OPEN, openInstead.getOpenAction());

        UnifiedFileManagerDialogController defaultHost = bare();
        setField(defaultHost, "currentLocation", UnifiedFileManagerDialogController.LocationItem.of(remote));
        setField(defaultHost, "activeSshService", ssh);
        setField(defaultHost, "preferenceService", prefs);
        assertNull(invokeForFailure(defaultHost, "handleFindInFiles", new Class<?>[0]));
        assertNull(defaultHost.getSelectedFile());
        assertEquals(UnifiedFileManagerDialogController.OpenAction.OPEN, defaultHost.getOpenAction());
    }

    @Test
    void closeDialogCancelsPrefetchAndDisconnectsWithoutFile() throws Exception {
        UnifiedFileManagerDialogController controller = bare();
        RecordingSsh ssh = new RecordingSsh();
        CompletableFuture<Object> pending = new CompletableFuture<>();
        setField(controller, "activeSshService", ssh);
        setField(controller, "currentPrefetchFuture", pending);
        assertNull(invokeForFailure(controller, "closeDialog", new Class<?>[0]));
        assertTrue(pending.isCancelled());
        assertEquals(1, ssh.disconnectCount.get());
    }

    private static UnifiedFileManagerDialogController bare() {
        return new UnifiedFileManagerDialogController();
    }

    private static SSHServerModel server(String name, String id) {
        SSHServerModel server = new SSHServerModel(name, "host.example.com", 22, "user");
        server.setId(id);
        return server;
    }

    private static FileInfo file(String name, String path, long size, long modified, boolean directory) {
        FileInfo info = new FileInfo();
        info.setName(name);
        info.setPath(path);
        info.setSize(size);
        info.setModifiedTime(modified);
        info.setDirectory(directory);
        return info;
    }

    private static RemoteFileInfo remoteFile(String name, String path) {
        RemoteFileInfo info = new RemoteFileInfo();
        info.setName(name);
        info.setPath(path);
        info.setDirectory(false);
        info.setSize(5L);
        info.setModifiedTime(1000L);
        return info;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) {
        try {
            java.lang.reflect.Field field = UnifiedFileManagerDialogController.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field field = UnifiedFileManagerDialogController.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object invoke(Object target, String name) {
        return invoke(target, name, new Class<?>[0]);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object invokeStatic(Class<?> type, String name, Class<?>[] types, Object... args) {
        try {
            Method method = type.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Throwable invokeForFailure(Object target, String name) {
        return invokeForFailure(target, name, new Class<?>[0]);
    }

    private static Throwable invokeForFailure(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(target, args);
            return null;
        } catch (InvocationTargetException e) {
            return e.getCause();
        } catch (Exception e) {
            return e;
        }
    }

    private static void onFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> directoryCache(Object controller) {
        return getField(controller, "directoryCache");
    }

    private static void putCacheEntry(Object controller, String key, List<FileInfo> files) {
        try {
            Class<?> entryClass = Class.forName(
                    "com.seeloggyplus.features.files.presentation.UnifiedFileManagerDialogController$CacheEntry");
            Constructor<?> constructor = entryClass.getDeclaredConstructor(List.class);
            constructor.setAccessible(true);
            directoryCache(controller).put(key, constructor.newInstance(files));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static FileInfo waitForTableItem(Object controller, String name) throws Exception {
        TableView<FileInfo> table = getField(controller, "fileTable");
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            FileInfo found = WaitForAsyncUtils.asyncFx(() -> table.getItems().stream()
                    .filter(f -> name.equals(f.getName())).findFirst().orElse(null)).get();
            if (found != null) {
                return found;
            }
            Thread.sleep(25);
        }
        return null;
    }

    static class FakeLocalFileService implements LocalFileService {
        final AtomicInteger calls = new AtomicInteger();
        volatile CountDownLatch blockOnList;

        @Override
        public String getHomeDirectory() {
            return HOME;
        }

        @Override
        public List<FileInfo> listFiles(String directoryPath) throws IOException {
            calls.incrementAndGet();
            CountDownLatch latch = blockOnList;
            if (latch != null) {
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            List<FileInfo> files = new ArrayList<>();
            files.add(file("marker-folder", directoryPath + SEP + "marker-folder", 0L, 1000L, true));
            files.add(file("marker.log", directoryPath + SEP + "marker.log", 5L, 1000L, false));
            return files;
        }
    }

    static class RecordingSsh extends SSHServiceImpl {
        volatile boolean connected = true;
        final AtomicInteger connectCount = new AtomicInteger();
        final AtomicInteger disconnectCount = new AtomicInteger();
        final List<String> listedPaths = new ArrayList<>();
        final Map<String, List<RemoteFileInfo>> listings = new HashMap<>();
        final List<String> failPaths = new ArrayList<>();

        @Override
        public boolean connect(String host, int port, String username, String password) {
            connectCount.incrementAndGet();
            return connected;
        }

        @Override
        public boolean connect(String host, int port, String username, String password, long ttlMillis) {
            return connect(host, port, username, password);
        }

        @Override
        public void disconnect() {
            disconnectCount.incrementAndGet();
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public List<RemoteFileInfo> listFiles(String remotePath) throws IOException {
            synchronized (listedPaths) {
                listedPaths.add(remotePath);
            }
            if (failPaths.contains(remotePath)) {
                throw new IOException("simulated failure for " + remotePath);
            }
            List<RemoteFileInfo> listing = listings.get(remotePath);
            return listing == null ? List.of() : new ArrayList<>(listing);
        }
    }

    static class FakeSshFactory implements SSHServiceFactory {
        volatile RecordingSsh next;
        final AtomicInteger created = new AtomicInteger();

        @Override
        public SSHService create() {
            created.incrementAndGet();
            return next;
        }
    }

    static class RecordingCatalog implements ServerCatalog {
        final List<SSHServerModel> servers = new ArrayList<>();
        final List<String> groups = new ArrayList<>();
        final List<String> saved = new ArrayList<>();
        final List<List<String>> reorderCalls = new ArrayList<>();

        @Override
        public List<SSHServerModel> getAllServers() {
            return new ArrayList<>(servers);
        }

        @Override
        public SSHServerModel getServerById(String id) {
            return servers.stream().filter(server -> Objects.equals(id, server.getId())).findFirst().orElse(null);
        }

        @Override
        public void saveServer(SSHServerModel server) {
            saved.add(server.getId());
            if (!servers.contains(server)) {
                servers.add(server);
            }
        }

        @Override
        public void reorderServers(List<String> orderedIds) {
            reorderCalls.add(new ArrayList<>(orderedIds));
        }

        @Override
        public List<String> getGroupNames() {
            return new ArrayList<>(groups);
        }

        @Override
        public void createGroup(String name) {
            groups.add(name);
        }

        @Override
        public void renameGroup(String oldName, String newName) {
            groups.remove(oldName);
            groups.add(newName);
        }

        @Override
        public void deleteGroup(String name) {
            groups.remove(name);
        }

        @Override
        public void updateServerLastUsed(String serverId) {
        }
    }

    static class RecordingFavoriteService implements FavoriteFolderService {
        final List<FavoriteFolder> favorites = new ArrayList<>();
        final List<Integer> added = new ArrayList<>();
        final List<Integer> removed = new ArrayList<>();

        @Override
        public void addFavorite(String name, String path, String locationId) {
            FavoriteFolder folder = new FavoriteFolder(favorites.size() + 100, name, path, locationId);
            favorites.add(folder);
            added.add(folder.getId());
        }

        @Override
        public void removeFavorite(int id) {
            removed.add(id);
            favorites.removeIf(folder -> folder.getId() != null && folder.getId() == id);
        }

        @Override
        public List<FavoriteFolder> getFavoritesForLocation(String locationId) {
            return new ArrayList<>(favorites);
        }

        @Override
        public boolean isFavorite(String path, String locationId) {
            return favorites.stream().anyMatch(folder -> Objects.equals(folder.getPath(), path));
        }
    }

    static class InMemoryPreferenceService implements PreferenceService {
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
            store.forEach((code, value) -> list.add(new Preference(code, value)));
            return list;
        }

        @Override
        public void saveOrUpdatePreferences(Preference preferences) {
            if (preferences != null) {
                store.put(preferences.getCode(), preferences.getValue());
            }
        }
    }

    static class RecordingHost implements FileManagerHost {
        FindInFilesResult result;
        int previews;
        int lastMaxTailWindow = -1;

        @Override
        public FindInFilesResult openFindInFiles(SSHService sshService, SSHServerModel server, String path,
                int maxTailWindow) {
            lastMaxTailWindow = maxTailWindow;
            return result;
        }

        @Override
        public void openPreview(FileInfo file, SSHService sshService) {
            previews++;
        }

        @Override
        public void openServerManagement() {
        }
    }
}
