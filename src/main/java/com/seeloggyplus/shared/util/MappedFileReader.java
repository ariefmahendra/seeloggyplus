package com.seeloggyplus.shared.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

public class MappedFileReader implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MappedFileReader.class);

    private static final long CHUNK_SIZE = 1024L * 1024L * 1024L;

    public static final int MAX_LINE_BYTES = 256 * 1024;

    private final long fileSize;
    private final Charset charset;
    private final long chunkSize;
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final MappedByteBuffer[] buffers;
    private final long[] chunkOffsets;

    public MappedFileReader(File file) throws IOException {
        this(file, StandardCharsets.UTF_8);
    }

    public MappedFileReader(File file, Charset charset) throws IOException {
        this(file, charset, CHUNK_SIZE);
    }

    MappedFileReader(File file, Charset charset, long chunkSize) throws IOException {
        this.fileSize = file.length();
        this.charset = charset;
        this.chunkSize = chunkSize;

        this.raf = new RandomAccessFile(file, "r");
        this.channel = raf.getChannel();

        int numChunks = (int) ((fileSize + chunkSize - 1) / chunkSize);
        this.buffers = new MappedByteBuffer[numChunks];
        this.chunkOffsets = new long[numChunks];

        for (int i = 0; i < numChunks; i++) {
            long offset = (long) i * chunkSize;
            long size = Math.min(chunkSize, fileSize - offset);
            chunkOffsets[i] = offset;
            buffers[i] = channel.map(FileChannel.MapMode.READ_ONLY, offset, size);
        }

        logger.info("Mapped {} chunks for file: {} ({}MB)",
                numChunks, file.getName(), fileSize / 1024 / 1024);
    }

    public String readLine(long startOffset, int maxLength) {
        if (startOffset < 0 || startOffset >= fileSize || maxLength <= 0) {
            return "";
        }

        int cap = (int) Math.min((long) maxLength, MAX_LINE_BYTES);
        byte[] lineBuffer = new byte[cap];
        int len = fillLineBytes(startOffset, lineBuffer, cap);

        return new String(lineBuffer, 0, len, charset);
    }

    public String readLine(LineOffsetIndex index, long lineNumber) {
        if (lineNumber < 0 || lineNumber >= index.getLineCount()) {
            return "";
        }

        long offset = index.getOffset((int) lineNumber);
        long lineLength = index.getLineLength((int) lineNumber, fileSize);

        return readLine(offset, (int) Math.min(lineLength, MAX_LINE_BYTES));
    }

    public boolean lineMatches(LineOffsetIndex index, int lineNumber,
            Predicate<CharSequence> matcher) {
        if (lineNumber < 0 || lineNumber >= index.getLineCount()) {
            return false;
        }

        return matcher.test(readLine(index, lineNumber));
    }

    private int fillLineBytes(long startOffset, byte[] target, int maxBytes) {
        int written = 0;
        long position = startOffset;

        while (written < maxBytes && position < fileSize) {
            int chunkIndex = (int) (position / chunkSize);
            int localOffset = (int) (position - chunkOffsets[chunkIndex]);
            MappedByteBuffer buffer = buffers[chunkIndex];

            int available = buffer.capacity() - localOffset;
            if (available <= 0) {
                break;
            }

            int scanned = 0;
            boolean endOfLine = false;

            while (scanned < available && written < maxBytes) {
                byte b = buffer.get(localOffset + scanned);
                scanned++;
                if (b == '\n') {
                    endOfLine = true;
                    break;
                }
                if (b != '\r') {
                    target[written++] = b;
                }
            }

            position += scanned;

            if (endOfLine) {
                break;
            }
        }

        return written;
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
