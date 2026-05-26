package com.geo.analytics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class OrganizationEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "plan_id", nullable = false, length = 20)
    private String planId;
    @Column(name = "credit_balance", nullable = false)
    private long creditBalance;
    @Column(name = "billing_cycle_anchor", nullable = false)
    private LocalDateTime billingCycleAnchor;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    @Column(name = "logo_file_path", length = 1024)
    private String logoFilePath;
    @Column(name = "brand_color", length = 64)
    private String brandColor;
    @Column(name = "tool_name", length = 255)
    private String toolName;
    protected OrganizationEntity() {
    }
    public UUID getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getPlanId() {
        return planId;
    }
    public long getCreditBalance() {
        return creditBalance;
    }
    public void setCreditBalance(long creditBalance) {
        this.creditBalance = creditBalance;
    }
    public LocalDateTime getBillingCycleAnchor() {
        return billingCycleAnchor;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
    public String getLogoFilePath() {
        return logoFilePath;
    }
    public void setLogoFilePath(String logoFilePath) {
        this.logoFilePath = logoFilePath;
    }
    public String getBrandColor() {
        return brandColor;
    }
    public void setBrandColor(String brandColor) {
        this.brandColor = brandColor;
    }
    public String getToolName() {
        return toolName;
    }
    public void setToolName(String toolName) {
        this.toolName = toolName;
    }
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
