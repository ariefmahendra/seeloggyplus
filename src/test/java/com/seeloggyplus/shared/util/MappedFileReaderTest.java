package com.seeloggyplus.shared.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class MappedFileReaderTest {

    @Test
    void readsLinesLongerThanTheLegacyReadWindow() throws Exception {
        String longLine = "a".repeat(20_000);
        File file = writeTempLog(longLine + "\nshort\n");
        LineOffsetIndex index = buildIndex(file);

        try (MappedFileReader reader = new MappedFileReader(file)) {
            assertEquals(20_000, reader.readLine(index, 0).length());
            assertEquals("short", reader.readLine(index, 1));
        }
    }

    @Test
    void matchesTokensBeyondTheLegacyReadWindow() throws Exception {
        File file = writeTempLog("b".repeat(30_000) + "NEEDLE\nplain\n");
        LineOffsetIndex index = buildIndex(file);

        try (MappedFileReader reader = new MappedFileReader(file)) {
            assertTrue(reader.lineMatches(index, 0, cs -> cs.toString().contains("NEEDLE")));
            assertFalse(reader.lineMatches(index, 0, cs -> cs.toString().contains("MISSING")));
            assertFalse(reader.lineMatches(index, 1, cs -> cs.toString().contains("NEEDLE")));
        }
    }

    @Test
    void keepsMultiByteCharactersBeyondTheLegacyReadWindow() throws Exception {
        String longLine = "é".repeat(10_000) + "END";
        File file = writeTempLog(longLine + "\n");
        LineOffsetIndex index = buildIndex(file);

        try (MappedFileReader reader = new MappedFileReader(file)) {
            assertEquals(longLine, reader.readLine(index, 0));
        }
    }

    @Test
    void clipsLinesAtTheSafetyBound() throws Exception {
        File file = writeTempLog("c".repeat(MappedFileReader.MAX_LINE_BYTES + 5_000) + "\n");
        LineOffsetIndex index = buildIndex(file);

        try (MappedFileReader reader = new MappedFileReader(file)) {
            assertEquals(MappedFileReader.MAX_LINE_BYTES, reader.readLine(index, 0).length());
        }
    }

    @Test
    void readsLinesAcrossChunkBoundaries() throws Exception {
        String longLine = "d".repeat(40);
        File file = writeTempLog(longLine + "\nlast\n");
        LineOffsetIndex index = buildIndex(file);

        try (MappedFileReader reader = new MappedFileReader(file, StandardCharsets.UTF_8, 16)) {
            assertEquals(longLine, reader.readLine(index, 0));
            assertEquals("last", reader.readLine(index, 1));
            assertTrue(reader.lineMatches(index, 0, cs -> cs.toString().endsWith("dddd")));
        }
    }

    @Test
    void readsEmptyMiddleLineAndRejectsUnknownLines() throws Exception {
        File file = writeTempLog("first\n\nthird\n");
        LineOffsetIndex index = buildIndex(file);

        try (MappedFileReader reader = new MappedFileReader(file)) {
            assertEquals("first", reader.readLine(index, 0));
            assertEquals("", reader.readLine(index, 1));
            assertEquals("third", reader.readLine(index, 2));
            assertEquals("", reader.readLine(index, 99));
            assertFalse(reader.lineMatches(index, 99, cs -> true));
        }
    }

    @Test
    void honoursASmallerCallerWindow() throws Exception {
        File file = writeTempLog("abcdefghij\n");
        LineOffsetIndex index = buildIndex(file);

        try (MappedFileReader reader = new MappedFileReader(file)) {
            assertEquals("abcdef", reader.readLine(0, 6));
        }
    }

    @Test
    void closeReleasesThePerThreadReadBuffer() throws Exception {
        File file = writeTempLog("line\n");
        LineOffsetIndex index = buildIndex(file);

        Object buffer;
        try (MappedFileReader reader = new MappedFileReader(file)) {
            assertEquals("line", reader.readLine(index, 0));
            buffer = readBuffer().get();
            assertNotNull(buffer);
        }
        assertNotSame(buffer, readBuffer().get());
    }

    private static ThreadLocal<?> readBuffer() throws Exception {
        var field = MappedFileReader.class.getDeclaredField("READ_BUFFER");
        field.setAccessible(true);
        return (ThreadLocal<?>) field.get(null);
    }

    private static File writeTempLog(String content) throws Exception {
        File file = File.createTempFile("mapped-reader", ".log");
        file.deleteOnExit();
        Files.writeString(file.toPath(), content);
        return file;
    }

    private static LineOffsetIndex buildIndex(File file) throws Exception {
        LineOffsetIndex index = new LineOffsetIndex();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            index.buildIndex(raf, null);
        }
        return index;
    }
}
