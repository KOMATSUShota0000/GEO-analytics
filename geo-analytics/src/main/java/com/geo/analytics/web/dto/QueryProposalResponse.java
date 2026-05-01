package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.application.dto.DomainAnalysisResult;
import com.geo.analytics.application.dto.QueryProposalProposeOutcome;
import com.geo.analytics.application.dto.SuggestedQuery;
import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record QueryProposalResponse(
        String inferredPersona, List<QueryProposalSuggestedQueryResponse> queries, UUID id) {

    public static QueryProposalResponse from(QueryProposalProposeOutcome outcome) {
        return from(outcome.analysis(), outcome.savedProposalId());
    }

    public static QueryProposalResponse from(DomainAnalysisResult result, UUID id) {
        List<QueryProposalSuggestedQueryResponse> rows =
                result.queries().stream().map(QueryProposalResponse::row).toList();
        return new QueryProposalResponse(result.inferredPersona(), rows, id);
    }

    private static QueryProposalSuggestedQueryResponse row(SuggestedQuery q) {
        return new QueryProposalSuggestedQueryResponse(q.queryText(), q.intent());
    }
}
