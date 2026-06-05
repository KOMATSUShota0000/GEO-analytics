package com.geo.analytics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "geo_asset_snapshots")
public class GeoAssetSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "readiness_score", nullable = false)
    private double readinessScore;

    @Column(name = "local_trust_count", nullable = false)
    private long localTrustCount;

    // このスナップショットを算出したスコア計算モデルの版（V13 Sprint4a-3）。旧データは NULL。
    @Column(name = "calculation_version", length = 32)
    private String calculationVersion;

    public GeoAssetSnapshotEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public double getReadinessScore() {
        return readinessScore;
    }

    public void setReadinessScore(double readinessScore) {
        this.readinessScore = readinessScore;
    }

    public long getLocalTrustCount() {
        return localTrustCount;
    }

    public void setLocalTrustCount(long localTrustCount) {
        this.localTrustCount = localTrustCount;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public void setCalculationVersion(String calculationVersion) {
        this.calculationVersion = calculationVersion;
    }
}
