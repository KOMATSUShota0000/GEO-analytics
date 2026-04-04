package com.geo.analytics.domain.matching;

import java.lang.StrictMath;

public final class HybridEntityResolutionEngine {

    public static final double WEIGHT_PHONETIC = 0.4;
    public static final double WEIGHT_JARO_WINKLER = 0.6;
    public static final double JW_PREFIX_BOOST = 0.1;
    public static final int JW_PREFIX_LENGTH_CAP = 4;
    private static final int OUTPUT_BANK_SCALE = 10;

    private HybridEntityResolutionEngine() {
    }

    public static double rawHybridScore(int packedPhoneticA, int packedPhoneticB, CharSequence query, CharSequence candidate) {
        double ph = PhoneticBitComparator.score(packedPhoneticA, packedPhoneticB);
        double jw = jaroWinkler(query, candidate);
        double raw = StrictMath.fma(WEIGHT_JARO_WINKLER, jw, StrictMath.fma(WEIGHT_PHONETIC, ph, 0.0));
        return RobustAuditMathUtil.bankRoundHalfEven(RobustAuditMathUtil.softwareFtzFlush(raw), OUTPUT_BANK_SCALE);
    }

    public static double rawHybridScore(double phoneticScore, CharSequence query, CharSequence candidate) {
        double ph = RobustAuditMathUtil.softwareFtzFlush(phoneticScore);
        double jw = jaroWinkler(query, candidate);
        double raw = StrictMath.fma(WEIGHT_JARO_WINKLER, jw, StrictMath.fma(WEIGHT_PHONETIC, ph, 0.0));
        return RobustAuditMathUtil.bankRoundHalfEven(RobustAuditMathUtil.softwareFtzFlush(raw), OUTPUT_BANK_SCALE);
    }

    public static double bayesianSmoothedScore(double rawScore, double n, double c, double muPrior) {
        double sum = StrictMath.fma(n, 1.0, c);
        if (StrictMath.abs(sum) < RobustAuditMathUtil.EPSILON) {
            return RobustAuditMathUtil.bankRoundHalfEven(RobustAuditMathUtil.softwareFtzFlush(rawScore), OUTPUT_BANK_SCALE);
        }
        double num = StrictMath.fma(n, rawScore, StrictMath.fma(c, muPrior, 0.0));
        double out = num / sum;
        return RobustAuditMathUtil.bankRoundHalfEven(RobustAuditMathUtil.softwareFtzFlush(out), OUTPUT_BANK_SCALE);
    }

    public static double fullScore(int packedPhoneticA, int packedPhoneticB, CharSequence query, CharSequence candidate, double n, double c, double muPrior) {
        double raw = StrictMath.fma(WEIGHT_JARO_WINKLER, jaroWinkler(query, candidate),
                StrictMath.fma(WEIGHT_PHONETIC, PhoneticBitComparator.score(packedPhoneticA, packedPhoneticB), 0.0));
        raw = RobustAuditMathUtil.softwareFtzFlush(raw);
        return bayesianSmoothedScore(raw, n, c, muPrior);
    }

    public static double fullScoreWithNormalization(
            NormalizationLayer layer,
            CharSequence rawQuery,
            CharSequence rawCandidate,
            double n,
            double c,
            double muPrior) {
        String q = rawQuery == null ? "" : rawQuery.toString().strip();
        String cand = rawCandidate == null ? "" : rawCandidate.toString().strip();
        CharSequence jwQ = layer.surfaceForJaroWinkler(q);
        CharSequence jwC = layer.surfaceForJaroWinkler(cand);
        CharSequence phoQ = layer.surfaceForPhoneticEncoding(q);
        CharSequence phoC = layer.surfaceForPhoneticEncoding(cand);
        char[] ra = new char[256];
        char[] rb = new char[256];
        char[] p1 = new char[8];
        char[] p2 = new char[8];
        char[] s1 = new char[8];
        char[] s2 = new char[8];
        int[] m1 = new int[2];
        int[] m2 = new int[2];
        int packQ = PhoneticBitEncoder.encode(phoQ, ra, p1, s1, m1);
        int packC = PhoneticBitEncoder.encode(phoC, rb, p2, s2, m2);
        return fullScore(packQ, packC, jwQ, jwC, n, c, muPrior);
    }

    public static double jaroWinkler(CharSequence s1, CharSequence s2) {
        if (s1 == null || s2 == null) {
            return RobustAuditMathUtil.bankRoundHalfEven(0.0, OUTPUT_BANK_SCALE);
        }
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0 && len2 == 0) {
            return RobustAuditMathUtil.bankRoundHalfEven(1.0, OUTPUT_BANK_SCALE);
        }
        if (len1 == 0 || len2 == 0) {
            return RobustAuditMathUtil.bankRoundHalfEven(0.0, OUTPUT_BANK_SCALE);
        }
        double j = jaroSimilarity(s1, s2, len1, len2);
        int prefix = commonPrefixLength(s1, s2, len1, len2, JW_PREFIX_LENGTH_CAP);
        double boost = StrictMath.fma(StrictMath.fma(JW_PREFIX_BOOST, (double) prefix, 0.0), 1.0 - j, j);
        double clamped = StrictMath.min(1.0, StrictMath.max(0.0, boost));
        return RobustAuditMathUtil.bankRoundHalfEven(RobustAuditMathUtil.softwareFtzFlush(clamped), OUTPUT_BANK_SCALE);
    }

    private static double jaroSimilarity(CharSequence s1, CharSequence s2, int len1, int len2) {
        int searchRange = StrictMath.max(0, StrictMath.max(len1, len2) / 2 - 1);
        boolean[] m1 = new boolean[len1];
        boolean[] m2 = new boolean[len2];
        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = StrictMath.max(0, i - searchRange);
            int end = StrictMath.min(i + searchRange + 1, len2);
            for (int j = start; j < end; j++) {
                if (m2[j]) {
                    continue;
                }
                if (normChar(s1.charAt(i)) == normChar(s2.charAt(j))) {
                    m1[i] = true;
                    m2[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) {
            return 0.0;
        }
        char[] ms1 = new char[matches];
        char[] ms2 = new char[matches];
        int p1 = 0;
        for (int i = 0; i < len1; i++) {
            if (m1[i]) {
                ms1[p1++] = normChar(s1.charAt(i));
            }
        }
        int p2 = 0;
        for (int j = 0; j < len2; j++) {
            if (m2[j]) {
                ms2[p2++] = normChar(s2.charAt(j));
            }
        }
        int diff = 0;
        for (int i = 0; i < matches; i++) {
            if (ms1[i] != ms2[i]) {
                diff++;
            }
        }
        int t = diff / 2;
        double md = (double) matches;
        double term1 = md / (double) len1;
        double term2 = md / (double) len2;
        double term3 = (md - (double) t) / md;
        return StrictMath.fma(1.0 / 3.0, term1, StrictMath.fma(1.0 / 3.0, term2, (1.0 / 3.0) * term3));
    }

    private static int commonPrefixLength(CharSequence s1, CharSequence s2, int len1, int len2, int maxLen) {
        int n = StrictMath.min(StrictMath.min(len1, len2), maxLen);
        int p = 0;
        while (p < n && normChar(s1.charAt(p)) == normChar(s2.charAt(p))) {
            p++;
        }
        return p;
    }

    private static char normChar(char c) {
        if (c >= 'a' && c <= 'z') {
            return (char) (c - 32);
        }
        return c;
    }
}
