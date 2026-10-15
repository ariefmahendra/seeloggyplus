package com.seeloggyplus.app;

import com.seeloggyplus.features.files.presentation.FileManagerHost;
import com.seeloggyplus.features.preview.presentation.LogPreviewDialogController;
import com.seeloggyplus.features.search.presentation.RemoteLogSearchDialogController;
import com.seeloggyplus.features.servers.presentation.ServerManagementDialogController;
import com.seeloggyplus.shared.model.FileInfo;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.settings.PreferenceService;
import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.shared.ssh.SSHServiceFactory;
import com.seeloggyplus.shared.ui.AppIcons;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.ui.DialogPresenter;
import com.seeloggyplus.shared.ui.DialogWindowSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.BiConsumer;

public class FileManagerDialogHost implements FileManagerHost {

    private static final Logger logger = LoggerFactory.getLogger(FileManagerDialogHost.class);

    private final Stage owner;
    private final PreferenceService preferenceService;
    private final SSHServiceFactory sshServiceFactory;
    private final BiConsumer<String, String> errorReporter;
    private final DialogPresenter dialogPresenter;

    public FileManagerDialogHost(Stage owner, PreferenceService preferenceService,
            SSHServiceFactory sshServiceFactory, BiConsumer<String, String> errorReporter,
            DialogPresenter dialogPresenter) {
        this.owner = owner;
        this.preferenceService = preferenceService;
        this.sshServiceFactory = sshServiceFactory;
        this.errorReporter = errorReporter == null ? (title, message) -> { } : errorReporter;
        this.dialogPresenter = dialogPresenter == null ? DialogPresenter.showAndWait() : dialogPresenter;
    }

    @Override
    public FindInFilesResult openFindInFiles(SSHService sshService, SSHServerModel server, String path,
            int maxTailWindow) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RemoteLogSearchDialog.fxml"));
            Parent searchRoot = loader.load();
            RemoteLogSearchDialogController searchController = loader.getController();
            searchController.setContext(sshService, server, path);
            searchController.setMaxTailWindow(maxTailWindow);

            Stage searchDialog = new Stage();
            searchDialog.setTitle("Find in Files");
            AppIcons.apply(searchDialog);
            searchDialog.initModality(Modality.WINDOW_MODAL);
            searchDialog.initOwner(owner);
            searchDialog.setScene(AppTheme.scene(searchRoot));
            dialogPresenter.present(searchDialog);

            FileInfo chosen = searchController.getChosenFile();
            if (chosen == null) {
                return null;
            }
            return new FindInFilesResult(chosen, searchController.getTargetLine(),
                    searchController.getTailWindowLines(), searchController.getTailJumpIndex(),
                    searchController.isTailAction(), searchController.isOpenInstead());
        } catch (IOException e) {
            logger.error("Failed to open Find in Files dialog", e);
            errorReporter.accept("Find in Files", "Could not open dialog: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void openPreview(FileInfo file, SSHService sshService) {
        try {
            Stage restoreStage = ultimateOwner(owner);
            boolean wasMaximized = restoreStage.isMaximized();
            double oldX = restoreStage.getX();
            double oldY = restoreStage.getY();
            double oldWidth = restoreStage.getWidth();
            double oldHeight = restoreStage.getHeight();

            FXMLLoader previewLoader = new FXMLLoader(getClass().getResource("/fxml/LogPreviewDialog.fxml"));
            Parent previewRoot = previewLoader.load();

            LogPreviewDialogController previewController = previewLoader.getController();
            previewController.setPreferenceService(preferenceService);
            previewController.loadFile(file, sshService);

            Stage stage = new Stage();
            AppIcons.apply(stage);
            stage.setTitle("Log Preview");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(owner);
            DialogWindowSupport.restoreWindow(restoreStage, wasMaximized, oldX, oldY, oldWidth, oldHeight,
                    previewRoot, stage, dialogPresenter);
        } catch (IOException e) {
            logger.error("Failed to open log preview dialog", e);
            errorReporter.accept("Preview Error", "Could not open the log preview: " + e.getMessage());
        }
    }

    @Override
    public void openServerManagement() {
        try {
            Stage restoreStage = ultimateOwner(owner);
            boolean wasMaximized = restoreStage.isMaximized();
            double oldX = restoreStage.getX();
            double oldY = restoreStage.getY();
            double oldWidth = restoreStage.getWidth();
            double oldHeight = restoreStage.getHeight();

            FXMLLoader serverLoader = new FXMLLoader(getClass().getResource("/fxml/ServerManagementDialog.fxml"));
            Parent serverRoot = serverLoader.load();
            ServerManagementDialogController serverController = serverLoader.getController();
            serverController.setSshServiceFactory(sshServiceFactory);

            Stage stage = new Stage();
            AppIcons.apply(stage);
            stage.setTitle("Server Management");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(owner);
            DialogWindowSupport.restoreWindow(restoreStage, wasMaximized, oldX, oldY, oldWidth, oldHeight,
                    serverRoot, stage, dialogPresenter);
        } catch (IOException e) {
            logger.error("Failed to open server management", e);
        }
    }

    private static Stage ultimateOwner(Stage stage) {
        Stage current = stage;
        while (current != null && current.getOwner() != null) {
            current = (Stage) current.getOwner();
        }
        return current;
    }
}
