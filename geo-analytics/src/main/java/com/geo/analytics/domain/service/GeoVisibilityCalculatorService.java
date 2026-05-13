package com.geo.analytics.domain.service;

import com.geo.analytics.domain.matching.RobustAuditMathUtil;
import com.geo.analytics.domain.matching.TokenizerManager;
import com.geo.analytics.domain.model.SomRawMetrics;
import com.geo.analytics.domain.model.VisibilityStageMapper;
import java.lang.StrictMath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GeoVisibilityCalculatorService {
    private static final Logger log = LoggerFactory.getLogger(GeoVisibilityCalculatorService.class);

    /** Must fit DB column varchar(32) on audit_history.calculation_version. */
    public static final String CALCULATION_VERSION = "V11_GEO_PURE";
    public static final double BM25_K1 = 1.2;
    public static final double BM25_B = 0.75;
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
        StringBuilder sb = new StringBuilder(
                StrictMath.max(16, responseTokens.size() * 8 + responseTokens.size() + 2));
        appendWrappedTokens(sb, responseTokens);
        String hay = sb.toString();

        List<String> needleStrs = new ArrayList<>();
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            List<String> seq = tokenizerManager.tokenizeToNormalizedList(kw.strip());
            if (seq.isEmpty()) {
                continue;
            }
            sb.setLength(0);
            appendWrappedTokens(sb, seq);
            needleStrs.add(sb.toString());
        }
        if (needleStrs.isEmpty()) {
            return 0;
        }

        int mentions = 0;
        int charFrom = 0;
        while (charFrom < hay.length()) {
            int best = -1;
            int bestLen = 0;
            for (String needle : needleStrs) {
                int found = hay.indexOf(needle, charFrom);
                if (found >= 0
                        && found >= charFrom
                        && (best < 0
                                || found < best
                                || (found == best && needle.length() > bestLen))) {
                    best = found;
                    bestLen = needle.length();
                }
            }
            if (best < 0) {
                break;
            }
            mentions++;
            charFrom = best + bestLen;
        }
        return mentions;
    }

    private static void appendWrappedTokens(StringBuilder sb, List<String> tokens) {
        sb.append('\u0001');
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                sb.append('\u0001');
            }
            sb.append(tokens.get(i));
        }
        sb.append('\u0001');
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

    public static double bm25Score(double idf, double termFreq, double docLength, double avgDocLength) {
        double lAvg = StrictMath.max(RobustAuditMathUtil.EPSILON, avgDocLength);
        double d = StrictMath.max(RobustAuditMathUtil.EPSILON, docLength);
        double f = StrictMath.max(0.0, termFreq);
        double ratio = d / lAvg;
        double inner = StrictMath.fma(BM25_B, ratio, 1.0 - BM25_B);
        double denom = StrictMath.fma(BM25_K1, inner, f);
        denom = StrictMath.max(RobustAuditMathUtil.EPSILON, denom);
        double num = StrictMath.fma(idf, StrictMath.fma(f, BM25_K1 + 1.0, 0.0), 0.0);
        return num / denom;
    }

    public static double calculateFinalGeoScore(
            double aiAuditTotal, double meoScore, double machineReadabilityScore) {
        double safeAi = Double.isFinite(aiAuditTotal) ? aiAuditTotal : 0.0d;
        double safeMeo = Double.isFinite(meoScore) ? meoScore : 0.0d;
        double safeMr = Double.isFinite(machineReadabilityScore) ? machineReadabilityScore : 0.0d;
        double partial = StrictMath.fma(1.0d, safeMeo, safeMr);
        double sum = StrictMath.fma(1.0d, safeAi, partial);
        return StrictMath.max(0.0d, StrictMath.min(100.0d, sum));
    }

    public static GbvsResult compute(SomRawMetrics metrics, double lAvgJob) {
        return computeBatch(List.of(metrics), lAvgJob).getFirst();
    }

    public static List<GbvsResult> computeBatch(List<SomRawMetrics> rows, double lAvgJob) {
        return computeBatch(rows, lAvgJob, rows.size());
    }

    public static List<GbvsResult> computeBatch(
            List<SomRawMetrics> rows,
            double lAvgJob,
            int bayesSampleCardinality) {
        int n = rows.size();
        if (n == 0) {
            return List.of();
        }
        int blendN = StrictMath.max(n, StrictMath.max(1, bayesSampleCardinality));
        int df = 0;
        for (SomRawMetrics r : rows) {
            if (r.isSemanticallyMentioned() && (r.nounCount() > 0 || r.aiCitationPosition() != null)) {
                df++;
            }
        }
        double idf = bm25Idf(bayesSampleCardinality, df);
        double sumLen = 0.0;
        for (SomRawMetrics r : rows) {
            sumLen = StrictMath.fma(StrictMath.max(0, r.responseTokenLength()), 1.0, sumLen);
        }
        double lAvgUse = lAvgJob > RobustAuditMathUtil.EPSILON
                ? lAvgJob
                : StrictMath.max(RobustAuditMathUtil.EPSILON, sumLen / StrictMath.max(1, n));
        double[] raw = new double[n];
        final double idfF = idf;
        final double lAvgF = lAvgUse;
        IntStream.range(0, n).forEach(i -> raw[i] = weightedSom(rows.get(i), lAvgF, idfF));
        double[] work = new double[n];
        if (n < BAYES_SAMPLE_THRESHOLD) {
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
        Arrays.sort(sorted);
        double minR = sorted[0];
        double maxR = sorted[n - 1];
        GbvsResult[] out = new GbvsResult[n];
        IntStream.range(0, n).forEach(i -> {
            double z = zScores[i];
            int stage = stageFromModifiedZ(z);
            double span = StrictMath.fma(maxR, 1.0, -minR);
            double pct = span > RobustAuditMathUtil.EPSILON
                    ? StrictMath.fma(100.0, (work[i] - minR) / span, 0.0)
                    : StrictMath.fma(clamp01(work[i]), 100.0, 0.0);
            out[i] = new GbvsResult(clampPercent(pct), stage, z);
        });
        return IntStream.range(0, n).mapToObj(i -> out[i]).toList();
    }

    private static double bm25Idf(int corpusN, int docFreq) {
        double nn = StrictMath.max(1.0, (double) corpusN);
        double ddf = StrictMath.max(0.0, (double) docFreq);
        double ratio = StrictMath.fma(nn, 1.0, -ddf + 0.5) / StrictMath.max(RobustAuditMathUtil.EPSILON, ddf + 0.5);
        return StrictMath.log(StrictMath.max(RobustAuditMathUtil.EPSILON, ratio));
    }

    private static void sanitizeWorkForRobustStatistics(double[] work) {
        for (int i = 0; i < work.length; i++) {
            if (!Double.isFinite(work[i])) {
                work[i] = 0.0;
            }
        }
    }

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

    private static double weightedSom(SomRawMetrics metrics, double lAvgJob, double idf) {
        Integer aiPos = metrics.aiCitationPosition();
        if (!metrics.isSemanticallyMentioned()) {
            log.info(
                    "[MATH DEBUG] outcome=not_semantic aiPos={} f(nounCount)={} tokenCount={} responseTokenLength={} sourceWeight={} stuffingDensity={} sentimentIntensity={} somScore=0.0",
                    aiPos,
                    metrics.nounCount(),
                    metrics.tokenCount(),
                    metrics.responseTokenLength(),
                    metrics.sourceWeight(),
                    metrics.stuffingDensity(),
                    metrics.sentimentIntensity());
            return 0.0;
        }
        int f = StrictMath.max(0, metrics.nounCount());
        int total = StrictMath.max(0, metrics.responseTokenLength());
        double presence = (f > 0 || aiPos != null) ? 1.0 : 0.0;
        if (presence <= 0.0) {
            log.info(
                    "[MATH DEBUG] outcome=no_presence aiPos={} f={} total={} somScore=0.0",
                    aiPos,
                    f,
                    total);
            return 0.0;
        }
        double sourceWeight = metrics.sourceWeight() > RobustAuditMathUtil.EPSILON
                ? metrics.sourceWeight()
                : SOURCE_WEIGHT_LOW;
        double sentiment = sentimentWeight(metrics.sentimentIntensity());
        double fTok = StrictMath.max(0.0, (double) f);
        if (fTok < RobustAuditMathUtil.EPSILON && aiPos != null) {
            fTok = 1.0;
        }
        double docLen = StrictMath.max(RobustAuditMathUtil.EPSILON, (double) total);
        double bm25 = bm25Score(idf, fTok, docLen, lAvgJob);
        double stuffingPenalty = 1.0;
        double brandSignalCore = sourceWeight * sentiment * bm25 * stuffingPenalty;
        double otherPresence = StrictMath.max(total - f, 0) > 0 ? 1.0 : 0.0;
        double otherSignal = otherPresence * SOURCE_WEIGHT_LOW;
        double textCoreDenom = brandSignalCore + otherSignal;
        double textCore = textCoreDenom > RobustAuditMathUtil.EPSILON
                ? clamp01(brandSignalCore / textCoreDenom)
                : 0.0;
        int sStar = (aiPos != null && f == 0) ? 10 : stageFromScorePercent(textCore * 100.0);
        double progressRate = aiPos != null
                ? VisibilityStageMapper.define(sStar, aiPos).progressRate()
                : 0.0;
        double brandSignal = presence * progressRate * brandSignalCore;
        double denom = brandSignal + otherSignal;
        if (denom <= RobustAuditMathUtil.EPSILON) {
            log.info(
                    "[MATH DEBUG] outcome=zero_denom aiPos={} sStar={} p={} f={} fTok={} total={} presence={} bm25={} density={} stuffingPenalty={} brandSignalCore={} brandSignal={} otherPresence={} otherSignal={} denom={} idf={} lAvgJob={} sourceWeight={} sentiment={} textCore={} somScore=0.0",
                    aiPos,
                    sStar,
                    progressRate,
                    f,
                    fTok,
                    total,
                    presence,
                    bm25,
                    0.0,
                    stuffingPenalty,
                    brandSignalCore,
                    brandSignal,
                    otherPresence,
                    otherSignal,
                    denom,
                    idf,
                    lAvgJob,
                    sourceWeight,
                    sentiment,
                    textCore);
            return 0.0;
        }
        double somScore = clamp01(brandSignal / denom);
        log.info(
                "[MATH DEBUG] outcome=ok aiPos={} sStar={} p={} f={} fTok={} total={} presence={} bm25={} density={} stuffingPenalty={} brandSignalCore={} brandSignal={} otherPresence={} otherSignal={} denom={} idf={} lAvgJob={} sourceWeight={} sentiment={} textCore={} somScore={}",
                aiPos,
                sStar,
                progressRate,
                f,
                fTok,
                total,
                presence,
                bm25,
                0.0,
                stuffingPenalty,
                brandSignalCore,
                brandSignal,
                otherPresence,
                otherSignal,
                denom,
                idf,
                lAvgJob,
                sourceWeight,
                sentiment,
                textCore,
                somScore);
        return somScore;
    }

    private static double sentimentWeight(double sentimentScore) {
        if (Double.isNaN(sentimentScore) || Double.isInfinite(sentimentScore)) {
            return 1.0;
        }
        double s = clamp(sentimentScore, -1.0, 1.0);
        return clamp(StrictMath.fma(0.5, s, 1.0), 0.5, 1.5);
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
        return StrictMath.max(lo, StrictMath.min(hi, v));
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
