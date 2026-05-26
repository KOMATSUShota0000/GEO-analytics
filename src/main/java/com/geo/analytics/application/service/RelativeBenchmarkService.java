package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.RubricCriterionId;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.AuditRubricResultRepository;
import com.geo.analytics.infrastructure.repository.AuditRubricResultRepository.RubricBenchmarkAggregate;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import com.geo.analytics.web.dto.AnalyticsSummaryResponse;
import com.geo.analytics.web.dto.CompetitorSharePoint;
import com.geo.analytics.web.dto.RelativeBenchmarkResponse;
import com.geo.analytics.web.dto.RelativeBenchmarkRow;
import com.geo.analytics.web.dto.TrendDataPoint;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 相対評価（ベンチマーク）の実データを組み立てる。
 *
 * <p>3指標すべて既存の実テーブル由来に限定し、スタブ/モックは一切返さない（CLAUDE.md コア原則）。
 * 競合比較は Pro 以上の機能であり、対象外プランや未解析時は偽値ではなく
 * {@code locked}/{@code available=false} を返す。
 */
@Service
@Transactional(readOnly = true)
public class RelativeBenchmarkService {

    private static final String CRITERION_STRUCTURED = RubricCriterionId.MACHINE_READABILITY_SIGNAL.name();
    private static final String CRITERION_ENTITY = RubricCriterionId.ENTITY_BIOGRAPHY.name();
    private static final List<String> TARGET_CRITERIA = List.of(CRITERION_STRUCTURED, CRITERION_ENTITY);
    private static final RelativeBenchmarkResponse UNAVAILABLE =
            new RelativeBenchmarkResponse(false, false, List.of());
    private static final RelativeBenchmarkResponse LOCKED =
            new RelativeBenchmarkResponse(true, false, List.of());

    private final AnalyticsAggregationService analyticsAggregationService;
    private final JobRepository jobRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final AuditRubricResultRepository auditRubricResultRepository;
    private final JdbcTemplate jdbcTemplate;

    public RelativeBenchmarkService(
            AnalyticsAggregationService analyticsAggregationService,
            JobRepository jobRepository,
            AuditHistoryRepository auditHistoryRepository,
            AuditRubricResultRepository auditRubricResultRepository,
            JdbcTemplate jdbcTemplate) {
        this.analyticsAggregationService = analyticsAggregationService;
        this.jobRepository = jobRepository;
        this.auditHistoryRepository = auditHistoryRepository;
        this.auditRubricResultRepository = auditRubricResultRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public RelativeBenchmarkResponse getBenchmark(UUID projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId is required");
        }
        Optional<AnalyticsSummaryResponse> summaryOpt =
                analyticsAggregationService.summarizeProject(projectId);
        if (summaryOpt.isEmpty()) {
            return UNAVAILABLE;
        }
        AnalyticsSummaryResponse summary = summaryOpt.get();
        boolean pro = summary.subscriptionPlan() != null
                && summary.subscriptionPlan().usesProTierFeatures();
        if (!pro) {
            return LOCKED;
        }
        UUID workspaceId = TenantContextHolder
                .getTenantId()
                .orElseThrow(() -> new EntityNotFoundException("project"));
        Map<String, double[]> rubric = TenantPlanScope.executeWithTenant(workspaceId, () -> {
            verifyProjectVisible(projectId);
            return loadRubricAverages(projectId);
        });
        return assemble(summary, rubric);
    }

    private void verifyProjectVisible(UUID projectId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projects WHERE id = ?", Integer.class, projectId);
        if (count == null || count.intValue() == 0) {
            throw new EntityNotFoundException("project");
        }
    }

