package com.geo.analytics.domain.entity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import com.geo.analytics.domain.enums.IndustryType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.geo.analytics.domain.model.CompetitorProfile;
import com.geo.analytics.domain.model.MinorityReport;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Entity
@Table(name = "projects")
public class ProjectEntity extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "target_url", nullable = false)
    private String targetUrl;
    @Column(name = "brand_color", nullable = false, length = 64)
    private String brandColor = "#4F46E5";
    @Column(name = "logo_url", length = 2048)
    private String logoUrl;
    @ElementCollection
    @CollectionTable(name = "project_competitors", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "competitor_url")
    @Size(max = 3)
    private List<String> competitorUrls = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "auto_audit_enabled", nullable = false)
    private boolean autoAuditEnabled = false;
    @Column(name = "slack_webhook_url", length = 2048)
    private String slackWebhookUrl;
    @Column(name = "notification_email", length = 320)
    private String notificationEmail;
    @Column(name = "last_audit_at")
    private LocalDateTime lastAuditAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "industry_type", nullable = false, length = 32)
    private IndustryType industryType = IndustryType.OTHER;
    @Column(name = "extracted_strengths", columnDefinition = "text")
    private String extractedStrengths;
    @Column(name = "target_audience", columnDefinition = "text")
    private String targetAudience;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "minority_reports", nullable = false, columnDefinition = "jsonb")
    private List<MinorityReport> minorityReports = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "competitor_profiles", nullable = false, columnDefinition = "jsonb")
    private List<CompetitorProfile> competitorProfiles = new ArrayList<>();
    public ProjectEntity() {
    }
    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getTargetUrl() {
        return targetUrl;
    }
    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
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
    public List<String> getCompetitorUrls() {
        return competitorUrls;
    }
    public void setCompetitorUrls(List<String> competitorUrls) {
        this.competitorUrls = competitorUrls != null ? competitorUrls : new ArrayList<>();
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
    public boolean isAutoAuditEnabled() {
        return autoAuditEnabled;
    }
    public void setAutoAuditEnabled(boolean autoAuditEnabled) {
        this.autoAuditEnabled = autoAuditEnabled;
    }
    public String getSlackWebhookUrl() {
        return slackWebhookUrl;
    }
    public void setSlackWebhookUrl(String slackWebhookUrl) {
        this.slackWebhookUrl = slackWebhookUrl;
    }
    public String getNotificationEmail() {
        return notificationEmail;
    }
    public void setNotificationEmail(String notificationEmail) {
        this.notificationEmail = notificationEmail;
    }
    public LocalDateTime getLastAuditAt() {
        return lastAuditAt;
    }
    public void setLastAuditAt(LocalDateTime lastAuditAt) {
        this.lastAuditAt = lastAuditAt;
    }
    public IndustryType getIndustryType() {
        return industryType;
    }
    public void setIndustryType(IndustryType industryType) {
        this.industryType = industryType;
    }
    public String getExtractedStrengths() {
        return extractedStrengths;
    }
    public void setExtractedStrengths(String extractedStrengths) {
        this.extractedStrengths = extractedStrengths;
    }
    public String getTargetAudience() {
        return targetAudience;
    }
    public void setTargetAudience(String targetAudience) {
        this.targetAudience = targetAudience;
    }
    public List<MinorityReport> getMinorityReports() {
        return minorityReports;
    }
    public void setMinorityReports(List<MinorityReport> minorityReports) {
        this.minorityReports = minorityReports != null ? minorityReports : new ArrayList<>();
    }
    public List<CompetitorProfile> getCompetitorProfiles() {
        return competitorProfiles;
    }
    public void setCompetitorProfiles(List<CompetitorProfile> competitorProfiles) {
        this.competitorProfiles = competitorProfiles != null ? competitorProfiles : new ArrayList<>();
    }
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (brandColor == null || brandColor.isBlank()) {
            brandColor = "#4F46E5";
        }
        if (industryType == null) {
            industryType = IndustryType.OTHER;
        }
        if (minorityReports == null) {
            minorityReports = new ArrayList<>();
        }
        if (competitorProfiles == null) {
            competitorProfiles = new ArrayList<>();
        }
    }
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
