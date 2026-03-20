package com.geo.analytics.application.dto;

public record VerificationRequest(
    String brandName,
    String query,
    String url,
    String crawledContent,
    String contentHash
) {
    public VerificationRequest(String brandName, String query) {
        this(brandName, query, null, null, null);
    }

    public VerificationRequest {
        if (brandName == null || brandName.isBlank()) {
            throw new IllegalArgumentException("brandName must not be blank");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
    }
}
