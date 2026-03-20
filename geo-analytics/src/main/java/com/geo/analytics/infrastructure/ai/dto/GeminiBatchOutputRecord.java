package com.geo.analytics.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiBatchOutputRecord(
    String key,
    GeminiBatchRequestStatus status,
    JsonNode response
) {}
