package com.geo.analytics.domain.matching;

import java.util.function.IntConsumer;

public final class ZeroAllocationTokenizer {

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
}
