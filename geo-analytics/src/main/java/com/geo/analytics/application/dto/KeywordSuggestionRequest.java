package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeywordSuggestionRequest(@JsonProperty("url") @NotBlank String url, @JsonProperty("target_description") @NotBlank String targetDescription) {
}
