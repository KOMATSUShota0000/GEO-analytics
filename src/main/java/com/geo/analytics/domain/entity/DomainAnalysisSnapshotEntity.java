package com.geo.analytics.domain.entity;

import com.geo.analytics.application.dto.SuggestedQuery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "domain_analysis_snapshots")
public class DomainAnalysisSnapshotEntity extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_url", nullable = false, columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "inferred_persona", nullable = false, columnDefinition = "text")
    private String inferredPersona;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "queries", nullable = false, columnDefinition = "jsonb")
    private List<SuggestedQuery> queries;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public DomainAnalysisSnapshotEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getInferredPersona() {
        return inferredPersona;
    }

    public void setInferredPersona(String inferredPersona) {
        this.inferredPersona = inferredPersona;
    }

    public List<SuggestedQuery> getQueries() {
        return queries;
    }

    public void setQueries(List<SuggestedQuery> queries) {
        this.queries = queries;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
