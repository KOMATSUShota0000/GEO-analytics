package com.geo.analytics.application.dto;

public record ExtractedPlace(
        String name,
        String websiteUrl,
        Double rating,
        Integer userRatingsTotal) {
}
