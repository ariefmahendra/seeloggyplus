package com.seeloggyplus.features.ssh.infrastructure;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import com.jcraft.jsch.JSchException;
import com.seeloggyplus.shared.ssh.SshAuthConfig;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SSHSessionManagerImplTest {

    private static Session connectedSession() throws Exception {
        Session session = new JSch().getSession("user", "host", 22);
        var connected = Session.class.getDeclaredField("isConnected");
        connected.setAccessible(true);
        connected.setBoolean(session, true);
        return session;
    }

    @Test
    void closeStopsTheOwnedSchedulerAndIsIdempotent() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            var manager = new SSHSessionManagerImpl(scheduler);
            var resource = assertInstanceOf(AutoCloseable.class, manager);
            resource.close();
            resource.close();
            assertTrue(scheduler.isShutdown());
            assertTrue(scheduler.awaitTermination(2, TimeUnit.SECONDS));
            assertThrows(JSchException.class, () -> manager.getSession("host", 22, "user", "", 1000));
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void closeDisconnectsPooledSessions() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        var session = connectedSession();
        try {
            var manager = new SSHSessionManagerImpl(scheduler) {
                @Override
                Session createNewSession(String host, int port, String username, String secret, SshAuthConfig auth) {
                    return session;
                }
            };
            var resource = assertInstanceOf(AutoCloseable.class, manager);
            assertSame(session, manager.getSession("host", 22, "user", "", 1000));
            assertSame(session, manager.getSession("host", 22, "user", "", 2000));
            resource.close();
            assertFalse(session.isConnected());
            assertDoesNotThrow(() -> manager.closeSession("host", 22, "user"));
        } finally {
            scheduler.shutdownNow();
            session.disconnect();
        }
    }

    @Test
    void aConnectionCompletingAfterCloseIsDisconnectedInsteadOfBeingPooled() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        var connecting = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var session = connectedSession();
        try (var connections = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var manager = new SSHSessionManagerImpl(scheduler) {
                    @Override
                    Session createNewSession(String host, int port, String username, String secret, SshAuthConfig auth)
                            throws JSchException {
                        connecting.countDown();
                        try {
                            if (!release.await(2, TimeUnit.SECONDS)) throw new JSchException("test timeout");
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new JSchException("interrupted", error);
                        }
                        return session;
                    }
                };
                var resource = assertInstanceOf(AutoCloseable.class, manager);
                var pending = connections.submit(() -> manager.getSession("host", 22, "user", "", 1000));
                assertTrue(connecting.await(2, TimeUnit.SECONDS));
                resource.close();
                release.countDown();
                assertInstanceOf(JSchException.class,
                        assertThrows(ExecutionException.class, () -> pending.get(2, TimeUnit.SECONDS)).getCause());
                assertFalse(session.isConnected());
            } finally {
                release.countDown();
            }
        } finally {
            scheduler.shutdownNow();
            session.disconnect();
        }
    }

    @Test
    void singletonIsStable() {
        assertSame(SSHSessionManagerImpl.getInstance(), SSHSessionManagerImpl.getInstance());
    }

    @Test
    void closingAndTrustingUnknownSessionsAreSafe() {
        SSHSessionManagerImpl manager = SSHSessionManagerImpl.getInstance();

        assertDoesNotThrow(() -> manager.closeSession("unknown-host", 22, "nobody"));
        assertDoesNotThrow(() -> manager.trustPendingHostKey("unknown-host", 22));
    }

    @Test
    void managedSessionTracksValidityAndExpiry() throws Exception {
        Session session = new JSch().getSession("user", "example.com", 22);
        Class<?> managedType = Class.forName(
                "com.seeloggyplus.features.ssh.infrastructure.SSHSessionManagerImpl$ManagedSession");
        Constructor<?> constructor = managedType.getDeclaredConstructor(Session.class, long.class);
        constructor.setAccessible(true);
        Object managed = constructor.newInstance(session, 1000L);

        Method isValid = managedType.getDeclaredMethod("isValid");
        Method getSession = managedType.getDeclaredMethod("getSession");
        Method touch = managedType.getDeclaredMethod("touch", long.class);
        Method isExpired = managedType.getDeclaredMethod("isExpired", long.class);
        isValid.setAccessible(true);
        getSession.setAccessible(true);
        touch.setAccessible(true);
        isExpired.setAccessible(true);

        assertSame(session, getSession.invoke(managed));
        assertFalse((boolean) isValid.invoke(managed), "a never-connected session is not valid");

        long now = System.currentTimeMillis();
        assertFalse((boolean) isExpired.invoke(managed, now));

        touch.invoke(managed, 5000L);
        assertFalse((boolean) isExpired.invoke(managed, System.currentTimeMillis()));
        assertTrue((boolean) isExpired.invoke(managed, System.currentTimeMillis() + 6000L));
    }
}
