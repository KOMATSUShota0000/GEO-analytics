package com.geo.analytics.domain.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
@Entity
@Table(name = "audit_histories", indexes = {
    @Index(name = "idx_audit_histories_tenant_project_date", columnList = "tenant_id, project_id, audit_date"),
    @Index(name = "idx_audit_histories_job_id", columnList = "job_id")
})
public class AuditHistoryEntity extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;
    @Column(name = "query", nullable = false, columnDefinition = "text")
    private String query;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb", nullable = false)
    private String rawResponse;
    @Column(name = "som_score", nullable = false)
    private Double somScore;
    @Column(name = "brand_mentioned", nullable = false)
    private Boolean brandMentioned;
    @Column(name = "mention_rank")
    private Integer mentionRank;
    @Column(name = "overall_score")
    private Integer overallScore;
    @Column(name = "resolved_entity_label", length = 512)
    private String resolvedEntityLabel;
    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;
    @Column(name = "rank_position", nullable = false)
    private Integer rankPosition;
    @Column(name = "sentiment_intensity", nullable = false)
    private Double sentimentIntensity;
    @Column(name = "audit_date", nullable = false)
    private LocalDate auditDate;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    public AuditHistoryEntity() {
    }
    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }
    public UUID getJobId() {
        return jobId;
    }
    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }
    public ProjectEntity getProject() {
        return project;
    }
    public void setProject(ProjectEntity project) {
        this.project = project;
    }
    public String getQuery() {
        return query;
    }
    public void setQuery(String query) {
        this.query = query;
    }
    public String getRawResponse() {
        return rawResponse;
    }
    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }
    public Double getSomScore() {
        return somScore;
    }
    public void setSomScore(Double somScore) {
        this.somScore = somScore;
    }
    public Boolean getBrandMentioned() {
        return brandMentioned;
    }
    public void setBrandMentioned(Boolean brandMentioned) {
        this.brandMentioned = brandMentioned;
    }
    public Integer getMentionRank() {
        return mentionRank;
    }
    public void setMentionRank(Integer mentionRank) {
        this.mentionRank = mentionRank;
    }
    public Integer getOverallScore() {
        return overallScore;
    }
    public void setOverallScore(Integer overallScore) {
        this.overallScore = overallScore;
    }
    public String getResolvedEntityLabel() {
        return resolvedEntityLabel;
    }
    public void setResolvedEntityLabel(String resolvedEntityLabel) {
        this.resolvedEntityLabel = resolvedEntityLabel;
    }
    public Integer getTokenCount() {
        return tokenCount;
    }
    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }
    public Integer getRankPosition() {
        return rankPosition;
    }
    public void setRankPosition(Integer rankPosition) {
        this.rankPosition = rankPosition;
    }
    public Double getSentimentIntensity() {
        return sentimentIntensity;
    }
    public void setSentimentIntensity(Double sentimentIntensity) {
        this.sentimentIntensity = sentimentIntensity;
    }
    public LocalDate getAuditDate() {
        return auditDate;
    }
    public void setAuditDate(LocalDate auditDate) {
        this.auditDate = auditDate;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
