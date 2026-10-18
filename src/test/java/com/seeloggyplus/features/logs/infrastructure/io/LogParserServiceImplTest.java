package com.seeloggyplus.features.logs.infrastructure.io;

import com.seeloggyplus.shared.model.LogEntry;
import com.seeloggyplus.shared.model.ParsingConfig;
import com.seeloggyplus.shared.progress.ProgressCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class LogParserServiceImplTest {

    @TempDir
    Path directory;

    private final LogParserServiceImpl parser = new LogParserServiceImpl();

    @Test
    void readsUtf8LinesInOrderAndReportsCompletion() throws Exception {
        Path file = directory.resolve("sample.log");
        Files.writeString(file, "first line\n日本語\nlast line");
        AtomicLong completed = new AtomicLong(-1);
        List<LogEntry> entries = parser.parseFileParallel(file.toFile(), ParsingConfig.createRawConfig(),
                new ProgressCallback() {
                    @Override
                    public void onProgress(double progress, long processed, long total) {
                        assertTrue(progress >= 0 && progress <= 1);
                        assertTrue(processed <= total);
                    }
                    @Override
                    public void onComplete(long items) {
                        completed.set(items);
                    }
                });
        assertEquals(List.of("first line", "日本語", "last line"),
                entries.stream().map(LogEntry::getRawLog).toList());
        assertEquals(3, completed.get());
    }

    @Test
    void rawLineAndSearchBehaviorIsPreserved() {
        LogEntry info = parser.parseLine("INFO started", 1);
        LogEntry error = parser.parseLine("ERROR failed", 2);
        List<LogEntry> entries = List.of(info, error);
        assertEquals("INFO started", info.getRawLog());
        assertEquals(List.of(error), parser.search(entries, "error", false, false));
        assertTrue(parser.search(entries, "error", false, true).isEmpty());
        assertEquals(List.of(info), parser.search(entries, "^INFO", true, true));
        assertEquals(entries, parser.search(entries, "", false, false));
    }

    @Test
    void emptyFileAndMissingFileRemainDistinct() throws Exception {
        Path empty = Files.createFile(directory.resolve("empty.log"));
        assertTrue(parser.parseFileParallel(empty.toFile(), ParsingConfig.createRawConfig(), null).isEmpty());
        assertThrows(java.io.IOException.class, () -> parser.parseFileParallel(
                directory.resolve("missing.log").toFile(), ParsingConfig.createRawConfig(), null));
    }
}
