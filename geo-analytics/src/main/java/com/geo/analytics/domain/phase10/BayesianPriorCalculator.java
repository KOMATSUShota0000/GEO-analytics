package com.geo.analytics.domain.phase10;

public final class BayesianPriorCalculator {

    private BayesianPriorCalculator() {
    }

    public static final double ALPHA = 1.0d / 3.0d;
    public static final double BETA = 1.0d / 3.0d;
    public static final double TOTAL_WEIGHT = ALPHA + BETA;

    public static double adaptiveK(double kBase, double lambda, double progress) {
        if (!Double.isFinite(kBase) || !Double.isFinite(lambda) || !Double.isFinite(progress)) {
            throw new IllegalArgumentException();
        }
        if (kBase <= 0.0d) {
            throw new IllegalArgumentException();
        }
        if (lambda < 0.0d) {
            throw new IllegalArgumentException();
        }
        double clampedProgress = Math.max(0.0d, Math.min(1.0d, progress));
        return kBase * StrictMath.exp(-lambda * clampedProgress);
    }
}
