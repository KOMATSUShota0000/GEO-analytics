package com.geo.analytics.domain.logic;

import com.geo.analytics.domain.model.GeoRagEvidence;
import com.geo.analytics.domain.model.GeoEvidenceRow;
import java.lang.StrictMath;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 検索オーガニック行から鮮度・類似度に基づき精鋭 N 件の {@link GeoRagEvidence} のみを返す。下位候補は {@link
 * GeoRagEvidence} に詰め替えない。
 */
@Component
public class GeoEvidenceRanker {

    /** 鮮度減衰 $\lambda$（年⁻¹）。 */
    public static final double DEFAULT_FRESHNESS_LAMBDA = 0.5d;

    /** 公開日不明時に仮定する経過年（最大減衰）。 */
    public static final int DEFAULT_MAX_YEARS_WHEN_DATE_UNKNOWN = 10;

    public static final double DEFAULT_SNIPPET_DUPLICATE_THRESHOLD = 0.88d;
    public static final int DEFAULT_MAX_PER_DOMAIN = 2;

    private static final KeywordSimilarityScorer SNIPPET_PAIR_SIMILARITY = new KeywordSimilarityScorer();

    private final SimilarityScorer similarityScorer;
    private final double wSim;
    private final double freshnessLambda;
    private final int maxYearsWhenDateUnknown;
    private final double snippetDuplicateThreshold;

    @Autowired
    public GeoEvidenceRanker(SimilarityScorer similarityScorer) {
        this(
                similarityScorer,
                1.0d,
                DEFAULT_FRESHNESS_LAMBDA,
                DEFAULT_MAX_YEARS_WHEN_DATE_UNKNOWN,
                DEFAULT_SNIPPET_DUPLICATE_THRESHOLD);
    }

    public GeoEvidenceRanker(
            SimilarityScorer similarityScorer,
            double wSim,
            double freshnessLambda,
            int maxYearsWhenDateUnknown,
            double snippetDuplicateThreshold) {
        this.similarityScorer = Objects.requireNonNull(similarityScorer, "similarityScorer");
        this.wSim = sanitizeWeight(wSim);
        this.freshnessLambda =
                (!Double.isFinite(freshnessLambda) || freshnessLambda < 0.0d) ? 0.0d : freshnessLambda;
        this.maxYearsWhenDateUnknown =
                maxYearsWhenDateUnknown < 1 ? DEFAULT_MAX_YEARS_WHEN_DATE_UNKNOWN : maxYearsWhenDateUnknown;
        this.snippetDuplicateThreshold =
                (!Double.isFinite(snippetDuplicateThreshold) || snippetDuplicateThreshold < 0.0d
                                || snippetDuplicateThreshold > 1.0d)
                        ? DEFAULT_SNIPPET_DUPLICATE_THRESHOLD
                        : snippetDuplicateThreshold;
    }

    /**
     * @param query 検索クエリ
     * @param rows 最大100件程度のオーガニック行
     * @param maxEvidenceCount 返却する証拠の上限 N（正の整数以外は空リスト）
     * @param maxPerDomain 同一ホストあたりの上限 k（1 未満は 1 に繰り上げ）
     * @param defaultRelevanceCategory 行にカテゴリが無い場合のラベル（null は空文字）
     */
    public List<GeoRagEvidence> provideEvidences(
            String query,
            List<GeoEvidenceRow> rows,
            int maxEvidenceCount,
            int maxPerDomain,
            String defaultRelevanceCategory) {
        return provideEvidences(query, rows, maxEvidenceCount, maxPerDomain, defaultRelevanceCategory, null);
    }

