package com.geo.analytics.domain.model;

import com.geo.analytics.domain.enums.ModelType;
import com.geo.analytics.domain.enums.SubscriptionPlan;

public final class QuotaCreditCalculator {
    public static final int DEPOSIT_PER_KEYWORD = 10;
    private QuotaCreditCalculator() {
    }
    public static long actualCredits(long textLength, SubscriptionPlan plan) {
        int mult = combinedModelMultiplier(plan);
        long tier = textLength <= 0L ? 0L : (long) Math.ceil(textLength / 1000.0);
        return Math.max(1L, 1L + tier * mult);
    }
    public static long actualCreditsGeminiBatchLine(long textLength) {
        long tier = textLength <= 0L ? 0L : (long) Math.ceil(textLength / 1000.0);
        return Math.max(1L, 1L + tier);
    }
    public static long refundAfterDeposit(long deposit, long actualCost) {
        long d = deposit - actualCost;
        return d > 0L ? d : 0L;
    }
    public static int combinedModelMultiplier(SubscriptionPlan plan) {
        int s = 0;
        for (ModelType mt : ModelType.values()) {
            if (plan.hasModelAccess(mt)) {
                s += mt.quotaMultiplier();
            }
        }
        return Math.max(1, s);
    }
}
