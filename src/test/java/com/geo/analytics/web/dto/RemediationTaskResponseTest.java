package com.geo.analytics.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.enums.TaskCategory;
import com.geo.analytics.domain.enums.TaskPriority;
import com.geo.analytics.domain.model.RemediationTask;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * #84 / #139: 伏せるのは手順と根拠だけで、「なぜ効くか」は見せる。
 *
 * <p>ロック時の文面は、数時間で終わるS級タスク（すぐ直せる対策）もあるため「難度が高い」とは書かない。
 */
class RemediationTaskResponseTest {

    private static RemediationTask task(TaskCategory category, TaskPriority priority) {
        return new RemediationTask(
                UUID.randomUUID(), category, priority, "各サービスページに料金の目安を書く",
                "1. 料金ページを開きます。", 0.9d, "料金がわかるとAIが回答に使いやすくなります。",
                "診断項目「詳細な料金体系と制約」：自社サイトには料金の記載がありません。");
    }

    @Test
    void standardでは効果大の手順と根拠を伏せなぜ効くかは返す() {
        RemediationTaskResponse res =
                RemediationTaskResponse.from(task(TaskCategory.SPIKE, TaskPriority.S), SubscriptionPlan.STANDARD);

        assertThat(res.isMasked()).isTrue();
        assertThat(res.content()).contains("効果「大」").contains("Pro プラン").doesNotContain("難度");
        assertThat(res.evidence()).isNull();
        assertThat(res.rationale()).isEqualTo("料金がわかるとAIが回答に使いやすくなります。");
    }

    @Test
    void proでは効果大も全文を返す() {
        RemediationTaskResponse res =
                RemediationTaskResponse.from(task(TaskCategory.SLAB, TaskPriority.S), SubscriptionPlan.PRO);

        assertThat(res.isMasked()).isFalse();
        assertThat(res.content()).isEqualTo("1. 料金ページを開きます。");
        assertThat(res.evidence()).startsWith("診断項目");
    }
}
