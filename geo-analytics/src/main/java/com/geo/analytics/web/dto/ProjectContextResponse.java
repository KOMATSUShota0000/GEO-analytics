package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.domain.enums.IndustryType;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProjectContextResponse(IndustryType industryType, List<String> strengths, String targetAudience) {
}
