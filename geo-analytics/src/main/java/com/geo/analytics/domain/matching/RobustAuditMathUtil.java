package com.geo.analytics.domain.matching;

import java.lang.StrictMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

public final class RobustAuditMathUtil {

    public static final double EPSILON = 1e-10;
    public static final double MAD_NORMALIZATION = 1.3489;
    private static final int DEFAULT_BANK_SCALE = 10;

    private RobustAuditMathUtil() {
    }

    public static double softwareFtzFlush(double x) {
        double ax = StrictMath.abs(x);
        if (ax < EPSILON) {
            return StrictMath.copySign(0.0, x);
        }
        return x;
    }

    public static double[] betaMomentAlphaBeta(double mean, double variance) {
        double m = softwareFtzFlush(mean);
        m = StrictMath.min(StrictMath.max(m, EPSILON), 1.0 - EPSILON);
        double cap = StrictMath.fma(m, 1.0 - m, -EPSILON);
        double v = variance;
        if (v >= cap) {
            v = cap;
        }
        if (v < EPSILON) {
            v = EPSILON;
        }
        double s = StrictMath.fma(m, 1.0 - m, 0.0) / v - 1.0;
        if (s <= EPSILON) {
            s = EPSILON;
        }
        double alpha = StrictMath.fma(m, s, 0.0);
        double beta = StrictMath.fma(1.0 - m, s, 0.0);
        return new double[]{bankRoundHalfEvenDefault(alpha), bankRoundHalfEvenDefault(beta)};
    }

    public static double betaBinomialPosteriorMean(double k, double n, double alpha, double beta) {
        double kk = softwareFtzFlush(k);
        double nn = softwareFtzFlush(n);
        double a = softwareFtzFlush(alpha);
        double b = softwareFtzFlush(beta);
        double den = StrictMath.fma(nn, 1.0, StrictMath.fma(a, 1.0, b));
        if (StrictMath.abs(den) < EPSILON) {
            return bankRoundHalfEven(0.0, DEFAULT_BANK_SCALE);
        }
        double num = StrictMath.fma(kk, 1.0, a);
        return bankRoundHalfEven(softwareFtzFlush(num / den), DEFAULT_BANK_SCALE);
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
        return StrictMath.fma(0.5, copy[mid - 1], 0.5 * copy[mid]);
    }

    public static double medianAbsoluteDeviation(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double med = median(values);
        int n = values.length;
        double[] dev = new double[n];
        for (int i = 0; i < n; i++) {
            dev[i] = StrictMath.abs(StrictMath.fma(values[i], 1.0, -med));
        }
        return median(dev);
    }

    public static double modifiedZScore(double value, double[] sample) {
        if (sample.length == 0) {
            return 0.0;
        }
        double med = median(sample);
        double mad = medianAbsoluteDeviation(sample);
        if (mad < EPSILON) {
            return 0.0;
        }
        double scale = StrictMath.fma(MAD_NORMALIZATION, mad, 0.0);
        double num = StrictMath.fma(value, 1.0, -med);
        return bankRoundHalfEvenDefault(softwareFtzFlush(num / scale));
    }

    public static double logSigmoidStable(double x) {
        if (x >= 0.0) {
            double ex = StrictMath.exp(-x);
            return bankRoundHalfEvenDefault(-StrictMath.log1p(ex));
        }
        double ex = StrictMath.exp(x);
        return bankRoundHalfEvenDefault(StrictMath.fma(x, 1.0, -StrictMath.log1p(ex)));
    }

    public static double tanhStable(double x) {
        if (x > 20.0) {
            return bankRoundHalfEvenDefault(1.0);
        }
        if (x < -20.0) {
            return bankRoundHalfEvenDefault(-1.0);
        }
        double e2x = StrictMath.exp(StrictMath.fma(2.0, x, 0.0));
        return bankRoundHalfEvenDefault((e2x - 1.0) / (e2x + 1.0));
    }

    public static double bankRoundHalfEven(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_EVEN).doubleValue();
    }

    public static double bankRoundHalfEvenDefault(double value) {
        return bankRoundHalfEven(value, DEFAULT_BANK_SCALE);
    }

    public static double finalizeScore(double score) {
        return bankRoundHalfEvenDefault(softwareFtzFlush(score));
    }
}
