package com.geo.analytics.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AddQueriesRequest(
    @NotEmpty(message = "queries must not be empty")
    @Size(max = 100, message = "queries must not exceed 100 items per batch")
    List<@NotBlank String> queries
) {}
