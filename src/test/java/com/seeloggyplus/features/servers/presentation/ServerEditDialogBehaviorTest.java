package com.seeloggyplus.features.servers.presentation;

import com.seeloggyplus.features.servers.application.ServerManagementService;
import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ui.AppTheme;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class ServerEditDialogBehaviorTest {

    private Parent root;
    private Stage stage;
    private ServerEditDialogController controller;
    private final FakeServerService service = new FakeServerService();

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerEditDialog.fxml"));
        root = loader.load();
        controller = loader.getController();
        stage.setScene(AppTheme.scene(root));
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @AfterEach
    void keepStageAlive() {
        if (!stage.isShowing()) {
            onFx(stage::show);
        }
    }

    @Test
    void saveRejectsMissingOrInvalidFieldsWithoutClosing() {
        onFx(() -> {
            textField("hostField").setText(" ");
            textField("portField").setText("70000");
            textField("usernameField").setText("");
            button("saveButton").fire();
        });

        assertTrue(stage.isShowing(), "invalid input must keep the dialog open");
        assertFalse(controller.isSaved());
        assertTrue(label("hostErrorLabel").isVisible());
        assertEquals("Host is required", label("hostErrorLabel").getText());
        assertEquals("Port must be between 1 and 65535", label("portErrorLabel").getText());
        assertEquals("Username is required", label("usernameErrorLabel").getText());
    }

    @Test
    void nonNumericPortIsReportedAsNotANumber() {
        onFx(() -> {
            textField("hostField").setText("host");
            textField("portField").setText("abc");
            textField("usernameField").setText("user");
            button("saveButton").fire();
        });

        assertEquals("Port must be a number", label("portErrorLabel").getText());
        assertFalse(controller.isSaved());
    }

    @Test
    void typingClearsTheFieldError() {
        onFx(() -> {
            textField("hostField").setText("");
            button("saveButton").fire();
        });
        assertTrue(label("hostErrorLabel").isVisible());

        onFx(() -> textField("hostField").setText("fixed.example.com"));

        assertFalse(label("hostErrorLabel").isVisible());
        assertFalse(label("hostErrorLabel").isManaged());
    }

    @Test
    void saveCreatesANewTrimmedServerWithoutPasswordWhenUnchecked() {
        onFx(() -> {
            controller.setServerService(service);
            textField("nameField").setText("  Prod  ");
            textField("hostField").setText("  prod.example.com ");
            textField("portField").setText(" 2222 ");
            textField("usernameField").setText(" deploy ");
            textField("passwordField").setText("secret");
            textField("defaultPathField").setText(" /var/log ");
            checkBox("savePasswordCheckBox").setSelected(false);
            button("saveButton").fire();
        });

        assertTrue(controller.isSaved());
        SSHServerModel saved = controller.getSavedServer();
        assertEquals("Prod", saved.getName());
        assertEquals("prod.example.com", saved.getHost());
        assertEquals(2222, saved.getPort());
        assertEquals("deploy", saved.getUsername());
        assertEquals("/var/log", saved.getDefaultPath());
        assertNull(saved.getPassword());
        assertFalse(saved.usesKeyAuth());
        assertEquals(List.of(saved), service.saved);
        assertFalse(stage.isShowing(), "a successful save closes the dialog");
    }

    @Test
    void saveStoresPasswordWhenCheckboxIsSelected() {
        onFx(() -> {
            controller.setServerService(service);
            textField("hostField").setText("prod.example.com");
            textField("portField").setText("22");
            textField("usernameField").setText("deploy");
            textField("passwordField").setText("secret");
            checkBox("savePasswordCheckBox").setSelected(true);
            button("saveButton").fire();
        });

        SSHServerModel saved = controller.getSavedServer();
        assertEquals("secret", saved.getPassword());
        assertTrue(saved.isSavePassword());
    }

    @Test
    void editingKeepsTheExistingIdentity() {
        SSHServerModel existing = new SSHServerModel("Old", "old.example.com", 22, "deploy");
        existing.setId("server-1");
        existing.setCreatedAt(LocalDateTime.of(2025, 1, 1, 8, 0));

        onFx(() -> {
            controller.setServerService(service);
            controller.setServer(existing);
            textField("nameField").setText("Renamed");
            button("saveButton").fire();
        });

        SSHServerModel saved = controller.getSavedServer();
        assertEquals("server-1", saved.getId());
        assertEquals(LocalDateTime.of(2025, 1, 1, 8, 0), saved.getCreatedAt());
        assertEquals("Renamed", saved.getName());
        assertEquals(List.of(saved), service.saved);
    }

    @Test
    void setServerDefaultsMissingPathAndPassword() {
        SSHServerModel bare = new SSHServerModel("Bare", "bare.example.com", 22, "deploy");
        bare.setPassword(null);
        bare.setDefaultPath(null);

        onFx(() -> controller.setServer(bare));

        assertEquals("/", textField("defaultPathField").getText());
        assertEquals("", passwordField("passwordField").getText());
    }

    @Test
    void keyAuthSaveStoresTheKeyPathAndDropsUncheckedPassphrase() {
        onFx(() -> {
            controller.setServerService(service);
            textField("hostField").setText("prod.example.com");
            textField("portField").setText("22");
            textField("usernameField").setText("deploy");
            choice("authTypeChoice").getSelectionModel().select("Private key");
            textField("keyPathField").setText(" C:\\keys\\id_ed25519 ");
            passwordField("keyPassphraseField").setText("phrase");
            checkBox("savePassphraseCheckBox").setSelected(false);
            button("saveButton").fire();
        });

        SSHServerModel saved = controller.getSavedServer();
        assertTrue(saved.usesKeyAuth());
        assertEquals("C:\\keys\\id_ed25519", saved.getKeyPath());
        assertNull(saved.getKeyPassphrase());
    }

    @Test
    void clonePreparesANamedCopyWithDefaults() {
        SSHServerModel original = new SSHServerModel("   ", "orig.example.com", 0, "deploy");
        original.setId("server-9");

        onFx(() -> {
            controller.setServerService(service);
            controller.setCloneServer(original);
            button("saveButton").fire();
        });

        SSHServerModel saved = controller.getSavedServer();
        assertEquals("orig.example.com (Copy)", saved.getName());
        assertEquals(22, saved.getPort());
        assertFalse("server-9".equals(saved.getId()), "a clone must receive a new identity");
    }

    @Test
    void passwordVisibilityTogglesBetweenFields() throws Exception {
        onFx(() -> textField("passwordField").setText("secret"));

        onFx(() -> button("showPasswordButton").fire());

        assertFalse(passwordField("passwordField").isVisible());
        assertTrue(passwordTextField().isVisible());
        assertEquals("secret", passwordTextField().getText());
        assertEquals("EYE_SLASH", iconName());

        onFx(() -> button("showPasswordButton").fire());

        assertTrue(passwordField("passwordField").isVisible());
        assertFalse(passwordTextField().isVisible());
        assertEquals("EYE", iconName());
    }

    @Test
    void cancelClosesWithoutSaving() {
        onFx(() -> {
            controller.setServerService(service);
            textField("hostField").setText("prod.example.com");
            button("cancelButton").fire();
        });

        assertFalse(controller.isSaved());
        assertTrue(service.saved.isEmpty());
        assertFalse(stage.isShowing());
    }

    private void onFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get();
            WaitForAsyncUtils.waitForFxEvents();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private TextField textField(String id) {
        return (TextField) root.lookup("#" + id);
    }

    private PasswordField passwordField(String id) {
        return (PasswordField) root.lookup("#" + id);
    }

    private Button button(String id) {
        return (Button) root.lookup("#" + id);
    }

    private Label label(String id) {
        return (Label) root.lookup("#" + id);
    }

    private CheckBox checkBox(String id) {
        return (CheckBox) root.lookup("#" + id);
    }

    @SuppressWarnings("unchecked")
    private ChoiceBox<String> choice(String id) {
        return (ChoiceBox<String>) root.lookup("#" + id);
    }

    private TextField passwordTextField() throws Exception {
        Field field = ServerEditDialogController.class.getDeclaredField("passwordTextField");
        field.setAccessible(true);
        return (TextField) field.get(controller);
    }

    private String iconName() {
        return ((FontAwesomeIconView) button("showPasswordButton").getGraphic()).getGlyphName();
    }

    private static class FakeServerService implements ServerManagementService {
        private final List<SSHServerModel> saved = new ArrayList<>();

        @Override
        public void saveServer(SSHServerModel server) {
            saved.add(server);
        }

        @Override
        public List<SSHServerModel> getAllServers() {
            return List.of();
        }

        @Override
        public SSHServerModel getServerById(String id) {
            return null;
        }

        @Override
        public void deleteServer(String id) {
        }

        @Override
        public void reorderServers(List<String> orderedIds) {
        }

        @Override
        public List<String> getGroupNames() {
            return List.of();
        }

        @Override
        public void createGroup(String name) {
        }

        @Override
        public void renameGroup(String oldName, String newName) {
        }

        @Override
        public void deleteGroup(String name) {
        }

        @Override
        public void updateServerLastUsed(String serverId) {
        }
    }
}
