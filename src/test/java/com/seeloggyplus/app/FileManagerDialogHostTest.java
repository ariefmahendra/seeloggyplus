package com.seeloggyplus.app;

import com.seeloggyplus.features.files.presentation.FileManagerHost;
import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.shared.model.Preference;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.settings.PreferenceService;
import com.seeloggyplus.shared.ui.DialogPresenter;
import javafx.application.Platform;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(ApplicationExtension.class)
class FileManagerDialogHostTest {

    private Stage stage;
    private final FakePresenter presenter = new FakePresenter();
    private final List<String> reportedErrors = new ArrayList<>();
    private FileManagerDialogHost host;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        stage.show();
        host = new FileManagerDialogHost(stage, new InMemoryPreferenceService(), SSHServiceImpl::new,
                (title, message) -> reportedErrors.add(title + ": " + message), presenter);
    }

    @Test
    void rejectsANullOwner() {
        assertThrows(NullPointerException.class,
                () -> new FileManagerDialogHost(null, new InMemoryPreferenceService(), SSHServiceImpl::new,
                        (title, message) -> { }, presenter));
    }

    @Test
    @DisplayName("the preview host opens a themed modal log preview dialog")
    void openPreviewShowsTheLogPreviewDialog() throws Exception {
        Path temp = Files.createTempFile("seeloggyplus-host-preview", ".log");
        Files.writeString(temp, "line one\n");
        temp.toFile().deleteOnExit();
        FileInfo file = new FileInfo();
        file.setName("preview.log");
        file.setPath(temp.toString());

        SSHServiceImpl ssh = new SSHServiceImpl();
        onFx(() -> host.openPreview(file, ssh));

        Stage dialog = presenter.lastStage();
        assertEquals("Log Preview", dialog.getTitle());
        assertEquals(Modality.APPLICATION_MODAL, dialog.getModality());
        assertSame(stage, dialog.getOwner());
        assertFalse(dialog.getIcons().isEmpty(), "the dialog must get the application icon");
        assertNotNull(dialog.getScene());
        org.junit.jupiter.api.Assertions.assertTrue(dialog.isResizable());
        org.junit.jupiter.api.Assertions.assertTrue(dialog.getMinWidth() >= 560);
        org.junit.jupiter.api.Assertions.assertTrue(dialog.getMinHeight() >= 360);
    }

    @Test
    @DisplayName("the server management host opens the themed modal dialog")
    void openServerManagementShowsTheServerDialog() {
        onFx(host::openServerManagement);

        Stage dialog = presenter.lastStage();
        assertEquals("Server Management", dialog.getTitle());
        assertEquals(Modality.APPLICATION_MODAL, dialog.getModality());
        assertSame(stage, dialog.getOwner());
    }

    @Test
    @DisplayName("Find in Files opens with the search context and no choice when dismissed")
    void openFindInFilesWiresTheSearchContext() {
        SSHServerModel server = new SSHServerModel("Prod", "10.0.0.1", 22, "user");
        server.setId("srv-prod");

        FileManagerHost.FindInFilesResult result = syncFx(
                () -> host.openFindInFiles(new SSHServiceImpl(), server, "/var/log", 1234));

        assertNull(result, "a dismissed search dialog must not choose a file");
        Stage dialog = presenter.lastStage();
        assertEquals("Find in Files", dialog.getTitle());
        assertEquals(Modality.WINDOW_MODAL, dialog.getModality());
        org.junit.jupiter.api.Assertions.assertTrue(dialog.isResizable());
        org.junit.jupiter.api.Assertions.assertTrue(dialog.getMinWidth() >= 800);
        org.junit.jupiter.api.Assertions.assertTrue(dialog.getMinHeight() >= 560);
        TextField rootPathField = (TextField) dialog.getScene().lookup("#rootPathField");
        assertEquals("/var/log", rootPathField.getText());
    }

    private void onFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get();
            WaitForAsyncUtils.waitForFxEvents();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private <T> T syncFx(java.util.concurrent.Callable<T> action) {
        try {
            T result = WaitForAsyncUtils.asyncFx(action).get();
            WaitForAsyncUtils.waitForFxEvents();
            return result;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class FakePresenter implements DialogPresenter {
        private final List<Stage> stages = new ArrayList<>();

        Stage lastStage() {
            return stages.get(stages.size() - 1);
        }

        @Override
        public void present(Stage dialog) {
            stages.add(dialog);
        }

        @Override
        public <T> Optional<T> present(Dialog<T> dialog) {
            return Optional.empty();
        }
    }

    private static final class InMemoryPreferenceService implements PreferenceService {
        @Override
        public void savePreferences(Preference preferences) {
        }

        @Override
        public void updatePreferences(Preference preferences) {
        }

        @Override
        public Optional<String> getPreferencesByCode(String code) {
            return Optional.empty();
        }

        @Override
        public List<Preference> getListPreferences() {
            return List.of();
        }

        @Override
        public void saveOrUpdatePreferences(Preference preferences) {
        }
    }
}
