package com.geo.analytics.domain.entity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.TenantId;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Entity
@Table(name = "projects")
public class ProjectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @TenantId
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
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
    public ProjectEntity() {
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
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (brandColor == null || brandColor.isBlank()) {
            brandColor = "#4F46E5";
        }
    }
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
