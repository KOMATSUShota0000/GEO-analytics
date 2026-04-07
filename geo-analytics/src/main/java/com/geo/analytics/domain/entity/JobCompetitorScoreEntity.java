package com.geo.analytics.domain.entity;

import com.geo.analytics.domain.enums.MatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "job_competitor_scores")
public class JobCompetitorScoreEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "audit_history_id", nullable = false)
    private AuditHistoryEntity auditHistory;

    @Column(name = "competitor_name", nullable = false, length = 512)
    private String competitorName;

    @Column(name = "som_score", nullable = false)
    private Double somScore;

    @Column(name = "rank_position")
    private Integer rankPosition;

    @Column(name = "visibility_stage")
    private Integer visibilityStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 32)
    private MatchStatus matchStatus;

    @Column(name = "noun_count", nullable = false)
    private int nounCount;

    public JobCompetitorScoreEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AuditHistoryEntity getAuditHistory() {
        return auditHistory;
    }

    public void setAuditHistory(AuditHistoryEntity auditHistory) {
        this.auditHistory = auditHistory;
    }

    public String getCompetitorName() {
        return competitorName;
    }

    public void setCompetitorName(String competitorName) {
        this.competitorName = competitorName;
    }

    public Double getSomScore() {
        return somScore;
    }

    public void setSomScore(Double somScore) {
        this.somScore = somScore;
    }

    public Integer getRankPosition() {
        return rankPosition;
    }

    public void setRankPosition(Integer rankPosition) {
        this.rankPosition = rankPosition;
    }

    public Integer getVisibilityStage() {
        return visibilityStage;
    }

    public void setVisibilityStage(Integer visibilityStage) {
        this.visibilityStage = visibilityStage;
    }

    public MatchStatus getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(MatchStatus matchStatus) {
        this.matchStatus = matchStatus;
    }

    public int getNounCount() {
        return nounCount;
    }

    public void setNounCount(int nounCount) {
        this.nounCount = nounCount;
    }
}
