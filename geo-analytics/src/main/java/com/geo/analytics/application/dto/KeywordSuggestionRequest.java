package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeywordSuggestionRequest(
    @JsonProperty("url") @NotBlank String url,
    @JsonProperty("target_description") @NotBlank String targetDescription,
    @JsonProperty("registered_keywords") List<String> registeredKeywords) {
    public KeywordSuggestionRequest {
        registeredKeywords = registeredKeywords != null ? List.copyOf(registeredKeywords) : List.of();
    }
}
