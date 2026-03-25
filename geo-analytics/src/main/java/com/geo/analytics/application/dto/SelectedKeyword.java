package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record SelectedKeyword(@JsonProperty("text") @NotBlank String text, @JsonProperty("category_name") @NotBlank String categoryName) {
}
