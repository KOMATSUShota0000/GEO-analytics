package com.geo.analytics.domain.inclusion;

import com.geo.analytics.domain.gatekeeper.DictionaryGatekeeper;
import com.geo.analytics.domain.metrics.EntropyMetricsCalculator;
import com.geo.analytics.domain.semantic.SemanticJudgmentEngine;

public final class InclusionProbabilityCalculator {

    private static final double P_BASE = 0.1d;

    private static final double GAMMA = 0.5d;

    private static final double D_KANA = 0.6d;

    private static final double D_DENOM = 0.4d;

    private static final double P_MIN = 0.0d;

    private static final double P_MAX = 0.25d;

    private static final double TAU_BASE = 0.85d;

    private static final double TAU_NUM = 0.13d;

    private static final double TAU_K = 1.5d;

    private static final double TAU_L0 = 3.5d;

    private static final double HIT = 1.0d;

    private static final double MISS = 0.0d;

    private static final int PREFIX_CAP = 4;

    public double computePib(
            DictionaryGatekeeper.GatekeeperResult gatekeeper,
            EntropyMetricsCalculator.EntropyMetrics entropy,
            SemanticJudgmentEngine.SemanticJudgmentOutcome semantic,
            String brandReferenceNormalized) {
        if (gatekeeper.dictionaryHit()) {
            return HIT;
        }
        if (semantic instanceof SemanticJudgmentEngine.SemanticJudgmentOutcome.Failure) {
            return MISS;
        }
        if (semantic instanceof SemanticJudgmentEngine.SemanticJudgmentOutcome.Success success) {
            SemanticJudgmentEngine.AiBrandMentionResult result = success.result();
            if (!result.brandMentioned()) {
                return MISS;
            }
            double dEntropy = entropy.entropyDensity();
            double numer = StrictMath.fma(-1.0d, D_KANA, dEntropy);
            double ratio = numer / D_DENOM;
            double inner = StrictMath.fma(GAMMA, ratio, 1.0d);
            double pRaw = StrictMath.fma(P_BASE, inner, 0.0d);
            double p = StrictMath.min(P_MAX, StrictMath.max(P_MIN, pRaw));
            String left = gatekeeper.normalizedText();
            String right = brandReferenceNormalized == null ? "" : brandReferenceNormalized;
            double j = jaro(left, right);
            int l = commonPrefixLength(left, right);
            double jw = StrictMath.fma(StrictMath.fma((double) l, p, 0.0d), StrictMath.fma(-1.0d, j, 1.0d), j);
            double lEff = entropy.effectiveLength();
            double expArg = StrictMath.fma(TAU_K, StrictMath.fma(-1.0d, TAU_L0, lEff), 0.0d);
            double expVal = StrictMath.exp(expArg);
            double denomSig = StrictMath.fma(1.0d, expVal, 1.0d);
            double tau = StrictMath.fma(TAU_NUM, 1.0d / denomSig, TAU_BASE);
            double s = StrictMath.fma(result.confidenceScore(), jw, 0.0d);
            if (s > tau) {
                return s;
            }
            return MISS;
        }
        return MISS;
    }

    private static int commonPrefixLength(String a, String b) {
        if (a == null) {
            a = "";
        }
        if (b == null) {
            b = "";
        }
        int n = StrictMath.min(PREFIX_CAP, StrictMath.min(a.length(), b.length()));
        int k = 0;
        while (k < n && a.charAt(k) == b.charAt(k)) {
            k++;
        }
        return k;
    }

    private static double jaro(String s1, String s2) {
        if (s1 == null) {
            s1 = "";
        }
        if (s2 == null) {
            s2 = "";
        }
        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0 && len2 == 0) {
            return 1.0d;
        }
        if (len1 == 0 || len2 == 0) {
            return 0.0d;
        }
        int matchDistance = StrictMath.max(len1, len2) / 2 - 1;
        if (matchDistance < 0) {
            matchDistance = 0;
        }
        boolean[] s1Match = new boolean[len1];
        boolean[] s2Match = new boolean[len2];
        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = StrictMath.max(0, i - matchDistance);
            int end = StrictMath.min(i + matchDistance + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Match[j]) {
                    continue;
                }
                if (s1.charAt(i) != s2.charAt(j)) {
                    continue;
                }
                s1Match[i] = true;
                s2Match[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) {
            return 0.0d;
        }
        int transCount = 0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Match[i]) {
                continue;
            }
            while (k < len2 && !s2Match[k]) {
                k++;
            }
            if (k >= len2) {
                break;
            }
            if (s1.charAt(i) != s2.charAt(k)) {
                transCount++;
            }
            k++;
        }
        double m = (double) matches;
        double halfTrans = StrictMath.fma(0.5d, (double) transCount, 0.0d);
        double term1 = m / (double) len1;
        double term2 = m / (double) len2;
        double term3 = StrictMath.fma(-1.0d, halfTrans, m) / m;
        return StrictMath.fma(term1, 1.0d, StrictMath.fma(term2, 1.0d, term3)) / 3.0d;
    }
}
