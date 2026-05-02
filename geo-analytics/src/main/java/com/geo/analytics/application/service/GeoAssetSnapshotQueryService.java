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

    public GeoAssetSnapshotQueryService(JdbcTemplate jdbcTemplate, GeoAssetSnapshotRepository geoAssetSnapshotRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.geoAssetSnapshotRepository = geoAssetSnapshotRepository;
    }

    public AssetSnapshotsChartResponse getChartData(UUID projectId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        UUID workspaceId = resolveWorkspaceId(projectId);
        UUID currentTenant =
                TenantContextHolder.getTenantId().orElseThrow(() -> new EntityNotFoundException("project"));
        if (!workspaceId.equals(currentTenant)) {
            throw new EntityNotFoundException("project");
        }
        List<AssetSnapshotChartPoint> points = TenantPlanScope.executeWithTenant(workspaceId, () -> mapSnapshots(projectId, from, to));
        return new AssetSnapshotsChartResponse(points);
    }

    private List<AssetSnapshotChartPoint> mapSnapshots(UUID projectId, LocalDate from, LocalDate to) {
        List<GeoAssetSnapshotEntity> entities =
                geoAssetSnapshotRepository.findByProjectIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(projectId, from, to);
        List<AssetSnapshotChartPoint> out = new ArrayList<>(entities.size());
        for (GeoAssetSnapshotEntity entity : entities) {
            out.add(new AssetSnapshotChartPoint(
                    entity.getSnapshotDate().toString(),
                    entity.getReadinessScore(),
                    entity.getLocalTrustCount()));
        }
        return out;
    }

    private UUID resolveWorkspaceId(UUID projectId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT tenant_id FROM projects WHERE id = ?",
                ps -> ps.setObject(1, projectId),
                (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty()) {
            throw new EntityNotFoundException("project");
        }
        String tenantId = rows.get(0);
        if (tenantId == null || tenantId.isBlank()) {
            throw new EntityNotFoundException("project");
        }
        return UUID.fromString(tenantId);
    }
}
