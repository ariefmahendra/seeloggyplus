package com.seeloggyplus.app;

import com.seeloggyplus.shared.model.SSHServerModel;
import com.seeloggyplus.shared.ssh.SSHService;
import com.seeloggyplus.shared.ui.SshConnectFlow;
import javafx.concurrent.Task;

public class ServerConnectTask extends Task<Boolean> {

    private final SSHService sshService;
    private final SSHServerModel server;
    private final String secret;

    public ServerConnectTask(SSHService sshService, SSHServerModel server, String secret) {
        this.sshService = sshService;
        this.server = server;
        this.secret = secret;
    }

    public static String storedSecret(SSHServerModel server) {
        return server.usesKeyAuth() ? server.getKeyPassphrase() : server.getPassword();
    }

    @Override
    protected Boolean call() {
        return connect();
    }

    public boolean connect() {
        if (sshService.isConnected()) {
            return true;
        }
        if (!server.usesKeyAuth() && (secret == null || secret.isBlank())) {
            return false;
        }
        return SshConnectFlow.connect(sshService, server, secret);
    }
}
