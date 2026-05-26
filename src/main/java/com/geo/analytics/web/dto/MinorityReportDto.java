package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MinorityReportDto(
        @Size(max = 1000) String insight,
        @Size(max = 1000) String conflictReason,
        @Size(max = 1000) String evidence) {}
