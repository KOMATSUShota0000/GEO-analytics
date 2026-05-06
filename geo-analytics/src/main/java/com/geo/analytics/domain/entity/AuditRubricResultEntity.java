package com.geo.analytics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "audit_rubric_results",
        indexes = {
            @Index(name = "idx_audit_rubric_results_tenant_history", columnList = "tenant_id, audit_history_id"),
            @Index(name = "idx_audit_rubric_results_history_criterion", columnList = "audit_history_id, criterion_id")
        })
public class AuditRubricResultEntity extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "audit_history_id", nullable = false)
    private UUID auditHistoryId;

    @Column(name = "criterion_id", nullable = false, length = 64)
    private String criterionId;

    @Column(name = "verdict", nullable = false, length = 16)
    private String verdict;

    @Column(name = "evidence", columnDefinition = "text")
    private String evidence;

    @Column(name = "score", nullable = false, precision = 7, scale = 3)
    private BigDecimal score;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(name = "is_self", nullable = false)
    private boolean isSelf;

    public AuditRubricResultEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAuditHistoryId() {
        return auditHistoryId;
    }

    public void setAuditHistoryId(UUID auditHistoryId) {
        this.auditHistoryId = auditHistoryId;
    }

    public String getCriterionId() {
        return criterionId;
    }

    public void setCriterionId(String criterionId) {
        this.criterionId = criterionId;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public boolean isSelf() {
        return isSelf;
    }

    public void setSelf(boolean self) {
        isSelf = self;
    }
}
