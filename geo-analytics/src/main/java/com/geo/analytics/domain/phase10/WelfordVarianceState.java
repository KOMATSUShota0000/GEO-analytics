package com.geo.analytics.domain.phase10;

public record WelfordVarianceState(long count, double mean, double m2) {

    public static WelfordVarianceState empty() {
        return new WelfordVarianceState(0L, 0.0d, 0.0d);
    }

    public double populationVariance() {
        if (count > 0L) {
            return StrictMath.max(0.0d, m2 / count);
        }
        return 0.0d;
    }

    public double sampleVariance() {
        if (count > 1L) {
            return StrictMath.max(0.0d, m2 / (count - 1L));
        }
        return 0.0d;
    }

    public double populationStandardDeviation() {
        return StrictMath.sqrt(populationVariance());
    }

    public double sampleStandardDeviation() {
        return StrictMath.sqrt(sampleVariance());
    }
}