    /**
     * @param referenceInstant 鮮度計算の基準時刻（null なら {@link Instant#now()}）
     */
    public List<GeoRagEvidence> provideEvidences(
            String query,
            List<GeoEvidenceRow> rows,
            int maxEvidenceCount,
            int maxPerDomain,
            String defaultRelevanceCategory,
            Instant referenceInstant) {
        String q = query == null ? "" : query;
        String categoryBase = defaultRelevanceCategory == null ? "" : defaultRelevanceCategory;
        if (rows == null || rows.isEmpty() || maxEvidenceCount <= 0) {
            return List.of();
        }
        Instant ref = referenceInstant == null ? Instant.now() : referenceInstant;
        int k = Math.max(1, maxPerDomain);

        List<RankedRow> ranked = new ArrayList<>(rows.size());
        for (GeoEvidenceRow row : rows) {
            if (row == null) {
                continue;
            }
            String url = row.url() == null ? "" : row.url();
            if (url.isEmpty()) {
                continue;
            }
            String title = row.title() == null ? "" : row.title();
            String snippet = row.snippet() == null ? "" : row.snippet();
            String content = title + " " + snippet;
            double sSim = safeSim(similarityScorer.score(q, content));
            double years = yearsElapsed(row.publishedAt(), ref);
            double decay = StrictMath.exp(StrictMath.fma(-freshnessLambda, years, 0.0d));
            if (!Double.isFinite(decay) || decay < 0.0d) {
                decay = 0.0d;
            }
            double priority = StrictMath.fma(sSim * wSim, decay, 0.0d);
            if (!Double.isFinite(priority) || priority < 0.0d) {
                priority = 0.0d;
            }
            String rel = row.rowRelevanceCategory().filter(s -> s != null && !s.isEmpty()).orElse(categoryBase);
            ranked.add(new RankedRow(row, priority, rel));
        }

        ranked.sort(
                Comparator.comparingDouble(RankedRow::priority)
                        .reversed()
                        .thenComparing(r -> r.row().url(), Comparator.nullsFirst(String::compareTo)));

        Map<String, Integer> perHost = new HashMap<>();
        List<GeoRagEvidence> packed = new ArrayList<>(Math.min(maxEvidenceCount, ranked.size()));

        for (RankedRow rr : ranked) {
            if (packed.size() >= maxEvidenceCount) {
                break;
            }
            GeoEvidenceRow row = rr.row();
            String host = hostKey(row.url());
            int used = perHost.getOrDefault(host, 0);
            if (used >= k) {
                continue;
            }
            boolean dup = false;
            for (GeoRagEvidence kept : packed) {
                if (snippetTooSimilar(row.title(), row.snippet(), kept.title(), kept.snippet())) {
                    dup = true;
                    break;
                }
            }
            if (dup) {
                continue;
            }
            perHost.put(host, used + 1);
            packed.add(
                    new GeoRagEvidence(
                            row.url(),
                            row.title() == null ? "" : row.title(),
                            row.snippet() == null ? "" : row.snippet(),
                            rr.priority(),
                            row.publishedAt() == null ? Optional.empty() : row.publishedAt(),
                            rr.relevanceCategory() == null ? "" : rr.relevanceCategory()));
        }

        return List.copyOf(packed);
    }

    private boolean snippetTooSimilar(String titleA, String snipA, String titleB, String snipB) {
        String blockA = (titleA == null ? "" : titleA) + '' + (snipA == null ? "" : snipA);
        String blockB = (titleB == null ? "" : titleB) + '' + (snipB == null ? "" : snipB);
        double sim = SNIPPET_PAIR_SIMILARITY.score(blockA, blockB);
        if (!Double.isFinite(sim)) {
            return false;
        }
        return sim >= snippetDuplicateThreshold;
    }

    private static double safeSim(double raw) {
        if (Double.isNaN(raw) || raw < 0.0d) {
            return 0.0d;
        }
        if (raw > 1.0d) {
            return 1.0d;
        }
        return raw;
    }

    private static double sanitizeWeight(double w) {
        if (Double.isNaN(w) || w < 0.0d) {
            return 1.0d;
        }
        if (!Double.isFinite(w)) {
            return 1.0d;
        }
        return w;
    }

    /**
     * $t_{pub}$ から参照時刻までの経過年（非負）。{@code published} が空なら {@link
     * #maxYearsWhenDateUnknown}。未来日付は 0。
     */
    private double yearsElapsed(Optional<Instant> published, Instant reference) {
        if (published == null || published.isEmpty()) {
            return (double) maxYearsWhenDateUnknown;
        }
        Instant p = published.get();
        if (p.isAfter(reference)) {
            return 0.0d;
        }
        long seconds = Duration.between(p, reference).getSeconds();
        if (seconds < 0L) {
            return 0.0d;
        }
        double days = seconds / 86400.0d;
        return days / 365.25d;
    }

    static String hostKey(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return "";
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private record RankedRow(GeoEvidenceRow row, double priority, String relevanceCategory) {}
}
