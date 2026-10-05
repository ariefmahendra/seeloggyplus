package com.seeloggyplus.shared.session;

import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TabSessionManager {

    private final Map<Tab, LogSession> sessions = new LinkedHashMap<>();
    private LogSession active;

    public LogSession getActive() {
        return active;
    }

    public void setActive(LogSession session) {
        this.active = session;
    }

    public void register(Tab tab, LogSession session) {
        sessions.put(tab, session);
    }

    public LogSession get(Tab tab) {
        return sessions.get(tab);
    }

    public void remove(Tab tab) {
        sessions.remove(tab);
    }

    public void clear() {
        sessions.clear();
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    public int size() {
        return sessions.size();
    }

    public List<LogSession> all() {
        return new ArrayList<>(sessions.values());
    }

    public Map<Tab, LogSession> sessions() {
        return sessions;
    }
}
