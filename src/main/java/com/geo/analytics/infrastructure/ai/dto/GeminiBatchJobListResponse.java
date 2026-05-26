package com.geo.analytics.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiBatchJobListResponse(List<GeminiBatchJob> batches) {}
