package com.geo.analytics.web.dto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.annotation.JsonProperty;
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CompetitorSharePoint(
    @JsonProperty("name") String name,
    @JsonProperty("share") Double share
) {}
