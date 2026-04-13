package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.geo.analytics.domain.support.TextWhitespaceNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateJobRequest(
    @NotBlank(message = "brandName must not be blank")
    @Size(max = 255, message = "brandName must not exceed 255 characters")
    String brandName,
    @JsonProperty("idempotency_key") UUID idempotencyKey
) {
    public CreateJobRequest {
        brandName = TextWhitespaceNormalizer.normalize(brandName);
    }
}
