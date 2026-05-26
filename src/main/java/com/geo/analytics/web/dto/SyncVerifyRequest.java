package com.geo.analytics.web.dto;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SyncVerifyRequest(
    @NotBlank(message = "brandName must not be blank")
    @Size(max = 255, message = "brandName must not exceed 255 characters")
    String brandName,

    @NotBlank(message = "query must not be blank")
    @Size(max = 2000, message = "query must not exceed 2000 characters")
    String query,

    SubscriptionPlan plan
) {}
