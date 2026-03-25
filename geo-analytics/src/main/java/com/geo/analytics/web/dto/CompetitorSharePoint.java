package com.geo.analytics.web.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
public record CompetitorSharePoint(
    @JsonProperty("name") String name,
    @JsonProperty("share") Double share
) {}
