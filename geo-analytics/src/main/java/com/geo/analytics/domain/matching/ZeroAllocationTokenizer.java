package com.geo.analytics.domain.matching;

import java.lang.StrictMath;
import java.util.Arrays;
import java.util.function.IntConsumer;

public final class ZeroAllocationTokenizer {

    public static final int MAX_BIGRAMS_CAP = 8192;

    private ZeroAllocationTokenizer() {
    }

    public static void forEachBigram(CharSequence text, IntConsumer consumer) {
        int n = text.length();
        if (n < 2) {
            return;
        }
        for (int i = 0; i < n - 1; i++) {
            int c1 = text.charAt(i);
            int c2 = text.charAt(i + 1);
            consumer.accept((c1 << 16) | c2);
        }
    }

    public static void forEachBigram(char[] text, int length, IntConsumer consumer) {
        if (length < 2) {
            return;
        }
        for (int i = 0; i < length - 1; i++) {
            int c1 = text[i];
            int c2 = text[i + 1];
            consumer.accept((c1 << 16) | c2);
        }
    }

    /**
     * Writes up to {@code outCap} packed UTF-16 bigrams from {@code text} (truncation if longer than cap).
     *
     * @return number of bigrams written
     */
    public static int fillPackedBigrams(CharSequence text, int[] out, int outCap) {
        int n = text.length();
        if (n < 2 || outCap <= 0) {
            return 0;
        }
        int limit = StrictMath.min(outCap, n - 1);
        for (int i = 0; i < limit; i++) {
            out[i] = (text.charAt(i) << 16) | (text.charAt(i + 1) & 0xFFFF);
        }
        return limit;
    }

    public static int packedBigramAt(CharSequence hay, int charIndex) {
        return (hay.charAt(charIndex) << 16) | (hay.charAt(charIndex + 1) & 0xFFFF);
    }

    /**
     * Multiset Dice coefficient on packed bigrams. Sorts {@code workA[0:ca)} and {@code workB[0:cb)} in place.
     */
    public static double diceCoefficientSortedDestructive(int[] workA, int ca, int[] workB, int cb) {
        if (ca <= 0 && cb <= 0) {
            return 1.0;
        }
        if (ca <= 0 || cb <= 0) {
            return 0.0;
        }
        Arrays.sort(workA, 0, ca);
        Arrays.sort(workB, 0, cb);
        long inter = 0;
        int i = 0;
        int j = 0;
        while (i < ca && j < cb) {
            int va = workA[i];
            int vb = workB[j];
            if (va < vb) {
                i++;
            } else if (va > vb) {
                j++;
            } else {
                int ia = i;
                while (ia < ca && workA[ia] == va) {
                    ia++;
                }
                int jb = j;
                while (jb < cb && workB[jb] == vb) {
                    jb++;
                }
                inter += StrictMath.min(ia - i, jb - j);
                i = ia;
                j = jb;
            }
        }
        double denom = (double) StrictMath.addExact(ca, cb);
        return StrictMath.fma(2.0 * (double) inter, 1.0 / denom, 0.0);
    }

    /**
     * Fills workspaces then computes multiset Dice. Uses at most {@link #MAX_BIGRAMS_CAP} bigrams per side.
     */
    public static double diceCoefficient(CharSequence a, CharSequence b, int[] workA, int[] workB) {
        int ca = fillPackedBigrams(a, workA, StrictMath.min(workA.length, MAX_BIGRAMS_CAP));
        int cb = fillPackedBigrams(b, workB, StrictMath.min(workB.length, MAX_BIGRAMS_CAP));
        return diceCoefficientSortedDestructive(workA, ca, workB, cb);
    }

    public static void buildKmpFailure(int[] pat, int pLen, int[] failBuf) {
        if (pLen <= 0) {
            return;
        }
        failBuf[0] = 0;
        int len = 0;
        for (int i = 1; i < pLen; i++) {
            while (len > 0 && pat[i] != pat[len]) {
                len = failBuf[len - 1];
            }
            if (pat[i] == pat[len]) {
                len++;
            }
            failBuf[i] = len;
        }
    }

    /**
     * First packed-bigram match of {@code pat} in {@code hay} starting at character index {@code hayStartChar}.
     *
     * @return start char index of match, or -1
     */
    public static int kmpFirstPackedBigramMatch(CharSequence hay, int hayStartChar, int[] pat, int pLen, int[] failBuf) {
        if (pLen <= 0) {
            return hayStartChar <= hay.length() ? hayStartChar : -1;
        }
        int hayLen = hay.length();
        if (hayStartChar > hayLen - 2) {
            return -1;
        }
        buildKmpFailure(pat, pLen, failBuf);
        int pos = hayStartChar;
        int j = 0;
        final int lastStart = hayLen - 2;
        while (pos <= lastStart) {
            if (pat[j] == packedBigramAt(hay, pos)) {
                pos++;
                j++;
                if (j == pLen) {
                    return pos - pLen;
                }
            } else if (j > 0) {
                j = failBuf[j - 1];
            } else {
                pos++;
            }
        }
        return -1;
    }
}
