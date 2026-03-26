package com.geo.analytics.domain.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
@Entity
@Table(name = "job_queries")
public class QueryEntity extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "query_text", nullable = false, columnDefinition = "text")
    private String queryText;
    @Column(name = "processed", nullable = false)
    private Boolean processed = false;
    public QueryEntity() {
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
    public String getQueryText() {
        return queryText;
    }
    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }
    public Boolean getProcessed() {
        return processed;
    }
    public void setProcessed(Boolean processed) {
        this.processed = processed;
    }
}
