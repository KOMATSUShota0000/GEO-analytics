package com.geo.analytics.domain.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity
@Table(name = "sge_results")
public class SgeResultEntity extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "query_id", nullable = false)
    private UUID queryId;
    @Column(name = "query", nullable = false, columnDefinition = "text")
    private String query;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sge_raw_response", nullable = false)
    private String sgeRawResponse;
    @Column(name = "sge_mentioned", nullable = false)
    private Boolean sgeMentioned;
    @Column(name = "mention_count", nullable = false)
    private int mentionCount;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    public SgeResultEntity() {
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
    public UUID getQueryId() {
        return queryId;
    }
    public void setQueryId(UUID queryId) {
        this.queryId = queryId;
    }
    public String getQuery() {
        return query;
    }
    public void setQuery(String query) {
        this.query = query;
    }
    public String getSgeRawResponse() {
        return sgeRawResponse;
    }
    public void setSgeRawResponse(String sgeRawResponse) {
        this.sgeRawResponse = sgeRawResponse;
    }
    public Boolean getSgeMentioned() {
        return sgeMentioned;
    }
    public void setSgeMentioned(Boolean sgeMentioned) {
        this.sgeMentioned = sgeMentioned;
    }
    public int getMentionCount() {
        return mentionCount;
    }
    public void setMentionCount(int mentionCount) {
        this.mentionCount = mentionCount;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
