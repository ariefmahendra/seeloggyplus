package com.seeloggyplus.controller;

import com.seeloggyplus.model.SSHServerModel;
import com.seeloggyplus.util.AppTheme;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Server editor auth UI: choosing private-key auth swaps the password panel for
 * the key panel, and existing key settings are shown when editing a server.
 */
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

        javafx.application.Platform.runLater(() -> choice.getSelectionModel().select("Private key"));
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(passwordBox.isVisible(), "password panel must hide for key auth");
        assertFalse(passwordBox.isManaged());
        assertTrue(keyBox.isVisible(), "key panel must show for key auth");
        assertTrue(keyBox.isManaged());

        javafx.application.Platform.runLater(() -> choice.getSelectionModel().select("Password"));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(passwordBox.isVisible());
        assertFalse(keyBox.isVisible());
    }

    @Test
    @DisplayName("test-connection failure keeps the real reason visible")
    void testFailureMessageKeepsDetail() {
        assertEquals("Failed to connect. Please check your credentials.",
                ServerEditDialogController.formatTestFailure(null));
        assertEquals("Failed to connect. Please check your credentials.",
                ServerEditDialogController.formatTestFailure("   "));
        assertTrue(ServerEditDialogController.formatTestFailure("invalid privatekey")
                .contains("invalid privatekey"));
    }

    @Test
    @DisplayName("action buttons stay visible when the dialog opens (both auth modes)")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void actionButtonsAreVisibleOnOpen() {
        for (int authIndex : new int[]{0, 1}) {
            javafx.application.Platform.runLater(() -> {
                ((ChoiceBox) root.lookup("#authTypeChoice")).getSelectionModel().select(authIndex);
                root.applyCss();
                root.layout();
            });
            WaitForAsyncUtils.waitForFxEvents();

            Button test = (Button) root.lookup("#testButton");
            Button cancel = (Button) root.lookup("#cancelButton");
            Button save = (Button) root.lookup("#saveButton");
            double dialogHeight = root.getBoundsInLocal().getHeight();

            for (Button button : java.util.List.of(test, cancel, save)) {
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
        javafx.application.Platform.runLater(() -> {
            ((javafx.scene.control.ChoiceBox<?>) root.lookup("#authTypeChoice")).getSelectionModel().select(1);
            root.applyCss();
            root.layout();
        });
        WaitForAsyncUtils.waitForFxEvents();

        javafx.scene.control.Button browse = (javafx.scene.control.Button) root.lookup("#browseKeyButton");
        TextField keyPath = (TextField) root.lookup("#keyPathField");
        assertNotNull(browse);
        assertNotNull(keyPath);
        assertTrue(browse.getStyleClass().contains("browse-button"),
                "Browse must use the compact style class");

        // Tolerances keep this stable across platforms/fonts while still catching
        // the old behaviour (button stretched to the row height / oversized padding).
        assertTrue(browse.getHeight() <= keyPath.getHeight() + 4,
                "Browse must stay proportional to the field instead of stretching (browse="
                        + browse.getHeight() + ", field=" + keyPath.getHeight() + ")");
        assertTrue(browse.getWidth() <= 140,
                "Browse must stay proportional to its label, was " + browse.getWidth());
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

        javafx.application.Platform.runLater(() -> controller.setServer(server));
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
