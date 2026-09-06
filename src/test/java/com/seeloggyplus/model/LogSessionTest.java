package com.seeloggyplus.model;

import com.seeloggyplus.util.IntArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class LogSessionTest {

    @Test
    @DisplayName("Should correctly initialize LogSession with unique ID and default state")
    void testSessionInitialization() {
        LogSession session = new LogSession("app.log", LogSession.SessionType.LOCAL);

        assertNotNull(session.getId());
        assertEquals("app.log", session.getTitle());
        assertEquals(LogSession.SessionType.LOCAL, session.getSessionType());
        assertFalse(session.isActive());
        assertFalse(session.isClosed());
        assertEquals(0, session.getUnreadTailLines());
        assertFalse(session.isTailModeEnabled());
        assertTrue(session.isFollowTail());
    }

    @Test
    @DisplayName("Should track unread tail lines and reset when session becomes active")
    void testUnreadCounterLogic() {
        LogSession session = new LogSession("server.log", LogSession.SessionType.REMOTE);

        session.addUnreadTailLines(10);
        assertEquals(10, session.getUnreadTailLines());

        session.addUnreadTailLines(5);
        assertEquals(15, session.getUnreadTailLines());

        // Becoming active automatically resets unread count
        session.setActive(true);
        assertTrue(session.isActive());
        assertEquals(0, session.getUnreadTailLines());

        // Background lines arriving while active: if marked dormant again
        session.setActive(false);
        session.addUnreadTailLines(20);
        assertEquals(20, session.getUnreadTailLines());

        session.resetUnreadTailLines();
        assertEquals(0, session.getUnreadTailLines());
    }

    @Test
    @DisplayName("Should manage remote tail line counter and live buffers")
    void testTailBufferAndLiveTailList() {
        LogSession session = new LogSession("test.log", LogSession.SessionType.REMOTE);

        assertEquals(0, session.getRemoteTailLineCounter());
        assertEquals(1, session.incrementRemoteTailLineCounter());
        assertEquals(2, session.incrementRemoteTailLineCounter());
        assertEquals(2, session.getRemoteTailLineCounter());

        LogEntry entry = new LogEntry(1, "2026-09-06 12:00:00 [INFO] Started");
        session.getLiveTailList().add(entry);
        session.getTailBuffer().add(entry);

        assertEquals(1, session.getLiveTailList().size());
        assertEquals(1, session.getTailBuffer().size());
    }

    @Test
    @DisplayName("Should preserve search query, regex, and filter match indices")
    void testSearchAndFilterStatePreservation() {
        LogSession session = new LogSession("audit.log", LogSession.SessionType.LOCAL);

        session.setSearchQuery("ERROR OR WARN");
        session.setRegex(true);
        session.setCaseSensitive(false);
        session.setCurrentMatchIndex(4);
        session.setTotalMatches(12);

        IntArrayList matches = new IntArrayList();
        matches.add(10);
        matches.add(25);
        matches.add(42);
        session.setFilteredIndexes(matches);

        assertEquals("ERROR OR WARN", session.getSearchQuery());
        assertTrue(session.isRegex());
        assertFalse(session.isCaseSensitive());
        assertEquals(4, session.getCurrentMatchIndex());
        assertEquals(12, session.getTotalMatches());
        assertEquals(3, session.getFilteredIndexes().size());
        assertEquals(25, session.getFilteredIndexes().get(1));
    }

    @Test
    @DisplayName("Should release buffers and mark session as closed cleanly and idempotently")
    void testCloseCleanup() {
        LogSession session = new LogSession("cleanup.log", LogSession.SessionType.LOCAL);
        session.setLocalFile(new File("dummy.log"));

        LogEntry entry = new LogEntry(1, "Dummy log line");
        session.getLiveTailList().add(entry);
        session.getTailBuffer().add(entry);

        IntArrayList filter = new IntArrayList();
        filter.add(1);
        session.setFilteredIndexes(filter);

        session.close();

        assertTrue(session.isClosed());
        assertEquals(0, session.getLiveTailList().size());
        assertEquals(0, session.getTailBuffer().size());
        assertNull(session.getFilteredIndexes());

        // Idempotent close - subsequent call should not throw exception
        assertDoesNotThrow(session::close);
    }
}
