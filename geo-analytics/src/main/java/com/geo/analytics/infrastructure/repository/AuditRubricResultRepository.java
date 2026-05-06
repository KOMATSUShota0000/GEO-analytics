package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.AuditRubricResultEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRubricResultRepository extends JpaRepository<AuditRubricResultEntity, UUID> {

    List<AuditRubricResultEntity> findByAuditHistoryId(UUID auditHistoryId);
}
