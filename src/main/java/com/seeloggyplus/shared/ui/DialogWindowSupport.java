package com.seeloggyplus.shared.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.stage.Stage;

public final class DialogWindowSupport {

    private DialogWindowSupport() {
    }

    public static void restoreWindow(Stage mainStage, boolean wasMaximized, double oldX, double oldY,
            double oldWidth, double oldHeight, Parent root, Stage dialog) {
        restoreWindow(mainStage, wasMaximized, oldX, oldY, oldWidth, oldHeight, root, dialog,
                DialogPresenter.showAndWait());
    }

    public static void restoreWindow(Stage mainStage, boolean wasMaximized, double oldX, double oldY,
            double oldWidth, double oldHeight, Parent root, Stage dialog, DialogPresenter presenter) {
        dialog.setScene(AppTheme.scene(root));
        presenter.present(dialog);

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
