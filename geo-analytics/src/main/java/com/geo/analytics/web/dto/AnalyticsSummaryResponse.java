package com.geo.analytics.web.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import java.util.List;
public record AnalyticsSummaryResponse(
    @JsonProperty("trend_data") List<TrendDataPoint> trendData,
    @JsonProperty("competitor_shares") List<CompetitorSharePoint> competitorShares,
    @JsonProperty("subscription_plan") SubscriptionPlan subscriptionPlan
) {}
