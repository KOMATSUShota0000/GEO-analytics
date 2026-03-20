package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SomScoreData(
    String response,
    Boolean brandMentioned,
    Integer mentionRank,
    Double confidenceScore
) {}
