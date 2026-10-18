package com.seeloggyplus.shared.session;

import javafx.scene.control.Tab;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabSessionManagerTest {

    @Test
    @DisplayName("A new manager holds no sessions")
    void startsEmpty() {
        TabSessionManager manager = new TabSessionManager();

        assertTrue(manager.isEmpty());
        assertEquals(0, manager.size());
        assertTrue(manager.all().isEmpty());
    }

    @Test
    @DisplayName("Sessions are retrievable by their tab and keep registration order")
    void registersAndRetrievesSessions() {
        TabSessionManager manager = new TabSessionManager();
        Tab firstTab = new Tab("first");
        Tab secondTab = new Tab("second");
        LogSession first = new LogSession("first.log", LogSession.SessionType.LOCAL);
        LogSession second = new LogSession("second.log", LogSession.SessionType.REMOTE);

        manager.register(firstTab, first);
        manager.register(secondTab, second);

        assertSame(first, manager.get(firstTab));
        assertSame(second, manager.get(secondTab));
        assertEquals(List.of(first, second), manager.all());
        assertEquals(2, manager.size());
        assertFalse(manager.isEmpty());
    }

    @Test
    @DisplayName("Removing a tab forgets only that session")
    void removesOnlyTheGivenTab() {
        TabSessionManager manager = new TabSessionManager();
        Tab firstTab = new Tab("first");
        Tab secondTab = new Tab("second");
        LogSession first = new LogSession("first.log", LogSession.SessionType.LOCAL);
        LogSession second = new LogSession("second.log", LogSession.SessionType.LOCAL);
        manager.register(firstTab, first);
        manager.register(secondTab, second);

        manager.remove(firstTab);

        assertNull(manager.get(firstTab));
        assertSame(second, manager.get(secondTab));
        assertEquals(List.of(second), manager.all());
    }

    @Test
    @DisplayName("Clearing drops every session")
    void clearDropsAllSessions() {
        TabSessionManager manager = new TabSessionManager();
        manager.register(new Tab("first"), new LogSession("first.log", LogSession.SessionType.LOCAL));
        manager.register(new Tab("second"), new LogSession("second.log", LogSession.SessionType.LOCAL));

        manager.clear();

        assertTrue(manager.isEmpty());
        assertTrue(manager.all().isEmpty());
    }

    @Test
    @DisplayName("The active session can be set and cleared")
    void tracksActiveSession() {
        TabSessionManager manager = new TabSessionManager();
        LogSession session = new LogSession("active.log", LogSession.SessionType.LOCAL);

        assertNull(manager.getActive());

        manager.setActive(session);
        assertSame(session, manager.getActive());

        manager.setActive(null);
        assertNull(manager.getActive());
    }

    @Test
    @DisplayName("Removing or clearing tabs does not silently change the explicit active selection")
    void removalKeepsExplicitActiveSelection() {
        TabSessionManager manager = new TabSessionManager();
        Tab tab = new Tab("active");
        LogSession session = new LogSession("active.log", LogSession.SessionType.LOCAL);
        manager.register(tab, session);
        manager.setActive(session);

        manager.remove(tab);
        assertSame(session, manager.getActive());

        manager.clear();
        assertSame(session, manager.getActive());
    }

    @Test
    @DisplayName("all() returns a snapshot that cannot mutate the manager")
    void allReturnsDetachedSnapshot() {
        TabSessionManager manager = new TabSessionManager();
        Tab tab = new Tab("first");
        LogSession session = new LogSession("first.log", LogSession.SessionType.LOCAL);
        manager.register(tab, session);

        manager.all().clear();

        assertEquals(1, manager.size());
        assertSame(session, manager.get(tab));
        assertEquals(1, manager.all().size());
    }

    @Test
    @DisplayName("sessions() exposes the live map used by existing callers")
    void sessionsViewIsLive() {
        TabSessionManager manager = new TabSessionManager();
        Tab tab = new Tab("live");
        LogSession session = new LogSession("live.log", LogSession.SessionType.LOCAL);

        Map<Tab, LogSession> view = manager.sessions();
        view.put(tab, session);

        assertSame(session, manager.get(tab));
        assertSame(view, manager.sessions());
    }
}
