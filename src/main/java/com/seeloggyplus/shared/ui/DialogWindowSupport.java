package com.seeloggyplus.shared.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Dialog;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.stage.Window;
import javafx.stage.Stage;

public final class DialogWindowSupport {

    private DialogWindowSupport() {
    }

    public static void styleDialog(Dialog<?> dialog, Window owner) {
        DialogPane pane = dialog.getDialogPane();
        if (!pane.getStyleClass().contains("message-dialog")) {
            pane.getStyleClass().add("message-dialog");
        }
        pane.setPrefWidth(480);
        pane.setMinWidth(360);
        if (owner != null && dialog.getOwner() == null) {
            dialog.initOwner(owner);
        }
        if (dialog instanceof TextInputDialog || dialog instanceof ChoiceDialog<?>) {
            dialog.setGraphic(null);
        }
        AppTheme.install(pane.getScene());
        pane.sceneProperty().addListener((observable, oldScene, scene) -> AppTheme.install(scene));
        dialog.setOnShowing(event -> {
            if (dialog.getOwner() == null) {
                Window.getWindows().stream().filter(window -> window.isShowing() && window.isFocused())
                        .filter(window -> pane.getScene() == null || window != pane.getScene().getWindow())
                        .findFirst().ifPresent(dialog::initOwner);
            }
            AppTheme.install(pane.getScene());
            addAppIcon(pane);
        });
        dialog.setOnShown(event -> addAppIcon(pane));
    }

    private static void addAppIcon(DialogPane pane) {
        if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage && stage.getIcons().isEmpty()) {
            var resource = DialogWindowSupport.class.getResource("/images/app-icon.png");
            if (resource != null) {
                stage.getIcons().add(new Image(resource.toExternalForm()));
            }
        }
    }

    public static void setMinimumContentSize(Stage dialog, double width, double height) {
        dialog.setResizable(true);
        dialog.setMinWidth(width);
        dialog.setMinHeight(height);
        dialog.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWN, event -> {
            if (dialog.getScene() == null) return;
            dialog.setMinWidth(width + Math.max(0, dialog.getWidth() - dialog.getScene().getWidth()));
            dialog.setMinHeight(height + Math.max(0, dialog.getHeight() - dialog.getScene().getHeight()));
        });
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
