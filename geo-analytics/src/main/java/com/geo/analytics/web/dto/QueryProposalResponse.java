package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.application.dto.DomainAnalysisResult;
import com.geo.analytics.application.dto.SuggestedQuery;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record QueryProposalResponse(String inferredPersona, List<QueryProposalSuggestedQueryResponse> queries) {

    public static QueryProposalResponse from(DomainAnalysisResult result) {
        List<QueryProposalSuggestedQueryResponse> rows =
                result.queries().stream().map(QueryProposalResponse::row).toList();
        return new QueryProposalResponse(result.inferredPersona(), rows);
    }

    private static QueryProposalSuggestedQueryResponse row(SuggestedQuery q) {
        return new QueryProposalSuggestedQueryResponse(q.queryText(), q.intent());
    }
}
