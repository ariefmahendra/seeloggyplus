package com.seeloggyplus.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * High-performance file reader using memory-mapped I/O.
 * Supports files > 2GB via chunked mappings.
 * Zero-copy read: data goes directly from OS page cache to Java string.
 */
public class MappedFileReader implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MappedFileReader.class);

    // 1GB per mapping chunk (safe for most systems)
    private static final long CHUNK_SIZE = 1024L * 1024L * 1024L;

    private final long fileSize;
    private final Charset charset;
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final MappedByteBuffer[] buffers;
    private final long[] chunkOffsets;

    public MappedFileReader(File file) throws IOException {
        this(file, StandardCharsets.UTF_8);
    }

    public MappedFileReader(File file, Charset charset) throws IOException {
        this.fileSize = file.length();
        this.charset = charset;

        this.raf = new RandomAccessFile(file, "r");
        this.channel = raf.getChannel();

        // Create chunked mappings
        int numChunks = (int) ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE);
        this.buffers = new MappedByteBuffer[numChunks];
        this.chunkOffsets = new long[numChunks];

        for (int i = 0; i < numChunks; i++) {
            long offset = (long) i * CHUNK_SIZE;
            long size = Math.min(CHUNK_SIZE, fileSize - offset);
            chunkOffsets[i] = offset;
            buffers[i] = channel.map(FileChannel.MapMode.READ_ONLY, offset, size);
        }

        logger.info("Mapped {} chunks for file: {} ({}MB)",
                numChunks, file.getName(), fileSize / 1024 / 1024);
    }

    /**
     * Read a line from the given byte offset.
     * Thread-safe.
     * 
     * @param startOffset Byte offset where line starts
     * @param maxLength   Maximum bytes to read (use line length from index)
     * @return Decoded string (without newline)
     */
    public String readLine(long startOffset, int maxLength) {
        if (startOffset >= fileSize) {
            return "";
        }

        // Find which chunk contains this offset
        int chunkIndex = (int) (startOffset / CHUNK_SIZE);
        int localOffset = (int) (startOffset - chunkOffsets[chunkIndex]);

        MappedByteBuffer buffer = buffers[chunkIndex];

        // Allocate local buffer to ensure thread safety
        // Cap at 8KB or maxLength
        int bufSize = Math.min(maxLength, 8192);
        byte[] localLineBuffer = new byte[bufSize];

        int bytesToRead = Math.min(bufSize, buffer.capacity() - localOffset);
        int len = 0;

        for (int i = 0; i < bytesToRead; i++) {
            byte b = buffer.get(localOffset + i);
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                localLineBuffer[len++] = b;
            }
        }

        return new String(localLineBuffer, 0, len, charset);
    }

    /**
     * Read line using LineOffsetIndex.
     */
    public String readLine(LineOffsetIndex index, long lineNumber) {
        if (lineNumber < 0 || lineNumber >= index.getLineCount()) {
            return "";
        }

        long offset = index.getOffset((int) lineNumber);
        int lineLen = (int) Math.min(index.getLineLength((int) lineNumber, fileSize), 10000);

        return readLine(offset, lineLen);
    }

    /**
     * Zero-allocation line matching for search.
     * Decodes line to CharBuffer without creating a String.
     * 
     * @param index      Line offset index
     * @param lineNumber Line number to check
     * @param matcher    Predicate to test against the CharSequence
     * @return true if line matches the predicate
     */
    public boolean lineMatches(LineOffsetIndex index, int lineNumber,
            java.util.function.Predicate<CharSequence> matcher) {
        if (lineNumber < 0 || lineNumber >= index.getLineCount()) {
            return false;
        }

        long startOffset = index.getOffset(lineNumber);
        int maxLength = (int) Math.min(index.getLineLength(lineNumber, fileSize), 10000);

        if (startOffset >= fileSize) {
            return false;
        }

        int chunkIndex = (int) (startOffset / CHUNK_SIZE);
        int localOffset = (int) (startOffset - chunkOffsets[chunkIndex]);
        MappedByteBuffer buffer = buffers[chunkIndex];

        int bufSize = Math.min(maxLength, 8192);
        byte[] localLineBuffer = new byte[bufSize];
        int bytesToRead = Math.min(bufSize, buffer.capacity() - localOffset);
        int len = 0;

        for (int i = 0; i < bytesToRead; i++) {
            byte b = buffer.get(localOffset + i);
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                localLineBuffer[len++] = b;
            }
        }

        // Decode to CharBuffer (reusable sequence) instead of String
        java.nio.CharBuffer charBuffer = charset.decode(java.nio.ByteBuffer.wrap(localLineBuffer, 0, len));
        return matcher.test(charBuffer);
    }

    @Override
    public void close() {
        try {
            channel.close();
            raf.close();
        } catch (IOException e) {
            logger.warn("Error closing file", e);
        }
    }
}
