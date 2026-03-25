package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeywordCategory(@JsonProperty("category_name") String categoryName, @JsonProperty("keywords") List<String> keywords) {
    public KeywordCategory {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
