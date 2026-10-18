package com.seeloggyplus.shared.ui;

import javafx.css.PseudoClass;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class ThemedAlertsTest {
    private Stage owner;

    @Start
    void start(Stage stage) {
        owner = stage;
        stage.setScene(AppTheme.scene(new VBox()));
        stage.show();
    }

    @AfterEach
    void reset(FxRobot robot) {
        robot.interact(() -> AppTheme.setTheme(AppTheme.Theme.LIGHT));
    }

    @Test
    void messageTypesUseCompactReadableSurfacesInBothThemes(FxRobot robot) {
        robot.interact(() -> {
            for (var theme : AppTheme.Theme.values()) {
                AppTheme.setTheme(theme);
                for (var type : new Alert.AlertType[]{Alert.AlertType.CONFIRMATION, Alert.AlertType.ERROR,
                        Alert.AlertType.WARNING, Alert.AlertType.INFORMATION}) {
                    Alert alert = ThemedAlerts.create(type, owner);
                    alert.setHeaderText("Action required");
                    alert.setContentText("Check the selected server and try again.");
                    try {
                        alert.show();
                        DialogPane pane = alert.getDialogPane();
                        pane.applyCss();
                        pane.layout();
                        ScrollPane message = (ScrollPane) pane.lookup(".message-scroll");
                        assertTrue(message.getViewportBounds().getHeight() >= ((Label) message.getContent()).prefHeight(message.getViewportBounds().getWidth()), "The complete message line must fit in the viewport");
                        assertTrue(pane.getContent().getBoundsInParent().getHeight() >= 32, "Content must include readable text and bottom spacing");
                        Color surface = RenderedColors.background(pane);
                        assertEquals(Color.web(theme == AppTheme.Theme.LIGHT ? "#ffffff" : "#191a1c"), surface);
                        assertEquals(0, pane.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius());
                        var glyph = (Text) pane.lookup(".message-icon-glyph");
                        assertNotNull(glyph);
                        assertEquals(20, glyph.getFont().getSize(), .01);
                        assertTrue(contrast((Color) glyph.getFill(), surface) >= 3, theme + " " + type);
                        for (var node : pane.lookupAll(".label")) {
                            var label = (Label) node;
                            if (label.getText() != null && !label.getText().isBlank()) {
                                assertTrue(contrast((Color) label.getTextFill(), surface) >= 4.5, label.getText());
                            }
                        }
                        for (var buttonType : alert.getButtonTypes()) {
                            var button = (Button) pane.lookupButton(buttonType);
                            for (String state : new String[]{"normal", "hover", "pressed", "focused"}) {
                                button.pseudoClassStateChanged(PseudoClass.getPseudoClass(state), true);
                                pane.applyCss();
                                pane.layout();
                                assertEquals(28, button.getHeight(), .01);
                                assertTrue(contrast((Color) button.getTextFill(), RenderedColors.background(button)) >= 4.5);
                                button.pseudoClassStateChanged(PseudoClass.getPseudoClass(state), false);
                            }
                            button.setDisable(true);
                            pane.applyCss();
                            assertEquals(28, button.getHeight(), .01);
                            assertTrue(button.getOpacity() < 1);
                        }
                        assertFalse(((Stage) pane.getScene().getWindow()).getIcons().isEmpty());
                    } finally {
                        alert.close();
                    }
                }
            }
        });
    }

    @Test
    void shownDialogAndExpandableDetailsFollowThemeChanges(FxRobot robot) {
        robot.interact(() -> {
            Alert alert = ThemedAlerts.create(Alert.AlertType.ERROR, owner);
            alert.setHeaderText("Unable to connect");
            alert.setContentText("Check your network settings.");
            TextArea details = new TextArea("Connection refused\nServer: example.org");
            details.setEditable(false);
            details.setPrefRowCount(4);
            alert.getDialogPane().setExpandableContent(details);
            try {
                alert.show();
                alert.getDialogPane().setExpanded(true);
                for (var theme : new AppTheme.Theme[]{AppTheme.Theme.DARK, AppTheme.Theme.LIGHT}) {
                    AppTheme.setTheme(theme);
                    var pane = alert.getDialogPane();
                    pane.applyCss();
                    pane.layout();
                    assertEquals(Color.web(theme == AppTheme.Theme.DARK ? "#191a1c" : "#ffffff"), RenderedColors.background(pane));
                    assertTrue(contrast((Color) ((Text) details.lookup(".text")).getFill(),
                            RenderedColors.background((Region) details.lookup(".content"))) >= 4.5);
                    assertNull(((Hyperlink) pane.lookup(".details-button")).getGraphic(), "Details toggle must avoid the unthemed bitmap icon");
                    assertFalse(details.isEditable());
                    assertEquals("Connection refused\nServer: example.org", details.getText());
                }
            } finally {
                alert.close();
            }
        });
    }

    @Test
    void longMessagesScrollAndKeepButtonsVisible(FxRobot robot) {
        robot.interact(() -> {
            Alert alert = ThemedAlerts.create(Alert.AlertType.WARNING, owner);
            alert.setHeaderText("Unable to read selected files");
            String message = "Long server location /var/log/production/service and detailed explanation.\n".repeat(60);
            alert.setContentText(message);
            try {
                alert.show();
                var pane = alert.getDialogPane();
                pane.applyCss();
                pane.layout();
                ScrollPane scroll = (ScrollPane) pane.lookup(".message-scroll");
                assertTrue(scroll.getHeight() <= 320, "height=" + scroll.getHeight() + " pref=" + scroll.prefHeight(-1) + " min=" + scroll.minHeight(-1) + " max=" + scroll.maxHeight(-1));
                assertEquals(message, ((Label) scroll.getContent()).getText());
                assertTrue(((Label) scroll.getContent()).isWrapText());
                assertTrue(scroll.getContent().getBoundsInParent().getHeight() > scroll.getViewportBounds().getHeight());
                var button = pane.lookupButton(ButtonType.OK);
                assertTrue(button.localToScene(button.getBoundsInLocal()).getMaxY() <= pane.getScene().getHeight(), "button=" + button.localToScene(button.getBoundsInLocal()) + " scene=" + pane.getScene().getHeight() + " pane=" + pane.getHeight() + " pref=" + pane.prefHeight(-1));
            } finally {
                alert.close();
            }
        });
    }

    @Test
    void customConfirmationButtonsKeepTheirResultsAndOwners(FxRobot robot) {
        robot.interact(() -> {
            Alert alert = ThemedAlerts.create(Alert.AlertType.CONFIRMATION, owner);
            ButtonType trust = new ButtonType("Trust & Save", ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().setAll(trust, ButtonType.CANCEL);
            try {
                alert.show();
                assertSame(owner, alert.getOwner());
                ((Button) alert.getDialogPane().lookupButton(trust)).fire();
                assertSame(trust, alert.getResult());
                alert.show();
                ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
                assertSame(ButtonType.CANCEL, alert.getResult());
            } finally {
                alert.close();
            }
        });
    }

    @Test
    void startupErrorCanBeShownWithoutAnOwner(FxRobot robot) {
        robot.interact(() -> {
            owner.hide();
            AppTheme.setTheme(AppTheme.Theme.DARK);
            Alert alert = ThemedAlerts.create(Alert.AlertType.ERROR);
            alert.setContentText("Failed to open the workspace.");
            try {
                alert.show();
                alert.getDialogPane().applyCss();
                assertNull(alert.getOwner());
                assertEquals(Color.web("#191a1c"), RenderedColors.background(alert.getDialogPane()));
            } finally {
                alert.close();
            }
        });
    }

    private static double contrast(Color a, Color b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }

    private static double luminance(Color c) {
        return .2126 * linear(c.getRed()) + .7152 * linear(c.getGreen()) + .0722 * linear(c.getBlue());
    }

    private static double linear(double value) {
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
