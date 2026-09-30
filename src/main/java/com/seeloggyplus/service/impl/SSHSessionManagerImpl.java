package com.seeloggyplus.service.impl;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.seeloggyplus.service.PreferenceService;
import com.seeloggyplus.service.SSHSessionManager;
import com.seeloggyplus.ssh.HostKeyVerificationException;
import com.seeloggyplus.ssh.SshAuthConfig;
import com.seeloggyplus.ssh.SshKnownHosts;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Singleton implementation of {@link SSHSessionManager}.
 * <p>
 * Manages a pool of SSH sessions with TTL-based expiration and automatic
 * cleanup.
 */
public class SSHSessionManagerImpl implements SSHSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SSHSessionManagerImpl.class);
    private static final SSHSessionManagerImpl INSTANCE = new SSHSessionManagerImpl();

    private final Map<String, ManagedSession> sessionPool = new ConcurrentHashMap<>();
    /** Host keys seen but not yet trusted, keyed by {@code host:port}. */
    private final Map<String, HostKey> pendingHostKeys = new ConcurrentHashMap<>();
    private final PreferenceService preferenceService;

    /**
     * Private constructor for Singleton.
     * Initializes preference service and cleanup scheduler.
     */
    private SSHSessionManagerImpl() {
        this.preferenceService = new PreferenceServiceImpl();
        ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Retrieves the singleton instance.
     *
     * @return The singleton instance.
     */
    public static SSHSessionManagerImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public Session getSession(String host, int port, String username, String password, long ttlMillis)
            throws JSchException {
        return getSession(host, port, username, password, ttlMillis, SshAuthConfig.password());
    }

    @Override
    public Session getSession(String host, int port, String username, String secret, long ttlMillis,
            SshAuthConfig authConfig) throws JSchException {
        SshAuthConfig auth = authConfig == null ? SshAuthConfig.password() : authConfig;
        String baseKey = generateKey(host, port, username);
        // Key-based sessions never share a pool entry with password sessions.
        String key = auth.type() == SshAuthConfig.AuthType.KEY
                ? baseKey + "#key:" + (auth.keyPath() == null ? "" : auth.keyPath())
                : baseKey;
        if (sessionPool.containsKey(key)) {
            ManagedSession managed = sessionPool.get(key);
            if (managed.isValid()) {
                managed.touch(ttlMillis);
                return managed.getSession();
            } else {
                sessionPool.remove(key);
            }
        }

        logger.info("Creating NEW SSH session for {} ({})", key, auth.type());
        Session session = createNewSession(host, port, username, secret, auth);
        sessionPool.put(key, new ManagedSession(session, ttlMillis));
        return session;
    }

    private Session createNewSession(String host, int port, String username, String secret, SshAuthConfig auth)
            throws JSchException {
        JSch jsch = new JSch();
        HostKeyRepository knownHosts = SshKnownHosts.load(jsch, SshKnownHosts.defaultFile());

        Session session = jsch.getSession(username, host, port);
        Properties config = new Properties();
        if (auth.type() == SshAuthConfig.AuthType.KEY) {
            String keyPath = auth.keyPath();
            if (keyPath == null || keyPath.isBlank() || !Files.exists(Path.of(keyPath))) {
                throw new JSchException("Private key file not found: " + keyPath);
            }
            jsch.addIdentity(keyPath, auth.passphrase() == null ? "" : auth.passphrase());
            config.put("PreferredAuthentications", "publickey");
        } else {
            session.setPassword(secret);
            config.put("PreferredAuthentications", "password,keyboard-interactive");
        }

        // Host keys are verified manually right after login so we can surface the
        // fingerprint and ask the user before trusting a new host.
        config.put("StrictHostKeyChecking", "no");
        config.put("cipher.s2c", "aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com");
        config.put("cipher.c2s", "aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com");
        config.put("compression.s2c", "none,zlib@openssh.com");
        config.put("compression.c2s", "none,zlib@openssh.com");
        config.put("MaxAuthTries", "3");

        session.setServerAliveInterval(60 * 1000); // 60 seconds
        session.setServerAliveCountMax(3);

        session.setConfig(config);

        // Get timeout from preferences (in seconds), convert to milliseconds
        int timeoutSeconds = 60; // default
        try {
            String timeoutStr = preferenceService.getPreferencesByCode("ssh_connection_timeout").orElse("60");
            timeoutSeconds = Integer.parseInt(timeoutStr);
        } catch (NumberFormatException e) {
            logger.warn("Invalid SSH timeout preference, using default: 60 seconds");
        }

        int timeoutMillis = timeoutSeconds * 1000;
        logger.info("Connecting to SSH with timeout: {} seconds", timeoutSeconds);
        session.connect(timeoutMillis);
        verifyHostKey(session, knownHosts, host, port);
        return session;
    }

    /** Manual trust-on-first-use check (StrictHostKeyChecking=no + explicit verify). */
    private void verifyHostKey(Session session, HostKeyRepository knownHosts, String host, int port) {
        HostKey hostKey = session.getHostKey();
        int status = SshKnownHosts.check(knownHosts, host, hostKey);
        if (status == HostKeyRepository.OK) {
            pendingHostKeys.remove(host + ":" + port);
            return;
        }
        session.disconnect();
        String fingerprint = SshKnownHosts.fingerprint(hostKey);
        String keyType = hostKey == null ? "unknown" : hostKey.getType();
        if (status == HostKeyRepository.NOT_INCLUDED) {
            pendingHostKeys.put(host + ":" + port, hostKey);
            throw new HostKeyVerificationException(host, port, keyType, fingerprint,
                    HostKeyVerificationException.Reason.UNKNOWN);
        }
        throw new HostKeyVerificationException(host, port, keyType, fingerprint,
                HostKeyVerificationException.Reason.CHANGED);
    }

    @Override
    public void trustPendingHostKey(String host, int port) {
        HostKey pending = pendingHostKeys.remove(host + ":" + port);
        if (pending == null) {
            return;
        }
        try {
            JSch jsch = new JSch();
            HostKeyRepository knownHosts = SshKnownHosts.load(jsch, SshKnownHosts.defaultFile());
            SshKnownHosts.trust(knownHosts, pending);
            logger.info("Trusted host key for {}:{} ({})", host, port, pending.getType());
        } catch (JSchException e) {
            logger.error("Failed to save host key for {}:{}", host, port, e);
        }
    }

    private String generateKey(String host, int port, String username) {
        return username + "@" + host + ":" + port;
    }

    @Override
    public void closeSession(String host, int port, String username) {
        String base = generateKey(host, port, username);
        sessionPool.keySet().removeIf(k -> {
            if (!k.equals(base) && !k.startsWith(base + "#")) {
                return false;
            }
            ManagedSession managed = sessionPool.get(k);
            if (managed != null && managed.getSession().isConnected()) {
                managed.getSession().disconnect();
            }
            logger.info("Session manually closed: {}", k);
            return true;
        });
    }

    @Override
    public Set<String> getActiveSessionKeys() {
        return Collections.unmodifiableSet(sessionPool.keySet());
    }

    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessionPool.forEach((key, managed) -> {
            if (managed.isExpired(now)) {
                logger.info("Closing expired SSH session (TTL reached): {}", key);
                if (managed.getSession().isConnected()) {
                    managed.getSession().disconnect();
                }
                sessionPool.remove(key);
            }
        });
    }

    /**
     * Inner class to wrap Session with metadata.
     */
    private static class ManagedSession {
        @Getter
        private final Session session;
        private long lastAccessTime;
        private long ttlMillis;

        public ManagedSession(Session session, long ttlMillis) {
            this.session = session;
            this.ttlMillis = ttlMillis;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public boolean isValid() {
            return session != null && session.isConnected();
        }

        public void touch(long newTtl) {
            this.lastAccessTime = System.currentTimeMillis();
            this.ttlMillis = newTtl;
        }

        public boolean isExpired(long now) {
            return (now - lastAccessTime) > ttlMillis;
        }
    }
}