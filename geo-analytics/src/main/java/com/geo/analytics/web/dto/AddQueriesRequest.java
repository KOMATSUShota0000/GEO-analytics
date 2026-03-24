package com.geo.analytics.web.dto;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AddQueriesRequest(
    @NotEmpty(message = "queries must not be empty")
    @Size(max = 100, message = "queries must not exceed 100 items per batch")
    List<@NotBlank String> queries,
    @NotNull(message = "plan must not be null")
    SubscriptionPlan plan
) {}
