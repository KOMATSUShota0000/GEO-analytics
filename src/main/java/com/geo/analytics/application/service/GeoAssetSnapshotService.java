package com.geo.analytics.application.service;

import com.geo.analytics.application.snapshot.JobAuditMetricsExtractor;
import com.geo.analytics.application.snapshot.JobAuditMetricsExtractor.SnapshotMetricInputs;
import com.geo.analytics.domain.entity.GeoAssetSnapshotEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.scoring.ScoreBreakdown;
import com.geo.analytics.domain.scoring.ScoringService;
import com.geo.analytics.infrastructure.repository.GeoAssetSnapshotRepository;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
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
    private final ScoringService scoringService;
    private final JobAuditMetricsExtractor jobAuditMetricsExtractor;

    public GeoAssetSnapshotService(
            JobRepository jobRepository,
            WorkspaceRepository workspaceRepository,
            GeoAssetSnapshotRepository geoAssetSnapshotRepository,
            ScoringService scoringService,
            JobAuditMetricsExtractor jobAuditMetricsExtractor) {
        this.jobRepository = jobRepository;
        this.workspaceRepository = workspaceRepository;
        this.geoAssetSnapshotRepository = geoAssetSnapshotRepository;
        this.scoringService = scoringService;
        this.jobAuditMetricsExtractor = jobAuditMetricsExtractor;
    }

    public GeoAssetSnapshotEntity createSnapshot(UUID jobId, UUID projectId, UUID workspaceId) {
        WorkspaceEntity workspace =
                workspaceRepository.findById(workspaceId).orElseThrow(() -> new EntityNotFoundException("workspace"));
        JobEntity job = jobRepository.findById(jobId).orElseThrow(() -> new EntityNotFoundException("job"));
        if (!projectId.equals(job.getProjectId()) || !workspaceId.equals(job.getWorkspaceId())) {
            throw new IllegalStateException("job project or workspace mismatch");
        }
        SnapshotMetricInputs metrics = jobAuditMetricsExtractor.extract(jobId);
        ScoreBreakdown breakdown = scoringService.calculateFinalScore(
                metrics.aiAuditPercent(), metrics.meoTrustPercent(), metrics.machineReadabilityPercent());
        GeoAssetSnapshotEntity entity = new GeoAssetSnapshotEntity();
        entity.setOrganizationId(workspace.getOrganizationId());
        entity.setProjectId(projectId);
        entity.setSnapshotDate(LocalDate.now(ZoneId.of("Asia/Tokyo")));
        entity.setReadinessScore(breakdown.geoReadinessScore());
        entity.setLocalTrustCount(metrics.localTrustCount());
        return geoAssetSnapshotRepository.save(entity);
    }
}
