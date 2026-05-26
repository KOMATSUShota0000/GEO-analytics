package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.GeoAssetSnapshotEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeoAssetSnapshotRepository extends JpaRepository<GeoAssetSnapshotEntity, UUID> {

    List<GeoAssetSnapshotEntity> findByProjectIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            UUID projectId, LocalDate fromInclusive, LocalDate toInclusive);
}
