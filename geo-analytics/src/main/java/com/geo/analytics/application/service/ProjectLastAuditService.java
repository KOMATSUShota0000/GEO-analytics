package com.geo.analytics.application.service;

import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class ProjectLastAuditService {
    private final ProjectRepository projectRepository;

    public ProjectLastAuditService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public void markLastAudit(UUID projectId, UUID workspaceId) {
        TenantContext.executeWithTenant(workspaceId, () -> {
            projectRepository.updateLastAuditAt(
                projectId,
                LocalDateTime.now(ZoneId.of("Asia/Tokyo"))
            );
        });
    }
}
