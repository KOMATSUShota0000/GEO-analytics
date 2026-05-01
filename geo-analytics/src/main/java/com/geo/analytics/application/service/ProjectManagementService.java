package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProjectManagementService {

    /**
     * 既存ワークスペース向けにデフォルトブランド名プロジェクトを解決した結果（所属組織 ID を含む）。
     */
    public record DefaultProjectResolution(ProjectEntity project, UUID organizationId) {}
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
        return TenantPlanScope.executeWithTenant(workspaceId, () -> {
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

    /**
     * 指定ワークスペース内でブランド名に紐づくプロジェクトを取得または作成する。ワークスペース行は既に存在すること（自動作成しない）。
     */
    @Transactional
    public DefaultProjectResolution getOrCreateDefaultProjectForWorkspace(String brandName, UUID workspaceId) {
        Objects.requireNonNull(brandName, "brandName");
        Objects.requireNonNull(workspaceId, "workspaceId");
        WorkspaceEntity workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Workspace not found: " + workspaceId));
        UUID orgId = workspace.getOrganizationId();
        ProjectEntity project = TenantPlanScope.executeWithTenant(workspaceId, () -> projectRepository
                .findByTenantIdAndName(workspaceId.toString(), brandName)
                .orElseGet(() -> {
                    ProjectEntity projectEntity = new ProjectEntity();
                    projectEntity.setWorkspaceId(workspaceId);
                    projectEntity.setName(brandName);
                    projectEntity.setTargetUrl("");
                    projectEntity.setCompetitorUrls(new ArrayList<>());
                    forceSetTenantId(projectEntity, workspaceId);
                    return projectRepository.saveAndFlush(projectEntity);
                }));
        return new DefaultProjectResolution(project, orgId);
    }
}
