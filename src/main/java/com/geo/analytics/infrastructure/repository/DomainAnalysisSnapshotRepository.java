package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.DomainAnalysisSnapshotEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainAnalysisSnapshotRepository extends JpaRepository<DomainAnalysisSnapshotEntity, UUID> {}
