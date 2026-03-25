package com.geo.analytics.infrastructure.repository;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {}
