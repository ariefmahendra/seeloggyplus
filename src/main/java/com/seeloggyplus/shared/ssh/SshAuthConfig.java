package com.seeloggyplus.shared.ssh;

import com.seeloggyplus.shared.model.SSHServerModel;

public record SshAuthConfig(AuthType type, String keyPath, String passphrase) {

    public enum AuthType { PASSWORD, KEY }

    public static SshAuthConfig password() {
        return new SshAuthConfig(AuthType.PASSWORD, null, null);
    }

    public static SshAuthConfig key(String keyPath, String passphrase) {
        return new SshAuthConfig(AuthType.KEY, keyPath, passphrase);
    }

    public static SshAuthConfig from(SSHServerModel server) {
        if (server != null && server.usesKeyAuth()) {
            return key(server.getKeyPath(), server.getKeyPassphrase());
        }
        return password();
    }
}
