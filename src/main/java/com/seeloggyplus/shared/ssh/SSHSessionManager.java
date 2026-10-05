package com.seeloggyplus.shared.ssh;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.seeloggyplus.shared.ssh.SshAuthConfig;

/**
 * Service interface for managing SSH sessions.
 * <p>
 * Handles the creation, caching, lifecycle management, and cleanup of
 * SSH connections to ensure efficient resource usage.
 */
public interface SSHSessionManager {

    /**
     * Retrieves an existing valid session or creates a new one.
     *
     * @param host      Remote host address.
     * @param port      SSH port.
     * @param username  SSH username.
     * @param password  SSH password.
     * @param ttlMillis Time-to-live for the session in milliseconds.
     * @return An active JSch Session.
     * @throws JSchException If connection fails.
     */
    Session getSession(String host, int port, String username, String password, long ttlMillis) throws JSchException;

    /**
     * Retrieves an existing valid session or creates a new one using the given
     * authentication settings (password or private key).
     *
     * @param host       Remote host address.
     * @param port       SSH port.
     * @param username   SSH username.
     * @param secret     password, or the key passphrase for key auth.
     * @param ttlMillis  Time-to-live for the session in milliseconds.
     * @param authConfig authentication method and key settings.
     * @return An active JSch Session.
     * @throws JSchException                 If connection fails.
     * @throws com.seeloggyplus.shared.ssh.HostKeyVerificationException when the host key is unknown or changed.
     */
    default Session getSession(String host, int port, String username, String secret, long ttlMillis,
            SshAuthConfig authConfig) throws JSchException {
        return getSession(host, port, username, secret, ttlMillis);
    }

    /**
     * Saves the pending (unknown) host key of the last failed connection attempt
     * into the known_hosts file. No-op when nothing is pending.
     */
    default void trustPendingHostKey(String host, int port) {
        // no-op for implementations that do not verify host keys
    }

    /**
     * Closes a specific SSH session.
     *
     * @param host     Remote host address.
     * @param port     SSH port.
     * @param username SSH username.
     */
    void closeSession(String host, int port, String username);
}
