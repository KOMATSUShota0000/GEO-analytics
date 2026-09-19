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
    /**
     * 引用順位を Java の実測値へ差し替える（#66）。
     *
     * <p>Why: 順位は確定的な物理量なので LLM に数えさせない（.cursorrules 12節）。LLM 申告は比較ログにだけ残す。
     */
    public SomScoreData withAiCitationPosition(Integer measuredPosition) {
        return new SomScoreData(tokenCount, measuredPosition, sentimentIntensity, brandMentioned);
    }

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
