package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record KeywordRegistrationRequest(
    @JsonProperty("project_id") @NotNull UUID projectId,
    @NotEmpty @Valid List<SelectedKeyword> keywords) {
}
