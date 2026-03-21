package com.geo.analytics.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BatchConfig(
    @JsonProperty("displayName") String displayName,
    @JsonProperty("input_config") InputConfig inputConfig
) {}
