package com.geo.analytics.domain.service;

import com.geo.analytics.application.dto.CompetitorResult;
import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.domain.enums.MatchStatus;
import com.geo.analytics.domain.enums.ModelType;
import com.geo.analytics.domain.exception.AiAnalysisTimeoutException;
import com.geo.analytics.domain.matching.RobustAuditMathUtil;
import com.geo.analytics.domain.matching.TokenizerManager;
import com.geo.analytics.domain.model.SomRawMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.StrictMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class InformationTheoryBasedAggregator {
    public static final String AGGREGATION_CALCULATION_VERSION = "V11_GEO_PURE";
    private static final Logger log = LoggerFactory.getLogger(InformationTheoryBasedAggregator.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal TARGET_TOTAL_PCT = new BigDecimal("100.00");
    private static final Pattern LEGAL_ENTITY = Pattern.compile(
            "^(株式会社|有限会社|合同会社|一般社団法人|財団法人)\\s*(.+)$|(.+?)\\s*(株式会社|有限会社|合同会社|一般社団法人|財団法人)$");
    private final TokenizerManager tokenizerManager;

    public InformationTheoryBasedAggregator(TokenizerManager tokenizerManager) {
        this.tokenizerManager = Objects.requireNonNull(tokenizerManager);
    }

    public List<GeoVisibilityCalculatorService.GbvsResult> finalizeGbvsBatchForJob(
            List<SomRawMetrics> rows,
            double lAvgJob,
            long plannedQueryCount) {
        return SomScoreCalculator.computeBatchForJob(rows, lAvgJob, plannedQueryCount);
    }

    public VerificationResponse aggregate(List<VerificationResponse> successes, VerificationRequest request) {
        if (successes == null || successes.isEmpty()) {
            throw new AiAnalysisTimeoutException();
        }
        int sampleCount = successes.size();
        var insights = new LinkedHashMap<ModelType, String>();
        for (var v : successes) {
            insights.put(v.modelType(), v.rawResponseJson());
        }
        SequencedMap<ModelType, String> insightView = Collections.unmodifiableSequencedMap(insights);
        double sourceWeight = GeoVisibilityCalculatorService.sourceWeightFromUrl(request.url());
        double sumBrandSignal = 0.0;
        double sumAllBrands = 0.0;
        for (var v : successes) {
            double presence = Boolean.TRUE.equals(v.brandMentioned()) ? 1.0 : 0.0;
            double sentiment = normalizeSentiment(v.sentimentIntensity());
            double brandSignal = presence * sourceWeight * sentiment;
            sumBrandSignal += brandSignal;
            sumAllBrands += brandSignal;
            for (var c : v.competitorResults()) {
                if (c.matchStatus() == MatchStatus.NO_MATCH) {
                    continue;
                }
                double cs = c.somScore() != null ? c.somScore() : 0.0;
                sumAllBrands += clampD(cs / 100.0, 0.0, 1.0);
            }
        }
        if (sumAllBrands <= RobustAuditMathUtil.EPSILON) {
            log.info(
                    "InformationTheoryBasedAggregator: aggregated finalSom forced to 0.0 because sumAllBrands={} <= EPSILON={}",
                    sumAllBrands,
                    RobustAuditMathUtil.EPSILON);
        }
        double finalSom = sumAllBrands > RobustAuditMathUtil.EPSILON
                ? (sumBrandSignal / sumAllBrands) * 100.0
                : 0.0;
        finalSom = halfEven2(clampD(finalSom, 0.0, 100.0));
        boolean brand = successes.stream().anyMatch(v -> Boolean.TRUE.equals(v.brandMentioned()));
        int sumMr = 0;
        long sumRp = 0L;
        int mentionedQueryCount = 0;
        int sumTok = 0;
        int sumOv = 0;
        int sumVs = 0;
        int countVs = 0;
        double sumSi = 0.0;
        double sumMz = 0.0;
        int countMz = 0;
        for (VerificationResponse v : successes) {
            Integer mr = v.mentionRank();
            if (mr != null) {
                sumMr += mr;
            }
            Integer rp = v.aiCitationPosition();
            if (rp != null) {
                sumRp += rp;
                mentionedQueryCount++;
            }
            sumTok += v.tokenCount();
            Integer ov = v.overallScore();
            if (ov != null) {
                sumOv += ov;
            }
            sumSi += v.sentimentIntensity();
            Integer vs = v.visibilityStage();
            if (vs != null) {
                sumVs += vs;
                countVs++;
            }
            Double mz = v.modifiedZScore();
            if (mz != null) {
                sumMz += mz;
                countMz++;
            }
        }
        double sumGbvsNorm = 0.0;
        int gbvsNormCount = 0;
        for (VerificationResponse v : successes) {
            if (v.gbvsNormalizedScore() != null) {
                sumGbvsNorm += v.gbvsNormalizedScore();
                gbvsNormCount++;
            }
        }
        Double avgGbvsNormalized = gbvsNormCount > 0 ? halfEven2(sumGbvsNorm / gbvsNormCount) : null;
        int avgMr = sumMr / sampleCount;
        Integer avgAiCitationPosition = mentionedQueryCount > 0
                ? roundHalfUpToInt((double) sumRp / mentionedQueryCount)
                : null;
        int avgTok = sumTok / sampleCount;
        int avgOv = sumOv / sampleCount;
        double avgSi = sumSi / (double) sampleCount;
        var first = successes.getFirst();
        Integer avgVs = countVs > 0 ? sumVs / countVs : first.visibilityStage();
        Double avgMz = countMz > 0 ? sumMz / countMz : first.modifiedZScore();
        Map<String, EntityAggregate> byEntity = successes.stream()
                .flatMap(v -> v.competitorResults().stream()
                        .filter(c -> c.matchStatus() != MatchStatus.NO_MATCH)
                        .map(c -> BrandContrib.from(tokenizerManager, c)))
                .collect(Collectors.groupingBy(
                        BrandContrib::groupingKey,
                        Collectors.collectingAndThen(Collectors.toList(), EntityAggregate::fromContribs)));
        List<EntityAggregate> entities = byEntity.values().stream()
                .sorted(Comparator.comparing(EntityAggregate::totalPoints, Comparator.reverseOrder())
                        .thenComparing(EntityAggregate::canonicalLabel))
                .toList();
        int entityCount = entities.size();
        int[] competitionRanks = new int[entityCount];
        for (int i = 0; i < entityCount; i++) {
            BigDecimal pi = entities.get(i).totalPoints();
            int better = 0;
            for (int j = 0; j < entityCount; j++) {
                if (entities.get(j).totalPoints().compareTo(pi) > 0) {
                    better++;
                }
            }
            competitionRanks[i] = better + 1;
        }
        BigDecimal totalPoints = entities.stream()
                .map(EntityAggregate::totalPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> somPercents = new ArrayList<>(entityCount);
        if (totalPoints.compareTo(BigDecimal.ZERO) == 0) {
            for (int i = 0; i < entityCount; i++) {
                somPercents.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
        } else {
            for (int i = 0; i < entityCount; i++) {
                somPercents.add(entities.get(i).totalPoints()
                        .multiply(HUNDRED)
                        .divide(totalPoints, 2, RoundingMode.HALF_UP));
            }
            BigDecimal sumPct = somPercents.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal drift = TARGET_TOTAL_PCT.subtract(sumPct);
            if (drift.compareTo(BigDecimal.ZERO) != 0) {
                int absorbIdx = 0;
                for (int i = 1; i < entityCount; i++) {
                    BigDecimal pi = entities.get(i).totalPoints();
                    BigDecimal pa = entities.get(absorbIdx).totalPoints();
                    int cmpPts = pi.compareTo(pa);
                    if (cmpPts > 0) {
                        absorbIdx = i;
                    } else if (cmpPts == 0) {
                        String a = entities.get(absorbIdx).canonicalLabel();
                        String b = entities.get(i).canonicalLabel();
                        if (a == null) {
                            absorbIdx = i;
                        } else if (b != null && b.compareTo(a) < 0) {
                            absorbIdx = i;
                        }
                    }
                }
                somPercents.set(absorbIdx, somPercents.get(absorbIdx).add(drift));
            }
        }
        List<CompetitorResult> merged = new ArrayList<>(entityCount);
        for (int i = 0; i < entityCount; i++) {
            EntityAggregate e = entities.get(i);
            int rank = competitionRanks[i];
            int stageRaw = StrictMath.subtractExact(11, rank);
            int visStage = stageRaw < 1 ? 1 : (stageRaw > 10 ? 10 : stageRaw);
            double somVal = somPercents.get(i).doubleValue();
            long tm = e.totalMentions();
            int nounAgg = tm > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) tm;
            merged.add(new CompetitorResult(
                    e.canonicalLabel(),
                    halfEven2(clampD(somVal, 0.0, 100.0)),
                    rank,
                    visStage,
                    e.aggregatedStatus(),
                    nounAgg));
        }
        merged.sort(Comparator.comparing(
                (CompetitorResult cr) -> cr.aiCitationPosition() != null ? cr.aiCitationPosition() : Integer.MAX_VALUE,
                Comparator.naturalOrder())
                .thenComparing(
                        (CompetitorResult cr) -> cr.somScore() != null ? cr.somScore() : 0.0,
                        Comparator.reverseOrder())
                .thenComparing(CompetitorResult::competitorLabel, Comparator.nullsFirst(Comparator.naturalOrder())));
        return new VerificationResponse(
                first.modelType(),
                first.rawResponseJson(),
                finalSom,
                brand,
                avgMr,
                avgOv,
                avgTok,
                avgAiCitationPosition,
                avgSi,
                first.resolvedEntityLabel(),
                avgVs,
                avgMz,
                AGGREGATION_CALCULATION_VERSION,
                List.copyOf(merged),
                insightView,
                avgGbvsNormalized);
    }

    public String pipeline(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFKC);
        normalized = normalized.toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("[\\p{Punct}\\s]+", "");
        var m = LEGAL_ENTITY.matcher(normalized);
        if (m.matches() && m.group(1) != null) {
            var corp = m.group(1);
            var rest = m.group(2);
            if (rest != null) {
                return rest + corp;
            }
        }
        return normalized;
    }

    private static double normalizeSentiment(double sentimentIntensity) {
        if (Double.isNaN(sentimentIntensity) || Double.isInfinite(sentimentIntensity)) {
            return 1.0;
        }
        double s = clampD(sentimentIntensity, -1.0, 1.0);
        return StrictMath.fma(0.5, s, 1.0);
    }

    private static double clampD(double v, double lo, double hi) {
        return StrictMath.max(lo, StrictMath.min(hi, v));
    }

    private static double halfEven2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
    }

    private static Integer roundHalfUpToInt(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private record BrandContrib(String groupingKey, String surfaceLabel, BigDecimal points, long mentions, MatchStatus status) {
        static BrandContrib from(TokenizerManager tm, CompetitorResult c) {
            String surface = c.competitorLabel() != null ? c.competitorLabel() : "";
            List<String> tok = tm.tokenizeToNormalizedList(surface);
            String gk = tok.isEmpty() ? surface : String.join("", tok);
            double sc = c.somScore() != null ? c.somScore() : 0.0;
            sc = clampD(sc, 0.0, 100.0);
            long mentions = c.nounCount();
            if (mentions < 0L) {
                mentions = 0L;
            }
            return new BrandContrib(gk, surface, BigDecimal.valueOf(sc), mentions, c.matchStatus());
        }
    }

    private record EntityAggregate(String canonicalLabel, BigDecimal totalPoints, MatchStatus aggregatedStatus, long totalMentions) {
        static EntityAggregate fromContribs(List<BrandContrib> rows) {
            BigDecimal sumPts = rows.stream()
                    .collect(Collectors.toMap(
                            BrandContrib::surfaceLabel,
                            BrandContrib::points,
                            BigDecimal::add))
                    .values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long sumMentions = rows.stream().mapToLong(BrandContrib::mentions).sum();
            MatchStatus st = MatchStatus.AUTO_MATCH;
            String canonical = rows.stream()
                    .map(BrandContrib::surfaceLabel)
                    .filter(Objects::nonNull)
                    .distinct()
                    .min(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
                    .orElse("");
            return new EntityAggregate(canonical, sumPts, st, sumMentions);
        }
    }
}
