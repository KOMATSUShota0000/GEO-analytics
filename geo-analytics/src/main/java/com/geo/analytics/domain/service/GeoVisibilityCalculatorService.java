package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.SomRawMetrics;

public final class GeoVisibilityCalculatorService {
    public static final String CALCULATION_VERSION = "GBVS_V1";
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final double LAMBDA = 0.5;
    private static final double STUFFING_THRESHOLD = 0.03;
    private static final double STUFFING_COEFF = 500.0;

    public GbvsResult compute(SomRawMetrics metrics, double lAvgJob) {
        var f = Math.max(0, metrics.nounCount());
        var L = Math.max(0, metrics.responseTokenLength());
        var lAvgEff = effectiveLavg(L, lAvgJob);
        var bm25 = bm25Core(f, L, lAvgEff);
        var sent = sentimentMultiplier(metrics.sentimentIntensity());
        var density = L > 0 ? (double) f / L : 0.0;
        var penalty = stuffingPenaltyMultiplier(density);
        var raw = bm25 * sent * penalty;
        var theoreticalMax = (K1 + 1.0) * (1.0 + LAMBDA);
        var normalized = raw / theoreticalMax * 100.0;
        var scorePercent = Math.clamp(normalized, 0.0, 100.0);
        return new GbvsResult(scorePercent, visibilityStage(scorePercent));
    }

    private static double effectiveLavg(int L, double lAvgJob) {
        if (lAvgJob > 0.0) {
            return lAvgJob;
        }
        if (L > 0) {
            return L;
        }
        return 1.0;
    }

    private static double bm25Core(int f, int L, double lAvgEff) {
        if (f == 0) {
            return 0.0;
        }
        var normLen = 1.0 - B + B * ((double) L / lAvgEff);
        var den = f + K1 * normLen;
        return f * (K1 + 1.0) / den;
    }

    private static double sentimentMultiplier(double sentimentScore) {
        var s = Math.clamp(sentimentScore, -1.0, 1.0);
        return 1.0 + LAMBDA * Math.tanh(s);
    }

    private static double stuffingPenaltyMultiplier(double density) {
        if (density <= STUFFING_THRESHOLD) {
            return 1.0;
        }
        var d = density - STUFFING_THRESHOLD;
        return Math.max(0.0, 1.0 - STUFFING_COEFF * d * d);
    }

    private static int visibilityStage(double scorePercent) {
        if (scorePercent >= 100.0) {
            return 10;
        }
        var s = Math.clamp(scorePercent, 0.0, 99.999);
        return (int) Math.clamp((int) (s / 10.0) + 1, 1, 10);
    }

    public record GbvsResult(double scorePercent, int visibilityStage) {}
}
