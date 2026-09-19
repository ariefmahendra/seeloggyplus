package com.seeloggyplus.service.impl;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class ResumableDownload {
    interface Source {
        long size() throws IOException;
        InputStream open(long offset) throws IOException;
        default boolean cancelled() { return false; }
    }

    interface Progress {
        void update(long transferred, long total);
    }

    private static final int BUFFER_SIZE = 256 * 1024;

    private ResumableDownload() {
    }

    static boolean download(Source source, Path target, Progress progress, int maxAttempts) {
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        try {
            long total = source.size();
            if (total <= 0) return false;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                long offset = Files.exists(partial) ? Files.size(partial) : 0;
                if (offset > total) {
                    Files.deleteIfExists(partial);
                    offset = 0;
                }
                try {
                    long transferred = offset;
                    try (RandomAccessFile output = new RandomAccessFile(partial.toFile(), "rw");
                         InputStream input = source.open(offset)) {
                        output.seek(offset);
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (source.cancelled()) throw new IOException("Transfer cancelled");
                        output.write(buffer, 0, read);
                            transferred += read;
                            if (progress != null) progress.update(transferred, total);
                        }
                    }
                    if (transferred != total) throw new EOFException("Incomplete transfer");
                    moveAtomically(partial, target);
                    return true;
                } catch (IOException e) {
                    if (attempt + 1 == maxAttempts) return false;
                    try {
                        Thread.sleep(500L << attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static void moveAtomically(Path partial, Path target) throws IOException {
        try {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
