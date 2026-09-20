package com.geo.analytics.domain.model;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.enums.TaskPriority;

/**
 * 改善タスクの優先度に対応する表示レベルと、本文を見るために必要なプラン（#84）。
 *
 * <p>Why: 旧実装は「S級は基礎スコア80点で解放」というスコア基準で、<b>優先度が高いタスクほど高い
 * スコアを要求する循環</b>になっていた（S級を読むには80点、80点にするにはS級の実行が必要）。
 * 助言を最も必要とする低スコアの顧客にだけ助言が届かない構造だったため、プラン基準へ置き換える
 * （オーナー確定 2026-09-20）。プランならスコアと無関係に解除条件が決まり、核③のアップセル誘導とも整合する。
 */
public record RemediationPriorityLevel(Integer level, boolean requiresProPlan) {

    public static RemediationPriorityLevel forPriority(TaskPriority p) {
        return switch (p) {
            case B -> new RemediationPriorityLevel(1, false);
            case A -> new RemediationPriorityLevel(2, false);
            case S -> new RemediationPriorityLevel(3, true);
        };
    }

    /** 本文を伏せるか。S級のみ Pro/Expert 限定とし、それ以外は全プランで全文を見せる。 */
    public static boolean isLockedFor(TaskPriority priority, SubscriptionPlan plan) {
        if (!forPriority(priority).requiresProPlan()) {
            return false;
        }
        return plan == null || !plan.usesProTierFeatures();
    }
}
