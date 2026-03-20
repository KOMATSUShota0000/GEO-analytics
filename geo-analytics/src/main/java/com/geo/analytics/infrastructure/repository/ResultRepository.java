package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.ResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ResultRepository extends JpaRepository<ResultEntity, UUID> {
    List<ResultEntity> findByJobId(UUID jobId);
}
