package com.geo.analytics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "unresolved_entity_queue")
public class UnresolvedEntityQueueEntity extends BaseTenantEntity {
    public static final String CALCULATION_VERSION = "ER_PIPELINE_V1";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "left_label", nullable = false, columnDefinition = "text")
    private String leftLabel;

    @Column(name = "right_label", nullable = false, columnDefinition = "text")
    private String rightLabel;

    @Column(name = "left_blocking_hash", nullable = false, length = 64)
    private String leftBlockingHash;

    @Column(name = "right_blocking_hash", nullable = false, length = 64)
    private String rightBlockingHash;

    @Column(name = "manual_review_required", nullable = false)
    private boolean manualReviewRequired = true;

    @Column(name = "calculation_version", nullable = false, length = 32)
    private String calculationVersion = CALCULATION_VERSION;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getLeftLabel() {
        return leftLabel;
    }

    public void setLeftLabel(String leftLabel) {
        this.leftLabel = leftLabel;
    }

    public String getRightLabel() {
        return rightLabel;
    }

    public void setRightLabel(String rightLabel) {
        this.rightLabel = rightLabel;
    }

    public String getLeftBlockingHash() {
        return leftBlockingHash;
    }

    public void setLeftBlockingHash(String leftBlockingHash) {
        this.leftBlockingHash = leftBlockingHash;
    }

    public String getRightBlockingHash() {
        return rightBlockingHash;
    }

    public void setRightBlockingHash(String rightBlockingHash) {
        this.rightBlockingHash = rightBlockingHash;
    }

    public boolean isManualReviewRequired() {
        return manualReviewRequired;
    }

    public void setManualReviewRequired(boolean manualReviewRequired) {
        this.manualReviewRequired = manualReviewRequired;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public void setCalculationVersion(String calculationVersion) {
        this.calculationVersion = calculationVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
