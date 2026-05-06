package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.GeoAssetSnapshotEntity;
import com.geo.analytics.infrastructure.repository.GeoAssetSnapshotRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import com.geo.analytics.web.dto.AssetSnapshotChartPoint;
import com.geo.analytics.web.dto.AssetSnapshotsChartResponse;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GeoAssetSnapshotQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final GeoAssetSnapshotRepository geoAssetSnapshotRepository;

    public GeoAssetSnapshotQueryService(
            JdbcTemplate jdbcTemplate, GeoAssetSnapshotRepository geoAssetSnapshotRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.geoAssetSnapshotRepository = geoAssetSnapshotRepository;
    }

    public AssetSnapshotsChartResponse getChartData(UUID projectId, LocalDate from, LocalDate to) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId is required");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        UUID workspaceId = TenantContextHolder
                .getTenantId()
                .orElseThrow(() -> new EntityNotFoundException("project"));
        List<AssetSnapshotChartPoint> points = TenantPlanScope.executeWithTenant(workspaceId, () -> {
            verifyProjectVisible(projectId);
            return loadChartPoints(projectId, from, to);
        });
        return new AssetSnapshotsChartResponse(points);
    }

    private void verifyProjectVisible(UUID projectId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projects WHERE id = ?",
                Integer.class,
                projectId);
        if (count == null || count.intValue() == 0) {
            throw new EntityNotFoundException("project");
        }
    }

    private List<AssetSnapshotChartPoint> loadChartPoints(UUID projectId, LocalDate from, LocalDate to) {
        List<GeoAssetSnapshotEntity> entities = geoAssetSnapshotRepository
                .findByProjectIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(projectId, from, to);
        List<AssetSnapshotChartPoint> out = new ArrayList<>(entities.size());
        for (GeoAssetSnapshotEntity entity : entities) {
            out.add(new AssetSnapshotChartPoint(
                    entity.getSnapshotDate().toString(),
                    entity.getReadinessScore(),
                    entity.getLocalTrustCount()));
        }
        return out;
    }
}
