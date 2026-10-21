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

    private static final ThreadLocal<byte[]> READ_BUFFER = ThreadLocal.withInitial(() -> new byte[4096]);

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
        byte[] lineBuffer = buffer(cap);
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

    private static byte[] buffer(int capacity) {
        byte[] bytes = READ_BUFFER.get();
        if (bytes.length < capacity) {
            bytes = new byte[Math.min(MAX_LINE_BYTES, Math.max(capacity, bytes.length * 2))];
            READ_BUFFER.set(bytes);
        }
        return bytes;
    }

    public boolean lineContainsLiteral(LineOffsetIndex index, int lineNumber, String term, boolean caseSensitive) {
        if (lineNumber < 0 || lineNumber >= index.getLineCount()) return false;
        long offset = index.getOffset(lineNumber);
        int capacity = (int) Math.min(index.getLineLength(lineNumber, fileSize), MAX_LINE_BYTES);
        byte[] bytes = buffer(capacity);
        int length = fillLineBytes(offset, bytes, capacity);
        boolean asciiTerm = charset.equals(StandardCharsets.UTF_8);
        for (int i = 0; asciiTerm && i < term.length(); i++) asciiTerm = term.charAt(i) < 128;
        String language = java.util.Locale.getDefault().getLanguage();
        if (!asciiTerm || (!caseSensitive && (language.equals("tr") || language.equals("az")))) {
            return decodedContains(bytes, length, term, caseSensitive);
        }
        if (term.isEmpty()) return true;
        int first = term.charAt(0);
        if (!caseSensitive && first >= 'A' && first <= 'Z') first += 32;
        boolean unicode = false;
        int limit = length - term.length();
        for (int start = 0; start < length; start++) {
            int actual = bytes[start];
            if (actual < 0) {unicode = true; continue;}
            if (!caseSensitive && actual >= 'A' && actual <= 'Z') actual += 32;
            if (actual != first || start > limit) continue;
            int at = 1;
            while (at < term.length()) {
                actual = bytes[start + at];
                int expected = term.charAt(at);
                if (!caseSensitive) {
                    if (actual >= 'A' && actual <= 'Z') actual += 32;
                    if (expected >= 'A' && expected <= 'Z') expected += 32;
                }
                if (actual != expected) break;
                at++;
            }
            if (at == term.length()) return true;
        }
        return unicode && !caseSensitive && decodedContains(bytes, length, term, false);
    }

    private boolean decodedContains(byte[] bytes, int length, String term, boolean caseSensitive) {
        String line = new String(bytes, 0, length, charset);
        return caseSensitive ? line.contains(term) : line.toLowerCase().contains(term.toLowerCase());
    }

    private int fillLineBytes(long startOffset, byte[] target, int maxBytes) {
        int written = 0;
        long position = startOffset;
        while (written < maxBytes && position < fileSize) {
            int chunkIndex = (int) (position / chunkSize);
            int localOffset = (int) (position - chunkOffsets[chunkIndex]);
            MappedByteBuffer buffer = buffers[chunkIndex];
            int count = Math.min(buffer.capacity() - localOffset, maxBytes - written);
            if (count <= 0) break;
            int begin = written;
            buffer.get(localOffset, target, begin, count);
            for (int i = begin; i < begin + count; i++) {
                byte value = target[i];
                if (value == '\n') return written;
                if (value != '\r') target[written++] = value;
            }
            position += count;
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
