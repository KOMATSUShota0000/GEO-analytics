package com.geo.analytics.web.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
public record TrendDataPoint(
    @JsonProperty("audit_date") LocalDate date,
    @JsonProperty("average_som_score") Double averageSomScore,
    @JsonProperty("average_overall_score") Double averageOverallScore
) {}
