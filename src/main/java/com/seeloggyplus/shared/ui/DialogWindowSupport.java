package com.seeloggyplus.shared.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.stage.Stage;

/** Shared window/dialog restoration helper used after modal dialogs close. */
public final class DialogWindowSupport {

    private DialogWindowSupport() {
    }

    public static void restoreWindow(Stage mainStage, boolean wasMaximized, double oldX, double oldY,
            double oldWidth, double oldHeight, Parent root, Stage dialog) {
        dialog.setScene(AppTheme.scene(root));
        dialog.showAndWait();

        Platform.runLater(() -> {
            if (wasMaximized) {
                mainStage.setMaximized(true);
            } else {
                mainStage.setX(oldX);
                mainStage.setY(oldY);
                mainStage.setWidth(oldWidth);
                mainStage.setHeight(oldHeight);
            }
        });
    }
}
