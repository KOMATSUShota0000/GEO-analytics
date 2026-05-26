package com.geo.analytics.infrastructure.repository;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);
}
