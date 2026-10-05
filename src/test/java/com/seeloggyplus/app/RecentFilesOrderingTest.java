package com.seeloggyplus.app;

import com.seeloggyplus.features.recent.presentation.RecentFilesPanelController;

import com.seeloggyplus.features.recent.domain.RecentFilesDto;
import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.session.TabSessionManager;
import com.seeloggyplus.features.recent.domain.RecentFile;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.logs.LogFileServiceImpl;
import com.seeloggyplus.features.recent.infrastructure.RecentConfigServiceImpl;
import com.seeloggyplus.features.servers.infrastructure.ServerManagementServiceImpl;
import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.features.recent.presentation.RecentFileTreeCell;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class RecentFilesOrderingTest {

    private MainController controller;
    private RecentFilesPanelController recentPanel;
    private TabPane tabs;
    private TreeView<RecentFileTreeCell.RecentNode> tree;
    private Map<Tab, LogSession> sessions;
    private final RecentConfigServiceImpl recentService = new RecentConfigServiceImpl();
    private final LogFileServiceImpl fileService = new LogFileServiceImpl();
    private final ServerManagementServiceImpl serverService = new ServerManagementServiceImpl();
    private final List<LogFile> files = new ArrayList<>();
    private SSHServerModel server;

    @Start
    @SuppressWarnings("unchecked")
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        tabs = (TabPane) field("logTabPane");
        recentPanel = (RecentFilesPanelController) field("recentPanelController");
        tree = (TreeView<RecentFileTreeCell.RecentNode>) featureField("recentFilesTreeView");
        sessions = ((TabSessionManager) field("tabSessionManager")).sessions();
        server = new SSHServerModel("Recent-order-" + UUID.randomUUID(), "127.0.0.1", 22, "test-user");
        serverService.saveServer(server);
        stage.setScene(AppTheme.scene(root));
        stage.show();
    }

    @AfterEach
    void cleanup(FxRobot robot) {
        robot.interact(() -> new ArrayList<>(sessions.values()).forEach(LogSession::close));
        for (LogFile file : files) {
            recentService.deleteByFileId(file.getId());
            fileService.deleteLogFileById(file.getId());
        }
        serverService.deleteServer(server.getId());
    }

    @Test
    void reopeningFromRecentUpdatesDataMovesToGroupStartAndKeepsHighlight(FxRobot robot) {
        LogFile older = remoteFile("older.log", 2);
        LogFile newer = remoteFile("newer.log", 1);
        LocalDateTime previous = lastOpened(older);
        LocalDateTime siblingTime = lastOpened(newer);
        robot.interact(() -> {
            addSession(older, true, null);
            recentPanel.refresh();
            invoke("handleRecentFileSelected", new Class<?>[]{RecentFilesDto.class}, dto(older));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(lastOpened(older).isAfter(previous), "The last-opened timestamp must change in the database");
        assertEquals(siblingTime, lastOpened(newer), "Opening one file must not rewrite its sibling's timestamp");
        assertEquals(older.getId(), savedGroupOrder().get(0));
        assertFirstAndSelected(robot, older);
        assertEquals(1, tabs.getTabs().size(), "An already-open log must reuse its tab");
    }

    @Test
    void openingAnExistingRemoteTabFromFileManagerAlsoUpdatesRecentOrder(FxRobot robot) {
        LogFile older = remoteFile("older.log", 2);
        remoteFile("newer.log", 1);
        LocalDateTime previous = lastOpened(older);
        robot.interact(() -> {
            addSession(older, true, null);
            recentPanel.refresh();
            invoke("openRemoteLogFile", new Class<?>[]{String.class, String.class, SSHService.class, SSHServerModel.class},
                    older.getFilePath(), older.getName(), new NoNetworkSsh(), server);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(lastOpened(older).isAfter(previous));
        assertFirstAndSelected(robot, older);
        assertEquals(RecentFile.MODE_TAIL, recentService.findByFileId(older.getId()).orElseThrow().getMode());
    }

    @Test
    void startingTailAgainRefreshesAnExistingRecentTimestamp(FxRobot robot) {
        LogFile older = remoteFile("older.log", 2);
        remoteFile("newer.log", 1);
        LocalDateTime previous = lastOpened(older);
        robot.interact(() -> invoke("startRemoteTail", new Class<?>[]{String.class, SSHService.class, SSHServerModel.class},
                older.getFilePath(), new NoNetworkSsh(), server));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(lastOpened(older).isAfter(previous));
        assertFirstAndSelected(robot, older);
    }

    @Test
    void reopeningLocalRecentEntryMovesItToStartAndKeepsOpenMode(FxRobot robot) throws Exception {
        Path first = Files.createTempFile("seeloggyplus-recent-old", ".log");
        Path second = Files.createTempFile("seeloggyplus-recent-new", ".log");
        try {
            LogFile older = record("old-local.log", first.toString(), false, 2);
            record("new-local.log", second.toString(), false, 1);
            LocalDateTime previous = lastOpened(older);
            robot.interact(() -> {
                addSession(older, false, first);
                recentPanel.refresh();
                invoke("handleRecentFileSelected", new Class<?>[]{RecentFilesDto.class}, dto(older));
            });
            WaitForAsyncUtils.waitForFxEvents();
            assertTrue(lastOpened(older).isAfter(previous));
            assertFirstAndSelected(robot, older);
            assertEquals(RecentFile.MODE_OPEN, recentService.findByFileId(older.getId()).orElseThrow().getMode());
        } finally {
            Files.deleteIfExists(first);
            Files.deleteIfExists(second);
        }
    }

    @Test
    void ordinaryTabSwitchingDoesNotRewriteOpenHistory(FxRobot robot) {
        LogFile first = remoteFile("first.log", 2);
        LogFile second = remoteFile("second.log", 1);
        LocalDateTime firstTime = lastOpened(first), secondTime = lastOpened(second);
        robot.interact(() -> {
            addSession(first, true, null);
            addSession(second, true, null);
            tabs.getSelectionModel().select(0);
            tabs.getSelectionModel().select(1);
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(firstTime, lastOpened(first));
        assertEquals(secondTime, lastOpened(second));
    }

    private LogFile remoteFile(String name, int daysAgo) {
        return record(name, "/var/log/" + UUID.randomUUID() + "/" + name, true, daysAgo);
    }

    private LogFile record(String name, String path, boolean remote, int daysAgo) {
        LogFile file = new LogFile();
        file.setName(name);
        file.setFilePath(path);
        file.setRemote(remote);
        file.setSshServerID(remote ? server.getId() : null);
        file.setSize("1");
        file.setModified("1");
        fileService.insertLogFile(file);
        files.add(file);
        RecentFile recent = new RecentFile(UUID.randomUUID().toString(), file.getId(), LocalDateTime.now().minusDays(daysAgo),
                remote ? RecentFile.MODE_TAIL : RecentFile.MODE_OPEN);
        recentService.save(file, recent);
        return file;
    }

    private void addSession(LogFile file, boolean tail, Path local) {
        LogSession session = new LogSession(file.getName(), local == null ? LogSession.SessionType.REMOTE : LogSession.SessionType.LOCAL);
        session.setLogFileRecord(file);
        session.setTailModeEnabled(tail);
        if (local == null) {
            session.setRemotePath(file.getFilePath());
            session.setSshServer(server);
            session.setSshService(new NoNetworkSsh());
        } else {
            session.setLocalFile(local.toFile());
        }
        Tab tab = (Tab) invoke("createTabForSession", new Class<?>[]{LogSession.class}, session);
        sessions.put(tab, session);
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
    }

    private RecentFilesDto dto(LogFile file) {
        return recentService.findAll().stream().filter(item -> file.getId().equals(item.logFile().getId())).findFirst().orElseThrow();
    }

    private LocalDateTime lastOpened(LogFile file) {
        return recentService.findByFileId(file.getId()).orElseThrow().getLastOpened();
    }

    private List<String> savedGroupOrder() {
        return recentService.findAll().stream().filter(item -> server.getId().equals(item.logFile().getSshServerID()))
                .map(item -> item.logFile().getId()).toList();
    }

    private void assertFirstAndSelected(FxRobot robot, LogFile file) {
        robot.interact(() -> {
            String source = file.isRemote() ? server.getName() : "Local";
            var group = tree.getRoot().getChildren().stream().filter(node -> source.equals(node.getValue().getServerName())).findFirst().orElseThrow();
            assertEquals(file.getId(), group.getChildren().get(0).getValue().getFile().logFile().getId());
            assertEquals(file.getId(), tree.getSelectionModel().getSelectedItem().getValue().getFile().logFile().getId());
        });
    }

    private Object featureField(String name) throws Exception {
        Field field = RecentFilesPanelController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(recentPanel);
    }

    private Object field(String name) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(controller);
    }

    private Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = MainController.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(controller, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class NoNetworkSsh extends SSHServiceImpl {
        @Override public boolean isConnected() { return true; }
        @Override public void tailFile(String path, int count, Consumer<String> onLine, Consumer<String> onError) { }
    }
}
