package com.geo.analytics.application.dto;

import java.util.List;

public record SyncVerificationResult(
        String rawResponseJson,
        Double somScore,
        Boolean brandMentioned,
        Integer mentionRank,
        Integer overallScore,
        int tokenCount,
        Integer aiCitationPosition,
        double sentimentIntensity,
        String naturalLanguageResponse,
        String resolvedEntityLabel,
        Integer visibilityStage,
        Double modifiedZScore,
        String calculationVersion,
        List<CompetitorScoreRow> competitorScoreRows,
        String modelInsightsJson,
        Double gbvsNormalizedScore,
        int analysisTextLength
) {}
