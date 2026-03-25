package com.geo.analytics.domain.entity;

import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class JobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @TenantId
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Column(name = "project_id")
    private UUID projectId;
    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", nullable = false, length = 20)
    private JobStatus jobStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", length = 16)
    private SubscriptionPlan subscriptionPlan;
    @Column(name = "brand_name", nullable = false)
    private String brandName;
    @Column(name = "brand_color", nullable = false, length = 64)
    private String brandColor = "#4F46E5";
    @Column(name = "logo_url", length = 2048)
    private String logoUrl;
    @Column(name = "gemini_job_name")
    private String geminiJobName;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "pdf_status", length = 32)
    private String pdfStatus;

    @Column(name = "pdf_file_path", length = 1024)
    private String pdfFilePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public JobEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
    public UUID getWorkspaceId() {
        return workspaceId;
    }
    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }
    public UUID getProjectId() {
        return projectId;
    }
    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }
    public JobStatus getJobStatus() {
        return jobStatus;
    }

    public void setJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }
    public SubscriptionPlan getSubscriptionPlan() {
        return subscriptionPlan;
    }
    public void setSubscriptionPlan(SubscriptionPlan subscriptionPlan) {
        this.subscriptionPlan = subscriptionPlan;
    }
    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }
    public String getBrandColor() {
        return brandColor;
    }
    public void setBrandColor(String brandColor) {
        this.brandColor = brandColor;
    }
    public String getLogoUrl() {
        return logoUrl;
    }
    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }
    public String getGeminiJobName() {
        return geminiJobName;
    }

    public void setGeminiJobName(String geminiJobName) {
        this.geminiJobName = geminiJobName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getPdfStatus() {
        return pdfStatus;
    }

    public void setPdfStatus(String pdfStatus) {
        this.pdfStatus = pdfStatus;
    }

    public String getPdfFilePath() {
        return pdfFilePath;
    }

    public void setPdfFilePath(String pdfFilePath) {
        this.pdfFilePath = pdfFilePath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (jobStatus == null) {
            jobStatus = JobStatus.CREATED;
        }
        if (brandColor == null || brandColor.isBlank()) {
            brandColor = "#4F46E5";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
