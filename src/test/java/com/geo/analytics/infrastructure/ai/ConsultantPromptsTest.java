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

    @Test
    void buildKeywordSuggestionPrompt_handlesEmptyRegistered() {
        assertThat(ConsultantPrompts.buildKeywordSuggestionPrompt(List.of()))
                .contains("(なし)");
    }
}
