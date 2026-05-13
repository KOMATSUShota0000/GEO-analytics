package com.geo.analytics.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.PlanLimitsSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonJsonbOperationsSanityTest {

    @Test
    void serializesPlanLimitsSnapshot() {
        var ops = new JacksonJsonbOperations(new ObjectMapper());
        String raw = ops.serialize(PlanLimitsSnapshot.fromPlan(SubscriptionPlan.STANDARD));
        assertThat(raw).contains("dailyLimit").contains("\"totalLimit\"").contains("SUBSCRIPTION_LIMITS");
    }
}
