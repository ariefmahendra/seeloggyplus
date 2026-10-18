package com.seeloggyplus.app;

import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.servers.ServerCatalog;
import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.shared.ssh.SSHServiceFactory;
import com.seeloggyplus.shared.ui.DialogPresenter;
import com.seeloggyplus.shared.ui.SshConnectFlow;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RemoteConnectTask extends Task<SSHService> {

    private static final Logger logger = LoggerFactory.getLogger(RemoteConnectTask.class);

    private final String sshServerId;
    private final ServerCatalog serverCatalog;
    private final SSHServiceFactory sshServiceFactory;
    private final DialogPresenter dialogPresenter;

    public RemoteConnectTask(String sshServerId, ServerCatalog serverCatalog, SSHServiceFactory sshServiceFactory,
            DialogPresenter dialogPresenter) {
        this.sshServerId = sshServerId;
        this.serverCatalog = serverCatalog;
        this.sshServiceFactory = sshServiceFactory;
        this.dialogPresenter = dialogPresenter;
    }

    @Override
    protected SSHService call() throws Exception {
        SSHServerModel server = resolveServer();
        Optional<String> secret = awaitSecret(server);
        if (!server.usesKeyAuth() && secret.isEmpty()) {
            logger.info("User cancelled password prompt for remote recent file.");
            updateMessage("SSH connection cancelled.");
            cancel();
            return null;
        }
        updateMessage("Connecting to " + server.getHost() + "...");
        return connect(server, secret.orElse(null));
    }

    SSHServerModel resolveServer() throws IOException {
        if (sshServerId == null || sshServerId.isBlank()) {
            throw new IOException("This remote file does not have SSH server information.");
        }
        SSHServerModel server = serverCatalog.getServerById(sshServerId);
        if (server == null) {
            throw new IOException("SSH server configuration with ID " + sshServerId + " was not found.");
        }
        return server;
    }

    SSHService connect(SSHServerModel server, String secret) throws IOException {
        SSHService sshService = sshServiceFactory.create();
        if (!SshConnectFlow.connect(sshService, server, secret)) {
            throw new IOException("Could not connect to " + server.getHost());
        }
        serverCatalog.updateServerLastUsed(server.getId());
        return sshService;
    }

    private Optional<String> awaitSecret(SSHServerModel server) throws Exception {
        CompletableFuture<Optional<String>> secretFuture = new CompletableFuture<>();
        Platform.runLater(() -> secretFuture.complete(ServerCredentials.resolveSecret(server, dialogPresenter)));
        return secretFuture.get();
    }
}
