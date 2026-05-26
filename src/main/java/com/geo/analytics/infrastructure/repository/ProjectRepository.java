package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.ProjectEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
    Optional<ProjectEntity> findByTenantIdAndName(String tenantId, String name);

    List<ProjectEntity> findByAutoAuditEnabledIsTrue();

    @EntityGraph(attributePaths = {"competitorUrls"})
    @Query("SELECT p FROM ProjectEntity p WHERE p.id = :id")
    Optional<ProjectEntity> findByIdWithCompetitorUrls(@Param("id") UUID id);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update ProjectEntity p set p.lastAuditAt = :lastAuditAt where p.id = :id")
    void updateLastAuditAt(@Param("id") UUID id, @Param("lastAuditAt") LocalDateTime lastAuditAt);
}