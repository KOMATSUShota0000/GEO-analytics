package com.geo.analytics.domain.service;

import com.geo.analytics.domain.model.CompetitorResult;
import com.geo.analytics.domain.model.SomRawMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AI 回答に同時に登場した他ブランドを回答文から実測する（#64 / #65 / #112）。
 *
 * <p>Why: 同じ測定をリアルタイム経路（{@code GeminiVerificationAdapter}）とバッチ経路
 * （{@code GeminiResultProcessor}）が別々に書くと、片方だけ直る事故が起きる。名前は LLM、選別と計数は
 * Java という分担（.cursorrules 12節）を1箇所に閉じ込める（SSOT、同10節）。
 */
public final class CompetitorMeasurer {

    private final BrandMentionEngine brandMentionEngine;
    private final EntityNormalizer entityNormalizer;

    public CompetitorMeasurer(BrandMentionEngine brandMentionEngine, EntityNormalizer entityNormalizer) {
        this.brandMentionEngine = Objects.requireNonNull(brandMentionEngine, "brandMentionEngine");
        this.entityNormalizer = Objects.requireNonNull(entityNormalizer, "entityNormalizer");
    }

    /**
     * @param answerText  AI 回答の本文
     * @param mainBrand   評価対象のブランド名
     * @param namedBrands LLM が挙げた、回答文に登場するブランド名
     * @param isProPlan   SoM 算出のプラン係数
     * @param lAvg        SoM 算出に使う平均応答長
     */
    public List<CompetitorResult> measure(
            String answerText, String mainBrand, List<String> namedBrands, boolean isProPlan, double lAvg) {
        List<String> acceptedLabels = CompetitorSelection.accept(namedBrands, mainBrand, entityNormalizer);
        if (acceptedLabels.isEmpty()) {
            return List.of();
        }
        // Why: 引用順位は「自社を含む候補の中での登場順」。自社を先頭に入れないと競合同士の相対順位になる（#66）。
        List<String> rankingNames = new ArrayList<>(acceptedLabels.size() + 1);
        rankingNames.add(mainBrand);
        rankingNames.addAll(acceptedLabels);
        List<CompetitorResult> results = new ArrayList<>(acceptedLabels.size());
        for (String label : acceptedLabels) {
            var mention = brandMentionEngine.measure(answerText, label);
            int position = brandMentionEngine.citationPosition(answerText, label, rankingNames);
            SomRawMetrics metrics = new SomRawMetrics(
                    mention.mentionChars(),
                    position > 0 ? position : null,
                    0.0,
                    isProPlan,
                    mention.mentionCount() > 0,
                    mention.mentionCount(),
                    mention.totalTokens());
            results.add(new CompetitorResult(
                    label,
                    SomScoreCalculator.compute(metrics, lAvg).scorePercent(),
                    position > 0 ? position : null,
                    mention.mentionCount()));
        }
        return List.copyOf(results);
    }
}
