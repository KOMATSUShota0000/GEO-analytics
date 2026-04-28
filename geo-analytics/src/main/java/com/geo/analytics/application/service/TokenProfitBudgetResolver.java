package com.geo.analytics.application.service;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.config.AppProperties;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class TokenProfitBudgetResolver {

    private final AppProperties appProperties;

    public TokenProfitBudgetResolver(AppProperties appProperties) {
        this.appProperties = Objects.requireNonNull(appProperties, "appProperties");
    }

    /**
     * 競合エビデンス XML（{@link com.geo.analytics.domain.logic.SeoEvidenceXmlBuilder}）に許容する最大文字数。
     * 計算: 有効予算USD = planBudget × (1 − 保留利益率)、許容トークン ≈ 有効予算 / (USD/百万トークン) × 1e6、
     * 許容文字数 ≈ 許容トークン / charsPerTokenApprox。異常値時は 0（クリッパーは空リスト）。
     */
    public int maxCompetitorXmlChars(SubscriptionPlan plan) {
        SubscriptionPlan resolved = plan != null ? plan : SubscriptionPlan.STANDARD;
        AppProperties.TokenProfitGuard guard = appProperties.getAi().getTokenProfitGuard();
        double planUsd = resolvePlanBudgetUsd(guard.getPlanBudgetUsd(), resolved);
        double margin = clamp01(guard.getReservedMarginRate());
        double effectiveUsd = planUsd * (1.0d - margin);
        if (effectiveUsd <= 0.0d) {
            return 0;
        }
        double usdPerM = guard.getUsdPerMillionTokens();
        if (usdPerM <= 0.0d) {
            return 0;
        }
        double maxTokens = (effectiveUsd / usdPerM) * 1_000_000d;
        double charsPerTok = guard.getCharsPerTokenApprox();
        if (charsPerTok <= 0.0d) {
            charsPerTok = 1.0d;
        }
        double maxCharsDouble = maxTokens / charsPerTok;
        if (maxCharsDouble <= 0.0d || Double.isNaN(maxCharsDouble) || Double.isInfinite(maxCharsDouble)) {
            return 0;
        }
        long rounded = (long) Math.floor(maxCharsDouble);
        return (int) Math.min(rounded, Integer.MAX_VALUE);
    }

    private static double resolvePlanBudgetUsd(Map<String, Double> map, SubscriptionPlan plan) {
        if (map == null || map.isEmpty()) {
            return 0.02d;
        }
        Double v = map.get(plan.name());
        if (v != null && v > 0.0d) {
            return v;
        }
        Double std = map.get(SubscriptionPlan.STANDARD.name());
        if (std != null && std > 0.0d) {
            return std;
        }
        return map.values().stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).max().orElse(0.02d);
    }

    private static double clamp01(double margin) {
        if (margin < 0.0d) {
            return 0.0d;
        }
        if (margin > 1.0d) {
            return 1.0d;
        }
        return margin;
    }
}
