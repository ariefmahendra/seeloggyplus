package com.seeloggyplus.app;

import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ui.DialogPresenter;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class ServerCredentialsTest {

    private final FakePresenter presenter = new FakePresenter();

    @Start
    void start(Stage stage) {
    }

    private Optional<String> resolve(SSHServerModel server) {
        try {
            return WaitForAsyncUtils.asyncFx(() -> ServerCredentials.resolveSecret(server, presenter)).get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("the stored secret follows the authentication type")
    void storedSecretFollowsAuthType() {
        SSHServerModel passwordServer = passwordServer("pw");
        assertEquals("pw", ServerCredentials.storedSecret(passwordServer));

        SSHServerModel keyServer = passwordServer(null);
        keyServer.setAuthType(SSHServerModel.AUTH_KEY);
        keyServer.setKeyPassphrase("phrase");
        assertEquals("phrase", ServerCredentials.storedSecret(keyServer));
    }

    @Test
    @DisplayName("a saved password is returned without prompting")
    void savedPasswordSkipsThePrompt() {
        Optional<String> secret = resolve(passwordServer("saved"));

        assertEquals(Optional.of("saved"), secret);
        assertEquals(0, presenter.dialogs.size(), "a saved password must not prompt");
    }

    @Test
    @DisplayName("a key passphrase is returned without prompting")
    void keyPassphraseSkipsThePrompt() {
        SSHServerModel server = passwordServer(null);
        server.setAuthType(SSHServerModel.AUTH_KEY);
        server.setKeyPassphrase("phrase");

        assertEquals(Optional.of("phrase"), resolve(server));
        assertEquals(0, presenter.dialogs.size());
    }

    @Test
    @DisplayName("a missing password prompts and returns the typed value")
    void missingPasswordPromptsForTheTypedValue() {
        presenter.result = Optional.of("typed");

        Optional<String> secret = resolve(passwordServer(null));

        assertEquals(Optional.of("typed"), secret);
        assertEquals(1, presenter.dialogs.size());
    }

    @Test
    @DisplayName("a blank or cancelled prompt counts as missing")
    void blankOrCancelledPromptCountsAsMissing() {
        presenter.result = Optional.of("   ");
        assertTrue(resolve(passwordServer(null)).isEmpty());

        presenter.result = Optional.empty();
        assertTrue(resolve(passwordServer(null)).isEmpty());
    }

    @Test
    @DisplayName("a blank saved password also prompts")
    void blankSavedPasswordPrompts() {
        presenter.result = Optional.of("typed");

        Optional<String> secret = resolve(passwordServer("   "));

        assertEquals(Optional.of("typed"), secret);
        assertEquals(1, presenter.dialogs.size());
    }

    private static SSHServerModel passwordServer(String password) {
        SSHServerModel server = new SSHServerModel("Prod", "host", 22, "user");
        server.setPassword(password);
        return server;
    }

    private static final class FakePresenter implements DialogPresenter {
        private final List<Dialog<?>> dialogs = new ArrayList<>();
        private Optional<String> result = Optional.empty();

        @Override
        public void present(javafx.stage.Stage dialog) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> present(Dialog<T> dialog) {
            dialogs.add(dialog);
            return (Optional<T>) result;
        }
    }
}
