package com.geo.analytics.domain.matching;

import java.lang.StrictMath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * UTF-16 character bigrams as plain strings (length 2). Multiset Dice matches prior packed-int semantics for BMP pairs.
 */
public final class StringBigramTokenizer {

    public static final int MAX_BIGRAMS_CAP = 8192;

    private StringBigramTokenizer() {
    }

    /** Overlapping bigrams from the start of {@code text}, at most {@link #MAX_BIGRAMS_CAP}. */
    public static List<String> bigrams(CharSequence text) {
        int n = text.length();
        if (n < 2) {
            return List.of();
        }
        int limit = StrictMath.min(MAX_BIGRAMS_CAP, n - 1);
        ArrayList<String> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(text.subSequence(i, i + 2).toString());
        }
        return out;
    }

    /**
     * Multiset Dice coefficient on bigram multisets (sorted multiset intersection, same formula as legacy packed-int path).
     */
    public static double diceCoefficient(CharSequence a, CharSequence b) {
        ArrayList<String> workA = new ArrayList<>(bigrams(a));
        ArrayList<String> workB = new ArrayList<>(bigrams(b));
        int ca = workA.size();
        int cb = workB.size();
        if (ca <= 0 && cb <= 0) {
            return 1.0;
        }
        if (ca <= 0 || cb <= 0) {
            return 0.0;
        }
        Collections.sort(workA);
        Collections.sort(workB);
        return diceCoefficientSortedDestructive(workA, ca, workB, cb);
    }

    private static double diceCoefficientSortedDestructive(List<String> workA, int ca, List<String> workB, int cb) {
        long inter = 0;
        int i = 0;
        int j = 0;
        while (i < ca && j < cb) {
            String va = workA.get(i);
            String vb = workB.get(j);
            int cmp = va.compareTo(vb);
            if (cmp < 0) {
                i++;
            } else if (cmp > 0) {
                j++;
            } else {
                int ia = i;
                while (ia < ca && workA.get(ia).equals(va)) {
                    ia++;
                }
                int jb = j;
                while (jb < cb && workB.get(jb).equals(vb)) {
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
}
