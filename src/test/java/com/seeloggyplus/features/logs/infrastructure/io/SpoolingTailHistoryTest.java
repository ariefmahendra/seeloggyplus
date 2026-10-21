package com.seeloggyplus.features.logs.infrastructure.io;

import com.seeloggyplus.shared.model.LogEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SpoolingTailHistoryTest {
    @TempDir Path root;

    @Test void cacheBudgetIsSharedAcrossSessionsAndDiskSegmentsAreReclaimed() throws Exception {
        var histories = new java.util.ArrayList<SpoolingTailHistory>();
        try {
            for (int session = 0; session < 5; session++) {
                var history = new SpoolingTailHistory(root, 8L * 1024 * 1024);
                histories.add(history);
                for (int row = 0; row < 6000; row++) history.add(new LogEntry(row,
                        "POST session=" + session + " row=" + row + " " + "x".repeat(1024)));
                assertTrue(history.cachedBytes() <= 8L * 1024 * 1024);
            }
            assertTrue(histories.stream().mapToLong(SpoolingTailHistory::cachedBytes).sum() <= 32L * 1024 * 1024);
            for (int session = 0; session < histories.size(); session++) {
                var history = histories.get(session);
                assertTrue(history.getFirst().getRawLog().startsWith("POST session=" + session + " row=0 "));
                history.subList(0, 5990).clear();
                assertEquals(10, history.size());
                assertEquals(5990, history.getFirst().getLineNumber());
            }
            long diskBytes;
            try (var files = Files.walk(root)) {
                diskBytes = files.filter(Files::isRegularFile).mapToLong(path -> {
                    try {return Files.size(path);} catch (java.io.IOException error) {throw new java.io.UncheckedIOException(error);}
                }).sum();
            }
            assertTrue(diskBytes < 5L * 4 * 1024 * 1024, "expired tail segments must be reclaimed");
        } finally {histories.forEach(SpoolingTailHistory::close);}
        assertEquals(0, histories.stream().mapToLong(SpoolingTailHistory::cachedBytes).sum());
        try (var paths = Files.list(root)) {assertEquals(0, paths.count());}
    }

    @Test void aSearchSnapshotPinsEvictedDiskRowsUntilSearchFinishes() throws Exception {
        var history = new SpoolingTailHistory(root, 8192L);
        List<LogEntry> rows = history;
        String raw = "row-" + "x".repeat(100_000);
        for (int i = 0; i < 50; i++) rows.add(new LogEntry(i, raw + i));
        var snapshot = history.snapshot();
        assertEquals(0, snapshot.baseIndex());
        rows.clear();
        assertEquals(50, history.baseIndex());
        assertEquals(0, snapshot.baseIndex());
        assertEquals(raw + 0, snapshot.getFirst().getRawLog());
        snapshot.close();
        history.close();
        try (var paths = Files.list(root)) {assertEquals(0, paths.count());}
    }

    @Test void evictedRowsRemainCompleteAndCloseDeletesOnlyOwnedFiles() throws Exception {
        Path unrelated = Files.writeString(root.resolve("keep.txt"), "keep");
        var history = new SpoolingTailHistory(root, 8192L);
        List<LogEntry> rows = history;
        String raw = "POST \u0130 \u212a \ud83d\ude00 " + "x".repeat(2000);
        for (int i = 0; i < 100; i++) {
            LogEntry entry = new LogEntry(i, raw + i);
            entry.setLevel("ERROR");
            rows.add(entry);
        }
        assertTrue(history.cachedBytes() <= 8192);
        assertEquals(raw + 0, rows.getFirst().getRawLog());
        assertEquals("ERROR", rows.getFirst().getLevel());
        rows.subList(0, 90).clear();
        assertEquals(10, rows.size());
        assertEquals(raw + 90, rows.getFirst().getRawLog());
        assertEquals(90, rows.getFirst().getLineNumber());
        history.close();
        assertEquals(0, rows.size());
        try (var paths = Files.list(root)) {assertEquals(List.of(unrelated), paths.toList());}
        history.close();
    }
}
