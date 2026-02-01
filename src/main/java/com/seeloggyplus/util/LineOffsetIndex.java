package com.seeloggyplus.util;

import lombok.Getter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.function.LongConsumer;

/**
 * Stores byte offsets for each line ending in a log file.
 * Enables O(1) lookup: getOffset(lineNumber) → byte position.
 * Memory usage: ~8 bytes per line (long[]).
 * For 10M lines: ~80MB RAM.
 */
public class LineOffsetIndex {

    private long[] offsets;
    /**
     * -- GETTER --
     *  Get total number of lines indexed.
     */
    @Getter
    private int lineCount;

    public LineOffsetIndex() {
        this.offsets = new long[0];
        this.lineCount = 0;
    }

    /**
     * Build index by scanning the file for newline characters.
     * 
     * @param file             The file to index
     * @param progressCallback Called with bytes processed (for progress UI)
     * @throws IOException If file read fails
     */
    public void buildIndex(RandomAccessFile file, LongConsumer progressCallback) throws IOException {
        long fileSize = file.length();

        // Estimate initial capacity (assume avg 100 bytes per line)
        int estimatedLines = (int) Math.min(fileSize / 100, Integer.MAX_VALUE - 8);
        LongArrayBuilder builder = new LongArrayBuilder(Math.max(estimatedLines, 1000));

        // Line 0 always starts at offset 0
        builder.add(0);

        FileChannel channel = file.getChannel();
        ByteBuffer buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024); // 8MB buffer

        long position = 0;
        long lastProgress = 0;

        while (position < fileSize) {
            buffer.clear();
            int bytesRead = channel.read(buffer, position);
            if (bytesRead <= 0)
                break;

            buffer.flip();
            for (int i = 0; i < bytesRead; i++) {
                byte b = buffer.get(i);
                if (b == '\n') {
                    // Next line starts at position + i + 1
                    long nextLineStart = position + i + 1;
                    if (nextLineStart < fileSize) {
                        builder.add(nextLineStart);
                    }
                }
            }

            position += bytesRead;

            // Report progress every 10MB
            if (progressCallback != null && position - lastProgress > 10_000_000) {
                progressCallback.accept(position);
                lastProgress = position;
            }
        }

        this.offsets = builder.toArray();
        this.lineCount = offsets.length;

        if (progressCallback != null) {
            progressCallback.accept(fileSize);
        }
    }

    /**
     * Get the byte offset where the specified line starts.
     * 
     * @param lineNumber 0-based line number
     * @return Byte offset in file
     */
    public long getOffset(int lineNumber) {
        if (lineNumber < 0 || lineNumber >= lineCount) {
            throw new IndexOutOfBoundsException("Line: " + lineNumber + ", Total: " + lineCount);
        }
        return offsets[lineNumber];
    }

    /**
     * Get the length of a specific line (bytes until next line or EOF).
     */
    public long getLineLength(int lineNumber, long fileSize) {
        if (lineNumber < 0 || lineNumber >= lineCount) {
            return 0;
        }
        if (lineNumber == lineCount - 1) {
            return fileSize - offsets[lineNumber];
        }
        return offsets[lineNumber + 1] - offsets[lineNumber];
    }

    /**
     * Dynamic array builder for longs (avoids ArrayList<Long> boxing overhead).
     */
    private static class LongArrayBuilder {
        private long[] data;
        private int size;

        LongArrayBuilder(int initialCapacity) {
            this.data = new long[initialCapacity];
            this.size = 0;
        }

        void add(long value) {
            if (size >= data.length) {
                // Grow by 50%
                long[] newData = new long[data.length + (data.length >> 1)];
                System.arraycopy(data, 0, newData, 0, size);
                data = newData;
            }
            data[size++] = value;
        }

        long[] toArray() {
            if (size == data.length) {
                return data;
            }
            long[] result = new long[size];
            System.arraycopy(data, 0, result, 0, size);
            return result;
        }
    }
}
