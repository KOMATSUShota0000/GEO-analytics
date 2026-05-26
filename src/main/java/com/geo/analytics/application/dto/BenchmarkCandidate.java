package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.BenchmarkSource;

public record BenchmarkCandidate(
        String name,
        String websiteUrl,
        Double rating,
        Integer reviewCount,
        BenchmarkSource source,
        String selectionReason) {
}
