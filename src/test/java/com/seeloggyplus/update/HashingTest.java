package com.seeloggyplus.update;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HashingTest {

    @Test
    void computesKnownSha256ForBytes() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Hashing.sha256("abc".getBytes()));
    }

    @Test
    void computesSameHashForFileAndBytes() throws Exception {
        byte[] data = "SeeLoggyPlus update payload".getBytes();
        Path file = Files.createTempFile("seeloggy-hash", ".bin");
        try {
            Files.write(file, data);
            assertEquals(Hashing.sha256(data), Hashing.sha256(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
