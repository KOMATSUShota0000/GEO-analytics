package com.geo.analytics.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * クエリ提案の AI 分析結果と、永続化された {@link com.geo.analytics.domain.entity.QueryProposalEntity} の ID。
 */
public record QueryProposalProposeOutcome(DomainAnalysisResult analysis, UUID savedProposalId) {

    public QueryProposalProposeOutcome {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(savedProposalId, "savedProposalId");
    }
}
