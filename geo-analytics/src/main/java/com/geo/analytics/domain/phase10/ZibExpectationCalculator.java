package com.geo.analytics.domain.phase10;

public final class ZibExpectationCalculator {

    private ZibExpectationCalculator() {
    }

    public static double expectedShareOfModel(double sumOfValidScores, long nPlanned, double adaptiveK) {
        if (!Double.isFinite(sumOfValidScores) || !Double.isFinite(adaptiveK)) {
            throw new IllegalArgumentException();
        }
        if (nPlanned < 0L) {
            throw new IllegalArgumentException();
        }
        if (sumOfValidScores < 0.0d) {
            throw new IllegalArgumentException();
        }
        if (adaptiveK < 0.0d) {
            throw new IllegalArgumentException();
        }
        double num = StrictMath.fma(adaptiveK, BayesianPriorCalculator.ALPHA, sumOfValidScores);
        double den = StrictMath.fma(adaptiveK, BayesianPriorCalculator.TOTAL_WEIGHT, (double) nPlanned);
        if (den == 0.0d) {
            return 0.0d;
        }
        return num / den;
    }
}
