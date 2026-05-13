package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.ProjectKeywordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProjectKeywordRepository extends JpaRepository<ProjectKeywordEntity, UUID> {
    List<ProjectKeywordEntity> findByProjectId(UUID projectId);
}
