package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.SgeResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SgeResultRepository extends JpaRepository<SgeResultEntity, UUID> {
    List<SgeResultEntity> findByJobId(UUID jobId);
}
