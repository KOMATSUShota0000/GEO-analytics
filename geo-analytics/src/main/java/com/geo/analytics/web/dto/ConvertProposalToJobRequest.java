package com.geo.analytics.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ConvertProposalToJobRequest(@NotNull(message = "plan must not be null") SubscriptionPlan plan) {}
