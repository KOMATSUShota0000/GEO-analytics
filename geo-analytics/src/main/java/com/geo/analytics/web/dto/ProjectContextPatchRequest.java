package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.domain.enums.IndustryType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProjectContextPatchRequest(
    @NotNull IndustryType industryType,
    @Size(max = 1000) String extractedStrengths,
    @Size(max = 1000) String targetAudience) {
}
