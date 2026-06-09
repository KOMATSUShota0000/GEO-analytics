package com.geo.analytics.domain.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SubscriptionPlanTest {

    @Test
    void defaultQueryCount_matchesPlanTier() {
        assertEquals(3, SubscriptionPlan.STANDARD.defaultQueryCount());
        assertEquals(10, SubscriptionPlan.PRO.defaultQueryCount());
        assertEquals(30, SubscriptionPlan.EXPERT.defaultQueryCount());
    }

    @Test
    void defaultQueryCount_withinRealtimeBatchMax() {
        // デフォルト本数が同時実行上限を超えると解析投入が弾かれるため、不変条件として担保する。
        for (SubscriptionPlan plan : SubscriptionPlan.values()) {
            assertTrue(
                    plan.isRealtimeAllowed(plan.defaultQueryCount()),
                    () -> plan + " defaultQueryCount must be within realtimeBatchMax");
        }
    }
}
