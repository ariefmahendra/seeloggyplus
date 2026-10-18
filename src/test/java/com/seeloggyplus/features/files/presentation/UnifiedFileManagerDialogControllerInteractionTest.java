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
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class UnifiedFileManagerDialogControllerInteractionTest {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final String HOME = IS_WINDOWS ? "C:\\" : "/";

    private Stage stage;
    private UnifiedFileManagerDialogController controller;
    private RecordingCatalog catalog;
    private RecordingFavoriteService favorites;
    private InMemoryPreferenceService preferences;
    private RecordingSsh ssh;
    private FakeSshFactory sshFactory;
    private SSHServerModel serverA;
    private SSHServerModel serverB;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UnifiedFileManagerDialog.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        catalog = new RecordingCatalog();
        favorites = new RecordingFavoriteService();
        preferences = new InMemoryPreferenceService();
        ssh = new RecordingSsh();
        sshFactory = new FakeSshFactory(ssh);
        serverA = new SSHServerModel("Alpha", "alpha.example.com", 22, "user");
        serverA.setId("srv-a");
        serverB = new SSHServerModel("Beta", "beta.example.com", 22, "user");
        serverB.setId("srv-b");
        catalog.servers.addAll(List.of(serverA, serverB));
        setField(controller, "localFileService", new FakeLocalFileService());
        setField(controller, "favoriteFolderService", favorites);
        setField(controller, "preferenceService", preferences);
        setField(controller, "sshServiceFactory", sshFactory);
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.asyncFx(() -> controller.setServerCatalog(catalog)).get();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void toolbarButtonsRemainReadableWhenHoveredAndFocusedAcrossThemes() throws Exception {
        onFx(() -> {
            var root = stage.getScene().getRoot();
            var toolbar = (javafx.scene.layout.HBox) root.lookup("#pathToolbar");
            try {
                for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                    AppTheme.setTheme(theme); root.applyCss(); root.layout();
                    for (String id : List.of("backButton", "forwardButton", "upButton", "homeButton", "refreshButton", "findInFilesButton")) {
                        var button = (javafx.scene.control.Button) root.lookup("#" + id);
                        button.setDisable(false);
                        double height = button.getHeight(), width = button.getWidth();
                        for (String state : List.of("hover", "pressed")) {
                            button.pseudoClassStateChanged(PseudoClass.getPseudoClass(state), true);
                            button.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
                            root.applyCss(); root.layout();
                            var bg = com.seeloggyplus.shared.ui.RenderedColors.background(button);
                            assertTrue(colourContrast((javafx.scene.paint.Color) button.getTextFill(), bg) >= 4.5, theme + id + state);
                            if (button.getGraphic() instanceof de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView icon) {
                                assertTrue(colourContrast((javafx.scene.paint.Color) icon.getFill(), bg) >= 3.0, theme + id + state + " icon");
                            }
                            assertTrue(button.getHeight() >= 24);
                            assertEquals(height, button.getHeight(), 0.01);
                            assertEquals(width, button.getWidth(), 0.01);
                            button.pseudoClassStateChanged(PseudoClass.getPseudoClass(state), false);
                            button.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), false);
                        }
                    }
                }
            } finally { AppTheme.setTheme(AppTheme.Theme.LIGHT); }
        });
    }

    private static double colourContrast(javafx.scene.paint.Color a, javafx.scene.paint.Color b) {
        double x = colourLuminance(a), y = colourLuminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static double colourLuminance(javafx.scene.paint.Color c) {
        return 0.2126 * colourLinear(c.getRed()) + 0.7152 * colourLinear(c.getGreen()) + 0.0722 * colourLinear(c.getBlue());
    }

    private static double colourLinear(double v) {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    @Test
    void parentDirectoryStaysFirstForEverySortDirectionAndWithoutSorting() throws Exception {
        onFx(() -> {
            TableView<FileInfo> table = getField(controller, "fileTable");
            ObservableList<FileInfo> files = getField(controller, "allFiles");
            FileInfo parent = directory("..", HOME);
            parent.setModifiedTime(1000);
            parent.setPermissions("zzzz"); parent.setOwner("zzzz");
            FileInfo alpha = new FileInfo("alpha.log", HOME + "alpha.log", 1, false, 2000, FileInfo.SourceType.LOCAL);
            alpha.setPermissions("aaaa"); alpha.setOwner("aaaa");
            FileInfo zeta = new FileInfo("zeta.log", HOME + "zeta.log", 9999, false, 3000, FileInfo.SourceType.LOCAL);
            zeta.setPermissions("mmmm"); zeta.setOwner("mmmm");
            table.getSortOrder().clear();
            files.setAll(zeta, alpha, parent);
            assertSame(parent, table.getItems().getFirst());
            for (TableColumn<FileInfo, ?> column : table.getColumns()) {
                if (!column.isSortable()) continue;
                for (TableColumn.SortType direction : TableColumn.SortType.values()) {
                    column.setSortType(direction);
                    table.getSortOrder().setAll(column);
                    table.sort();
                    assertSame(parent, table.getItems().getFirst(), column.getId() + direction);
                    assertTrue(table.getComparator().compare(table.getItems().get(1), table.getItems().get(2)) <= 0,
                            "other entries must follow the selected sorting");
                }
            }
            table.getSortOrder().clear(); table.sort();
            assertSame(parent, table.getItems().getFirst());
        });
    }

    @Test
    void breadcrumbWidthFollowsFileManagerWidth() throws Exception {
        onFx(() -> {
            javafx.scene.layout.Region root = (javafx.scene.layout.Region) stage.getScene().getRoot();
            root.resize(1000, 600); root.requestLayout(); root.layout();
            javafx.scene.layout.Region breadcrumb = (javafx.scene.layout.Region) root.lookup(".file-path-breadcrumb");
            double narrow = breadcrumb.getWidth();
            root.resize(1500, 600); root.requestLayout(); root.layout();
            assertEquals(500, breadcrumb.getWidth() - narrow, 2);
            root.resize(1000, 600); root.requestLayout(); root.layout();
            assertEquals(narrow, breadcrumb.getWidth(), 2);
        });
    }

    @Test
    void controlLAcceleratorSelectsPathFieldText() throws Exception {
        TextField pathField = getField(controller, "pathField");
        WaitForAsyncUtils.asyncFx(() -> pathField.setText(HOME + "accel")).get();
        Runnable action = WaitForAsyncUtils.asyncFx(() -> stage.getScene().getAccelerators()
                .get(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN))).get();
        assertNotNull(action);
        onFx(action);
        assertEquals(HOME + "accel", WaitForAsyncUtils.asyncFx(pathField::getSelectedText).get());
    }

    @Test
    void windowGainedFocusClearsSuppressionFlag() throws Exception {
        setField(controller, "suppressAutoRefresh", true);
        onFx(() -> invoke(controller, "handleWindowGainedFocus"));
        assertFalse((Boolean) getField(controller, "suppressAutoRefresh"));
        onFx(() -> invoke(controller, "handleWindowGainedFocus"));
        assertFalse((Boolean) getField(controller, "suppressAutoRefresh"));
    }

    @Test
    void locationContainerDragOverAndDropQueuePlacement() throws Exception {
        catalog.reorderCalls.clear();
        VBox container = getField(controller, "locationsContainer");
        Label hint = getField(controller, "locationDropHint");
        setField(controller, "draggedLocationServerId", serverA.getId());
        setField(controller, "draggedLocationGroup", null);
        DragEvent over = new DragEvent(DragEvent.DRAG_OVER, null, 10, 10, 10, 10,
                TransferMode.MOVE, null, null, null);
        DragEvent firedOver = (DragEvent) fire(container, over);
        assertTrue(firedOver.isConsumed());
        assertTrue(hint.isVisible());
        assertTrue(hint.getText().contains("Move to end of ungrouped servers"));

        DragEvent dropped = new DragEvent(DragEvent.DRAG_DROPPED, null, 10, 10, 10, 10,
                TransferMode.MOVE, null, null, null);
        DragEvent firedDropped = (DragEvent) fire(container, dropped);
        assertTrue(firedDropped.isDropCompleted());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !catalog.reorderCalls.isEmpty());
        assertEquals(List.of(serverB.getId(), serverA.getId()), catalog.reorderCalls.get(0));
        assertFalse(hint.isVisible());
    }

    @Test
    void locationTreeKeyPressConsumesNavigationKeys() throws Exception {
        TreeView<UnifiedFileManagerDialogController.LocationItem> tree = getField(controller, "locationTree");
        WaitForAsyncUtils.asyncFx(() -> tree.getSelectionModel().clearSelection()).get();
        KeyEvent enter = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false);
        assertTrue(((KeyEvent) fire(tree, enter)).isConsumed());
        KeyEvent space = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, false, false, false);
        assertTrue(((KeyEvent) fire(tree, space)).isConsumed());
        KeyEvent tab = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.A, false, false, false, false);
        assertFalse(((KeyEvent) fire(tree, tab)).isConsumed());
    }

    @Test
    void favoriteDoubleClickNavigatesToFolder() throws Exception {
        favorites.favorites.clear();
        String favPath = HOME + "favtarget";
        favorites.favorites.add(new FavoriteFolder(21, "favtarget", favPath, "local"));
        setField(controller, "currentLocation", UnifiedFileManagerDialogController.LocationItem.local());
        reloadFavorites();
        ListView<FavoriteFolder> list = getField(controller, "favoritesListView");
        assertEquals(1, list.getItems().size());
        WaitForAsyncUtils.asyncFx(() -> list.getSelectionModel().select(0)).get();
        MouseEvent click = new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5, MouseButton.PRIMARY, 2,
                false, false, false, false, false, false, false, false, false, true, null);
        onFx(() -> Event.fireEvent(list, click));
        TextField pathField = getField(controller, "pathField");
        assertEquals(favPath, pathField.getText());
    }

    @Test
    void favoriteAreaDoesNotExposeSelectedServerGroupActions() {
        ListView<FavoriteFolder> list = getField(controller, "favoritesListView");
        onFx(() -> {
            assertTrue(list.getContextMenu().getItems().stream()
                    .noneMatch(item -> java.util.Set.of("Rename group...", "Delete group", "Move to group...").contains(item.getText())));
            for (String field : new String[]{"favoritesPane", "favoritesBox"}) {
                Node target = getField(controller, field);
                Event.fireEvent(target, contextMenuEvent());
                assertTrue(javafx.stage.Window.getWindows().stream()
                        .noneMatch(window -> window instanceof javafx.scene.control.ContextMenu menu
                                && menu.isShowing() && menu.getItems().stream()
                                .anyMatch(item -> "New group...".equals(item.getText()))), field);
            }
        });
    }

    @Test
    void favoriteRemoveMenuItemRemovesFavorite() throws Exception {
        favorites.favorites.clear();
        favorites.removed.clear();
        favorites.favorites.add(new FavoriteFolder(22, "removeme", HOME + "removeme", "local"));
        setField(controller, "currentLocation", UnifiedFileManagerDialogController.LocationItem.local());
        reloadFavorites();
        ListView<FavoriteFolder> list = getField(controller, "favoritesListView");
        WaitForAsyncUtils.asyncFx(() -> list.getSelectionModel().select(0)).get();
        MenuItem remove = list.getContextMenu().getItems().stream()
                .filter(item -> "Remove Favorite".equals(item.getText())).findFirst().orElseThrow();
        onFx(remove::fire);
        assertEquals(List.of(22), favorites.removed);
        assertTrue(list.getItems().isEmpty());
    }

    @Test
    void fileTableContextMenuReflectsFavoriteState() throws Exception {
        FileInfo dir = directory("ctxdir", HOME + "ctxdir");
        ObservableList<FileInfo> allFiles = getField(controller, "allFiles");
        TableView<FileInfo> fileTable = getField(controller, "fileTable");
        onFx(() -> {
            allFiles.setAll(dir);
            fileTable.getSelectionModel().select(dir);
        });
        MenuItem add = getField(controller, "addToFavoritesMenuItem");
        MenuItem remove = getField(controller, "removeFromFavoritesMenuItem");
        onFx(() -> Event.fireEvent(fileTable, contextMenuEvent()));
        assertTrue(add.isVisible());
        assertFalse(remove.isVisible());
        Set<String> cache = getField(controller, "favoritePathsCache");
        onFx(() -> {
            cache.add(dir.getPath());
            Event.fireEvent(fileTable, contextMenuEvent());
        });
        assertFalse(add.isVisible());
        assertTrue(remove.isVisible());
        onFx(() -> fileTable.getContextMenu().hide());
    }

    @Test
    void fileTableDoubleClickOnDirectoryNavigates() throws Exception {
        FileInfo dir = directory("navdir", HOME + "navdir");
        ObservableList<FileInfo> allFiles = getField(controller, "allFiles");
        TableView<FileInfo> fileTable = getField(controller, "fileTable");
        onFx(() -> {
            allFiles.setAll(dir);
            fileTable.getSelectionModel().select(dir);
            fileTable.refresh();
            fileTable.applyCss();
            fileTable.layout();
        });
        WaitForAsyncUtils.waitForFxEvents();
        TableRow<?> row = WaitForAsyncUtils.asyncFx(() -> fileTable.lookupAll(".table-row-cell").stream()
                .filter(node -> node instanceof TableRow<?> candidate && !candidate.isEmpty())
                .map(node -> (TableRow<?>) node).findFirst().orElse(null)).get();
        assertNotNull(row);
        assertNull(invoke(controller, "findParentTableRow", new Class<?>[]{Node.class}, fileTable));
        MouseEvent click = new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5, MouseButton.PRIMARY, 2,
                false, false, false, false, false, false, false, false, false, true, null);
        onFx(() -> {
            allFiles.setAll(dir);
            fileTable.getSelectionModel().select(dir);
            Event.fireEvent(row, click);
        });
        TextField pathField = getField(controller, "pathField");
        assertEquals(HOME + "navdir", pathField.getText());
    }

    @Test
    void fileTableKeyPressNavigatesEnterAndBackspace() throws Exception {
        FileInfo dir = directory("keydir", HOME + "keydir");
        ObservableList<FileInfo> allFiles = getField(controller, "allFiles");
        TableView<FileInfo> fileTable = getField(controller, "fileTable");
        Stack<String> backHistory = getField(controller, "backHistory");
        KeyEvent enter = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false);
        onFx(() -> {
            backHistory.clear();
            setField(controller, "currentPath", HOME + "keybase");
            backHistory.push(HOME + "keybase");
            allFiles.setAll(dir);
            fileTable.getSelectionModel().select(dir);
            assertSame(dir, fileTable.getSelectionModel().getSelectedItem());
            assertTrue(((KeyEvent) fireNow(fileTable, enter)).isConsumed());
        });
        TextField pathField = getField(controller, "pathField");
        assertEquals(HOME + "keydir", pathField.getText());
        KeyEvent back = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.BACK_SPACE, false, false, false, false);
        assertTrue(((KeyEvent) fire(fileTable, back)).isConsumed());
        assertEquals(HOME + "keybase", pathField.getText());
    }

    @Test
    void fileTableControlCIgnoresEmptySelection() throws Exception {
        TableView<FileInfo> fileTable = getField(controller, "fileTable");
        ObservableList<FileInfo> allFiles = getField(controller, "allFiles");
        onFx(() -> {
            allFiles.clear();
            fileTable.getSelectionModel().clearSelection();
        });
        KeyEvent copy = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.C, false, true, false, false);
        assertTrue(((KeyEvent) fire(fileTable, copy)).isConsumed());
    }

    @Test
    void nameColumnRendersPlainFileIcon() throws Exception {
        FileInfo plain = new FileInfo();
        plain.setName("data.cfg");
        plain.setPath(HOME + "data.cfg");
        plain.setDirectory(false);
        ObservableList<FileInfo> allFiles = getField(controller, "allFiles");
        TableView<FileInfo> fileTable = getField(controller, "fileTable");
        onFx(() -> {
            allFiles.setAll(plain);
            fileTable.refresh();
            fileTable.applyCss();
            fileTable.layout();
        });
        WaitForAsyncUtils.waitForFxEvents();
        Set<Node> icons = WaitForAsyncUtils.asyncFx(() -> fileTable.lookupAll(".file-icon-file")).get();
        assertFalse(icons.isEmpty());
    }

    @Test
    void sizeAndModifiedComparatorsHandleFormattedValues() throws Exception {
        TableColumn<FileInfo, String> sizeColumn = getField(controller, "sizeColumn");
        TableColumn<FileInfo, String> modifiedColumn = getField(controller, "modifiedColumn");
        assertTrue(sizeColumn.getComparator().compare("1 KB", "2 KB") < 0);
        assertEquals(0, sizeColumn.getComparator().compare("1024 B", "1 KB"));
        assertEquals(0, modifiedColumn.getComparator().compare("-", "-"));
        assertEquals(-1, modifiedColumn.getComparator().compare(null, "2024-01-01 00:00"));
        assertEquals(1, modifiedColumn.getComparator().compare("2024-01-01 00:00", "-"));
        assertTrue(modifiedColumn.getComparator().compare("2024-01-01 00:00", "2024-02-01 00:00") < 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void locationCellDragOverAndDropReorderServer() throws Exception {
        catalog.reorderCalls.clear();
        TreeView<UnifiedFileManagerDialogController.LocationItem> tree = getField(controller, "locationTree");
        TreeCell<UnifiedFileManagerDialogController.LocationItem> cell =
                WaitForAsyncUtils.asyncFx(() -> tree.getCellFactory().call(tree)).get();
        UnifiedFileManagerDialogController.LocationItem target =
                UnifiedFileManagerDialogController.LocationItem.of(serverB);
        Method updateItem = javafx.scene.control.Cell.class.getDeclaredMethod("updateItem", Object.class, boolean.class);
        updateItem.setAccessible(true);
        onFx(() -> {
            try {
                updateItem.invoke(cell, target, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        setField(controller, "draggedLocationServerId", serverA.getId());
        setField(controller, "draggedLocationGroup", null);
        DragEvent over = new DragEvent(DragEvent.DRAG_OVER, null, 5, 5, 5, 5,
                TransferMode.MOVE, null, null, null);
        assertTrue(((DragEvent) fire(cell, over)).isConsumed());
        Label hint = getField(controller, "locationDropHint");
        assertTrue(hint.getText().contains("Move after Beta"));
        assertTrue(cell.getPseudoClassStates().contains(PseudoClass.getPseudoClass("drop-after")));

        DragEvent dropped = new DragEvent(DragEvent.DRAG_DROPPED, null, 5, 5, 5, 5,
                TransferMode.MOVE, null, null, null);
        assertTrue(((DragEvent) fire(cell, dropped)).isDropCompleted());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !catalog.reorderCalls.isEmpty());
        assertEquals(List.of(serverB.getId(), serverA.getId()), catalog.reorderCalls.get(0));

        DragEvent done = new DragEvent(DragEvent.DRAG_DONE, null, 5, 5, 5, 5,
                TransferMode.MOVE, null, null, null);
        fire(cell, done);
        assertNull(getField(controller, "draggedLocationServerId"));
        assertNull(getField(controller, "draggedLocationGroup"));
    }

    private void reloadFavorites() {
        onFx(() -> {
            setField(controller, "cachedFavoritesLocationId", null);
            invoke(controller, "loadFavoritesForCurrentLocation");
        });
    }

    private static FileInfo directory(String name, String path) {
        FileInfo info = new FileInfo();
        info.setName(name);
        info.setPath(path);
        info.setDirectory(true);
        return info;
    }

    private static ContextMenuEvent contextMenuEvent() {
        return new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 5, 5, 5, 5, false, null);
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Event fireNow(EventTarget target, Event event) {
        Event[] captured = new Event[1];
        javafx.event.EventHandler filter = dispatched -> captured[0] = (Event) dispatched;
        target.addEventFilter(event.getEventType(), filter);
        try {
            Event.fireEvent(target, event);
        } finally {
            target.removeEventFilter(event.getEventType(), filter);
        }
        return captured[0] != null ? captured[0] : event;
    }

    private static Event fire(EventTarget target, Event event) {
        Event[] captured = new Event[1];
        onFx(() -> captured[0] = fireNow(target, event));
        return captured[0] != null ? captured[0] : event;
    }

    private static void onFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    static class FakeLocalFileService implements LocalFileService {
        @Override
        public String getHomeDirectory() {
            return HOME;
        }

        @Override
        public List<FileInfo> listFiles(String directoryPath) throws IOException {
            return new ArrayList<>();
        }
    }

    static class RecordingSsh extends SSHServiceImpl {
        volatile boolean connected = true;
        final AtomicInteger connectCount = new AtomicInteger();
        final AtomicInteger disconnectCount = new AtomicInteger();
        final List<String> listedPaths = new ArrayList<>();
        final Map<String, List<RemoteFileInfo>> listings = new HashMap<>();

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
            List<RemoteFileInfo> listing = listings.get(remotePath);
            return listing == null ? List.of() : new ArrayList<>(listing);
        }
    }

    static class FakeSshFactory implements SSHServiceFactory {
        private final RecordingSsh next;

        FakeSshFactory(RecordingSsh next) {
            this.next = next;
        }

        @Override
        public SSHService create() {
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
        final List<Integer> removed = new ArrayList<>();

        @Override
        public void addFavorite(String name, String path, String locationId) {
            favorites.add(new FavoriteFolder(favorites.size() + 100, name, path, locationId));
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
        public void savePreferences(Preference preference) {
            if (preference != null) {
                store.put(preference.getCode(), preference.getValue());
            }
        }

        @Override
        public void updatePreferences(Preference preference) {
            if (preference != null) {
                store.put(preference.getCode(), preference.getValue());
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
        public void saveOrUpdatePreferences(Preference preference) {
            if (preference != null) {
                store.put(preference.getCode(), preference.getValue());
            }
        }
    }
}
