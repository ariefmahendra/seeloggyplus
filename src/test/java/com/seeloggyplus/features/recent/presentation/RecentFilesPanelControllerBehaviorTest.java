package com.seeloggyplus.features.recent.presentation;

import com.seeloggyplus.features.recent.application.RecentFileService;
import com.seeloggyplus.features.recent.domain.RecentFile;
import com.seeloggyplus.features.recent.domain.RecentFilesDto;
import com.seeloggyplus.shared.logs.port.LogFileService;
import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.session.TabSessionManager;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.event.Event;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class RecentFilesPanelControllerBehaviorTest {

    private RecentFilesPanelController panel;
    private TreeView<RecentFileTreeCell.RecentNode> tree;
    private Stage stage;
    private final FakeRecentFileService recentService = new FakeRecentFileService();
    private final FakeHost host = new FakeHost();
    private java.util.concurrent.CompletableFuture<Void> initialRefresh;

    @org.junit.jupiter.api.BeforeEach
    void awaitInitialRefresh() {
        try {
            initialRefresh.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Start
    @SuppressWarnings("unchecked")
    void start(Stage stage) throws Exception {
        this.stage = stage;
        recentService.files.addAll(List.of(
                dto(local("file-1", "/logs/app.log", "2026-10-12T10:00:00"), null, RecentFile.MODE_OPEN),
                dto(remote("file-2", "/var/log/remote.log", "srv-a"), "prod", RecentFile.MODE_TAIL)));

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RecentFilesPanel.fxml"));
        Parent root = loader.load();
        panel = loader.getController();
        panel.start(recentService, new NoopLogFileService(), new TabSessionManager(), host);
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();
        initialRefresh = panel.refresh();

        tree = (TreeView<RecentFileTreeCell.RecentNode>) root.lookup("#recentFilesTreeView");
        assertNotNull(tree);
    }

    @Test
    void selectForSessionHighlightsTheMatchingLocalEntryCaseInsensitively() {
        LogSession session = new LogSession("app.log", LogSession.SessionType.LOCAL);
        session.setLogFileRecord(local("file-1", "C:\\Logs\\APP.LOG", null));

        onFx(() -> panel.selectForSession(session));

        assertEquals("file-1", selectedFileId());
    }

    @Test
    void selectForSessionMatchesRemoteEntriesByServerAndPath() {
        LogSession session = new LogSession("remote.log", LogSession.SessionType.REMOTE);
        session.setLogFileRecord(remote("file-2", "/var//log/remote.log", "srv-a"));

        onFx(() -> panel.selectForSession(session));

        assertEquals("file-2", selectedFileId());
    }

    @Test
    void selectForSessionClearsSelectionWhenTheSessionIsNull() {
        onFx(() -> panel.selectForSession(null));

        assertNull(tree.getSelectionModel().getSelectedItem());
    }

    @Test
    void cancellingClearAllKeepsEverything() {
        onFx(() -> ((Button) panel.getRoot().lookup("#clearRecentButton")).fire());

        assertTrue(recentService.deletedAll.isEmpty());
        assertTrue(host.closedAllTabs == false);
        assertEquals(0, host.cleanupTempFilesCalls);
    }

    @Test
    void confirmingClearAllRemovesEveryEntryAndCleansTempFiles() {
        host.confirmResult = Optional.of(ButtonType.OK);

        onFx(() -> ((Button) panel.getRoot().lookup("#clearRecentButton")).fire());

        assertEquals(1, recentService.deletedAll.size());
        assertTrue(host.closedAllTabs);
        assertEquals(1, host.cleanupTempFilesCalls);
    }

    @Test
    void doubleClickOpensTheSelectedRecentFile() {
        TreeItem<RecentFileTreeCell.RecentNode> localNode = fileNodes().get(0);
        onFx(() -> {
            tree.getSelectionModel().select(localNode);
            Event.fireEvent(tree, doubleClickEvent());
        });

        assertEquals(1, host.opened.size());
        assertEquals("file-1", host.opened.get(0).logFile().getId());
    }

    @Test
    void pinButtonRequestsCloseThroughTheHost() {
        onFx(() -> {
            panel.setOnCloseRequest(() -> host.closeRequests++);
            ((Button) panel.getRoot().lookup("#pinLeftPanelButton")).fire();
        });

        assertEquals(1, host.closeRequests);
    }

    @Test
    void refreshLoadsRecentFilesOffTheFxThread() {
        recentService.resetTracking();

        onFx(panel::refresh);

        awaitFx(() -> recentService.findAllCalls > 0);
        assertFalse(recentService.findAllRanOnFxThread,
                "recent files must be loaded off the JavaFX application thread");
    }

    @Test
    void refreshShowsEntriesAddedAfterStart() {
        recentService.files.add(dto(local("file-3", "/logs/third.log", "2026-10-12T11:00:00"), null,
                RecentFile.MODE_OPEN));

        onFx(panel::refresh);

        awaitFx(() -> fileNodes().stream()
                .anyMatch(node -> "file-3".equals(node.getValue().getFile().logFile().getId())));
        assertTrue(fileNodes().stream()
                .anyMatch(node -> "file-3".equals(node.getValue().getFile().logFile().getId())));
    }

    private void awaitFx(java.util.function.BooleanSupplier condition) {
        try {
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> {
                try {
                    return WaitForAsyncUtils.asyncFx(() -> condition.getAsBoolean()).get(2, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return false;
                }
            });
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("condition was not met within 5 seconds", e);
        }
    }

    private List<TreeItem<RecentFileTreeCell.RecentNode>> fileNodes() {
        List<TreeItem<RecentFileTreeCell.RecentNode>> nodes = new ArrayList<>();
        for (TreeItem<RecentFileTreeCell.RecentNode> group : tree.getRoot().getChildren()) {
            nodes.addAll(group.getChildren());
        }
        return nodes;
    }

    private String selectedFileId() {
        TreeItem<RecentFileTreeCell.RecentNode> item = tree.getSelectionModel().getSelectedItem();
        return item == null || item.getValue() == null || item.getValue().getFile() == null
                ? null
                : item.getValue().getFile().logFile().getId();
    }

    private MouseEvent doubleClickEvent() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
                false, false, false, false, true, false, false, true, false, false, null);
    }

    private void onFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get();
            WaitForAsyncUtils.waitForFxEvents();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static RecentFilesDto dto(LogFile file, String serverName, String mode) {
        return new RecentFilesDto(file, null, serverName, mode);
    }

    private static LogFile local(String id, String path, String lastOpened) {
        LogFile file = new LogFile();
        file.setId(id);
        file.setName("app.log");
        file.setFilePath(path);
        file.setRemote(false);
        return file;
    }

    private static LogFile remote(String id, String path, String serverId) {
        LogFile file = new LogFile();
        file.setId(id);
        file.setName("remote.log");
        file.setFilePath(path);
        file.setRemote(true);
        file.setSshServerID(serverId);
        return file;
    }

    private static class FakeRecentFileService implements RecentFileService {
        private final List<RecentFilesDto> files = new ArrayList<>();
        private final List<RecentFile> deletedAll = new ArrayList<>();
        private volatile int findAllCalls;
        private volatile boolean findAllRanOnFxThread;

        void resetTracking() {
            findAllCalls = 0;
            findAllRanOnFxThread = false;
        }

        @Override
        public List<RecentFilesDto> findAll() {
            findAllCalls++;
            if (Platform.isFxApplicationThread()) {
                findAllRanOnFxThread = true;
            }
            return List.copyOf(files);
        }

        @Override
        public void save(LogFile logFile, RecentFile recentFile) {
        }

        @Override
        public void deleteAll() {
            deletedAll.add(new RecentFile());
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

    private static class NoopLogFileService implements LogFileService {
        @Override
        public void insertLogFile(LogFile logFile) {
        }

        @Override
        public LogFile getLogFileById(String id) {
            return null;
        }

        @Override
        public void deleteLogFileById(String id) {
        }

        @Override
        public void updateLogFile(LogFile logFile) {
        }

        @Override
        public LogFile getLogFileByPathAndName(String name, String filePath) {
            return null;
        }

        @Override
        public LogFile getLogFileByPathNameAndServer(String name, String filePath, String sshServerId,
                boolean isRemote) {
            return null;
        }

        @Override
        public void deleteAllLogFiles() {
        }

        @Override
        public void updateParsingConfigIdForLogFiles(String parsingConfigId, String logFileId) {
        }
    }

    private static class FakeHost implements RecentFilesPanelController.Host {
        private final List<RecentFilesDto> opened = new ArrayList<>();
        private int closeRequests;
        private int cleanupTempFilesCalls;
        private boolean closedAllTabs;
        private Optional<ButtonType> confirmResult = Optional.empty();

        @Override
        public void onOpenRecent(RecentFilesDto recent) {
            opened.add(recent);
        }

        @Override
        public void closeSession(LogSession session) {
        }

        @Override
        public void closeAllTabs() {
            closedAllTabs = true;
        }

        @Override
        public void clearSearch() {
        }

        @Override
        public void stopRemoteTailIfMonitoring(String remotePath) {
        }

        @Override
        public void stopRemoteTail() {
        }

        @Override
        public void cleanupTempFiles() {
            cleanupTempFilesCalls++;
        }

        @Override
        public Optional<ButtonType> showConfirmation(Alert alert) {
            return confirmResult;
        }

        @Override
        public String monitoringRemotePath() {
            return null;
        }
    }
}
