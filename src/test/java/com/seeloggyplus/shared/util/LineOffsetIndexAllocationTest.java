package com.seeloggyplus.shared.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import static org.junit.jupiter.api.Assertions.*;

class LineOffsetIndexAllocationTest {
    @TempDir Path directory;
    @Test void indexingLongLinesDoesNotPreallocateForTenTimesAsManyRows() throws Exception {
        Path fixture = directory.resolve("long-lines.log");
        String row = "x".repeat(1023) + "\n";
        try (var writer = Files.newBufferedWriter(fixture)) {
            for (int line = 0; line < 16384; line++) writer.write(row);
        }
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        var index = new LineOffsetIndex();
        try (var file = new RandomAccessFile(fixture.toFile(), "r")) {
            long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
            index.buildIndex(file, null);
            long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
            assertEquals(16384, index.getLineCount());
            assertEquals(16383L * 1024, index.getOffset(16383));
            assertTrue(allocated < 1024 * 1024, "Index builder allocated " + allocated + " bytes");
        }
    }
}
