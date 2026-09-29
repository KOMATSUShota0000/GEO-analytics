package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkspaceResponse(
        @JsonProperty("subscription_plan") String subscriptionPlan,
        @JsonProperty("has_stripe_subscription") boolean hasStripeSubscription) {}
