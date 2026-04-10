package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProjectManagementService {
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    @PersistenceContext
    private EntityManager entityManager;

    public ProjectManagementService(WorkspaceRepository workspaceRepository, ProjectRepository projectRepository) {
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectEntity getOrCreateDefaultProject(String brandName) {
        Objects.requireNonNull(brandName, "brandName");
        UUID workspaceId = DefaultTenantIds.WORKSPACE_ID;
        return TenantContext.executeWithTenant(workspaceId, () -> {
            workspaceRepository.findById(workspaceId).orElseGet(() -> {
                WorkspaceEntity workspaceEntity = new WorkspaceEntity();
                workspaceEntity.setId(workspaceId);
                workspaceEntity.setOrganizationId(DefaultTenantIds.DEFAULT_ORGANIZATION_ID);
                workspaceEntity.setName("Default");
                entityManager.persist(workspaceEntity);
                entityManager.flush();
                return workspaceEntity;
            });
            return projectRepository.findByTenantIdAndName(workspaceId.toString(), brandName).orElseGet(() -> {
                ProjectEntity projectEntity = new ProjectEntity();
                projectEntity.setWorkspaceId(workspaceId);
                projectEntity.setName(brandName);
                projectEntity.setTargetUrl("");
                projectEntity.setCompetitorUrls(new ArrayList<>());
                return projectRepository.save(projectEntity);
            });
        });
    }
}
