package com.geo.analytics.domain.phase10;

public final class ZScorePurgeFilter {

    private static final double EPSILON = 1.0e-9d;

    private static final double IQR_DIVISOR = 1.3489d;

    private static final double Z_PRIME_PURGE_THRESHOLD = 3.5d;

    private static final long MIN_COUNT_FOR_PURGE = 3L;

    private ZScorePurgeFilter() {
    }

    public static boolean shouldPurge(PSquareQuantileState quantileState, double slabScore) {
        if (Double.isNaN(slabScore) || Double.isInfinite(slabScore)) {
            return true;
        }
        if (quantileState.count() < MIN_COUNT_FOR_PURGE) {
            return false;
        }
        double numerator = StrictMath.abs(slabScore - quantileState.median());
        double denominator = StrictMath.fma(quantileState.iqr(), 1.0d / IQR_DIVISOR, EPSILON);
        return (numerator / denominator) > Z_PRIME_PURGE_THRESHOLD;
    }
}
