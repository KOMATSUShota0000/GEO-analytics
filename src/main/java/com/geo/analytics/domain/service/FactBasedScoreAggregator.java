package com.geo.analytics.domain.service;

import java.lang.StrictMath;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FactBasedScoreAggregator {
    private FactBasedScoreAggregator() {}

    private static double smAdd(double a, double b) {
        return StrictMath.fma(1.0d, a, b);
    }

    public static double aggregate(double aiScore, double meoScore, double mrScore) {
        double partial = StrictMath.fma(1.0d, meoScore, mrScore);
        double sum = smAdd(aiScore, partial);
        double clamped = StrictMath.max(0.0d, StrictMath.min(100.0d, sum));
        return BigDecimal.valueOf(clamped).setScale(1, RoundingMode.HALF_EVEN).doubleValue();
    }
}
