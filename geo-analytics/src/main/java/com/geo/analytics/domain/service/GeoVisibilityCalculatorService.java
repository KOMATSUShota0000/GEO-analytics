package com.geo.analytics.domain.service;

import com.geo.analytics.domain.matching.RobustAuditMathUtil;
import com.geo.analytics.domain.matching.TokenizerManager;
import com.geo.analytics.domain.model.SomRawMetrics;
import java.lang.StrictMath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public final class GeoVisibilityCalculatorService {
    public static final String CALCULATION_VERSION = "PHASE8.5_ROBUST_AUDIT_V1";
    private static final double EPSILON = 1.0E-10;
    private static final int BAYES_SAMPLE_THRESHOLD = 30;
    private static final double SOURCE_WEIGHT_HIGH = 1.5;
    private static final double SOURCE_WEIGHT_MEDIUM = 1.0;
    private static final double SOURCE_WEIGHT_LOW = 0.3;

    private final TokenizerManager tokenizerManager;

    public GeoVisibilityCalculatorService(TokenizerManager tokenizerManager) {
        this.tokenizerManager = Objects.requireNonNull(tokenizerManager);
    }

    public List<String> tokenizeResponseForMentions(String text) {
        return tokenizerManager.tokenizeToNormalizedList(text);
    }

    public int countNormalizedMentions(List<String> responseTokens, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0;
        }
        List<List<String>> sequences = new ArrayList<>();
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            List<String> seq = tokenizerManager.tokenizeToNormalizedList(kw.strip());
            if (!seq.isEmpty()) {
                sequences.add(seq);
            }
        }
        if (sequences.isEmpty()) {
            return 0;
        }
        int mentions = 0;
        int fromIndex = 0;
        final int n = responseTokens.size();
        while (fromIndex < n) {
            int bestRel = Integer.MAX_VALUE;
            int bestLen = 0;
            for (List<String> seq : sequences) {
                int rel = Collections.indexOfSubList(responseTokens.subList(fromIndex, n), seq);
                if (rel < 0) {
                    continue;
                }
                if (rel < bestRel || (rel == bestRel && seq.size() > bestLen)) {
                    bestRel = rel;
                    bestLen = seq.size();
                }
            }
            if (bestRel == Integer.MAX_VALUE) {
                break;
            }
            mentions++;
            fromIndex += bestRel + bestLen;
        }
        return mentions;
    }

    public static List<String> splitBrandAliasPhrases(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return Arrays.stream(primary.split("[,、]"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        if (fallback != null && !fallback.isBlank()) {
            return List.of(fallback.strip());
        }
        return List.of();
    }

    public static GbvsResult compute(
            SomRawMetrics metrics,
            @SuppressWarnings("unused") double lAvgJob) { // Reserved for Phase 11 cross-model L_avg bias correction
        return computeBatch(List.of(metrics), lAvgJob).getFirst();
    }

    public static List<GbvsResult> computeBatch(
            List<SomRawMetrics> rows,
            @SuppressWarnings("unused") double lAvgJob) { // Reserved for Phase 11 cross-model L_avg bias correction
        return computeBatch(rows, lAvgJob, rows.size());
    }

    public static List<GbvsResult> computeBatch(
            List<SomRawMetrics> rows,
            @SuppressWarnings("unused") double lAvgJob, // Reserved for Phase 11 cross-model L_avg bias correction
            int bayesSampleCardinality) {
        int n = rows.size();
        if (n == 0) {
            return List.of();
        }
        int blendN = StrictMath.max(n, StrictMath.max(1, bayesSampleCardinality));
        double[] raw = new double[n];
        IntStream.range(0, n).parallel().forEach(i -> raw[i] = weightedSom(rows.get(i)));
        double[] work = new double[n];
        if (blendN < BAYES_SAMPLE_THRESHOLD) {
            applySmallSampleBetaPipeline(rows, raw, blendN, n, work);
        } else {
            System.arraycopy(raw, 0, work, 0, n);
        }
        sanitizeWorkForRobustStatistics(work);
        double[] zScores = RobustAuditMathUtil.modifiedZScores(work);
        if (n == 1) {
            double pct = StrictMath.fma(clamp01(work[0]), 100.0, 0.0);
            return List.of(new GbvsResult(clampPercent(pct), stageFromScorePercent(pct), zScores[0]));
        }
        double[] sorted = Arrays.copyOf(work, n);
        Arrays.parallelSort(sorted);
        double minR = sorted[0];
        double maxR = sorted[n - 1];
        GbvsResult[] out = new GbvsResult[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            double z = zScores[i];
            int stage = stageFromModifiedZ(z);
            double span = StrictMath.fma(maxR, 1.0, -minR);
            double pct = span > EPSILON
                    ? StrictMath.fma(100.0, (work[i] - minR) / span, 0.0)
                    : StrictMath.fma(clamp01(work[i]), 100.0, 0.0);
            out[i] = new GbvsResult(clampPercent(pct), stage, z);
        });
        return IntStream.range(0, n).mapToObj(i -> out[i]).toList();
    }

    /** NaN / Infinity must not reach median or MAD ({@link RobustAuditMathUtil#modifiedZScores(double[])}). */
    private static void sanitizeWorkForRobustStatistics(double[] work) {
        for (int i = 0; i < work.length; i++) {
            if (!Double.isFinite(work[i])) {
                work[i] = 0.0;
            }
        }
    }

    /**
     * Moment-matched Beta hyperparameters with {@link RobustAuditMathUtil#betaMomentAlphaBeta(double, double)} variance clamp,
     * then per-row Beta–Binomial posterior means (effective trials = {@code blendN}).
     */
    private static void applySmallSampleBetaPipeline(
            List<SomRawMetrics> rows, double[] raw, int blendN, int n, double[] work) {
        int mentionedCount = 0;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            if (rows.get(i).isSemanticallyMentioned()) {
                mentionedCount++;
                sum = StrictMath.fma(raw[i], 1.0, sum);
            }
        }
        if (mentionedCount == 0) {
            Arrays.fill(work, 0.0);
            return;
        }
        double m = sum / (double) mentionedCount;
        double v;
        if (mentionedCount < 2) {
            v = StrictMath.fma(m, 1.0 - m, 0.0) / (double) StrictMath.max(1, blendN);
        } else {
            double ss = 0.0;
            for (int i = 0; i < n; i++) {
                if (rows.get(i).isSemanticallyMentioned()) {
                    double d = StrictMath.fma(raw[i], 1.0, -m);
                    ss = StrictMath.fma(d, d, ss);
                }
            }
            v = ss / (double) (mentionedCount - 1);
        }
        double[] ab = RobustAuditMathUtil.betaMomentAlphaBeta(m, v);
        double alpha = ab[0];
        double beta = ab[1];
        double nn = (double) blendN;
        for (int i = 0; i < n; i++) {
            if (!rows.get(i).isSemanticallyMentioned()) {
                work[i] = 0.0;
                continue;
            }
            double kEff = RobustAuditMathUtil.softwareFtzFlush(
                    StrictMath.min(nn, StrictMath.max(0.0, StrictMath.fma(raw[i], nn, 0.0))));
            work[i] = RobustAuditMathUtil.betaBinomialPosteriorMean(kEff, nn, alpha, beta);
        }
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
        if (!metrics.isSemanticallyMentioned()) {
            return 0.0;
        }
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
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return lo;
        }
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

    /** Modified Z′ bands on ±3.0 tails; inner rank inverted to visibility stage (11 − inner). */
    private static int stageFromModifiedZ(double z) {
        if (Double.isNaN(z) || Double.isInfinite(z)) {
            return 6;
        }
        int inner;
        if (z >= 3.0) {
            inner = 10;
        } else if (z <= -3.0) {
            inner = 1;
        } else if (z >= 2.25) {
            inner = 9;
        } else if (z >= 1.5) {
            inner = 8;
        } else if (z >= 0.75) {
            inner = 7;
        } else if (z >= 0.0) {
            inner = 6;
        } else if (z >= -0.75) {
            inner = 5;
        } else if (z >= -1.5) {
            inner = 4;
        } else if (z >= -2.25) {
            inner = 3;
        } else {
            inner = 2;
        }
        return StrictMath.subtractExact(11, inner);
    }

    public record GbvsResult(double scorePercent, int visibilityStage, double modifiedZScore) {}
}
