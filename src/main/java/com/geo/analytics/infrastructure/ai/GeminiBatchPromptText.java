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
        // Why: ユーザーターン本文の組み立ては ConsultantPrompts.userBody へ集約している（ADR-039 / SSOT）。
        //      バッチ経路も実測の AI Overview を材料にする（ADR-046）。取れなかったクエリは null を渡し、
        //      userBody 側の優先順位（実測 > サイト本文 > 推定）に従って推定へ落ちる。
        String system = ConsultantPrompts.systemText(subscriptionPlan, brandName);
        String user = ConsultantPrompts.userBody(
                brandName, batchQueryLine.queryText(), batchQueryLine.aiOverviewText(), jobPromptContext);
        return system + "\n\n" + user;
    }
}
