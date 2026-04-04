package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.SomRawMetrics;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public final class GeoVisibilityCalculatorService {
    public static final String CALCULATION_VERSION = "PHASE4.5_FINAL_V1";
    private static final double EPSILON = 1.0E-10;
    private static final double IQR_SCALE = 1.3489;
    private static final int BAYES_SAMPLE_THRESHOLD = 30;
    private static final double MARKET_PRIOR_INTEGRATED_RAW = 0.42;
    private static final double PRIOR_EQUIVALENT_N = 18.0;
    private static final double SOURCE_WEIGHT_HIGH = 1.5;
    private static final double SOURCE_WEIGHT_MEDIUM = 1.0;
    private static final double SOURCE_WEIGHT_LOW = 0.3;

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
        IntStream.range(0, n).parallel().forEach(i -> raw[i] = weightedSom(rows.get(i)));
        double[] work = new double[n];
        if (n < BAYES_SAMPLE_THRESHOLD) {
            int finalN = n;
            IntStream.range(0, n).parallel().forEach(i -> work[i] = bayesBlend(raw[i], finalN));
        } else {
            System.arraycopy(raw, 0, work, 0, n);
        }
        if (n == 1) {
            double pct = clamp01(work[0]) * 100.0;
            return List.of(new GbvsResult(pct, stageFromScorePercent(pct), 0.0));
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
            double pct = maxR > minR + EPSILON ? 100.0 * (work[i] - minR) / (maxR - minR) : clamp01(work[i]) * 100.0;
            out[i] = new GbvsResult(clampPercent(pct), stage, zPrime);
        });
        return Arrays.asList(out);
    }

    private static double bayesBlend(double rawI, int n) {
        double w = (double) n / ((double) n + PRIOR_EQUIVALENT_N);
        return w * rawI + (1.0 - w) * MARKET_PRIOR_INTEGRATED_RAW;
    }

    public static double sourceWeightFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return SOURCE_WEIGHT_LOW;
        }
        var normalized = url.strip().toLowerCase();
        if (!normalized.contains("://")) {
            if (normalized.startsWith("prtimes.jp") || normalized.startsWith("www.prtimes.jp")) {
                return SOURCE_WEIGHT_HIGH;
            }
            if (normalized.startsWith("detail.chiebukuro.yahoo.co.jp")) {
                return SOURCE_WEIGHT_MEDIUM;
            }
            return SOURCE_WEIGHT_LOW;
        }
        try {
            var host = java.net.URI.create(normalized).getHost();
            if (host == null || host.isBlank()) {
                return SOURCE_WEIGHT_LOW;
            }
            var h = host.toLowerCase();
            if (h.equals("prtimes.jp") || h.endsWith(".prtimes.jp")) {
                return SOURCE_WEIGHT_HIGH;
            }
            if (h.equals("detail.chiebukuro.yahoo.co.jp")) {
                return SOURCE_WEIGHT_MEDIUM;
            }
            return SOURCE_WEIGHT_LOW;
        } catch (Exception ignored) {
            return SOURCE_WEIGHT_LOW;
        }
    }

    private static double weightedSom(SomRawMetrics metrics) {
        int f = StrictMath.max(0, metrics.nounCount());
        int rank = metrics.rankPosition();
        int total = StrictMath.max(0, metrics.responseTokenLength());
        double presence = (f > 0 || rank > 0) ? 1.0 : 0.0;
        double sourceWeight = metrics.sourceWeight() > EPSILON ? metrics.sourceWeight() : SOURCE_WEIGHT_LOW;
        double sentiment = sentimentWeight(metrics.sentimentIntensity());
        double brandSignal = presence * sourceWeight * sentiment;
        double otherPresence = StrictMath.max(total - f, 0) > 0 ? 1.0 : 0.0;
        double otherSignal = otherPresence * SOURCE_WEIGHT_LOW;
        double denom = brandSignal + otherSignal;
        if (denom <= EPSILON) {
            return 0.0;
        }
        return clamp01(brandSignal / denom);
    }

    private static double sentimentWeight(double sentimentScore) {
        if (Double.isNaN(sentimentScore) || Double.isInfinite(sentimentScore)) {
            return 1.0;
        }
        if (sentimentScore < 0.5) {
            if (sentimentScore >= -1.0 && sentimentScore <= 1.0) {
                return 1.0 + 0.5 * sentimentScore;
            }
        }
        return clamp(sentimentScore, 0.5, 1.5);
    }

    private static double clamp01(double v) {
        return clamp(v, 0.0, 1.0);
    }

    private static double clampPercent(double v) {
        return clamp(v, 0.0, 100.0);
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    private static int stageFromScorePercent(double pct) {
        int inner;
        if (pct >= 90.0) {
            inner = 10;
        } else if (pct >= 80.0) {
            inner = 9;
        } else if (pct >= 70.0) {
            inner = 8;
        } else if (pct >= 60.0) {
            inner = 7;
        } else if (pct >= 50.0) {
            inner = 6;
        } else if (pct >= 40.0) {
            inner = 5;
        } else if (pct >= 30.0) {
            inner = 4;
        } else if (pct >= 20.0) {
            inner = 3;
        } else if (pct >= 10.0) {
            inner = 2;
        } else {
            inner = 1;
        }
        return StrictMath.subtractExact(11, inner);
    }

    private static int stageFromZPrime(double z) {
        int inner;
        if (z >= 2.0) {
            inner = 10;
        } else if (z >= 1.5) {
            inner = 9;
        } else if (z >= 1.0) {
            inner = 8;
        } else if (z >= 0.5) {
            inner = 7;
        } else if (z >= 0.0) {
            inner = 6;
        } else if (z >= -0.5) {
            inner = 5;
        } else if (z >= -1.0) {
            inner = 4;
        } else if (z >= -1.5) {
            inner = 3;
        } else if (z >= -2.0) {
            inner = 2;
        } else {
            inner = 1;
        }
        return StrictMath.subtractExact(11, inner);
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
