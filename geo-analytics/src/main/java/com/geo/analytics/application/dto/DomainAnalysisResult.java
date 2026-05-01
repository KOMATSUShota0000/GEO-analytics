package com.geo.analytics.application.dto;

import java.util.List;

/**
 * ドメイン分析の最終アウトプット（推定ペルソナと提案クエリ列）。{@code queries} は不変リストとして保持する。
 */
public record DomainAnalysisResult(String inferredPersona, List<SuggestedQuery> queries) {

    public DomainAnalysisResult {
        inferredPersona = inferredPersona == null ? "" : inferredPersona;
        queries = queries == null ? List.of() : List.copyOf(queries);
    }
}
