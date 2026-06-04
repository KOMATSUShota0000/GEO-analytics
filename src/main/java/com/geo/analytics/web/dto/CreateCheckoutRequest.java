package com.geo.analytics.web.dto;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;

public record CreateCheckoutRequest(@NotNull SubscriptionPlan plan) {}
