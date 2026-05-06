package com.geo.analytics.web.dto;

public record BenchmarkResponse(
        String name,
        String websiteUrl,
        Double rating,
        Integer reviewCount,
        String source,
        String selectionReason) {}
