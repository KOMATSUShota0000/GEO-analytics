package com.geo.analytics.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InputConfig(@JsonProperty("file_name") String fileName) {}
