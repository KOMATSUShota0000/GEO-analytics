package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.MathDebateAuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MathDebateAuditEventRepository extends JpaRepository<MathDebateAuditEventEntity, UUID> {
    List<MathDebateAuditEventEntity> findByTargetIdOrderByCreatedAtAsc(UUID targetId);
}
