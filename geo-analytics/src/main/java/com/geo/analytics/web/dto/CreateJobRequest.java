package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.geo.analytics.domain.enums.CompetitorExtractionMode;
import com.geo.analytics.domain.support.TextWhitespaceNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateJobRequest(
    @NotBlank(message = "brandName must not be blank")
    @Size(max = 255, message = "brandName must not exceed 255 characters")
    String brandName,
    @NotBlank(message = "targetUrl must not be blank")
    @Size(max = 2048, message = "targetUrl must not exceed 2048 characters")
    String targetUrl,
    @Size(max = 16000)
    String businessSummary,
    @Size(max = 16000)
    String targetAudience,
    @Size(max = 16000)
    String focusPoints,
    @JsonProperty("industryType") CompetitorExtractionMode competitorExtractionMode,
    @JsonProperty("idempotency_key") UUID idempotencyKey
) {
    public CreateJobRequest {
        brandName = TextWhitespaceNormalizer.normalize(brandName);
        targetUrl = TextWhitespaceNormalizer.normalize(targetUrl);
        businessSummary = optionalText(businessSummary);
        targetAudience = optionalText(targetAudience);
        focusPoints = optionalText(focusPoints);
        if (competitorExtractionMode == null) {
            competitorExtractionMode = CompetitorExtractionMode.LOCAL_STORE;
        }
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String s = value.strip();
        return s.isEmpty() ? null : s;
    }
}
