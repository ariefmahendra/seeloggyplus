package com.seeloggyplus.shared.ssh;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.seeloggyplus.shared.ssh.SshAuthConfig;

public interface SSHSessionManager {

    Session getSession(String host, int port, String username, String password, long ttlMillis) throws JSchException;

    default Session getSession(String host, int port, String username, String secret, long ttlMillis,
            SshAuthConfig authConfig) throws JSchException {
        return getSession(host, port, username, secret, ttlMillis);
    }

    default void trustPendingHostKey(String host, int port) {
    }

    void closeSession(String host, int port, String username);
}
