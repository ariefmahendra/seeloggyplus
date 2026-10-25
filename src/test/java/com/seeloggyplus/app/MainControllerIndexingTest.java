package com.seeloggyplus.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

class MainControllerIndexingTest {

    @TempDir
    Path directory;

    @Test
    void closesTheFileAfterSuccessfulIndexing() throws Exception {
        Path path = Files.writeString(directory.resolve("success.log"), "first\nsecond\n");
        try (var file = new RandomAccessFile(path.toFile(), "r")) {
            var index = MainController.buildFileIndex(file, null);
            assertEquals(2, index.getLineCount());
            assertEquals(6, index.getOffset(1));
            assertFalse(file.getFD().valid());
        }
    }

    @Test
    void closesTheFileWhenTheProgressCallbackFails() throws Exception {
        Path path = Files.writeString(directory.resolve("failure.log"), "line\n");
        var failure = new IllegalStateException("progress failed");
        try (var file = new RandomAccessFile(path.toFile(), "r")) {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> MainController.buildFileIndex(file, (bytes, lines) -> { throw failure; })));
            assertFalse(file.getFD().valid());
        }
    }

    @Test
    void closesTheFileWhenIndexingIsCancelled() throws Exception {
        Path path = Files.writeString(directory.resolve("cancelled.log"), "line\n");
        try (var file = new RandomAccessFile(path.toFile(), "r")) {
            assertThrows(CancellationException.class,
                    () -> MainController.buildFileIndex(file, (bytes, lines) -> { throw new CancellationException(); }));
            assertFalse(file.getFD().valid());
        }
    }

    @Test
    void closesTheFileWhenReadingItsLengthFails() throws Exception {
        Path path = Files.writeString(directory.resolve("read-error.log"), "line\n");
        try (var file = new RandomAccessFile(path.toFile(), "r") {
            @Override
            public long length() throws IOException {
                throw new IOException("length failed");
            }
        }) {
            assertThrows(IOException.class, () -> MainController.buildFileIndex(file, null));
            assertFalse(file.getFD().valid());
        }
    }
}
