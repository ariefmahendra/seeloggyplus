package com.seeloggyplus.app;

import com.seeloggyplus.features.recent.domain.RecentFilesDto;
import com.seeloggyplus.features.servers.infrastructure.ServerManagementServiceImpl;
import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.model.LogFile;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.progress.ProgressCallback;
import com.seeloggyplus.shared.session.LogSession;
import com.seeloggyplus.shared.session.TabSessionManager;
import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.canvas.CanvasLogViewer;
import com.seeloggyplus.shared.util.FileUtils;
import com.seeloggyplus.shared.util.IntArrayList;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class MainControllerReachableFlowsTest {

    private MainController controller;
    private TabPane logTabPane;
    private TabSessionManager sessionManager;
    private Map<Tab, LogSession> sessionMap;
    private Stage stage;

    static class FakeSshService extends SSHServiceImpl {
        volatile boolean connected = true;
        volatile boolean stopTailingCalled;
        volatile int downloadCount;
        volatile String tailedRemotePath;
        volatile long downloadDelayMillis;
        volatile boolean emitProgress;
        final CountDownLatch progressEmitted = new CountDownLatch(1);
        volatile CountDownLatch releaseProgress;
        Consumer<String> lineConsumer;
        Consumer<String> errorConsumer;

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean connect(String host, int port, String username, String password) {
            return connected;
        }

        @Override
        public void stopTailing() {
            stopTailingCalled = true;
        }

        @Override
        public void tailFile(String remotePath, int lines, Consumer<String> logConsumer, Consumer<String> errorConsumer) {
            this.tailedRemotePath = remotePath;
            this.lineConsumer = logConsumer;
            this.errorConsumer = errorConsumer;
        }

        @Override
        public boolean downloadFileConcurrent(String remotePath, String localPath, int threadCount,
                ProgressCallback progressCallback) {
            downloadCount++;
            try {
                if (downloadDelayMillis > 0) {
                    Thread.sleep(downloadDelayMillis);
                }
                if (emitProgress && progressCallback != null) {
                    progressCallback.onProgress(0.5, 50, 100);
                    progressEmitted.countDown();
                    CountDownLatch release = releaseProgress;
                    if (release != null) {
                        release.await();
                    }
                }
                Files.writeString(Path.of(localPath), "remote line\n");
                return true;
            } catch (IOException e) {
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    @Start
    @SuppressWarnings("unchecked")
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        logTabPane = (TabPane) field("logTabPane");
        sessionManager = (TabSessionManager) field("tabSessionManager");
        sessionMap = sessionManager.sessions();
        this.stage = stage;
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @AfterEach
    void cleanup() {
        Platform.runLater(() -> {
            new ArrayList<>(sessionMap.values()).forEach(LogSession::close);
            sessionMap.clear();
            logTabPane.getTabs().clear();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void stopRemoteTailStopsServiceAndClearsMonitoringState() throws Exception {
        FakeSshService ssh = new FakeSshService();
        setField("activeTailSshService", ssh);
        setField("monitoringRemotePath", "/var/log/app.log");

        fx(() -> invoke("stopRemoteTail", new Class<?>[0]));

        assertTrue(ssh.stopTailingCalled);
        assertNull(field("activeTailSshService"));
        assertNull(field("monitoringRemotePath"));
    }

    @Test
    void cleanupTempFilesDeletesOnlyMatchingFilesInConfiguredDirectory(@TempDir Path dir) throws Exception {
        Path download = Files.createFile(dir.resolve("seeloggyplus-42-remote.log"));
        Path keep = Files.createFile(dir.resolve("keep-remote.log"));
        setField("sshDownloadDirectory", dir.toString());

        fx(() -> invoke("cleanupTempFiles", new Class<?>[0]));

        assertFalse(Files.exists(download));
        assertTrue(Files.exists(keep));
    }

    @Test
    void uiRefreshHelpersHandleGcAndLayoutNormalization() throws Exception {
        setField("isLeftPanelPinned", false);
        setField("isBottomPanelPinned", false);
        setField("tailModeEnabled", true);

        fx(() -> {
            invoke("handleGC", new Class<?>[0]);
            invoke("normalizeLayoutState", new Class<?>[0]);
            invoke("updateTailButtonState", new Class<?>[0]);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(((VBox) field("leftPanel")).isVisible());
        assertFalse(((VBox) field("detailPane")).isVisible());
        String memoryText = ((Label) field("memoryStatusLabel")).getText();
        assertTrue(memoryText.matches("Heap: \\d+ MiB"),
                "The GC refresh must show usage with its heap total (no max): " + memoryText);
        assertTrue(((ToggleButton) field("tailButton")).isDisabled());
        assertFalse((boolean) field("tailModeEnabled"));
    }

    @Test
    void isInTabHeaderAreaDetectsHeaderAncestry() throws Exception {
        fx(() -> {
            VBox header = new VBox();
            header.getStyleClass().add("tab-header-area");
            Label inner = new Label("inner");
            header.getChildren().add(inner);
            TabPane pane = (TabPane) field("logTabPane");

            assertTrue((boolean) invoke("isInTabHeaderArea", new Class<?>[]{Object.class}, (Object) inner));
            assertFalse((boolean) invoke("isInTabHeaderArea", new Class<?>[]{Object.class}, (Object) pane));
            assertFalse((boolean) invoke("isInTabHeaderArea", new Class<?>[]{Object.class}, (Object) new Label("loose")));
            assertFalse((boolean) invoke("isInTabHeaderArea", new Class<?>[]{Object.class}, (Object) null));
        });
    }

    @Test
    void dialogIconHelpersAttachIcons() throws Exception {
        fx(() -> {
            Stage appStage = new Stage();
            Stage dialogStage = new Stage();
            invoke("addAppIcon", new Class<?>[]{Stage.class}, appStage);
            invokeStatic("addDialogIcon", new Class<?>[]{javafx.stage.Window.class}, dialogStage);
            assertFalse(appStage.getIcons().isEmpty());
            assertFalse(dialogStage.getIcons().isEmpty());
        });
    }

    @Test
    void panelTogglesFlipPinsAndPersistPreferences() throws Exception {
        boolean initialLeft = (boolean) field("isLeftPanelPinned");
        fx(() -> invoke("toggleLeftPanel", new Class<?>[0]));
        assertEquals(!initialLeft, field("isLeftPanelPinned"));

        boolean afterInvokeLeft = (boolean) field("isLeftPanelPinned");
        fx(() -> ((ToggleButton) field("toggleLeftPanelButton")).fire());
        assertEquals(!afterInvokeLeft, field("isLeftPanelPinned"));

        boolean initialBottom = (boolean) field("isBottomPanelPinned");
        fx(() -> invoke("handleToggleBottomPanelPin", new Class<?>[0]));
        assertEquals(!initialBottom, field("isBottomPanelPinned"));

        boolean afterInvokeBottom = (boolean) field("isBottomPanelPinned");
        fx(() -> ((ToggleButton) field("toggleBottomPanelButton")).fire());
        assertEquals(!afterInvokeBottom, field("isBottomPanelPinned"));

        boolean beforeMenuLeft = (boolean) field("isLeftPanelPinned");
        fx(() -> ((CheckMenuItem) field("showLeftPanelMenuItem")).fire());
        assertEquals(!beforeMenuLeft, field("isLeftPanelPinned"));

        boolean beforeMenuBottom = (boolean) field("isBottomPanelPinned");
        fx(() -> ((CheckMenuItem) field("showBottomPanelMenuItem")).fire());
        assertEquals(!beforeMenuBottom, field("isBottomPanelPinned"));
    }

    @Test
    void showLoadingShowsOverlayAfterDelay() throws Exception {
        VBox overlay = (VBox) field("loadingOverlay");

        fx(() -> invoke("showLoading", new Class<?>[]{String.class}, "Loading test"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, overlay::isVisible);

        assertTrue(overlay.isManaged());

        fx(() -> invoke("hideLoading", new Class<?>[0]));
        assertFalse(overlay.isVisible());
    }

    @Test
    void lastSessionFactoriesCaptureLocalAndRemoteState() throws Exception {
        File file = File.createTempFile("seeloggyplus-last-session", ".log");
        file.deleteOnExit();
        LogFile logFile = new LogFile();
        logFile.setFilePath("/var/log/remote.log");
        FakeSshService ssh = new FakeSshService();

        Object local = newLastSession("local", file, null, null, null);
        assertEquals("LOCAL", fieldOf(local, "type").toString());
        assertSame(file, fieldOf(local, "localFile"));
        assertNull(fieldOf(local, "remotePath"));

        Object remote = newLastSession("remote", null, "/var/log/remote.log", logFile, ssh);
        assertEquals("REMOTE", fieldOf(remote, "type").toString());
        assertNull(fieldOf(remote, "localFile"));
        assertEquals("/var/log/remote.log", fieldOf(remote, "remotePath"));
        assertSame(logFile, fieldOf(remote, "logFromDb"));
        assertSame(ssh, fieldOf(remote, "sshService"));
    }

    @Test
    void handleReloadReopensLastLocalSession() throws Exception {
        File file = File.createTempFile("seeloggyplus-reload-last-local", ".log");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "line one\nline two\n");
        setField("lastSession", newLastSession("local", file, null, null, null));

        fx(() -> invoke("handleReload", new Class<?>[0]));
        awaitTabs(1);

        LogSession session = sessionMap.get(logTabPane.getTabs().get(0));
        assertNotNull(session);
        assertEquals(file.getCanonicalPath(), session.getLocalFile().getCanonicalPath());
    }

    @Test
    void reloadLastRemoteTailConnectsAndStartsTail() throws Exception {
        SSHServerModel server = new SSHServerModel("reload-last", "127.0.0.1", 22, "user");
        server.setPassword("secret");
        ServerManagementServiceImpl catalog = new ServerManagementServiceImpl();
        catalog.saveServer(server);
        try {
            FakeSshService ssh = new FakeSshService();
            LogFile logFile = new LogFile();
            logFile.setId(UUID.randomUUID().toString());
            logFile.setSshServerID(server.getId());
            setField("lastSession", newLastSession("remote", null, "/var/log/reload-last.log", logFile, ssh));

            fx(() -> invoke("handleReload", new Class<?>[0]));
            awaitTabs(1);

            assertEquals("/var/log/reload-last.log", ssh.tailedRemotePath);
            LogSession session = sessionMap.get(logTabPane.getTabs().get(0));
            assertTrue(session.isTailModeEnabled());
        } finally {
            catalog.deleteServer(server.getId());
        }
    }

    @Test
    void reloadActiveRemoteTailConnectsAndStartsTail() throws Exception {
        SSHServerModel server = new SSHServerModel("reload-active", "127.0.0.1", 22, "user");
        server.setPassword("secret");
        ServerManagementServiceImpl catalog = new ServerManagementServiceImpl();
        catalog.saveServer(server);
        try {
            FakeSshService ssh = new FakeSshService();
            LogFile logFile = new LogFile();
            logFile.setId(UUID.randomUUID().toString());
            logFile.setSshServerID(server.getId());
            setField("currentLogFromDb", logFile);
            setField("monitoringRemotePath", "/var/log/reload-active.log");
            setField("tailModeEnabled", true);
            setField("activeTailSshService", ssh);

            fx(() -> invoke("reloadActiveRemoteTail", new Class<?>[0]));
            awaitTabs(1);

            assertEquals("/var/log/reload-active.log", ssh.tailedRemotePath);
        } finally {
            catalog.deleteServer(server.getId());
        }
    }

    @Test
    void closeSessionDisablesTailAndClearsActiveState() throws Exception {
        File file = openTempFile("seeloggyplus-close-tail");
        LogSession session = openLocalAndSync(file);
        session.setTailModeEnabled(true);

        fx(() -> invoke("closeSession", new Class<?>[]{LogSession.class}, session));

        awaitTabs(0);
        assertTrue(session.isClosed());
        assertFalse(session.isTailModeEnabled());
        assertNull(sessionManager.getActive());
    }

    @Test
    void closeSessionSelectsNextSessionWhenActivePointerIsStale() throws Exception {
        File firstFile = openTempFile("seeloggyplus-close-first");
        File secondFile = openTempFile("seeloggyplus-close-second");
        LogSession first = openLocalAndSync(firstFile);
        LogSession second = openLocalAndSync(secondFile);

        fx(() -> {
            logTabPane.getSelectionModel().select(second.getTab());
            invoke("onActiveSessionChanged", new Class<?>[]{LogSession.class}, first);
        });
        fx(() -> invoke("closeSession", new Class<?>[]{LogSession.class}, first));

        assertEquals(1, logTabPane.getTabs().size());
        assertSame(second, sessionManager.getActive());
        assertTrue(first.isClosed());
    }

    @Test
    void openExistingLocalFileWithTailReusesTabAndEnablesTail() throws Exception {
        File file = openTempFile("seeloggyplus-reuse-tail");
        LogSession session = openLocalAndSync(file);
        assertFalse(session.isTailModeEnabled());

        fx(() -> invoke("openLocalLogFile", new Class<?>[]{File.class, String.class, SSHServerModel.class, String.class,
                boolean.class, boolean.class, int.class}, file, null, null, null, false, true, 0));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, logTabPane.getTabs().size());
        assertSame(session, sessionMap.get(logTabPane.getTabs().get(0)));
        assertTrue(session.isTailModeEnabled());

        fx(() -> {
            invoke("startLocalTail", new Class<?>[]{File.class}, file);
            invoke("stopLocalTail", new Class<?>[0]);
        });
        assertNotNull(session.getTailService());
    }

    @Test
    void openLocalOverloadsStripTemporaryDownloadPrefix() throws Exception {
        File prefixed = File.createTempFile("seeloggyplus-987654-clean-", ".log");
        prefixed.deleteOnExit();
        Files.writeString(prefixed.toPath(), "prefixed line\n");
        File plain = openTempFile("seeloggyplus-plain-overload");

        fx(() -> invoke("openLocalLogFile", new Class<?>[]{File.class, String.class, SSHServerModel.class,
                boolean.class}, prefixed, null, null, false));
        awaitTabs(1);

        String stripped = prefixed.getName().replaceFirst("^seeloggyplus-\\d+-", "");
        assertEquals(stripped, logTabPane.getTabs().get(0).getText());

        fx(() -> invoke("openLocalLogFile", new Class<?>[]{File.class, boolean.class, boolean.class}, plain, false,
                false));
        awaitTabs(2);
    }

    @Test
    void getOrCreateLogFileInsertsThenUpdatesExistingRecord() throws Exception {
        File file = File.createTempFile("seeloggyplus-321-log-", ".log");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "abc");

        LogFile inserted = (LogFile) invoke("getOrCreateLogFile", new Class<?>[]{File.class}, file);
        assertNotNull(inserted);
        assertFalse(inserted.getName().startsWith("seeloggyplus-"));
        assertNotNull(inserted.getId());

        Files.writeString(file.toPath(), "abcdefghi");
        LogFile updated = (LogFile) invoke("getOrCreateLogFile", new Class<?>[]{File.class, String.class,
                SSHServerModel.class, String.class}, file, inserted.getName(), null, null);

        assertNotNull(updated);
        assertEquals(inserted.getId(), updated.getId());
        assertEquals(inserted.getName(), updated.getName());
        assertEquals(FileUtils.formatFileSize(file.length()), updated.getSize());
    }

    @Test
    void onActiveSessionChangedHandlesInvalidRegexAndSelectedLine() throws Exception {
        File file = openTempFile("seeloggyplus-invalid-regex");
        LogSession session = openLocalAndSync(file);
        IntArrayList matches = new IntArrayList();
        matches.add(0);

        fx(() -> {
            session.setSearchQuery("[");
            session.setRegex(true);
            session.setCaseSensitive(false);
            session.setFilteredIndexes(matches);
            session.setSelectedLine(0L);
            session.setSelectedLineContent("selected content");
        });
        fx(() -> invoke("onActiveSessionChanged", new Class<?>[]{LogSession.class}, session));

        SplitPane split = (SplitPane) field("searchSplitPane");
        assertNotNull(((javafx.scene.Node) field("searchResultPanel")).getScene());
        assertEquals("selected content", session.getSelectedLineContent());
    }

    @Test
    void canvasLineClickDisplaysDetailForActiveSession() throws Exception {
        File file = openTempFile("seeloggyplus-line-click");
        LogSession session = openLocalAndSync(file);
        CanvasLogViewer viewer = session.getCanvasLogViewer();
        assertNotNull(viewer);
        Object handler = reflectField(viewer, "onLineClick");
        assertNotNull(handler);
        Method handle = handler.getClass().getMethod("handle", long.class, String.class);

        fx(() -> {
            try {
                handle.invoke(handler, 0L, "clicked content");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertEquals(0L, session.getSelectedLine());
        assertEquals("clicked content", session.getSelectedLineContent());
    }

    @Test
    void navigateMatchesCyclesThroughFilteredIndexes() throws Exception {
        File file = openTempFile("seeloggyplus-navigate");
        Files.writeString(file.toPath(), "line one\nline two\nline three\n");
        openLocalAndSync(file);
        IntArrayList matches = new IntArrayList();
        matches.add(0);
        matches.add(1);
        matches.add(2);
        setField("filteredIndexes", matches);

        setField("currentMatchIndex", 0);
        fx(() -> invoke("navigateNextMatch", new Class<?>[0]));
        assertEquals(1, (int) field("currentMatchIndex"));

        fx(() -> invoke("navigateNextMatch", new Class<?>[0]));
        assertEquals(2, (int) field("currentMatchIndex"));

        setField("currentMatchIndex", 3);
        fx(() -> invoke("navigateNextMatch", new Class<?>[0]));
        assertEquals(1, (int) field("currentMatchIndex"));

        setField("currentMatchIndex", 3);
        fx(() -> invoke("navigatePreviousMatch", new Class<?>[0]));
        assertEquals(2, (int) field("currentMatchIndex"));

        setField("currentMatchIndex", 0);
        fx(() -> invoke("navigatePreviousMatch", new Class<?>[0]));
        assertEquals(3, (int) field("currentMatchIndex"));

        setField("filteredIndexes", new IntArrayList());
        fx(() -> {
            invoke("navigateNextMatch", new Class<?>[0]);
            invoke("navigatePreviousMatch", new Class<?>[0]);
        });
        assertEquals(3, (int) field("currentMatchIndex"));
    }

    @Test
    void tailFlushJumpsToPendingIndexAndResetsIt() throws Exception {
        LogSession session = startRemoteTailSession("/var/log/flush-jump.log");

        fx(() -> {
            session.getTailBuffer().add(new LogEntry(1L, "first"));
            session.getTailBuffer().add(new LogEntry(2L, "second"));
            session.setPendingTailJumpIndex(1);
        });
        fx(() -> invoke("scheduleTailFlush", new Class<?>[]{LogSession.class}, session));

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> session.getLiveTailList().size() == 2);
        assertEquals(-1, session.getPendingTailJumpIndex());

        fx(() -> {
            session.getTailBuffer().add(new LogEntry(3L, "third"));
            session.setPendingTailJumpIndex(50);
        });
        fx(() -> invoke("scheduleTailFlush", new Class<?>[]{LogSession.class}, session));

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> session.getLiveTailList().size() == 3);
        assertEquals(50, session.getPendingTailJumpIndex());
    }

    @Test
    void tailFlushScansMatchesIntoFilteredIndexes() throws Exception {
        LogSession session = startRemoteTailSession("/var/log/flush-search.log");
        setField("currentTailSearchPattern", Pattern.compile("error"));
        setField("currentTailSearchPredicate", (java.util.function.Predicate<String>) line -> line.contains("error"));
        setField("filteredIndexes", new IntArrayList());
        setField("tailSearchScannedUpTo", 0);
        session.setTailSearchScannedUpTo(0);

        fx(() -> {
            session.getTailBuffer().add(new LogEntry(1L, "error one"));
            session.getTailBuffer().add(new LogEntry(2L, "info two"));
        });
        fx(() -> invoke("scheduleTailFlush", new Class<?>[]{LogSession.class}, session));

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> session.getLiveTailList().size() == 2);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> ((IntArrayList) field("filteredIndexes")).size() == 1);
        IntArrayList filtered = (IntArrayList) field("filteredIndexes");
        assertEquals(1, filtered.size());
        assertEquals(0, filtered.get(0));
    }

    @Test
    void searchFieldActionsRouteToSearchAndNavigation() throws Exception {
        File file = openTempFile("seeloggyplus-search-field");
        openLocalAndSync(file);
        TextField search = (TextField) field("searchField");
        setField("isSkippingFilterTrigger", true);
        fx(() -> search.setText("alpha"));
        setField("isSkippingFilterTrigger", false);
        setField("lastAppliedSearchQuery", "alpha");
        setField("lastAppliedRegex", false);
        setField("lastAppliedCaseSensitive", false);
        IntArrayList matches = new IntArrayList();
        matches.add(0);
        matches.add(1);
        setField("filteredIndexes", matches);
        setField("currentMatchIndex", 0);

        fx(() -> search.getOnAction().handle(new ActionEvent()));
        assertEquals(1, (int) field("currentMatchIndex"));

        setField("isSkippingFilterTrigger", true);
        fx(() -> search.setText("beta"));
        setField("isSkippingFilterTrigger", false);
        fx(() -> search.getOnAction().handle(new ActionEvent()));
        assertEquals("beta", field("lastAppliedSearchQuery"));

        fx(() -> {
            search.getOnKeyPressed().handle(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false,
                    false, false));
            search.getOnKeyPressed().handle(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, true, false,
                    false, false));
        });
        assertEquals("", search.getText());
        assertNull(field("filteredIndexes"));
    }

    @Test
    void performSearchEdgeCases() throws Exception {
        File file = openTempFile("seeloggyplus-perform-search");
        openLocalAndSync(file);
        TextField search = (TextField) field("searchField");
        IntArrayList matches = new IntArrayList();
        matches.add(0);
        setField("filteredIndexes", matches);
        setField("isSkippingFilterTrigger", true);
        fx(() -> search.setText(""));
        setField("isSkippingFilterTrigger", false);

        fx(() -> invoke("performSearch", new Class<?>[0]));
        assertNull(field("filteredIndexes"));

        setField("tailModeEnabled", true);
        setField("isSkippingFilterTrigger", true);
        fx(() -> {
            search.setText("[");
            ((ToggleButton) field("regexCheckBox")).setSelected(true);
        });
        setField("isSkippingFilterTrigger", false);
        fx(() -> invoke("performSearch", new Class<?>[0]));
        assertNull(field("currentTailSearchPattern"));

        setField("isSkippingFilterTrigger", true);
        fx(() -> {
            search.setText("zzz-no-match");
            ((ToggleButton) field("regexCheckBox")).setSelected(false);
        });
        setField("isSkippingFilterTrigger", false);
        fx(() -> invoke("performSearch", new Class<?>[0]));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> field("filteredIndexes") instanceof IntArrayList);

        SplitPane split = (SplitPane) field("searchSplitPane");
        fx(() -> {
            var results=(com.seeloggyplus.features.search.presentation.SearchResultPanel) field("searchResultPanel");
            assertEquals(0,results.getItemCount());
            assertNotNull(results.getScene(), "No-match feedback remains visible while Tail can add matches");
        });
    }

    @Test
    void performSearchNonTailTaskPaths() throws Exception {
        File file = openTempFile("seeloggyplus-search-task");
        openLocalAndSync(file);
        TextField search = (TextField) field("searchField");

        setField("isSkippingFilterTrigger", true);
        fx(() -> {
            search.setText("definitely-absent-term");
            ((ToggleButton) field("regexCheckBox")).setSelected(false);
        });
        setField("isSkippingFilterTrigger", false);
        fx(() -> invoke("performSearch", new Class<?>[0]));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> field("filteredIndexes") instanceof IntArrayList);
        assertEquals(0, ((IntArrayList) field("filteredIndexes")).size());
        assertEquals(-1, (int) field("currentMatchIndex"));

        setField("isSkippingFilterTrigger", true);
        fx(() -> {
            search.setText("[");
            ((ToggleButton) field("regexCheckBox")).setSelected(true);
        });
        setField("isSkippingFilterTrigger", false);
        fx(() -> invoke("performSearch", new Class<?>[0]));
        fx(() -> {
            assertNull(field("filteredIndexes"));
            var navigator=(com.seeloggyplus.features.search.presentation.SearchNavigator) field("searchNavigator");
            assertTrue(navigator.isVisible());
            assertTrue(((Label)navigator.getChildren().getFirst()).getText().contains("Invalid"));
        });

        setField("isSkippingFilterTrigger", true);
        fx(() -> {
            search.setText("line");
            ((ToggleButton) field("regexCheckBox")).setSelected(false);
        });
        setField("isSkippingFilterTrigger", false);
        fx(() -> {
            invoke("performSearch", new Class<?>[0]);
            ((java.util.concurrent.atomic.AtomicLong) field("searchGeneration")).incrementAndGet();
        });
        Thread.sleep(300);
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(field("filteredIndexes"), "A stale search must not publish results");

        CountDownLatch started = new CountDownLatch(1);
        Task<Void> blocker = new Task<>() {
            @Override
            protected Void call() throws Exception {
                started.countDown();
                Thread.sleep(5000);
                return null;
            }
        };
        Thread.ofVirtual().start(blocker);
        assertTrue(started.await(2, TimeUnit.SECONDS));
        setField("currentSearchTask", blocker);
        fx(() -> invoke("clearSearch", new Class<?>[0]));
        fx(() -> assertTrue(blocker.isCancelled()));

        setField("isSkippingFilterTrigger", true);
        fx(() -> search.setText("line"));
        setField("isSkippingFilterTrigger", false);
        CountDownLatch startedAgain = new CountDownLatch(1);
        Task<Void> blockerAgain = new Task<>() {
            @Override
            protected Void call() throws Exception {
                startedAgain.countDown();
                Thread.sleep(5000);
                return null;
            }
        };
        Thread.ofVirtual().start(blockerAgain);
        assertTrue(startedAgain.await(2, TimeUnit.SECONDS));
        setField("currentSearchTask", blockerAgain);
        fx(() -> invoke("performSearch", new Class<?>[0]));
        fx(() -> assertTrue(blockerAgain.isCancelled()));
    }

    @Test
    void saveRemoteTailToRecentInsertsThenUpdatesExistingRecord() throws Exception {
        SSHServerModel server = new SSHServerModel("recent-update", "127.0.0.1", 22, "user");
        server.setId("srv-recent-update");
        AtomicReference<LogFile> first = new AtomicReference<>();
        AtomicReference<LogFile> second = new AtomicReference<>();

        fx(() -> {
            first.set((LogFile) invoke("saveRemoteTailToRecent", new Class<?>[]{String.class, SSHServerModel.class},
                    "/var/log/recent-update.log", server));
            second.set((LogFile) invoke("saveRemoteTailToRecent", new Class<?>[]{String.class, SSHServerModel.class},
                    "/var/log/recent-update.log", server));
        });

        assertNotNull(first.get());
        assertNotNull(second.get());
        assertEquals(first.get().getId(), second.get().getId());
        assertTrue(second.get().isRemote());
    }

    @Test
    void sceneAcceleratorsRunTheirHandlers() throws Exception {
        Scene scene = stage.getScene();
        KeyCodeCombination focusSearch = new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN);
        KeyCodeCombination reload = new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN);
        KeyCodeCombination toggleLeft = new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN);
        KeyCodeCombination toggleBottom = new KeyCodeCombination(KeyCode.J, KeyCombination.CONTROL_DOWN);
        KeyCodeCombination nextMatch = new KeyCodeCombination(KeyCode.F3);
        KeyCodeCombination closeTab = new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN);
        KeyCodeCombination copy = new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN);

        fx(() -> {
            assertNotNull(scene.getAccelerators().get(focusSearch));
            scene.getAccelerators().get(focusSearch).run();
            scene.getAccelerators().get(reload).run();
            scene.getAccelerators().get(toggleLeft).run();
            scene.getAccelerators().get(toggleBottom).run();
            scene.getAccelerators().get(nextMatch).run();
            scene.getAccelerators().get(closeTab).run();
            scene.getAccelerators().get(copy).run();
        });
    }

    @Test
    void menuAndToolbarHandlersFireWithoutErrors() throws Exception {
        setField("tailModeEnabled", true);
        fx(() -> ((MenuItem) field("clearLogMenuItem")).fire());
        assertFalse((boolean) field("tailModeEnabled"));

        fx(() -> {
            ((Button) field("searchButton")).fire();
            ((Button) field("clearSearchButton")).fire();
            ((MenuItem) field("refreshMenuItem")).fire();
            ((MenuItem) field("closeAllTabsMenuItem")).fire();
        });

        File file = openTempFile("seeloggyplus-menu-fire");
        openLocalAndSync(file);

        fx(() -> ((MenuItem) field("closeTabMenuItem")).fire());
        awaitTabs(0);
    }

    @Test
    void recentPanelHostCallbacksDelegateToController() throws Exception {
        Object panelController = field("recentPanelController");
        Field hostField = panelController.getClass().getDeclaredField("host");
        hostField.setAccessible(true);
        Object host = hostField.get(panelController);
        assertNotNull(host);
        Class<?> hostType = Class.forName("com.seeloggyplus.features.recent.presentation.RecentFilesPanelController$Host");
        Method clearSearch = hostType.getMethod("clearSearch");
        Method closeSession = hostType.getMethod("closeSession", LogSession.class);
        Method closeAllTabs = hostType.getMethod("closeAllTabs");
        Method onOpenRecent = hostType.getMethod("onOpenRecent", RecentFilesDto.class);
        Method stopRemoteTailIfMonitoring = hostType.getMethod("stopRemoteTailIfMonitoring", String.class);
        Method stopRemoteTail = hostType.getMethod("stopRemoteTail");
        Method cleanupTempFiles = hostType.getMethod("cleanupTempFiles");
        Method monitoringRemotePath = hostType.getMethod("monitoringRemotePath");

        TextField search = (TextField) field("searchField");
        fx(() -> search.setText("stale query"));
        fx(() -> {
            try {
                clearSearch.invoke(host);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals("", search.getText());

        setField("monitoringRemotePath", "/var/log/monitored.log");
        assertEquals("/var/log/monitored.log", monitoringRemotePath.invoke(host));
        fx(() -> {
            try {
                stopRemoteTailIfMonitoring.invoke(host, "/var/log/other.log");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals("/var/log/monitored.log", field("monitoringRemotePath"));
        fx(() -> {
            try {
                stopRemoteTailIfMonitoring.invoke(host, "/var/log/monitored.log");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertNull(field("monitoringRemotePath"));

        File file = openTempFile("seeloggyplus-host-recent");
        Files.writeString(file.toPath(), "host line\n");
        LogFile logFile = new LogFile();
        logFile.setName(file.getName());
        logFile.setFilePath(file.getAbsolutePath());
        logFile.setRemote(false);
        RecentFilesDto dto = new RecentFilesDto(logFile, null, null);

        fx(() -> {
            try {
                onOpenRecent.invoke(host, dto);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        awaitTabs(1);
        LogSession session = sessionMap.get(logTabPane.getTabs().get(0));
        assertNotNull(session);

        fx(() -> {
            try {
                closeSession.invoke(host, session);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        awaitTabs(0);

        fx(() -> {
            try {
                onOpenRecent.invoke(host, dto);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        awaitTabs(1);
        fx(() -> {
            try {
                closeAllTabs.invoke(host);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        awaitTabs(0);

        fx(() -> {
            try {
                stopRemoteTail.invoke(host);
                cleanupTempFiles.invoke(host);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void openRemoteLogFileReusesCacheAndReportsProgress() throws Exception {
        SSHServerModel server = new SSHServerModel("remote-flow", "127.0.0.1", 22, "user");
        server.setId("srv-remote-flow");
        String remotePath = "/var/log/remote-flow.log";
        FakeSshService first = new FakeSshService();
        first.downloadDelayMillis = 800;
        first.emitProgress = true;
        first.releaseProgress = new CountDownLatch(1);

        fx(() -> invoke("openRemoteLogFile", new Class<?>[]{String.class, String.class, SSHService.class,
                SSHServerModel.class}, remotePath, "remote-flow.log", first, server));

        Label loadingLabel = (Label) field("loadingLabel");
        assertTrue(first.progressEmitted.await(10, TimeUnit.SECONDS),
                "the fake download must report progress");
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> loadingLabel.getText() != null && loadingLabel.getText().startsWith("Downloading... 50%"));
        first.releaseProgress.countDown();
        awaitTabs(1);
        assertEquals(1, first.downloadCount);

        LogSession session = sessionMap.get(logTabPane.getTabs().get(0));
        fx(() -> invoke("closeSession", new Class<?>[]{LogSession.class}, session));
        awaitTabs(0);

        FakeSshService second = new FakeSshService();
        fx(() -> invoke("openRemoteLogFile", new Class<?>[]{String.class, String.class, SSHService.class,
                SSHServerModel.class}, remotePath, "remote-flow.log", second, server));
        awaitTabs(1);
        assertEquals(0, second.downloadCount);
    }

    private File openTempFile(String prefix) throws IOException {
        File file = File.createTempFile(prefix, ".log");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "line one\nline two\nline three\n");
        return file;
    }

    private LogSession openLocalAndSync(File file) throws Exception {
        int before = logTabPane.getTabs().size();
        fx(() -> invoke("openLocalLogFile", new Class<?>[]{File.class, boolean.class}, file, false));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> logTabPane.getTabs().size() == before + 1);
        Tab selected = logTabPane.getSelectionModel().getSelectedItem();
        assertNotNull(selected);
        LogSession session = sessionMap.get(selected);
        assertNotNull(session);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> session.getReader() != null && session.getIndex() != null);
        fx(() -> invoke("onActiveSessionChanged", new Class<?>[]{LogSession.class}, session));
        return session;
    }

    private LogSession startRemoteTailSession(String remotePath) throws Exception {
        SSHServerModel server = new SSHServerModel("flush", "127.0.0.1", 22, "user");
        server.setId("srv-flush");
        FakeSshService ssh = new FakeSshService();
        fx(() -> invoke("startRemoteTail", new Class<?>[]{String.class, SSHService.class, SSHServerModel.class},
                remotePath, ssh, server));
        WaitForAsyncUtils.waitForFxEvents();
        Tab selected = logTabPane.getSelectionModel().getSelectedItem();
        assertNotNull(selected);
        LogSession session = sessionMap.get(selected);
        assertNotNull(session);
        assertTrue(session.isTailModeEnabled());
        return session;
    }

    private Object newLastSession(String kind, File file, String remotePath, LogFile logFile, SSHService ssh)
            throws Exception {
        Class<?> type = Class.forName("com.seeloggyplus.app.MainController$LastSession");
        if ("local".equals(kind)) {
            Method factory = type.getDeclaredMethod("local", File.class);
            factory.setAccessible(true);
            return factory.invoke(null, file);
        }
        Method factory = type.getDeclaredMethod("remote", String.class, LogFile.class, SSHService.class);
        factory.setAccessible(true);
        return factory.invoke(null, remotePath, logFile, ssh);
    }

    private static Object fieldOf(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private Object reflectField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void awaitTabs(int expected) throws Exception {
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> logTabPane.getTabs().size() == expected);
    }

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }

    private void fx(FxAction action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        if (failure.get() != null) {
            throw new AssertionError("FX action failed", failure.get());
        }
    }

    private Object invokeStatic(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = MainController.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private Object field(String name) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(controller);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = MainController.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(controller, args);
    }
}
