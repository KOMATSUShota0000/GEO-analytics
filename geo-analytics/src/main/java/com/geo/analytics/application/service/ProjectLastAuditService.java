package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ProjectLastAuditService {
    private final ProjectRepository projectRepository;

    public ProjectLastAuditService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void markLastAudit(UUID projectId, UUID workspaceId) {
        TenantContext.executeWithTenant(workspaceId, () -> {
            ProjectEntity projectEntity = projectRepository.findById(projectId).orElse(null);
            if (projectEntity == null) {
                return;
            }
            projectEntity.setLastAuditAt(LocalDateTime.now());
            projectRepository.save(projectEntity);
        });
    }
}
