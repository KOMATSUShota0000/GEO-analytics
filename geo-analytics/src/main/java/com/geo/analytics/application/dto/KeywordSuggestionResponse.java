package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeywordSuggestionResponse(@JsonProperty("categories") List<KeywordCategory> categories) {
    public KeywordSuggestionResponse {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }
}
