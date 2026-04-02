package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.ModelType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

public record VerificationResponse(
        ModelType modelType,
        String rawResponseJson,
        Double somScore,
        Boolean brandMentioned,
        Integer mentionRank,
        Integer overallScore,
        int tokenCount,
        int rankPosition,
        double sentimentIntensity,
        String resolvedEntityLabel,
        Integer visibilityStage,
        Double modifiedZScore,
        String calculationVersion,
        List<CompetitorResult> competitorResults,
        SequencedMap<ModelType, String> modelInsights
) {
    public VerificationResponse {
        competitorResults = competitorResults == null ? List.of() : List.copyOf(competitorResults);
        if (modelInsights == null || modelInsights.isEmpty()) {
            modelInsights = new LinkedHashMap<>();
        } else {
            var copy = new LinkedHashMap<ModelType, String>();
            copy.putAll(modelInsights);
            modelInsights = Collections.unmodifiableSequencedMap(copy);
        }
    }
}
