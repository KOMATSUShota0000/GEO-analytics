package com.geo.analytics.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.ai.dto.BatchQueryLine;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeminiBatchClientTest {

    @Test
    void writeBatchRequestJsonlInjectsPromptContextBetweenSystemAndUserPrompt() {
        UUID qId = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
        BatchQueryLine line = new BatchQueryLine(qId, "価格");
        String text =
                GeminiBatchPromptText.combinedPromptText(
                        "BrandX", line, SubscriptionPlan.STANDARD, "JOB_CTX_BLOCK");
        String system = ConsultantPrompts.systemText(SubscriptionPlan.STANDARD, "BrandX");
        String user = ConsultantPrompts.userTextBrandQueryOnly("BrandX", "価格");
        assertThat(text).isEqualTo(system + "\n\n" + "JOB_CTX_BLOCK" + "\n\n" + user);
    }

    @Test
    void writeBatchRequestJsonlOmitsBlankJobPromptContextWithoutExtraBlankLines() {
        UUID qId = UUID.fromString("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22");
        BatchQueryLine line = new BatchQueryLine(qId, "品質");
        String text =
                GeminiBatchPromptText.combinedPromptText(
                        "Acme", line, SubscriptionPlan.PRO, "   \t\n  ");
        String system = ConsultantPrompts.systemText(SubscriptionPlan.PRO, "Acme");
        String user = ConsultantPrompts.userTextBrandQueryOnly("Acme", "品質");
        assertThat(text).isEqualTo(system + "\n\n" + user);
    }
}
