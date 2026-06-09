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

    public int getRealtimeBatchMax() {
        return realtimeBatchMax;
    }

    public boolean isRealtimeAllowed(int queryCount) {
        return queryCount > 0 && queryCount <= realtimeBatchMax;
    }

    public boolean usesProTierFeatures() {
        return this == PRO || this == EXPERT;
    }

    /**
     * 1解析あたり自動生成するデフォルトのクエリ本数。SoM 等の信頼性は多クエリで高まる。
     * {@link #realtimeBatchMax} 以内に収め、上位プランほど多角的に測れる（アップセル）。
     */
    public int defaultQueryCount() {
        return switch (this) {
            case STANDARD -> 3;
            case PRO -> 10;
            case EXPERT -> 30;
        };
    }

    /**
     * RAG で組み込む競合エビデンス（XML 換算文字数の目安／クリッピング上限）。
     */
    public int maxCompetitorEvidenceXmlChars() {
        return switch (this) {
            case STANDARD -> 12_000;
            case PRO -> 24_000;
            case EXPERT -> 48_000;
        };
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
