package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import java.util.UUID;

public record VerificationRequest(
    String brandName,
    String query,
    String url,
    String crawledContent,
    String contentHash,
    SubscriptionPlan subscriptionPlan,
    UUID jobId,
    UUID queryId
) {
    public VerificationRequest(String brandName, String query) {
        this(brandName, query, null, null, null, SubscriptionPlan.STANDARD, null, null);
    }

    public VerificationRequest(String brandName, String query, String url, String crawledContent, String contentHash) {
        this(brandName, query, url, crawledContent, contentHash, SubscriptionPlan.STANDARD, null, null);
    }

    public VerificationRequest {
        if (brandName == null || brandName.isBlank()) {
            throw new IllegalArgumentException("brandName must not be blank");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (subscriptionPlan == null) {
            throw new IllegalArgumentException("subscriptionPlan must not be null");
        }
    }
}
