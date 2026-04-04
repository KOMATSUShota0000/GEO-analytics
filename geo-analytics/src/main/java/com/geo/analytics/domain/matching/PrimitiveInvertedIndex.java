package com.geo.analytics.domain.matching;

import java.util.Arrays;
import java.util.function.IntConsumer;

public final class PrimitiveInvertedIndex {

    private int[] pairKeys;
    private int[] pairDocs;
    private int pairCount;
    private int pairCap;

    private int tableCap;
    private int tableMask;
    private int[] slotKeys;
    private int[] slotRows;
    private byte[] slotState;

    private int[] offsets;
    private int[] postings;
    private int rowCount;
    private boolean sealed;

    public PrimitiveInvertedIndex(int initialPairCapacityPow2) {
        if (initialPairCapacityPow2 <= 0 || (initialPairCapacityPow2 & (initialPairCapacityPow2 - 1)) != 0) {
            throw new IllegalArgumentException();
        }
        pairCap = initialPairCapacityPow2;
        pairKeys = new int[pairCap];
        pairDocs = new int[pairCap];
        pairCount = 0;
        tableCap = 0;
        tableMask = 0;
        slotKeys = PrimitiveInvertedIndex.EMPTY_INT;
        slotRows = PrimitiveInvertedIndex.EMPTY_INT;
        slotState = PrimitiveInvertedIndex.EMPTY_BYTE;
        offsets = PrimitiveInvertedIndex.EMPTY_INT;
        postings = PrimitiveInvertedIndex.EMPTY_INT;
        rowCount = 0;
        sealed = false;
    }

    private static final int[] EMPTY_INT = new int[0];
    private static final byte[] EMPTY_BYTE = new byte[0];

    public void addPosting(int key, int docId) {
        if (sealed) {
            throw new IllegalStateException();
        }
        if (pairCount == pairCap) {
            int ncap = pairCap << 1;
            pairKeys = Arrays.copyOf(pairKeys, ncap);
            pairDocs = Arrays.copyOf(pairDocs, ncap);
            pairCap = ncap;
        }
        pairKeys[pairCount] = key;
        pairDocs[pairCount] = docId;
        pairCount++;
    }

    public void seal() {
        if (sealed) {
            return;
        }
        sealed = true;
        if (pairCount == 0) {
            rowCount = 0;
            offsets = new int[1];
            postings = PrimitiveInvertedIndex.EMPTY_INT;
            tableCap = 8;
            tableMask = tableCap - 1;
            slotKeys = new int[tableCap];
            slotRows = new int[tableCap];
            slotState = new byte[tableCap];
            return;
        }
        long[] pack = new long[pairCount];
        for (int i = 0; i < pairCount; i++) {
            pack[i] = ((long) pairKeys[i] << 32) | (pairDocs[i] & 0xFFFFFFFFL);
        }
        Arrays.sort(pack);
        int distinct = 1;
        for (int i = 1; i < pairCount; i++) {
            int pk = (int) (pack[i] >>> 32);
            int pkm = (int) (pack[i - 1] >>> 32);
            if (pk != pkm) {
                distinct++;
            }
        }
        rowCount = distinct;
        offsets = new int[rowCount + 1];
        postings = new int[pairCount];
        offsets[0] = 0;
        int row = 0;
        int w = 0;
        int prevKey = (int) (pack[0] >>> 32);
        for (int i = 0; i < pairCount; i++) {
            int k = (int) (pack[i] >>> 32);
            int d = (int) pack[i];
            if (i > 0 && k != prevKey) {
                row++;
                offsets[row] = w;
                prevKey = k;
            }
            postings[w++] = d;
        }
        offsets[rowCount] = w;
        int tc = 8;
        while (tc < rowCount * 2) {
            tc <<= 1;
        }
        tableCap = tc;
        tableMask = tableCap - 1;
        slotKeys = new int[tableCap];
        slotRows = new int[tableCap];
        slotState = new byte[tableCap];
        row = 0;
        prevKey = (int) (pack[0] >>> 32);
        insertSlot(prevKey, 0);
        for (int i = 1; i < pairCount; i++) {
            int k = (int) (pack[i] >>> 32);
            if (k != prevKey) {
                row++;
                prevKey = k;
                insertSlot(k, row);
            }
        }
    }

    private void insertSlot(int key, int rowIndex) {
        int idx = hashIndex(key) & tableMask;
        for (; ; ) {
            byte st = slotState[idx];
            if (st == 0) {
                slotKeys[idx] = key;
                slotRows[idx] = rowIndex;
                slotState[idx] = 1;
                return;
            }
            if (st == 1 && slotKeys[idx] == key) {
                return;
            }
            idx = (idx + 1) & tableMask;
        }
    }

    private static int fmix32(int k) {
        int x = k;
        x ^= x >>> 16;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        x *= 0xC2B2AE35;
        x ^= x >>> 16;
        return x;
    }

    private static int hashIndex(int key) {
        return fmix32(key);
    }

    public int lookupRow(int key) {
        if (!sealed) {
            throw new IllegalStateException();
        }
        if (rowCount == 0) {
            return -1;
        }
        int idx = hashIndex(key) & tableMask;
        for (; ; ) {
            byte st = slotState[idx];
            if (st == 0) {
                return -1;
            }
            if (st == 1) {
                if (slotKeys[idx] == key) {
                    return slotRows[idx];
                }
            }
            idx = (idx + 1) & tableMask;
        }
    }

    public void forEachPosting(int key, IntConsumer consumer) {
        int r = lookupRow(key);
        if (r < 0) {
            return;
        }
        int a = offsets[r];
        int b = offsets[r + 1];
        for (int i = a; i < b; i++) {
            consumer.accept(postings[i]);
        }
    }

    public int postingRangeStart(int key) {
        int r = lookupRow(key);
        if (r < 0) {
            return -1;
        }
        return offsets[r];
    }

    public int postingRangeEnd(int key) {
        int r = lookupRow(key);
        if (r < 0) {
            return -1;
        }
        return offsets[r + 1];
    }

    public int[] postingsArray() {
        return postings;
    }

    public int[] offsetsArray() {
        return offsets;
    }

    public int[] slotKeysArray() {
        return slotKeys;
    }

    public int rowCount() {
        return rowCount;
    }

    public int pairCount() {
        return pairCount;
    }

    public boolean isSealed() {
        return sealed;
    }
}
