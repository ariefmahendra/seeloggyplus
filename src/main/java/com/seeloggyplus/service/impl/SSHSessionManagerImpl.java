package com.seeloggyplus.service.impl;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.seeloggyplus.service.SSHSessionManager;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;

public class SSHSessionManagerImpl implements SSHSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SSHSessionManagerImpl.class);
    private static final SSHSessionManagerImpl INSTANCE = new SSHSessionManagerImpl();

    private final Map<String, ManagedSession> sessionPool = new ConcurrentHashMap<>();

    private SSHSessionManagerImpl() {
        ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 1, 1, TimeUnit.MINUTES);
    }

    public static SSHSessionManagerImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public Session getSession(String host, int port, String username, String password, long ttlMillis) throws JSchException {
        String key = generateKey(host, port, username);
        if (sessionPool.containsKey(key)) {
            ManagedSession managed = sessionPool.get(key);
            if (managed.isValid()) {
                managed.touch(ttlMillis);
                return managed.getSession();
            } else {
                sessionPool.remove(key);
            }
        }

        logger.info("Creating NEW SSH session for {}", key);
        Session session = createNewSession(host, port, username, password);
        sessionPool.put(key, new ManagedSession(session, ttlMillis));
        return session;
    }

    private Session createNewSession(String host, int port, String username, String password) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "password");

        session.setServerAliveInterval(60 * 1000);
        session.setServerAliveCountMax(3);

        session.setConfig(config);
        session.connect(30000);
        return session;
    }

    private String generateKey(String host, int port, String username) {
        return username + "@" + host + ":" + port;
    }

    @Override
    public void closeSession(String host, int port, String username) {
        String key = generateKey(host, port, username);
        ManagedSession managed = sessionPool.remove(key);
        if (managed != null && managed.getSession().isConnected()) {
            managed.getSession().disconnect();
            logger.info("Session manually closed: {}", key);
        }
    }

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