package com.geo.analytics.domain.model;

import com.geo.analytics.domain.enums.SubscriptionPlan;

public record PlanLimitsSnapshot(int dailyLimit, int totalLimit, int realtimeBatchMax, String limitsVersion) {
    public static PlanLimitsSnapshot fromPlan(SubscriptionPlan plan) {
        return new PlanLimitsSnapshot(
                plan.getDailyLimit(), plan.getTotalLimit(), plan.getRealtimeBatchMax(), SubscriptionPlan.LIMITS_CALCULATION_VERSION);
    }

    public boolean isRealtimeAllowed(int queryCount) {
        return queryCount > 0 && queryCount <= realtimeBatchMax;
    }
}
