package com.seeloggyplus.util;

import com.seeloggyplus.util.AppTheme.Theme;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Themed "What's New" dialog shown once after an update. Built in code but fully
 * themed: app icon, theme tokens on every control, an accent update badge and a
 * themed primary "Got it" button. Displays the plain-text release notes embedded
 * at build time.
 */
public final class WhatsNewDialog {

    private static final Logger logger = LoggerFactory.getLogger(WhatsNewDialog.class);

    /** Node ids the caller can look up in tests. */
    public static final String VERSION_LABEL_ID = "whatsNewVersionLabel";
    public static final String NOTES_TEXT_ID = "whatsNewNotes";

    private WhatsNewDialog() {
    }

    public static void show(String version, String notes) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(version, notes));
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("What's New");
        dialog.initModality(Modality.WINDOW_MODAL);
        Window owner = frontmostWindow();
        if (owner != null) {
            dialog.initOwner(owner);
        }

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("whats-new-dialog");
        Theme face = AppTheme.getTheme();
        pane.getStylesheets().setAll(AppTheme.sceneStylesheets(face));
        AppTheme.applyThemeState(pane, face);

        // --- Header: app icon + titles + accent badge -------------------------
        Label title = new Label("What's New");
        title.getStyleClass().add("section-title");

        Label versionLabel = new Label("SeeLoggyPlus " + version);
        versionLabel.getStyleClass().add("about-version");
        versionLabel.setId(VERSION_LABEL_ID);

        Label badge = new Label();
        FontAwesomeIconView badgeIcon = new FontAwesomeIconView(FontAwesomeIcon.ARROW_UP);
        badgeIcon.setSize("15");
        badge.setGraphic(badgeIcon);
        badge.getStyleClass().add("whats-new-badge");
        badge.setPrefSize(34, 34);
        badge.setAlignment(Pos.CENTER);

        HBox header = new HBox(12, loadAppIcon(), new VBox(2, title, versionLabel), badge);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 4, 0));
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);

        // --- Body: plain-text release notes, themed like the rest of the app --
        String plainNotes = com.seeloggyplus.update.ReleaseNotes.toPlainText(notes);
        TextArea notesArea = new TextArea(plainNotes.isBlank()
                ? "Thanks for updating! See the release notes for details."
                : plainNotes);
        notesArea.setId(NOTES_TEXT_ID);
        notesArea.setEditable(false);
        notesArea.setWrapText(true);
        notesArea.setPrefColumnCount(58);
        notesArea.setPrefRowCount(16);
        notesArea.getStyleClass().add("whats-new-notes");

        VBox content = new VBox(10, header, new Separator(), notesArea);
        content.setPadding(new Insets(16));
        pane.setContent(content);

        // --- Button: themed primary action -----------------------------------
        pane.getButtonTypes().setAll(ButtonType.OK);
        Button ok = (Button) pane.lookupButton(ButtonType.OK);
        if (ok != null) {
            ok.setText("Got it");
            ok.getStyleClass().add("btn-primary");
            ok.setDefaultButton(true);
        }

        dialog.setOnShown(e -> addAppIcon(dialog));
        dialog.resizableProperty().set(false);
        dialog.showAndWait();
    }

    /** The main window when present, so the dialog centers over it. */
    private static Window frontmostWindow() {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window instanceof Stage) {
                return window;
            }
        }
        return null;
    }

    /** Adds the application window icon to the dialog's stage once shown. */
    private static void addAppIcon(Dialog<Void> dialog) {
        if (dialog.getDialogPane().getScene() == null
                || !(dialog.getDialogPane().getScene().getWindow() instanceof Stage stage)) {
            return;
        }
        try {
            stage.getIcons().add(loadImage());
        } catch (Exception e) {
            logger.warn("Failed to load the What's New dialog icon", e);
        }
    }

    /** Scales the app icon for the header; an unreadable asset returns an empty box. */
    private static ImageView loadAppIcon() {
        try {
            ImageView view = new ImageView(loadImage());
            view.setFitHeight(48);
            view.setFitWidth(48);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            return view;
        } catch (Exception e) {
            logger.warn("App icon not available for What's New", e);
            return new ImageView();
        }
    }

    private static Image loadImage() {
        return new Image(java.util.Objects.requireNonNull(
                WhatsNewDialog.class.getResourceAsStream("/images/app-icon.png")));
    }
}
