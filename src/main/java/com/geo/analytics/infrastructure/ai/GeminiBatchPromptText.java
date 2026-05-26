package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.ai.dto.BatchQueryLine;

public final class GeminiBatchPromptText {

    private GeminiBatchPromptText() {}

    public static String combinedPromptText(
            String brandName,
            BatchQueryLine batchQueryLine,
            SubscriptionPlan subscriptionPlan,
            String jobPromptContext) {
        String system = ConsultantPrompts.systemText(subscriptionPlan, brandName);
        String user = ConsultantPrompts.userTextBrandQueryOnly(brandName, batchQueryLine.queryText());
        String ctx = jobPromptContext == null ? "" : jobPromptContext.strip();
        if (ctx.isEmpty()) {
            return system + "\n\n" + user;
        }
        return system + "\n\n" + ctx + "\n\n" + user;
    }
}
