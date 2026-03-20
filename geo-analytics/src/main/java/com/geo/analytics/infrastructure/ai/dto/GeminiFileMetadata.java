package com.geo.analytics.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiFileMetadata(
    String name,
    String uri,
    String state,
    String mimeType
) {}
