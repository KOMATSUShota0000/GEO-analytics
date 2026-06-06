package com.geo.analytics.web.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import java.util.List;
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalyticsSummaryResponse(
    @JsonProperty("trend_data") List<TrendDataPoint> trendData,
    @JsonProperty("subscription_plan") SubscriptionPlan subscriptionPlan
) {}
