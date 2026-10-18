package com.seeloggyplus.features.ssh.infrastructure;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SSHSessionManagerImplTest {

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
