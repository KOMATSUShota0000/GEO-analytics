package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.IndustryType;

public record TargetAttributes(
        IndustryType industry,
        String categoryKeyword,
        String tradeAreaLabel,
        String city,
        String ward,
        String town,
        String confidenceNote) {
    public TargetAttributes {
        categoryKeyword = normalizeOptional(categoryKeyword);
        city = normalizeOptional(city);
        ward = normalizeOptional(ward);
        town = normalizeOptional(town);
    }

    private static String normalizeOptional(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
