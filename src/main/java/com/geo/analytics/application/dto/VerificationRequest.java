package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.MaterialSource;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import java.util.UUID;

/**
 * 検証1回分の入力。
 *
 * <p>Why: かつてはクロールした自社サイト本文とドメイン信頼度を材料として持っていたが、AI 回答内での
 * 見え方を測る指標に自社サイト本文を混ぜるのは自作自演だった（ADR-039）。材料は実測の AI Overview
 * （取れなければ材料なしの推定）に一本化し、クロール関連のフィールドを撤去した（ADR-058）。
 */
public record VerificationRequest(
    String brandName,
    String query,
    String url,
    SubscriptionPlan subscriptionPlan,
    UUID jobId,
    UUID queryId,
    String canonicalMainBrand,
    String aiOverviewText
) {
    public VerificationRequest(String brandName, String query) {
        this(brandName, query, null, SubscriptionPlan.STANDARD, null, null, null, null);
    }

    public VerificationRequest {
        if (brandName == null || brandName.isBlank()) {
            throw new IllegalArgumentException("brandName must not be blank");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (subscriptionPlan == null) {
            throw new IllegalArgumentException("subscriptionPlan must not be null");
        }
    }

    /**
     * 材料の出どころ。
     *
     * <p>Why: 材料の有無から一意に決まるため、別フィールドに持たせて食い違う状態を作らない（#92）。
     */
    public MaterialSource materialSource() {
        return aiOverviewText != null && !aiOverviewText.isBlank()
                ? MaterialSource.MEASURED
                : MaterialSource.ESTIMATED;
    }
}
