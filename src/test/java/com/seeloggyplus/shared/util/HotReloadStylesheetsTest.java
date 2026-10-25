package com.seeloggyplus.shared.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class HotReloadStylesheetsTest {

    @TempDir
    Path directory;

    @Test
    void rotatesCopiesAndCleansOnlyItsOwnFiles() throws Exception {
        Path source = Files.writeString(directory.resolve("components.css"), "root {}");
        Path unrelated = Files.writeString(directory.resolve("unrelated.txt"), "keep");
        var copies = new ArrayList<Path>();
        var storage = new HotReloadStylesheets(3);
        try {
            for (int i = 0; i < 6; i++) copies.add(storage.copy(source));
            for (int i = 0; i < 3; i++) assertFalse(Files.exists(copies.get(i)));
            for (int i = 3; i < 6; i++) assertEquals("root {}", Files.readString(copies.get(i)));
        } finally {
            storage.close();
        }
        assertEquals("keep", Files.readString(unrelated));
        assertFalse(Files.exists(copies.getLast().getParent()));
        assertDoesNotThrow(storage::close);
    }

    @Test
    void missingSourceDoesNotLeaveTemporaryCopies() throws Exception {
        try (var storage = new HotReloadStylesheets(3)) {
            storage.prepare();
            Path copied = storage.copy(Files.writeString(directory.resolve("theme.css"), "root {}"));
            assertThrows(java.io.IOException.class, () -> storage.copy(directory.resolve("missing.css")));
            try (var files = Files.list(copied.getParent())) {
                assertEquals(1, files.count());
            }
        }
    }
}
