package com.geo.analytics.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "query_proposals")
public class QueryProposalEntity extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "url", nullable = false, length = 2083)
    private String url;

    @Column(name = "business_description", nullable = false, columnDefinition = "text")
    private String businessDescription;

    @Column(name = "target_audience", nullable = false, columnDefinition = "text")
    private String targetAudience;

    @Column(name = "strategic_focus", nullable = false, columnDefinition = "text")
    private String strategicFocus;

    @Column(name = "inferred_persona", nullable = false, columnDefinition = "text")
    private String inferredPersona;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<QueryProposalSuggestedQueryEntity> suggestedQueries = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public QueryProposalEntity() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (suggestedQueries == null) {
            suggestedQueries = new ArrayList<>();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBusinessDescription() {
        return businessDescription;
    }

    public void setBusinessDescription(String businessDescription) {
        this.businessDescription = businessDescription;
    }

    public String getTargetAudience() {
        return targetAudience;
    }

    public void setTargetAudience(String targetAudience) {
        this.targetAudience = targetAudience;
    }

    public String getStrategicFocus() {
        return strategicFocus;
    }

    public void setStrategicFocus(String strategicFocus) {
        this.strategicFocus = strategicFocus;
    }

    public String getInferredPersona() {
        return inferredPersona;
    }

    public void setInferredPersona(String inferredPersona) {
        this.inferredPersona = inferredPersona;
    }

    public List<QueryProposalSuggestedQueryEntity> getSuggestedQueries() {
        return suggestedQueries;
    }

    public void setSuggestedQueries(List<QueryProposalSuggestedQueryEntity> suggestedQueries) {
        this.suggestedQueries = suggestedQueries != null ? suggestedQueries : new ArrayList<>();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
