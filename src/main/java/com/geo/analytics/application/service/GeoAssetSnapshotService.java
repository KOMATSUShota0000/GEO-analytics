package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.JobAnalysisAggregate;
import com.geo.analytics.application.snapshot.JobAuditMetricsExtractor;
import com.geo.analytics.application.snapshot.JobAuditMetricsExtractor.SnapshotMetricInputs;
import com.geo.analytics.domain.entity.GeoAssetSnapshotEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.service.GeoVisibilityCalculatorService;
import com.geo.analytics.infrastructure.repository.GeoAssetSnapshotRepository;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.web.dto.ScoreBreakdown;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GeoAssetSnapshotService {

    private final JobRepository jobRepository;
    private final WorkspaceRepository workspaceRepository;
    private final GeoAssetSnapshotRepository geoAssetSnapshotRepository;
    private final JobPersistenceService jobPersistenceService;
    private final JobAuditMetricsExtractor jobAuditMetricsExtractor;

    public GeoAssetSnapshotService(
            JobRepository jobRepository,
            WorkspaceRepository workspaceRepository,
            GeoAssetSnapshotRepository geoAssetSnapshotRepository,
            JobPersistenceService jobPersistenceService,
            JobAuditMetricsExtractor jobAuditMetricsExtractor) {
        this.jobRepository = jobRepository;
        this.workspaceRepository = workspaceRepository;
        this.geoAssetSnapshotRepository = geoAssetSnapshotRepository;
        this.jobPersistenceService = jobPersistenceService;
        this.jobAuditMetricsExtractor = jobAuditMetricsExtractor;
    }

    public GeoAssetSnapshotEntity createSnapshot(UUID jobId, UUID projectId, UUID workspaceId) {
        WorkspaceEntity workspace =
                workspaceRepository.findById(workspaceId).orElseThrow(() -> new EntityNotFoundException("workspace"));
        JobEntity job = jobRepository.findById(jobId).orElseThrow(() -> new EntityNotFoundException("job"));
        if (!projectId.equals(job.getProjectId()) || !workspaceId.equals(job.getWorkspaceId())) {
            throw new IllegalStateException("job project or workspace mismatch");
        }
        // スナップショットのスコアは per-job レポートと同一の V13 GEO Readiness（ScoreBreakdown.finalScore）を用いる。
        // 旧 ScoringService(50/25/25 の avgSoM 退化スコア)は廃止し、単一ソースに統一（バージョン併記の前提・ADR-027）。
        JobAnalysisAggregate aggregate = jobPersistenceService.findJobAnalysisAggregate(jobId);
        ScoreBreakdown breakdown = jobPersistenceService
                .loadJobAnalysisAttachment(jobId, aggregate.auditHistories())
                .scoreBreakdown();
        String calculationVersion = breakdown.calculationVersion() != null
                ? breakdown.calculationVersion()
                : GeoVisibilityCalculatorService.CALCULATION_VERSION;
        // localTrustCount は従来どおり監査メトリクスから供給（スコア軸とは独立の表示用カウント）。
        SnapshotMetricInputs metrics = jobAuditMetricsExtractor.extract(jobId);
        GeoAssetSnapshotEntity entity = new GeoAssetSnapshotEntity();
        entity.setOrganizationId(workspace.getOrganizationId());
        entity.setProjectId(projectId);
        entity.setSnapshotDate(LocalDate.now(ZoneId.of("Asia/Tokyo")));
        entity.setReadinessScore(breakdown.finalScore());
        entity.setCalculationVersion(calculationVersion);
        entity.setLocalTrustCount(metrics.localTrustCount());
        return geoAssetSnapshotRepository.save(entity);
    }
}
