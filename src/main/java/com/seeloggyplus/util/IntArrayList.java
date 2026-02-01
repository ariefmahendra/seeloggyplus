package com.seeloggyplus.util;

import java.util.Arrays;

/**
 * A simple, high-performance, resizeable array of primitive ints.
 * Designed to avoid the boxing overhead of ArrayList<Integer>.
 */
public class IntArrayList {
    private int[] data;
    private int size;
    private static final int DEFAULT_CAPACITY = 10;

    public IntArrayList() {
        this(DEFAULT_CAPACITY);
    }

    public IntArrayList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal Capacity: " + initialCapacity);
        }
        this.data = new int[initialCapacity];
        this.size = 0;
    }

    public void add(int element) {
        ensureCapacity(size + 1);
        data[size++] = element;
    }

    public int get(int index) {
        rangeCheck(index);
        return data[index];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        size = 0;
    }

    public int indexOf(int value) {
        for (int i = 0; i < size; i++) {
            if (data[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity - data.length > 0) {
            grow(minCapacity);
        }
    }

    private void grow(int minCapacity) {
        int oldCapacity = data.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }
        data = Arrays.copyOf(data, newCapacity);
    }

    private void rangeCheck(int index) {
        if (index >= size || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }
}
