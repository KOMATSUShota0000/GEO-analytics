package com.geo.analytics.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.RemediationPriorityLevel;
import com.geo.analytics.domain.model.RemediationTask;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RemediationTaskResponse(
        UUID id,
        String category,
        String priority,
        String title,
        String content,
        @JsonProperty("impact_score") double impactScore,
        String rationale,
        String evidence,
        Integer level,
        @JsonProperty("requires_pro_plan") boolean requiresProPlan,
        @JsonProperty("is_masked") boolean isMasked) {

    /** Why: ロック時の文面はサーバが content に入れて返す。表示側に解除条件の知識を持たせない（#84）。 */
    private static final String LOCKED_CONTENT =
            "🔒 Proプランで解放されます。S級タスクは効果が大きいぶん実装の難度も高く、"
                    + "根拠と手順を含む全文をProプラン以上でご覧いただけます。";

    public static RemediationTaskResponse from(RemediationTask task) {
        return from(task, null);
    }

    /**
     * @param plan 解析に適用されたプラン。null は STANDARD 相当として扱い、S級を伏せる
     */
    public static RemediationTaskResponse from(RemediationTask task, SubscriptionPlan plan) {
        if (task == null) {
            return null;
        }
        RemediationPriorityLevel cap = RemediationPriorityLevel.forPriority(task.priority());
        boolean locked = RemediationPriorityLevel.isLockedFor(task.priority(), plan);
        return new RemediationTaskResponse(
                task.id(),
                task.category().name(),
                task.priority().name(),
                task.title(),
                locked ? LOCKED_CONTENT : task.content(),
                task.impactScore(),
                // Why: ロック時も「なぜ効くか」は見せる。伏せると価値が伝わらずアップセルにならない（#79 / #84）。
                //      伏せるのは実装手順（content）と、その裏付けの引用（evidence）だけにする。
                task.rationale(),
                locked ? null : task.evidence(),
                cap.level(),
                cap.requiresProPlan(),
                locked);
    }
}
