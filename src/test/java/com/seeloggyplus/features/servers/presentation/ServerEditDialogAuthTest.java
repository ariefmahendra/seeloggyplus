package com.seeloggyplus.features.servers.presentation;

import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ui.AppTheme;
import com.seeloggyplus.shared.util.SshConnectionFeedback;
import com.seeloggyplus.features.ssh.infrastructure.SSHServiceImpl;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;
import org.testfx.api.FxRobot;
import javafx.stage.Window;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import javafx.application.Platform;

@ExtendWith(ApplicationExtension.class)
class ServerEditDialogAuthTest {

    private Parent root;
    private ServerEditDialogController controller;

    @Start
    void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerEditDialog.fxml"));
        root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("selecting private key swaps the auth panels")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void authPanelsSwap() {
        ChoiceBox choice = (ChoiceBox) root.lookup("#authTypeChoice");
        VBox passwordBox = (VBox) root.lookup("#passwordAuthBox");
        VBox keyBox = (VBox) root.lookup("#keyAuthBox");
        assertNotNull(choice);
        assertNotNull(passwordBox);
        assertNotNull(keyBox);

        assertEquals(2, choice.getItems().size());
        assertTrue(passwordBox.isVisible(), "password panel is the default");
        assertFalse(keyBox.isVisible());

        Platform.runLater(() -> choice.getSelectionModel().select("Private key"));
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(passwordBox.isVisible(), "password panel must hide for key auth");
        assertFalse(passwordBox.isManaged());
        assertTrue(keyBox.isVisible(), "key panel must show for key auth");
        assertTrue(keyBox.isManaged());

        Platform.runLater(() -> choice.getSelectionModel().select("Password"));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(passwordBox.isVisible());
        assertFalse(keyBox.isVisible());
    }

    @Test
    @DisplayName("test-connection failure keeps the real reason visible")
    void testFailureMessageKeepsDetail() {
        var feedback = SshConnectionFeedback.describe("invalid privatekey", true);
        assertEquals("invalid privatekey", feedback.detail());
        assertTrue(feedback.message().contains("private key file"));
        assertFalse(feedback.message().contains("invalid privatekey"));
    }

    @AfterEach
    void dismissResultDialogs(FxRobot robot) {
        robot.interact(() -> {
            for (Window window : new ArrayList<>(Window.getWindows())) {
                if (window.getScene() != null && window.getScene().getRoot().lookup(".dialog-pane") instanceof DialogPane) {
                    window.hide();
                }
            }
            AppTheme.setTheme(AppTheme.Theme.LIGHT);
        });
    }

