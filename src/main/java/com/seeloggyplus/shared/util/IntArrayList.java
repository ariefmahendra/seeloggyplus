package com.seeloggyplus.shared.util;

import java.util.ArrayList;
import java.util.List;

public class IntArrayList {
    private static final int BLOCK_SIZE = 4096;
    private final List<int[]> blocks = new ArrayList<>();
    private final List<Integer> starts = new ArrayList<>();
    private int size;
    private int tailUsed;

    public IntArrayList() { }

    public IntArrayList(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException("Illegal Capacity: " + initialCapacity);
    }

    public void add(int value) {
        if (!blocks.isEmpty() && tailUsed == blocks.getLast().length && tailUsed < BLOCK_SIZE) {
            blocks.set(blocks.size() - 1, java.util.Arrays.copyOf(blocks.getLast(), Math.min(BLOCK_SIZE, tailUsed * 2)));
        }
        if (blocks.isEmpty() || tailUsed == blocks.getLast().length) {
            blocks.add(new int[64]);
            starts.add(size);
            tailUsed = 0;
        }
        blocks.getLast()[tailUsed++] = value;
        size++;
    }

    public void moveAllFrom(IntArrayList source) {
        if (source == this) throw new IllegalArgumentException("Cannot move a list into itself");
        if (source.isEmpty()) return;
        int offset = size;
        for (int i = 0; i < source.blocks.size(); i++) {
            blocks.add(source.blocks.get(i));
            starts.add(offset + source.starts.get(i));
        }
        size += source.size;
        tailUsed = source.tailUsed;
        source.clear();
    }

    public int get(int index) {
        int block = block(index);
        return blocks.get(block)[index - starts.get(block)];
    }

    public void set(int index, int value) {
        int block = block(index);
        blocks.get(block)[index - starts.get(block)] = value;
    }

    public void removeLast() {
        if (size == 0) return;
        size--;
        tailUsed--;
        if (tailUsed == 0) {
            blocks.removeLast(); starts.removeLast();
            tailUsed = blocks.isEmpty() ? 0 : size - starts.getLast();
        }
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public void clear() { blocks.clear(); starts.clear(); size = 0; tailUsed = 0; }

    public int indexOf(int value) {
        for (int b = 0; b < blocks.size(); b++) {
            int count = b == blocks.size() - 1 ? tailUsed : starts.get(b + 1) - starts.get(b);
            int[] values = blocks.get(b);
            for (int i = 0; i < count; i++) if (values[i] == value) return starts.get(b) + i;
        }
        return -1;
    }

    private int block(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        int low = 0, high = starts.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (starts.get(middle) <= index) low = middle; else high = middle - 1;
        }
        return low;
    }
}
