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
        // Why: ユーザーターン本文の組み立ては ConsultantPrompts.userBody へ集約した（ADR-039 / SSOT）。
        //      バッチ経路は現在クロール本文を持たないため material に null を渡すが、材料構成が変わる際は
        //      userBody 側だけを直せば両経路へ同時に効く。
        String system = ConsultantPrompts.systemText(subscriptionPlan, brandName);
        String user = ConsultantPrompts.userBody(
                brandName, batchQueryLine.queryText(), null, 1.0, null, jobPromptContext);
        return system + "\n\n" + user;
    }
}
