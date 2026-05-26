package com.geo.analytics.domain.service;

import java.lang.StrictMath;

public final class MeoTrustScoreCalculator {
    private static final int MEO_FULL_REVIEWS = 50;
    private static final double MEO_FULL_STARS = 4.0d;
    private static final double MAX_SCORE = 25.0d;

    private MeoTrustScoreCalculator() {}

    private static double smMul(double a, double b) {
        return StrictMath.fma(a, b, 0.0d);
    }

    private static double smDiv(double a, double b) {
        return StrictMath.fma(a, 1.0d / b, 0.0d);
    }

    private static double smMin(double a, double b) {
        return StrictMath.min(a, b);
    }

    private static double smMax(double a, double b) {
        return StrictMath.max(a, b);
    }

    public static double scoreMeoTrust(int reviewCount, double averageStars) {
        int count = reviewCount > 0 ? reviewCount : 0;
        double stars = 0.0d;
        if (!Double.isNaN(averageStars) && averageStars > 0.0d) {
            stars = smMin(averageStars, 5.0d);
        }
        if (count >= MEO_FULL_REVIEWS && stars >= MEO_FULL_STARS) {
            return MAX_SCORE;
        }
        double rc = smMin(smMax(0.0d, (double) count), (double) MEO_FULL_REVIEWS);
        double countRatio = smDiv(rc, (double) MEO_FULL_REVIEWS);
        double starRatio = smMin(smDiv(stars, MEO_FULL_STARS), 1.0d);
        return smMul(smMul(countRatio, starRatio), MAX_SCORE);
    }
}
