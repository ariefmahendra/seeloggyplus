package com.seeloggyplus.service;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.util.Set;

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
     * Closes a specific SSH session.
     *
     * @param host     Remote host address.
     * @param port     SSH port.
     * @param username SSH username.
     */
    void closeSession(String host, int port, String username);

    /**
     * Returns a set of keys for all currently active sessions.
     *
     * @return Set of session keys (username@host:port).
     */
    Set<String> getActiveSessionKeys();
}
