package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.SomRawMetrics;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SomScoreData(
    @JsonProperty("token_count") Integer tokenCount,
    @JsonProperty("rank_position") Integer rankPosition,
    @JsonProperty("sentiment_intensity") Double sentimentIntensity
) {
    public SomRawMetrics toRawMetrics(SubscriptionPlan subscriptionPlan) {
        int tc = tokenCount != null ? tokenCount : 0;
        int rp = rankPosition != null ? rankPosition : 0;
        double si = sentimentIntensity != null ? sentimentIntensity : 0.0;
        return new SomRawMetrics(tc, rp, si, subscriptionPlan.usesProTierFeatures(), false, 0, 0.0, 0, 0.3);
    }
}
