package com.geo.analytics.application.dto;

public record SyncVerificationResult(
    String rawResponseJson,
    Double somScore,
    Boolean brandMentioned,
    Integer mentionRank,
    String naturalLanguageResponse
) {}
