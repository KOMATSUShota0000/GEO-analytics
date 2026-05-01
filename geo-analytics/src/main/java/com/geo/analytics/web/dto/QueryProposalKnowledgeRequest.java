package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record QueryProposalKnowledgeRequest(
        @Size(max = 50_000) String businessDescription,
        @Size(max = 50_000) String targetAudience,
        @Size(max = 50_000) String strategicFocus) {}
