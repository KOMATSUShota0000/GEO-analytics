package com.geo.analytics.web.dto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record VerifyStreamEvent(String kind, String text, String queryId) {
    public VerifyStreamEvent(String kind, String text) {
        this(kind, text, null);
    }
}