package com.geo.analytics.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiFileMetadata(
    String name,
    String uri,
    @JsonProperty("downloadUri")
    @JsonAlias("download_uri")
    String downloadUri,
    String state,
    String mimeType
) {}
