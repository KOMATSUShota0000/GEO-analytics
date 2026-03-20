package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.QueryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface QueryRepository extends JpaRepository<QueryEntity, UUID> {
    List<QueryEntity> findByJobIdAndProcessedFalse(UUID jobId);
}
