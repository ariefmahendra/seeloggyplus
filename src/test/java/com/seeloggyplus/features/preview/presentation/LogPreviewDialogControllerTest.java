package com.seeloggyplus.features.preview.presentation;

import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.settings.PreferenceService;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class LogPreviewDialogControllerTest {

    @TempDir
    Path tempDir;

    private LogPreviewDialogController controller;
    private Parent root;
    private Stage stage;

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LogPreviewDialog.fxml"));
        root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();
    }

    @Test
    void localFileIsPreviewedWithDetectedLineNumbers() throws Exception {
        Path log = writeLog("app.log", "alpha", "beta", "gamma");

        onFx(() -> controller.loadFile(localFile(log), null));
        awaitItems(3);

        ListView<LogPreviewDialogController.LogLine> list = listView();
        assertEquals(1, list.getItems().get(0).getLineNumber());
        assertEquals("alpha", list.getItems().get(0).getContent());
        assertEquals(3, list.getItems().get(2).getLineNumber());
        assertEquals("gamma", list.getItems().get(2).getContent());
        assertTrue(label("fileNameLabel").getText().contains(log.getFileName().toString()));
        assertTrue(label("fileNameLabel").getText().contains("first 500 lines"));
        assertFalse(progressIndicator().isVisible());
    }

    @Test
    void emptyFileStopsLoadingAndShowsAnEmptyState() throws Exception {
        Path log = writeLog("empty.log");
        onFx(() -> controller.loadFile(localFile(log), null));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !progressIndicator().isVisible());
        onFx(() -> {
            assertTrue(listView().getItems().isEmpty());
            assertEquals("No lines to preview", label("previewStateLabel").getText());
            assertFalse(progressIndicator().isManaged());
        });
    }

    @Test
    void previewHonoursTheConfiguredLineLimit() throws Exception {
        Map<String, String> prefs = new HashMap<>();
        prefs.put("lp_line_limit", "2");
        onFx(() -> controller.setPreferenceService(new MapPreferenceService(prefs)));

        Path log = writeLog("limited.log", "one", "two", "three", "four");
        onFx(() -> controller.loadFile(localFile(log), null));
        awaitItems(2);

        assertTrue(label("fileNameLabel").getText().contains("first 2 lines"));
        assertEquals(List.of("one", "two"), listView().getItems().stream()
                .map(LogPreviewDialogController.LogLine::getContent).toList());
    }

    @Test
    void invalidFontPreferencesFallBackWithoutBreakingTheDialog() {
        Map<String, String> prefs = new HashMap<>();
        prefs.put("lp_line_limit", "not-a-number");
        prefs.put("app_font_size", "huge");

        onFx(() -> controller.setPreferenceService(new MapPreferenceService(prefs)));

        String style = listView().getStyle();
        assertTrue(style.contains("Consolas"));
        assertTrue(style.contains("12px"));
    }

    @Test
    void remotePreviewWithoutConnectionShowsAnErrorEntry() throws Exception {
        FileInfo remote = new FileInfo();
        remote.setName("remote.log");
        remote.setPath("/var/log/remote.log");
        remote.setDirectory(false);
        remote.setSourceType(FileInfo.SourceType.REMOTE);

        onFx(() -> controller.loadFile(remote, null));
        awaitItems(2);

        ListView<LogPreviewDialogController.LogLine> list = listView();
        assertEquals("ERROR: Could not load file.", list.getItems().get(0).getContent());
        assertTrue(list.getItems().get(1).getContent().contains("SSH service is not connected"));
    }

    @Test
    void copySelectionWritesTabSeparatedLineNumbers() throws Exception {
        Path log = writeLog("copy.log", "alpha", "beta", "gamma");
        onFx(() -> controller.loadFile(localFile(log), null));
        awaitItems(3);

        onFx(() -> {
            ListView<LogPreviewDialogController.LogLine> list = listView();
            list.getSelectionModel().selectIndices(0, 2);
            Event.fireEvent(list, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.C, false, true, false, false));
        });

        assertEquals("1\talpha\n3\tgamma\n", clipboardText());
    }

    @Test
    void closeButtonClosesTheDialog() {
        onFx(() -> ((Button) root.lookup("#closeButton")).fire());

        assertFalse(stage.isShowing());
        onFx(stage::show);
    }

    private Path writeLog(String name, String... lines) throws Exception {
        Path file = tempDir.resolve(name);
        Files.write(file, List.of(lines));
        return file;
    }

    private static FileInfo localFile(Path path) {
        FileInfo info = new FileInfo();
        info.setName(path.getFileName().toString());
        info.setPath(path.toString());
        info.setDirectory(false);
        info.setSourceType(FileInfo.SourceType.LOCAL);
        return info;
    }

    private void onFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get();
            WaitForAsyncUtils.waitForFxEvents();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private String clipboardText() {
        try {
            return WaitForAsyncUtils.asyncFx(() -> Clipboard.getSystemClipboard().getString()).get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void awaitItems(int expectedCount) throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> listView().getItems().size() == expectedCount);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @SuppressWarnings("unchecked")
    private ListView<LogPreviewDialogController.LogLine> listView() {
        return (ListView<LogPreviewDialogController.LogLine>) root.lookup("#logListView");
    }

    private Label label(String id) {
        return (Label) root.lookup("#" + id);
    }

    private ProgressIndicator progressIndicator() {
        return (ProgressIndicator) root.lookup("#progressIndicator");
    }

    private static class MapPreferenceService implements PreferenceService {
        private final Map<String, String> store;

        MapPreferenceService(Map<String, String> store) {
            this.store = store;
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
}
