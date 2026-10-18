package com.seeloggyplus.shared.util;

import lombok.Getter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.function.LongConsumer;

public class LineOffsetIndex {

    private long[] offsets;

    @Getter
    private int lineCount;

    public LineOffsetIndex() {
        this.offsets = new long[0];
        this.lineCount = 0;
    }

    public void buildIndex(RandomAccessFile file, LongConsumer progressCallback) throws IOException {
        long fileSize = file.length();

        int estimatedLines = (int) Math.min(fileSize / 100, Integer.MAX_VALUE - 8);
        LongArrayBuilder builder = new LongArrayBuilder(Math.max(estimatedLines, 1000));

        builder.add(0);

        FileChannel channel = file.getChannel();
        ByteBuffer buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024);

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
                    long nextLineStart = position + i + 1;
                    if (nextLineStart < fileSize) {
                        builder.add(nextLineStart);
                    }
                }
            }

            position += bytesRead;

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

    public long getOffset(int lineNumber) {
        if (lineNumber < 0 || lineNumber >= lineCount) {
            throw new IndexOutOfBoundsException("Line: " + lineNumber + ", Total: " + lineCount);
        }
        return offsets[lineNumber];
    }

    public long getLineLength(int lineNumber, long fileSize) {
        if (lineNumber < 0 || lineNumber >= lineCount) {
            return 0;
        }
        if (lineNumber == lineCount - 1) {
            return fileSize - offsets[lineNumber];
        }
        return offsets[lineNumber + 1] - offsets[lineNumber];
    }

    private static class LongArrayBuilder {
        private long[] data;
        private int size;

        LongArrayBuilder(int initialCapacity) {
            this.data = new long[initialCapacity];
            this.size = 0;
        }

        void add(long value) {
            if (size >= data.length) {
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
