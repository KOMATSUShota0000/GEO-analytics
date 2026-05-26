package com.geo.analytics.domain.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

public final class RobustAuditMathUtil {

    public static final double EPSILON = 1.0E-9;
    public static final double IQR_NORMALIZATION = 1.3489;
    private static final int DEFAULT_BANK_SCALE = 10;

    private RobustAuditMathUtil() {
    }

    public static double softwareFtzFlush(double x) {
        double ax = Math.abs(x);
        if (ax < EPSILON) {
            return Math.copySign(0.0, x);
        }
        return x;
    }

    public static double median(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        int mid = copy.length >>> 1;
        if ((copy.length & 1) == 1) {
            return copy[mid];
        }
        return Math.fma(0.5, copy[mid - 1], 0.5 * copy[mid]);
    }

    public static double quartileSorted(double[] sorted, double p) {
        int n = sorted.length;
        if (n == 0) {
            return 0.0;
        }
        if (n == 1) {
            return sorted[0];
        }
        double pos = Math.fma(n - 1, p, 0.0);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        lo = Math.max(0, Math.min(n - 1, lo));
        hi = Math.max(0, Math.min(n - 1, hi));
        if (lo == hi) {
            return sorted[lo];
        }
        double w = Math.fma(pos, 1.0, -lo);
        return Math.fma(sorted[hi] - sorted[lo], w, sorted[lo]);
    }

    public static double iqr(double[] values) {
        if (values.length < 2) {
            return 0.0;
        }
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        double q1 = quartileSorted(sorted, 0.25);
        double q3 = quartileSorted(sorted, 0.75);
        return Math.fma(q3, 1.0, -q1);
    }

    public static double[] modifiedZScores(double[] sample) {
        int n = sample.length;
        if (n == 0) {
            return new double[0];
        }
        double med = median(sample);
        double iqrVal = iqr(sample);
        if (iqrVal < EPSILON) {
            return new double[n];
        }
        double denom = Math.max(EPSILON, iqrVal / IQR_NORMALIZATION);
        double[] z = new double[n];
        for (int i = 0; i < n; i++) {
            double num = Math.fma(sample[i], 1.0, -med);
            z[i] = bankRoundHalfEvenDefault(softwareFtzFlush(num / denom));
        }
        return z;
    }

    public static double logSigmoidStable(double x) {
        if (x >= 0.0) {
            double ex = Math.exp(-x);
            return bankRoundHalfEvenDefault(-Math.log1p(ex));
        }
        double ex = Math.exp(x);
        return bankRoundHalfEvenDefault(Math.fma(x, 1.0, -Math.log1p(ex)));
    }

    public static double tanhStable(double x) {
        if (x > 20.0) {
            return bankRoundHalfEvenDefault(1.0);
        }
        if (x < -20.0) {
            return bankRoundHalfEvenDefault(-1.0);
        }
        double e2x = Math.exp(Math.fma(2.0, x, 0.0));
        return bankRoundHalfEvenDefault((e2x - 1.0) / (e2x + 1.0));
    }

    public static double bankRoundHalfEven(double value, int scale) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (!Double.isFinite(value)) {
            return Math.copySign(1.0, value);
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_EVEN).doubleValue();
    }

    public static double bankRoundHalfEvenDefault(double value) {
        return bankRoundHalfEven(value, DEFAULT_BANK_SCALE);
    }

    public static double finalizeScore(double score) {
        return bankRoundHalfEvenDefault(softwareFtzFlush(score));
    }
}
