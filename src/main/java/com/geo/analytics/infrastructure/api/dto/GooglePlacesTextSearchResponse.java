package com.geo.analytics.infrastructure.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GooglePlacesTextSearchResponse(List<GooglePlace> places) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GooglePlace(
            LocalizedText displayName,
            String websiteUri,
            Double rating,
            Integer userRatingCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocalizedText(String text) {
    }
}
