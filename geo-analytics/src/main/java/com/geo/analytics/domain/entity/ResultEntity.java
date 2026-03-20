package com.geo.analytics.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "results")
public class ResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "query", nullable = false, columnDefinition = "text")
    private String query;

    @Column(name = "raw_response", columnDefinition = "jsonb", nullable = false)
    private String rawResponse;

    @Column(name = "som_score", nullable = false)
    private Double somScore;

    @Column(name = "brand_mentioned", nullable = false)
    private Boolean brandMentioned;

    @Column(name = "mention_rank")
    private Integer mentionRank;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ResultEntity() {
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
