package com.geo.analytics.domain.trie;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class OffHeapDoubleArrayTrie implements AutoCloseable {

    private static final int EOS = 0x110000;

    private final Arena arena;

    private final MemorySegment storage;

    private final int tableSize;

    private final int[] alphabet;

    private final int eosSymbolIndex;

    private final AtomicBoolean closed;

    private final AtomicInteger activeReads;

    private OffHeapDoubleArrayTrie(
            Arena arena,
            MemorySegment storage,
            int tableSize,
            int[] alphabet,
            int eosSymbolIndex,
            AtomicBoolean closed,
            AtomicInteger activeReads) {
        this.arena = arena;
        this.storage = storage;
        this.tableSize = tableSize;
        this.alphabet = alphabet;
        this.eosSymbolIndex = eosSymbolIndex;
        this.closed = closed;
        this.activeReads = activeReads;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        while (activeReads.get() != 0) {
            Thread.onSpinWait();
        }
        arena.close();
    }

    public boolean contains(String text) {
        if (text == null) {
            return false;
        }
        if (closed.get()) {
            return false;
        }
        activeReads.incrementAndGet();
        try {
            if (closed.get()) {
                return false;
            }
            int state = 0;
            for (int i = 0, len = text.length(); i < len; ) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                int sym = symbolIndex(cp);
                if (sym < 0) {
                    return false;
                }
                state = transition(state, sym);
                if (state < 0) {
                    return false;
                }
            }
            state = transition(state, eosSymbolIndex);
            if (state < 0) {
                return false;
            }
            return readBase(state) < 0;
        } finally {
            activeReads.decrementAndGet();
        }
    }

    private int transition(int state, int sym) {
        int b = readBase(state);
        if (b < 0) {
            return -1;
        }
        long tLong = (long) b + (long) sym;
        if (tLong < 0L || tLong >= (long) tableSize) {
            return -1;
        }
        int t = (int) tLong;
        if (readCheck(t) != state) {
            return -1;
        }
        return t;
    }

    private int symbolIndex(int cp) {
        int lo = 0;
        int hi = alphabet.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int v = alphabet[mid];
            if (v < cp) {
                lo = mid + 1;
            } else if (v > cp) {
                hi = mid - 1;
            } else {
                return mid + 1;
            }
        }
        return -1;
    }

    private int readBase(int index) {
        return storage.get(ValueLayout.JAVA_INT, (long) index * (long) Integer.BYTES);
    }

    private int readCheck(int index) {
        return storage.get(
                ValueLayout.JAVA_INT,
                (long) tableSize * (long) Integer.BYTES + (long) index * (long) Integer.BYTES);
    }

    public static final class Builder {

        public OffHeapDoubleArrayTrie build(List<String> dictionary) {
            if (dictionary == null) {
                throw new IllegalArgumentException();
            }
            TrieNode root = new TrieNode();
            TreeSet<Integer> symbols = new TreeSet<>();
            symbols.add(EOS);
            for (int i = 0; i < dictionary.size(); i++) {
                String s = dictionary.get(i);
                if (s == null) {
                    throw new IllegalArgumentException();
                }
                TrieNode cur = root;
                for (int p = 0, sl = s.length(); p < sl; ) {
                    int cp = s.codePointAt(p);
                    p += Character.charCount(cp);
                    symbols.add(cp);
                    cur = cur.next.computeIfAbsent(cp, k -> new TrieNode());
                }
                cur.next.computeIfAbsent(EOS, k -> new TrieNode());
            }
            int[] alphabet = new int[symbols.size()];
            int w = 0;
            for (Integer v : symbols) {
                alphabet[w++] = v;
            }
            int eosSymbolIndex = -1;
            for (int i = 0; i < alphabet.length; i++) {
                if (alphabet[i] == EOS) {
                    eosSymbolIndex = i + 1;
                    break;
                }
            }
            if (eosSymbolIndex < 0) {
                throw new IllegalStateException();
            }
            IntTable table = new IntTable(16);
            int[] high = new int[]{0};
            assign(0, root, table, alphabet, high);
            int used = high[0] + 1;
            if (used <= 0) {
                used = 1;
            }
            Arena arena = Arena.ofShared();
            long bytes = (long) used * 2L * (long) Integer.BYTES;
            MemorySegment segment = arena.allocate(bytes, ValueLayout.JAVA_INT.byteAlignment());
            long half = (long) used * (long) Integer.BYTES;
            for (int i = 0; i < used; i++) {
                int bv = i < table.base.length ? table.base[i] : 0;
                int cv = i < table.check.length ? table.check[i] : -1;
                segment.set(ValueLayout.JAVA_INT, (long) i * (long) Integer.BYTES, bv);
                segment.set(ValueLayout.JAVA_INT, half + (long) i * (long) Integer.BYTES, cv);
            }
            AtomicBoolean closed = new AtomicBoolean(false);
            AtomicInteger activeReads = new AtomicInteger(0);
            return new OffHeapDoubleArrayTrie(arena, segment, used, alphabet, eosSymbolIndex, closed, activeReads);
        }

        private static void assign(int state, TrieNode node, IntTable table, int[] alphabet, int[] high) {
            if (node.next.isEmpty()) {
                table.base[state] = -1;
                updateHigh(high, state);
                return;
            }
            int childCount = node.next.size();
            int[] syms = new int[childCount];
            int idx = 0;
            for (Map.Entry<Integer, TrieNode> e : node.next.entrySet()) {
                syms[idx++] = symbolIndexForAlphabet(e.getKey(), alphabet);
            }
            int b = locateBase(table, high, syms);
            table.base[state] = b;
            updateHigh(high, state);
            for (Map.Entry<Integer, TrieNode> e : node.next.entrySet()) {
                int sym = symbolIndexForAlphabet(e.getKey(), alphabet);
                long tLong = (long) b + (long) sym;
                if (tLong < 0L || tLong > (long) Integer.MAX_VALUE) {
                    throw new IllegalStateException();
                }
                int t = (int) tLong;
                table.ensure(t);
                if (table.check[t] != -1) {
                    throw new IllegalStateException();
                }
                table.check[t] = state;
                updateHigh(high, t);
                assign(t, e.getValue(), table, alphabet, high);
            }
        }

        private static int symbolIndexForAlphabet(int cp, int[] alphabet) {
            int lo = 0;
            int hi = alphabet.length - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int v = alphabet[mid];
                if (v < cp) {
                    lo = mid + 1;
                } else if (v > cp) {
                    hi = mid - 1;
                } else {
                    return mid + 1;
                }
            }
            throw new IllegalStateException();
        }

        private static int locateBase(IntTable table, int[] high, int[] syms) {
            int cand = 1;
            for (;;) {
                boolean ok = true;
                for (int i = 0; i < syms.length; i++) {
                    int sym = syms[i];
                    long pLong = (long) cand + (long) sym;
                    if (pLong < 0L || pLong > (long) Integer.MAX_VALUE) {
                        ok = false;
                        break;
                    }
                    int p = (int) pLong;
                    table.ensure(p);
                    if (table.check[p] != -1) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return cand;
                }
                cand++;
                if (cand == 0) {
                    throw new IllegalStateException();
                }
            }
        }

        private static void updateHigh(int[] high, int index) {
            if (index > high[0]) {
                high[0] = index;
            }
        }
    }

    private static final class IntTable {

        private int[] base;

        private int[] check;

        private IntTable(int initial) {
            base = new int[initial];
            check = new int[initial];
            java.util.Arrays.fill(check, -1);
        }

        private void ensure(int minIndex) {
            if (minIndex < base.length) {
                return;
            }
            int need = minIndex + 1;
            int newLen = base.length;
            while (newLen < need) {
                long doubled = (long) newLen * 2L;
                if (doubled > (long) Integer.MAX_VALUE - 8) {
                    newLen = Integer.MAX_VALUE - 8;
                    break;
                }
                newLen = (int) doubled;
            }
            if (newLen < need) {
                newLen = need;
            }
            int[] nb = new int[newLen];
            int[] nc = new int[newLen];
            System.arraycopy(base, 0, nb, 0, base.length);
            System.arraycopy(check, 0, nc, 0, check.length);
            java.util.Arrays.fill(nc, check.length, newLen, -1);
            base = nb;
            check = nc;
        }
    }

    private static final class TrieNode {

        private final TreeMap<Integer, TrieNode> next = new TreeMap<>();
    }
}
