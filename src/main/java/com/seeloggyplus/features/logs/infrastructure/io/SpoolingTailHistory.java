package com.seeloggyplus.features.logs.infrastructure.io;

import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.tail.TailHistory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class SpoolingTailHistory extends AbstractList<LogEntry> implements TailHistory {
    private static final long GLOBAL_LIMIT = 32L * 1024 * 1024;
    private static final Object CACHE_LOCK = new Object();
    private static final LinkedHashMap<Record, Cached> CACHE = new LinkedHashMap<>(256, .75f, true);
    private static long globalBytes;
    private static final class Payload extends ByteArrayOutputStream {
        final DataOutputStream output = new DataOutputStream(this);
        Payload() {super(4096);}
        void writeTo(RandomAccessFile file) throws IOException {file.write(buf, 0, count);}
    }
    private static final ThreadLocal<Payload> WRITE_BUFFER = ThreadLocal.withInitial(Payload::new);
    private final Path root;
    private final long limit;
    private final ArrayList<Record> rows = new ArrayList<>();
    private final Set<Segment> segments = new HashSet<>();
    private final LinkedHashMap<Record, java.util.function.Consumer<LogEntry>> pending = new LinkedHashMap<>();
    private Path directory;
    private Segment active;
    private long bytes;
    private int head;
    private long baseIndex;
    private boolean closed;
    private boolean loading;

    public SpoolingTailHistory(Path root, long limit) {
        this.root = root;
        if (limit < 1) throw new IllegalArgumentException("Positive cache budget required");
        this.limit = limit;
    }

    private final class Segment {
        final Path path;
        final RandomAccessFile file;
        long length;
        int references;
        int pins;
        Segment() throws IOException {
            if (directory == null) directory = Files.createTempDirectory(root, "seeloggy-tail-");
            path = Files.createTempFile(directory, "history-", ".bin");
            file = new RandomAccessFile(path.toFile(), "rw");
            segments.add(this);
        }
    }

    private final class Record extends LogEntry {
        final Segment segment;
        final long offset;
        final int length;
        final boolean parsed;
        final long end;
        final LogEntry original;
        final long estimatedBytes;
        Record(LogEntry entry, Segment segment, long offset, int length) {
            super(entry.getLineNumber(), "");
            this.length = length;
            this.end = entry.getEndLineNumber();
            this.estimatedBytes = entry.getClass() == LogEntry.class ? 128L + 2L * entry.getRawLog().length() : 128;
            this.original = entry.getClass() == LogEntry.class ? null : entry;
            this.segment = segment;
            this.offset = offset;
            this.parsed = entry.isParsed();
        }
        SpoolingTailHistory owner() {return SpoolingTailHistory.this;}
        @Override public long getEndLineNumber() {return end;}
        @Override public int hashCode() {return System.identityHashCode(this);}
        @Override public boolean equals(Object value) {return this == value;}
        @Override public String getRawLog() {return read(this).getRawLog();}
        @Override public String getMessage() {return read(this).getMessage();}
        @Override public String getLevel() {return read(this).getLevel();}
        @Override public Map<String, String> getParsedFields() {return read(this).getParsedFields();}
    }

    private record Cached(LogEntry entry, long cost, SpoolingTailHistory owner) {}

    @Override public synchronized long baseIndex() {return baseIndex;}
    @Override public synchronized int lineLength(int index) {
        Record record = recordAt(index);
        return record.original == null ? (int) ((record.estimatedBytes - 128) / 2) : record.original.getRawLog().length();
    }

    @Override public long entryBytes(LogEntry entry) {
        return entry instanceof SpoolingTailHistory.Record record && record.owner() == this
                ? record.estimatedBytes : 128L + 2L * entry.getRawLog().length();
    }

    @Override public synchronized LogEntry prepare(LogEntry entry) {
        if (closed) throw new IllegalStateException("Tail history is closed");
        if (entry instanceof SpoolingTailHistory.Record record && record.owner() == this) return entry;
        if (entry.getClass() != LogEntry.class) return new Record(entry, null, 0, 0);
        try {
            if (active == null || active.length >= 4L * 1024 * 1024) active = new Segment();
            long offset = active.length;
            Payload payload = WRITE_BUFFER.get();
            if (payload.size() > 256 * 1024) {payload = new Payload(); WRITE_BUFFER.set(payload);}
            payload.reset();
            DataOutputStream output = payload.output;
            writeString(output, entry.getRawLog());
            Map<String, String> fields = entry.getParsedFields();
            output.writeInt(fields.size());
            for (var field : fields.entrySet()) {
                writeString(output, field.getKey());
                writeString(output, field.getValue());
            }
            output.writeBoolean(entry.getTimestamp() != null);
            if (entry.getTimestamp() != null) writeString(output, entry.getTimestamp().toString());
            active.file.seek(offset);
            payload.writeTo(active.file);
            active.length = active.file.getFilePointer();
            active.references++;
            Record record = new Record(entry, active, offset, payload.size());
            cache(record, entry);
            reap();
            return record;
        } catch (IOException error) {
            throw new UncheckedIOException("Could not spool tail history", error);
        }
    }

    private static void writeString(DataOutput file, String value) throws IOException {
        file.writeBoolean(value != null);
        if (value == null) return;
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        file.writeInt(encoded.length);
        file.write(encoded);
    }

    private static String readString(DataInput file) throws IOException {
        if (!file.readBoolean()) return null;
        byte[] encoded = new byte[file.readInt()];
        file.readFully(encoded);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private synchronized LogEntry read(Record record) {
        if (record.original != null) return record.original;
        synchronized (CACHE_LOCK) {
            Cached value = CACHE.get(record);
            if (value != null) return value.entry;
        }
        if (closed) throw new IllegalStateException("Tail history is closed");
        try {
            RandomAccessFile source = record.segment.file;
            source.seek(record.offset);
            byte[] encoded = new byte[record.length];
            source.readFully(encoded);
            DataInputStream file = new DataInputStream(new ByteArrayInputStream(encoded));
            String raw = readString(file);
            int count = file.readInt();
            Map<String, String> fields = new HashMap<>();
            for (int i = 0; i < count; i++) fields.put(readString(file), readString(file));
            LogEntry entry = record.parsed ? new LogEntry(record.getLineNumber(), raw, fields)
                    : record.getLineNumber() == record.getEndLineNumber() ? new LogEntry(record.getLineNumber(), raw)
                    : new LogEntry(record.getLineNumber(), record.getEndLineNumber(), raw);
            if (!record.parsed && !fields.isEmpty()) entry.addFields(fields);
            if (file.readBoolean()) entry.setTimestamp(java.time.LocalDateTime.parse(readString(file)));
            if (fields.containsKey("level")) entry.setLevel(fields.get("level"));
            if (fields.containsKey("message")) entry.setMessage(fields.get("message"));
            if (fields.containsKey("thread")) entry.setThread(fields.get("thread"));
            if (fields.containsKey("logger")) entry.setLogger(fields.get("logger"));
            cache(record, entry);
            return entry;
        } catch (IOException error) {throw new UncheckedIOException(error);}
    }

    private void cache(Record record, LogEntry entry) {
        long cost = 160L + 2L * entry.getRawLog().length();
        for (var field : entry.getParsedFields().entrySet()) {
            cost += 64L + 2L * field.getKey().length() + (field.getValue() == null ? 0 : 2L * field.getValue().length());
        }
        synchronized (CACHE_LOCK) {
            Cached previous = CACHE.remove(record);
            if (previous != null) {globalBytes -= previous.cost; bytes -= previous.cost;}
            if (cost > limit) return;
            CACHE.put(record, new Cached(entry, cost, this));
            globalBytes += cost;
            bytes += cost;
            var iterator = CACHE.entrySet().iterator();
            while (iterator.hasNext() && (bytes > limit || globalBytes > GLOBAL_LIMIT)) {
                Cached value = iterator.next().getValue();
                if (globalBytes > GLOBAL_LIMIT || value.owner == this) {
                    globalBytes -= value.cost;
                    value.owner.bytes -= value.cost;
                    iterator.remove();
                }
            }
        }
    }

    private final class HistorySnapshot extends AbstractList<LogEntry> implements TailHistory.Snapshot {
        private final List<Record> captured;
        private final Set<Segment> pinned = new HashSet<>();
        private boolean released;
        private final long base = baseIndex;
        @Override public long baseIndex() {return base;}
        HistorySnapshot() {
            captured = new ArrayList<>(rows.subList(head, rows.size()));
            for (Record record : captured) if (record.segment != null) pinned.add(record.segment);
            for (Segment segment : pinned) segment.pins++;
        }
        @Override public LogEntry get(int index) {return read(captured.get(index));}
        @Override public int size() {return captured.size();}
        @Override public void close() {
            synchronized (SpoolingTailHistory.this) {
                if (released) return;
                released = true;
                for (Segment segment : pinned) segment.pins = Math.max(0, segment.pins - 1);
                pinned.clear(); captured.clear(); reap();
            }
        }
    }
    @Override public synchronized TailHistory.Snapshot snapshot() {return new HistorySnapshot();}

    @Override public synchronized boolean add(LogEntry entry) {
        rows.add((Record) prepare(entry));
        modCount++;
        return true;
    }
    @Override public synchronized boolean addAll(Collection<? extends LogEntry> entries) {
        for (LogEntry entry : entries) add(entry);
        return !entries.isEmpty();
    }
    @Override public synchronized int size() {return rows.size() - head;}
    @Override public synchronized LogEntry get(int index) {return read(recordAt(index));}
    private Record recordAt(int index) {Objects.checkIndex(index, size()); return rows.get(head + index);}
    @Override public synchronized LogEntry peek(int index) {
        Record record = recordAt(index);
        if (record.original != null) return record.original;
        synchronized (CACHE_LOCK) {
            Cached cached = CACHE.get(record);
            return cached == null ? null : cached.entry;
        }
    }
    @Override public long cachedBytes() {synchronized (CACHE_LOCK) {return bytes;}}

    @Override public synchronized Object keyAt(int index) {return recordAt(index);}

    @Override public synchronized void loadAsync(int index, java.util.function.Consumer<LogEntry> ready) {
        if (closed || index < 0 || index >= size()) return;
        Record requested = recordAt(index);
        if (pending.size() < 256 || pending.containsKey(requested)) pending.put(requested, ready);
        if (loading) return;
        loading = true;
        Thread.ofVirtual().name("tail-history-read").start(() -> {
            try {
                while (true) {
                    LogEntry entry;
                    java.util.function.Consumer<LogEntry> callback;
                    synchronized (this) {
                        if (closed || pending.isEmpty()) {loading = false; return;}
                        Record record = pending.keySet().iterator().next();
                        callback = pending.remove(record);
                        if (record.segment != null && record.segment.references <= 0) continue;
                        entry = read(record);
                    }
                    callback.accept(entry);
                }
            } catch (RuntimeException error) {
                synchronized (this) {loading = false;}
                throw error;
            }
        });
    }

    @Override protected synchronized void removeRange(int from, int to) {
        Objects.checkFromToIndex(from, to, size());
        for (int i = from; i < to; i++) {
            Record record = rows.get(head + i);
            if (record.segment != null) record.segment.references--;
            pending.remove(record);
            synchronized (CACHE_LOCK) {
                Cached removed = CACHE.remove(record);
                if (removed != null) {bytes -= removed.cost; globalBytes -= removed.cost;}
            }
        }
        if (from == 0) {
            baseIndex += to;
            for (int i = 0; i < to; i++) rows.set(head + i, null);
            head += to;
            if (head > 8192) {rows.subList(0, head).clear(); head = 0;}
        } else rows.subList(head + from, head + to).clear();
        modCount++;
        reap();
    }

    private void reap() {
        var iterator = segments.iterator();
        while (iterator.hasNext()) {
            Segment segment = iterator.next();
            if (segment == active || segment.references > 0 || segment.pins > 0) continue;
            try {segment.file.close(); Files.deleteIfExists(segment.path);}
            catch (IOException error) {throw new UncheckedIOException(error);}
            iterator.remove();
        }
    }

    @Override public synchronized void clear() {
        synchronized (CACHE_LOCK) {
            var iterator = CACHE.values().iterator();
            while (iterator.hasNext()) {
                Cached value = iterator.next();
                if (value.owner == this) {globalBytes -= value.cost; iterator.remove();}
            }
            bytes = 0;
        }
        baseIndex += size();
        pending.clear(); rows.clear(); head = 0; modCount++;
        active = null;
        for (Segment segment : segments) {segment.references = 0; if (closed) segment.pins = 0;}
        reap();
        if (directory != null && segments.isEmpty()) {
            try {Files.deleteIfExists(directory); directory = null;}
            catch (IOException error) {throw new UncheckedIOException(error);}
        }
    }
    @Override public synchronized void close() {closed = true; clear(); WRITE_BUFFER.remove();}
}
