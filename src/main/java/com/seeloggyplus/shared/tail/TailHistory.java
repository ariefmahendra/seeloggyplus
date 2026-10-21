package com.seeloggyplus.shared.tail;

import com.seeloggyplus.shared.model.LogEntry;
import java.util.List;
import java.util.ServiceLoader;

public interface TailHistory extends List<LogEntry>, AutoCloseable {
    static TailHistory open() {
        return ServiceLoader.load(TailHistoryFactory.class).findFirst().orElseThrow().create();
    }
    interface Snapshot extends List<LogEntry>, AutoCloseable {
        long baseIndex();
        @Override void close();
    }
    Snapshot snapshot();
    long baseIndex();
    int lineLength(int index);
    long entryBytes(LogEntry entry);
    LogEntry prepare(LogEntry entry);
    LogEntry peek(int index);
    Object keyAt(int index);
    void loadAsync(int index, java.util.function.Consumer<LogEntry> ready);
    default void loadAsync(int index, Runnable ready) {loadAsync(index, entry -> ready.run());}
    long cachedBytes();
    @Override void close();
}
