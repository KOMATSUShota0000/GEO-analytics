package com.geo.analytics.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(
    @NotBlank(message = "brandName must not be blank")
    @Size(max = 255, message = "brandName must not exceed 255 characters")
    String brandName
) {}