    /** criterionId -> {selfAvg, competitorAvg}（欠損は NaN）。 */
    private Map<String, double[]> loadRubricAverages(UUID projectId) {
        Optional<JobEntity> latestJob =
                jobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
        if (latestJob.isEmpty()) {
            return Map.of();
        }
        List<AuditHistoryEntity> histories =
                auditHistoryRepository.findByJobId(latestJob.get().getId());
        List<UUID> ids = new ArrayList<>(histories.size());
        for (AuditHistoryEntity h : histories) {
            ids.add(h.getId());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<RubricBenchmarkAggregate> aggregates =
                auditRubricResultRepository.aggregateByCriterion(ids, TARGET_CRITERIA);
        return foldAggregates(aggregates);
    }

    static Map<String, double[]> foldAggregates(List<RubricBenchmarkAggregate> aggregates) {
        Map<String, double[]> out = new java.util.HashMap<>();
        for (RubricBenchmarkAggregate a : aggregates) {
            if (a.getCriterionId() == null || a.getSelfFlag() == null || a.getAvgScore() == null) {
                continue;
            }
            double[] pair = out.computeIfAbsent(
                    a.getCriterionId(), k -> new double[] {Double.NaN, Double.NaN});
            int idx = a.getSelfFlag() ? 0 : 1;
            pair[idx] = a.getAvgScore().doubleValue();
        }
        return out;
    }

    static RelativeBenchmarkResponse assemble(
            AnalyticsSummaryResponse summary, Map<String, double[]> rubric) {
        Double selfSom = latestSom(summary.trendData());
        Double competitorSom = topCompetitorShare(summary.competitorShares());

        List<RelativeBenchmarkRow> rows = new ArrayList<>(3);
        rows.add(new RelativeBenchmarkRow(
                "AI言及シェア",
                selfSom != null ? fmtPercent(selfSom) : "データなし",
                competitorSom != null ? fmtPercent(competitorSom) : "データなし",
                selfSom != null && competitorSom != null && selfSom < competitorSom));
        rows.add(rubricRow(
                "構造化シグナル密度",
                rubric.get(CRITERION_STRUCTURED),
                RubricCriterionId.MACHINE_READABILITY_SIGNAL.maxScore()));
        rows.add(rubricRow(
                "エンティティ解像度",
                rubric.get(CRITERION_ENTITY),
                RubricCriterionId.ENTITY_BIOGRAPHY.maxScore()));

        boolean hasSelfData = selfSom != null
                || hasSelf(rubric.get(CRITERION_STRUCTURED))
                || hasSelf(rubric.get(CRITERION_ENTITY));
        if (!hasSelfData) {
            return UNAVAILABLE;
        }
        return new RelativeBenchmarkResponse(false, true, List.copyOf(rows));
    }

    private static boolean hasSelf(double[] pair) {
        return pair != null && !Double.isNaN(pair[0]);
    }

    private static RelativeBenchmarkRow rubricRow(String label, double[] pair, double maxScore) {
        Double self = pair != null && !Double.isNaN(pair[0]) ? pair[0] : null;
        Double comp = pair != null && !Double.isNaN(pair[1]) ? pair[1] : null;
        boolean gap = self != null && comp != null && self < comp;
        return new RelativeBenchmarkRow(
                label,
                self != null ? qualitative(self / maxScore) : "データなし",
                comp != null ? qualitative(comp / maxScore) : "データなし",
                gap);
    }

    private static String qualitative(double ratio) {
        if (ratio >= 0.7d) {
            return "良好";
        }
        if (ratio >= 0.4d) {
            return "普通";
        }
        return "要強化";
    }

    private static Double latestSom(List<TrendDataPoint> trend) {
        if (trend == null || trend.isEmpty()) {
            return null;
        }
        for (int i = trend.size() - 1; i >= 0; i--) {
            Double v = trend.get(i).averageSomScore();
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static Double topCompetitorShare(List<CompetitorSharePoint> shares) {
        if (shares == null || shares.isEmpty()) {
            return null;
        }
        Double max = null;
        for (CompetitorSharePoint s : shares) {
            if (s.share() == null) {
                continue;
            }
            if (max == null || s.share() > max) {
                max = s.share();
            }
        }
        return max;
    }

    private static String fmtPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }
}
