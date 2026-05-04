package com.geo.analytics.application.dto;

public record SelectedCompetitor(
        String name,
        String websiteUrl,
        Double rating,
        Integer userRatingsTotal,
        String reasoning,
        boolean synthetic) {
}
