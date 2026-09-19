package com.geo.analytics.application.dto;

import com.geo.analytics.domain.enums.MaterialSource;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import java.util.UUID;

public record VerificationRequest(
    String brandName,
    String query,
    String url,
    String crawledContent,
    String contentHash,
    SubscriptionPlan subscriptionPlan,
    UUID jobId,
    UUID queryId,
    String canonicalMainBrand,
    Double domainTrustScore,
    String technicalSeoEvidenceSummary,
    String aiOverviewText
) {
    public VerificationRequest(String brandName, String query) {
        this(brandName, query, null, null, null, SubscriptionPlan.STANDARD, null, null, null, null, null, null);
    }

    public VerificationRequest(String brandName, String query, String url, String crawledContent, String contentHash) {
        this(brandName, query, url, crawledContent, contentHash, SubscriptionPlan.STANDARD, null, null, null, null, null, null);
    }

    /** AI Overview を材料にしない呼び出し（バッチ経路・test-sync）向けの形。 */
    public VerificationRequest(
            String brandName,
            String query,
            String url,
            String crawledContent,
            String contentHash,
            SubscriptionPlan subscriptionPlan,
            UUID jobId,
            UUID queryId,
            String canonicalMainBrand,
            Double domainTrustScore,
            String technicalSeoEvidenceSummary) {
        this(brandName, query, url, crawledContent, contentHash, subscriptionPlan, jobId, queryId,
                canonicalMainBrand, domainTrustScore, technicalSeoEvidenceSummary, null);
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
     * <p>Why: 材料の有無から一意に決まるため、別フィールドとして持たせて食い違う状態を作らない（#92）。
     */
    public MaterialSource materialSource() {
        return aiOverviewText != null && !aiOverviewText.isBlank()
                ? MaterialSource.MEASURED
                : MaterialSource.ESTIMATED;
    }
}
