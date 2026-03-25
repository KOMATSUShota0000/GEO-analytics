package com.geo.analytics.application.dto;

public record VerificationResponse(
    String rawResponseJson,
    Double somScore,
    Boolean brandMentioned,
    Integer mentionRank,
    Integer overallScore,
    int tokenCount,
    int rankPosition,
    double sentimentIntensity,
    String resolvedEntityLabel
) {}
