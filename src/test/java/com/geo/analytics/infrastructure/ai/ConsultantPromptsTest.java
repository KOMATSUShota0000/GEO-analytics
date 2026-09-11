package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultantPromptsTest {

    @Test
    void userTextBrandQueryOnly_containsBrandAndQuery() {
        assertThat(ConsultantPrompts.userTextBrandQueryOnly("ACME", "価格"))
                .contains("ACME")
                .contains("価格");
    }

    @Test
    void systemText_plainGbvs_containsGeoConsultantFraming() {
        String s = ConsultantPrompts.systemText(SubscriptionPlan.STANDARD, "ACME");
        assertThat(s).contains("GEO (Generative Engine Optimization)");
        assertThat(s).contains("ACME");
    }

    /** #58: 後ろに足される指示に埋もれないよう、回答文の確定が他のすべての指示より先に来ること。 */
    @Test
    void systemText_responseStepPrecedesEveryOtherInstruction() {
        for (SubscriptionPlan plan : List.of(SubscriptionPlan.STANDARD, SubscriptionPlan.PRO)) {
            String s = ConsultantPrompts.systemText(plan, "ACME");
            int step1 = s.indexOf("Step 1 - response (mandatory, never empty)");
            assertThat(step1).as(plan.name()).isPositive();
            assertThat(step1).as(plan.name()).isLessThan(s.indexOf("produce prioritized remediation tasks"));
            assertThat(step1).as(plan.name()).isLessThan(s.indexOf("competitorComparison"));
            assertThat(s.indexOf("plain prose only, with no HTML tags")).as(plan.name()).isLessThan(s.indexOf("produce prioritized remediation tasks"));
        }
    }

    @Test
    void systemText_pro_derivesCompetitorsOnlyFromResponse() {
        assertThat(ConsultantPrompts.systemText(SubscriptionPlan.PRO, "ACME"))
                .contains("Derive competitorComparison only from brands actually named in response.");
    }

    @Test
    void buildKeywordSuggestionPrompt_handlesEmptyRegistered() {
        assertThat(ConsultantPrompts.buildKeywordSuggestionPrompt(List.of()))
                .contains("(なし)");
    }
}