    @Test
    void authenticationTestFailureShowsReadableMessageAndExpandableDetails(FxRobot robot) throws Exception {
        String raw = "Auth fail for methods 'publickey,gssapi-keyex,gssapi-with-mic,password'";
        controller.setSshServiceFactory(() -> new SSHServiceImpl() {
            @Override
            public boolean connect(String host, int port, String username, String password) { return false; }
            @Override
            public String getLastConnectError() { return raw; }
        });
        robot.interact(() -> {
            ((TextField) root.lookup("#hostField")).setText("127.0.0.1");
            ((TextField) root.lookup("#usernameField")).setText("test-user");
            ((PasswordField) root.lookup("#passwordField")).setText("test-secret-not-for-display");
            ((Button) root.lookup("#testButton")).fire();
        });
        AtomicReference<DialogPane> result = new AtomicReference<>();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() -> {
            for (Window window : Window.getWindows()) {
                if (window.getScene() != null && window.getScene().getRoot().lookup(".dialog-pane") instanceof DialogPane pane
                        && "Unable to sign in to server".equals(pane.getHeaderText())) {
                    result.set(pane);
                    return true;
                }
            }
            return false;
        }).get());
        robot.interact(() -> {
            DialogPane pane = result.get();
            assertTrue(pane.getContentText().contains("username and password"));
            assertFalse(pane.getContentText().contains("gssapi"));
            TextArea details = assertInstanceOf(TextArea.class, pane.getExpandableContent());
            assertTrue(details.getText().contains(raw));
            assertFalse(details.getText().contains("test-secret-not-for-display"));
            assertFalse(details.isEditable());
            assertFalse(pane.isExpanded());
        });
    }

    @Test
    @DisplayName("action buttons stay visible when the dialog opens (both auth modes)")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void actionButtonsAreVisibleOnOpen() {
        for (int authIndex : new int[]{0, 1}) {
            Platform.runLater(() -> {
                ((ChoiceBox) root.lookup("#authTypeChoice")).getSelectionModel().select(authIndex);
                root.applyCss();
                root.layout();
            });
            WaitForAsyncUtils.waitForFxEvents();

            Button test = (Button) root.lookup("#testButton");
            Button cancel = (Button) root.lookup("#cancelButton");
            Button save = (Button) root.lookup("#saveButton");
            double dialogHeight = root.getBoundsInLocal().getHeight();

            for (Button button : List.of(test, cancel, save)) {
                assertNotNull(button);
                assertTrue(button.isVisible(), button.getText() + " must be visible");
                assertTrue(button.getBoundsInParent().getMaxY() <= dialogHeight + 1,
                        button.getText() + " must stay inside the dialog (y="
                                + button.getBoundsInParent().getMaxY() + ", dialog=" + dialogHeight + ")");
                assertTrue(button.getHeight() > 0, button.getText() + " must have a real size");
            }
        }
    }

    @Test
    @DisplayName("the Browse button stays compact next to the key path field")
    void browseButtonIsCompact() {
        Platform.runLater(() -> {
            ((ChoiceBox<?>) root.lookup("#authTypeChoice")).getSelectionModel().select(1);
            root.applyCss();
            root.layout();
        });
        WaitForAsyncUtils.waitForFxEvents();

        Button browse = (Button) root.lookup("#browseKeyButton");
        TextField keyPath = (TextField) root.lookup("#keyPathField");
        assertNotNull(browse);
        assertNotNull(keyPath);
        assertTrue(browse.getStyleClass().contains("browse-button"),
                "Browse must use the compact style class");

        assertTrue(browse.getHeight() <= keyPath.getHeight() + 4,
                "Browse must stay proportional to the field instead of stretching (browse="
                        + browse.getHeight() + ", field=" + keyPath.getHeight() + ")");
        assertTrue(browse.getWidth() <= 140,
                "Browse must stay proportional to its label, was " + browse.getWidth());
    }

    @Test
    @DisplayName("compact flat layout fits all themes and auth modes")
    void compactFlatLayoutFitsAllThemes() {
        for (AppTheme.Theme theme : AppTheme.Theme.values()) {
            for (int authIndex : new int[]{0, 1}) {
                Platform.runLater(() -> {
                    AppTheme.setTheme(theme);
                    ((ChoiceBox<?>) root.lookup("#authTypeChoice")).getSelectionModel().select(authIndex);
                    root.applyCss();
                    root.layout();
                });
                WaitForAsyncUtils.waitForFxEvents();
                var region = (javafx.scene.layout.Region) root;
                assertTrue(region.getStyleClass().contains("server-edit-dialog"));
                assertTrue(region.getPrefWidth() <= 460);
                assertTrue(region.getPrefHeight() <= 440);
                for (String id : List.of("nameField", "hostField", "portField", "usernameField", "defaultPathField", "testButton", "cancelButton", "saveButton")) {
                    var node = (javafx.scene.layout.Region) root.lookup("#" + id);
                    assertEquals(28, node.getHeight(), 1, id);
                }
                var host = root.lookup("#hostField");
                var port = root.lookup("#portField");
                assertEquals(host.localToScene(0, 0).getY(), port.localToScene(0, 0).getY(), 1);
            }
        }
    }

    @Test
    @DisplayName("editing a key-auth server populates the key fields")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void keyServerIsPopulated() {
        SSHServerModel server = new SSHServerModel();
        server.setName("Prod");
        server.setHost("10.0.0.1");
        server.setPort(22);
        server.setUsername("deployer");
        server.setAuthType(SSHServerModel.AUTH_KEY);
        server.setKeyPath("C:\\keys\\id_ed25519");
        server.setKeyPassphrase("secret-pass");

        Platform.runLater(() -> controller.setServer(server));
        WaitForAsyncUtils.waitForFxEvents();

        ChoiceBox choice = (ChoiceBox) root.lookup("#authTypeChoice");
        TextField keyPath = (TextField) root.lookup("#keyPathField");
        PasswordField passphrase = (PasswordField) root.lookup("#keyPassphraseField");
        CheckBox savePassphrase = (CheckBox) root.lookup("#savePassphraseCheckBox");

        assertEquals("Private key", choice.getSelectionModel().getSelectedItem());
        assertEquals("C:\\keys\\id_ed25519", keyPath.getText());
        assertEquals("secret-pass", passphrase.getText());
        assertTrue(savePassphrase.isSelected());
        assertTrue(((VBox) root.lookup("#keyAuthBox")).isVisible());
    }
}
