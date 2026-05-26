package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.SomRawMetrics;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SomScoreData(
    @JsonProperty("token_count") Integer tokenCount,
    @JsonProperty("ai_citation_position") Integer aiCitationPosition,
    @JsonProperty("sentiment_intensity") Double sentimentIntensity,
    @JsonProperty("brand_mentioned") Boolean brandMentioned
) {
    public SomRawMetrics toRawMetrics(
            SubscriptionPlan subscriptionPlan,
            double normalizedSentimentIntensity,
            int nlpResponseTokenLength,
            int nlpNounCount,
            double stuffingDensity,
            double sourceWeight) {
        int tc = tokenCount != null ? tokenCount : 0;
        boolean mentioned = Boolean.TRUE.equals(brandMentioned);
        return new SomRawMetrics(
                tc,
                aiCitationPosition,
                normalizedSentimentIntensity,
                subscriptionPlan.usesProTierFeatures(),
                mentioned,
                nlpNounCount,
                stuffingDensity,
                nlpResponseTokenLength,
                sourceWeight);
    }
}
