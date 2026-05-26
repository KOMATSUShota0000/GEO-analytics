package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticVerificationAdapterTest {

    @Test
    void gbvsSystemPrompts_excludeLegacySearchEvidenceBlockMarkers() {
        String plain = ConsultantPrompts.systemText(SubscriptionPlan.STANDARD, "BrandX");
        assertThat(plain).doesNotContain("GEO可視性エビデンス（1〜100）");
        assertThat(ConsultantPrompts.userTextBrandQueryOnly("BrandX", "q"))
                .doesNotContain("GEO可視性エビデンス（1〜100）");
    }
}
