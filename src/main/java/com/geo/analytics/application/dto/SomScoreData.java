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

    /**
     * スコアの入力を Java の実測値で組み立てる（#60）。
     *
     * <p>Why: 旧実装は LLM 自己申告の「言及文字数」を {@code nounCount}（回数）へ渡しており、計算式が
     * 「文字数 ÷ トークン数」という単位の合わない割り算になっていた。回数・文字数・トークン数はすべて
     * {@code BrandMentionEngine} の実測値を渡す（.cursorrules 12節）。LLM 申告の token_count は使わない。
     *
     * @param measuredMentionCount 回答文中のブランド言及回数（Java 実測）
     * @param measuredMentionChars 言及箇所の文字数（Java 実測）
     * @param measuredTokenCount   回答文の形態素トークン数（Java 実測）
     */
    public SomRawMetrics toRawMetrics(
            SubscriptionPlan subscriptionPlan,
            double normalizedSentimentIntensity,
            int measuredTokenCount,
            int measuredMentionCount,
            int measuredMentionChars,
            double stuffingDensity) {
        boolean mentioned = Boolean.TRUE.equals(brandMentioned);
        return new SomRawMetrics(
                measuredMentionChars,
                aiCitationPosition,
                normalizedSentimentIntensity,
                subscriptionPlan.usesProTierFeatures(),
                mentioned,
                measuredMentionCount,
                stuffingDensity,
                measuredTokenCount);
    }
}
