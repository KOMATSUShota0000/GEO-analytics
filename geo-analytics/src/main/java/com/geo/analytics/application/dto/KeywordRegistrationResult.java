package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KeywordRegistrationResult(
    @JsonProperty("registered_count") int registeredCount,
    @JsonProperty("skipped_count") int skippedCount) {
}
