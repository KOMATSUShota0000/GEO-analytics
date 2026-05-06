package com.geo.analytics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

@Entity
@Table(
        name = "jobs_pdf_audit_logs",
        indexes = {
            @Index(name = "idx_jobs_pdf_audit_tenant_job", columnList = "tenant_id, job_id"),
            @Index(name = "idx_jobs_pdf_audit_tenant_exported_at", columnList = "tenant_id, exported_at")
        })
public class JobPdfExportAuditEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_session_id", updatable = false)
    private UUID actorSessionId;

    @Column(name = "pdf_byte_size", nullable = false, updatable = false)
    private long pdfByteSize;

    @Column(name = "report_kind", nullable = false, updatable = false, length = 32)
    private String reportKind;

    @Column(name = "exported_at", nullable = false, updatable = false)
    private OffsetDateTime exportedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public JobPdfExportAuditEntity() {}

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (exportedAt == null) {
            exportedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (reportKind == null || reportKind.isBlank()) {
            reportKind = "JOB_REPORT";
        }
    }

    @PreUpdate
    void preUpdate() {
        throw new UnsupportedOperationException(
                "jobs_pdf_audit_logs is append-only (WORM); update is not permitted");
    }

    @PreRemove
    void preRemove() {
        throw new UnsupportedOperationException(
                "jobs_pdf_audit_logs is append-only (WORM); delete is not permitted");
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public UUID getActorSessionId() {
        return actorSessionId;
    }

    public void setActorSessionId(UUID actorSessionId) {
        this.actorSessionId = actorSessionId;
    }

    public long getPdfByteSize() {
        return pdfByteSize;
    }

    public void setPdfByteSize(long pdfByteSize) {
        this.pdfByteSize = pdfByteSize;
    }

    public String getReportKind() {
        return reportKind;
    }

    public void setReportKind(String reportKind) {
        this.reportKind = reportKind;
    }

    public OffsetDateTime getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(OffsetDateTime exportedAt) {
        this.exportedAt = exportedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
