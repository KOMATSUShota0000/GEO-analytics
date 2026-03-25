package com.geo.analytics.application.dto;

public record SyncVerificationResult(
    String rawResponseJson,
    Double somScore,
    Boolean brandMentioned,
    Integer mentionRank,
    Integer overallScore,
    int tokenCount,
    int rankPosition,
    double sentimentIntensity,
    String naturalLanguageResponse,
    String resolvedEntityLabel
) {}
