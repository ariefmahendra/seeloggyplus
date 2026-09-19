package com.seeloggyplus.service.impl;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResumableDownloadTest {
    @Test
    void resumesFromExistingPartialFile() throws Exception {
        byte[] content = new byte[1024];
        for (int i = 0; i < content.length; i++) content[i] = (byte) i;
        Path target = Files.createTempFile("seeloggyplus-resume", ".log");
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        Files.deleteIfExists(target);
        Files.write(partial, Arrays.copyOf(content, 400));
        AtomicInteger requestedOffset = new AtomicInteger(-1);

        boolean completed = ResumableDownload.download(new ResumableDownload.Source() {
            @Override public long size() { return content.length; }
            @Override public InputStream open(long offset) {
                requestedOffset.set((int) offset);
                return new ByteArrayInputStream(content, (int) offset, content.length - (int) offset);
            }
        }, target, null, 1);

        assertTrue(completed);
        assertEquals(400, requestedOffset.get());
        assertArrayEquals(content, Files.readAllBytes(target));
        assertFalse(Files.exists(partial));
        Files.deleteIfExists(target);
    }

    @Test
    void retainsPartialFileAfterAllAttemptsFail() throws Exception {
        Path target = Files.createTempFile("seeloggyplus-failure", ".log");
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        Files.deleteIfExists(target);

        boolean completed = ResumableDownload.download(new ResumableDownload.Source() {
            @Override public long size() { return 10; }
            @Override public InputStream open(long offset) throws IOException {
                throw new IOException("connection lost");
            }
        }, target, null, 1);

        assertFalse(completed);
        assertFalse(Files.exists(target));
        assertTrue(Files.exists(partial), "Partial file must remain available for resume");
        Files.deleteIfExists(partial);
    }

    @Test
    void cancellationPreventsPublishingLateDownload() throws Exception {
        byte[] content = new byte[512 * 1024];
        Path target = Files.createTempFile("seeloggyplus-cancel", ".log");
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        Files.deleteIfExists(target);
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();

        boolean completed = ResumableDownload.download(new ResumableDownload.Source() {
            @Override public long size() { return content.length; }
            @Override public InputStream open(long offset) { return new ByteArrayInputStream(content); }
            @Override public boolean cancelled() { return cancelled.get(); }
        }, target, (transferred, total) -> cancelled.set(true), 1);

        assertFalse(completed);
        assertFalse(Files.exists(target));
        assertTrue(Files.exists(partial));
        Files.deleteIfExists(partial);
    }

    @Test
    void doesNotPublishTargetUntilFullTransferCompletes() throws Exception {
        byte[] content = "complete transfer".getBytes();
        Path target = Files.createTempFile("seeloggyplus-atomic", ".log");
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        Files.deleteIfExists(target);

        boolean completed = ResumableDownload.download(new ResumableDownload.Source() {
            @Override public long size() { return content.length + 1L; }
            @Override public InputStream open(long offset) { return new ByteArrayInputStream(content); }
        }, target, null, 1);

        assertFalse(completed);
        assertFalse(Files.exists(target));
        assertTrue(Files.exists(partial));
        Files.deleteIfExists(partial);
    }
}
