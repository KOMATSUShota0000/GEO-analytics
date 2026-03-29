package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.SomRawMetrics;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public final class GeoVisibilityCalculatorService {
    public static final String CALCULATION_VERSION = "PHASE4.5_FINAL_V1";
    private static final double EPSILON = 1.0E-10;
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final double LAMBDA = 0.5;
    private static final double STUFFING_THRESHOLD = 0.03;
    private static final double STUFFING_COEFF = 500.0;
    private static final double RBP_P = 0.5;
    private static final double RBP_ONE_MINUS_P = 0.5;
    private static final double IQR_SCALE = 1.3489;
    private static final double SINGLE_SAMPLE_RAW_SCALE = 25.0;
    private static final int BAYES_SAMPLE_THRESHOLD = 30;
    private static final double MARKET_PRIOR_INTEGRATED_RAW = 0.48;
    private static final double PRIOR_EQUIVALENT_N = 18.0;

    private GeoVisibilityCalculatorService() {}

    public static GbvsResult compute(SomRawMetrics metrics, double lAvgJob) {
        return computeBatch(List.of(metrics), lAvgJob).getFirst();
    }

    public static List<GbvsResult> computeBatch(List<SomRawMetrics> rows, double lAvgJob) {
        int n = rows.size();
        if (n == 0) {
            return List.of();
        }
        double[] raw = new double[n];
        IntStream.range(0, n).parallel().forEach(i -> raw[i] = integratedRaw(rows.get(i), lAvgJob));
        double[] work = new double[n];
        if (n < BAYES_SAMPLE_THRESHOLD) {
            int finalN = n;
            IntStream.range(0, n).parallel().forEach(i -> work[i] = bayesBlend(raw[i], finalN));
        } else {
            System.arraycopy(raw, 0, work, 0, n);
        }
        double[] sorted = Arrays.copyOf(work, n);
        Arrays.parallelSort(sorted);
        double med = medianSorted(sorted);
        double iq = iqrSorted(sorted);
        double denom = iq > EPSILON ? iq / IQR_SCALE : 0.0;
        double minR = sorted[0];
        double maxR = sorted[n - 1];
        GbvsResult[] out = new GbvsResult[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            double zPrime = denom > EPSILON ? (work[i] - med) / denom : 0.0;
            int stage = stageFromZPrime(zPrime);
            double pct =
                maxR > minR + EPSILON
                    ? 100.0 * (work[i] - minR) / (maxR - minR)
                    : Math.clamp(work[i] * SINGLE_SAMPLE_RAW_SCALE, 0.0, 100.0);
            out[i] = new GbvsResult(Math.clamp(pct, 0.0, 100.0), stage, zPrime);
        });
        return Arrays.asList(out);
    }

    private static double bayesBlend(double rawI, int n) {
        double w = (double) n / ((double) n + PRIOR_EQUIVALENT_N);
        return w * rawI + (1.0 - w) * MARKET_PRIOR_INTEGRATED_RAW;
    }

    private static double integratedRaw(SomRawMetrics metrics, double lAvgJob) {
        int f = Math.max(0, metrics.nounCount());
        int L = Math.max(0, metrics.responseTokenLength());
        double lAvgEff = lAvgEffective(lAvgJob);
        double bm25 = bm25Term(f, L, lAvgEff);
        double rbp = rbpTerm(f, metrics.rankPosition());
        double sent = sentimentMultiplier(metrics.sentimentIntensity());
        double density = L > 0 ? (double) f / (double) L : 0.0;
        double penalty = stuffingPenaltyMultiplier(density);
        return bm25 * rbp * sent * penalty;
    }

    private static double lAvgEffective(double lAvgJob) {
        if (lAvgJob > EPSILON) {
            return lAvgJob;
        }
        return 1.0;
    }

    private static double bm25Term(int f, int L, double lAvgEff) {
        if (f == 0) {
            return 0.0;
        }
        double lenBase = StrictMath.max(lAvgEff, 1.0);
        double normLen = 1.0 - B + B * ((double) L / lenBase);
        double den = (double) f + K1 * normLen;
        return (double) f * (K1 + 1.0) / StrictMath.max(den, EPSILON);
    }

    private static double rbpTerm(int f, int rankPosition) {
        if (f <= 0) {
            return 0.0;
        }
        int r0 = rankPosition > 0 ? rankPosition : 1;
        double sum = 0.0;
        for (int j = 0; j < f; j++) {
            int r = r0 + j;
            sum += RBP_ONE_MINUS_P * StrictMath.pow(RBP_P, (double) (r - 1));
        }
        return sum;
    }

    private static double sentimentMultiplier(double sentimentScore) {
        double s = Math.clamp(sentimentScore, -1.0, 1.0);
        return 1.0 + LAMBDA * StrictMath.tanh(s);
    }

    private static double stuffingPenaltyMultiplier(double density) {
        if (density <= STUFFING_THRESHOLD) {
            return 1.0;
        }
        double d = density - STUFFING_THRESHOLD;
        return StrictMath.max(0.0, 1.0 - STUFFING_COEFF * d * d);
    }

    private static int stageFromZPrime(double z) {
        if (z >= 2.0) {
            return 10;
        }
        if (z >= 1.5) {
            return 9;
        }
        if (z >= 1.0) {
            return 8;
        }
        if (z >= 0.5) {
            return 7;
        }
        if (z >= 0.0) {
            return 6;
        }
        if (z >= -0.5) {
            return 5;
        }
        if (z >= -1.0) {
            return 4;
        }
        if (z >= -1.5) {
            return 3;
        }
        if (z >= -2.0) {
            return 2;
        }
        return 1;
    }

    private static double medianSorted(double[] sorted) {
        int n = sorted.length;
        if (n == 0) {
            return 0.0;
        }
        if ((n & 1) == 1) {
            return sorted[n / 2];
        }
        return (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

    private static double iqrSorted(double[] sorted) {
        int n = sorted.length;
        if (n == 0) {
            return 0.0;
        }
        if (n == 1) {
            return 0.0;
        }
        double q1 = percentileSorted(sorted, 0.25);
        double q3 = percentileSorted(sorted, 0.75);
        return q3 - q1;
    }

    private static double percentileSorted(double[] sorted, double p) {
        int n = sorted.length;
        if (n == 1) {
            return sorted[0];
        }
        double h = (n - 1) * p;
        int lo = (int) StrictMath.floor(h);
        int hi = (int) StrictMath.ceil(h);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (h - (double) lo);
    }

    public record GbvsResult(double scorePercent, int visibilityStage, double modifiedZScore) {}
}
