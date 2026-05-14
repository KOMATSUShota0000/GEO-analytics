package com.geo.analytics.domain.service;

import com.geo.analytics.domain.matching.RobustAuditMathUtil;
import com.geo.analytics.domain.model.SomRawMetrics;
import com.geo.analytics.domain.model.VisibilityStageMapper;
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

    /** 出現密度 mentionDensity がこの値に達するとブランドシグナルの密度成分が飽和する（説明用コメント付き定数）。 */
    private static final double MENTION_DENSITY_SATURATION = 0.30d;

    /** この出現回数を超えるとブランドシグナルの回数成分が飽和する。 */
    private static final double MENTION_COUNT_SATURATION = 12.0d;

    private static final double SOURCE_WEIGHT_HIGH = 1.5;
    private static final double SOURCE_WEIGHT_MEDIUM = 1.0;
    private static final double SOURCE_WEIGHT_LOW = 0.3;

    /**
     * ブランド別名をカンマ区切りで分割（後段の LLM ハンドオフ用のユーティリティ）。
     */
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

    public static double calculateFinalGeoScore(
            double aiAuditTotal, double meoScore, double machineReadabilityScore) {
        double safeAi = Double.isFinite(aiAuditTotal) ? aiAuditTotal : 0.0d;
        double safeMeo = Double.isFinite(meoScore) ? meoScore : 0.0d;
        double safeMr = Double.isFinite(machineReadabilityScore) ? machineReadabilityScore : 0.0d;
        double partial = Math.fma(1.0d, safeMeo, safeMr);
        double sum = Math.fma(1.0d, safeAi, partial);
        return Math.max(0.0d, Math.min(100.0d, sum));
    }

    public static GbvsResult compute(SomRawMetrics metrics, double lAvgJob) {
        return computeBatch(List.of(metrics), lAvgJob).getFirst();
    }

    public static List<GbvsResult> computeBatch(List<SomRawMetrics> rows, double lAvgJob) {
        int n = rows.size();
        if (n == 0) {
            return List.of();
        }
        double[] raw = new double[n];
        IntStream.range(0, n).forEach(i -> raw[i] = weightedSom(rows.get(i)));
        double[] work = Arrays.copyOf(raw, n);
        sanitizeWorkForRobustStatistics(work);
        double[] zScores = RobustAuditMathUtil.modifiedZScores(work);
        if (n == 1) {
            double pct = Math.fma(clamp01(work[0]), 100.0, 0.0);
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
            double span = Math.fma(maxR, 1.0, -minR);
            double pct = span > RobustAuditMathUtil.EPSILON
                    ? Math.fma(100.0, (work[i] - minR) / span, 0.0)
                    : Math.fma(clamp01(work[i]), 100.0, 0.0);
            out[i] = new GbvsResult(clampPercent(pct), stage, z);
        });
        return IntStream.range(0, n).mapToObj(i -> out[i]).toList();
    }

    private static void sanitizeWorkForRobustStatistics(double[] work) {
        for (int i = 0; i < work.length; i++) {
            if (!Double.isFinite(work[i])) {
                work[i] = 0.0;
            }
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

    /**
     * LLM が構造化出力した token_count（ブランド実質言及パッセージの文字数相当）と応答長からブレンド SOM を構成する。
     * 感情強度は後段の倍率補正を行わず原文値をログのみに残す（スコア本体には乗算しない）。
     */
    private static double weightedSom(SomRawMetrics metrics) {
        Integer aiPos = metrics.aiCitationPosition();
        if (!metrics.isSemanticallyMentioned()) {
            log.info(
                    "[MATH DEBUG] outcome=not_semantic aiPos={} mentions={} tokenCount={} responseTokenLength={} sourceWeight={} stuffingDensity={} sentimentIntensity={} somScore=0.0",
                    aiPos,
                    metrics.nounCount(),
                    metrics.tokenCount(),
                    metrics.responseTokenLength(),
                    metrics.sourceWeight(),
                    metrics.stuffingDensity(),
                    metrics.sentimentIntensity());
            return 0.0;
        }
        int mentions = Math.max(0, metrics.nounCount());
        int total = Math.max(0, metrics.responseTokenLength());
        double presence = (mentions > 0 || aiPos != null) ? 1.0 : 0.0;
        if (presence <= 0.0) {
            log.info(
                    "[MATH DEBUG] outcome=no_presence aiPos={} mentions={} total={} somScore=0.0",
                    aiPos,
                    mentions,
                    total);
            return 0.0;
        }
        double sourceWeight = metrics.sourceWeight() > RobustAuditMathUtil.EPSILON
                ? metrics.sourceWeight()
                : SOURCE_WEIGHT_LOW;
        double mentionDensity = total > 0 ? (double) mentions / (double) total : 0.0;
        double densityFactor = Math.min(1.0, mentionDensity / MENTION_DENSITY_SATURATION);
        double countFactor = Math.min(1.0, (double) mentions / MENTION_COUNT_SATURATION);
        double mentionSignal = clamp01(0.55 * densityFactor + 0.45 * countFactor);
        if (mentions == 0 && aiPos != null) {
            mentionSignal = Math.max(mentionSignal, 0.12);
        }
        double stuffingPenalty = 1.0;
        double brandSignalCore = sourceWeight * mentionSignal * stuffingPenalty;
        double otherPresence = Math.max(total - mentions, 0) > 0 ? 1.0 : 0.0;
        double otherSignal = otherPresence * SOURCE_WEIGHT_LOW;
        double textCoreDenom = brandSignalCore + otherSignal;
        double textCore = textCoreDenom > RobustAuditMathUtil.EPSILON
                ? clamp01(brandSignalCore / textCoreDenom)
                : 0.0;
        int sStar = (aiPos != null && mentions == 0) ? 10 : stageFromScorePercent(textCore * 100.0);
        double progressRate = aiPos != null
                ? VisibilityStageMapper.define(sStar, aiPos).progressRate()
                : 0.0;
        double brandSignal = presence * progressRate * brandSignalCore;
        double denom = brandSignal + otherSignal;
        double sentimentObserved = metrics.sentimentIntensity();
        if (denom <= RobustAuditMathUtil.EPSILON) {
            log.info(
                    "[MATH DEBUG] outcome=zero_denom aiPos={} sStar={} p={} mentions={} total={} presence={} mentionSignal={} density={} stuffingPenalty={} brandSignalCore={} brandSignal={} otherPresence={} otherSignal={} denom={} sourceWeight={} sentimentIntensity_observed={} textCore={} somScore=0.0",
                    aiPos,
                    sStar,
                    progressRate,
                    mentions,
                    total,
                    presence,
                    mentionSignal,
                    mentionDensity,
                    stuffingPenalty,
                    brandSignalCore,
                    brandSignal,
                    otherPresence,
                    otherSignal,
                    denom,
                    sourceWeight,
                    sentimentObserved,
                    textCore);
            return 0.0;
        }
        double somScore = clamp01(brandSignal / denom);
        log.info(
                "[MATH DEBUG] outcome=ok aiPos={} sStar={} p={} mentions={} total={} presence={} mentionSignal={} density={} stuffingPenalty={} brandSignalCore={} brandSignal={} otherPresence={} otherSignal={} denom={} sourceWeight={} sentimentIntensity_observed={} textCore={} somScore={}",
                aiPos,
                sStar,
                progressRate,
                mentions,
                total,
                presence,
                mentionSignal,
                mentionDensity,
                stuffingPenalty,
                brandSignalCore,
                brandSignal,
                otherPresence,
                otherSignal,
                denom,
                sourceWeight,
                sentimentObserved,
                textCore,
                somScore);
        return somScore;
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
        return Math.max(lo, Math.min(hi, v));
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
        return Math.subtractExact(11, inner);
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
        return Math.subtractExact(11, inner);
    }

    public record GbvsResult(double scorePercent, int visibilityStage, double modifiedZScore) {}
}
