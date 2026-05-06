package com.geo.analytics.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OneClickCompetitorExtractionRequest(
        @NotBlank(message = "selfUrl must not be blank") @Size(max = 2048, message = "selfUrl must not exceed 2048 characters")
                String selfUrl) {
    public OneClickCompetitorExtractionRequest {
        selfUrl = selfUrl == null ? null : selfUrl.trim();
    }
}
