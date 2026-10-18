package com.seeloggyplus.app;

import com.seeloggyplus.shared.ui.AppTheme;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.util.SshConnectionFeedback;

@ExtendWith(ApplicationExtension.class)
class LabelContrastTest {

    private static final double MIN_TEXT_CONTRAST = 4.5;

    private static final String[] FXMLS = {
            "/fxml/MainView.fxml",
            "/fxml/PreferencesDialog.fxml",
            "/fxml/ServerManagementDialog.fxml",
            "/fxml/ServerEditDialog.fxml",
            "/fxml/UnifiedFileManagerDialog.fxml",
            "/fxml/RemoteLogSearchDialog.fxml",
            "/fxml/LogPreviewDialog.fxml",
            "/fxml/UpdateDialog.fxml"
    };

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @AfterEach
    void resetTheme() {
        onFxThread(() -> AppTheme.setDark(false));
    }

    @Test
    void allLabelsContrastInEveryTheme() {
        onFxThread(() -> {
            for (String fxml : FXMLS) {
                for (var theme : AppTheme.Theme.values()) assertLabelsContrast(fxml, theme);
            }
            AppTheme.setDark(false);
        });
    }

    @Test
    void remotePathErrorLabelsContrastInEveryTheme() {
        onFxThread(() -> {
            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                var alert = MainController.createRemotePathError("C:\\Users\\user\\Temp\\app.log");
                try {
                    alert.show();
                    Parent root = alert.getDialogPane();
                    root.applyCss();
                    root.layout();
                    assertLabelNodesContrast("remote-path-error [" + theme + "]", root, theme == AppTheme.Theme.DARK);
                    alert.getDialogPane().setExpanded(true);
                    root.applyCss();
                    root.layout();
                    assertLabelNodesContrast("remote-path-error expanded [" + theme + "]", root, theme == AppTheme.Theme.DARK);
                } finally {
                    alert.close();
                }
            }
        });
    }

    @Test
    void sshConnectionFeedbackIsReadableAndCompactInEveryTheme() {
        onFxThread(() -> {
            for (AppTheme.Theme theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                var server = new SSHServerModel("Test", "127.0.0.1", 22, "test-user");
                var alert = SshConnectionFeedback.createAlert(
                        "Auth fail for methods 'publickey,gssapi-keyex,gssapi-with-mic,password'", server);
                try {
                    alert.show();
                    var pane = alert.getDialogPane();
                    pane.applyCss();
                    pane.layout();
                    assertTrue(pane.getWidth() <= 600);
                    assertTrue(pane.getHeight() <= 420);
                    assertLabelNodesContrast("ssh-connection-feedback [" + theme + "]", pane, theme == AppTheme.Theme.DARK);
                    pane.setExpanded(true);
                    pane.applyCss();
                    pane.layout();
                    assertLabelNodesContrast("ssh-connection-feedback expanded [" + theme + "]", pane, theme == AppTheme.Theme.DARK);
                } finally {
                    alert.close();
                }
            }
        });
    }

    private void assertLabelsContrast(String fxml, AppTheme.Theme theme) {
        try {
            AppTheme.setTheme(theme);
            boolean dark = theme == AppTheme.Theme.DARK;
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Scene scene = AppTheme.scene(root);
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();

            String mode = theme.name().toLowerCase();
            assertLabelNodesContrast(fxml + " [" + mode + "]", root, dark);
            snapshot(root, fxml, mode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void snapshot(Parent root, String fxml, String theme) throws Exception {
        var image = root.snapshot(null, null);
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        var png = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0, 0, width, height, pixels, 0, width);
        var path = java.nio.file.Path.of("build", "test-snapshots", "flat-" + fxml.substring(fxml.lastIndexOf('/') + 1).replace(".fxml", "") + "-" + theme + ".png");
        java.nio.file.Files.createDirectories(path.getParent());
        javax.imageio.ImageIO.write(png, "png", path.toFile());
    }

    private static void assertLabelNodesContrast(String context, Parent root, boolean dark) {
        for (Node node : root.lookupAll(".label")) {
            if (!(node instanceof Label label) || label.getText() == null || label.getText().isBlank()) {
                continue;
            }
            Color fg = label.getTextFill() instanceof Color c ? c : null;
            if (fg == null) {
                continue;
            }
            Color bg = effectiveBackground(label, dark);
            double ratio = contrast(fg, bg);
            assertTrue(ratio >= MIN_TEXT_CONTRAST,
                    String.format("%s label '%s' contrast too low: %.2f:1 (fg=%s bg=%s, classes=%s)",
                            context, label.getText(), ratio, fg, bg, label.getStyleClass()));
        }
    }

    private static Color effectiveBackground(Node node, boolean dark) {
        return com.seeloggyplus.shared.ui.RenderedColors.background(node.getParent());
    }

    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static void onFxThread(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        WaitForAsyncUtils.waitForFxEvents();
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }
}
