package com.geo.analytics.infrastructure.repository;
import com.geo.analytics.domain.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
    Optional<ProjectEntity> findByTenantIdAndName(String tenantId, String name);
    List<ProjectEntity> findByAutoAuditEnabledIsTrue();
}
