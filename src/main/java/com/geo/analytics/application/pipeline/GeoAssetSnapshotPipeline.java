package com.geo.analytics.application.pipeline;

import com.geo.analytics.application.service.GeoAssetSnapshotService;
import com.geo.analytics.domain.entity.GeoAssetSnapshotEntity;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.event.ProjectAuditCompletedEvent;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class GeoAssetSnapshotPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(GeoAssetSnapshotPipeline.class);

    private final WorkspaceRepository workspaceRepository;
    private final GeoAssetSnapshotService geoAssetSnapshotService;

    public GeoAssetSnapshotPipeline(WorkspaceRepository workspaceRepository, GeoAssetSnapshotService geoAssetSnapshotService) {
        this.workspaceRepository = workspaceRepository;
        this.geoAssetSnapshotService = geoAssetSnapshotService;
    }

    @EventListener
    public void onProjectAuditCompleted(ProjectAuditCompletedEvent projectAuditCompletedEvent) {
        UUID jobId = projectAuditCompletedEvent.jobId();
        UUID projectId = projectAuditCompletedEvent.projectId();
        UUID workspaceId = projectAuditCompletedEvent.workspaceId();
        Thread.ofVirtual()
                .start(() -> {
                    WorkspaceEntity workspace = workspaceRepository.findById(workspaceId).orElse(null);
                    if (workspace == null) {
                        LOG.warn("GeoAssetSnapshotPipeline skip workspace missing workspaceId={} jobId={}", workspaceId, jobId);
                        return;
                    }
                    UUID organizationId = workspace.getOrganizationId();
                    LOG.info(
                            "GeoAssetSnapshotPipeline virtualThread bind workspaceId={} organizationId={} jobId={}",
                            workspaceId,
                            organizationId,
                            jobId);
                    TenantPlanScope.executeWithTenantOrganizationAndPlan(
                            workspaceId,
                            organizationId,
                            workspace.getSubscriptionPlan(),
                            () -> {
                                LOG.info(
                                        "GeoAssetSnapshotPipeline scoped tenant context organizationId={}",
                                        TenantContextHolder.getOrganizationId().orElse(null));
                                GeoAssetSnapshotEntity saved = geoAssetSnapshotService.createSnapshot(jobId, projectId, workspaceId);
                                LOG.info(
                                        "GeoAssetSnapshotPipeline saved snapshot id={} organizationId={}",
                                        saved.getId(),
                                        saved.getOrganizationId());
                            });
                });
    }
}
