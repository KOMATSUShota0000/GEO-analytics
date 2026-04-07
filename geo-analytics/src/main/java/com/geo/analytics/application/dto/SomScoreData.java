package com.geo.analytics.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.SomRawMetrics;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SomScoreData(
    @JsonProperty("token_count") Integer tokenCount,
    @JsonProperty("rank_position") Integer rankPosition,
    @JsonProperty("sentiment_intensity") Double sentimentIntensity,
    @JsonProperty("brand_mentioned") Boolean brandMentioned
) {
    /**
     * NLP-derived lengths and counts must be supplied by the Java NLP pipeline (not AI text fields).
     * {@code normalizedSentimentIntensity} must be the coefficient after {@code JapaneseNlpService} normalization.
     */
    public SomRawMetrics toRawMetrics(
            SubscriptionPlan subscriptionPlan,
            double normalizedSentimentIntensity,
            int nlpResponseTokenLength,
            int nlpNounCount,
            double stuffingDensity,
            double sourceWeight) {
        int tc = tokenCount != null ? tokenCount : 0;
        int rp = rankPosition != null ? rankPosition : 0;
        boolean mentioned = Boolean.TRUE.equals(brandMentioned);
        return new SomRawMetrics(
                tc,
                rp,
                normalizedSentimentIntensity,
                subscriptionPlan.usesProTierFeatures(),
                mentioned,
                nlpNounCount,
                stuffingDensity,
                nlpResponseTokenLength,
                sourceWeight);
    }
}
