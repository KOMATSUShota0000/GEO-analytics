package com.geo.analytics.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.enums.TaskPriority;
import org.junit.jupiter.api.Test;

/**
 * #84: 改善タスクのゲートはプラン基準。
 *
 * <p>旧実装のスコア基準は「S級を読むには80点、80点にするにはS級の実行が必要」という循環で、
 * 助言を最も必要とする低スコアの顧客にだけ助言が届かなかった。
 */
class RemediationPriorityLevelTest {

    @Test
    void s級はStandardで伏せられPro以上で解放される() {
        assertThat(RemediationPriorityLevel.isLockedFor(TaskPriority.S, SubscriptionPlan.STANDARD)).isTrue();
        assertThat(RemediationPriorityLevel.isLockedFor(TaskPriority.S, SubscriptionPlan.PRO)).isFalse();
        assertThat(RemediationPriorityLevel.isLockedFor(TaskPriority.S, SubscriptionPlan.EXPERT)).isFalse();
    }

    @Test
    void a級とb級はどのプランでも全文を見せる() {
        for (SubscriptionPlan plan : SubscriptionPlan.values()) {
            assertThat(RemediationPriorityLevel.isLockedFor(TaskPriority.A, plan)).isFalse();
            assertThat(RemediationPriorityLevel.isLockedFor(TaskPriority.B, plan)).isFalse();
        }
    }

    @Test
    void プラン不明はStandard相当として扱う() {
        assertThat(RemediationPriorityLevel.isLockedFor(TaskPriority.S, null)).isTrue();
        assertThat(RemediationPriorityLevel.isLockedFor(TaskPriority.B, null)).isFalse();
    }

    @Test
    void 表示レベルは優先度の高い順に大きい() {
        assertThat(RemediationPriorityLevel.forPriority(TaskPriority.S).level()).isEqualTo(3);
        assertThat(RemediationPriorityLevel.forPriority(TaskPriority.A).level()).isEqualTo(2);
        assertThat(RemediationPriorityLevel.forPriority(TaskPriority.B).level()).isEqualTo(1);
    }
}
