package com.geo.analytics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "query_proposal_suggested_queries")
public class QueryProposalSuggestedQueryEntity extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private QueryProposalEntity proposal;

    @Column(name = "query_text", nullable = false, columnDefinition = "text")
    private String queryText;

    @Column(name = "intent", nullable = false, columnDefinition = "text")
    private String intent;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public QueryProposalSuggestedQueryEntity() {}

    @PrePersist
    void alignTenantWithProposal() {
        if (proposal != null) {
            setWorkspaceId(proposal.getWorkspaceId());
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public QueryProposalEntity getProposal() {
        return proposal;
    }

    public void setProposal(QueryProposalEntity proposal) {
        this.proposal = proposal;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
