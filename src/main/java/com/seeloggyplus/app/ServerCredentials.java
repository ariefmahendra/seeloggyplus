package com.seeloggyplus.app;

import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ui.DialogPresenter;
import com.seeloggyplus.shared.util.PasswordPromptDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class ServerCredentials {

    private static final Logger logger = LoggerFactory.getLogger(ServerCredentials.class);

    private ServerCredentials() {
    }

    public static String storedSecret(SSHServerModel server) {
        return server.usesKeyAuth() ? server.getKeyPassphrase() : server.getPassword();
    }

    public static Optional<String> resolveSecret(SSHServerModel server, DialogPresenter dialogPresenter) {
        String secret = storedSecret(server);
        if (server.usesKeyAuth()) {
            return Optional.ofNullable(secret);
        }
        if (secret != null && !secret.isBlank()) {
            return Optional.of(secret);
        }
        logger.info("Password for server {} is not saved, prompting user.", server.getName());
        DialogPresenter presenter = dialogPresenter == null ? DialogPresenter.showAndWait() : dialogPresenter;
        PasswordPromptDialog prompt = new PasswordPromptDialog(server.getHost(), server.getUsername());
        return presenter.present(prompt).filter(value -> value != null && !value.isBlank());
    }
}
