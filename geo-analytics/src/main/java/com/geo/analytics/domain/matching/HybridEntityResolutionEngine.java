package com.geo.analytics.domain.matching;

import java.lang.StrictMath;

public final class HybridEntityResolutionEngine {

    public static final double JW_PREFIX_BOOST = 0.1;
    public static final int JW_PREFIX_LENGTH_CAP = 4;
    private static final int OUTPUT_BANK_SCALE = 10;

    private HybridEntityResolutionEngine() {
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

    /**
     * Lexical-only score after Sudachi-backed normalization surfaces: Bigram multiset Dice plus Winkler-style prefix boost,
     * then Bayesian smoothing.
     */
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
        double raw = lexicalBigramDiceWinklerStyle(jwQ, jwC);
        raw = RobustAuditMathUtil.softwareFtzFlush(raw);
        return bayesianSmoothedScore(raw, n, c, muPrior);
    }

    /**
     * Lexical similarity via bigram multiset Dice, plus a Winkler-style prefix boost (no per-call boolean[]/char[] match matrices).
     */
    public static double jaroWinkler(CharSequence s1, CharSequence s2) {
        return lexicalBigramDiceWinklerStyle(s1, s2);
    }

    private static double lexicalBigramDiceWinklerStyle(CharSequence s1, CharSequence s2) {
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
        double dice = StringBigramTokenizer.diceCoefficient(s1, s2);
        int prefix = commonPrefixLength(s1, s2, len1, len2, JW_PREFIX_LENGTH_CAP);
        double boost = StrictMath.fma(StrictMath.fma(JW_PREFIX_BOOST, (double) prefix, 0.0), 1.0 - dice, dice);
        double clamped = StrictMath.min(1.0, StrictMath.max(0.0, boost));
        return RobustAuditMathUtil.bankRoundHalfEven(RobustAuditMathUtil.softwareFtzFlush(clamped), OUTPUT_BANK_SCALE);
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
