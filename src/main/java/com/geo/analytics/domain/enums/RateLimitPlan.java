package com.geo.analytics.domain.enums;

/**
 * レート制限（バケット容量）専用のプラン区分。
 *
 * <p>課金・サブスクリプションとは無関係（課金は {@code SubscriptionPlan}）。
 * 旧名 {@code PricingPlan} は「価格表/課金」を誤連想させたため 2026-05-20 に改名（ADR-005）。
 */
public enum RateLimitPlan {
    FREE(5L, 60L),
    STANDARD(30L, 600L),
    PRO(100L, 3000L),
    ENTERPRISE(500L, 20000L);

    private final long burstCapacity;
    private final long sustainedCapacity;

    RateLimitPlan(long burstCapacity, long sustainedCapacity) {
        this.burstCapacity = burstCapacity;
        this.sustainedCapacity = sustainedCapacity;
    }

    public long burstCapacity() {
        return burstCapacity;
    }

    public long sustainedCapacity() {
        return sustainedCapacity;
    }
}
