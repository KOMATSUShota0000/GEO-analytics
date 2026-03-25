package com.geo.analytics.application.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.CompetitorShareEntry;
import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import com.geo.analytics.web.dto.AnalyticsSummaryResponse;
import com.geo.analytics.web.dto.CompetitorSharePoint;
import com.geo.analytics.web.dto.TrendDataPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
@Transactional(readOnly = true)
public class AnalyticsAggregationService {
    private final JdbcTemplate jdbcTemplate;
    private final AuditHistoryRepository auditHistoryRepository;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    public AnalyticsAggregationService(
            JdbcTemplate jdbcTemplate,
            AuditHistoryRepository auditHistoryRepository,
            JobRepository jobRepository,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditHistoryRepository = auditHistoryRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }
    public Optional<AnalyticsSummaryResponse> summarizeProject(UUID projectId) {
        List<UUID> rows = jdbcTemplate.query(
            "SELECT workspace_id FROM projects WHERE id = ?",
            ps -> ps.setObject(1, projectId),
            (rs, rowNum) -> rs.getObject(1, UUID.class));
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        UUID workspaceId = rows.get(0);
        return Optional.of(TenantContext.executeWithTenant(workspaceId, () -> buildSummary(projectId)));
    }
    private AnalyticsSummaryResponse buildSummary(UUID projectId) {
        List<AuditHistoryEntity> histories = auditHistoryRepository.findByProject_IdOrderByAuditDateAsc(projectId);
        List<TrendDataPoint> trend = aggregateTrend(histories);
        Optional<JobEntity> latestJob = jobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);
        SubscriptionPlan plan = latestJob.map(JobEntity::getSubscriptionPlan).orElse(SubscriptionPlan.STANDARD);
        if (plan == null) {
            plan = SubscriptionPlan.STANDARD;
        }
        List<CompetitorSharePoint> shares = List.of();
        if (plan == SubscriptionPlan.PRO && latestJob.isPresent()) {
            shares = extractCompetitorShares(latestJob.get().getId());
        }
        return new AnalyticsSummaryResponse(trend, shares, plan);
    }
    private List<TrendDataPoint> aggregateTrend(List<AuditHistoryEntity> histories) {
        return histories.stream()
            .collect(Collectors.groupingBy(AuditHistoryEntity::getAuditDate, Collectors.toList()))
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> {
                List<AuditHistoryEntity> group = e.getValue();
                double somAvg = group.stream().mapToDouble(AuditHistoryEntity::getSomScore).average().orElse(0d);
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
    private List<CompetitorSharePoint> extractCompetitorShares(UUID jobId) {
        List<AuditHistoryEntity> rows = auditHistoryRepository.findByJobId(jobId);
        Map<String, List<Double>> acc = new HashMap<>();
        for (AuditHistoryEntity row : rows) {
            try {
                ConsultantOutputData data = objectMapper.readValue(row.getRawResponse(), ConsultantOutputData.class);
                if (data.competitorComparison() == null) {
                    continue;
                }
                for (CompetitorShareEntry entry : data.competitorComparison()) {
                    if (entry.competitorName() == null || entry.share() == null) {
                        continue;
                    }
                    acc.computeIfAbsent(entry.competitorName(), k -> new ArrayList<>()).add(entry.share());
                }
            } catch (JsonProcessingException ignored) {
            }
        }
        return acc.entrySet()
            .stream()
            .map(en -> new CompetitorSharePoint(
                en.getKey(),
                roundOneDecimal(
                    en.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0d))))
            .sorted(Comparator.comparing(CompetitorSharePoint::name))
            .toList();
    }
    private static double roundOneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
