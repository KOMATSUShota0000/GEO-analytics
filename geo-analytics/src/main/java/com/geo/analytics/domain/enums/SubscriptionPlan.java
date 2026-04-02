package com.geo.analytics.domain.enums;

import java.util.List;

public enum SubscriptionPlan {
    STANDARD(10, 10, 10),
    PRO(100, 500, 50),
    EXPERT(200, 2000, 50);

    public static final String LIMITS_CALCULATION_VERSION = "SUBSCRIPTION_LIMITS_V2.8";

    private final int dailyLimit;
    private final int totalLimit;
    private final int realtimeBatchMax;

    SubscriptionPlan(int dailyLimit, int totalLimit, int realtimeBatchMax) {
        this.dailyLimit = dailyLimit;
        this.totalLimit = totalLimit;
        this.realtimeBatchMax = realtimeBatchMax;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public int getTotalLimit() {
        return totalLimit;
    }

    public boolean isRealtimeAllowed(int queryCount) {
        return queryCount > 0 && queryCount <= realtimeBatchMax;
    }

    public boolean usesProTierFeatures() {
        return this == PRO || this == EXPERT;
    }

    public boolean hasModelAccess(ModelType modelType) {
        return switch (modelType) {
            case GEMINI -> true;
            case CHATGPT -> this != STANDARD;
            case CLAUDE -> this == EXPERT;
        };
    }

    public static List<SubscriptionPlan> proTierPlans() {
        return List.of(PRO, EXPERT);
    }
}
