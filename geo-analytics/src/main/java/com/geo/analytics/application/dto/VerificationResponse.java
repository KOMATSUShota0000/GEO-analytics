package com.geo.analytics.application.dto;

public record VerificationResponse(
    String rawResponseJson,
    Double somScore,
    Boolean brandMentioned,
    Integer mentionRank
) {}
