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

    private void forceSetTenantId(Object entity, UUID tenantId) {
        if (entity == null || tenantId == null) return;
        Class<?> clazz = entity.getClass();
        while (clazz != null) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                for (java.lang.annotation.Annotation ann : field.getAnnotations()) {
                    if (ann.annotationType().getSimpleName().equals("TenantId")) {
                        field.setAccessible(true);
                        try {
                            if (field.getType().equals(String.class)) {
                                field.set(entity, tenantId.toString());
                            } else {
                                field.set(entity, tenantId);
                            }
                            return;
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException("Failed to force set TenantId", e);
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
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
                forceSetTenantId(projectEntity, workspaceId);
                return projectRepository.saveAndFlush(projectEntity);
            });
        });
    }
}
