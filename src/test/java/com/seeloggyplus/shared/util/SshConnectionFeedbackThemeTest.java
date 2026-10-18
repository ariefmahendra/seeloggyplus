package com.seeloggyplus.shared.util;

import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ui.AppTheme;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class SshConnectionFeedbackThemeTest {
    private Stage owner;
    @Start void start(Stage stage) {
        owner = stage;
        stage.setScene(AppTheme.scene(new javafx.scene.layout.VBox()));
        stage.show();
    }

    @Test void lightDialogIsBalancedAndReadable() throws Exception { verifyTheme(AppTheme.Theme.LIGHT); }
    @Test void darkDialogIsBalancedAndReadable() throws Exception { verifyTheme(AppTheme.Theme.DARK); }

    private void verifyTheme(AppTheme.Theme theme) throws Exception {
        WaitForAsyncUtils.asyncFx(() -> {
            AppTheme.setTheme(theme);
            var server = new SSHServerModel("Production", "prod.example.com", 22, "deploy");
            Alert alert = SshConnectionFeedback.createAlert("java.net.ConnectException: Connection refused", server);
            alert.initOwner(owner);
            try {
                alert.show();
                DialogPane pane = alert.getDialogPane();
                pane.setExpanded(true);
                pane.applyCss();
                pane.layout();
                ((Stage) pane.getScene().getWindow()).sizeToScene();
                pane.applyCss(); pane.layout();
                snapshot(pane, theme);
                assertTrue(pane.getStyleClass().contains("message-dialog"), "SSH errors must share the message dialog layout");
                Color surface = background(pane);
                Region header = (Region) pane.lookup(".header-panel");
                assertTrue(contrast(surface, background(header)) <= 1.15, "header must blend with the dialog");
                for (var node : pane.lookupAll(".label")) {
                    Label label = (Label) node;
                    if (label.getText() == null || label.getText().isBlank()) continue;
                    assertTrue(contrast((Color) label.getTextFill(), surface) >= 4.5, theme + " " + label.getText());
                }
                assertNotNull(pane.getGraphic());
                var glyph = (javafx.scene.text.Text) pane.lookup(".message-icon-glyph");
                assertTrue(contrast((Color) glyph.getFill(), background((Region) pane.getGraphic())) >= 3);
                assertTrue(pane.getGraphic().getBoundsInParent().getWidth() <= 40, "error icon must be compact");
                assertTrue(pane.getGraphic().getBoundsInParent().getWidth() >= 20);
                TextArea details = (TextArea) pane.getExpandableContent();
                Region content = (Region) details.lookup(".content");
                assertTrue(contrast((Color) ((javafx.scene.text.Text) details.lookup(".text")).getFill(), background(content)) >= 4.5);
                assertTrue(contrast(background(details), background(content)) < 1.5, "details frame must follow the theme");
                assertTrue(details.getHeight() >= 90);
                Button close = (Button) pane.lookupButton(ButtonType.OK);
                double width = close.getWidth(), height = close.getHeight();
                for (String state : new String[] { "hover", "pressed", "focused" }) {
                    close.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass(state), true);
                    pane.applyCss(); pane.layout();
                    assertTrue(contrast((Color) close.getTextFill(), background(close)) >= 4.5, theme + state);
                    Color feedback=background(close);
                    double maximum=Math.max(feedback.getRed(),Math.max(feedback.getGreen(),feedback.getBlue()));
                    double minimum=Math.min(feedback.getRed(),Math.min(feedback.getGreen(),feedback.getBlue()));
                    assertTrue(maximum-minimum<.1,"close button should use a neutral surface");
                    if(!state.equals("pressed")) assertTrue(contrast(surface,feedback)<1.5,"hover and focus should remain subtle");
                    else assertNotEquals(surface,feedback,"pressed state should remain visible");
                    assertTrue(close.getWidth() >= 80);
                    assertEquals(28, close.getHeight(), 0.01);
                    assertEquals(width, close.getWidth(), 0.01);
                    assertEquals(height, close.getHeight(), 0.01);
                    close.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass(state), false);
                }
            } finally {
                alert.close();
                AppTheme.setTheme(AppTheme.Theme.LIGHT);
            }
        }).get();
    }

    private static Color background(Region region) {
        assertNotNull(region.getBackground());
        var fill = region.getBackground().getFills().getFirst().getFill();
        assertInstanceOf(Color.class, fill, "dialog surfaces must resolve to theme colours");
        return com.seeloggyplus.shared.ui.RenderedColors.background(region);
    }
    private static void snapshot(DialogPane pane, AppTheme.Theme theme) {
        try {
            var image = pane.snapshot(null, null);
            int width = (int) image.getWidth(), height = (int) image.getHeight();
            int[] pixels = new int[width * height];
            image.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
            var png = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            png.setRGB(0, 0, width, height, pixels, 0, width);
            Path target = Path.of("build", "test-snapshots", "ssh-error-" + theme.name().toLowerCase() + ".png");
            Files.createDirectories(target.getParent());
            javax.imageio.ImageIO.write(png, "png", target.toFile());
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private static double contrast(Color a, Color b) { double x = luminance(a), y = luminance(b); return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05); }
    private static double luminance(Color c) { return 0.2126*linear(c.getRed())+0.7152*linear(c.getGreen())+0.0722*linear(c.getBlue()); }
    private static double linear(double v) { return v <= 0.04045 ? v/12.92 : Math.pow((v+0.055)/1.055,2.4); }
}
