package com.geo.analytics.application.service;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import com.geo.analytics.web.dto.AnalyticsSummaryResponse;
import com.geo.analytics.web.dto.TrendDataPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
@Transactional(readOnly = true)
public class AnalyticsAggregationService {
    private final JdbcTemplate jdbcTemplate;
    private final AuditHistoryRepository auditHistoryRepository;
    private final JobRepository jobRepository;
    public AnalyticsAggregationService(
            JdbcTemplate jdbcTemplate,
            AuditHistoryRepository auditHistoryRepository,
            JobRepository jobRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditHistoryRepository = auditHistoryRepository;
        this.jobRepository = jobRepository;
    }
    public Optional<AnalyticsSummaryResponse> summarizeProject(UUID projectId) {
        List<String> rows = jdbcTemplate.query(
            "SELECT tenant_id FROM projects WHERE id = ?",
            ps -> ps.setObject(1, projectId),
            (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) {
            return Optional.empty();
        }
        UUID workspaceId = UUID.fromString(rows.get(0));
        return Optional.of(TenantPlanScope.executeWithTenant(workspaceId, () -> buildSummary(projectId)));
    }
    private AnalyticsSummaryResponse buildSummary(UUID projectId) {
        List<AuditHistoryEntity> histories = auditHistoryRepository.findByProject_IdOrderByAuditDateAsc(projectId);
        List<TrendDataPoint> trend = aggregateTrend(histories);
        Optional<JobEntity> latestJob = jobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
        SubscriptionPlan plan = latestJob.map(JobEntity::getAppliedPlan).orElse(SubscriptionPlan.STANDARD);
        if (plan == null) {
            plan = SubscriptionPlan.STANDARD;
        }
        return new AnalyticsSummaryResponse(trend, plan);
    }
    private List<TrendDataPoint> aggregateTrend(List<AuditHistoryEntity> histories) {
        return histories.stream()
            .collect(Collectors.groupingBy(AuditHistoryEntity::getAuditDate, Collectors.toList()))
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> {
                List<AuditHistoryEntity> group = e.getValue();
                double somAvg = group.stream()
                    .map(AuditHistoryEntity::getSomScore)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0d);
                List<Integer> over = group.stream()
                    .map(AuditHistoryEntity::getOverallScore)
                    .filter(v -> v != null)
                    .toList();
                Double overallAvg = null;
                if (!over.isEmpty()) {
                    overallAvg = roundOneDecimal(
                        over.stream().mapToInt(Integer::intValue).average().orElse(0d));
                }
                return new TrendDataPoint(
                    e.getKey(),
                    roundOneDecimal(somAvg),
                    overallAvg);
            })
            .toList();
    }
    private static double roundOneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
