package com.geo.analytics.application.service;

import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class ProjectLastAuditService {
    private final ProjectRepository projectRepository;

    public ProjectLastAuditService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * {@link com.geo.analytics.application.service.ProjectAuditEventListener} の {@code @Async} から呼ばれるため、
     * ここで明示的にトランザクションを開始する（リスナー本体に {@code @Transactional} を置いても非同期プロキシの都合で期待どおりにならないことがある）。
     */
    @Transactional(readOnly = false)
    public void markLastAudit(UUID projectId, UUID workspaceId) {
        TenantPlanScope.executeWithTenant(workspaceId, () -> {
            projectRepository.updateLastAuditAt(
                projectId,
                LocalDateTime.now(ZoneId.of("Asia/Tokyo"))
            );
        });
    }
}
