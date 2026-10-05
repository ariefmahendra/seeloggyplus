package com.seeloggyplus.shared.ssh;

import com.seeloggyplus.shared.model.SSHServerModel;

/**
 * Authentication settings for one SSH connection attempt.
 *
 * @param type       password or private-key authentication
 * @param keyPath    private key file (KEY only)
 * @param passphrase optional key passphrase (KEY only)
 */
public record SshAuthConfig(AuthType type, String keyPath, String passphrase) {

    public enum AuthType { PASSWORD, KEY }

    public static SshAuthConfig password() {
        return new SshAuthConfig(AuthType.PASSWORD, null, null);
    }

    public static SshAuthConfig key(String keyPath, String passphrase) {
        return new SshAuthConfig(AuthType.KEY, keyPath, passphrase);
    }

    /** Maps a stored server configuration to its auth settings. */
    public static SshAuthConfig from(SSHServerModel server) {
        if (server != null && server.usesKeyAuth()) {
            return key(server.getKeyPath(), server.getKeyPassphrase());
        }
        return password();
    }
}
