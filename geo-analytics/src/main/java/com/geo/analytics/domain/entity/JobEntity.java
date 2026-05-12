package com.geo.analytics.domain.entity;
import com.geo.analytics.domain.enums.CompetitorExtractionMode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@Entity
@Table(name = "jobs")
public class JobEntity extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "project_id")
    private UUID projectId;
    @Enumerated(EnumType.STRING)
    @Column(name = "industry_type", nullable = false, length = 32)
    private CompetitorExtractionMode competitorExtractionMode = CompetitorExtractionMode.LOCAL_STORE;
    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", nullable = false, length = 32)
    private JobStatus jobStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", length = 16)
    private SubscriptionPlan appliedPlan;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_limits_snapshot")
    private String planLimitsSnapshot;
    @Column(name = "brand_name", nullable = false)
    private String brandName;
    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;
    @Column(name = "business_summary", columnDefinition = "text")
    private String businessSummary;
    @Column(name = "target_audience", columnDefinition = "text")
    private String targetAudience;
    @Column(name = "focus_points", columnDefinition = "text")
    private String focusPoints;
    @Column(name = "extracted_knowledge", columnDefinition = "text")
    private String extractedKnowledge;
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
    @Column(name = "job_diagnostic_message", columnDefinition = "text")
    private String jobDiagnosticMessage;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_recommended_actions")
    private List<String> jobRecommendedActions;
    @Column(name = "gap_batch_idempotency_key")
    private UUID gapBatchIdempotencyKey;
    @Column(name = "create_idempotency_key")
    private UUID createIdempotencyKey;
    @Column(name = "gap_analysis_gemini_job_name", length = 512)
    private String gapAnalysisGeminiJobName;
    @Column(name = "gap_analysis_completed", nullable = false)
    private Boolean gapAnalysisCompleted = false;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "self_rubric_audit_json", columnDefinition = "jsonb")
    private String selfRubricAuditJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "competitor_rubric_audits_json", columnDefinition = "jsonb")
    private String competitorRubricAuditsJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "self_crawled_page_json", columnDefinition = "jsonb")
    private String selfCrawledPageJson;
    @Column(name = "meo_review_count")
    private Integer meoReviewCount;
    @Column(name = "meo_average_stars")
    private Double meoAverageStars;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "emotional_alert", columnDefinition = "jsonb")
    private String emotionalAlertJson;
    public JobEntity() {
    }
    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }
    public UUID getProjectId() {
        return projectId;
    }
    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }
    public CompetitorExtractionMode getCompetitorExtractionMode() {
        return competitorExtractionMode;
    }
    public void setCompetitorExtractionMode(CompetitorExtractionMode competitorExtractionMode) {
        this.competitorExtractionMode = competitorExtractionMode;
    }
    public JobStatus getJobStatus() {
        return jobStatus;
    }
    public void setJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }
    public SubscriptionPlan getAppliedPlan() {
        return appliedPlan;
    }

    public void setAppliedPlan(SubscriptionPlan appliedPlan) {
        if (appliedPlan == null) {
            return;
        }
        if (this.appliedPlan != null && this.appliedPlan != appliedPlan) {
            throw new IllegalStateException("appliedPlan is immutable once set");
        }
        this.appliedPlan = appliedPlan;
    }
    public String getPlanLimitsSnapshot() {
        return planLimitsSnapshot;
    }
    public void setPlanLimitsSnapshot(String planLimitsSnapshot) {
        this.planLimitsSnapshot = planLimitsSnapshot;
    }
    public String getBrandName() {
        return brandName;
    }
    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }
    public String getTargetUrl() {
        return targetUrl;
    }
    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }
    public String getBusinessSummary() {
        return businessSummary;
    }
    public void setBusinessSummary(String businessSummary) {
        this.businessSummary = businessSummary;
    }
    public String getTargetAudience() {
        return targetAudience;
    }
    public void setTargetAudience(String targetAudience) {
        this.targetAudience = targetAudience;
    }
    public String getFocusPoints() {
        return focusPoints;
    }
    public void setFocusPoints(String focusPoints) {
        this.focusPoints = focusPoints;
    }
    public String getExtractedKnowledge() {
        return extractedKnowledge;
    }
    public void setExtractedKnowledge(String extractedKnowledge) {
        this.extractedKnowledge = extractedKnowledge;
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
    public String getJobDiagnosticMessage() {
        return jobDiagnosticMessage;
    }
    public void setJobDiagnosticMessage(String jobDiagnosticMessage) {
        this.jobDiagnosticMessage = jobDiagnosticMessage;
    }
    public List<String> getJobRecommendedActions() {
        return jobRecommendedActions;
    }
    public void setJobRecommendedActions(List<String> jobRecommendedActions) {
        this.jobRecommendedActions = jobRecommendedActions;
    }
    public UUID getGapBatchIdempotencyKey() {
        return gapBatchIdempotencyKey;
    }
    public void setGapBatchIdempotencyKey(UUID gapBatchIdempotencyKey) {
        this.gapBatchIdempotencyKey = gapBatchIdempotencyKey;
    }
    public UUID getCreateIdempotencyKey() {
        return createIdempotencyKey;
    }
    public void setCreateIdempotencyKey(UUID createIdempotencyKey) {
        this.createIdempotencyKey = createIdempotencyKey;
    }
    public String getGapAnalysisGeminiJobName() {
        return gapAnalysisGeminiJobName;
    }
    public void setGapAnalysisGeminiJobName(String gapAnalysisGeminiJobName) {
        this.gapAnalysisGeminiJobName = gapAnalysisGeminiJobName;
    }
    public Boolean getGapAnalysisCompleted() {
        return gapAnalysisCompleted;
    }
    public void setGapAnalysisCompleted(Boolean gapAnalysisCompleted) {
        this.gapAnalysisCompleted = gapAnalysisCompleted;
    }
    public String getSelfRubricAuditJson() {
        return selfRubricAuditJson;
    }
    public void setSelfRubricAuditJson(String selfRubricAuditJson) {
        this.selfRubricAuditJson = selfRubricAuditJson;
    }
    public String getCompetitorRubricAuditsJson() {
        return competitorRubricAuditsJson;
    }
    public void setCompetitorRubricAuditsJson(String competitorRubricAuditsJson) {
        this.competitorRubricAuditsJson = competitorRubricAuditsJson;
    }
    public String getSelfCrawledPageJson() {
        return selfCrawledPageJson;
    }
    public void setSelfCrawledPageJson(String selfCrawledPageJson) {
        this.selfCrawledPageJson = selfCrawledPageJson;
    }
    public Integer getMeoReviewCount() {
        return meoReviewCount;
    }
    public void setMeoReviewCount(Integer meoReviewCount) {
        this.meoReviewCount = meoReviewCount;
    }
    public Double getMeoAverageStars() {
        return meoAverageStars;
    }
    public void setMeoAverageStars(Double meoAverageStars) {
        this.meoAverageStars = meoAverageStars;
    }
    public String getEmotionalAlertJson() {
        return emotionalAlertJson;
    }
    public void setEmotionalAlertJson(String emotionalAlertJson) {
        this.emotionalAlertJson = emotionalAlertJson;
    }
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (competitorExtractionMode == null) {
            competitorExtractionMode = CompetitorExtractionMode.LOCAL_STORE;
        }
        if (jobStatus == null) {
            jobStatus = JobStatus.CREATED;
        }
        if (brandColor == null || brandColor.isBlank()) {
            brandColor = "#4F46E5";
        }
        if (gapAnalysisCompleted == null) {
            gapAnalysisCompleted = false;
        }
    }
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
